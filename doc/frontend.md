# ByteMall 前端设计

## 1. 交付形态

`web` 是一个 React + TypeScript + Rspack 工程，包含买家响应式 Web 与 `/merchant` 商家后台。双端共享领域类型、异步服务接口和 mock 数据，但使用独立布局与路由级懒加载。商家后台使用 Ant Design，买家端使用语义 HTML 与原生 CSS，不引入额外 CSS 框架。

当前不交付 React Native 或原生 App。移动端通过响应式 Web 验证；未来 App 可复用 API 契约与领域类型。

## 2. 页面与路由

### 2.1 买家端

| 路由 | 页面 | 访问 | 核心内容/操作 |
| --- | --- | --- | --- |
| `/` | 首页 | 公开 | 品牌主视觉、分类、推荐/新品、商家入口 |
| `/search` | 搜索列表 | 公开 | 关键词、分类/品牌/价格筛选、排序、分页、空结果 |
| `/product/:spuId` | 商品详情 | 公开 | 画廊、SPU 信息、SKU 规格、动态价格库存、加购 |
| `/cart` | 购物车 | 买家 | 勾选、数量、删除、金额汇总、去结算 |
| `/checkout` | 确认订单 | 买家 | 地址、按商家分组、价格快照提示、幂等提交 |
| `/payment/:orderNo` | 模拟收银台 | 买家 | 应付金额、模拟渠道、支付状态、轮询式反馈 |
| `/orders` | 订单列表 | 买家 | 状态筛选、订单卡片、继续支付/取消 |
| `/orders/:orderNo` | 订单详情 | 买家 | 商品/地址快照、金额、状态时间线、确认收货 |
| `/account` | 个人中心 | 买家 | 资料、地址增删改、默认地址、mock 会话 |
| `/login` | 登录/注册 | 公开 | 演示身份选择、可见表单标签与校验 |
| `/merchant/apply` | 商家入驻 | 买家 | 店铺与联系信息、申请状态；不是商家工作台 |
| `*` | 404 | 公开 | 返回首页与搜索入口 |

搜索参数写入 URL：`q`、`category`、`brand`、`minPrice`、`maxPrice`、`sort`、`page`。价格为界面元值，进入服务层时转换为分。

### 2.2 商家后台

| 路由 | 页面 | 核心内容/操作 |
| --- | --- | --- |
| `/merchant` | 经营看板 | 销售额、订单、商品、低库存、趋势图与数据表 |
| `/merchant/products` | 商品列表 | 本商家商品筛选、状态、库存汇总、编辑/上下架 |
| `/merchant/products/new` | 新建商品 | SPU 信息、图片、动态 SKU 行 |
| `/merchant/products/:spuId/edit` | 编辑商品 | 同上，版本冲突提示由真实 API 负责 |
| `/merchant/orders` | 订单管理 | 本商家订单、状态筛选、发货入口 |
| `/merchant/orders/:orderNo` | 订单详情 | 快照、状态时间线、物流表单 |
| `/merchant/inventory` | 库存管理 | 可售/预扣/总量、库存调整、低库存提示 |
| `/merchant/shop` | 店铺资料 | 店铺信息、入驻状态、演示身份 |

商家路由要求 mock 会话包含 `MERCHANT`。路由守卫只改善体验；真实安全必须由 API 服务和 `merchantId` 数据范围保证。

## 3. 信息架构与组件边界

```text
App
├── AppStoreProvider（会话、商品、购物车、订单、库存）
├── BuyerLayout
│   ├── BuyerHeader（导航、搜索、购物车数量）
│   ├── ProductCard / Price / StockBadge
│   ├── SkuSelector / QuantityStepper
│   ├── AddressForm / OrderSummary / StatusTimeline
│   └── BuyerFooter
├── MerchantLayout（Ant Design）
│   ├── MerchantSidebar / MerchantHeader
│   ├── MetricCard / SalesChart
│   ├── ProductTable / SkuEditor
│   └── OrderTable / ShipmentForm
└── Shared UI
    ├── LoadingState / EmptyState / ErrorState
    ├── PageHeader / Pagination
    └── LiveAnnouncer
```

页面负责编排和路由参数；服务层负责异步数据访问；store 负责共享状态与持久化；展示组件不直接读写 localStorage。价格格式化、状态文案等纯函数集中管理，不在页面复制规则。

## 4. 服务与状态设计

### 4.1 API 适配层

`src/services/api.ts` 定义领域接口，`mock-adapter.ts` 实现本地演示，`remote-adapter.ts` 实现线上 HTTP。接口均返回 Promise，页面无需感知数据来源。主要契约：

- `searchProducts(query)`：确定性筛选、排序、分页和聚合。
- `getProduct(spuId)`：SPU、SKU、图片和可售状态。
- `createOrder(input, idempotencyKey)`：重新定价、库存预扣、订单快照。
- `createPayment(orderNo, channel, idempotencyKey)`：创建/复用支付单。
- 商家商品、库存、发货操作复用同一状态，不使用另一份静态数据。

`npm run dev:mock` 通过 Rspack `--env BYTE_MALL_USE_MOCK=true` 使用本地 mock；`npm run dev` 默认使用线上适配器。线上模式必须设置 `BYTE_MALL_API_BASE_URL`，值包含 `/api/v1`，例如 `BYTE_MALL_API_BASE_URL=https://api.example.com/api/v1 npm run dev`。Rspack 通过 DefinePlugin 注入公开地址与数据源标志；未配置地址时会显示明确错误。浏览器不得保存生产支付密钥或内部服务令牌。

