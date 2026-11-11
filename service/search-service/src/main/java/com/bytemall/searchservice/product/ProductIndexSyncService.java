package com.bytemall.searchservice.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexSyncService {

    private final ElasticsearchClient elasticsearchClient;

    public void synchronizeBySpuId(String spuId) throws IOException {
        List<ProductIndexDocument> documents = null;
        // TODO 这里去查一下商品服务
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("商品不存在，spuId=" + spuId);
        }

        ProductIndexDocument document = documents.get(0);
        elasticsearchClient.index(index -> index
                .index(ProductIndexService.INDEX)
                .id(document.spuId())
                .document(document)
                .refresh(Refresh.WaitFor)
        );
        log.info("单商品索引同步完成，spuId={}", spuId);
    }
}
