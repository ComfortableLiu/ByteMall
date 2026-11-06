import type { OrderStatus, PaymentStatus, ProductStatus } from '../types/domain.ts'

const moneyFormatter = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2,
})

export const formatMoney = (amount: number): string => moneyFormatter.format(amount / 100)

export const formatDate = (value?: string): string => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

export const formatSpec = (specs: Record<string, string>): string =>
  Object.entries(specs).map(([key, value]) => `${key}：${value}`).join(' · ')

export const orderStatusLabel: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待付款',
  PENDING_SHIPMENT: '待发货',
  SHIPPED: '运输中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
}

export const productStatusLabel: Record<ProductStatus, string> = {
  DRAFT: '草稿',
  ON_SALE: '销售中',
  OFF_SALE: '已下架',
}

export const paymentStatusLabel: Record<PaymentStatus, string> = {
  PENDING: '待支付',
  SUCCESS: '支付成功',
  FAILED: '支付失败',
  CLOSED: '已关闭',
}
