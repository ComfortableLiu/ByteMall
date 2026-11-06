import { useId } from 'react'
import { formatMoney } from '../../utils/format.ts'

export interface SalesPoint {
  day: string
  amount: number
  orders: number
}

export function SalesTrend({ data }: { data: SalesPoint[] }) {
  const id = useId()
  const width = 720
  const height = 250
  const padding = 45
  const max = Math.max(...data.map((item) => item.amount), 1)
  const points = data.map((item, index) => ({
    ...item,
    x: padding + index * ((width - padding * 2) / Math.max(1, data.length - 1)),
    y: height - padding - (item.amount / max) * (height - padding * 2),
  }))
  const path = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ')
  return (
    <figure className="sales-figure">
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-labelledby={`${id}-title ${id}-desc`}>
        <title id={`${id}-title`}>近期销售趋势</title>
        <desc id={`${id}-desc`}>按订单创建日期统计，共 {data.reduce((sum, item) => sum + item.orders, 0)} 笔订单，销售额 {formatMoney(data.reduce((sum, item) => sum + item.amount, 0))}。</desc>
        {[0, 1, 2, 3].map((line) => {
          const y = padding + line * ((height - padding * 2) / 3)
          return <line key={line} x1={padding} x2={width - padding} y1={y} y2={y} className="chart-grid-line" />
        })}
        <path d={path} className="chart-line" />
        {points.map((point) => <g key={point.day}><circle cx={point.x} cy={point.y} r="6" className="chart-point" /><text x={point.x} y={height - 15} textAnchor="middle" className="chart-label">{point.day}</text></g>)}
      </svg>
      <figcaption>订单创建口径的销售趋势；悬停数值可查看下方数据表。</figcaption>
      <details><summary>查看趋势数据表</summary><div className="table-scroll-region" tabIndex={0} role="region" aria-label="销售趋势数据表"><table><caption className="visually-hidden">近期每日销售数据</caption><thead><tr><th scope="col">日期</th><th scope="col">订单数</th><th scope="col">销售金额</th></tr></thead><tbody>{data.map((item) => <tr key={item.day}><th scope="row">{item.day}</th><td>{item.orders}</td><td>{formatMoney(item.amount)}</td></tr>)}</tbody></table></div></details>
    </figure>
  )
}
