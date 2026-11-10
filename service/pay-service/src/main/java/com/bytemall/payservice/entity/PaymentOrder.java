package com.bytemall.payservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bytemall.payservice.payments.PaymentChannel;
import com.bytemall.payservice.payments.PaymentStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付单，记录订单的一次支付过程及最终支付状态。
 */
@Data
@TableName("payment_order")
public class PaymentOrder {
    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支付单号，全局唯一，用于对外查询支付单。 */
    private String payNo;

    /** 关联的业务订单号；订单对应的旧支付单关闭或失败后允许重新创建支付单。 */
    private String orderNo;

    /** 买家用户ID。 */
    private Long userId;

    /** 支付渠道：MOCK、ALIPAY 或 WECHAT。 */
    private PaymentChannel channel;

    /** 支付金额，单位为人民币分。 */
    private Long amount;

    /** 支付状态：PENDING、SUCCESS、FAILED 或 CLOSED。 */
    private PaymentStatus status;

    /** 支付渠道返回的交易号，支付渠道未返回时为空。 */
    private String channelTradeNo;

    /** 支付截止时间，数据库统一按UTC存储。 */
    private LocalDateTime expireAt;

    /** 支付成功时间，未支付成功时为空，数据库统一按UTC存储。 */
    private LocalDateTime paidAt;

    /** 乐观锁版本号，防止并发更新覆盖。 */
    @Version
    private Long version;

    /** 创建时间，数据库统一按UTC存储。 */
    private LocalDateTime createdAt;

    /** 最后更新时间，数据库统一按UTC存储。 */
    private LocalDateTime updatedAt;
}
