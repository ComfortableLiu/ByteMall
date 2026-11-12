# 服务间 RPC 与 Kafka 接口约定

## 1. 文档范围与实现状态

本文统一描述 ByteMall 服务间同步调用和 Kafka 消息契约，不面向买家 Web/App 直接开放。

状态分为：

- **已有代码骨架**：项目中已有 Controller、Listener 或 Service 骨架，但不代表鉴权、事务、重试等生产逻辑已经完整实现。
- **规划**：架构需要，但当前没有可调用实现，不能据此直接联调。

当前内部同步调用可以使用 **REST + JSON** 或 **gRPC + Protobuf**。REST 统一前缀为 `/api/v1`；gRPC 契约集中在 `byte-mall-proto/src/main/proto/`。当前 `GetAvailableStocks` 已作为 gRPC 参考板子实现，其余规划接口不能据此视为已经提供 gRPC 服务。

## 2. 通用调用约定

- 内部接口路径包含 `/internal/**`，只允许服务身份调用，不能使用买家或商家 JWT 代替服务鉴权。
- 推荐使用 mTLS 或独立服务令牌，并仅在内网开放。
- 每次调用传播 `X-Request-Id`；创建、预扣、支付等写操作必须携带 `Idempotency-Key`。
- 请求和响应使用 UTF-8 JSON，字段使用 `camelCase`。
- 外部 ID 使用字符串；金额使用整数人民币分；时间使用带时区的 ISO 8601 字符串。
- 普通内部接口使用统一 `ApiResponse<T>` 包装。支付渠道回调的响应格式以渠道协议为准，不使用统一包装。
- 超时、限流和重试必须由调用方配置。非幂等请求不能在网络超时后直接重试，应先查询真实状态。

## 3. 已有内部 RPC 接口骨架

### 3.1 库存预扣、确认和释放

调用方：`order-service`。确认操作也可由支付/订单事件处理器触发。

| 方法 | 路径 | 语义 | 状态 |
| --- | --- | --- | --- |
| `POST` | `/internal/stocks/reservations` | 整体预扣多个 SKU | 已有代码骨架 |
| `POST` | `/internal/stocks/reservations/{reservationNo}/confirm` | 支付后确认扣减 | 已有代码骨架 |
| `POST` | `/internal/stocks/reservations/{reservationNo}/release` | 取消或失败补偿回补 | 已有代码骨架 |

预扣请求：

```json
{
  "reservationNo": "RSV202611110001",
  "orderNo": "BM202611110001",
  "expireAt": "2026-11-11T11:30:00Z",
  "items": [
    {
      "skuId": "10001",
      "quantity": 2
    }
  ]
}
```

约束：

- `reservationNo` 是预扣业务单号，由订单创建方在调用库存前生成；重复请求必须返回首次结果。
- `orderNo` 用于关联业务订单，必须唯一对应一次预扣。
- 同一预扣单中的多个 SKU 必须整体成功，任一失败则全部回滚。
- 库存不足返回 `409 STOCK_INSUFFICIENT`，不能返回部分成功。
- 创建订单的本地事务失败时，订单服务同步调用 `release`，原因使用 `ORDER_CREATE_FAILED`。
- 订单已经创建成功后的取消释放通过 `OrderCancelled` 事件执行，不走同步调用链。
- `confirm` 和 `release` 必须幂等；同一预扣单不能从 `CONFIRMED` 再迁移为 `RELEASED`。

### 3.2 支付渠道通知

| 方法 | 路径 | 语义 | 状态 |
| --- | --- | --- | --- |
| `POST` | `/internal/payment-callbacks/{channel}` | 接收支付渠道异步通知 | 已有代码骨架 |

`channel` 当前枚举为 `MOCK`、`ALIPAY`、`WECHAT`。

当前骨架通知字段示例：

```json
{
  "notifyId": "notify-202611110001",
  "orderNo": "BM202611110001",
  "amount": 129900,
  "status": "SUCCESS",
  "signature": "channel-signature"
}
```

正式接入时不能直接信任该 JSON：

- 必须按照渠道协议验证签名、商户号、订单号、金额、币种和通知状态。
- 使用 `notifyId` 持久化去重，防止重复通知和重放。
- 本地支付单和 Outbox 必须在同一事务中更新。
- 订单和库存通过后续事件更新，不能在回调中直接跨库修改。
- 响应体和成功/失败应答格式以具体支付渠道协议为准。

### 3.3 启动支付对账任务

| 方法 | 路径 | 成功状态 | 语义 |
| --- | --- | --- | --- |
| `POST` | `/internal/payments/reconciliation-jobs` | `202` | 启动异步对账任务 |

请求：

```json
{
  "channel": "ALIPAY",
  "bizDate": "2026-11-10"
}
```

任务只能异步执行并返回接受状态，不能用同步 HTTP 请求等待整份渠道账单处理完成。

### 3.4 批量查询可售库存（gRPC）

