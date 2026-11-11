package com.bytemall.productservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.productservice.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}