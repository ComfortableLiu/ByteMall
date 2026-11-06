import { Link } from 'react-router-dom'
import type { Order } from '../../types/domain.ts'
import { formatDate, formatSpec } from '../../utils/format.ts'
import { Price } from '../ui/Price.tsx'
import { StatusBadge } from '../ui/StatusBadge.tsx'

export function OrderCard({ order, onCancel, pending = false }: { order: Order; onCancel?: (orderNo: string) => void; pending?: boolean }) {
  return (
    <article className="order-card">
      <header><div><span className="order-number">订单号 {order.orderNo}</span><time dateTime={order.createdAt}>{formatDate(order.createdAt)}</time></div><StatusBadge status={order.status} /></header>
      <Link to={`/orders/${order.orderNo}`} className="order-card-items">
        {order.items.map((item) => <div className="order-item-mini" key={item.skuId}><img src={item.productImage} alt="" width="88" height="88" loading="lazy" /><div><h3>{item.productTitle}</h3><p>{formatSpec(item.specs)}</p><span>× {item.quantity}</span></div><Price amount={item.totalPrice} /></div>)}
      </Link>
      <footer><span>共 {order.items.reduce((sum, item) => sum + item.quantity, 0)} 件，实付/应付 <strong>{<Price amount={order.payableAmount} />}</strong></span><div className="order-actions">{order.status === 'PENDING_PAYMENT' && <><Link className="button button-primary button-small" to={`/payment/${order.orderNo}`}>去支付</Link>{onCancel && <button className="button button-secondary button-small" type="button" disabled={pending} onClick={() => onCancel(order.orderNo)}>取消订单</button>}</>}<Link className="button button-secondary button-small" to={`/orders/${order.orderNo}`}>订单详情</Link></div></footer>
    </article>
  )
}
