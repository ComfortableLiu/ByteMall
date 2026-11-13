# 服务间 RPC 与 Kafka 接口约定

## 1. 文档范围与协议边界

本文统一描述 ByteMall 服务间同步调用和 Kafka 消息契约。

**项目中的所有内部 RPC 接口统一使用 gRPC + Protobuf，不再新增 `/internal/**` REST RPC 接口。**

以下三类通信必须区分：

| 类型 | 协议 | 示例 |
| --- | --- | --- |
| 内部同步 RPC | **gRPC** | 批量查库存、库存预扣、关闭支付单、启动对账 |
| 买家/商家公开 API | REST + JSON | 登录、商品搜索、购物车、订单、买家支付 |
| 三方平台回调与模拟页面 HTTP 接口 | REST + JSON | 支付渠道通知、模拟三方查单 |

特别说明：

- 关闭支付单统一调用 `PaymentService.CloseOrder` gRPC 方法，不保留 `POST /payments/{payNo}/close` REST 接口。
- `POST /internal/payment-callbacks/{channel}` 是三方渠道主动通知的 HTTP 入口，不是内部 RPC。
- `GET /internal/mock-payments/query` 是模拟三方查单的 HTTP 入口，也不是内部 RPC。
- `POST /payments`、`GET /payments/{payNo}`、`GET /stocks/skus/{skuId}` 以及用户地址、商品公开查询属于公开 REST，不能因为存在相近的内部 gRPC 能力就直接删除。
- 当前部分内部 REST 骨架仍存在于代码中，但属于待迁移实现，不纳入目标契约，不能作为新功能的联调依据。

实现状态：

- **已有代码骨架**：已有 `.proto`、Listener 或 Service 骨架，不代表鉴权、事务、重试等生产逻辑已完整。
- **规划**：架构需要，但当前没有可调用实现，不能直接联调。

## 2. gRPC 通用约定

### 2.1 契约与代码生成

- 所有 `.proto` 文件集中在 `byte-mall-proto/src/main/proto/`。
- 服务定义使用 `service XxxService`，生成的 Java 类名为 `XxxServiceGrpc`。
- 服务端继承 `XxxServiceGrpc.XxxServiceImplBase`。
- 客户端通过 `XxxServiceGrpc.newBlockingStub(channel)` 创建 Stub，像普通 Java 函数一样发起远程调用。
- gRPC 使用 HTTP/2，不需要单独部署 gRPC Broker。各业务服务在自己的进程中同时提供 REST 和 gRPC 接口。

### 2.2 元数据与鉴权

| 元数据 | 必填场景 | 说明 |
| --- | --- | --- |
| `authorization` | 所有内部调用 | 独立服务身份令牌；不能使用买家或商家 JWT 代替服务鉴权 |
| `x-request-id` | 所有调用 | 跨服务链路追踪 ID |
| `idempotency-key` | 创建、预扣、支付、退款等写操作 | 调用方生成；相同键和相同请求返回首次结果 |
| `x-actor-id` | 需要用户或商家数据范围的调用 | 由可信服务身份上下文传入，接收方仍需校验数据归属 |

内部 gRPC 推荐使用 mTLS 或服务签名令牌，并仅在内网开放。当前示例中的 `usePlaintext()` 仅用于本地开发。

### 2.3 数据类型

- 外部 ID 使用 `string`，数据库/Java 数字 ID 可映射为 `int64`。
- 金额使用 `int64`，单位为人民币分。
- 时间使用带时区的 ISO 8601 `string`，或统一使用 Protobuf `Timestamp`，同一契约中不能混用。
- JSON 字段使用 `camelCase`，Protobuf 字段使用 `snake_case`。
- 集合使用 `repeated`，空集合等价于空数组，不额外返回 `null` 包装对象。
- gRPC 不使用 `ApiResponse<T>` 包装，业务结果通过消息字段和状态码表达。

### 2.4 状态码

| gRPC 状态码 | 使用场景 |
| --- | --- |
| `INVALID_ARGUMENT` | 参数为空、格式错误、超出批量上限 |
| `NOT_FOUND` | 商品、库存、订单、支付单不存在 |
| `ALREADY_EXISTS` | 业务单号或幂等记录已存在且需要显式提示 |
| `ABORTED` | 幂等键相同但请求指纹不同、状态条件更新失败 |
| `FAILED_PRECONDITION` | 当前状态不允许关闭支付单、确认或释放库存 |
| `RESOURCE_EXHAUSTED` | 库存不足、超过调用配额或限流 |
| `UNAUTHENTICATED` | 服务身份缺失或令牌无效 |
| `PERMISSION_DENIED` | 商家或用户数据范围不匹配 |
| `UNAVAILABLE` | 下游暂时不可用，可有限重试 |
| `DEADLINE_EXCEEDED` | 调用超时；先查询真实状态，不能直接重复写入 |
| `INTERNAL` | 未分类服务端异常，不向前端暴露 SQL 和堆栈 |

## 3. 已有 gRPC 契约骨架

### 3.1 `StockService.GetAvailableStocks`

