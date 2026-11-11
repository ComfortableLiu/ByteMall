package com.bytemall.payservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.payservice.entity.RefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {

    @Select("""
            SELECT id, refund_no, pay_no, order_no, amount, reason, status,
                   channel_refund_no, version, refunded_at, created_at, updated_at
            FROM refund_order
            WHERE refund_no = #{refundNo}
            """)
    RefundOrder selectByRefundNo(@Param("refundNo") String refundNo);

    @Select("""
            SELECT id, refund_no, pay_no, order_no, amount, reason, status,
                   channel_refund_no, version, refunded_at, created_at, updated_at
            FROM refund_order
            WHERE refund_no = #{refundNo}
            FOR UPDATE
            """)
    RefundOrder selectByRefundNoForUpdate(@Param("refundNo") String refundNo);
}
