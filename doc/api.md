# ByteMall 接口文档

## 1. 基本信息

- Base URL：`/api/v1`
- 格式：HTTPS + JSON；请求和响应默认 `application/json; charset=utf-8`。
- 认证：`Authorization: Bearer <accessToken>`；公开接口除外。
- 内部接口：`/internal/**` 不经过买家鉴权，使用独立服务身份并仅在内网暴露。
- 机器契约：[openapi.yaml](./openapi.yaml)。本文解释业务语义，两者不一致时必须一起修正，不能静默选择其一。

### 1.1 通用请求头

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `Authorization` | 非公开接口 | JWT；商家接口还要求有效 `MERCHANT` 角色 |
| `X-Request-Id` | 否 | 客户端可携带合法 UUID；网关生成/透传并写入响应 |
| `Idempotency-Key` | 创建订单/支付及库存命令 | 建议 UUID；同一操作者下唯一，长度 16–64 |
| `Content-Type` | 有请求体时 | `application/json` |

### 1.2 数据规范

- 金额：整数，人民币分；`129900` = ¥1,299.00。
- 时间：带时区 ISO 8601；服务端数据库使用 UTC。
- ID、订单号、支付号：JSON 字符串。
- 分页：`page` 默认 1，`size` 默认 20，范围 1–100；响应含 `page`、`size`、`total`、`items`。
- 商品排序：`RECOMMENDED`、`NEWEST`、`PRICE_ASC`、`PRICE_DESC`、`SALES_DESC`。
- 空筛选参数省略，不传 `"undefined"` 或 `"null"` 字符串。

## 2. 响应、错误与安全

### 2.1 成功响应

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "7e71a260-76d0-4c7b-a20a-c15a82b15a3a"
}
```

创建资源使用 `201`，查询/更新使用 `200`，无返回内容的删除可使用 `204`。即使业务失败，也不能用 HTTP 200 包装所有错误。

### 2.2 错误响应

```json
{
  "code": "STOCK_INSUFFICIENT",
  "message": "部分商品库存不足",
  "details": [
    { "field": "items[0].quantity", "reason": "requested_exceeds_available" }
  ],
  "requestId": "7e71a260-76d0-4c7b-a20a-c15a82b15a3a"
}
```

| HTTP | 稳定错误码（示例） | 含义/客户端处理 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`、`INVALID_ARGUMENT` | 修正字段；不要自动原样重试 |
| 401 | `UNAUTHORIZED`、`TOKEN_EXPIRED` | 重新登录或刷新令牌 |
| 403 | `FORBIDDEN`、`MERCHANT_REQUIRED` | 显示无权，不提供可绕过的重试 |
| 404 | `RESOURCE_NOT_FOUND`、`ORDER_NOT_FOUND` | 资源不存在或对当前操作者不可见 |
| 409 | `IDEMPOTENCY_CONFLICT` | 同键不同请求体，需用户确认 |
| 409 | `STOCK_INSUFFICIENT` | 展示最新库存并允许调整数量 |
| 409 | `INVALID_STATE_TRANSITION` | 刷新资源状态，不重复提交 |
| 409 | `PRODUCT_OFF_SALE` | 返回商品页查看最新状态 |
| 409 | `PAYMENT_AMOUNT_MISMATCH` | 停止支付并核对订单 |
| 410 | `ORDER_EXPIRED` | 返回购物车重新确认 |
| 429 | `RATE_LIMITED` | 按 `Retry-After` 等待 |
| 500 | `INTERNAL_ERROR` | 使用请求 ID 排查，可有限重试读操作 |
| 503 | `SERVICE_UNAVAILABLE` | 稍后重试；支付状态不明时先查询 |

### 2.3 授权和隐私

- 用户只能读写自己的地址、购物车、订单和支付单。
- 商家接口在服务层强制 `merchantId` 数据范围，不能仅依赖前端路由守卫。
- 404 可用于隐藏其他用户资源是否存在；审计日志仍记录真实拒绝原因。
- 密码、完整令牌、支付密钥、完整地址不得进入日志或错误 `details`。
- mock 的 `MOCK` 渠道与 `mock-complete` 只允许开发/测试环境，生产部署必须移除或关闭。