| 项目 | 约定 |
| --- | --- |
| Proto | `byte-mall-proto/src/main/proto/stock.proto` |
| 完整服务名 | `com.bytemall.stock.StockService` |
| 方法 | `GetAvailableStocks` |
| 服务端 | `stock-service`，默认 gRPC 端口 `9091` |
| 客户端 | `cart-service` 的 `StockServiceBlockingStub` |

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

约束：

- `sku_ids` 必填且至少一项，单次最多 100 个；每个 ID 必须是 1–19 位数字字符串并位于 Java `Long` 范围内。
- 重复 ID 自动去重，返回顺序与去重后的请求顺序一致。
- `available_stock` 是可用于新订单预扣的数量，不包含已预扣库存。
- `missing_sku_ids` 表示库存记录不存在，不能与 `available_stock=0` 混淆。
- 参数错误返回 `INVALID_ARGUMENT`。

处理流程：校验 SKU 数量、格式和 `Long` 范围 → 去重并保持请求顺序 → 通过 `StockMainMapper.selectByIds` 一次批量查询 → 按 SKU 映射可售量和版本 → 将无库存记录的 ID 放入 `missing_sku_ids` → 返回只读结果，不修改库存。

客户端示例：

```java
GetAvailableStocksRequest request = GetAvailableStocksRequest.newBuilder()
        .addSkuIds("10001")
        .addSkuIds("10002")
        .build();

GetAvailableStocksResponse response =
        stockServiceBlockingStub.getAvailableStocks(request);
```

### 3.2 `PaymentService.CloseOrder`

以下按“关闭业务订单对应的待支付支付单”定义业务契约。`payment.proto` 和服务端 `MOCK` 渠道关单流程已实现；`ALIPAY`、`WECHAT` 仍需接入真实渠道关单或查单适配器。

```proto
service PaymentService {
  rpc CloseOrder (CloseOrderRequest) returns (CloseOrderResponse);
}

message CloseOrderRequest {
  string order_no = 1;
  string reason = 2;
}

message CloseOrderResponse {
  string order_no = 1;
  string pay_no = 2;
  string status = 3;
  string closed_at = 4;
}
```

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `order_no` | string | 是 | 需要关闭支付的业务订单号，由支付服务定位该订单当前的待支付支付单 |
| 入参 | `reason` | string | 是 | 关闭原因：`ORDER_CANCELLED`、`PAYMENT_TIMEOUT` 或 `RISK_REJECTED` |
| 出参 | `order_no` | string | 是 | 业务订单号 |
| 出参 | `pay_no` | string | 是 | 实际被关闭的支付单号；幂等重复调用时返回首次关闭的支付单号 |
| 出参 | `status` | string | 是 | 关闭后的支付单状态，固定为 `CLOSED` |
| 出参 | `closed_at` | string(date-time) | 是 | 支付服务记录的关闭时间，ISO 8601；幂等重复调用时保持首次关闭时间 |

调用方还必须通过 gRPC 元数据传入 `idempotency-key` 和 `x-request-id`。相同幂等键对应不同 `order_no` 或 `reason` 时返回 `ABORTED`。

业务约束：

- 请求只表达“关闭哪个业务订单的支付、为什么关闭”，不要求调用方提前查询支付单号，也不接收用户 ID、金额、渠道或收银台参数。
- 仅允许 `PENDING → CLOSED`；已关闭则幂等返回首次关闭结果；已成功支付返回 `FAILED_PRECONDITION`，并触发支付状态核对，不能改为关闭。
- 订单从未创建支付单时返回 `NOT_FOUND`。响应不返回完整支付单，需要完整信息时另行调用 `GetPayment`。
- 关闭支付单只提供 gRPC 接口，不提供买家 REST 关闭路径。

处理流程：校验参数和幂等键 → 按 `order_no` 查询并锁定当前支付单 → 校验支付状态 → 请求渠道关闭或查单，确认未支付 → 条件更新为 `CLOSED`，保存关闭原因和时间并递增版本 → 返回关闭结果。渠道结果未知时不能宣称关闭成功，应返回可重试错误并继续核对渠道状态；回调和关闭操作必须基于同一支付单状态做并发控制。

### 3.3 三方 HTTP 接口不属于内部 gRPC

支付渠道回调 `POST /internal/payment-callbacks/{channel}` 和模拟查单 `GET /internal/mock-payments/query` 继续使用 HTTP，因为它们模拟的是三方平台与 `pay-service` 的外部通信，不是服务间 RPC。

沙箱页面的调用顺序：

1. `POST /internal/payment-callbacks/MOCK`，模拟三方回调。
2. `GET /internal/mock-payments/query?orderNo={orderNo}&amount={amount}&status={result}`，模拟主动查单。
3. 页面按结果展示成功或失败。

这些接口仅用于演示，不查询真实渠道。当前 MOCK 回调已实现固定签名校验、`notifyId` 去重、金额与渠道核对、支付状态更新和回调流水；真实渠道验签以及 `PaymentSucceeded` Outbox/Kafka 仍为 TODO，因此当前回调不会自动推进订单和库存。

