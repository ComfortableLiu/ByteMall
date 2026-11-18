package com.bytemall.stockservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.stockservice.entity.StockMain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StockMainMapper extends BaseMapper<StockMain> {

    @Select("""
            SELECT sku_id, merchant_id, total, available, reserved, version, created_at, updated_at
            FROM stock_main
            WHERE sku_id = #{skuId}
            FOR UPDATE
            """)
    StockMain selectBySkuIdForUpdate(@Param("skuId") Long skuId);

    @Update("""
            UPDATE stock_main
            SET available = available - #{quantity},
                reserved = reserved + #{quantity},
                updated_at = UTC_TIMESTAMP(6),
                version = version + 1
            WHERE sku_id = #{skuId}
              AND version = #{version}
              AND available >= #{quantity}
              AND available >= 0
              AND reserved >= 0
              AND total = available + reserved
            """)
    int reserveStock(@Param("skuId") Long skuId,
                     @Param("quantity") Integer quantity,
                     @Param("version") Long version);

    @Update("""
            UPDATE stock_main
            SET total = total - #{quantity},
                reserved = reserved - #{quantity},
                updated_at = UTC_TIMESTAMP(6),
                version = version + 1
            WHERE sku_id = #{skuId}
              AND version = #{version}
              AND reserved >= #{quantity}
              AND total >= #{quantity}
              AND available >= 0
              AND total = available + reserved
            """)
    int confirmReservation(@Param("skuId") Long skuId,
                           @Param("quantity") Integer quantity,
                           @Param("version") Long version);

    @Update("""
            UPDATE stock_main
            SET available = available + #{quantity},
                reserved = reserved - #{quantity},
                updated_at = UTC_TIMESTAMP(6),
                version = version + 1
            WHERE sku_id = #{skuId}
              AND version = #{version}
              AND reserved >= #{quantity}
              AND available >= 0
              AND total = available + reserved
            """)
    int releaseReservation(@Param("skuId") Long skuId,
                           @Param("quantity") Integer quantity,
                           @Param("version") Long version);

    @Update("""
            UPDATE stock_main
            SET available = available + #{availableDelta},
                total = total + #{availableDelta},
                updated_at = UTC_TIMESTAMP(6),
                version = version + 1
            WHERE sku_id = #{skuId}
              AND merchant_id = #{merchantId}
              AND version = #{version}
              AND available + #{availableDelta} >= 0
              AND total + #{availableDelta} >= 0
              AND reserved >= 0
              AND total = available + reserved
            """)
    int adjustAvailableStock(@Param("skuId") Long skuId,
                             @Param("merchantId") Long merchantId,
                             @Param("availableDelta") Integer availableDelta,
                             @Param("version") Long version);
}
