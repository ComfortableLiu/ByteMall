package com.bytemall.common.model.constants;

/**
 * Kafka 主题常量
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /**
     * 搜索服务消费者组
     */
    public static final String SEARCH_SERVICE_CONSUMER_GROUP_V1 = "search-service-consumer-group-v1";

    /**
     * 商品信息变更主题
     */
    public static final String PRODUCT_CHANGED = "product-changed";
}