## 4. 全部内部 gRPC 契约清单

以下接口均使用 gRPC，**不再设计对应的 `/internal/**` REST 路径**。除特别标注为已有骨架的方法外，其余均为规划。

通用元数据 `authorization`、`x-request-id`、`idempotency-key` 不在每个接口的业务字段表中重复；写操作是否必须携带幂等键以各方法说明为准。

### 4.1 StockService

#### 4.1.1 `GetAvailableStocks`（服务端已实现）

调用方：`cart-service`。入参为 `repeated string sku_ids`，出参为 `repeated AvailableStock items` 和 `repeated string missing_sku_ids`。字段类型、约束和调用示例见 3.1。

处理逻辑：校验数量与 ID 格式 → 去重并保持顺序 → 批量查询 `stock_main` → 返回可售量和版本 → 将不存在的 ID 放入 `missing_sku_ids`。该接口只读，不修改库存。

#### 4.1.2 `ReserveStock`（规划）

调用方：`order-service`。用途：创建订单时整体预扣多个 SKU。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `reservation_no` | string | 是 | 预扣单号，由订单创建方在调用前生成 |
| 入参 | `order_no` | string | 是 | 业务订单号 |
| 入参 | `expire_at` | string | 是 | 预扣过期时间，ISO 8601 |
| 入参 | `items` | `repeated ReservationItem` | 是 | 至少一项；每项包含 `sku_id` 和正整数 `quantity` |
| 出参 | `reservation_no` | string | 是 | 预扣单号 |
| 出参 | `order_no` | string | 是 | 业务订单号 |
| 出参 | `status` | string | 是 | 成功时为 `RESERVED` |
| 出参 | `items` | `repeated ReservationItem` | 是 | 实际预扣明细 |

处理逻辑：校验数量与明细 → 校验幂等键和请求指纹 → 按固定顺序锁定/更新 SKU → 检查可售量 → 条件更新 `available`、`reserved` 和版本 → 同事务保存预扣单、明细及流水 → 返回 `RESERVED`。任一明细失败则整体回滚；库存不足返回 `RESOURCE_EXHAUSTED`，重复请求返回首次结果。

#### 4.1.3 `ConfirmReservation`（规划）

调用方：订单支付事件处理器。用途：支付成功后将预扣转为确认扣减。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `reservation_no` | string | 是 | 需要确认的预扣单号 |
| 出参 | `reservation_no` | string | 是 | 预扣单号 |
| 出参 | `order_no` | string | 是 | 关联订单号 |
| 出参 | `status` | string | 是 | 成功时为 `CONFIRMED` |
| 出参 | `items` | `repeated ReservationItem` | 是 | 已确认扣减明细 |

处理逻辑：按预扣单号加锁查询 → 已确认则直接返回首次结果 → 已释放则返回 `FAILED_PRECONDITION` → 同时扣减 `total` 和 `reserved` 并递增版本 → 保存库存流水 → 返回确认结果。不得对同一预扣重复扣减。

#### 4.1.4 `ReleaseReservation`（规划）

调用方：`order-service`。用途：订单取消或创建失败时回补预扣库存。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `reservation_no` | string | 是 | 需要释放的预扣单号 |
| 入参 | `reason` | string | 是 | `USER`、`TIMEOUT`、`ORDER_CREATE_FAILED` 等原因 |
| 出参 | `reservation_no` | string | 是 | 预扣单号 |
| 出参 | `order_no` | string | 是 | 关联订单号 |
| 出参 | `status` | string | 是 | 成功时为 `RELEASED` |
| 出参 | `items` | `repeated ReservationItem` | 是 | 已回补明细 |

处理逻辑：校验释放原因 → 加锁查询预扣单 → 已释放则幂等返回 → 已确认则返回 `FAILED_PRECONDITION` → 增加 `available`、减少 `reserved` 并递增版本 → 保存释放原因和流水 → 返回释放结果。禁止 `CONFIRMED → RELEASED`。

### 4.2 PaymentService

#### 4.2.1 `CreatePayment`（本地模拟流程已实现，订单上下文与真实渠道待接入）

调用方：结算服务或 `pay-service` 的公开接口适配层。用途：为待支付订单创建支付单并返回收银台参数。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `order_no` | string | 是 | 待支付订单号 |
| 入参 | `channel` | string | 是 | `MOCK`、`ALIPAY` 或 `WECHAT` |
| 出参 | `pay_no` | string | 是 | 支付单号 |
| 出参 | `order_no` | string | 是 | 业务订单号 |
| 出参 | `user_id` | string | 是 | 买家 ID，项目统一序列化为字符串 |
| 出参 | `channel` | string | 是 | 支付渠道 |
| 出参 | `amount` | uint64 | 是 | 服务端核定的应付金额，单位分 |
| 出参 | `status` | string | 是 | 初始为 `PENDING` |
| 出参 | `cashier_token` | string | 否 | 收银台令牌或渠道支付参数，不记录明文日志 |
| 出参 | `expire_at` | string | 是 | 支付截止时间 |
| 出参 | `paid_at` | string | 否 | 支付成功时间，未支付时为空 |
| 出参 | `created_at` | string | 是 | 创建时间 |

