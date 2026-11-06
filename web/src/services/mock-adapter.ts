import { createSeedState } from '../data/seed.ts'
import type {
  Address,
  AddressInput,
  AppState,
  Cart,
  CreateOrderInput,
  DemoRole,
  MerchantApplication,
  MerchantShop,
  MerchantStockItem,
  Order,
  OrderStatus,
  Payment,
  PaymentChannel,
  ProductDetail,
  ProductInput,
  ProductSearchQuery,
  ProductStatus,
  ProductSummary,
  SearchResult,
  StockRecord,
  User,
} from '../types/domain.ts'
import type { LoginOptions } from './api.ts'

export class DomainError extends Error {
  readonly code: string
  readonly status: number

  constructor(code: string, message: string, status = 400) {
    super(message)
    this.name = 'DomainError'
    this.code = code
    this.status = status
  }
}

interface MockOptions {
  persist?: boolean
  latency?: number
}

const clone = <T>(value: T): T => structuredClone(value)
const fingerprint = (value: unknown): string => JSON.stringify(value)

export class MockAdapter {
  readonly dataSource = 'mock' as const
  private state: AppState
  private readonly persist: boolean
  private readonly latency: number
  loadWarning = ''

  constructor(initialState?: AppState, options: MockOptions = {}) {
    this.state = initialState ? clone(initialState) : createSeedState()
    this.persist = options.persist ?? false
    this.latency = options.latency ?? 120
  }

  get snapshot(): AppState {
    return clone(this.state)
  }

  private async wait(): Promise<void> {
    if (this.latency > 0) {
      await new Promise((resolve) => setTimeout(resolve, this.latency))
    }
  }

  private commit(): void {
    if (this.persist && typeof localStorage !== 'undefined') {
      localStorage.setItem('bytemall-demo-v1', JSON.stringify(this.state))
    }
  }

  private currentUser(): User {
    if (!this.state.currentUser) {
      throw new DomainError('UNAUTHORIZED', '请先登录后再继续', 401)
    }
    return this.state.currentUser
  }

  private buyer(): User {
    const user = this.currentUser()
    if (!user.roles.includes('BUYER')) {
      throw new DomainError('FORBIDDEN', '当前身份没有买家权限', 403)
    }
    return user
  }

  private merchant(): User {
    const user = this.currentUser()
    if (!user.roles.includes('MERCHANT') || !user.merchantId) {
      throw new DomainError('MERCHANT_REQUIRED', '请切换为商家身份后继续', 403)
    }
    return user
  }

  private validateIdempotencyKey(key: string): void {
    if (key.length < 16 || key.length > 64) {
      throw new DomainError('VALIDATION_ERROR', '幂等键长度应为 16–64 个字符')
    }
  }

  private findSku(skuId: string) {
    for (const product of this.state.products) {
      const found = product.skus.find((item) => item.skuId === skuId)
      if (found) {
        const stock = this.state.stocks[skuId]
        if (!stock) throw new DomainError('RESOURCE_NOT_FOUND', '库存记录不存在', 404)
        return { product, sku: found, stock }
      }
    }
    throw new DomainError('RESOURCE_NOT_FOUND', '商品规格不存在', 404)
  }

  private toSummary(product: ProductDetail): ProductSummary {
    const enabledPrices = product.skus.filter((item) => item.enabled).map((item) => item.price)
    const prices = enabledPrices.length > 0 ? enabledPrices : product.skus.map((item) => item.price)
    return {
      spuId: product.spuId,
      merchantId: product.merchantId,
      title: product.title,
      subtitle: product.subtitle,
      brand: product.brand,
      categoryId: product.categoryId,
      coverImage: product.coverImage,
      minPrice: Math.min(...prices),
      maxPrice: Math.max(...prices),
      salesCount: product.salesCount,
      status: product.status,
      publishedAt: product.publishedAt,
    }
  }

  private toDetail(product: ProductDetail): ProductDetail {
    const detail = clone(product)
    detail.skus = detail.skus.map((item) => ({
      ...item,
      availableStock: this.state.stocks[item.skuId]?.availableStock ?? 0,
    }))
    return detail
  }

  private nextId(prefix: string): string {
    const value = `${prefix}${String(this.state.sequence).padStart(8, '0')}`
    this.state.sequence += 1
    return value
  }

  private releaseReservation(order: Order, reason: string): void {
    const reservation = this.state.reservations[order.orderNo]
    if (reservation?.status !== 'RESERVED') return
    for (const item of order.items) {
      const stock = this.state.stocks[item.skuId]
      if (!stock) continue
      stock.availableStock += item.quantity
      stock.reservedStock -= item.quantity
      stock.version += 1
    }
    reservation.status = 'RELEASED'
    reservation.reason = reason
  }

