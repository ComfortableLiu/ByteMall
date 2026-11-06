import { CheckCircleFilled, LockOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../components/ui/Feedback.tsx'
import { Price } from '../../components/ui/Price.tsx'
import { PageTitle } from '../../router.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { Order, Payment, PaymentChannel } from '../../types/domain.ts'
import { formatDate } from '../../utils/format.ts'

export default function PaymentPage() {
  const { orderNo = '' } = useParams()
  const { actions } = useAppStore()
  const [order, setOrder] = useState<Order | null>(null)
  const [payment, setPayment] = useState<Payment | null>(null)
  const [channel, setChannel] = useState<PaymentChannel>('MOCK')
  const [error, setError] = useState<unknown>(null)
  const [pending, setPending] = useState(false)
  const [paymentKey] = useState(() => crypto.randomUUID())

  useEffect(() => {
    let active = true
    actions.getOrder(orderNo).then((data) => { if (active) setOrder(data) }).catch((reason) => { if (active) setError(reason) })
    return () => { active = false }
  }, [actions, orderNo])

  const pay = async () => {
    if (!order) return
    setPending(true)
    setError(null)
    try {
      const created = payment ?? await actions.createPayment(order.orderNo, channel, paymentKey)
      setPayment(created)
      const completed = await actions.completeMockPayment(created.payNo)
      setPayment(completed)
      setOrder(await actions.getOrder(order.orderNo))
    } catch (reason) {
      setError(reason)
    } finally {
      setPending(false)
    }
  }

  if (!order) return <div className="container page-section">{error ? <ErrorState error={error} /> : <LoadingState label="正在准备收银台" />}</div>
  const paid = order.status === 'PENDING_SHIPMENT' || payment?.status === 'SUCCESS'
  return (
    <div className="container page-section payment-page">
      <PageTitle title="模拟收银台" />
      <div className="payment-card">
        <header className="payment-header"><div><span className="eyebrow"><LockOutlined aria-hidden="true" /> DEMO CASHIER</span><h1>{paid ? '支付已完成' : '确认并支付'}</h1></div><span className="demo-badge">模拟环境</span></header>
        {paid ? <div className="payment-success"><CheckCircleFilled aria-hidden="true" /><h2>支付成功，订单已进入待发货</h2><p>支付单 {payment?.payNo ?? '已完成'}。商家发货后，你将在订单详情中看到物流信息。</p><div className="payment-success-amount"><Price amount={order.payableAmount} large /></div><Link className="button button-primary" to={`/orders/${order.orderNo}`}>查看订单详情</Link><Link className="button button-secondary" to="/search">继续购物</Link></div> : <><div className="payment-order-info"><div><span>订单编号</span><strong>{order.orderNo}</strong></div><div><span>支付截止</span><strong>{formatDate(order.expireAt)}</strong></div><div><span>收款商家</span><strong>{order.merchantId === 'merchant-001' ? 'ByteMall 精选店' : 'ByteMall 合作商家'}</strong></div></div><div className="payment-amount"><span>应付金额</span><Price amount={order.payableAmount} large /><small>请以订单金额为准，演示不会真实扣款</small></div><form onSubmit={(event) => { event.preventDefault(); void pay() }}><fieldset className="payment-channels"><legend>选择支付方式</legend><label className="payment-channel selected"><input type="radio" name="channel" value="MOCK" checked={channel === 'MOCK'} onChange={() => setChannel('MOCK')} /><span className="channel-mark">M</span><span><strong>ByteMall 模拟支付</strong><small>立即返回成功，验证订单状态闭环</small></span><span className="demo-badge">推荐演示</span></label><label className="payment-channel disabled"><input type="radio" name="channel" value="ALIPAY" disabled /><span className="channel-mark">支</span><span><strong>支付宝沙箱</strong><small>需服务端接入后可用</small></span></label><label className="payment-channel disabled"><input type="radio" name="channel" value="WECHAT" disabled /><span className="channel-mark">微</span><span><strong>微信支付沙箱</strong><small>需服务端接入后可用</small></span></label></fieldset>{error !== null && <p className="form-error" role="alert">{getErrorMessage(error)}</p>}<button className="button button-primary payment-submit" type="submit" disabled={pending || order.status !== 'PENDING_PAYMENT'}>{pending ? '正在确认支付状态…' : `确认支付 ¥${(order.payableAmount / 100).toFixed(2)}`}</button></form><p className="payment-security"><LockOutlined aria-hidden="true" />真实支付必须由 pay-service 验签并异步确认，浏览器状态不可作为扣款依据。</p></>}
      </div>
    </div>
  )
}
