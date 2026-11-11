package com.bytemall.searchservice.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductChangedListener {

    private final ObjectMapper objectMapper;
    private final ProductIndexSyncService productIndexSyncService;

    @KafkaListener(
            id = "productIndexListener",
            groupId = "${spring.kafka.consumer.group-id:search-service-consumer-group-v1}",
            topics = "${bytemall.kafka.topics.product-changed:product-changed}"
    )
    public void onProductChanged(String message, Acknowledgment acknowledgment) throws IOException {
        ProductChangedEvent event = objectMapper.readValue(message, ProductChangedEvent.class);
        if (!"ProductChanged".equals(event.eventType())) {
            log.warn("忽略非商品变更事件，eventId={}，eventType={}", event.eventId(), event.eventType());
            acknowledgment.acknowledge();
            return;
        }

        log.info("收到商品变更事件，eventId={}，spuId={}，version={}",
                event.eventId(), event.aggregateId(), event.version());
        productIndexSyncService.synchronizeBySpuId(event.aggregateId());
        acknowledgment.acknowledge();
        log.info("商品变更事件处理完成，eventId={}，spuId={}", event.eventId(), event.aggregateId());
    }
}