  private expireIfNeeded(order: Order): boolean {
    if (order.status !== 'PENDING_PAYMENT' || Date.parse(order.expireAt) > Date.now()) return false
    const now = new Date().toISOString()
    order.status = 'CANCELLED'
    order.cancelReason = '支付超时'
    order.cancelledAt = now
    order.version += 1
    order.history.push({ fromStatus: 'PENDING_PAYMENT', toStatus: 'CANCELLED', operatorType: 'SYSTEM', reason: '支付超时，自动关单', createdAt: now })
    this.releaseReservation(order, 'TIMEOUT')
    for (const payment of this.state.payments) {
      if (payment.orderNo === order.orderNo && payment.status === 'PENDING') payment.status = 'CLOSED'
    }
    return true
  }

  async initialize(): Promise<void> {
    await this.wait()
  }

  async login(role: DemoRole, options?: LoginOptions): Promise<User> {
    void options
    await this.wait()
    this.state.currentUser = clone(this.state.users[role])
    this.commit()
    return clone(this.state.currentUser)
  }

  async logout(): Promise<void> {
    await this.wait()
    this.state.currentUser = null
    this.commit()
  }

  async reset(): Promise<void> {
    await this.wait()
    this.state = createSeedState()
    this.commit()
  }

  async getCategories(): Promise<AppState['categories']> {
    await this.wait()
    return clone(this.state.categories)
  }

  async searchProducts(query: ProductSearchQuery): Promise<SearchResult> {
    await this.wait()
    if (query.page < 1 || query.size < 1 || query.size > 100) {
      throw new DomainError('VALIDATION_ERROR', '分页参数不合法')
    }
    if ((query.minPrice ?? 0) < 0 || (query.maxPrice ?? 0) < 0) {
      throw new DomainError('VALIDATION_ERROR', '价格不能为负数')
    }
    if (query.minPrice !== undefined && query.maxPrice !== undefined && query.minPrice > query.maxPrice) {
      throw new DomainError('VALIDATION_ERROR', '最低价格不能高于最高价格')
    }
    const keyword = query.q?.trim().toLocaleLowerCase('zh-CN') ?? ''
    const filtered = this.state.products.filter((product) => {
      if (product.status !== 'ON_SALE') return false
      const summary = this.toSummary(product)
      const haystack = `${product.title} ${product.subtitle} ${product.brand} ${product.descriptionHtml}`.toLocaleLowerCase('zh-CN')
      if (keyword && !haystack.includes(keyword)) return false
      if (query.category && product.categoryId !== query.category) return false
      if (query.brand && product.brand !== query.brand) return false
      if (query.minPrice !== undefined && summary.maxPrice < query.minPrice) return false
      if (query.maxPrice !== undefined && summary.minPrice > query.maxPrice) return false
      return true
    })
    const sorted = [...filtered]
    switch (query.sort ?? 'RECOMMENDED') {
      case 'PRICE_ASC':
        sorted.sort((a, b) => this.toSummary(a).minPrice - this.toSummary(b).minPrice || a.spuId.localeCompare(b.spuId))
        break
      case 'PRICE_DESC':
        sorted.sort((a, b) => this.toSummary(b).minPrice - this.toSummary(a).minPrice || a.spuId.localeCompare(b.spuId))
        break
      case 'NEWEST':
        sorted.sort((a, b) => b.publishedAt.localeCompare(a.publishedAt) || a.spuId.localeCompare(b.spuId))
        break
      case 'SALES_DESC':
        sorted.sort((a, b) => b.salesCount - a.salesCount || a.spuId.localeCompare(b.spuId))
        break
      default:
        sorted.sort((a, b) => b.salesCount * 0.4 + new Date(b.publishedAt).getTime() / 1e10 - (a.salesCount * 0.4 + new Date(a.publishedAt).getTime() / 1e10) || a.spuId.localeCompare(b.spuId))
    }
    const categoryCounts = new Map<string, number>()
    const brandCounts = new Map<string, number>()
    for (const item of filtered) {
      categoryCounts.set(item.categoryId, (categoryCounts.get(item.categoryId) ?? 0) + 1)
      brandCounts.set(item.brand, (brandCounts.get(item.brand) ?? 0) + 1)
    }
    const start = (query.page - 1) * query.size
    return {
      page: query.page,
      size: query.size,
      total: filtered.length,
      items: sorted.slice(start, start + query.size).map((item) => this.toSummary(item)),
      aggregations: {
        categories: [...categoryCounts].map(([value, count]) => ({
          value,
          label: this.state.categories.find((item) => item.categoryId === value)?.name ?? value,
          count,
        })),
        brands: [...brandCounts].map(([value, count]) => ({ value, label: value, count })),
      },
    }
  }

  async listProducts(query: Partial<ProductSearchQuery> = {}): Promise<SearchResult> {
    return this.searchProducts({ page: 1, size: 20, ...query })
  }

  async getProduct(spuId: string): Promise<ProductDetail> {
    await this.wait()
    const product = this.state.products.find((item) => item.spuId === spuId && item.status === 'ON_SALE')
    if (!product) throw new DomainError('RESOURCE_NOT_FOUND', '商品不存在或已下架', 404)
    return this.toDetail(product)
  }

