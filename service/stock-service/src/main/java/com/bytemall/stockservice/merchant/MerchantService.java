package com.bytemall.stockservice.merchant;

import com.bytemall.stockservice.client.ProductCatalogClient;
import com.bytemall.stockservice.client.UserContextClient;
import com.bytemall.stockservice.entity.StockMain;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantService {
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private final UserContextClient userContextClient;
    private final ProductCatalogClient productCatalogClient;
    private final MerchantStockService merchantStockService;

    public List<MerchantResponses.MerchantStockItem> listStocks(Long userId, boolean lowStock) {
        Long merchantId = userContextClient.requireActiveMerchant(userId);
        List<StockMain> stocks = merchantStockService.listByMerchant(merchantId, lowStock);
        Map<String, ProductCatalogClient.SkuSummary> summaries = productCatalogClient.getSkuBriefs(
                stocks.stream().map(StockMain::getSkuId).toList());
        return stocks.stream()
                .map(stock -> toResponse(stock, summaries.get(stock.getSkuId().toString())))
                .toList();
    }

    public MerchantResponses.MerchantStockItem adjustStock(
            Long userId,
            String skuId,
            MerchantRequests.AdjustStockRequest request,
            String requestId
    ) {
        Long merchantId = userContextClient.requireActiveMerchant(userId);
        Long parsedSkuId = parseSkuId(skuId);

        // 商品调用放在库存事务前，避免调整已提交但摘要查询失败而误导客户端重试。
        ProductCatalogClient.SkuSummary summary = productCatalogClient
                .getSkuBriefs(List.of(parsedSkuId))
                .get(parsedSkuId.toString());
        StockMain adjusted = merchantStockService.adjust(
                parsedSkuId,
                merchantId,
                userId,
                request.availableDelta(),
                request.version(),
                normalizeRequestId(requestId)
        );
        return toResponse(adjusted, summary);
    }

    private static MerchantResponses.MerchantStockItem toResponse(
            StockMain stock,
            ProductCatalogClient.SkuSummary summary
    ) {
        ProductCatalogClient.SkuSummary catalog = summary == null
                ? ProductCatalogClient.SkuSummary.empty()
                : summary;
        return new MerchantResponses.MerchantStockItem(
                stock.getSkuId().toString(),
                stock.getTotal(),
                stock.getAvailable(),
                stock.getReserved(),
                stock.getVersion(),
                catalog.spuId(),
                catalog.productTitle(),
                catalog.productImage(),
                catalog.skuCode(),
                catalog.specs()
        );
    }

    private static Long parseSkuId(String skuId) {
        if (skuId == null || !skuId.matches("\\d{1,19}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "skuId必须为正整数");
        }
        try {
            long parsed = Long.parseLong(skuId);
            if (parsed <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "skuId必须为正整数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "skuId超出支持范围", exception);
        }
    }

    private static String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = requestId.trim();
        if (normalized.length() > MAX_REQUEST_ID_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "X-Request-Id长度不能超过" + MAX_REQUEST_ID_LENGTH + "个字符"
            );
        }
        return normalized;
    }
}