处理逻辑：校验幂等键和请求指纹 → 调用 `OrderService.GetPaymentContext` → 校验订单归属、状态和金额 → 复用同订单有效支付单 → 本地事务创建 `PENDING` 支付单 → 调用对应渠道适配器下单 → 返回 `pay_no` 和收银台参数。客户端不能传入金额，渠道超时不能立即判失败或重复扣款。

当前实现支持参数校验、复用同渠道的 `PENDING` 支付单、拒绝已支付订单重复创建，以及不同渠道有效支付单的冲突提示。`OrderService.GetPaymentContext`、`idempotency-key` 请求指纹持久化和真实渠道下单尚未接入；买家 ID、金额及渠道令牌仍沿用本地占位逻辑，不能作为生产支付依据。

#### 4.2.2 `GetPayment`（规划）

调用方：结算/订单服务。用途：查询可信支付单状态。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `pay_no` | string | 是 | 支付单号 |
| 出参 | `pay_no` | string | 是 | 支付单号 |
| 出参 | `order_no` | string | 是 | 业务订单号 |
| 出参 | `user_id` | string | 是 | 买家 ID |
| 出参 | `channel` | string | 是 | 支付渠道 |
| 出参 | `amount` | uint64 | 是 | 支付金额，单位分 |
| 出参 | `status` | string | 是 | `PENDING`、`SUCCESS`、`FAILED` 或 `CLOSED` |
| 出参 | `cashier_token` | string | 否 | 仅渠道需要时返回 |
| 出参 | `expire_at`、`paid_at`、`created_at` | string | 按状态 | 支付截止、成功和创建时间 |

处理逻辑：校验支付单号 → 查询支付单 → 校验调用方是否有权读取该订单 → 返回数据库中的可信状态。前端轮询只用于展示，不能直接触发成功状态。

#### 4.2.3 `CloseOrder`（MOCK 流程已实现，真实渠道待接入）

调用方：`order-service` 或其他受信任的内部服务。用途：业务订单取消、支付超时或风控拒绝时，按 `order_no` 关闭该订单当前的待支付支付单。请求包含 `order_no`、`reason`，响应只包含 `order_no`、`pay_no`、`status` 和 `closed_at`；完整出入参及状态约束见 3.2。不再提供 `/payments/{payNo}/close` REST 接口。

处理逻辑：校验参数和幂等键 → 按 `order_no` 查询并锁定当前支付单 → 已关闭则幂等返回 → 已成功等不可关闭状态返回 `FAILED_PRECONDITION` → 请求渠道关闭或查单并确认未支付 → 条件更新为 `CLOSED`，记录关闭原因、关闭时间和新版本 → 返回关闭结果。渠道状态未知时返回可重试错误，不能提前返回关闭成功。

当前实现使用 `payment_order.updated_at` 保存关闭时间，使用 `payment_transaction` 的 `CLOSE` 流水保存关闭原因；重复关闭根据 `CLOSED` 状态幂等返回。`idempotency-key` 的请求指纹持久化及真实渠道适配器尚未接入。

#### 4.2.4 `StartReconciliationJob`（规划）

调用方：调度服务。用途：启动异步支付对账。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `channel` | string | 是 | 对账渠道：`MOCK`、`ALIPAY` 或 `WECHAT` |
| 入参 | `biz_date` | string(date) | 是 | 渠道账单业务日期，不能晚于当天 |
| 出参 | `job_id` | string | 是 | 对账任务 ID |
| 出参 | `channel` | string | 是 | 对账渠道 |
| 出参 | `biz_date` | string(date) | 是 | 对账业务日期 |
| 出参 | `status` | string | 是 | 接受后使用 `QUEUED` 或 `ACCEPTED` |
| 出参 | `created_at` | string | 是 | 任务创建时间 |

处理逻辑：校验渠道和账期 → 创建唯一任务 ID → 在同一事务中写入 Outbox `ReconciliationRequested` → 立即返回任务 ID → 后台任务拉取渠道账单并按交易号核对 → 差异写入 `reconciliation_record` → 可自动修复的按明确规则处理，其余告警并人工复核。不能用同步 RPC 等待整份账单处理完成。

### 4.3 UserService

#### 4.3.1 `GetAddress`（规划）

调用方：`order-service`。用途：读取买家地址并用于订单快照。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `address_id` | string | 是 | 地址 ID |
| 入参 | `user_id` | string | 是 | 当前买家 ID，用于归属校验 |
| 出参 | `address_id`、`user_id` | string | 是 | 地址及用户 ID |
| 出参 | `receiver_name` | string | 是 | 收件人 |
| 出参 | `receiver_mobile` | string | 是 | 收件手机号，按敏感信息保护 |
| 出参 | `province`、`city`、`district` | string | 是 | 省、市、区 |
| 出参 | `detail_address` | string | 是 | 详细地址 |
| 出参 | `postal_code` | string | 否 | 邮政编码 |
| 出参 | `is_default` | bool | 是 | 是否默认地址 |

