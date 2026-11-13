package com.bytemail.userservice.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemail.userservice.user.entity.MerchantApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商家入驻申请Mapper，负责申请记录及审核状态的数据访问。
 */
@Mapper
public interface MerchantApplicationMapper extends BaseMapper<MerchantApplication> {

}