## 3. user-service

### 3.1 认证与用户

| 方法 | 路径 | 权限 | 成功 | 语义与主要错误 |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | 公开 | 201 `AuthResult` | `mobile`、`password`、`nickname`；手机号已存在返回 409 |
| POST | `/auth/login` | 公开 | 200 `AuthResult` | 账号密码登录；失败统一 `INVALID_CREDENTIALS`，避免账号枚举 |
| POST | `/auth/refresh` | 公开（持刷新令牌） | 200 `AuthResult` | 旋转刷新令牌，旧令牌重放应使会话失效 |
| POST | `/auth/logout` | 登录 | 204 | 撤销当前刷新令牌/会话 |
| GET | `/users/me` | 登录 | 200 `User` | 当前用户资料、角色和商家 ID |
| PATCH | `/users/me` | 登录 | 200 `User` | 可修改 `nickname`、`avatarUrl`，不可修改角色 |

`AuthResult`：`accessToken`、`refreshToken`、`expiresIn`（秒）、`tokenType: "Bearer"`、`user`。生产环境优先采用安全 Cookie 管理刷新令牌；如果响应体不返回刷新令牌，应同步调整契约。

### 3.2 地址

| 方法 | 路径 | 权限 | 成功 | 请求体/说明 |
| --- | --- | --- | --- | --- |
| GET | `/users/me/addresses` | BUYER | 200 `Address[]` | 默认地址优先 |
| POST | `/users/me/addresses` | BUYER | 201 `Address` | `AddressInput` |
| PATCH | `/users/me/addresses/{addressId}` | BUYER | 200 `Address` | 部分字段；只能修改本人地址 |
| DELETE | `/users/me/addresses/{addressId}` | BUYER | 204 | 删除不影响历史订单地址快照 |
| POST | `/users/me/addresses/{addressId}/default` | BUYER | 200 `Address` | 原子切换默认地址 |

`AddressInput`：`receiverName`（1–60）、`receiverMobile`（合法手机号）、`province`、`city`、`district`、`detailAddress`（1–300）、`postalCode`（可选）、`isDefault`。姓名不限制为拉丁字符；表单使用正确 `autocomplete`。

### 3.3 商家入驻与店铺

| 方法 | 路径 | 权限 | 成功 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/merchants/applications` | BUYER | 201 `MerchantApplication` | 同一用户只允许一个待审核申请 |
| GET | `/merchants/applications/current` | BUYER | 200 `MerchantApplication` | 无申请返回 `data: null` |
| GET | `/merchant/shop` | MERCHANT | 200 `MerchantShop` | 当前商家店铺 |
| PATCH | `/merchant/shop` | MERCHANT | 200 `MerchantShop` | 店铺名、Logo、联系信息；不修改审核状态 |

入驻申请字段：`shopName`、`contactName`、`contactMobile`、`licenseNo`。审核接口属于平台运营后台，当前不开放给商家自行审核。

## 4. product-service

### 4.1 买家商品

| 方法 | 路径 | 权限 | 成功 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/categories` | 公开 | 200 `Category[]` | 仅启用分类，可按树返回 |
| GET | `/products` | 公开 | 200 `Page<ProductSummary>` | 非搜索的基础列表；固定过滤 `ON_SALE` |
| GET | `/products/{spuId}` | 公开 | 200 `ProductDetail` | 含图片、规格和 SKU；下架商品对游客返回 404 |

`ProductSummary`：`spuId`、`title`、`subtitle`、`brand`、`categoryId`、`coverImage`、`minPrice`、`maxPrice`、`salesCount`、`status`、`publishedAt`。

`ProductDetail` 额外包含 `descriptionHtml`、`images`、`skus`。SKU：`skuId`、`skuCode`、`specs`、`price`、`originalPrice`、`availableStock`、`enabled`。`availableStock` 是展示用快照，结算必须重新校验。

