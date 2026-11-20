package com.bytemall.searchservice.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** 商品变更索引同步：从商品服务拉取读模型后写入ES，商品已删除时清理索引条目。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexSyncService {

    private final ElasticsearchClient elasticsearchClient;
    private final ProductIndexClient productIndexClient;

    public void synchronizeBySpuId(String spuId) throws IOException {
        ProductIndexDocument document = productIndexClient.getIndexDocument(spuId);
        if (document == null) {
            // 商品已不存在时删除索引条目，避免搜索继续命中幽灵商品；删除失败只告警不阻断。
            log.warn("商品{}不存在，清理对应索引条目", spuId);
            try {
                elasticsearchClient.delete(index -> index
                        .index(ProductIndexService.INDEX)
                        .id(spuId)
                        .refresh(Refresh.WaitFor));
            } catch (Exception exception) {
                log.warn("删除索引条目失败，spuId={}", spuId, exception);
            }
            return;
        }

        elasticsearchClient.index(index -> index
                .index(ProductIndexService.INDEX)
                .id(document.spuId())
                .document(document)
                .refresh(Refresh.WaitFor)
        );
        log.info("单商品索引同步完成，spuId={}", spuId);
    }
}
