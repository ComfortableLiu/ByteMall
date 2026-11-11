package com.bytemall.payservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.payservice.entity.PaymentTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentTransactionMapper extends BaseMapper<PaymentTransaction> {

    @Select("""
            SELECT id, pay_no, notify_id, transaction_type, request_payload,
                   response_payload, verify_result, created_at
            FROM payment_transaction
            WHERE notify_id = #{notifyId}
            """)
    PaymentTransaction selectByNotifyId(@Param("notifyId") String notifyId);
}