  async searchSuggestions(q: string): Promise<string[]> {
    await this.wait()
    const keyword = q.trim().toLocaleLowerCase('zh-CN')
    if (!keyword) return []
    return this.state.products
      .filter((item) => item.status === 'ON_SALE' && `${item.title}${item.brand}`.toLocaleLowerCase('zh-CN').includes(keyword))
      .slice(0, 10)
      .map((item) => item.title)
  }

  async getCart(): Promise<Cart> {
    await this.wait()
    const user = this.buyer()
    const items = this.state.cart.filter((entry) => entry.userId === user.userId).map((entry): Cart['items'][number] => {
      const product = this.state.products.find((candidate) => candidate.skus.some((item) => item.skuId === entry.skuId))
      const item = product?.skus.find((candidate) => candidate.skuId === entry.skuId)
      const stock = this.state.stocks[entry.skuId]
      if (!product || !item || !stock) {
        return { ...clone(entry), spuId: '', merchantId: '', productTitle: '失效商品', productImage: '/favicon.svg', specs: {}, unitPrice: 0, availableStock: 0, enabled: false, productStatus: 'OFF_SALE' }
      }
      return {
        ...clone(entry),
        spuId: product.spuId,
        merchantId: product.merchantId,
        productTitle: product.title,
        productImage: product.coverImage,
        specs: clone(item.specs),
        unitPrice: item.price,
        availableStock: stock.availableStock,
        enabled: item.enabled,
        productStatus: product.status,
      }
    })
    const selected = items.filter((item) => item.selected && item.enabled && item.productStatus === 'ON_SALE')
    return {
      items,
      selectedAmount: selected.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0),
      selectedQuantity: selected.reduce((sum, item) => sum + item.quantity, 0),
    }
  }

  async addCartItem(skuId: string, quantity: number): Promise<Cart> {
    await this.wait()
    const user = this.buyer()
    const { product, sku: item, stock } = this.findSku(skuId)
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 999) {
      throw new DomainError('VALIDATION_ERROR', '商品数量应为 1–999 的整数')
    }
    if (product.status !== 'ON_SALE' || !item.enabled) {
      throw new DomainError('PRODUCT_OFF_SALE', '商品已下架或规格不可售', 409)
    }
    const existing = this.state.cart.find((entry) => entry.userId === user.userId && entry.skuId === skuId)
    const nextQuantity = (existing?.quantity ?? 0) + quantity
    if (nextQuantity > 999) throw new DomainError('VALIDATION_ERROR', '单个规格最多添加 999 件')
    if (nextQuantity > stock.availableStock) {
      throw new DomainError('STOCK_INSUFFICIENT', `当前仅可购买 ${stock.availableStock} 件`, 409)
    }
    if (existing) {
      existing.quantity = nextQuantity
      existing.selected = true
    } else {
      this.state.cart.push({ userId: user.userId, skuId, quantity, selected: true, addedAt: new Date().toISOString() })
    }
    this.commit()
    return this.getCart()
  }

  async updateCartItem(skuId: string, patch: { quantity?: number; selected?: boolean }): Promise<Cart> {
    await this.wait()
    const user = this.buyer()
    const entry = this.state.cart.find((item) => item.userId === user.userId && item.skuId === skuId)
    if (!entry) throw new DomainError('RESOURCE_NOT_FOUND', '购物车中没有该商品', 404)
    if (patch.quantity !== undefined) {
      if (!Number.isInteger(patch.quantity) || patch.quantity < 1 || patch.quantity > 999) {
        throw new DomainError('VALIDATION_ERROR', '商品数量应为 1–999 的整数')
      }
      if (patch.quantity > this.findSku(skuId).stock.availableStock) {
        throw new DomainError('STOCK_INSUFFICIENT', '修改后的数量超过可售库存', 409)
      }
      entry.quantity = patch.quantity
    }
    if (patch.selected !== undefined) entry.selected = patch.selected
    this.commit()
    return this.getCart()
  }

  async removeCartItem(skuId: string): Promise<Cart> {
    await this.wait()
    const user = this.buyer()
    this.state.cart = this.state.cart.filter((item) => item.userId !== user.userId || item.skuId !== skuId)
    this.commit()
    return this.getCart()
  }

  async setCartSelection(skuIds: string[], selected: boolean): Promise<Cart> {
    await this.wait()
    const user = this.buyer()
    for (const entry of this.state.cart) {
      if (entry.userId === user.userId && skuIds.includes(entry.skuId)) entry.selected = selected
    }
    this.commit()
    return this.getCart()
  }

  async createOrder(input: CreateOrderInput, idempotencyKey: string): Promise<Order> {
    await this.wait()
    const user = this.buyer()
    this.validateIdempotencyKey(idempotencyKey)
    const key = `${user.userId}:${idempotencyKey}`
    const requestFingerprint = fingerprint(input)
    const previous = this.state.orderIdempotency[key]
    if (previous) {
      if (previous.fingerprint !== requestFingerprint) {
        throw new DomainError('IDEMPOTENCY_CONFLICT', '相同幂等键不能用于不同的订单内容', 409)
      }
      const existing = this.state.orders.find((item) => item.orderNo === previous.resourceId)
      if (!existing) throw new DomainError('INTERNAL_ERROR', '幂等记录对应的订单不存在', 500)
      return clone(existing)
    }
    const address = this.state.addresses.find((item) => item.addressId === input.addressId && item.userId === user.userId)
    if (!address) throw new DomainError('VALIDATION_ERROR', '请选择有效的收货地址')
    if (!input.items.length) throw new DomainError('VALIDATION_ERROR', '订单至少包含一件商品')
    const merged = new Map<string, number>()
    for (const item of input.items) {
      if (!Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 999) {
        throw new DomainError('VALIDATION_ERROR', '商品数量应为 1–999 的整数')
      }
      merged.set(item.skuId, (merged.get(item.skuId) ?? 0) + item.quantity)
    }
    const lines = [...merged].map(([skuId, quantity]) => ({ ...this.findSku(skuId), quantity }))
    const merchantIds = new Set(lines.map((line) => line.product.merchantId))
    if (merchantIds.size !== 1) {
      throw new DomainError('CROSS_MERCHANT_ORDER_UNSUPPORTED', '每个订单只能包含同一商家的商品', 409)
    }
    for (const line of lines) {
      if (line.product.status !== 'ON_SALE' || !line.sku.enabled) {
        throw new DomainError('PRODUCT_OFF_SALE', `${line.product.title} 已下架或规格不可售`, 409)
      }
      if (line.quantity > line.stock.availableStock) {
        throw new DomainError('STOCK_INSUFFICIENT', `${line.product.title} 库存不足，剩余 ${line.stock.availableStock} 件`, 409)
      }
    }
    const orderNo = this.nextId('BM')
    const now = new Date()
    const items = lines.map((line) => ({
      spuId: line.product.spuId,
      skuId: line.sku.skuId,
      productTitle: line.product.title,
      productImage: line.product.coverImage,
      specs: clone(line.sku.specs),
      unitPrice: line.sku.price,
      quantity: line.quantity,
      totalPrice: line.sku.price * line.quantity,
    }))
    const itemAmount = items.reduce((sum, item) => sum + item.totalPrice, 0)
    const addressSnapshot: AddressInput = {
      receiverName: address.receiverName,
      receiverMobile: address.receiverMobile,
      province: address.province,
      city: address.city,
      district: address.district,
      detailAddress: address.detailAddress,
      postalCode: address.postalCode,
      isDefault: address.isDefault,
    }
    const order: Order = {
      orderNo,
      userId: user.userId,
      merchantId: lines[0].product.merchantId,
      status: 'PENDING_PAYMENT',
      itemAmount,
      freightAmount: 0,
      discountAmount: 0,
      payableAmount: itemAmount,
      address: addressSnapshot,
      items,
      remark: input.remark,
      expireAt: new Date(now.getTime() + 30 * 60 * 1000).toISOString(),
      createdAt: now.toISOString(),
      history: [{ fromStatus: null, toStatus: 'PENDING_PAYMENT', operatorType: 'BUYER', reason: '创建订单并完成库存预扣', createdAt: now.toISOString() }],
      version: 1,
    }
    for (const line of lines) {
      line.stock.availableStock -= line.quantity
      line.stock.reservedStock += line.quantity
      line.stock.version += 1
    }
    this.state.reservations[orderNo] = { reservationNo: `RS${orderNo}`, orderNo, status: 'RESERVED' }
    this.state.orders.unshift(order)
    this.state.orderIdempotency[key] = { fingerprint: requestFingerprint, resourceId: orderNo }
    const orderedSkuIds = new Set(items.map((item) => item.skuId))
    this.state.cart = this.state.cart.filter((item) => item.userId !== user.userId || !orderedSkuIds.has(item.skuId))
    this.commit()
    return clone(order)
  }

  async listOrders(status?: OrderStatus): Promise<Order[]> {
    await this.wait()
    const user = this.buyer()
    let changed = false
    for (const order of this.state.orders) {
      if (order.userId === user.userId && this.expireIfNeeded(order)) changed = true
    }
    if (changed) this.commit()
    return clone(this.state.orders.filter((item) => item.userId === user.userId && (!status || item.status === status)))
  }

  async getOrder(orderNo: string): Promise<Order> {
    await this.wait()
    const user = this.buyer()
    const order = this.state.orders.find((item) => item.orderNo === orderNo && item.userId === user.userId)
    if (!order) throw new DomainError('ORDER_NOT_FOUND', '订单不存在', 404)
    if (this.expireIfNeeded(order)) this.commit()
    return clone(order)
  }

  async cancelOrder(orderNo: string, reason = '用户取消'): Promise<Order> {
    await this.wait()
    const user = this.buyer()
    const order = this.state.orders.find((item) => item.orderNo === orderNo && item.userId === user.userId)
    if (!order) throw new DomainError('ORDER_NOT_FOUND', '订单不存在', 404)
    if (this.expireIfNeeded(order)) {
      this.commit()
      return clone(order)
    }
    if (order.status === 'CANCELLED') return clone(order)
    if (order.status !== 'PENDING_PAYMENT') {
      throw new DomainError('INVALID_STATE_TRANSITION', '当前订单状态不能取消', 409)
    }
    const now = new Date().toISOString()
    order.status = 'CANCELLED'
    order.cancelReason = reason
    order.cancelledAt = now
    order.version += 1
    order.history.push({ fromStatus: 'PENDING_PAYMENT', toStatus: 'CANCELLED', operatorType: 'BUYER', reason, createdAt: now })
    this.releaseReservation(order, 'USER')
    for (const payment of this.state.payments) {
      if (payment.orderNo === orderNo && payment.status === 'PENDING') payment.status = 'CLOSED'
    }
    this.commit()
    return clone(order)
  }

  async confirmOrder(orderNo: string): Promise<Order> {
    await this.wait()
    const user = this.buyer()
    const order = this.state.orders.find((item) => item.orderNo === orderNo && item.userId === user.userId)
    if (!order) throw new DomainError('ORDER_NOT_FOUND', '订单不存在', 404)
    if (order.status === 'COMPLETED') return clone(order)
    if (order.status !== 'SHIPPED') throw new DomainError('INVALID_STATE_TRANSITION', '订单尚未发货，不能确认收货', 409)
    order.history.push({ fromStatus: 'SHIPPED', toStatus: 'COMPLETED', operatorType: 'BUYER', reason: '买家确认收货', createdAt: new Date().toISOString() })
    order.status = 'COMPLETED'
    order.completedAt = new Date().toISOString()
    order.version += 1
    this.commit()
    return clone(order)
  }

  async createPayment(orderNo: string, channel: PaymentChannel, idempotencyKey: string): Promise<Payment> {
    await this.wait()
    const user = this.buyer()
    this.validateIdempotencyKey(idempotencyKey)
    const key = `${user.userId}:${idempotencyKey}`
    const requestFingerprint = fingerprint({ orderNo, channel })
    const previous = this.state.paymentIdempotency[key]
    if (previous) {
      if (previous.fingerprint !== requestFingerprint) {
        throw new DomainError('IDEMPOTENCY_CONFLICT', '相同幂等键不能用于不同的支付内容', 409)
      }
      const existing = this.state.payments.find((item) => item.payNo === previous.resourceId)
      if (!existing) throw new DomainError('INTERNAL_ERROR', '幂等记录对应的支付单不存在', 500)
      return clone(existing)
    }
    const order = this.state.orders.find((item) => item.orderNo === orderNo && item.userId === user.userId)
    if (!order) throw new DomainError('ORDER_NOT_FOUND', '订单不存在', 404)
    if (this.expireIfNeeded(order)) {
      this.commit()
      throw new DomainError('ORDER_EXPIRED', '订单已超过支付截止时间', 410)
    }
    if (order.status !== 'PENDING_PAYMENT') {
      throw new DomainError('INVALID_STATE_TRANSITION', '订单当前不可支付', 409)
    }
    const existing = this.state.payments.find((item) => item.orderNo === orderNo && item.status === 'PENDING')
    if (existing) {
      if (existing.channel !== channel) throw new DomainError('PAYMENT_CHANNEL_CONFLICT', '订单已有其他渠道的待支付单', 409)
      this.state.paymentIdempotency[key] = { fingerprint: requestFingerprint, resourceId: existing.payNo }
      this.commit()
      return clone(existing)
    }
    const now = new Date()
    const payment: Payment = {
      payNo: this.nextId('PAY'),
      orderNo,
      userId: user.userId,
      channel,
      amount: order.payableAmount,
      status: 'PENDING',
      cashierToken: channel === 'MOCK' ? `mock-${orderNo}` : undefined,
      expireAt: order.expireAt,
      createdAt: now.toISOString(),
    }
    this.state.payments.unshift(payment)
    this.state.paymentIdempotency[key] = { fingerprint: requestFingerprint, resourceId: payment.payNo }
    this.commit()
    return clone(payment)
  }

  async getPayment(payNo: string): Promise<Payment> {
    await this.wait()
    const user = this.buyer()
    const payment = this.state.payments.find((item) => item.payNo === payNo && item.userId === user.userId)
    if (!payment) throw new DomainError('RESOURCE_NOT_FOUND', '支付单不存在', 404)
    return clone(payment)
  }

  async completeMockPayment(payNo: string): Promise<Payment> {
    await this.wait()
    const user = this.buyer()
    const payment = this.state.payments.find((item) => item.payNo === payNo && item.userId === user.userId)
    if (!payment) throw new DomainError('RESOURCE_NOT_FOUND', '支付单不存在', 404)
    if (payment.channel !== 'MOCK') throw new DomainError('FORBIDDEN', '仅模拟渠道支持此操作', 403)
    if (payment.status === 'SUCCESS') return clone(payment)
    if (payment.status !== 'PENDING') throw new DomainError('INVALID_STATE_TRANSITION', '支付单当前不可完成', 409)
    const order = this.state.orders.find((item) => item.orderNo === payment.orderNo)
    if (!order) throw new DomainError('ORDER_NOT_FOUND', '订单不存在', 404)
    if (this.expireIfNeeded(order)) {
      this.commit()
      throw new DomainError('ORDER_EXPIRED', '订单已超过支付截止时间', 410)
    }
    if (order.status !== 'PENDING_PAYMENT') {
      throw new DomainError('INVALID_STATE_TRANSITION', '订单状态已变化，请核对后再操作', 409)
    }
    const now = new Date().toISOString()
    payment.status = 'SUCCESS'
    payment.paidAt = now
    order.status = 'PENDING_SHIPMENT'
    order.paidAt = now
    order.version += 1
    order.history.push({ fromStatus: 'PENDING_PAYMENT', toStatus: 'PENDING_SHIPMENT', operatorType: 'EVENT', reason: `支付成功 ${payment.payNo}`, createdAt: now })
    const reservation = this.state.reservations[order.orderNo]
    if (reservation?.status === 'RESERVED') {
      for (const item of order.items) {
        const stock = this.state.stocks[item.skuId]
        stock.reservedStock -= item.quantity
        stock.total -= item.quantity
        stock.version += 1
      }
      reservation.status = 'CONFIRMED'
    }
    this.commit()
    return clone(payment)
  }

  async closePayment(payNo: string): Promise<Payment> {
    await this.wait()
    const user = this.buyer()
    const payment = this.state.payments.find((item) => item.payNo === payNo && item.userId === user.userId)
    if (!payment) throw new DomainError('RESOURCE_NOT_FOUND', '支付单不存在', 404)
    if (payment.status === 'CLOSED') return clone(payment)
    if (payment.status !== 'PENDING') throw new DomainError('INVALID_STATE_TRANSITION', '仅待支付支付单可以关闭', 409)
    payment.status = 'CLOSED'
    this.commit()
    return clone(payment)
  }

  async saveAddress(input: AddressInput, addressId?: string): Promise<Address> {
    await this.wait()
    const user = this.buyer()
    if (!input.receiverName.trim() || !input.receiverMobile.trim() || !input.detailAddress.trim()) {
      throw new DomainError('VALIDATION_ERROR', '请完整填写收件人、手机号和详细地址')
    }
    if (input.isDefault) {
      for (const item of this.state.addresses.filter((item) => item.userId === user.userId)) item.isDefault = false
    }
    let address: Address
    if (addressId) {
      const existing = this.state.addresses.find((item) => item.addressId === addressId && item.userId === user.userId)
      if (!existing) throw new DomainError('RESOURCE_NOT_FOUND', '地址不存在', 404)
      Object.assign(existing, clone(input))
      address = existing
    } else {
      address = { ...clone(input), userId: user.userId, addressId: this.nextId('ADDR') }
      this.state.addresses.push(address)
    }
    const owned = this.state.addresses.filter((item) => item.userId === user.userId)
    if (!owned.some((item) => item.isDefault)) owned[0].isDefault = true
    this.commit()
    return clone(address)
  }

  async deleteAddress(addressId: string): Promise<void> {
    await this.wait()
    const user = this.buyer()
    this.state.addresses = this.state.addresses.filter((item) => item.userId !== user.userId || item.addressId !== addressId)
    const owned = this.state.addresses.filter((item) => item.userId === user.userId)
    if (owned.length && !owned.some((item) => item.isDefault)) owned[0].isDefault = true
    this.commit()
  }

  async setDefaultAddress(addressId: string): Promise<Address> {
    await this.wait()
    const user = this.buyer()
    const selected = this.state.addresses.find((item) => item.addressId === addressId && item.userId === user.userId)
    if (!selected) throw new DomainError('RESOURCE_NOT_FOUND', '地址不存在', 404)
    for (const item of this.state.addresses.filter((item) => item.userId === user.userId)) item.isDefault = item.addressId === addressId
    this.commit()
    return clone(selected)
  }

  async submitMerchantApplication(input: Omit<MerchantApplication, 'applicationId' | 'userId' | 'status' | 'createdAt'>): Promise<MerchantApplication> {
    await this.wait()
    const user = this.buyer()
    if (this.state.applications.some((item) => item.userId === user.userId && item.status === 'PENDING')) {
      throw new DomainError('INVALID_STATE_TRANSITION', '已有待审核的入驻申请', 409)
    }
    const application: MerchantApplication = {
      ...clone(input),
      userId: user.userId,
      applicationId: this.nextId('APPLY'),
      status: 'PENDING',
      createdAt: new Date().toISOString(),
    }
    this.state.applications.push(application)
    this.commit()
    return clone(application)
  }

  async listMerchantProducts(): Promise<ProductDetail[]> {
    await this.wait()
    const merchant = this.merchant()
    return this.state.products.filter((item) => item.merchantId === merchant.merchantId).map((item) => this.toDetail(item))
  }

  async getMerchantProduct(spuId: string): Promise<ProductDetail> {
    await this.wait()
    const merchant = this.merchant()
    const product = this.state.products.find((item) => item.spuId === spuId && item.merchantId === merchant.merchantId)
    if (!product) throw new DomainError('RESOURCE_NOT_FOUND', '商品不存在或不属于当前商家', 404)
    return this.toDetail(product)
  }

  async saveProduct(input: ProductInput, spuId?: string): Promise<ProductDetail> {
    await this.wait()
    const merchant = this.merchant()
    if (input.title.trim().length < 2 || !input.categoryId || !input.brand.trim() || !input.images.length || !input.skus.length) {
      throw new DomainError('VALIDATION_ERROR', '请完整填写商品资料，并至少保留一个 SKU 和一张图片')
    }
    const existingProduct = spuId ? this.state.products.find((item) => item.spuId === spuId && item.merchantId === merchant.merchantId) : undefined
    if (spuId && !existingProduct) throw new DomainError('RESOURCE_NOT_FOUND', '商品不存在或不属于当前商家', 404)
    if (existingProduct && input.version !== undefined && input.version !== existingProduct.version) {
      throw new DomainError('VERSION_CONFLICT', '商品已被修改，请刷新后重试', 409)
    }
    const codes = new Set<string>()
    const specs = new Set<string>()
    const skuIds = new Set<string>()
    for (const item of input.skus) {
      if (!item.skuCode.trim() || !Number.isInteger(item.price) || item.price < 0) {
        throw new DomainError('VALIDATION_ERROR', 'SKU 编码必填，价格必须为非负整数分')
      }
      if (codes.has(item.skuCode) || specs.has(fingerprint(item.specs)) || (item.skuId && skuIds.has(item.skuId))) {
        throw new DomainError('VALIDATION_ERROR', 'SKU 编码、ID 和规格组合不能重复')
      }
      if (item.skuId) {
        skuIds.add(item.skuId)
        if (spuId && !existingProduct?.skus.some((candidate) => candidate.skuId === item.skuId)) {
          throw new DomainError('SKU_ID_CONFLICT', 'SKU ID 不属于当前商品', 409)
        }
      }
      codes.add(item.skuCode)
      specs.add(fingerprint(item.specs))
      const duplicate = this.state.products.some((candidate) => candidate.spuId !== spuId && candidate.skus.some((skuItem) => skuItem.skuCode === item.skuCode))
      if (duplicate) throw new DomainError('VALIDATION_ERROR', 'SKU 编码已存在')
    }
    if (existingProduct) {
      const retained = new Set(input.skus.map((item) => item.skuId).filter(Boolean))
      for (const item of existingProduct.skus.filter((skuItem) => !retained.has(skuItem.skuId))) {
        const inCart = this.state.cart.some((entry) => entry.skuId === item.skuId)
        const reserved = Object.values(this.state.reservations).some((reservation) => reservation.status === 'RESERVED' && this.state.orders.find((order) => order.orderNo === reservation.orderNo)?.items.some((line) => line.skuId === item.skuId))
        if (inCart || reserved) throw new DomainError('SKU_IN_USE', '该 SKU 仍被购物车或有效预扣引用，请改为禁用', 409)
      }
    }
    let product: ProductDetail
    if (existingProduct) {
      product = existingProduct
      product.version += 1
    } else {
      product = {
        spuId: this.nextId('SPU'),
        merchantId: merchant.merchantId!,
        status: 'DRAFT',
        salesCount: 0,
        publishedAt: new Date().toISOString(),
        version: 1,
        title: '',
        subtitle: '',
        brand: '',
        categoryId: '',
        coverImage: '',
        minPrice: 0,
        maxPrice: 0,
        descriptionHtml: '',
        images: [],
        skus: [],
      }
      this.state.products.unshift(product)
    }
    product.title = input.title.trim()
    product.subtitle = input.subtitle.trim()
    product.brand = input.brand.trim()
    product.categoryId = input.categoryId
    product.descriptionHtml = input.descriptionHtml
    product.images = clone(input.images)
    product.coverImage = input.images.find((item) => item.isPrimary)?.url ?? input.images[0].url
    product.skus = input.skus.map((item) => ({
      skuId: existingProduct ? item.skuId ?? this.nextId('SKU') : this.nextId('SKU'),
      skuCode: item.skuCode.trim(),
      specs: clone(item.specs),
      price: item.price,
      originalPrice: item.originalPrice,
      enabled: item.enabled,
      availableStock: 0,
    }))
    product.minPrice = Math.min(...product.skus.map((item) => item.price))
    product.maxPrice = Math.max(...product.skus.map((item) => item.price))
    for (const item of product.skus) {
      if (!this.state.stocks[item.skuId]) {
        this.state.stocks[item.skuId] = { skuId: item.skuId, total: 0, availableStock: 0, reservedStock: 0, version: 1 }
      }
    }
    this.commit()
    return this.toDetail(product)
  }

  async setProductStatus(spuId: string, status: ProductStatus, version?: number): Promise<ProductDetail> {
    await this.wait()
    const merchant = this.merchant()
    const product = this.state.products.find((item) => item.spuId === spuId && item.merchantId === merchant.merchantId)
    if (!product) throw new DomainError('RESOURCE_NOT_FOUND', '商品不存在或不属于当前商家', 404)
    if (version !== undefined && version !== product.version) throw new DomainError('VERSION_CONFLICT', '商品版本已变化，请刷新后重试', 409)
    if (status === 'ON_SALE' && (!product.skus.some((item) => item.enabled) || !product.images.length)) {
      throw new DomainError('VALIDATION_ERROR', '商品至少需要一张图片和一个启用 SKU 才能上架')
    }
    product.status = status
    product.version += 1
    this.commit()
    return this.toDetail(product)
  }

  async listMerchantOrders(status?: OrderStatus): Promise<Order[]> {
    await this.wait()
    const merchant = this.merchant()
    return clone(this.state.orders.filter((item) => item.merchantId === merchant.merchantId && (!status || item.status === status)))
  }

  async getMerchantOrder(orderNo: string): Promise<Order> {
    await this.wait()
    const merchant = this.merchant()
    const order = this.state.orders.find((item) => item.orderNo === orderNo && item.merchantId === merchant.merchantId)
    if (!order) throw new DomainError('ORDER_NOT_FOUND', '订单不存在或不属于当前商家', 404)
    return clone(order)
  }

  async shipOrder(orderNo: string, carrier: string, trackingNo: string, version?: number): Promise<Order> {
    await this.wait()
    const merchant = this.merchant()
    const order = this.state.orders.find((item) => item.orderNo === orderNo && item.merchantId === merchant.merchantId)
    if (!order) throw new DomainError('ORDER_NOT_FOUND', '订单不存在或不属于当前商家', 404)
    if (order.status === 'SHIPPED' && order.carrier === carrier && order.trackingNo === trackingNo) return clone(order)
    if (version !== undefined && version !== order.version) throw new DomainError('VERSION_CONFLICT', '订单版本已变化，请刷新后重试', 409)
    if (order.status !== 'PENDING_SHIPMENT') throw new DomainError('INVALID_STATE_TRANSITION', '只有待发货订单可以发货', 409)
    if (!carrier.trim() || !trackingNo.trim()) throw new DomainError('VALIDATION_ERROR', '请填写物流公司和物流单号')
    const now = new Date().toISOString()
    order.history.push({ fromStatus: 'PENDING_SHIPMENT', toStatus: 'SHIPPED', operatorType: 'MERCHANT', reason: `${carrier} ${trackingNo}`, createdAt: now })
    order.status = 'SHIPPED'
    order.carrier = carrier.trim()
    order.trackingNo = trackingNo.trim()
    order.shippedAt = now
    order.version += 1
    this.commit()
    return clone(order)
  }

  async listMerchantStocks(): Promise<MerchantStockItem[]> {
    await this.wait()
    const merchant = this.merchant()
    const result: MerchantStockItem[] = []
    for (const product of this.state.products.filter((item) => item.merchantId === merchant.merchantId)) {
      for (const item of product.skus) {
        const stock = this.state.stocks[item.skuId]
        if (stock) {
          result.push({
            ...clone(stock),
            spuId: product.spuId,
            productTitle: product.title,
            productImage: product.coverImage,
            skuCode: item.skuCode,
            specs: clone(item.specs),
          })
        }
      }
    }
    return result
  }

  async adjustStock(skuId: string, availableDelta: number, reason: string, version?: number): Promise<StockRecord> {
    await this.wait()
    const merchant = this.merchant()
    const { product, stock } = this.findSku(skuId)
    if (product.merchantId !== merchant.merchantId) throw new DomainError('RESOURCE_NOT_FOUND', '库存不存在或不属于当前商家', 404)
    if (version !== undefined && version !== stock.version) throw new DomainError('VERSION_CONFLICT', '库存版本已变化，请刷新后重试', 409)
    if (!Number.isInteger(availableDelta) || !reason.trim()) throw new DomainError('VALIDATION_ERROR', '请输入整数库存变化量和调整原因')
    const nextAvailable = stock.availableStock + availableDelta
    if (nextAvailable < 0) throw new DomainError('STOCK_INVALID', '调整后库存不能为负数', 409)
    stock.availableStock = nextAvailable
    stock.total = nextAvailable + stock.reservedStock
    stock.version += 1
    this.commit()
    return clone(stock)
  }

  async updateShop(input: MerchantShop): Promise<MerchantShop> {
    await this.wait()
    const merchant = this.merchant()
    if (input.merchantId !== merchant.merchantId) throw new DomainError('FORBIDDEN', '不能修改其他商家店铺', 403)
    if (input.shopName.trim().length < 2 || !input.contactName.trim() || !input.contactMobile.trim()) {
      throw new DomainError('VALIDATION_ERROR', '请完整填写店铺和联系信息')
    }
    this.state.shop = { ...clone(input), status: this.state.shop.status }
    this.commit()
    return clone(this.state.shop)
  }
}
