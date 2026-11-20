package com.bytemall.orderservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bytemall.orderservice.orders.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单主表，对应 order_main；一个订单只属于一个商家。
 */
@Data
@TableName("order_main")
public class OrderMain {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long merchantId;

    private OrderStatus status;

    /** 商品金额合计，单位分。 */
    private Long itemAmount;

    /** 运费，单位分。 */
    private Long freightAmount;

    /** 优惠金额，单位分；当前默认0。 */
    private Long discountAmount;

    /** 应付金额 = 商品金额 + 运费 - 优惠，单位分。 */
    private Long payableAmount;

    /** 下单时的地址快照，JSON字符串，不随地址簿变化。 */
    private String addressSnapshot;

    private String remark;

    private String cancelReason;

    private String carrier;

    private String trackingNo;

    /** 待支付截止时间，按UTC存储。 */
    private LocalDateTime expireAt;

    private LocalDateTime paidAt;

    private LocalDateTime shippedAt;

    private LocalDateTime completedAt;

    private LocalDateTime cancelledAt;

    /** 状态条件更新使用的版本号。 */
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
