package com.bytemail.userservice.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemail.userservice.user.entity.Merchant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商家店铺Mapper，负责merchant表的基础数据访问。
 */
@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {

}
