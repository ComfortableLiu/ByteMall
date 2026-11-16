package com.bytemail.userservice.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemail.userservice.user.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {

    /** 注销时递增令牌版本，使该用户之前签发的所有Token失效。 */
    @Update("UPDATE user_account SET token_version = token_version + 1 WHERE id = #{userId}")
    int incrementTokenVersion(@Param("userId") Long userId);
}
