package com.bytemall.stockservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存变化流水，(reservationNo, skuId, operation)唯一键是防重复变更的最终屏障。
 */
@Data
@TableName("stock_log")
public class StockLog {
    public enum Operation {
        RESERVE,
        CONFIRM,
        RELEASE,
        ADJUST
    }

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private String reservationNo;
    private Operation operation;
    private Integer availableDelta;
    private Integer reservedDelta;
    private Integer beforeAvailable;
    private Integer afterAvailable;
    /** 调整原因，仅ADJUST操作记录，其他操作为空。 */
    private String reason;
    private Long operatorId;
    private String requestId;
    private LocalDateTime createdAt;
}
