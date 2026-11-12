package com.bytemall.productservice.products;

import com.bytemall.stock.proto.GetAvailableStocksRequest;
import com.bytemall.stock.proto.StockServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


//接收一组 SKU ID，通过 gRPC 向库存服务查询每个 SKU 的可售数量，再把结果整理成 Map<Long, Integer>
@Component // 让 Spring 扫描并注册这个客户端，供 ProductService 注入。
public class ProductStockClient {
    private static final int BATCH_SIZE = 100;// 库存接口每次最多接受 100 个 SKU ID。

    private final ManagedChannel channel;// 保存到库存服务的通信通道，供多次调用复用。
    private final StockServiceGrpc.StockServiceBlockingStub stub;// 保存阻塞式客户端，调用时等待响应或错误。

    public ProductStockClient(// Spring 创建客户端时调用这个构造器。
            @Value("${stock.grpc.host:localhost}") String host,// 读取库存服务地址，默认 localhost。
            @Value("${stock.grpc.port:9091}") int port// 读取库存服务的 gRPC 端口，默认 9091。
    ) {
        channel = ManagedChannelBuilder.forAddress(host, port)// 指定库存服务的地址和端口。
                .usePlaintext()// 使用明文通信，关闭该通道的 TLS。
                .build();// 创建可复用的 gRPC 通道。
        stub = StockServiceGrpc.newBlockingStub(channel);// 基于该通道创建阻塞式 Stub。
    }

    public Map<Long, Integer> getAvailableStocks(List<Long> skuIds) {// 接收 SKU ID 列表，返回每个 SKU 的可售数量。
        Map<Long, Integer> stocks = new HashMap<>();
        for (int start = 0; start < skuIds.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, skuIds.size());// 计算本批结束下标，避免最后一批越界。
            var request = GetAvailableStocksRequest.newBuilder()// 创建库存查询请求的构建器。
                    .addAllSkuIds(skuIds.subList(start, end).stream()// 取出本批 ID，包含 start，截止到 end 之前。
                            .map(String::valueOf)// 把 Long ID 转成字符串，例如 101L 转成 "101"。
                            .toList())// 将转换后的字符串收集成列表，并写入请求的 sku_ids 字段。
                    .build(); // 构建本批的 Protobuf 请求对象。

            var response = stub.withDeadlineAfter(1, TimeUnit.SECONDS)// 为本批 RPC 设置 1 秒期限。
                    .getAvailableStocks(request);// 发起远程调用，等待库存服务响应。调用失败时异常向上传递。
            response.getItemsList().forEach(item ->// 遍历库存服务实际返回的每条 SKU 库存记录。
                    stocks.put(Long.parseLong(item.getSkuId()), item.getAvailableStock()));// 把字符串 ID 转回 Long，并存入可售数量。
        }
        return stocks;
    }

    @PreDestroy// Spring 销毁这个 Bean 前会调用下面的方法。
    public void shutdown() {// 定义客户端的资源清理方法。
        channel.shutdown();// 发起有序关闭，拒绝新调用并允许进行中的调用结束。
    }
}