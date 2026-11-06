import { CheckCircleFilled, EnvironmentOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../components/ui/Feedback.tsx'
import { Price } from '../../components/ui/Price.tsx'
import { StatusBadge } from '../../components/ui/StatusBadge.tsx'
import { PageTitle } from '../../router.tsx'
import { useAppStore } from '../../store/app-store.tsx'
import type { Order } from '../../types/domain.ts'
import { formatDate, formatSpec, orderStatusLabel } from '../../utils/format.ts'

export default function OrderDetailPage() {
  const { orderNo = '' } = useParams()
  const { actions, announce } = useAppStore()
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    let active = true
    actions.getOrder(orderNo).then((data) => { if (active) { setOrder(data); setError(null) } }).catch((reason) => { if (active) setError(reason) })
    return () => { active = false }
  }, [actions, orderNo])

  const update = async (operation: () => Promise<Order>, message: string) => {
    setPending(true)
    try {
      setOrder(await operation())
      announce(message)
    } catch (reason) {
      setError(reason)
    } finally {
      setPending(false)
    }
  }

  if (error && !order) return <div className="container page-section"><ErrorState error={error} /></div>
  if (!order) return <div className="container page-section"><LoadingState label="正在加载订单详情" /></div>
  return (
    <div className="container page-section order-detail-page">
      <PageTitle title={`订单 ${order.orderNo}`} />
      <nav className="breadcrumb" aria-label="面包屑"><Link to="/orders">我的订单</Link><span aria-hidden="true">/</span><span aria-current="page">{order.orderNo}</span></nav>
      <section className="order-status-hero"><div><span className="eyebrow">ORDER STATUS</span><h1>{orderStatusLabel[order.status]}</h1><p>{order.status === 'PENDING_PAYMENT' ? `请在 ${formatDate(order.expireAt)} 前完成支付。` : order.status === 'PENDING_SHIPMENT' ? '支付已确认，商家正在准备发货。' : order.status === 'SHIPPED' ? `${order.carrier} 正在配送，物流单号 ${order.trackingNo}。` : order.status === 'COMPLETED' ? '订单已完成，感谢你的选择。' : '订单状态已更新，可在下方查看记录。'}</p></div><StatusBadge status={order.status} /><div className="order-status-actions">{order.status === 'PENDING_PAYMENT' && <><Link className="button button-primary" to={`/payment/${order.orderNo}`}>继续支付</Link><button className="button button-secondary" type="button" disabled={pending} onClick={() => { if (window.confirm('确定取消订单并释放库存吗？')) void update(() => actions.cancelOrder(order.orderNo), '订单已取消') }}>取消订单</button></>}{order.status === 'SHIPPED' && <button className="button button-primary" type="button" disabled={pending} onClick={() => void update(() => actions.confirmOrder(order.orderNo), '已确认收货，订单完成')}><CheckCircleFilled aria-hidden="true" />确认收货</button>}</div></section>
      {error !== null && <ErrorState error={error} />}
      <div className="order-detail-layout"><div>
        <section className="detail-section" aria-labelledby="order-products"><h2 id="order-products">商品信息</h2>{order.items.map((item) => <div className="detail-order-item" key={item.skuId}><img src={item.productImage} alt={item.productTitle} width="112" height="112" /><div><Link to={`/product/${item.spuId}`}><h3>{item.productTitle}</h3></Link><p>{formatSpec(item.specs)}</p><span>单价 <Price amount={item.unitPrice} /> · 数量 {item.quantity}</span></div><Price amount={item.totalPrice} /></div>)}</section>
        <section className="detail-section" aria-labelledby="order-timeline"><h2 id="order-timeline">订单进度</h2><ol className="status-timeline">{[...order.history].reverse().map((event, index) => <li key={`${event.createdAt}-${index}`}><span aria-hidden="true" /><div><strong>{orderStatusLabel[event.toStatus]}</strong><p>{event.reason}</p><time dateTime={event.createdAt}>{formatDate(event.createdAt)}</time></div></li>)}</ol></section>
      </div><aside>
        <section className="detail-section"><h2><EnvironmentOutlined aria-hidden="true" />收货信息</h2><p><strong>{order.address.receiverName}</strong> {order.address.receiverMobile}</p><p>{order.address.province}{order.address.city}{order.address.district}<br />{order.address.detailAddress}</p>{order.remark && <p>备注：{order.remark}</p>}</section>
        <section className="detail-section"><h2>费用信息</h2><dl className="order-cost"><div><dt>商品金额</dt><dd><Price amount={order.itemAmount} /></dd></div><div><dt>运费</dt><dd>¥0.00</dd></div><div><dt>优惠</dt><dd>− ¥0.00</dd></div><div><dt>合计</dt><dd><Price amount={order.payableAmount} large /></dd></div></dl><p>下单时间：{formatDate(order.createdAt)}</p>{order.paidAt && <p>支付时间：{formatDate(order.paidAt)}</p>}</section>
      </aside></div>
    </div>
  )
}
