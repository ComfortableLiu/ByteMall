package com.bytemall.stockservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.stockservice.entity.StockReservationItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockReservationItemMapper extends BaseMapper<StockReservationItem> {

    @Insert("""
            INSERT INTO stock_reservation_item (reservation_id, sku_id, quantity, created_at)
            VALUES (#{reservationId}, #{skuId}, #{quantity}, UTC_TIMESTAMP(6))
            """)
    int insertItem(StockReservationItem item);

    @Select("""
            SELECT id, reservation_id, sku_id, quantity, created_at
            FROM stock_reservation_item
            WHERE reservation_id = #{reservationId}
            ORDER BY sku_id ASC
            """)
    List<StockReservationItem> selectByReservationId(@Param("reservationId") Long reservationId);
}