处理逻辑：按 `address_id + user_id` 查询 → 不存在或不属于当前用户时返回 `NOT_FOUND`，避免暴露他人地址 → 返回完整地址 → 订单服务复制为快照，不依赖后续可变资料。

#### 4.3.2 `ValidateMerchant`（规划）

调用方：`product-service`、`stock-service`、`order-service`、`pay-service`。用途：校验用户和商家的有效绑定。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `user_id` | string | 是 | 当前用户 ID |
| 入参 | `merchant_id` | string | 是 | 请求访问的商家 ID |
| 出参 | `valid` | bool | 是 | 当前用户是否可操作该商家 |
| 出参 | `merchant_id` | string | 是 | 校验后的商家 ID |
| 出参 | `merchant_status` | string | 是 | 例如 `ACTIVE`、`SUSPENDED` |
| 出参 | `reason` | string | 否 | 无权限或商家不可用的原因 |

处理逻辑：查询商家及用户绑定 → 校验商家状态和绑定关系 → 返回 `valid=false` 或 `PERMISSION_DENIED`。不得只信任 JWT 或调用方传入的 `merchantId`；高频校验可使用带失效时间的短时缓存。

### 4.4 ProductService

#### 4.4.1 `GetSkuBatch`（规划）

调用方：`order-service`。用途：创建订单时重新定价并生成商品快照。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `sku_ids` | repeated string | 是 | 1–100 个 SKU ID |
| 出参 | `items` | repeated `ProductSkuInfo` | 是 | 商品与 SKU 快照数据 |
| 出参 | `missing_sku_ids` | repeated string | 是 | 不存在的 SKU ID |

`ProductSkuInfo` 包含：`sku_id`、`spu_id`、`merchant_id`、`title`、`subtitle`、`brand`、`category_id`、`cover_image`、`spec_json`、`price`、`original_price`、`enabled`、`status`、`version`。金额单位为分，`spec_json` 为规格 JSON。

处理逻辑：校验数量和 ID → 去重 → 批量查询 SKU、SPU 和主图，禁止 N+1 → 返回上下架状态与当前售价 → 缺失项放入 `missing_sku_ids`。不能过滤下架项后让订单服务误判为存在，也不能接受客户端传入价格。

#### 4.4.2 `GetProductSummaries`（规划）

调用方：`cart-service`。用途：批量补齐购物车商品摘要。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `sku_ids` | repeated string | 是 | 1–100 个 SKU ID |
| 出参 | `items` | repeated `ProductSummary` | 是 | 商品摘要列表 |
| 出参 | `missing_sku_ids` | repeated string | 是 | 不存在的 SKU ID |

`ProductSummary` 包含：`sku_id`、`spu_id`、`merchant_id`、`title`、`brand`、`category_id`、`cover_image`、`price`、`status`、`enabled`。购物车库存通过 `StockService.GetAvailableStocks` 单独查询，不在此接口重复维护。

处理逻辑：批量读取商品和 SKU → 映射名称、图片、规格售价及上下架状态 → 保持去重后的请求顺序 → 返回缺失项。该结果只用于展示，订单创建仍需再次实时校验。

#### 4.4.3 `GetIndexDocument`（规划）

调用方：`search-service`。用途：商品变更后获取完整索引读模型。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `spu_id` | string | 是 | 商品 SPU ID |
| 出参 | `document` | `ProductIndexDocument` | 是 | 完整 ES 索引文档 |

`ProductIndexDocument` 包含：`spu_id`、`merchant_id`、`category_id`、`brand`、`cover_image`、`title`、`subtitle`、`description_text`、`min_price`、`max_price`、`sales_count`、`status`、`published_at`、`updated_at`、`version`。

处理逻辑：查询 SPU、SKU 和图片 → 去除描述 HTML 并转纯文本 → 计算启用 SKU 的最低/最高价 → 选择主图 → 返回完整文档。`product-service` 不直接写 ES；下架和草稿保留 `status`，由搜索查询统一过滤。

#### 4.4.4 `GetIndexFeed`（规划）

调用方：`search-service`。用途：全量重建索引时分批读取商品。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `after_id` | string | 否 | 上一批最后一个 SPU ID，首批为 `0` |
| 入参 | `size` | uint32 | 否 | 默认 500，最大 1000 |
| 出参 | `items` | repeated `ProductIndexDocument` | 是 | 本批索引文档 |
| 出参 | `next_after_id` | string | 否 | 下一批游标；无更多数据时为空 |
| 出参 | `has_more` | bool | 是 | 是否还有下一批 |

处理逻辑：使用 `id > after_id ORDER BY id LIMIT size + 1` 游标扫描 → 按 `GetIndexDocument` 的相同规则批量组装文档 → 根据额外一条判断 `has_more` → 返回本批最后一个 ID 作为下一游标。禁止普通页码扫描导致数据变化时漏商品。

