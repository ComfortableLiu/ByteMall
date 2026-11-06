import type { AppState, Order, ProductDetail, ProductImage, ProductSku, StockRecord } from '../types/domain.ts'

const image = (url: string, altText: string): ProductImage[] => [
  { url, altText, sortOrder: 0, isPrimary: true },
]

const sku = (
  skuId: string,
  skuCode: string,
  specs: Record<string, string>,
  price: number,
  originalPrice?: number,
): ProductSku => ({ skuId, skuCode, specs, price, originalPrice, availableStock: 0, enabled: true })

const product = (
  spuId: string,
  title: string,
  subtitle: string,
  brand: string,
  categoryId: string,
  imageUrl: string,
  skus: ProductSku[],
  salesCount: number,
  publishedAt: string,
  merchantId = 'merchant-001',
): ProductDetail => ({
  spuId,
  merchantId,
  title,
  subtitle,
  brand,
  categoryId,
  coverImage: imageUrl,
  minPrice: Math.min(...skus.map((item) => item.price)),
  maxPrice: Math.max(...skus.map((item) => item.price)),
  salesCount,
  status: 'ON_SALE',
  publishedAt,
  descriptionHtml: `${title}以耐用材质、克制设计和可靠的日常体验为出发点。${subtitle}。支持七天无理由退货，具体以商品售后政策为准。`,
  images: image(imageUrl, title),
  skus,
  version: 1,
})

const products: ProductDetail[] = [
  product('spu-headphone', '声境 Pro 无线降噪耳机', '40 小时续航，自适应深度降噪', '声境', 'cat-digital', '/images/headphones.svg', [
    sku('sku-headphone-black', 'SJ-PRO-BK', { 颜色: '曜石黑' }, 129900, 149900),
    sku('sku-headphone-white', 'SJ-PRO-WH', { 颜色: '云雾白' }, 132900, 149900),
  ], 2380, '2026-10-18T08:00:00+08:00'),
  product('spu-watch', '岚川 Watch S 智能手表', '全天候健康记录，轻盈铝合金表身', '岚川', 'cat-digital', '/images/watch.svg', [
    sku('sku-watch-green', 'LC-WS-GN', { 表带: '苔原绿', 尺寸: '42mm' }, 159900, 179900),
    sku('sku-watch-silver', 'LC-WS-SV', { 表带: '月光银', 尺寸: '42mm' }, 159900, 179900),
  ], 1260, '2026-10-25T08:00:00+08:00'),
  product('spu-backpack', '原点 城市机能双肩包', '16 英寸电脑仓，防泼水再生面料', '原点', 'cat-travel', '/images/backpack.svg', [
    sku('sku-backpack-green', 'YD-BP-GN', { 颜色: '森野绿', 容量: '20L' }, 45900, 52900),
    sku('sku-backpack-black', 'YD-BP-BK', { 颜色: '岩石黑', 容量: '20L' }, 45900, 52900),
  ], 986, '2026-09-30T08:00:00+08:00'),
  product('spu-coffee', '屿光 意式咖啡机', '20Bar 稳定萃取，紧凑金属机身', '屿光', 'cat-home', '/images/coffee.svg', [
    sku('sku-coffee-silver', 'YG-CF-SV', { 颜色: '暖银' }, 189900, 219900),
    sku('sku-coffee-cream', 'YG-CF-CR', { 颜色: '奶油白' }, 194900, 219900),
  ], 568, '2026-10-08T08:00:00+08:00'),
  product('spu-lamp', '木予 云朵氛围台灯', '三档柔光，让夜晚慢下来', '木予', 'cat-home', '/images/lamp.svg', [
    sku('sku-lamp-cloud', 'MY-LP-CL', { 款式: '云朵', 光源: '暖光' }, 32900, 36900),
    sku('sku-lamp-moon', 'MY-LP-MN', { 款式: '月亮', 光源: '暖光' }, 34900, 38900),
  ], 1750, '2026-09-12T08:00:00+08:00'),
  product('spu-sneakers', '野行 轻量复古跑鞋', '缓震中底与复古轮廓，自在穿行', '野行', 'cat-fashion', '/images/sneakers.svg', [
    sku('sku-sneakers-38', 'YX-RN-38', { 颜色: '燕麦色', 尺码: '38' }, 56900, 65900),
    sku('sku-sneakers-40', 'YX-RN-40', { 颜色: '燕麦色', 尺码: '40' }, 56900, 65900),
    sku('sku-sneakers-42', 'YX-RN-42', { 颜色: '燕麦色', 尺码: '42' }, 56900, 65900),
  ], 832, '2026-10-20T08:00:00+08:00'),
  product('spu-keyboard', '键物 75 三模机械键盘', 'Gasket 结构，热插拔与柔和敲击声', '键物', 'cat-digital', '/images/keyboard.svg', [
    sku('sku-keyboard-blue', 'JW-75-BU', { 配色: '雾蓝', 轴体: '线性轴' }, 79900, 89900),
    sku('sku-keyboard-purple', 'JW-75-PP', { 配色: '烟紫', 轴体: '段落轴' }, 82900, 89900),
  ], 1105, '2026-10-22T08:00:00+08:00'),
  product('spu-speaker', '声境 Mini 便携音箱', '小巧机身，360° 环绕声场', '声境', 'cat-digital', '/images/speaker.svg', [
    sku('sku-speaker-grey', 'SJ-MN-GY', { 颜色: '深空灰' }, 69900, 79900),
  ], 689, '2026-09-26T08:00:00+08:00', 'merchant-002'),
]

