package com.bytemall.payservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付渠道交互流水，记录支付、关单、退款及渠道回调的脱敏报文。
 */
@Data
@TableName("payment_transaction")
public class PaymentTransaction {
    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的支付单号。 */
    private String payNo;

    /** 渠道通知ID，用于支付回调幂等去重；非回调流水可以为空。 */
    private String notifyId;

    /** 交易类型：PAY、CLOSE 或 REFUND。 */
    private String transactionType;

    /** 脱敏后的请求报文JSON。 */
    private String requestPayload;

    /** 脱敏后的渠道响应或回调报文JSON。 */
    private String responsePayload;

    /** 验签结果：true表示通过，false表示失败，null表示未验签或无需验签。 */
    private Boolean verifyResult;

    /** 流水创建时间，数据库统一按UTC存储。 */
    private LocalDateTime createdAt;
}
