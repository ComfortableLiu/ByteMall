package com.bytemall.payservice.merchant;

import com.bytemall.payservice.entity.ReconciliationRecord;
import com.bytemall.payservice.mapper.ReconciliationRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final ReconciliationRecordMapper reconciliationRecordMapper;

    /**
     * 查询指定账期的差异记录，没有差异时返回空列表。
     */
    public List<MerchantResponses.Reconciliations> reconciliations(LocalDate bizDate) {
        // TODO 接入商家认证及订单商家归属后，按merchantId过滤，防止跨商家查询对账数据。
        return reconciliationRecordMapper.selectDifferencesByBizDate(bizDate).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 将数据库记录映射为商家端DTO，保留缺失记录的空支付单号、交易号和金额。 */
    private MerchantResponses.Reconciliations toResponse(ReconciliationRecord record) {
        return new MerchantResponses.Reconciliations(
                record.getChannel(),
                record.getChannelTradeNo(),
                record.getPayNo(),
                record.getBizDate(),
                record.getChannelAmount(),
                record.getLocalAmount(),
                record.getStatus()
        );
    }
}
