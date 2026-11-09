package com.bytemail.stockservice.merchant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    public MerchantResponses.Stock stock() {
        log.info("stock");
        // TODO 查库存
        return new MerchantResponses.Stock("skuId", 100, 100, 0, "spuId", "productTitle", "productImage", "skuCode", Map.of("color", "red"));
    }

    public MerchantResponses.Stock stockBySkuId(String skuId, Integer availableDelta) {
        log.info("stockBySkuId: {},数量{}", skuId, availableDelta);
        // TODO 查库存
        return new MerchantResponses.Stock(skuId, 100, availableDelta, 0, "spuId", "productTitle", "productImage", "skuCode", Map.of("color", "red"));
    }
}
