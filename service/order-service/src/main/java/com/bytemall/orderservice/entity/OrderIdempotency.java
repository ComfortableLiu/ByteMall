package com.bytemall.orderservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单创建幂等记录，对应 order_idempotency；唯一键 (user_id, idempotency_key)。
 */
@Data
@TableName("order_idempotency")
public class OrderIdempotency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String idempotencyKey;

    /** 请求体规范化后的SHA-256指纹。 */
    private String requestHash;

    /** 幂等命中后返回的订单号。 */
    private String resourceId;

    /** 首次响应的HTTP状态码。 */
    private Integer responseStatus;

    /** 首次响应体快照，JSON字符串。 */
    private String responseSnapshot;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;
}
