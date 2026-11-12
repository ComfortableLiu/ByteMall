package com.bytemall.payservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.payservice.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    @Select("""
            SELECT id, pay_no, order_no, user_id, channel, amount, status,
                   channel_trade_no, expire_at, paid_at, version, created_at, updated_at
            FROM payment_order
            WHERE pay_no = #{payNo}
            """)
    PaymentOrder selectByPayNo(@Param("payNo") String payNo);

    @Select("""
            SELECT id, pay_no, order_no, user_id, channel, amount, status,
                   channel_trade_no, expire_at, paid_at, version, created_at, updated_at
            FROM payment_order
            WHERE pay_no = #{payNo}
            FOR UPDATE
            """)
    PaymentOrder selectByPayNoForUpdate(@Param("payNo") String payNo);

    /**
     * 查询订单最新的支付单并加行锁，调用方必须开启事务。
     */
    @Select("""
            SELECT id, pay_no, order_no, user_id, channel, amount, status,
                   channel_trade_no, expire_at, paid_at, version, created_at, updated_at
            FROM payment_order
            WHERE order_no = #{orderNo}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            FOR UPDATE
            """)
    PaymentOrder selectLatestByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Update("""
            UPDATE payment_order
            SET status = 'SUCCESS',
                channel_trade_no = #{channelTradeNo},
                paid_at = #{paidAt},
                version = version + 1
            WHERE pay_no = #{payNo}
              AND status = 'PENDING'
            """)
    int markPaymentSucceeded(@Param("payNo") String payNo,
                             @Param("channelTradeNo") String channelTradeNo,
                             @Param("paidAt") LocalDateTime paidAt);

    @Update("""
            UPDATE payment_order
            SET status = 'CLOSED',
                updated_at = #{closedAt},
                version = version + 1
            WHERE pay_no = #{payNo}
              AND status = 'PENDING'
            """)
    int closePendingPayment(@Param("payNo") String payNo,
                            @Param("closedAt") LocalDateTime closedAt);
}
