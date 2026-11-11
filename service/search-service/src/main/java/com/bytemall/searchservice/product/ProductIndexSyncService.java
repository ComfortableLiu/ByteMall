package com.bytemall.searchservice.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexSyncService {

    private static final int BATCH_SIZE = 500;
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String PRODUCT_SELECT = """
            SELECT spu.id, spu.merchant_id, spu.category_id, spu.title, spu.subtitle,
                   spu.brand, spu.description_html, spu.status, spu.sales_count,
                   spu.version, spu.published_at, spu.updated_at,
                   (SELECT MIN(sku.price) FROM product_sku sku
                    WHERE sku.spu_id = spu.id AND sku.enabled = 1) AS min_price,
                   (SELECT MAX(sku.price) FROM product_sku sku
                    WHERE sku.spu_id = spu.id AND sku.enabled = 1) AS max_price,
                   (SELECT image.url FROM product_image image
                    WHERE image.spu_id = spu.id
                    ORDER BY image.is_primary DESC, image.sort_order ASC, image.id ASC
                    LIMIT 1) AS cover_image
            FROM product_spu spu
            """;
    private static final String PRODUCT_QUERY = PRODUCT_SELECT + " ORDER BY spu.id";
    private static final String PRODUCT_BY_ID_QUERY = PRODUCT_SELECT + " WHERE spu.id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final ElasticsearchClient elasticsearchClient;

    public SyncResult synchronizeAll() throws IOException {
        List<ProductIndexDocument> documents = jdbcTemplate.query(PRODUCT_QUERY, (rs, rowNum) -> toDocument(rs));
        int succeeded = 0;
        int failed = 0;

        for (int start = 0; start < documents.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, documents.size());
            BulkResponse response = writeBatch(documents.subList(start, end));
            for (BulkResponseItem item : response.items()) {
                if (item.error() == null) {
                    succeeded++;
                } else {
                    failed++;
                    log.error("商品索引写入失败，spuId={}，类型={}，原因={}",
                            item.id(), item.error().type(), item.error().reason());
                }
            }
        }

        if (!documents.isEmpty()) {
            elasticsearchClient.indices().refresh(request -> request.index(ProductIndexService.INDEX));
        }

        log.info("商品索引同步完成，total={}，succeeded={}，failed={}", documents.size(), succeeded, failed);
        return new SyncResult(ProductIndexService.INDEX, documents.size(), succeeded, failed);
    }

    public void synchronizeBySpuId(String spuId) throws IOException {
        List<ProductIndexDocument> documents = jdbcTemplate.query(
                PRODUCT_BY_ID_QUERY,
                (rs, rowNum) -> toDocument(rs),
                Long.valueOf(spuId)
        );
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("商品不存在，spuId=" + spuId);
        }

        BulkResponse response = writeBatch(documents);
        BulkResponseItem item = response.items().get(0);
        if (item.error() != null) {
            throw new IOException("商品索引写入失败，spuId=" + spuId + "，原因=" + item.error().reason());
        }

        elasticsearchClient.indices().refresh(request -> request.index(ProductIndexService.INDEX));
        log.info("单商品索引同步完成，spuId={}", spuId);
    }

    private BulkResponse writeBatch(List<ProductIndexDocument> documents) throws IOException {
        BulkRequest.Builder request = new BulkRequest.Builder();
        for (ProductIndexDocument document : documents) {
            request.operations(operation -> operation.index(index -> index
                    .index(ProductIndexService.INDEX)
                    .id(document.spuId())
                    .document(document)
            ));
        }
        return elasticsearchClient.bulk(request.build());
    }

    private static ProductIndexDocument toDocument(ResultSet rs) throws SQLException {
        return new ProductIndexDocument(
                rs.getString("id"),
                rs.getString("merchant_id"),
                rs.getString("category_id"),
                rs.getString("brand"),
                rs.getString("cover_image"),
                rs.getString("title"),
                rs.getString("subtitle"),
                toPlainText(rs.getString("description_html")),
                getNullableLong(rs, "min_price"),
                getNullableLong(rs, "max_price"),
                rs.getLong("sales_count"),
                rs.getString("status"),
                getUtcString(rs, "published_at"),
                getUtcString(rs, "updated_at"),
                rs.getLong("version")
        );
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String getUtcString(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String withoutTags = HTML_TAG.matcher(html).replaceAll(" ");
        String unescaped = HtmlUtils.htmlUnescape(withoutTags);
        return WHITESPACE.matcher(unescaped).replaceAll(" ").trim();
    }

    public record SyncResult(
            String index,
            int total,
            int succeeded,
            int failed
    ) {
    }
}
