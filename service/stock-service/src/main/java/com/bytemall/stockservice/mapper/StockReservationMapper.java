package com.bytemall.stockservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.stockservice.entity.StockReservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface StockReservationMapper extends BaseMapper<StockReservation> {

    /**
     * 唯一键冲突时不覆盖首次请求，调用方随后锁定并核对原预扣单。
     */
    @Insert("""
            INSERT IGNORE INTO stock_reservation
                (reservation_no, order_no, status, request_hash, expire_at, created_at, updated_at)
            VALUES
                (#{reservationNo}, #{orderNo}, 'RESERVED', #{requestHash}, #{expireAt},
                 UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """)
    int insertIgnore(StockReservation reservation);

    @Select("""
            SELECT id, reservation_no, order_no, status, reason, request_hash,
                   expire_at, confirmed_at, released_at, created_at, updated_at
            FROM stock_reservation
            WHERE reservation_no = #{reservationNo}
            FOR UPDATE
            """)
    StockReservation selectByReservationNoForUpdate(@Param("reservationNo") String reservationNo);

    @Update("""
            UPDATE stock_reservation
            SET status = 'CONFIRMED',
                confirmed_at = UTC_TIMESTAMP(6),
                updated_at = UTC_TIMESTAMP(6)
            WHERE id = #{id}
              AND status = 'RESERVED'
            """)
    int confirmReserved(@Param("id") Long id);

    @Update("""
            UPDATE stock_reservation
            SET status = 'RELEASED',
                reason = #{reason},
                released_at = UTC_TIMESTAMP(6),
                updated_at = UTC_TIMESTAMP(6)
            WHERE id = #{id}
              AND status = 'RESERVED'
            """)
    int releaseReserved(@Param("id") Long id, @Param("reason") String reason);

    /**
     * 只负责限量取出候选单号，实际状态由释放事务锁定后再次核验。
     */
    @Select("""
            SELECT reservation_no
            FROM stock_reservation
            WHERE status = 'RESERVED'
              AND expire_at <= UTC_TIMESTAMP(6)
            ORDER BY expire_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<String> selectExpiredReservationNos(@Param("limit") int limit);
}