const stock = (skuId: string, availableStock: number): StockRecord => ({
  skuId,
  total: availableStock,
  availableStock,
  reservedStock: 0,
  version: 1,
})

const address = {
  receiverName: '林小满',
  receiverMobile: '13800138000',
  province: '上海市',
  city: '上海市',
  district: '徐汇区',
  detailAddress: '漕河泾街道示例路 88 号 5 楼',
  postalCode: '200030',
  isDefault: true,
}

const historyOrder = (
  orderNo: string,
  status: Order['status'],
  sourceProduct: ProductDetail,
  sourceSku: ProductSku,
  createdAt: string,
): Order => {
  const now = new Date(createdAt)
  const history: Order['history'] = [{ fromStatus: null, toStatus: 'PENDING_PAYMENT', operatorType: 'BUYER', reason: '创建订单', createdAt }]
  if (status !== 'PENDING_PAYMENT' && status !== 'CANCELLED') {
    history.push({ fromStatus: 'PENDING_PAYMENT', toStatus: 'PENDING_SHIPMENT', operatorType: 'EVENT', reason: '支付成功', createdAt: new Date(now.getTime() + 300000).toISOString() })
  }
  if (status === 'SHIPPED' || status === 'COMPLETED') {
    history.push({ fromStatus: 'PENDING_SHIPMENT', toStatus: 'SHIPPED', operatorType: 'MERCHANT', reason: '顺丰速运 SF20261026001', createdAt: new Date(now.getTime() + 86400000).toISOString() })
  }
  if (status === 'COMPLETED') {
    history.push({ fromStatus: 'SHIPPED', toStatus: 'COMPLETED', operatorType: 'BUYER', reason: '确认收货', createdAt: new Date(now.getTime() + 345600000).toISOString() })
  }
  return {
    orderNo,
    userId: 'buyer-001',
    merchantId: sourceProduct.merchantId,
    status,
    itemAmount: sourceSku.price,
    freightAmount: 0,
    discountAmount: 0,
    payableAmount: sourceSku.price,
    address,
    items: [{
      spuId: sourceProduct.spuId,
      skuId: sourceSku.skuId,
      productTitle: sourceProduct.title,
      productImage: sourceProduct.coverImage,
      specs: sourceSku.specs,
      unitPrice: sourceSku.price,
      quantity: 1,
      totalPrice: sourceSku.price,
    }],
    carrier: status === 'SHIPPED' || status === 'COMPLETED' ? '顺丰速运' : undefined,
    trackingNo: status === 'SHIPPED' || status === 'COMPLETED' ? 'SF20261026001' : undefined,
    expireAt: new Date(now.getTime() + 1800000).toISOString(),
    paidAt: status !== 'PENDING_PAYMENT' ? new Date(now.getTime() + 300000).toISOString() : undefined,
    shippedAt: status === 'SHIPPED' || status === 'COMPLETED' ? new Date(now.getTime() + 86400000).toISOString() : undefined,
    completedAt: status === 'COMPLETED' ? new Date(now.getTime() + 345600000).toISOString() : undefined,
    createdAt,
    history,
    version: 1,
  }
}

