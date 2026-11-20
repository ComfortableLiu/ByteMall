package com.bytemall.stockservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.stockservice.entity.StockLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockLogMapper extends BaseMapper<StockLog> {

    @Insert("""
            INSERT INTO stock_log
                (sku_id, reservation_no, operation, available_delta, reserved_delta,
                 before_available, after_available, reason, operator_id, request_id, created_at)
            VALUES
                (#{skuId}, #{reservationNo}, #{operation}, #{availableDelta}, #{reservedDelta},
                 #{beforeAvailable}, #{afterAvailable}, #{reason}, #{operatorId}, #{requestId}, UTC_TIMESTAMP(6))
            """)
    int insertLog(StockLog stockLog);
}
