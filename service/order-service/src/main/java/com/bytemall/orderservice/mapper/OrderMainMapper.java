package com.bytemall.orderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.orderservice.entity.OrderMain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderMainMapper extends BaseMapper<OrderMain> {

    /** 仅待支付订单可取消，条件更新挡住并发的取消/支付，返回0表示状态已变化。 */
    @Update("""
            UPDATE order_main
            SET status = 'CANCELLED',
                cancel_reason = #{reason},
                cancelled_at = #{cancelledAt},
                updated_at = #{cancelledAt},
                version = version + 1
            WHERE order_no = #{orderNo}
              AND status = 'PENDING_PAYMENT'
            """)
    int cancelPending(@Param("orderNo") String orderNo,
                      @Param("reason") String reason,
                      @Param("cancelledAt") LocalDateTime cancelledAt);

    /** 仅已发货订单可确认收货，条件更新挡住并发的状态迁移，返回0表示状态已变化。 */
    @Update("""
            UPDATE order_main
            SET status = 'COMPLETED',
                completed_at = #{completedAt},
                updated_at = #{completedAt},
                version = version + 1
            WHERE order_no = #{orderNo}
              AND status = 'SHIPPED'
            """)
    int confirmShipped(@Param("orderNo") String orderNo,
                       @Param("completedAt") LocalDateTime completedAt);
}
