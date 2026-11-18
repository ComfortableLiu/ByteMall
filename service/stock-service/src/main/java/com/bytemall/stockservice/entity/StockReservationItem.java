package com.bytemall.stockservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_reservation_item")
public class StockReservationItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reservationId;
    private Long skuId;
    private Integer quantity;
    private LocalDateTime createdAt;
}
