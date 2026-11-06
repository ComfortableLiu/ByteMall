# ByteMall 数据模型设计

## 1. 通用约定

- MySQL 8+，默认 `InnoDB`、`utf8mb4`；数据库字段使用 `snake_case`。
- 主键建议使用无业务含义的 `BIGINT UNSIGNED` 或 UUID/ULID 的二进制表示；对外统一序列化为字符串。
- 业务单号如 `order_no`、`pay_no` 使用带校验规则的字符串并建立唯一索引。
- 金额使用 `BIGINT`，单位人民币分，禁止 `FLOAT`/`DOUBLE`；库存使用非负整数。
- 时间统一以 UTC 写入 `DATETIME(6)`，接口输出带时区 ISO 8601。
- 业务表包含 `created_at`、`updated_at`；需要乐观锁的表包含 `version BIGINT NOT NULL DEFAULT 0`。
- 软删除只用于资料类数据；订单、支付、库存流水不物理删除。
- 下列字段是核心基线，不代表可省略建表评审、迁移脚本和容量评估。

## 2. 数据归属总览

| 服务/存储 | 表或对象 |
| --- | --- |
| user-service | `user_account`、`user_address`、`merchant`、`merchant_application` |
| product-service | `category`、`product_spu`、`product_sku`、`product_image`、`outbox_event` |
| order-service | `order_main`、`order_item`、`order_status_history`、`outbox_event`、`inbox_event` |
| stock-service | `stock_main`、`stock_reservation`、`stock_reservation_item`、`stock_log`、`outbox_event`、`inbox_event` |
| pay-service | `payment_order`、`payment_transaction`、`refund_order`、`reconciliation_record`、`outbox_event`、`inbox_event` |
| cart-service / Redis | `cart:{userId}` Hash |
| search-service / ES | `product-search-v*` 索引 |

Outbox/Inbox 表逻辑相同但由各服务私有，不能跨服务共用一张物理表。

## 3. user-service

### 3.1 `user_account`

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT UNSIGNED | PK |
| `mobile` | VARCHAR(20) | 可空；加密/脱敏策略由安全设计确定 |
| `email` | VARCHAR(254) | 可空 |
| `password_hash` | VARCHAR(255) | 不返回给前端 |
| `nickname` | VARCHAR(60) | NOT NULL |
| `avatar_url` | VARCHAR(500) | 可空 |
| `status` | VARCHAR(20) | `ACTIVE`/`DISABLED` |
| `token_version` | BIGINT | 注销或重置后递增 |
| `created_at` / `updated_at` | DATETIME(6) | NOT NULL |

对规范化后的 `mobile`、`email` 分别建唯一索引；允许为空时使用数据库约束与应用校验共同避免重复。

### 3.2 `user_address`

核心字段：`id`、`user_id`、`receiver_name`、`receiver_mobile`、`province`、`city`、`district`、`detail_address`、`postal_code`、`is_default`、`created_at`、`updated_at`。

索引：`idx_address_user (user_id, is_default)`。设置默认地址在一个事务中取消旧默认值并设置新值；同一用户建议最多 20 条。订单必须复制地址快照，不能通过外键依赖可变的地址表。

### 3.3 `merchant` 与 `merchant_application`

`merchant`：`id`、`owner_user_id`、`shop_name`、`logo_url`、`contact_name`、`contact_mobile`、`status`（`PENDING`/`ACTIVE`/`SUSPENDED`）、`created_at`、`updated_at`。`owner_user_id` 唯一；当前实现限定一个用户一个店铺。

`merchant_application`：`id`、`user_id`、`shop_name`、`contact_name`、`contact_mobile`、`license_no`、`status`（`PENDING`/`APPROVED`/`REJECTED`）、`reject_reason`、`reviewed_by`、`reviewed_at`、`created_at`、`updated_at`。同一用户只能有一条 `PENDING`，通过唯一约束/事务防止重复申请。证件号属于敏感信息，接口默认不回显完整值。

## 4. product-service

### 4.1 `category`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT UNSIGNED | PK |
| `parent_id` | BIGINT UNSIGNED | 顶级为 0 或 NULL，全库统一 |
| `name` | VARCHAR(80) | 分类名 |
| `level` | TINYINT | 当前限制最多三级 |
| `sort_order` | INT | 同级排序 |
| `enabled` | BOOLEAN | 停用不影响历史订单快照 |

