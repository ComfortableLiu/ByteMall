package com.bytemall.payservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.payservice.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {
}
