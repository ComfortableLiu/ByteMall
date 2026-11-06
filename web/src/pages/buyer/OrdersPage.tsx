import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { OrderCard } from '../../components/buyer/OrderCard.tsx'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/Feedback.tsx'
import { PageTitle } from '../../router.tsx'
import { useAppStore } from '../../store/app-store.tsx'
import type { Order, OrderStatus } from '../../types/domain.ts'
import { orderStatusLabel } from '../../utils/format.ts'

export default function OrdersPage() {
  const { actions, announce } = useAppStore()
  const [searchParams, setSearchParams] = useSearchParams()
  const status = (searchParams.get('status') ?? '') as OrderStatus | ''
  const [orders, setOrders] = useState<Order[] | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [pendingOrder, setPendingOrder] = useState('')

  useEffect(() => {
    let active = true
    actions.listOrders(status || undefined).then((data) => { if (active) { setOrders(data); setError(null) } }).catch((reason) => { if (active) setError(reason) })
    return () => { active = false }
  }, [actions, status])

  const cancel = async (orderNo: string) => {
    if (!window.confirm('确定取消这个待支付订单吗？预扣库存将自动释放。')) return
    setPendingOrder(orderNo)
    try {
      const updated = await actions.cancelOrder(orderNo)
      setOrders((current) => current?.map((item) => item.orderNo === orderNo ? updated : item) ?? null)
      announce('订单已取消，库存已释放')
    } catch (reason) {
      setError(reason)
    } finally {
      setPendingOrder('')
    }
  }

  return (
    <div className="container page-section orders-page">
      <PageTitle title="我的订单" />
      <header className="page-heading compact"><span className="eyebrow">ORDER JOURNEY</span><h1>我的订单</h1><p>每一次选择，都清楚地在这里留下进度。</p></header>
      <div className="order-filter"><label htmlFor="order-status">订单状态</label><select id="order-status" value={status} onChange={(event) => setSearchParams(event.target.value ? { status: event.target.value } : {})}><option value="">全部订单</option>{Object.entries(orderStatusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
      {error !== null && <div className="page-section-small"><ErrorState error={error} /></div>}
      {!orders ? <LoadingState label="正在加载订单" /> : orders.length ? <div className="order-list">{orders.map((order) => <OrderCard key={order.orderNo} order={order} onCancel={cancel} pending={pendingOrder === order.orderNo} />)}</div> : <EmptyState title={status ? '这个状态下还没有订单' : '还没有订单'} description="去挑选一件喜欢的好物，完成你的第一笔演示订单。" action={<Link className="button button-primary" to="/search">开始购物</Link>} />}
    </div>
  )
}