唯一索引：`uk_category_parent_name (parent_id, name)`；列表索引：`(parent_id, sort_order)`。

### 4.2 `product_spu`

核心字段：`id`、`merchant_id`、`category_id`、`title`、`subtitle`、`brand`、`description_html`、`status`（`DRAFT`/`ON_SALE`/`OFF_SALE`）、`version`、`published_at`、`created_at`、`updated_at`。

- `description_html` 必须在写入前净化。
- 索引：`idx_spu_merchant_status (merchant_id, status, updated_at)`、`idx_spu_category_status (category_id, status)`。
- 上下架通过 `status + version` 条件更新，避免覆盖并发编辑。

### 4.3 `product_sku`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT UNSIGNED | PK，对外为字符串 |
| `spu_id` | BIGINT UNSIGNED | 商品归属 |
| `sku_code` | VARCHAR(64) | 商家内唯一编码 |
| `spec_json` | JSON | 如 `{ "颜色": "黑色", "容量": "256GB" }` |
| `price` | BIGINT | 当前售价，单位分，非负 |
| `original_price` | BIGINT | 可空，单位分 |
| `enabled` | BOOLEAN | 禁用 SKU 不可新下单 |
| `version` | BIGINT | 乐观锁 |
| `created_at` / `updated_at` | DATETIME(6) | 审计 |

约束/索引：`uk_sku_code (sku_code)` 保证全局 SKU 编码唯一，`idx_sku_spu (spu_id)` 支持商品详情查询。同一 SPU 下规范化后的规格组合必须唯一，可由 `(spu_id, spec_hash)` 唯一约束实现，其中 `spec_hash BINARY(32)` 由规范化后的规格组合计算。

### 4.4 `product_image`

字段：`id`、`spu_id`、`url`、`alt_text`、`sort_order`、`is_primary`、`created_at`。索引 `(spu_id, sort_order)`。URL 必须来自受控上传域名；图片替代文本用于可访问性。

## 5. order-service

### 5.1 `order_main`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT UNSIGNED | PK |
| `order_no` | VARCHAR(32) | UNIQUE |
| `user_id` | BIGINT UNSIGNED | 买家 |
| `merchant_id` | BIGINT UNSIGNED | 当前版本一个订单只属于一个商家 |
| `status` | VARCHAR(32) | 订单状态机枚举 |
| `item_amount` | BIGINT | 商品金额合计，分 |
| `freight_amount` | BIGINT | 运费，分 |
| `discount_amount` | BIGINT | 优惠，分；当前默认 0 |
| `payable_amount` | BIGINT | `item + freight - discount` |
| `address_snapshot` | JSON | 收件人、电话、完整地址 |
| `cancel_reason` | VARCHAR(64) | 可空 |
| `expire_at` | DATETIME(6) | 待支付截止时间 |
| `paid_at` / `shipped_at` / `completed_at` / `cancelled_at` | DATETIME(6) | 可空 |
| `version` | BIGINT | 状态条件更新 |
| `created_at` / `updated_at` | DATETIME(6) | NOT NULL |

索引：`uk_order_no (order_no)`、`idx_order_user (user_id, created_at, id)`、`idx_order_merchant_status (merchant_id, status, created_at)`、`idx_order_expire (status, expire_at)`。

多商家购物车按 `merchant_id` 拆成独立订单；结算页可展示一个结算批次号，但支付金额必须能追溯到各订单。

### 5.2 `order_item`

字段：`id`、`order_id`、`order_no`、`spu_id`、`sku_id`、`merchant_id`、`product_title`、`product_image`、`spec_snapshot` JSON、`unit_price` BIGINT、`quantity` INT、`total_price` BIGINT、`created_at`。

唯一索引：`uk_order_sku (order_id, sku_id)`；商品查询索引：`idx_item_sku (sku_id, created_at)`。名称、图片、规格和单价均为下单时快照，之后商品修改不影响历史订单。

### 5.3 `order_status_history`

字段：`id`、`order_id`、`from_status`、`to_status`、`operator_type`（`BUYER`/`MERCHANT`/`SYSTEM`/`EVENT`）、`operator_id`、`reason`、`request_id`、`event_id`、`created_at`。索引 `(order_id, created_at)`、`(event_id)`。

