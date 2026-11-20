package com.bytemall.orderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.orderservice.entity.OrderStatusHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderStatusHistoryMapper extends BaseMapper<OrderStatusHistory> {
}
