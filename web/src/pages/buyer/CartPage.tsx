import { DeleteOutlined, ShoppingOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/Feedback.tsx'
import { Price } from '../../components/ui/Price.tsx'
import { QuantityStepper } from '../../components/ui/QuantityStepper.tsx'
import { PageTitle } from '../../router.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { Cart } from '../../types/domain.ts'
import { formatSpec } from '../../utils/format.ts'

export default function CartPage() {
  const { actions, announce } = useAppStore()
  const navigate = useNavigate()
  const [cart, setCart] = useState<Cart | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [pendingSku, setPendingSku] = useState('')
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    let active = true
    actions.getCart().then((data) => { if (active) { setCart(data); setError(null) } }).catch((reason) => { if (active) setError(reason) })
    return () => { active = false }
  }, [actions, retry])

  const update = async (skuId: string, operation: () => Promise<Cart>, message: string) => {
    setPendingSku(skuId)
    try {
      setCart(await operation())
      announce(message)
    } catch (reason) {
      setError(reason)
    } finally {
      setPendingSku('')
    }
  }

  if (error && !cart) return <div className="container page-section"><ErrorState error={error} onRetry={() => setRetry((value) => value + 1)} /></div>
  if (!cart) return <div className="container page-section"><LoadingState label="正在打开购物车" /></div>
  const allSelected = cart.items.length > 0 && cart.items.every((item) => item.selected)
  return (
    <div className="container page-section cart-page">
      <PageTitle title="购物车" />
      <header className="page-heading compact"><span className="eyebrow">YOUR SELECTION</span><h1>购物车</h1><p>把喜欢先放在一起，确认规格与数量后再结算。</p></header>
      {error !== null && <div className="inline-alert" role="alert">{getErrorMessage(error)}<button type="button" className="text-button" onClick={() => setError(null)}>关闭</button></div>}
      {!cart.items.length ? <EmptyState title="购物车还是空的" description="从一件真正喜欢的好物开始吧。" action={<Link className="button button-primary" to="/search">去逛逛 <ShoppingOutlined aria-hidden="true" /></Link>} /> : <div className="cart-layout"><section className="cart-list" aria-label="购物车商品"><div className="cart-list-header"><label className="checkbox-label"><input type="checkbox" checked={allSelected} onChange={(event) => void update('all', () => actions.setCartSelection(cart.items.map((item) => item.skuId), event.target.checked), event.target.checked ? '已全选' : '已取消全选')} />全选</label><span>{cart.items.length} 种商品</span></div>{cart.items.map((item) => <article className={`cart-item${!item.selected ? ' not-selected' : ''}`} key={item.skuId}><label className="visually-hidden" htmlFor={`select-${item.skuId}`}>选择 {item.productTitle}</label><input id={`select-${item.skuId}`} className="cart-checkbox" type="checkbox" checked={item.selected} disabled={pendingSku === item.skuId} onChange={(event) => void update(item.skuId, () => actions.updateCartItem(item.skuId, { selected: event.target.checked }), '已更新选中状态')} /><Link to={`/product/${item.spuId}`}><img src={item.productImage} alt={item.productTitle} width="144" height="144" loading="lazy" /></Link><div className="cart-item-info"><Link to={`/product/${item.spuId}`}><h2>{item.productTitle}</h2></Link><p>{formatSpec(item.specs)}</p><span className={item.availableStock < item.quantity || item.productStatus !== 'ON_SALE' ? 'stock-warning' : 'stock-normal'}>{item.productStatus !== 'ON_SALE' ? '商品已下架' : item.availableStock < item.quantity ? `仅余 ${item.availableStock} 件，请调整` : '现货'}</span><Price amount={item.unitPrice} /></div><div className="cart-item-controls"><QuantityStepper value={item.quantity} max={Math.max(1, item.availableStock)} onChange={(quantity) => void update(item.skuId, () => actions.updateCartItem(item.skuId, { quantity }), '数量已更新')} /><button className="icon-button" type="button" aria-label={`删除${item.productTitle}`} disabled={pendingSku === item.skuId} onClick={() => void update(item.skuId, () => actions.removeCartItem(item.skuId), '商品已移出购物车')}><DeleteOutlined aria-hidden="true" /></button></div></article>)}</section><aside className="cart-summary"><h2>订单摘要</h2><dl><div><dt>已选商品</dt><dd>{cart.selectedQuantity} 件</dd></div><div><dt>商品金额</dt><dd><Price amount={cart.selectedAmount} /></dd></div><div><dt>运费</dt><dd>免运费</dd></div><div className="summary-total"><dt>合计</dt><dd><Price amount={cart.selectedAmount} large /></dd></div></dl><p>最终价格与库存将在提交订单时再次校验。</p><button className="button button-primary checkout-button" type="button" disabled={!cart.selectedQuantity} onClick={() => navigate('/checkout')}>去结算 {cart.selectedQuantity > 0 ? `(${cart.selectedQuantity})` : ''}</button><Link to="/search" className="continue-link">继续挑选 →</Link></aside></div>}
    </div>
  )
}
