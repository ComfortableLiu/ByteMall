package com.bytemall.orderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.orderservice.entity.OrderIdempotency;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderIdempotencyMapper extends BaseMapper<OrderIdempotency> {
}
