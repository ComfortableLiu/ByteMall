import { CheckOutlined, LeftOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/Feedback.tsx'
import { Price } from '../../components/ui/Price.tsx'
import { QuantityStepper } from '../../components/ui/QuantityStepper.tsx'
import { PageTitle } from '../../router.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { ProductDetail } from '../../types/domain.ts'

export default function ProductPage() {
  const { spuId = '' } = useParams()
  const { state, actions, announce } = useAppStore()
  const navigate = useNavigate()
  const location = useLocation()
  const [product, setProduct] = useState<ProductDetail | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [selected, setSelected] = useState<Record<string, string>>({})
  const [quantity, setQuantity] = useState(1)
  const [pending, setPending] = useState(false)
  const [feedback, setFeedback] = useState('')
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    let active = true
    actions.getProduct(spuId).then((data) => {
      if (!active) return
      setProduct(data)
      setError(null)
      const first = data.skus.find((item) => item.enabled && item.availableStock > 0) ?? data.skus[0]
      setSelected(first?.specs ?? {})
      setQuantity(1)
    }).catch((reason) => { if (active) setError(reason) })
    return () => { active = false }
  }, [actions, spuId, retry])

  const groups = useMemo(() => {
    const result: Record<string, string[]> = {}
    for (const item of product?.skus ?? []) {
      for (const [key, value] of Object.entries(item.specs)) {
        result[key] = [...new Set([...(result[key] ?? []), value])]
      }
    }
    return result
  }, [product])
  const selectedSku = product?.skus.find((item) => Object.entries(item.specs).every(([key, value]) => selected[key] === value))
  const soldOut = selectedSku ? selectedSku.availableStock <= 0 || !selectedSku.enabled : true

  const addToCart = async () => {
    if (!selectedSku || soldOut) return
    if (!state.currentUser) {
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    setPending(true)
    setFeedback('')
    try {
      await actions.addCartItem(selectedSku.skuId, quantity)
      const message = `已加入购物车：${product?.title}，数量 ${quantity}`
      announce(message)
      setFeedback(message)
    } catch (reason) {
      setFeedback(getErrorMessage(reason))
    } finally {
      setPending(false)
    }
  }

  if (error) return <div className="container page-section"><ErrorState error={error} onRetry={() => setRetry((value) => value + 1)} /></div>
  if (!product) return <div className="container page-section"><LoadingState label="正在加载商品详情" /></div>
  if (!product.skus.length) return <div className="container page-section"><EmptyState title="暂无可售规格" description="请稍后再来看看。" /></div>

  return (
    <div className="container page-section product-page">
      <PageTitle title={product.title} />
      <nav className="breadcrumb" aria-label="面包屑"><Link to="/search">全部好物</Link><LeftOutlined aria-hidden="true" /><span>{product.brand}</span><LeftOutlined aria-hidden="true" /><span aria-current="page">{product.title}</span></nav>
      <div className="product-detail-grid">
        <div className="product-gallery"><div className="product-main-image"><img src={product.images[0]?.url ?? product.coverImage} alt={product.images[0]?.altText ?? product.title} width="800" height="800" fetchPriority="high" /><span>BYTE SELECTION</span></div><div className="gallery-caption"><span>01 / 01</span><p>{product.subtitle}</p></div></div>
        <section className="product-buy-panel" aria-labelledby="product-title"><span className="product-brand">{product.brand} · BYTE SELECTION</span><h1 id="product-title">{product.title}</h1><p className="product-subtitle">{product.subtitle}</p><div className="detail-price">{selectedSku ? <Price amount={selectedSku.price} original={selectedSku.originalPrice} large /> : <span>请选择完整规格</span>}</div><div className="service-note"><SafetyCertificateOutlined aria-hidden="true" />正品保障 · 七天无理由 · 演示订单免运费</div>
          <div className="sku-groups">{Object.entries(groups).map(([name, values]) => <fieldset key={name}><legend>{name} <span>{selected[name] ?? '未选择'}</span></legend><div className="sku-options">{values.map((value) => <button key={value} type="button" className={selected[name] === value ? 'selected' : ''} aria-pressed={selected[name] === value} onClick={() => { setSelected((current) => ({ ...current, [name]: value })); setFeedback('') }}>{selected[name] === value && <CheckOutlined aria-hidden="true" />}{value}</button>)}</div></fieldset>)}</div>
          <div className="stock-line" role="status">{selectedSku ? <><span className={soldOut ? 'stock-dot out' : 'stock-dot'} />{soldOut ? '该规格暂时缺货' : `现货 ${selectedSku.availableStock} 件`}<span className="sku-code">SKU {selectedSku.skuCode}</span></> : '该规格组合暂不存在，请重新选择'}</div>
          <div className="purchase-row"><QuantityStepper value={quantity} max={Math.max(1, selectedSku?.availableStock ?? 1)} onChange={setQuantity} /><button className="button button-primary add-cart-button" type="button" disabled={soldOut || !selectedSku || pending} onClick={() => void addToCart()}>{pending ? '正在加入…' : soldOut ? '暂时缺货' : '加入购物车'}</button></div>
          {feedback && <p className={feedback.startsWith('已加入') ? 'form-success' : 'form-error'} role="status">{feedback}{feedback.startsWith('已加入') && <Link to="/cart">去购物车 →</Link>}</p>}
          <dl className="delivery-list"><div><dt>配送</dt><dd>中国大陆地区，演示订单免运费</dd></div><div><dt>发货</dt><dd>支付后 48 小时内，实际以商家为准</dd></div><div><dt>售后</dt><dd>保持商品与包装完整，支持七天无理由</dd></div></dl>
        </section>
      </div>
      <section className="product-description" aria-labelledby="description-title"><div><span className="eyebrow">DESIGNED FOR EVERYDAY</span><h2 id="description-title">细节，让日常更从容</h2></div><div><p>{product.descriptionHtml}</p><div className="feature-pills"><span>品质材料</span><span>严格筛选</span><span>长期主义</span></div></div></section>
    </div>
  )
}
