package com.bytemall.payservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 支付对账记录，保存渠道账单与本地支付数据的核对结果及差异处理情况。
 */
@Data
@TableName("reconciliation_record")
public class ReconciliationRecord {
    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支付渠道：MOCK、ALIPAY 或 WECHAT。 */
    private String channel;

    /** 渠道交易号；本地有记录但渠道账单缺失时可以为空。 */
    private String channelTradeNo;

    /** 本地支付单号；渠道有记录但本地缺失时可以为空。 */
    private String payNo;

    /** 对账业务日期，与渠道账单的账期口径保持一致。 */
    private LocalDate bizDate;

    /** 渠道账单金额，单位为人民币分；渠道记录缺失时为空。 */
    private Long channelAmount;

    /** 本地支付金额，单位为人民币分；本地记录缺失时为空。 */
    private Long localAmount;

    /** 对账结果：MATCHED、DIFFERENT、MISSING_LOCAL 或 MISSING_CHANNEL。 */
    private String status;

    /** 差异处理结果或人工复核说明。 */
    private String resolution;

    /** 差异处理人标识。 */
    private String resolvedBy;

    /** 差异处理完成时间，数据库统一按UTC存储。 */
    private LocalDateTime resolvedAt;

    /** 对账记录创建时间，数据库统一按UTC存储。 */
    private LocalDateTime createdAt;
}
