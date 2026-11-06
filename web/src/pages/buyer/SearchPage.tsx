import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ProductCard } from '../../components/buyer/ProductCard.tsx'
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/Feedback.tsx'
import { Pagination } from '../../components/ui/Pagination.tsx'
import { PageTitle } from '../../router.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { ProductSort, SearchResult } from '../../types/domain.ts'

export default function SearchPage() {
  const { actions } = useAppStore()
  const [searchParams, setSearchParams] = useSearchParams()
  const [completed, setCompleted] = useState<{ key: string; result?: SearchResult; error?: unknown } | null>(null)
  const [retry, setRetry] = useState(0)
  const queryString = searchParams.toString()
  const requestKey = `${queryString}:${retry}`

  useEffect(() => {
    let active = true
    const params = new URLSearchParams(queryString)
    actions.searchProducts({
      q: params.get('q') ?? undefined,
      category: params.get('category') ?? undefined,
      brand: params.get('brand') ?? undefined,
      minPrice: params.get('minPrice') ? Number(params.get('minPrice')) * 100 : undefined,
      maxPrice: params.get('maxPrice') ? Number(params.get('maxPrice')) * 100 : undefined,
      sort: (params.get('sort') ?? 'RECOMMENDED') as ProductSort,
      page: Math.max(1, Number(params.get('page') ?? 1)),
      size: 8,
    }).then((data) => { if (active) setCompleted({ key: requestKey, result: data }) }).catch((reason) => { if (active) setCompleted({ key: requestKey, error: reason }) })
    return () => { active = false }
  }, [actions, queryString, requestKey])
  const loading = completed?.key !== requestKey
  const result = completed?.key === requestKey ? completed.result ?? null : null
  const error = completed?.key === requestKey ? completed.error ?? null : null

  const updateParams = (values: Record<string, string>) => {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(values)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    next.delete('page')
    setSearchParams(next)
  }

  const applyFilters = (form: HTMLFormElement) => {
    const data = new FormData(form)
    updateParams({
      q: data.get('q')?.toString().trim() ?? '',
      category: data.get('category')?.toString() ?? '',
      brand: data.get('brand')?.toString() ?? '',
      minPrice: data.get('minPrice')?.toString() ?? '',
      maxPrice: data.get('maxPrice')?.toString() ?? '',
    })
  }

  return (
    <div className="container page-section">
      <PageTitle title={searchParams.get('q') ? `搜索：${searchParams.get('q')}` : '全部好物'} />
      <header className="page-heading"><span className="eyebrow">DISCOVER BYTEMALL</span><h1>找到适合你的那一件</h1><p>按品类、品牌和预算筛选，少一点打扰，多一点确定。</p></header>
      <search className="search-panel">
        <form key={queryString} action="/search" method="GET" onSubmit={(event) => { event.preventDefault(); applyFilters(event.currentTarget) }}>
          <div className="field search-keyword"><label htmlFor="search-keyword">关键词</label><input id="search-keyword" name="q" type="search" defaultValue={searchParams.get('q') ?? ''} placeholder="例如：耳机、居家、声境" autoComplete="off" spellCheck={false} /></div>
          <div className="filter-layout">
            <fieldset><legend>商品分类</legend><div className="filter-options"><label><input type="radio" name="category" value="" defaultChecked={!searchParams.get('category')} />全部</label>{(result?.aggregations.categories ?? []).map((item) => <label key={item.value}><input type="radio" name="category" value={item.value} defaultChecked={searchParams.get('category') === item.value} />{item.label} <span>{item.count}</span></label>)}</div></fieldset>
            <fieldset><legend>品牌</legend><div className="filter-options"><label><input type="radio" name="brand" value="" defaultChecked={!searchParams.get('brand')} />全部</label>{(result?.aggregations.brands ?? []).map((item) => <label key={item.value}><input type="radio" name="brand" value={item.value} defaultChecked={searchParams.get('brand') === item.value} />{item.label} <span>{item.count}</span></label>)}</div></fieldset>
            <fieldset><legend>价格区间（元）</legend><div className="price-filter"><div className="field"><label htmlFor="min-price">最低价</label><input id="min-price" name="minPrice" type="number" min="0" step="1" defaultValue={searchParams.get('minPrice') ?? ''} /></div><span aria-hidden="true">—</span><div className="field"><label htmlFor="max-price">最高价</label><input id="max-price" name="maxPrice" type="number" min="0" step="1" defaultValue={searchParams.get('maxPrice') ?? ''} /></div></div></fieldset>
          </div>
          <div className="filter-actions"><button className="button button-primary" type="submit">应用筛选</button><button className="button button-secondary" type="button" onClick={() => setSearchParams({})}>清除全部筛选</button></div>
        </form>
      </search>
      <div className="result-toolbar"><p aria-live="polite">{loading ? '正在更新结果…' : result ? `找到 ${result.total} 件商品${searchParams.get('q') ? `，关键词“${searchParams.get('q')}”` : ''}` : ''}</p><div className="field"><label htmlFor="product-sort">排序方式</label><select id="product-sort" value={searchParams.get('sort') ?? 'RECOMMENDED'} onChange={(event) => updateParams({ sort: event.target.value })}><option value="RECOMMENDED">综合推荐</option><option value="NEWEST">最新上架</option><option value="SALES_DESC">销量优先</option><option value="PRICE_ASC">价格从低到高</option><option value="PRICE_DESC">价格从高到低</option></select></div></div>
      {loading ? <LoadingState label="正在挑选商品" /> : error ? <ErrorState error={error} onRetry={() => setRetry((value) => value + 1)} /> : result && result.items.length ? <><div className="product-grid search-results">{result.items.map((product) => <ProductCard key={product.spuId} product={product} />)}</div><Pagination page={result.page} size={result.size} total={result.total} onChange={(page) => { const next = new URLSearchParams(searchParams); next.set('page', String(page)); setSearchParams(next) }} /></> : <EmptyState title="没有找到匹配的商品" description={error ? getErrorMessage(error) : '试试减少筛选条件，或换一个更宽泛的关键词。'} action={<button className="button button-primary" type="button" onClick={() => setSearchParams({})}>查看全部商品</button>} />}
    </div>
  )
}