### 4.2 商家商品

| 方法 | 路径 | 权限 | 成功 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/merchant/products` | MERCHANT | 200 `Page<ProductSummary>` | 只查当前商家，可按状态/关键词筛选 |
| POST | `/merchant/products` | MERCHANT | 201 `ProductDetail` | 创建 SPU 和至少一个 SKU，默认 `DRAFT` |
| GET | `/merchant/products/{spuId}` | MERCHANT | 200 `ProductDetail` | 可读取草稿/下架商品，但限本商家 |
| PATCH | `/merchant/products/{spuId}` | MERCHANT | 200 `ProductDetail` | 携带 `version` 乐观锁；修改不影响订单快照 |
| POST | `/merchant/products/{spuId}/status` | MERCHANT | 200 `ProductDetail` | `{status: ON_SALE|OFF_SALE, version}` |

`ProductInput`：`title`、`subtitle`、`categoryId`、`brand`、`descriptionHtml`、`images`、`skus`、`version`（更新时必填）。每个 SKU 包含 `skuCode`、`specs`、`price`、`originalPrice`、`enabled`；同 SPU 规格组合和 SKU 编码不能重复。

上架前校验：必填资料完整、至少一个启用 SKU、价格非负、主图有效、分类有效。事件由 Outbox 异步同步到 ES，保存/上架响应不承诺搜索索引已更新。

## 5. search-service

| 方法 | 路径 | 权限 | 成功 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/search/products` | 公开 | 200 `SearchResult` | 分词、筛选、聚合、排序、分页 |
| GET | `/search/suggestions` | 公开 | 200 `Suggestion[]` | `q` 至少 1 个字符，最多返回 10 条 |

搜索参数：

| 参数 | 类型/示例 | 说明 |
| --- | --- | --- |
| `q` | string | 可空；空关键词表示筛选浏览 |
| `category` | string | 分类 ID |
| `brand` | string | 品牌精确筛选 |
| `minPrice` / `maxPrice` | integer，分 | 非负，且 `minPrice <= maxPrice` |
| `sort` | 枚举 | 默认 `RECOMMENDED` |
| `page` / `size` | integer | 通用分页 |

`SearchResult` 除分页商品外包含 `aggregations.categories` 和 `aggregations.brands`，每项为 `{value, label, count}`。聚合和商品结果使用同一筛选上下文。搜索服务异常不能导致结算绕过商品校验。

## 6. cart-service

| 方法 | 路径 | 权限 | 成功 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/cart` | BUYER | 200 `Cart` | 返回当前商品快照、选中状态和小计 |
| POST | `/cart/items` | BUYER | 201 `Cart` | `{skuId, quantity}`；已有 SKU 则合并数量 |
| PATCH | `/cart/items/{skuId}` | BUYER | 200 `Cart` | `{quantity?, selected?}`，至少一个字段 |
| DELETE | `/cart/items/{skuId}` | BUYER | 204 | 幂等删除不存在的项可返回 204 |
| POST | `/cart/selection` | BUYER | 200 `Cart` | `{skuIds, selected}` 批量选择 |

数量范围 1–999，合并后超过上限返回 400。读取时实时补齐商品名称、图片、售价、上下架和可售库存；购物车小计仅供展示。订单创建成功后，订单服务发出事件，购物车服务删除已下单项。

## 7. order-service

### 7.1 买家订单

| 方法 | 路径 | 权限 | 成功 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/orders` | BUYER | 201 `Order` | 需 `Idempotency-Key`；重新定价并整体预扣库存 |
| GET | `/orders` | BUYER | 200 `Page<OrderSummary>` | 可按 `status` 筛选，按创建时间倒序 |
| GET | `/orders/{orderNo}` | BUYER | 200 `Order` | 只能读取本人订单 |
| POST | `/orders/{orderNo}/cancel` | BUYER | 200 `Order` | 仅 `PENDING_PAYMENT`；`reason` 可空 |
| POST | `/orders/{orderNo}/confirm` | BUYER | 200 `Order` | 仅 `SHIPPED`；确认收货 |

