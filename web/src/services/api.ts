import type {
  Cart,
  CreateOrderInput,
  Order,
  PageResult,
  Payment,
  PaymentChannel,
  PaymentStatus,
  ProductDetail,
  ProductSearchQuery,
  ProductSummary,
  SearchResult,
} from '../types/domain.ts'

export interface CommerceApi {
  searchProducts(query: ProductSearchQuery): Promise<SearchResult>
  listProducts(query?: Partial<ProductSearchQuery>): Promise<PageResult<ProductSummary>>
  getProduct(spuId: string): Promise<ProductDetail>
  getCart(): Promise<Cart>
  createOrder(input: CreateOrderInput, idempotencyKey: string): Promise<Order>
  createPayment(orderNo: string, channel: PaymentChannel, idempotencyKey: string): Promise<Payment>
  mockPaymentCallback(input: MockPaymentCallbackInput): Promise<string>
  queryMockPayment(input: MockPaymentCallbackInput): Promise<MockPaymentNotification>
  uploadProductImage(file: File): Promise<{ url: string }>
}

export interface MockPaymentCallbackInput {
  payNo: string
  orderNo: string
  amount: number
  status: Extract<PaymentStatus, 'SUCCESS' | 'FAILED'>
}

export interface MockPaymentNotification {
  notifyId: string
  orderNo: string
  amount: number
  status: 'SUCCESS' | 'FAILED'
  signature: string
}

export interface LoginOptions {
  mobile: string
  password: string
  nickname?: string
  mode: 'login' | 'register'
}

export const API_BASE_URL = __BYTE_MALL_API_BASE_URL__
export const USE_MOCK_API = __BYTE_MALL_USE_MOCK__
