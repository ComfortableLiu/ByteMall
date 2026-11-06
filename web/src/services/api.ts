import type {
  Cart,
  CreateOrderInput,
  Order,
  PageResult,
  Payment,
  PaymentChannel,
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
}

export interface LoginOptions {
  mobile: string
  password: string
  nickname?: string
  mode: 'login' | 'register'
}

export const API_BASE_URL = __BYTE_MALL_API_BASE_URL__
export const USE_MOCK_API = __BYTE_MALL_USE_MOCK__
