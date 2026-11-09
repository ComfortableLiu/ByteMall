package com.bytemail.stockservice.stocks;

import com.bytemail.common.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/stocks")
@RequiredArgsConstructor
public class StocksController {
    private final StocksService stocksService;

    @GetMapping("/sku/{skuId}")
    public ApiResponse<StocksResponses.Sku> getStockBySkuId(@PathVariable Long skuId) {
        return ApiResponse.ok(stocksService.getStockBySkuId(skuId));
    }
}