创建请求：

```json
{
  "addressId": "10001",
  "items": [
    { "skuId": "sku-headphone-black", "quantity": 1 }
  ],
  "remark": "工作日送达"
}
```

创建成功响应 `data` 示例：

```json
{
  "orderNo": "BM202611060001",
  "userId": "1001",
  "merchantId": "2001",
  "status": "PENDING_PAYMENT",
  "itemAmount": 129900,
  "freightAmount": 0,
  "discountAmount": 0,
  "payableAmount": 129900,
  "items": [
    {
      "spuId": "spu-headphone",
      "skuId": "sku-headphone-black",
      "productTitle": "声境 Pro 无线降噪耳机",
      "productImage": "/images/headphone-black.svg",
      "specs": { "颜色": "曜石黑" },
      "unitPrice": 129900,
      "quantity": 1,
      "totalPrice": 129900
    }
  ],
  "expireAt": "2026-11-06T11:00:00+08:00",
  "createdAt": "2026-11-06T10:30:00+08:00"
}
```

多商家商品必须按商家拆单。若客户端需要一次结算，可先创建结算批次；当前 `POST /orders` 只接收同一商家的商品，否则返回 `CROSS_MERCHANT_ORDER_UNSUPPORTED`。

### 7.2 商家订单

| 方法 | 路径 | 权限 | 成功 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/merchant/orders` | MERCHANT | 200 `Page<OrderSummary>` | 仅当前商家，支持状态/关键词 |
| GET | `/merchant/orders/{orderNo}` | MERCHANT | 200 `Order` | 仅当前商家 |
| POST | `/merchant/orders/{orderNo}/shipments` | MERCHANT | 200 `Order` | 仅 `PENDING_SHIPMENT` |

发货请求：`carrier`（1–60）、`trackingNo`（1–64）、`version`（订单版本）。成功写入 `SHIPPED`、发货时间、状态历史并递增版本；版本不匹配返回 `VERSION_CONFLICT`。重复请求只有同一物流单号才可幂等返回，否则 409。

订单状态只能按状态机迁移；所有操作使用条件更新。客户端收到 409 后应重新查询，而不是循环提交。

## 8. stock-service

### 8.1 可售库存与商家库存

| 方法 | 路径 | 权限 | 成功 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/stocks/skus/{skuId}` | 公开 | 200 `StockView` | 仅公开 `skuId`、`availableStock`、`version` |
| GET | `/merchant/stocks` | MERCHANT | 200 `StockItem[]` | 当前商家库存，可按低库存筛选 |
| PATCH | `/merchant/stocks/{skuId}` | MERCHANT | 200 `StockItem` | `{availableDelta, reason, version}`；结果不得为负 |

商家调整库存会记录操作者和流水；不能用库存调整代替已有预扣单的释放/确认。

### 8.2 内部预扣命令

以下接口要求服务身份，不能由浏览器直接调用：

| 方法 | 路径 | 成功 | 语义 |
| --- | --- | --- | --- |
| POST | `/internal/stocks/reservations` | 201 `StockReservation` | 整体预扣多个 SKU |
| POST | `/internal/stocks/reservations/{reservationNo}/confirm` | 200 `StockReservation` | 支付后确认扣减 |
| POST | `/internal/stocks/reservations/{reservationNo}/release` | 200 `StockReservation` | 取消/失败补偿回补 |

预扣请求：`reservationNo`、`orderNo`、`expireAt`、`items[{skuId,quantity}]`，并携带 `Idempotency-Key` 和 `X-Request-Id`。释放请求：`reason`（`USER`、`TIMEOUT`、`ORDER_CREATE_FAILED` 等）。确认和释放均幂等，但终态冲突返回 409。

## 9. pay-service

### 9.1 买家支付

