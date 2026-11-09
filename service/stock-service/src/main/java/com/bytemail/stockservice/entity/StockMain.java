package com.bytemail.stockservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_main")
public class StockMain {
    @TableId(value = "sku_id", type = IdType.INPUT)
    private Long skuId;
    private Long merchantId;
    private Integer total;
    private Integer available;
    private Integer reserved;

    @Version
    private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
