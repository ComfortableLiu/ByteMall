package com.bytemail.stockservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemail.stockservice.entity.StockMain;
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
                version = version + 1
            WHERE sku_id = #{skuId}
              AND version = #{version}
              AND available >= #{quantity}
            """)
    int reserveStock(@Param("skuId") Long skuId,
                     @Param("quantity") Integer quantity,
                     @Param("version") Long version);

    @Update("""
            UPDATE stock_main
            SET total = total - #{quantity},
                reserved = reserved - #{quantity},
                version = version + 1
            WHERE sku_id = #{skuId}
              AND version = #{version}
              AND reserved >= #{quantity}
              AND total >= #{quantity}
            """)
    int confirmReservation(@Param("skuId") Long skuId,
                           @Param("quantity") Integer quantity,
                           @Param("version") Long version);

    @Update("""
            UPDATE stock_main
            SET available = available + #{quantity},
                reserved = reserved - #{quantity},
                version = version + 1
            WHERE sku_id = #{skuId}
              AND version = #{version}
              AND reserved >= #{quantity}
            """)
    int releaseReservation(@Param("skuId") Long skuId,
                           @Param("quantity") Integer quantity,
                           @Param("version") Long version);

    @Update("""
            UPDATE stock_main
            SET available = available + #{availableDelta},
                total = total + #{availableDelta},
                version = version + 1
            WHERE sku_id = #{skuId}
              AND merchant_id = #{merchantId}
              AND version = #{version}
              AND available + #{availableDelta} >= 0
              AND total + #{availableDelta} >= 0
            """)
    int adjustAvailableStock(@Param("skuId") Long skuId,
                             @Param("merchantId") Long merchantId,
                             @Param("availableDelta") Integer availableDelta,
                             @Param("version") Long version);
}
