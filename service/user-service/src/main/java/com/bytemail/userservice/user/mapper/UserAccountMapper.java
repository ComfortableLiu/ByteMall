package com.bytemail.userservice.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemail.userservice.user.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {

}
