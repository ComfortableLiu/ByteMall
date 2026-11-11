package com.bytemall.payservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.payservice.entity.InboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboxEventMapper extends BaseMapper<InboxEvent> {
}
