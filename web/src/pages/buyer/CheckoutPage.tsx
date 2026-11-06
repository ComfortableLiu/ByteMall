import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/Feedback.tsx'
import { Price } from '../../components/ui/Price.tsx'
import { PageTitle } from '../../router.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { Cart, Order } from '../../types/domain.ts'
import { formatSpec } from '../../utils/format.ts'

export default function CheckoutPage() {
  const { state, actions } = useAppStore()
  const navigate = useNavigate()
  const addresses = state.addresses.filter((item) => item.userId === state.currentUser?.userId)
  const [cart, setCart] = useState<Cart | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [addressId, setAddressId] = useState(() => addresses.find((item) => item.isDefault)?.addressId ?? addresses[0]?.addressId ?? '')
  const [remark, setRemark] = useState('')
  const [pending, setPending] = useState(false)
  const [createdOrders, setCreatedOrders] = useState<Order[]>([])
  const [batchKey] = useState(() => crypto.randomUUID())

  useEffect(() => {
    let active = true
    actions.getCart().then((data) => { if (active) setCart(data) }).catch((reason) => { if (active) setError(reason) })
    return () => { active = false }
  }, [actions])

  const selected = cart?.items.filter((item) => item.selected) ?? []
  const groups = Object.values(selected.reduce<Record<string, typeof selected>>((result, item) => {
    result[item.merchantId] = [...(result[item.merchantId] ?? []), item]
    return result
  }, {}))
  const submit = async () => {
    if (!addressId || !selected.length) {
      setError(new Error('请选择收货地址并确认结算商品'))
      return
    }
    setPending(true)
    setError(null)
    const orders: Order[] = []
    try {
      for (const group of groups) {
        const order = await actions.createOrder({ addressId, remark, items: group.map((item) => ({ skuId: item.skuId, quantity: item.quantity })) }, `${batchKey}:${group[0].merchantId}`)
        orders.push(order)
        setCreatedOrders([...orders])
        setCart(await actions.getCart())
      }
      navigate(`/payment/${orders[0].orderNo}`, { state: { created: orders.length } })
    } catch (reason) {
      setError(reason)
    } finally {
      setPending(false)
    }
  }

  if (!cart) return <div className="container page-section">{error ? <ErrorState error={error} /> : <LoadingState label="正在核对订单" />}</div>
  if (!selected.length) return <div className="container page-section"><PageTitle title="确认订单" /><EmptyState title="还没有选择结算商品" description="返回购物车勾选需要购买的商品。" action={<Link className="button button-primary" to="/cart">返回购物车</Link>} /></div>
  return (
    <div className="container page-section checkout-page">
      <PageTitle title="确认订单" />
      <header className="page-heading compact"><span className="eyebrow">SECURE CHECKOUT</span><h1>确认订单</h1><p>最后核对一次，价格与库存将在提交时重新校验。</p></header>
      <div className="checkout-layout"><div className="checkout-main">
        <section className="checkout-section" aria-labelledby="address-title"><div className="section-title-row"><h2 id="address-title">收货地址</h2><Link to="/account">管理地址 →</Link></div>{addresses.length ? <div className="address-options">{addresses.map((address) => <label className={`address-option${addressId === address.addressId ? ' selected' : ''}`} key={address.addressId}><input type="radio" name="addressId" value={address.addressId} checked={addressId === address.addressId} onChange={() => setAddressId(address.addressId)} /><strong>{address.receiverName} <span>{address.receiverMobile}</span></strong><span>{address.province}{address.city}{address.district}{address.detailAddress}</span>{address.isDefault && <small>默认</small>}</label>)}</div> : <div className="inline-alert">请先添加收货地址 <Link to="/account">去添加 →</Link></div>}</section>
        <section className="checkout-section" aria-labelledby="items-title"><h2 id="items-title">商品清单</h2>{groups.map((group) => <div className="checkout-merchant" key={group[0].merchantId}><h3>{group[0].merchantId === state.shop.merchantId ? state.shop.shopName : 'ByteMall 合作商家'}</h3>{group.map((item) => <div className="checkout-item" key={item.skuId}><img src={item.productImage} alt="" width="88" height="88" /><div><strong>{item.productTitle}</strong><span>{formatSpec(item.specs)}</span><small>× {item.quantity}</small></div><Price amount={item.unitPrice * item.quantity} /></div>)}</div>)}<div className="field remark-field"><label htmlFor="order-remark">订单备注（可选）</label><textarea id="order-remark" name="remark" rows={3} maxLength={200} placeholder="如有配送需求，可在这里说明" value={remark} onChange={(event) => setRemark(event.target.value)} /></div></section>
      </div><aside className="cart-summary checkout-summary"><h2>费用明细</h2><dl><div><dt>商品金额</dt><dd><Price amount={cart.selectedAmount} /></dd></div><div><dt>运费</dt><dd>免运费</dd></div><div><dt>优惠</dt><dd>− ¥0.00</dd></div><div className="summary-total"><dt>应付合计</dt><dd><Price amount={cart.selectedAmount} large /></dd></div></dl>{groups.length > 1 && <p>将按商家拆分为 {groups.length} 个订单，可分别支付。</p>}{createdOrders.length > 0 && error !== null && <div className="partial-order-results" role="alert"><strong>已创建 {createdOrders.length} 个订单</strong><p>这些订单已经生效并完成库存预扣，可先前往支付；未成功的商家可使用原幂等键安全重试。</p><div>{createdOrders.map((order) => <Link key={order.orderNo} className="button button-secondary button-small" to={`/payment/${order.orderNo}`}>支付 {order.orderNo}</Link>)}</div></div>}{error !== null && <p className="form-error" role="alert">{getErrorMessage(error)}</p>}<button className="button button-primary checkout-button" type="button" disabled={pending || !addressId} onClick={() => void submit()}>{pending ? '正在创建订单…' : createdOrders.length && error !== null ? '继续未完成的结算' : `提交订单 · ¥${(cart.selectedAmount / 100).toFixed(2)}`}</button><p className="checkout-terms">提交即表示你同意演示交易规则。请勿使用真实支付信息。</p></aside></div>
    </div>
  )
}
