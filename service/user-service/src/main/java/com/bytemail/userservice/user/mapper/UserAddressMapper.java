package com.bytemail.userservice.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemail.userservice.user.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户收货地址Mapper，继承BaseMapper后可直接使用基础增删改查。
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {

}
