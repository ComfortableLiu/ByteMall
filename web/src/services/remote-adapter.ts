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
  PageResult,
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
import { DomainError } from './mock-adapter.ts'

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | boolean | undefined>
  idempotencyKey?: string
}

type AuthResult = {
  accessToken: string
  refreshToken?: string
  expiresIn: number
  tokenType: string
  user: User
}

const emptyState = (): AppState => ({
  ...createSeedState(),
  currentUser: null,
  categories: [],
  products: [],
  stocks: {},
  cart: [],
  addresses: [],
  orders: [],
  payments: [],
  reservations: {},
  orderIdempotency: {},
  paymentIdempotency: {},
  applications: [],
  sequence: 1,
})

export class RemoteAdapter {
  readonly dataSource = 'remote' as const
  readonly loadWarning = ''
  private state: AppState = emptyState()
  private accessToken = ''
  private readonly baseUrl?: string

  constructor(baseUrl?: string) {
    this.baseUrl = baseUrl
    try {
      this.accessToken = sessionStorage.getItem('bytemall-dev-access-token') ?? ''
    } catch {
      this.accessToken = ''
    }
  }

  get snapshot(): AppState {
    return structuredClone(this.state)
  }

  private async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    if (!this.baseUrl) {
      throw new DomainError('API_BASE_URL_MISSING', '线上模式需要设置 BYTE_MALL_API_BASE_URL（包含 /api/v1）', 500)
    }
    const url = new URL(`${this.baseUrl.replace(/\/$/, '')}${path}`)
    for (const [key, value] of Object.entries(options.query ?? {})) {
      if (value !== undefined && value !== '') url.searchParams.set(key, String(value))
    }
    const headers = new Headers({
      accept: 'application/json',
      'x-request-id': globalThis.crypto?.randomUUID?.() ?? `req-${Date.now()}`,
    })
    if (options.body !== undefined) headers.set('content-type', 'application/json')
    if (this.accessToken) headers.set('authorization', `Bearer ${this.accessToken}`)
    if (options.idempotencyKey) headers.set('idempotency-key', options.idempotencyKey)
    try {
      const response = await fetch(url, {
        method: options.method ?? 'GET',
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        credentials: 'include',
      })
      const text = await response.text()
      const payload = text ? JSON.parse(text) : null
      if (!response.ok) {
        throw new DomainError(payload?.code ?? 'HTTP_ERROR', payload?.message ?? `线上请求失败（${response.status}）`, response.status)
      }
      return payload?.data as T
    } catch (error) {
      if (error instanceof DomainError) throw error
      if (error instanceof SyntaxError) throw new DomainError('INVALID_API_RESPONSE', '线上服务返回了无法解析的响应', 502)
      throw new DomainError('NETWORK_ERROR', '无法连接线上 API，请检查地址、网络和 CORS 配置', 503)
    }
  }

  private syncProducts(items: Array<ProductSummary | ProductDetail>): void {
    const byId = new Map(this.state.products.map((item) => [item.spuId, item]))
    for (const item of items) {
      byId.set(item.spuId, { ...byId.get(item.spuId), ...item } as ProductDetail)
    }
    this.state.products = [...byId.values()]
  }

  private syncCart(cart: Cart): void {
    this.state.cart = cart.items.map((item) => ({
      userId: item.userId,
      skuId: item.skuId,
      quantity: item.quantity,
      selected: item.selected,
      addedAt: item.addedAt,
    }))
  }

  private syncOrder(order: Order): void {
    this.state.orders = [order, ...this.state.orders.filter((item) => item.orderNo !== order.orderNo)]
  }

  private syncPayment(payment: Payment): void {
    this.state.payments = [payment, ...this.state.payments.filter((item) => item.payNo !== payment.payNo)]
  }

  private async loadPrivateData(): Promise<void> {
    const [cart, addresses, orders] = await Promise.all([
      this.request<Cart>('/cart'),
      this.request<Address[]>('/users/me/addresses'),
      this.request<PageResult<Order>>('/orders', { query: { page: 1, size: 100 } }),
    ])
    this.syncCart(cart)
    this.state.addresses = addresses
    this.state.orders = orders.items
    if (this.state.currentUser?.roles.includes('MERCHANT')) {
      const [shop] = await Promise.all([
        this.request<MerchantShop>('/merchant/shop'),
        this.listMerchantProducts(),
        this.listMerchantOrders(),
        this.listMerchantStocks(),
      ])
      this.state.shop = shop
    }
  }

  async initialize(): Promise<void> {
    const [categories, products] = await Promise.all([
      this.request<AppState['categories']>('/categories'),
      this.request<PageResult<ProductSummary>>('/products', { query: { page: 1, size: 20 } }),
    ])
    this.state.categories = categories
    this.syncProducts(products.items)
    try {
      const user = await this.request<User>('/users/me')
      this.state.currentUser = user
      await this.loadPrivateData()
    } catch (error) {
      if (!(error instanceof DomainError && error.status === 401)) throw error
    }
  }

  async login(role: DemoRole, options?: LoginOptions): Promise<User> {
    if (!options) throw new DomainError('VALIDATION_ERROR', '请输入线上账号和密码')
    const result = await this.request<AuthResult>(options.mode === 'register' ? '/auth/register' : '/auth/login', {
      method: 'POST',
      body: { mobile: options.mobile, password: options.password, ...(options.mode === 'register' ? { nickname: options.nickname } : {}) },
    })
    if (role === 'merchant' && !result.user.roles.includes('MERCHANT')) {
      throw new DomainError('MERCHANT_REQUIRED', '该账号没有商家权限', 403)
    }
    this.accessToken = result.accessToken
    try {
      sessionStorage.setItem('bytemall-dev-access-token', result.accessToken)
    } catch {
      this.accessToken = result.accessToken
    }
    this.state.currentUser = result.user
    await this.loadPrivateData()
    return structuredClone(result.user)
  }

  async logout(): Promise<void> {
    try {
      await this.request<void>('/auth/logout', { method: 'POST' })
    } finally {
      this.accessToken = ''
      try {
        sessionStorage.removeItem('bytemall-dev-access-token')
      } catch {
        this.accessToken = ''
      }
      this.state.currentUser = null
      this.state.cart = []
      this.state.addresses = []
      this.state.orders = []
    }
  }

  async reset(): Promise<void> {
    throw new DomainError('UNSUPPORTED_OPERATION', '线上模式不支持重置服务器数据，请退出账号或改用 dev:mock', 409)
  }

  async listProducts(query: Partial<ProductSearchQuery> = {}): Promise<PageResult<ProductSummary>> {
    const result = await this.request<PageResult<ProductSummary>>('/products', { query: { ...query } })
    this.syncProducts(result.items)
    return result
  }

  async searchProducts(query: ProductSearchQuery): Promise<SearchResult> {
    const result = await this.request<SearchResult>('/search/products', { query: { ...query } })
    this.syncProducts(result.items)
    return result
  }

  async getProduct(spuId: string): Promise<ProductDetail> {
    const product = await this.request<ProductDetail>(`/products/${encodeURIComponent(spuId)}`)
    this.syncProducts([product])
    for (const sku of product.skus) {
      this.state.stocks[sku.skuId] = {
        skuId: sku.skuId,
        total: sku.availableStock,
        availableStock: sku.availableStock,
        reservedStock: 0,
        version: this.state.stocks[sku.skuId]?.version ?? 0,
      }
    }
    return structuredClone(product)
  }

  async searchSuggestions(q: string): Promise<string[]> {
    const result = await this.request<Array<{ text: string }>>('/search/suggestions', { query: { q } })
    return result.map((item) => item.text)
  }

  async getCart(): Promise<Cart> {
    const cart = await this.request<Cart>('/cart')
    this.syncCart(cart)
    return cart
  }

  async addCartItem(skuId: string, quantity: number): Promise<Cart> {
    const cart = await this.request<Cart>('/cart/items', { method: 'POST', body: { skuId, quantity } })
    this.syncCart(cart)
    return cart
  }

  async updateCartItem(skuId: string, patch: { quantity?: number; selected?: boolean }): Promise<Cart> {
    const cart = await this.request<Cart>(`/cart/items/${encodeURIComponent(skuId)}`, { method: 'PATCH', body: patch })
    this.syncCart(cart)
    return cart
  }

  async removeCartItem(skuId: string): Promise<Cart> {
    await this.request<void>(`/cart/items/${encodeURIComponent(skuId)}`, { method: 'DELETE' })
    return this.getCart()
  }

  async setCartSelection(skuIds: string[], selected: boolean): Promise<Cart> {
    const cart = await this.request<Cart>('/cart/selection', { method: 'POST', body: { skuIds, selected } })
    this.syncCart(cart)
    return cart
  }

  async createOrder(input: CreateOrderInput, idempotencyKey: string): Promise<Order> {
    const order = await this.request<Order>('/orders', { method: 'POST', body: input, idempotencyKey })
    this.syncOrder(order)
    await this.getCart()
    return order
  }

  async listOrders(status?: OrderStatus): Promise<Order[]> {
    const result = await this.request<PageResult<Order>>('/orders', { query: { status, page: 1, size: 100 } })
    this.state.orders = result.items
    return result.items
  }

  async getOrder(orderNo: string): Promise<Order> {
    const order = await this.request<Order>(`/orders/${encodeURIComponent(orderNo)}`)
    this.syncOrder(order)
    return order
  }

  async cancelOrder(orderNo: string, reason = '用户取消'): Promise<Order> {
    const order = await this.request<Order>(`/orders/${encodeURIComponent(orderNo)}/cancel`, { method: 'POST', body: { reason } })
    this.syncOrder(order)
    return order
  }

  async confirmOrder(orderNo: string): Promise<Order> {
    const order = await this.request<Order>(`/orders/${encodeURIComponent(orderNo)}/confirm`, { method: 'POST' })
    this.syncOrder(order)
    return order
  }

  async createPayment(orderNo: string, channel: PaymentChannel, idempotencyKey: string): Promise<Payment> {
    const payment = await this.request<Payment>('/payments', { method: 'POST', body: { orderNo, channel }, idempotencyKey })
    this.syncPayment(payment)
    return payment
  }

  async getPayment(payNo: string): Promise<Payment> {
    const payment = await this.request<Payment>(`/payments/${encodeURIComponent(payNo)}`)
    this.syncPayment(payment)
    return payment
  }

  async completeMockPayment(payNo: string): Promise<Payment> {
    const payment = await this.request<Payment>(`/payments/${encodeURIComponent(payNo)}/mock-complete`, { method: 'POST' })
    this.syncPayment(payment)
    return payment
  }

  async closePayment(payNo: string): Promise<Payment> {
    const payment = await this.request<Payment>(`/payments/${encodeURIComponent(payNo)}/close`, { method: 'POST' })
    this.syncPayment(payment)
    return payment
  }

  async saveAddress(input: AddressInput, addressId?: string): Promise<Address> {
    const address = await this.request<Address>(addressId ? `/users/me/addresses/${encodeURIComponent(addressId)}` : '/users/me/addresses', {
      method: addressId ? 'PATCH' : 'POST',
      body: input,
    })
    this.state.addresses = [...this.state.addresses.filter((item) => item.addressId !== address.addressId), address]
    return address
  }

  async deleteAddress(addressId: string): Promise<void> {
    await this.request<void>(`/users/me/addresses/${encodeURIComponent(addressId)}`, { method: 'DELETE' })
    this.state.addresses = this.state.addresses.filter((item) => item.addressId !== addressId)
  }

  async setDefaultAddress(addressId: string): Promise<Address> {
    const selected = await this.request<Address>(`/users/me/addresses/${encodeURIComponent(addressId)}/default`, { method: 'POST' })
    this.state.addresses = this.state.addresses.map((item) => ({ ...item, isDefault: item.addressId === addressId }))
    return selected
  }

  async submitMerchantApplication(input: Omit<MerchantApplication, 'applicationId' | 'userId' | 'status' | 'createdAt'>): Promise<MerchantApplication> {
    const application = await this.request<MerchantApplication>('/merchants/applications', { method: 'POST', body: input })
    this.state.applications = [application, ...this.state.applications.filter((item) => item.applicationId !== application.applicationId)]
    return application
  }

  async listMerchantProducts(): Promise<ProductDetail[]> {
    const result = await this.request<PageResult<ProductSummary>>('/merchant/products', { query: { page: 1, size: 100 } })
    return Promise.all(result.items.map((item) => this.getMerchantProduct(item.spuId)))
  }

  async getMerchantProduct(spuId: string): Promise<ProductDetail> {
    const product = await this.request<ProductDetail>(`/merchant/products/${encodeURIComponent(spuId)}`)
    this.syncProducts([product])
    return product
  }

  async saveProduct(input: ProductInput, spuId?: string): Promise<ProductDetail> {
    const product = await this.request<ProductDetail>(spuId ? `/merchant/products/${encodeURIComponent(spuId)}` : '/merchant/products', {
      method: spuId ? 'PATCH' : 'POST',
      body: input,
    })
    this.syncProducts([product])
    return product
  }

  async setProductStatus(spuId: string, status: ProductStatus, version?: number): Promise<ProductDetail> {
    const product = await this.request<ProductDetail>(`/merchant/products/${encodeURIComponent(spuId)}/status`, { method: 'POST', body: { status, version } })
    this.syncProducts([product])
    return product
  }

  async listMerchantOrders(status?: OrderStatus): Promise<Order[]> {
    const result = await this.request<PageResult<Order>>('/merchant/orders', { query: { status, page: 1, size: 100 } })
    this.state.orders = result.items
    return result.items
  }

  async getMerchantOrder(orderNo: string): Promise<Order> {
    const order = await this.request<Order>(`/merchant/orders/${encodeURIComponent(orderNo)}`)
    this.syncOrder(order)
    return order
  }

  async shipOrder(orderNo: string, carrier: string, trackingNo: string, version?: number): Promise<Order> {
    const order = await this.request<Order>(`/merchant/orders/${encodeURIComponent(orderNo)}/shipments`, { method: 'POST', body: { carrier, trackingNo, version } })
    this.syncOrder(order)
    return order
  }

  async listMerchantStocks(): Promise<MerchantStockItem[]> {
    const stocks = await this.request<MerchantStockItem[]>('/merchant/stocks')
    for (const item of stocks) {
      this.state.stocks[item.skuId] = {
        skuId: item.skuId,
        total: item.total,
        availableStock: item.availableStock,
        reservedStock: item.reservedStock,
        version: item.version,
      }
    }
    return stocks
  }

  async adjustStock(skuId: string, availableDelta: number, reason: string, version?: number): Promise<StockRecord> {
    const stock = await this.request<StockRecord>(`/merchant/stocks/${encodeURIComponent(skuId)}`, { method: 'PATCH', body: { availableDelta, reason, version } })
    this.state.stocks[skuId] = stock
    return stock
  }

  async updateShop(input: MerchantShop): Promise<MerchantShop> {
    const shop = await this.request<MerchantShop>('/merchant/shop', { method: 'PATCH', body: input })
    this.state.shop = shop
    return shop
  }
}