#### 4.4.5 `GetSkuBriefs`（规划）

调用方：`stock-service`。用途：商家库存页补齐商品和 SKU 信息。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `sku_ids` | repeated string | 是 | 1–100 个 SKU ID |
| 出参 | `items` | repeated `SkuBrief` | 是 | SKU 摘要列表 |
| 出参 | `missing_sku_ids` | repeated string | 是 | 不存在的 SKU ID |

`SkuBrief` 包含：`sku_id`、`spu_id`、`merchant_id`、`product_title`、`product_image`、`sku_code`、`spec_json`、`enabled`、`status`、`version`。

处理逻辑：校验并去重 ID → 批量查询商品与 SKU → 校验商家归属 → 返回标题、主图、编码和规格。库存数量仍由 `stock-service` 提供，不在商品服务中读取库存表。

### 4.5 OrderService

#### 4.5.1 `GetPaymentContext`（规划）

调用方：`pay-service`。用途：创建支付前提供可信订单上下文。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `order_no` | string | 是 | 业务订单号 |
| 入参 | `user_id` | string | 是 | 当前买家 ID |
| 出参 | `order_no` | string | 是 | 业务订单号 |
| 出参 | `user_id` | string | 是 | 买家 ID |
| 出参 | `merchant_id` | string | 是 | 商家 ID |
| 出参 | `status` | string | 是 | 订单状态 |
| 出参 | `payable_amount` | uint64 | 是 | 权威应付金额，单位分 |
| 出参 | `expire_at` | string | 是 | 支付截止时间 |
| 出参 | `version` | uint64 | 是 | 订单版本，用于并发识别 |

处理逻辑：按订单号查询并校验 `user_id` → 校验 `PENDING_PAYMENT` 和未过期 → 返回订单中的应付金额与状态。`pay-service` 不得信任客户端金额，也不得用该查询直接修改订单状态。

#### 4.5.2 `GetRefundContext`（规划）

调用方：`pay-service`。用途：退款前核对订单实付和可退金额。

| 方向 | 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 入参 | `order_no` | string | 是 | 业务订单号 |
| 出参 | `order_no` | string | 是 | 业务订单号 |
| 出参 | `user_id` | string | 是 | 买家 ID |
| 出参 | `status` | string | 是 | 当前订单/售后状态 |
| 出参 | `paid_amount` | uint64 | 是 | 已支付金额，单位分 |
| 出参 | `refunded_amount` | uint64 | 是 | 已退款金额，单位分 |
| 出参 | `refundable_amount` | uint64 | 是 | 可退金额，不大于 `paid_amount - refunded_amount` |
| 出参 | `version` | uint64 | 是 | 订单版本 |

处理逻辑：读取订单及可信退款汇总 → 校验订单是否允许退款 → 计算可退金额 → 返回只读上下文。`pay-service` 创建退款时仍需在本地事务中核对支付单和累计退款额，不能仅依赖一次预先查询。

统一约束：

- `ReserveStock` 必须整体成功；库存不足返回 `RESOURCE_EXHAUSTED`，不能部分成功。
- `ConfirmReservation` 和 `ReleaseReservation` 必须幂等，终态冲突返回 `FAILED_PRECONDITION`。
- 批量接口必须限制 ID 数量，索引重建使用游标分页。
- 商品价格、上下架、订单金额和支付状态必须以权威服务返回值为准。
- 商家权限可使用短时缓存或本地投影，但不能只信任调用方传入的 `merchantId`。

## 5. Kafka 通用约定

### 5.1 消息输入与处理结果

- Broker 地址通过 `KAFKA_BROKERS` 配置，不写入前端或源码密钥。
- 消息 Key 和 Value 使用字符串，Value 为 UTF-8 JSON。
- 生产者使用 `StringSerializer`，消费者使用 `StringDeserializer`。
- Kafka 是异步消息，不是同步 RPC，**没有业务响应体**。
- 生产者调用 `KafkaTemplate.send()` 后获得 `SendResult`，可读取 `topic`、`partition`、`offset` 和 `timestamp` 作为投递元数据。
- 消费者的处理结果是本地业务副作用、Inbox 记录和位点提交，不向生产者返回业务结果。
- 采用至少一次投递，消费者必须幂等，不能假设消息只会到达一次。

### 5.2 通用消息字段

业务消息统一使用“事件信封 + `payload`”结构：

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "OrderCreated",
  "aggregateType": "ORDER",
  "aggregateId": "BM202611120001",
  "version": 1,
  "occurredAt": "2026-11-12T02:00:00Z",
  "payload": {}
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `eventId` | string | 是 | 全局唯一事件 ID，用于日志追踪、Inbox 去重和人工补偿 |
| `eventType` | string | 是 | 事件类型，例如 `OrderCreated` |
| `aggregateType` | string | 是 | 聚合类型，例如 `ORDER`、`PAYMENT`、`PRODUCT` |
| `aggregateId` | string | 是 | 聚合业务 ID，例如订单号、支付号或商品 ID |
| `version` | integer | 是 | 聚合版本，消费者据此识别旧事件和乱序 |
| `occurredAt` | string(date-time) | 是 | 业务事件发生时间，ISO 8601 |
| `payload` | object | 是 | 该事件的业务字段，见各事件参数表 |

