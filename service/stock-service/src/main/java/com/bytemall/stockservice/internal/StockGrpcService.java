package com.bytemall.stockservice.internal;

import com.bytemall.stock.proto.AvailableStock;
import com.bytemall.stock.proto.GetAvailableStocksRequest;
import com.bytemall.stock.proto.GetAvailableStocksResponse;
import com.bytemall.stock.proto.StockServiceGrpc;
import com.bytemall.stockservice.entity.StockMain;
import com.bytemall.stockservice.mapper.StockMainMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存gRPC服务端实现，接收客户端请求并返回数据库中的最新可售库存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockGrpcService extends StockServiceGrpc.StockServiceImplBase {

    private final StockMainMapper stockMainMapper;

    /**
     * gRPC调用入口：正常时返回响应并结束调用，参数错误时返回INVALID_ARGUMENT。
     */
    @Override
    public void getAvailableStocks(
            GetAvailableStocksRequest request,
            StreamObserver<GetAvailableStocksResponse> responseObserver
    ) {
        try {
            responseObserver.onNext(queryAvailableStocks(request));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * 批量查询库存：去重后一次查询数据库，找不到的SKU单独放入missingSkuIds。
     */
    private GetAvailableStocksResponse queryAvailableStocks(GetAvailableStocksRequest request) {
        if (request.getSkuIdsCount() == 0 || request.getSkuIdsCount() > 100) {
            throw new IllegalArgumentException("skuIds必须包含1到100项");
        }

        Set<Long> skuIds = request.getSkuIdsList().stream()
                .map(StockGrpcService::parseSkuId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, StockMain> stocksById = stockMainMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(StockMain::getSkuId, Function.identity()));

        GetAvailableStocksResponse.Builder response = GetAvailableStocksResponse.newBuilder();
        List<String> missingSkuIds = new ArrayList<>();
        for (Long skuId : skuIds) {
            StockMain stock = stocksById.get(skuId);
            if (stock == null) {
                missingSkuIds.add(skuId.toString());
                continue;
            }
            response.addItems(AvailableStock.newBuilder()
                    .setSkuId(skuId.toString())
                    .setAvailableStock(stock.getAvailable())
                    .setVersion(stock.getVersion())
                    .build());
        }
        response.addAllMissingSkuIds(missingSkuIds);
        log.info("Getting available stocks for {} SKUs, found {}, missing {}",
                skuIds.size(), response.getItemsCount(), missingSkuIds.size());
        return response.build();
    }

    /**
     * 将外部字符串ID安全转换为Long，同时拦截非数字和超出Long范围的输入。
     */
    private static long parseSkuId(String skuId) {
        if (skuId == null || !skuId.matches("\\d{1,19}")) {
            throw new IllegalArgumentException("skuId必须为1到19位数字字符串");
        }
        try {
            return Long.parseLong(skuId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("skuId超出Long范围: " + skuId, exception);
        }
    }
}
