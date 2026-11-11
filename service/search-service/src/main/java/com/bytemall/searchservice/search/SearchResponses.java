package com.bytemall.searchservice.search;

import java.time.Instant;
import java.util.List;

/**
 * 搜索接口响应对象，统一包含商品摘要、筛选聚合、分页结果和搜索建议。
 */
public final class SearchResponses {

    private SearchResponses() {
    }

    /**
     * 搜索结果中的商品摘要，不包含商品详情、完整SKU列表等重型字段。
     *
     * @param spuId       商品SPU ID，与ES文档ID保持一致
     * @param merchantId  商品所属商家ID
     * @param title       商品标题
     * @param subtitle    商品副标题
     * @param brand       商品品牌
     * @param categoryId  商品分类ID
     * @param coverImage  商品封面图片URL
     * @param minPrice    商品最低售价，单位为人民币分
     * @param maxPrice    商品最高售价，单位为人民币分
     * @param salesCount  商品销量
     * @param status      商品状态：DRAFT、ON_SALE或OFF_SALE，买家搜索只返回ON_SALE
     * @param publishedAt 商品上架时间，使用带时区的UTC时刻
     */
    public record ProductSummary(
            String spuId,
            String merchantId,
            String title,
            String subtitle,
            String brand,
            String categoryId,
            String coverImage,
            long minPrice,
            long maxPrice,
            long salesCount,
            String status,
            Instant publishedAt
    ) {
    }

    /**
     * 单个聚合筛选项，例如某个分类或品牌及其命中数量。
     *
     * @param value 用于提交筛选条件的值，例如分类ID或品牌名称
     * @param label 前端展示的文本
     * @param count 当前筛选上下文中该选项命中的商品数量
     */
    public record Aggregation(
            String value,
            String label,
            long count
    ) {
    }

    /**
     * 搜索结果的聚合筛选信息，与当前商品结果使用相同的筛选上下文。
     *
     * @param categories 商品分类聚合，空值会被转换为空列表
     * @param brands     商品品牌聚合，空值会被转换为空列表
     */
    public record Aggregations(
            List<Aggregation> categories,
            List<Aggregation> brands
    ) {
        public Aggregations {
            categories = categories == null ? List.of() : List.copyOf(categories);
            brands = brands == null ? List.of() : List.copyOf(brands);
        }
    }

    /**
     * 商品搜索分页结果。
     *
     * @param page         当前页码，从1开始
     * @param size         每页商品数量
     * @param total        符合当前搜索和筛选条件的商品总数
     * @param items        当前页的商品摘要，空值会被转换为空列表
     * @param aggregations 当前搜索和筛选条件下的分类、品牌聚合
     */
    public record SearchResult(
            int page,
            int size,
            long total,
            List<ProductSummary> items,
            Aggregations aggregations
    ) {
        public SearchResult {
            items = items == null ? List.of() : List.copyOf(items);
            aggregations = aggregations == null
                    ? new Aggregations(List.of(), List.of())
                    : aggregations;
        }
    }

    /**
     * 单条搜索建议。
     *
     * @param text 建议展示的搜索关键词
     */
    public record Suggestion(
            String text
    ) {
    }
}