建议同时携带消息头 `x-request-id`、`event-id`、`producer` 和 `content-type=application/json`。消息头用于追踪，不能替代事件体中的幂等字段。

### 5.3 生产者约束

- 业务数据、状态历史和 Outbox 必须在同一本地事务中提交，由 relay 投递 Kafka。
- 不能在数据库事务尚未提交时直接发事件，也不能只依赖日志中的“发送成功”。
- 发送配置需要明确 `acks=all`、有限重试和超时；评估并显式开启 `enable.idempotence=true`。
- 同一聚合使用固定分区键：商品使用 `spuId`，订单及其支付联动使用 `orderNo`。
- 分区键只保证同一 Key 的顺序，不保证不同订单或商品之间的全局顺序。
- Outbox 记录需要保存 `eventId`、事件类型、聚合 ID、聚合版本、载荷、状态、重试次数和下次重试时间。

### 5.4 消费者约束

- 先解析并校验事件类型、必填字段和版本，再处理业务。
- Inbox 的 `(eventId, consumer)` 唯一约束与业务更新必须在同一事务中提交。
- `payloadHash` 用于发现相同事件 ID 但内容不一致的异常情况。
- 只有在业务事务提交成功后才调用 `acknowledge()`；业务失败不能伪装成成功。
- 重复事件返回当前状态，不重复扣库存、迁移订单或发送后续事件。
- 旧版本事件不能覆盖新版本；需要时保存最后处理版本。
- 所有副作用都应可通过全量校准或人工补偿重新构建。

### 5.5 重试、死信和观测

- 暂时性失败使用有限指数退避，例如初始 1 秒、倍数 2、最多 5 次，并设置最大间隔。
- JSON 无法解析、未知事件类型、验签失败等不可重试错误应直接进入死信，不做无效重试。
- 死信 Topic 建议为 `<原Topic>.DLQ`，保留原消息、事件 ID、异常类型、失败原因和首次失败时间。
- 监控至少覆盖 Outbox 未发布数、消费滞后、重试次数、死信数量、处理耗时和幂等命中次数。
- 生产环境预先创建 Topic，明确分区数、副本数和保留时间，不依赖自动创建。

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

当前实现仍通过 `JdbcTemplate` 直读商品库，这是过渡方案；正式结构应切换为第 4 节中的 `search-service → product-service` gRPC。当前尚未实现完整的 Inbox、指数退避、死信队列和旧版本事件防护，不能视为完整生产可靠性方案。

## 7. 规划中的事件

以下事件仅有架构设计，当前没有完整生产者和消费者实现。所有事件都使用第 5 节的通用信封和 `payload`，Kafka 消费没有同步业务响应体。

| 事件 | Topic | 生产者 → 消费者 | 分区键 | 用途 |
| --- | --- | --- | --- | --- |
| `OrderCreated` | `order-created` | `order-service → cart-service` | `orderNo` | 清理已下单购物车项 |
| `OrderCancelled` | `order-cancelled` | `order-service → stock-service` | `orderNo` | 释放已创建订单的预扣库存 |
| `PaymentSucceeded` | `payment-succeeded` | `pay-service → order-service` | `orderNo` | 订单迁移为待发货 |
| `OrderPaid` | `order-paid` | `order-service → stock-service` | `orderNo` | 预扣库存确认扣减 |
| `RefundSucceeded` | `refund-succeeded` | `pay-service → order-service` | `orderNo` | 订单迁移为已退款 |
| `StockRestockRequested` | `stock-restock-requested` | `order-service → stock-service` | `orderNo` | 按售后决定回补库存 |

### 7.1 `OrderCreated`（规划）

用途：订单创建成功后，清理当前买家已下单的购物车商品。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payload.orderNo` | string | 是 | 业务订单号，与 `aggregateId` 一致 |
| `payload.userId` | string | 是 | 买家 ID，用于定位购物车 |
| `payload.items` | array | 是 | 下单明细，至少一项 |
| `payload.items[].skuId` | string | 是 | 已下单 SKU ID |
| `payload.items[].quantity` | integer | 是 | 已下单数量，正整数 |

处理逻辑：订单及 Outbox 先提交 → 购物车消费者写入 Inbox → 按 `userId + skuId` 删除对应购物车项 → 不存在时按幂等成功处理 → 提交位点。清理失败可以重试，不能回滚已经创建的订单。

### 7.2 `OrderCancelled`（规划）

用途：已创建订单取消后，释放对应预扣库存。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payload.orderNo` | string | 是 | 已取消订单号 |
| `payload.reservationNo` | string | 是 | 需要释放的库存预扣单号 |
| `payload.reason` | string | 是 | `USER`、`TIMEOUT` 等取消原因 |

