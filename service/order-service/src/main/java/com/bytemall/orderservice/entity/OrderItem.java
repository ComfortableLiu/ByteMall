package com.bytemall.orderservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单商品明细，对应 order_item；名称、图片、规格和单价均为下单时快照。
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long spuId;

    private Long skuId;

    private Long merchantId;

    private String productTitle;

    private String productImage;

    /** 规格快照，JSON对象字符串。 */
    private String specSnapshot;

    /** 下单时单价，单位分。 */
    private Long unitPrice;

    private Integer quantity;

    /** 单行总价 = 单价 × 数量，单位分。 */
    private Long totalPrice;

    private LocalDateTime createdAt;
}
