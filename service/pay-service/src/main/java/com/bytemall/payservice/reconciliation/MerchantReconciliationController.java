package com.bytemall.payservice.reconciliation;

import com.bytemall.common.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/merchant/reconciliations")
@RequiredArgsConstructor
public class MerchantReconciliationController {

    private final ReconciliationService reconciliationService;

    @GetMapping
    public ApiResponse<List<ReconciliationResponses.ReconciliationRecord>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate
    ) {
        return ApiResponse.ok(reconciliationService.listMerchantRecords(bizDate));
    }
}
