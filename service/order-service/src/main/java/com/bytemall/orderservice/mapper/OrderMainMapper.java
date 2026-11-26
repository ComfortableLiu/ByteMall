package com.bytemall.orderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.orderservice.entity.OrderMain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMainMapper extends BaseMapper<OrderMain> {

    /** 扫描已过支付截止时间的待支付订单，按到期时间升序限量返回。 */
    @Select("""
            SELECT order_no
            FROM order_main
            WHERE status = 'PENDING_PAYMENT'
              AND expire_at < UTC_TIMESTAMP(6)
            ORDER BY expire_at ASC
            LIMIT #{limit}
            """)
    List<String> selectExpiredOrderNos(@Param("limit") int limit);

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

    /** 仅待发货订单可发货，写入物流信息并递增版本，返回0表示状态已变化。 */
    @Update("""
            UPDATE order_main
            SET status = 'SHIPPED',
                carrier = #{carrier},
                tracking_no = #{trackingNo},
                shipped_at = #{shippedAt},
                updated_at = #{shippedAt},
                version = version + 1
            WHERE order_no = #{orderNo}
              AND status = 'PENDING_SHIPMENT'
            """)
    int shipOrder(@Param("orderNo") String orderNo,
                  @Param("carrier") String carrier,
                  @Param("trackingNo") String trackingNo,
                  @Param("shippedAt") LocalDateTime shippedAt);
}
