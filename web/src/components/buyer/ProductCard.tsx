import { Link } from 'react-router-dom'
import type { ProductSummary } from '../../types/domain.ts'
import { Price } from '../ui/Price.tsx'

export function ProductCard({ product, priority = false }: { product: ProductSummary; priority?: boolean }) {
  return (
    <article className="product-card">
      <Link to={`/product/${product.spuId}`} className="product-card-link" aria-label={`查看${product.title}`}>
        <div className="product-card-media"><img src={product.coverImage} alt={product.title} width="800" height="800" loading={priority ? 'eager' : 'lazy'} fetchPriority={priority ? 'high' : 'auto'} /><span className="product-card-arrow" aria-hidden="true">↗</span></div>
        <div className="product-card-body"><span className="product-brand">{product.brand}</span><h3>{product.title}</h3><p>{product.subtitle}</p><div><Price amount={product.minPrice} />{product.maxPrice > product.minPrice && <span className="price-suffix">起</span>}</div></div>
      </Link>
    </article>
  )
}
