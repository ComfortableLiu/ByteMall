package com.bytemall.payservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付服务事件收件箱，记录消费者已处理的事件，避免MQ重复投递造成重复处理。
 */
@Data
@TableName("inbox_event")
public class InboxEvent {
    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局事件ID，与消费者名称组成唯一约束。 */
    private String eventId;

    /** 消费者名称，用于区分不同业务消费者对同一事件的处理。 */
    private String consumer;

    /** 事件报文的SHA-256摘要，用于发现事件ID相同但内容不一致的异常情况。 */
    private String payloadHash;

    /** 事件处理完成时间，数据库统一按UTC存储。 */
    private LocalDateTime processedAt;
}