### 5.4 幂等请求

订单创建持久化 `idempotency_key`、`user_id`、`request_hash`、`resource_id`、`response_status`、`response_snapshot`、`created_at`、`expires_at`，唯一键 `(user_id, idempotency_key)`。保留期至少覆盖客户端最大重试窗口；重要订单建议长期保留或归档而非仅依赖 Redis。

## 6. stock-service

### 6.1 `stock_main`

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `sku_id` | BIGINT UNSIGNED | PK/UNIQUE |
| `merchant_id` | BIGINT UNSIGNED | 数据隔离与后台索引 |
| `total` | INT | 实物总量，非负 |
| `available` | INT | 可售量，非负 |
| `reserved` | INT | 预扣未确认量，非负 |
| `version` | BIGINT | 乐观锁与缓存版本 |
| `updated_at` | DATETIME(6) | 变更时间 |

不变量：`total = available + reserved`（已确认销售在业务出账后可通过独立扣减流水调整 total；若采用该口径需全库统一）。更直观的当前实现可将 `total` 定义为现存实物量，确认时同时减少 `total` 与 `reserved`，保持 `available + reserved = total`。库存服务必须集中维护该不变量。

索引：`(merchant_id, updated_at)`；低库存查询可维护投影或使用 `(merchant_id, available)`，避免在订单热表上做复杂报表。

### 6.2 预扣单与明细

`stock_reservation`：`id`、`reservation_no` UNIQUE、`order_no`、`status`（`RESERVED`/`CONFIRMED`/`RELEASED`）、`reason`、`expire_at`、`confirmed_at`、`released_at`、`created_at`、`updated_at`。索引 `(order_no)`、`(status, expire_at)`。

`stock_reservation_item`：`id`、`reservation_id`、`sku_id`、`quantity`、`created_at`，唯一键 `(reservation_id, sku_id)`。重复请求通过预扣单号和明细指纹核对，键相同但数量不同返回幂等冲突。

### 6.3 `stock_log`

字段：`id`、`sku_id`、`reservation_no`、`operation`（`RESERVE`/`CONFIRM`/`RELEASE`/`ADJUST`）、`available_delta`、`reserved_delta`、`before_available`、`after_available`、`operator_id`、`request_id`、`created_at`。唯一约束 `(reservation_no, sku_id, operation)` 防止重复流水；索引 `(sku_id, created_at)`。

## 7. pay-service

### 7.1 `payment_order`

核心字段：`id`、`pay_no` UNIQUE、`order_no`、`user_id`、`channel`（`ALIPAY`/`WECHAT`/`MOCK`）、`amount` BIGINT、`status`（`PENDING`/`SUCCESS`/`FAILED`/`CLOSED`）、`channel_trade_no`、`expire_at`、`paid_at`、`created_at`、`updated_at`、`version`。

索引：`idx_pay_order_status (order_no, status)` 支持查询；允许旧支付单关闭/失败后重新创建，因此 `order_no` 不设全局唯一约束。创建新单时在本地事务中锁定订单并检查是否已有 `PENDING`/`SUCCESS` 支付单，防止并发产生多个有效单；`channel + channel_trade_no` 唯一。

### 7.2 `payment_transaction`

字段：`id`、`pay_no`、`notify_id`、`transaction_type`（`PAY`/`CLOSE`/`REFUND`）、`request_payload`、`response_payload`、`verify_result`、`created_at`。`notify_id` 唯一；报文保存前脱敏并设置保留期限。

### 7.3 `refund_order`

字段：`id`、`refund_no` UNIQUE、`pay_no`、`order_no`、`amount` BIGINT、`reason`、`status`（`PENDING`/`SUCCESS`/`FAILED`）、`channel_refund_no`、`created_at`、`updated_at`。累计退款额不得超过已支付金额；创建退款与支付单需在同一事务核对。

### 7.4 `reconciliation_record`

字段：`id`、`channel`、`channel_trade_no`、`pay_no`、`biz_date`、`channel_amount`、`local_amount`、`status`（`MATCHED`/`DIFFERENT`/`MISSING_LOCAL`/`MISSING_CHANNEL`）、`resolution`、`resolved_by`、`created_at`、`resolved_at`。唯一键 `(channel, biz_date, channel_trade_no)`。