| 方法 | 路径 | 权限 | 成功 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/payments` | BUYER | 201 `Payment` | 需幂等键；校验本人待支付订单和应付金额 |
| GET | `/payments/{payNo}` | BUYER | 200 `Payment` | 查询可信服务端状态 |
| POST | `/payments/{payNo}/close` | BUYER | 200 `Payment` | 仅待支付；已成功的支付不能关闭 |
| POST | `/payments/{payNo}/mock-complete` | BUYER + 非生产 | 200 `Payment` | 模拟回调后的成功状态，用于前端演示 |

创建请求：

```json
{
  "orderNo": "BM202611060001",
  "channel": "MOCK"
}
```

`Payment`：`payNo`、`orderNo`、`channel`、`amount`、`status`、`cashierToken`（仅 mock/渠道所需）、`expireAt`、`paidAt`、`createdAt`。支付按钮必须显示确切金额。客户端不能传金额，也不能通过 mock-complete 指定金额。

创建支付时如果已有同订单有效支付单，返回现有单；若旧单已失效可创建新单。支付渠道超时保持 `PENDING`，先查单，禁止立即创建另一笔可能造成重复扣款的支付。

### 9.2 支付回调

`POST /internal/payment-callbacks/{channel}`，公开给对应渠道通知入口但不要求用户 Bearer。必须：

1. 使用渠道证书/公钥验证签名，验证商户号、订单号、金额、币种和通知状态。
2. 校验时间戳/通知 ID 并持久化去重，防止重放；重复且内容相同返回渠道要求的成功应答。
3. 本地事务更新支付单与 Outbox；业务订单通过 `PaymentSucceeded` 事件更新。
4. 验签失败、金额不符或未知订单返回失败并告警，不能标记成功。
5. 渠道超时按渠道协议重试；服务端消费必须幂等，响应体使用渠道规定格式而非统一 API 包装。

### 9.3 对账

| 方法 | 路径 | 权限 | 成功 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/merchant/reconciliations` | MERCHANT | 200 `ReconciliationRecord[]` | 当前商家按日期查询对账差异 |
| POST | `/internal/payments/reconciliation-jobs` | 服务身份 | 202 | `{channel,bizDate}` 启动异步对账 |

对账任务拉取渠道账单，与本地支付/退款按交易号核对。差异进入 `reconciliation_record`，自动修复只能执行明确且幂等的规则，其他情况人工复核。

## 10. 幂等、并发与重试

- 订单/支付：服务端为操作者 + 幂等键建立唯一记录，同时保存请求指纹和响应。相同键相同请求返回首次结果，不同请求返回 `IDEMPOTENCY_CONFLICT`。
- 库存：MySQL 预扣单唯一约束为永久依据，Redis 操作标记只是优化。
- 商家更新：带 `version`；版本不匹配返回 `409 VERSION_CONFLICT`，客户端刷新后由用户确认合并。
- GET 可有限重试；没有幂等键的 POST 不自动重试。支付结果未知时先查询，不以超时判断失败。
- 所有 MQ 消费者按至少一次投递实现 Inbox 去重；状态迁移还需条件更新抵御重复和乱序。

## 11. 前端接入映射

| 页面 | 主要接口 |
| --- | --- |
| 首页 | `GET /products`、`GET /categories` |
| 搜索 | `GET /search/products`、`GET /search/suggestions` |
| 商品详情 | `GET /products/{spuId}`；库存由详情聚合或 `/stocks/skus/{skuId}` 补充 |
| 购物车 | `/cart` 系列 |
| 结算 | `/users/me/addresses`、`POST /orders` |
| 收银台 | `POST /payments`、`GET /payments/{payNo}`；演示环境使用 mock-complete |
| 买家订单 | `/orders` 系列 |
| 商家商品 | `/merchant/products` 系列 |
| 商家库存 | `/merchant/stocks` 系列 |
| 商家订单 | `/merchant/orders` 系列、发货接口 |

本地 mock 应与上述 DTO 和状态规则保持一致；真实 HTTP 适配器统一处理响应包、错误码、请求 ID 和认证刷新。
