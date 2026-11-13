package com.bytemall.payservice.merchant;

import com.bytemall.common.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    /** 按账单日期查询对账差异，日期格式为 yyyy-MM-dd。 */
    @GetMapping("/reconciliations")
    public ApiResponse<List<MerchantResponses.Reconciliations>> reconciliations(
            @RequestParam LocalDate bizDate
    ) {
        return ApiResponse.ok(merchantService.reconciliations(bizDate));
    }
}
