package com.bytemall.payservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退款单，独立跟踪一次退款过程，不改变原支付单的最终状态。
 */
@Data
@TableName("refund_order")
public class RefundOrder {
    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退款单号，全局唯一。 */
    private String refundNo;

    /** 原支付单号。 */
    private String payNo;

    /** 关联的业务订单号。 */
    private String orderNo;

    /** 本次退款金额，单位为人民币分。 */
    private Long amount;

    /** 退款原因。 */
    private String reason;

    /** 退款状态：PENDING、SUCCESS 或 FAILED。 */
    private String status;

    /** 支付渠道返回的退款单号，未返回时为空。 */
    private String channelRefundNo;

    /** 退款成功时间，未退款成功时为空，数据库统一按UTC存储。 */
    private LocalDateTime refundedAt;

    /** 乐观锁版本号，防止并发更新覆盖。 */
    @Version
    private Long version;

    /** 创建时间，数据库统一按UTC存储。 */
    private LocalDateTime createdAt;

    /** 最后更新时间，数据库统一按UTC存储。 */
    private LocalDateTime updatedAt;
}
