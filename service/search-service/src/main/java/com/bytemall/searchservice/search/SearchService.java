package com.bytemall.searchservice.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.bytemall.searchservice.product.ProductIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// 标记这是一个Service
@Service
// 自动注入用来打日志的log变量
@Slf4j
// 自动注入依赖
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchClient elasticsearchClient;

    // 商品摘要只需要这些字段，查询时限制 _source 输出，省流量也避免多余字段干扰反序列化
    private static final List<String> SUMMARY_FIELDS = List.of(
            "spuId", "merchantId", "title", "subtitle", "brand", "categoryId",
            "coverImage", "minPrice", "maxPrice", "salesCount", "status", "publishedAt");

    public SearchResponses.SearchResult products(String query, Integer page, Integer pageSize, String category, String brand, Integer minPrice, Integer maxPrice, String sort) throws IOException {
        log.info("Searching for {}", query);

        // 分页参数兜底，防止非法值把 ES 查询搞挂
        int safePage = (page == null || page < 1) ? 1 : page;
        int safePageSize = (pageSize == null || pageSize < 1) ? 20 : pageSize;

        // 精确筛选统一放进 filter：不参与打分，而且 ES 能缓存 filter 结果
        List<Query> filters = new ArrayList<>();
        // 只搜上架中的商品
        filters.add(Query.of(q -> q.term(t -> t.field("status").value("ON_SALE"))));
        // 分类精确筛选
        if (category != null && !category.isBlank()) {
            filters.add(Query.of(q -> q.term(t -> t.field("categoryId").value(category))));
        }
        // 品牌精确筛选
        if (brand != null && !brand.isBlank()) {
            filters.add(Query.of(q -> q.term(t -> t.field("brand").value(brand))));
        }
        // 价格区间筛选：商品的 [minPrice, maxPrice] 和用户给的区间有交集就命中
        if (minPrice != null) {
            filters.add(Query.of(q -> q.range(r -> r.longNumber(n -> n.field("maxPrice").gte(minPrice.longValue())))));
        }
        if (maxPrice != null) {
            filters.add(Query.of(q -> q.range(r -> r.longNumber(n -> n.field("minPrice").lte(maxPrice.longValue())))));
        }

        // 关键词用分词后的多字段匹配；没给关键词就是纯筛选浏览，不需要 must
        String keyword = (query == null || query.isBlank()) ? null : query.trim();

        // 组装 ES 查询请求：查别名 product-search，它会路由到真实索引
        SearchResponse<SearchResponses.ProductSummary> response = elasticsearchClient.search(request -> {
            request.index(ProductIndexService.ALIAS)
                    .from((safePage - 1) * safePageSize)
                    .size(safePageSize)
                    // 只取商品摘要用得到的字段
                    .source(s -> s.filter(f -> f.includes(SUMMARY_FIELDS)))
                    // 返回精确的命中总数，分页才准
                    .trackTotalHits(t -> t.enabled(true))
                    .query(q -> q.bool(b -> {
                        b.filter(filters);
                        if (keyword != null) {
                            b.must(m -> m.multiMatch(mm -> mm
                                    .query(keyword)
                                    .fields("title", "subtitle", "descriptionText")));
                        }
                        return b;
                    }))
                    // 分类和品牌聚合：和商品结果使用同一筛选上下文
                    .aggregations("categories", a -> a.terms(t -> t.field("categoryId").size(50)))
                    .aggregations("brands", a -> a.terms(t -> t.field("brand").size(50)));

            // 按参数排序：价格、上架时间、销量，默认按相关度
            if ("PRICE_ASC".equals(sort)) {
                request.sort(s -> s.field(f -> f.field("minPrice").order(SortOrder.Asc)));
            } else if ("PRICE_DESC".equals(sort)) {
                request.sort(s -> s.field(f -> f.field("maxPrice").order(SortOrder.Desc)));
            } else if ("NEWEST".equals(sort)) {
                request.sort(s -> s.field(f -> f.field("publishedAt").order(SortOrder.Desc)));
            } else if ("SALES_DESC".equals(sort)) {
                request.sort(s -> s.field(f -> f.field("salesCount").order(SortOrder.Desc)));
            } else {
                // RECOMMENDED：先按相关度，再按销量，最后按唯一ID保证分页顺序稳定
                request.sort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
                request.sort(s -> s.field(f -> f.field("salesCount").order(SortOrder.Desc)));
            }
            request.sort(s -> s.field(f -> f.field("spuId").order(SortOrder.Asc)));
            return request;
        }, SearchResponses.ProductSummary.class);

        // 每个命中反序列化成商品摘要（_source 已限制成摘要字段）
        List<SearchResponses.ProductSummary> productSummaryList = response.hits()
                .hits()
                .stream()
                .map(Hit::source)
                .toList();

        long total = response.hits().total() == null ? 0 : response.hits().total().value();
        log.info("Searching for {} hit {} documents", query, total);

        // 组装前端要的搜索结果：分页商品 + 分类/品牌聚合
        SearchResponses.Aggregations aggregations = new SearchResponses.Aggregations(
                toAggregations(response.aggregations().get("categories")),
                toAggregations(response.aggregations().get("brands")));
        return new SearchResponses.SearchResult(safePage, safePageSize, total, productSummaryList, aggregations);
    }

    // 把 ES 的 terms 聚合转成前端要的筛选项；索引里只有分类 ID，label 先用值兜底
    private static List<SearchResponses.Aggregation> toAggregations(Aggregate aggregate) {
        if (aggregate == null || !aggregate.isSterms()) {
            return List.of();
        }
        return aggregate.sterms().buckets().array().stream()
                .map(bucket -> new SearchResponses.Aggregation(
                        bucket.key().stringValue(),
                        bucket.key().stringValue(),
                        bucket.docCount()))
                .toList();
    }

    public List<String> suggestions(String q) throws IOException {
        // 打日志
        log.info("Searching suggestions for {}", q);
        SearchResponse<String> response = elasticsearchClient.search(request -> {
            request.index(ProductIndexService.ALIAS)
                    .size(10)
                    .source(s -> s.filter(f -> f.includes(List.of("title"))))
                    .trackTotalHits(t -> t.enabled(true))
                    .query(query -> query.bool(b -> {
                        b.must(m -> m.multiMatch(mm -> mm
                                .query(q)
                                .fields("title")));
                        return b;
                    }));
            return request;
        }, String.class);
        return response.hits().hits().stream().map(Hit::source).toList();
    }
}