### 4.2 mock 状态

localStorage 键：`bytemall-demo-v1`。持久化内容只包含虚构的演示会话、购物车、商品、订单和库存；提供重置演示数据入口。JSON 解析失败或版本不匹配时回退到种子数据并给出提示，不让应用白屏。

订单创建和支付生成幂等键并在一次有效提交后锁定按钮。订单创建时记录价格/商品/地址快照；库存不足拒绝建单。取消仅在待支付状态执行并幂等回补，支付后将预扣确认，发货和确认收货遵守状态机。

## 5. 视觉设计

### 5.1 买家端

风格定位为“精选生活方式电商”：暖白画布、深墨色文字、电光蓝品牌色、珊瑚色行动强调，使用大留白、清晰商品摄影/插画和克制圆角。首页强调品牌与分类，交易页减少装饰并突出金额和下一步。

| Token | 值 | 用途 |
| --- | --- | --- |
| `--color-bg` | `#f7f7f2` | 页面背景 |
| `--color-surface` | `#ffffff` | 卡片/表单 |
| `--color-ink` | `#172033` | 主文字 |
| `--color-muted` | `#667085` | 次要文字 |
| `--color-brand` | `#3155e7` | 品牌/链接/主按钮 |
| `--color-accent` | `#e65332` | 价格/重要行动 |
| `--color-line` | `#e4e7ec` | 分隔线 |
| `--color-success` | `#16794c` | 成功状态 |
| `--color-danger` | `#b42318` | 错误状态 |

字体使用系统中文无衬线栈；正文至少 16px，标题通过 `clamp()` 有界缩放。内容最大宽度 1280px，卡片圆角 16–24px，阴影仅用于层级而非所有容器。

### 5.2 商家后台

使用 Ant Design 默认交互模式，主色与品牌蓝一致，侧栏深墨色、内容区浅灰白。优先信息密度、可扫描表格和明确状态，不照搬买家首页的大卡片。趋势图使用内联 SVG，必须附文字摘要和数据表，不依赖颜色单独表达状态。

## 6. 响应式与交互状态

- 桌面 `≥1200px`：首页四列商品，详情双栏，商家固定侧栏 + 数据表格。
- 平板 `768–1199px`：商品三列，筛选可折叠，商家侧栏可收起。
- 手机 `<768px`：商品两列或交易列表单列，底部/折叠导航，表格转为卡片或有标签的可滚动区域。
- 优先使用 Grid/Flex、`auto-fit` 和容器查询；移动视口高度使用 `dvh`，图片用 `aspect-ratio` 预留空间。
- 触控目标建议至少 48px，绝不关闭页面缩放。
- 所有异步视图具备加载、空、错误、成功状态；错误不会随机出现，支持明确重试。
- 提交按钮使用动作文案；有效提交后防重，校验失败时聚焦错误摘要或首个错误。
- 图片提供有含义的 `alt`，装饰图片使用空 `alt`；首屏主图不懒加载，离屏商品图懒加载并声明尺寸。

## 7. 可访问性

- 页面声明 `lang="zh-CN"`，每个视图有唯一标题和一个主 `h1`，标题层级连续。
- 使用 `header/nav/main/footer/search` 等语义区域，重复导航前提供“跳到主要内容”链接。
- 导航使用链接，动作使用按钮；不为原生控件重复添加 ARIA 角色。
- 所有表单控件有持久可见标签和 `name`；帮助/错误通过 `aria-describedby` 关联，动态错误使用适当 live region。
- 登录/地址使用正确 `autocomplete`，允许密码管理器和粘贴；不使用占位符代替标签。
- 使用 `:focus-visible` 和 `outline-offset`；DOM 顺序与视觉/键盘顺序一致。
- 弹窗优先原生 `dialog` 或 Ant Design 已验证的焦点管理；关闭后焦点返回触发控件。
- 状态除颜色外同时提供文字/图标；图表提供数据表；检查减少动画偏好与强制颜色模式。

## 8. 演示流程

### 买家

1. 首页搜索关键词并筛选，打开商品。
2. 选择完整 SKU，确认动态价格/库存，加入购物车。
3. 勾选商品并结算，选择地址后创建订单。
4. 在收银台执行明确标注的模拟支付，进入订单详情。
5. 刷新页面，购物车和订单状态仍保持；待支付订单可取消并回补库存。

### 商家

1. 以演示商家身份进入 `/merchant`，查看指标和低库存。
2. 新建/编辑 SPU/SKU 或调整库存，在买家端验证变化。
3. 找到本商家已支付订单，填写虚构物流单号并发货。
4. 买家端查看发货状态并确认收货。

## 9. 运行与验收

```bash
cd web
npm install
npm run dev:mock
npm run lint
npm run build
npm run preview
```

验收至少覆盖：

- lint、生产构建和 OpenAPI 校验通过。
- 在生产预览中完成买家与商家两条流程，而非仅检查首页。
- 1440×900 和 390×844 无异常横向溢出，直接访问路由与 404 正常。
- 键盘可完成导航、搜索、SKU 选择、表单、弹窗和提交，焦点清晰。
- 重复提交、无库存、空搜索、空购物车、非法状态迁移、角色越权和刷新恢复均得到可理解反馈。
- 浏览器控制台无未处理异常；断网不影响本地 mock 核心演示。

真实后端、短信、对象存储、支付沙箱、服务端权限与真实库存一致性不在前端验收的“已完成”范围内。
