package com.bytemall.payservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付服务事件发件箱，与业务数据在同一数据库事务中写入，供后台任务可靠投递到MQ。
 */
@Data
@TableName("outbox_event")
public class OutboxEvent {
    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一事件ID，用于消息追踪和消费者幂等去重。 */
    private String eventId;

    /** 事件类型，例如PaymentSucceeded。 */
    private String eventType;

    /** 聚合类型，例如PAYMENT。 */
    private String aggregateType;

    /** 聚合业务标识，例如支付单号。 */
    private String aggregateId;

    /** 聚合版本，用于消费者识别乱序或过期事件。 */
    private Long aggregateVersion;

    /** 事件报文JSON，只包含事件契约允许的字段。 */
    private String payload;

    /** 发布状态：NEW、PUBLISHED 或 FAILED。 */
    private String status;

    /** 发布失败后的已重试次数。 */
    private Integer retryCount;

    /** 下次允许重试的时间，数据库统一按UTC存储。 */
    private LocalDateTime nextRetryAt;

    /** 业务事件发生时间，数据库统一按UTC存储。 */
    private LocalDateTime occurredAt;

    /** 成功发布到MQ的时间，未发布时为空，数据库统一按UTC存储。 */
    private LocalDateTime publishedAt;
}
