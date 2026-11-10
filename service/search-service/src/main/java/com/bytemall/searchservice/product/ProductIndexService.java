package com.bytemall.searchservice.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
// TODO 索引服务，但是目前没有部署ES，所以只是先写出来
public class ProductIndexService {

    public static final String INDEX = "product-search-v1";
    public static final String ALIAS = "product-search";

    private final ElasticsearchClient elasticsearchClient;

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexIfAbsent() throws IOException {
        log.info("-------Checking if Elasticsearch index {} exists...", INDEX);
        boolean exists = elasticsearchClient.indices()
                .exists(request -> request.index(INDEX))
                .value();

        log.info("Elasticsearch index {} exists: {}", INDEX, exists);
        if (exists) {
            return;
        }

        log.info("Creating Elasticsearch index {}", INDEX);
        elasticsearchClient.indices().create(request -> request
                .index(INDEX)
                .mappings(mapping -> mapping
                        .properties("spuId", property -> property.keyword(value -> value))
                        .properties("merchantId", property -> property.keyword(value -> value))
                        .properties("categoryId", property -> property.keyword(value -> value))
                        .properties("brand", property -> property.keyword(value -> value))
                        .properties("coverImage", property -> property.keyword(value -> value))
                        .properties("title", property -> property.text(value -> value
                                .analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")
                        ))
                        .properties("subtitle", property -> property.text(value -> value
                                .analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")
                        ))
                        .properties("descriptionText", property -> property.text(value -> value
                                .analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")
                        ))
                        .properties("minPrice", property -> property.integer(value -> value))
                        .properties("maxPrice", property -> property.integer(value -> value))
                        .properties("salesCount", property -> property.integer(value -> value))
                        .properties("status", property -> property.keyword(value -> value))
                        .properties("publishedAt", property -> property.date(value -> value))
                        .properties("updatedAt", property -> property.date(value -> value))
                        .properties("version", property -> property.long_(value -> value))
                )
        );
        log.info("Creating Elasticsearch alias {} for index {}", ALIAS, INDEX);

        elasticsearchClient.indices().putAlias(request -> request
                .index(INDEX)
                .name(ALIAS)
        );
        log.info("Created Elasticsearch alias {} for index {}", ALIAS, INDEX);
    }
}