调用方：`cart-service`，也可用于其他只读库存场景。该接口是 gRPC 参考板子，不提供 `/internal/stocks/available` HTTP 路径。

| 项目 | 约定 |
| --- | --- |
| Proto | `byte-mall-proto/src/main/proto/stock.proto` |
| 服务 | `com.bytemall.stock.StockService` |
| 方法 | `GetAvailableStocks` |
| 服务端 | `stock-service`，默认 gRPC 端口 `9091` |
| 客户端 | `cart-service` 的 `StockServiceBlockingStub` |

契约：

```proto
service StockService {
  rpc GetAvailableStocks (GetAvailableStocksRequest)
      returns (GetAvailableStocksResponse);
}

message GetAvailableStocksRequest {
  repeated string sku_ids = 1;
}

message AvailableStock {
  string sku_id = 1;
  int32 available_stock = 2;
  int64 version = 3;
}

message GetAvailableStocksResponse {
  repeated AvailableStock items = 1;
  repeated string missing_sku_ids = 2;
}
```

服务端继承 `StockServiceGrpc.StockServiceImplBase`，通过 `ServerBuilder` 注册并启动。`stock-service` 的端口通过 `STOCK_GRPC_PORT` 配置。

客户端通过 `ManagedChannel` 创建 Stub，然后像普通函数一样调用：

```java
GetAvailableStocksRequest request = GetAvailableStocksRequest.newBuilder()
        .addSkuIds("10001")
        .addSkuIds("10002")
        .build();

GetAvailableStocksResponse response =
        stockServiceBlockingStub.getAvailableStocks(request);
```

约束：

- `sku_ids` 必填且至少一项，单次最多 100 个；每个 ID 必须是 1–19 位数字字符串，并位于 Java `Long` 范围内。
- 重复 ID 自动去重，返回顺序与去重后的请求顺序一致。
- `available_stock` 是可用于新订单预扣的数量，不包含已预扣库存。
- `missing_sku_ids` 表示库存记录不存在，不能与 `available_stock=0` 混淆。
- 参数错误返回 gRPC `INVALID_ARGUMENT`。
- 当前客户端使用明文连接，只适合本地开发或受控内网；生产环境应配置 TLS/mTLS 和调用超时。

## 4. 规划中的 RPC 契约

以下接口是业务所需的服务边界，**当前没有实现**。名称用于说明职责，不代表已有 REST 路径或 gRPC 方法。

| 调用方 → 被调方 | 建议契约/内部路径 | 主要出入参 | 用途 |
| --- | --- | --- | --- |
| `order-service → user-service` | `GetAddress(addressId, userId)` | 返回完整地址及归属 | 创建订单地址快照 |
| `order-service → product-service` | `GetSkuBatch(skuIds)` | 返回 SKU、SPU、规格、商家、状态和当前售价 | 重新定价和商品快照 |
| `pay-service → order-service` | `GetPaymentContext(orderNo, userId)` | 返回订单归属、状态、应付金额和过期时间 | 创建支付前校验 |
| `pay-service → order-service` | `GetRefundContext(orderNo)` | 返回实付金额、订单状态和售后状态 | 退款校验 |
| `cart-service → product-service` | `GetProductSummaries(skuIds)` | 批量返回名称、图片、规格、售价和上下架状态 | 购物车展示 |
| `search-service → product-service` | `GET /internal/products/{spuId}/index-document` | 返回完整商品索引文档 | 商品事件后的单商品索引更新 |
| `search-service → product-service` | `GET /internal/products/index-feed?afterId={id}&size={size}` | 返回一批索引文档和下一游标 | 全量索引重建 |
| `stock-service → product-service` | `GetSkuBriefs(skuIds)` | 返回标题、主图、SKU 编码、规格和归属 | 商家库存页补全 |
| `product/stock/order/pay → user-service` | `ValidateMerchant(userId, merchantId)` | 返回绑定关系及商家是否有效 | 商家写操作权限校验 |

约束：

- 批量接口必须限制单次 ID 数量，避免大请求和超时。
- 搜索索引重建使用 `id > afterId ORDER BY id LIMIT size` 的游标分页，不使用容易漏数据的普通页码扫描。
- 商品价格、上下架和订单金额必须以权威服务返回值为准，不能接受客户端提交值。
- 商家权限可以使用短时缓存或本地投影优化，但不能只信任 JWT 中的 `merchantId`。

## 5. Kafka 通用约定

- Broker 地址通过 `KAFKA_BROKERS` 配置，不写入前端或源码密钥。
- 消息 Key 和 Value 使用字符串，Value 为 UTF-8 JSON。
- 生产者使用 `StringSerializer`，消费者使用 `StringDeserializer`。
- 采用至少一次投递，消费者必须幂等，不能假设消息只会到达一次。
- 同一聚合使用同一分区键，例如商品使用 `spuId`，订单和支付使用 `orderNo`。
- 生产者在业务事务中同时写入 Outbox，由 relay 投递 Kafka；不能在数据库事务尚未提交时直接发事件。
- 消费者的 Inbox 去重记录和业务处理必须在同一数据库事务中提交。
- 分区数、副本数和保留时间由部署环境预先规划，生产环境不依赖自动创建 Topic。

