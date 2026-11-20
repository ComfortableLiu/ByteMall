package com.bytemall.stockservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存预扣单，数据库时间字段统一按UTC存储。
 */
@Data
@TableName("stock_reservation")
public class StockReservation {
    public enum Status {
        RESERVED,
        CONFIRMED,
        RELEASED
    }

    @TableId(type = IdType.AUTO)
    private Long id;
    private String reservationNo;
    private String orderNo;
    private Status status;
    private String reason;
    private String requestHash;
    private LocalDateTime expireAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
