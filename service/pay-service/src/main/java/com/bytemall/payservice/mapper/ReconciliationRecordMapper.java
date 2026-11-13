package com.bytemall.payservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytemall.payservice.entity.ReconciliationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReconciliationRecordMapper extends BaseMapper<ReconciliationRecord> {

    /**
     * 查询指定账期的差异记录，正常记录不占用商家端差异列表。
     */
    @Select("""
            SELECT id, channel, channel_trade_no, pay_no, biz_date,
                   channel_amount, local_amount, status, resolution,
                   resolved_by, resolved_at, created_at
            FROM reconciliation_record
            WHERE biz_date = #{bizDate}
              AND status <> 'MATCHED'
            ORDER BY created_at DESC, id DESC
            """)
    List<ReconciliationRecord> selectDifferencesByBizDate(@Param("bizDate") LocalDate bizDate);
}
