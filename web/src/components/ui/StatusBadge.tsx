import { orderStatusLabel, paymentStatusLabel, productStatusLabel } from '../../utils/format.ts'
import type { OrderStatus, PaymentStatus, ProductStatus } from '../../types/domain.ts'

export function StatusBadge({ status }: { status: OrderStatus | ProductStatus | PaymentStatus }) {
  const label = status in orderStatusLabel
    ? orderStatusLabel[status as OrderStatus]
    : status in productStatusLabel
      ? productStatusLabel[status as ProductStatus]
      : paymentStatusLabel[status as PaymentStatus]
  return <span className={`status-badge status-${status.toLowerCase().replaceAll('_', '-')}`}>{label}</span>
}