## 6. 已有商品变更事件：`ProductChanged`

### 6.1 Topic 与消费者

| 项目 | 约定 |
| --- | --- |
| Topic | `product-changed` |
| 生产者 | `product-service` 的 Outbox relay；当前测试可使用 `KafkaTemplate` 直接发送 |
| 消费者 | `search-service` |
| 消费组 | `search-service-consumer-group-v1` |
| 消息 Key | `spuId`，与 `aggregateId` 相同 |
| 位点提交 | 商品数据写入 ES 并等待刷新后，手动 `acknowledge()` |
| 幂等方式 | 使用 `spuId` 作为 ES 文档 `_id`，重复事件覆盖同一文档 |

### 6.2 消息体

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ProductChanged",
  "aggregateType": "PRODUCT",
  "aggregateId": "122",
  "version": 1,
  "occurredAt": "2026-11-11T02:00:00Z"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `eventId` | string | 是 | 全局唯一事件 ID，用于日志追踪和消费去重 |
| `eventType` | string | 是 | 固定为 `ProductChanged` |
| `aggregateType` | string | 是 | 固定为 `PRODUCT` |
| `aggregateId` | string | 是 | 商品 `spuId`，消费者据此获取最新商品读模型 |
| `version` | integer | 是 | 商品聚合版本，用于识别旧事件和乱序 |
| `occurredAt` | string(date-time) | 是 | 业务事件发生时间，ISO 8601 |

当前消息体只携带商品标识和版本，不携带完整商品内容，也不携带支付密钥、用户隐私或数据库凭据。

### 6.3 生产者调用示例

```java
String payload = objectMapper.writeValueAsString(event);
kafkaTemplate.send("product-changed", spuId.toString(), payload);
```

测试时可以阻塞等待 Broker 确认：

```java
var result = kafkaTemplate.send("product-changed", spuId, payload).get();
long offset = result.getRecordMetadata().offset();
```

正式业务不能仅依赖日志中的“发送成功”，必须通过 Outbox 处理数据库提交与消息投递之间的不一致。

### 6.4 消费者处理流程

1. 解析 JSON，并检查 `eventType=ProductChanged`。
2. 根据 `aggregateId` 获取商品最新读模型。
3. 使用 `spuId` 作为 `_id` 创建或覆盖 ES 文档。
4. 等待 ES 刷新成功后手动提交位点。
5. 商品不存在、JSON 无法解析或 ES 写入失败时，不执行成功确认。

当前实现仍通过 `JdbcTemplate` 直读商品库，这是过渡方案；正式结构应切换为第 4 节中的 `search-service → product-service` RPC。当前尚未实现完整的 Inbox、指数退避、死信队列和旧版本事件防护，不能视为完整生产可靠性方案。

## 7. 规划中的事件

以下事件仅有架构设计，当前没有完整生产者和消费者实现。

| 事件 | 生产者 → 消费者 | 分区键 | 用途 |
| --- | --- | --- | --- |
| `OrderCreated` | `order-service → cart-service` | `orderNo` | 清理已下单购物车项 |
| `OrderCancelled` | `order-service → stock-service` | `orderNo` | 释放已创建订单的预扣库存 |
| `PaymentSucceeded` | `pay-service → order-service` | `orderNo` | 订单迁移为待发货 |
| `OrderPaid` | `order-service → stock-service` | `orderNo` | 预扣库存确认扣减 |
| `RefundSucceeded` | `pay-service → order-service` | `orderNo` | 订单迁移为已退款 |
| 售后回库事件 | `order-service → stock-service` | `orderNo` | 按售后决定回补库存，不能由支付回调默认回库 |

这些事件必须包含 `eventId`、`eventType`、`aggregateType`、`aggregateId`、`version`、`occurredAt` 和业务 `payload`。消费者应先处理版本和幂等，再执行状态迁移，禁止旧事件覆盖新状态。

## 8. 联调检查清单

- 内部 REST 路径是否只在内网暴露，并校验独立服务身份。
- 所有写操作是否具备幂等键、请求指纹和冲突处理。
- RPC 是否配置连接超时、读取超时和有限重试，避免级联阻塞。
- Kafka 生产是否在数据库提交后执行，并具备 Outbox。
- 消费失败是否具备有限重试、死信、告警和人工补偿入口。
- 同一聚合的消息是否使用固定分区键，是否有旧版本覆盖新版本的防护。
- 是否验证过重复消息、乱序消息、消费者重启和 Broker 短暂不可用。
- 日志是否包含 `X-Request-Id`、`eventId`、聚合 ID、Topic、分区和 offset，且不记录密钥或完整敏感报文。
