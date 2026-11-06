export type Role = 'BUYER' | 'MERCHANT' | 'ADMIN'
export type DemoRole = 'buyer' | 'merchant'
export type ProductStatus = 'DRAFT' | 'ON_SALE' | 'OFF_SALE'
export type OrderStatus = 'PENDING_PAYMENT' | 'PENDING_SHIPMENT' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED' | 'REFUNDING' | 'REFUNDED'
export type PaymentChannel = 'MOCK' | 'ALIPAY' | 'WECHAT'
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CLOSED'
export type ProductSort = 'RECOMMENDED' | 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'SALES_DESC'

export interface User {
  userId: string
  nickname: string
  avatarUrl?: string
  roles: Role[]
  merchantId?: string
}

export interface Address {
  addressId: string
  userId: string
  receiverName: string
  receiverMobile: string
  province: string
  city: string
  district: string
  detailAddress: string
  postalCode?: string
  isDefault: boolean
}

export type AddressInput = Omit<Address, 'addressId' | 'userId'>

export interface Category {
  categoryId: string
  name: string
  level: number
  icon: string
}

export interface ProductImage {
  url: string
  altText: string
  sortOrder: number
  isPrimary: boolean
}

export interface ProductSku {
  skuId: string
  skuCode: string
  specs: Record<string, string>
  price: number
  originalPrice?: number
  availableStock: number
  enabled: boolean
}

export interface ProductSummary {
  spuId: string
  merchantId: string
  title: string
  subtitle: string
  brand: string
  categoryId: string
  coverImage: string
  minPrice: number
  maxPrice: number
  salesCount: number
  status: ProductStatus
  publishedAt: string
}

export interface ProductDetail extends ProductSummary {
  descriptionHtml: string
  images: ProductImage[]
  skus: ProductSku[]
  version: number
}

export interface SkuInput {
  skuId?: string
  skuCode: string
  specs: Record<string, string>
  price: number
  originalPrice?: number
  enabled: boolean
}

export interface ProductInput {
  title: string
  subtitle: string
  categoryId: string
  brand: string
  descriptionHtml: string
  images: ProductImage[]
  skus: SkuInput[]
  version?: number
}

export interface StockRecord {
  skuId: string
  total: number
  availableStock: number
  reservedStock: number
  version: number
}

export interface MerchantStockItem extends StockRecord {
  spuId: string
  productTitle: string
  productImage: string
  skuCode: string
  specs: Record<string, string>
}

export interface ProductSearchQuery {
  q?: string
  category?: string
  brand?: string
  minPrice?: number
  maxPrice?: number
  sort?: ProductSort
  page: number
  size: number
}

export interface PageResult<T> {
  page: number
  size: number
  total: number
  items: T[]
}

export interface Aggregation {
  value: string
  label: string
  count: number
}

export interface SearchResult extends PageResult<ProductSummary> {
  aggregations: {
    categories: Aggregation[]
    brands: Aggregation[]
  }
}

export interface CartEntry {
  userId: string
  skuId: string
  quantity: number
  selected: boolean
  addedAt: string
}

export interface CartItem extends CartEntry {
  spuId: string
  merchantId: string
  productTitle: string
  productImage: string
  specs: Record<string, string>
  unitPrice: number
  availableStock: number
  enabled: boolean
  productStatus: ProductStatus
}

export interface Cart {
  items: CartItem[]
  selectedAmount: number
  selectedQuantity: number
}

export interface CreateOrderInput {
  addressId: string
  items: Array<{ skuId: string; quantity: number }>
  remark?: string
}

export interface OrderItem {
  spuId: string
  skuId: string
  productTitle: string
  productImage: string
  specs: Record<string, string>
  unitPrice: number
  quantity: number
  totalPrice: number
}

export interface OrderEvent {
  fromStatus: OrderStatus | null
  toStatus: OrderStatus
  operatorType: 'BUYER' | 'MERCHANT' | 'SYSTEM' | 'EVENT'
  reason: string
  createdAt: string
}

export interface Order {
  orderNo: string
  userId: string
  merchantId: string
  status: OrderStatus
  itemAmount: number
  freightAmount: number
  discountAmount: number
  payableAmount: number
  address: AddressInput
  items: OrderItem[]
  remark?: string
  carrier?: string
  trackingNo?: string
  cancelReason?: string
  expireAt: string
  paidAt?: string
  shippedAt?: string
  completedAt?: string
  cancelledAt?: string
  createdAt: string
  history: OrderEvent[]
  version: number
}

export interface Payment {
  payNo: string
  orderNo: string
  userId: string
  channel: PaymentChannel
  amount: number
  status: PaymentStatus
  cashierToken?: string
  expireAt: string
  paidAt?: string
  createdAt: string
}

export interface MerchantApplication {
  applicationId: string
  userId: string
  shopName: string
  contactName: string
  contactMobile: string
  licenseNo: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  rejectReason?: string
  createdAt: string
}

export interface MerchantShop {
  merchantId: string
  shopName: string
  logoUrl?: string
  contactName: string
  contactMobile: string
  status: 'PENDING' | 'ACTIVE' | 'SUSPENDED'
}

export interface StockReservation {
  reservationNo: string
  orderNo: string
  status: 'RESERVED' | 'CONFIRMED' | 'RELEASED'
  reason?: string
}

interface IdempotencyRecord {
  fingerprint: string
  resourceId: string
}

export interface AppState {
  schemaVersion: 1
  currentUser: User | null
  users: Record<DemoRole, User>
  categories: Category[]
  products: ProductDetail[]
  stocks: Record<string, StockRecord>
  cart: CartEntry[]
  addresses: Address[]
  orders: Order[]
  payments: Payment[]
  reservations: Record<string, StockReservation>
  orderIdempotency: Record<string, IdempotencyRecord>
  paymentIdempotency: Record<string, IdempotencyRecord>
  applications: MerchantApplication[]
  shop: MerchantShop
  sequence: number
}
