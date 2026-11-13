package com.bytemall.payservice.merchant;

import java.time.LocalDate;

public final class MerchantResponses {

    private MerchantResponses() {
    }

    /**
     * 商家对账差异响应。金额单位为分，任一侧记录缺失时对应金额为空。
     */
    public record Reconciliations(
            String channel,
            String channelTradeNo,
            String payNo,
            LocalDate bizDate,
            Long channelAmount,
            Long localAmount,
            String status
    ) {
    }
}
