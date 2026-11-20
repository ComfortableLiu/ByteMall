package com.bytemall.orderservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流转历史，对应 order_status_history。
 */
@Data
@TableName("order_status_history")
public class OrderStatusHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 创建订单时无来源状态，存空字符串。 */
    private String fromStatus;

    private String toStatus;

    /** BUYER、MERCHANT、SYSTEM 或 EVENT。 */
    private String operatorType;

    private Long operatorId;

    private String reason;

    private String requestId;

    private String eventId;

    private LocalDateTime createdAt;
}