处理逻辑：订单取消事务提交后投递 → 库存消费者写入 Inbox → 调用与 `StockService.ReleaseReservation` 相同的幂等释放逻辑 → 已释放直接返回 → 已确认记录终态冲突并告警，不强行回库 → 提交位点。创建订单本地事务失败的同步补偿不走该事件。

### 7.3 `PaymentSucceeded`（规划）

用途：支付服务确认真实支付成功后，驱动订单进入待发货。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payload.payNo` | string | 是 | 支付单号 |
| `payload.orderNo` | string | 是 | 业务订单号 |
| `payload.amount` | integer | 是 | 实付金额，单位分 |
| `payload.channel` | string | 是 | 支付渠道 |
| `payload.channelTradeNo` | string | 是 | 渠道交易号 |
| `payload.paidAt` | string(date-time) | 是 | 渠道确认的支付时间 |

处理逻辑：支付单和 Outbox 先提交 → 订单消费者写入 Inbox → 核对订单金额和支付单归属 → 条件更新 `PENDING_PAYMENT → PENDING_SHIPMENT` 并保存状态历史 → 同事务写入 `OrderPaid` Outbox → 提交位点。重复事件返回当前状态；订单已取消等冲突进入退款核对，不能直接覆盖订单状态。

### 7.4 `OrderPaid`（规划）

用途：订单已进入待发货后，确认库存正式扣减。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payload.orderNo` | string | 是 | 已支付订单号 |
| `payload.reservationNo` | string | 是 | 需要确认的库存预扣单号 |
| `payload.paidAt` | string(date-time) | 是 | 支付成功时间 |
| `payload.items` | array | 是 | 订单商品明细，用于审计和校验 |

处理逻辑：订单状态提交后投递 → 库存消费者写入 Inbox → 调用与 `StockService.ConfirmReservation` 相同的幂等确认逻辑 → 同时扣减 `total` 和 `reserved` → 保存库存流水 → 提交位点。重复事件不得重复扣减；预扣已释放时进入人工补偿，而不是重新创建库存。

### 7.5 `RefundSucceeded`（规划）

用途：退款渠道确认成功后，驱动订单进入已退款。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payload.refundNo` | string | 是 | 退款单号 |
| `payload.payNo` | string | 是 | 原支付单号 |
| `payload.orderNo` | string | 是 | 业务订单号 |
| `payload.amount` | integer | 是 | 本次退款金额，单位分 |
| `payload.channelRefundNo` | string | 是 | 渠道退款交易号 |
| `payload.refundedAt` | string(date-time) | 是 | 渠道退款成功时间 |

处理逻辑：退款单和 Outbox 先提交 → 订单消费者写入 Inbox → 校验订单、支付单和累计退款金额 → 条件更新 `REFUNDING → REFUNDED` 并保存历史 → 提交位点。该事件不默认触发库存回补，是否回库由售后业务单独决定。

### 7.6 `StockRestockRequested`（规划）

用途：售后审核或业务规则确认后，请求库存服务回补商品。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payload.orderNo` | string | 是 | 业务订单号 |
| `payload.reservationNo` | string | 是 | 原库存预扣单号 |
| `payload.reason` | string | 是 | 售后回库原因 |
| `payload.items` | array | 是 | 需要回库的商品明细 |
| `payload.items[].skuId` | string | 是 | SKU ID |
| `payload.items[].quantity` | integer | 是 | 回库数量，正整数 |

处理逻辑：售后决策提交后投递 → 库存消费者写入 Inbox → 校验订单、原预扣单和本次回库数量 → 使用独立回库流水和幂等键增加库存 → 提交位点。不能在支付回调或退款成功监听器中无条件增加库存。

统一约束：

- 每个事件都必须包含通用信封和业务 `payload`，禁止只发一个订单号而没有可追踪的事件 ID。
- 消费失败按第 5 节执行有限重试和死信，不能无限快速重试。
- 同一订单使用 `orderNo` 作为分区键，并通过聚合版本防止旧事件覆盖新状态。
- 生产者、消费者、Inbox/Outbox 和后续事件必须形成可追踪链路。
- 所有业务副作用都要幂等，并能通过日志、指标和补偿任务核对。

## 8. 迁移与联调检查清单

- 新增内部 RPC 是否只有 gRPC 契约，没有重复新增 `/internal/**` REST 路径。
- 内部 REST 骨架是否已列入迁移计划，且未被误认为目标接口。
- gRPC 是否配置服务鉴权、调用截止时间、有限重试和统一错误码。
- 所有写操作是否具备幂等键、请求指纹和冲突处理。
- 三方 HTTP 回调是否与内部 gRPC 明确分离，不能用内部服务身份替代渠道验签。
- Kafka 生产是否在数据库提交后执行，并具备 Outbox。
- 消费失败是否具备有限重试、死信、告警和人工补偿入口。
- 同一聚合的消息是否使用固定分区键，是否有旧版本覆盖新版本的防护。
- 是否验证过重复消息、乱序消息、消费者重启和 Broker 短暂不可用。
- 日志是否包含 `x-request-id`、事件 ID、聚合 ID、Topic、分区和 offset，且不记录密钥或完整敏感报文。
