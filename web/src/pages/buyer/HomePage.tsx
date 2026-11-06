import { CompassOutlined, HomeOutlined, MobileOutlined, SkinOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { PageTitle } from '../../router.tsx'
import { useAppStore } from '../../store/app-store.tsx'
import { ProductCard } from '../../components/buyer/ProductCard.tsx'

const categoryIcons = {
  'cat-digital': MobileOutlined,
  'cat-home': HomeOutlined,
  'cat-fashion': SkinOutlined,
  'cat-travel': CompassOutlined,
}

export default function HomePage() {
  const { state } = useAppStore()
  const available = state.products.filter((item) => item.status === 'ON_SALE')
  const featured = [...available].sort((a, b) => b.salesCount - a.salesCount).slice(0, 4)
  const newest = [...available].sort((a, b) => b.publishedAt.localeCompare(a.publishedAt)).slice(0, 4)
  return (
    <>
      <PageTitle title="让好物融入日常" />
      <section className="home-hero">
        <div className="container hero-grid">
          <div className="hero-copy"><span className="eyebrow">THE EVERYDAY EDIT · 2026</span><h1>让每一次选择，<br /><em>更接近理想日常。</em></h1><p>不追逐短暂的潮流。ByteMall 为你挑选耐用、好看、真正提升体验的日常好物。</p><div className="hero-actions"><Link className="button button-primary" to="/search">开始探索 <span aria-hidden="true">→</span></Link><Link className="button button-ghost" to="/product/spu-headphone">查看本周精选</Link></div><dl className="hero-stats"><div><dt>8</dt><dd>款精选商品</dd></div><div><dt>7 天</dt><dd>无理由退货</dd></div><div><dt>0 元</dt><dd>全场演示运费</dd></div></dl></div>
          <div className="hero-visual"><span className="hero-orbit" aria-hidden="true" /><img src="/images/headphones.svg" alt="声境 Pro 曜石黑无线降噪耳机" width="800" height="800" fetchPriority="high" /><div className="hero-note"><span>本周精选 01</span><strong>静下来，听见更多。</strong><Link to="/product/spu-headphone">探索声境 Pro →</Link></div></div>
        </div>
      </section>
      <section className="container home-section" aria-labelledby="category-title"><div className="section-heading"><div><span className="eyebrow">SHOP BY CATEGORY</span><h2 id="category-title">从喜欢的角落开始</h2></div><Link to="/search">查看全部 →</Link></div><div className="category-grid">{state.categories.map((category) => {
        const Icon = categoryIcons[category.categoryId as keyof typeof categoryIcons] ?? CompassOutlined
        return <Link className="category-card" key={category.categoryId} to={`/search?category=${category.categoryId}`}><Icon aria-hidden="true" /><span>{category.name}</span><small>探索系列 →</small></Link>
      })}</div></section>
      <section className="container home-section" aria-labelledby="featured-title"><div className="section-heading"><div><span className="eyebrow">MOST LOVED</span><h2 id="featured-title">大家正在选择</h2></div><p>真实使用场景里的高人气之选</p></div><div className="product-grid">{featured.map((product, index) => <ProductCard key={product.spuId} product={product} priority={index === 0} />)}</div></section>
      <section className="editorial-banner"><div className="container editorial-grid"><div><span className="eyebrow">A BETTER ROUTINE</span><h2>好物不必喧哗，<br />体验自有答案。</h2><p>从一杯咖啡到一段专注时光，让设计服务于生活，而不是让生活迁就设计。</p><Link className="button button-secondary" to="/search?category=cat-home">探索居家系列</Link></div><img src="/images/coffee.svg" alt="屿光暖银意式咖啡机" width="800" height="800" loading="lazy" /></div></section>
      <section className="container home-section" aria-labelledby="new-title"><div className="section-heading"><div><span className="eyebrow">NEW ARRIVALS</span><h2 id="new-title">刚刚上新</h2></div><Link to="/search?sort=NEWEST">按上新时间浏览 →</Link></div><div className="product-grid">{newest.map((product) => <ProductCard key={product.spuId} product={product} />)}</div></section>
      <section className="container service-strip" aria-label="购物保障"><div><strong>正品精选</strong><span>多维度筛选入驻商品</span></div><div><strong>安心交付</strong><span>订单状态全程可追踪</span></div><div><strong>无忧售后</strong><span>清晰透明的服务规则</span></div></section>
    </>
  )
}