export function createSeedState(): AppState {
  const productCopies = structuredClone(products)
  return {
    schemaVersion: 1,
    currentUser: null,
    users: {
      buyer: { userId: 'buyer-001', nickname: '林小满', roles: ['BUYER'] },
      merchant: { userId: 'merchant-user-001', nickname: 'ByteMall 精选店', roles: ['BUYER', 'MERCHANT'], merchantId: 'merchant-001' },
    },
    categories: [
      { categoryId: 'cat-digital', name: '数码科技', level: 1, icon: 'digital' },
      { categoryId: 'cat-home', name: '居家生活', level: 1, icon: 'home' },
      { categoryId: 'cat-fashion', name: '服饰鞋包', level: 1, icon: 'fashion' },
      { categoryId: 'cat-travel', name: '出行户外', level: 1, icon: 'travel' },
    ],
    products: productCopies,
    stocks: {
      'sku-headphone-black': stock('sku-headphone-black', 18),
      'sku-headphone-white': stock('sku-headphone-white', 7),
      'sku-watch-green': stock('sku-watch-green', 12),
      'sku-watch-silver': stock('sku-watch-silver', 4),
      'sku-backpack-green': stock('sku-backpack-green', 26),
      'sku-backpack-black': stock('sku-backpack-black', 16),
      'sku-coffee-silver': stock('sku-coffee-silver', 5),
      'sku-coffee-cream': stock('sku-coffee-cream', 3),
      'sku-lamp-cloud': stock('sku-lamp-cloud', 0),
      'sku-lamp-moon': stock('sku-lamp-moon', 9),
      'sku-sneakers-38': stock('sku-sneakers-38', 8),
      'sku-sneakers-40': stock('sku-sneakers-40', 14),
      'sku-sneakers-42': stock('sku-sneakers-42', 20),
      'sku-keyboard-blue': stock('sku-keyboard-blue', 9),
      'sku-keyboard-purple': stock('sku-keyboard-purple', 6),
      'sku-speaker-grey': stock('sku-speaker-grey', 15),
    },
    cart: [],
    addresses: [{ addressId: 'address-1', userId: 'buyer-001', ...address }],
    orders: [
      historyOrder('BM202610280001', 'PENDING_SHIPMENT', productCopies[0], productCopies[0].skus[0], '2026-10-28T10:00:00+08:00'),
      historyOrder('BM202610250002', 'SHIPPED', productCopies[2], productCopies[2].skus[0], '2026-10-25T10:00:00+08:00'),
      historyOrder('BM202610180003', 'COMPLETED', productCopies[4], productCopies[4].skus[1], '2026-10-18T10:00:00+08:00'),
    ],
    payments: [],
    reservations: {},
    orderIdempotency: {},
    paymentIdempotency: {},
    applications: [],
    shop: {
      merchantId: 'merchant-001',
      shopName: 'ByteMall 精选店',
      contactName: '周店长',
      contactMobile: '13900139000',
      status: 'ACTIVE',
    },
    sequence: 1004,
  }
}