## 8. 通用 Outbox / Inbox

### `outbox_event`

| 字段 | 说明 |
| --- | --- |
| `id` / `event_id` | 内部主键 / 全局事件 ID，后者唯一 |
| `event_type` | 如 `OrderCreated` |
| `aggregate_type` / `aggregate_id` | 聚合类型与业务 ID |
| `aggregate_version` | 聚合版本，消费者可识别旧事件 |
| `payload` JSON | 仅包含契约允许字段 |
| `status` | `NEW`/`PUBLISHED`/`FAILED` |
| `retry_count` / `next_retry_at` | 重试控制 |
| `occurred_at` / `published_at` | 发生与发布时间 |

索引：`(status, next_retry_at)`、`(aggregate_type, aggregate_id, aggregate_version)`。

### `inbox_event`

字段：`id`、`event_id`、`consumer`、`payload_hash`、`processed_at`，唯一键 `(event_id, consumer)`。消费者先插入去重记录，再在同一事务处理业务；若业务失败，事务整体回滚。

## 9. Redis 设计

| Key | 类型 | 内容 | 过期/恢复 |
| --- | --- | --- | --- |
| `cart:{userId}` | Hash | field=`skuId`，value=`{quantity,selected,addedAt}` | 默认不过期；AOF；保存的是意图而非成交价 |
| `product:detail:{spuId}` | String JSON | 商品详情读模型 | 30 分钟 + 10% 抖动；MySQL 重建 |
| `stock:sku:{skuId}` | Hash | `available`、`reserved`、`version` | 无固定 TTL 或较短 TTL；MySQL 重建 |
| `stock:op:{reservationNo}:{skuId}` | String | Lua 操作类型与结果 | 7 天；MySQL 幂等记录兜底 |
| `search:suggest:{prefixHash}` | String JSON | 搜索建议 | 5 分钟；ES 重建 |
| `idempotency:{actor}:{key}` | String | 短期幂等结果 | 24 小时；订单/支付仍以 MySQL 为准 |

Key 必须带业务前缀，不记录完整手机号或地址。购物车 Hash 使用 `HSET`/`HGETALL` 等命令；库存 Hash 的多字段修改只能经 Lua 原子执行。缓存重建使用单航班或短锁，锁必须带唯一持有者值和安全过期。

## 10. Elasticsearch 商品索引

索引名使用 `product-search-v1`，通过别名 `product-search` 切换重建版本。建议字段：

| 字段 | 类型 | 用途 |
| --- | --- | --- |
| `spuId` / `merchantId` / `categoryId` | keyword | 精确过滤与路由到详情 |
| `title` | text | `ik_max_word` 建索引、`ik_smart` 查询；保留 `keyword` 子字段用于必要聚合 |
| `subtitle` / `brandName` | text + keyword | 搜索；品牌 keyword 聚合 |
| `descriptionText` | text | 去 HTML 后的正文，不索引敏感信息 |
| `skus.skuId` | keyword | SKU 精确匹配 |
| `skus.specText` | text/keyword | 规格检索和筛选 |
| `minPrice` / `maxPrice` | integer | 单位分，价格区间与排序 |
| `salesCount` | integer | 销量排序，可由事件投影更新 |
| `status` | keyword | 买家查询固定过滤 `ON_SALE` |
| `publishedAt` / `updatedAt` | date | 新品排序与增量同步 |
| `version` | long | 防止旧事件覆盖新文档 |

未安装 IK 时可评估 `smartcn`，但不能在文档与集群实际插件不一致时直接上线。所有买家查询强制 `status=ON_SALE`；价格区间、分类和品牌聚合在同样过滤条件下计算。分页限制 `page × size` 深度，深分页改用 `search_after`。

## 11. 数据校验与迁移检查

上线前至少验证：

- 金额均为整数分，订单项之和与订单应付金额可对账。
- 订单、支付、预扣的业务单号和幂等约束在并发下无重复。
- 库存任意操作后无负值，Redis 版本不会覆盖更新的 MySQL 数据。
- 商家所有查询都有 `merchant_id` 数据范围，历史订单商品/地址快照不随原资料变化。
- Outbox 业务提交与事件记录同事务，Inbox 去重与业务处理同事务。
- 所有迁移可回滚或有前向兼容方案；新增枚举先发布消费者，再发布生产者。
