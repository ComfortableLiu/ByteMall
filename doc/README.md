# ByteMall 项目文档

ByteMall 是一个多商家电商系统。本目录是产品、前端和后端共同使用的设计基线，覆盖买家购物、商家经营以及支撑这些流程的七个微服务。

## 文档导航

| 文档 | 内容 | 主要读者 |
| --- | --- | --- |
| [系统架构设计](./architecture.md) | 服务边界、核心时序、状态机、一致性、安全与可观测性 | 后端、架构、运维 |
| [数据模型设计](./data-model.md) | MySQL 表、Redis 键、ES 索引、约束与数据归属 | 后端、DBA |
| [接口文档](./api.md) | REST 约定、买家/商家/内部接口、错误与示例 | 前端、后端、测试 |
| [OpenAPI 契约](./openapi.yaml) | 可由工具校验和生成客户端的机器可读契约 | 前端、后端、测试 |
| [前端设计](./frontend.md) | 页面地图、组件、状态、视觉、响应式及验证要求 | 前端、设计、测试 |

建议先读架构和数据模型，再读接口文档。修改接口时必须同步修改 `api.md`、`openapi.yaml` 和前端领域类型，禁止以页面中的临时字段作为隐式契约。

## 当前交付范围

- 买家响应式 Web：登录、搜索、商品详情、SKU 选择、购物车、结算、模拟支付、订单、地址和商家入驻。
- 商家后台：经营看板、SPU/SKU 管理、库存、订单发货和店铺资料。
- 微服务的架构、数据与接口设计，不包含服务端代码。
- `web` 默认使用本地 mock 服务；mock 不代表真实鉴权、支付或分布式事务已经实现。

明确不在当前范围内：原生 App、真实短信、对象存储、物流轨迹、完整售后工单、发票、优惠券、真实支付宝/微信支付及对账任务实现。

## 全局契约

- API 前缀：`/api/v1`；契约版本：OpenAPI 3.1。
- JSON 字段使用 `camelCase`，数据库字段使用 `snake_case`。
- 金额一律为人民币分，JSON 使用整数，例如 `129900` 表示 ¥1,299.00；前端只在展示层格式化。
- ID、用户 ID、商家 ID、订单号、支付号均使用字符串，避免 JavaScript 长整型精度丢失。
- 时间为带时区的 ISO 8601 字符串，例如 `2026-11-06T10:30:00+08:00`；数据库存储统一使用 UTC。
- 公开创建订单、支付及内部库存命令必须支持幂等。
- 列表统一使用 `page`、`size`、`total`、`items`；页码从 1 开始。
- 错误必须包含稳定的机器可读 `code`，用户提示不能替代错误码。

## 核心状态词表

| 资源 | 状态 |
| --- | --- |
| 订单 | `PENDING_PAYMENT`、`PENDING_SHIPMENT`、`SHIPPED`、`COMPLETED`、`CANCELLED`、`REFUNDING`、`REFUNDED` |
| 支付单 | `PENDING`、`SUCCESS`、`FAILED`、`CLOSED` |
| 库存预扣单 | `RESERVED`、`CONFIRMED`、`RELEASED` |
| 商品 | `DRAFT`、`ON_SALE`、`OFF_SALE` |
| 商家入驻 | `PENDING`、`APPROVED`、`REJECTED` |

状态迁移规则见[系统架构设计](./architecture.md)。页面文案可以中文化，但接口和存储只能使用上述稳定枚举。

## 前端运行

```bash
cd web
npm install
npm run dev:mock
```

普通 `npm run dev` 使用线上 API，需要提供包含 `/api/v1` 的网关地址：

```bash
BYTE_MALL_API_BASE_URL=https://api.example.com/api/v1 npm run dev
```

交付检查：

```bash
cd web
npm run lint
npm run build
```

机器契约检查：

```bash
npx -y @redocly/cli@latest lint doc/openapi.yaml
```
