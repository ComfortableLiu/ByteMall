import { Alert, Card, Col, Row, Statistic, Table, Tag } from 'antd'
import { ArrowRightOutlined, InboxOutlined, ShoppingCartOutlined, WalletOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { SalesTrend } from '../../components/merchant/SalesTrend.tsx'
import type { SalesPoint } from '../../components/merchant/SalesTrend.tsx'
import { StatusBadge } from '../../components/ui/StatusBadge.tsx'
import { useAppStore } from '../../store/app-store.tsx'
import { formatDate, formatMoney } from '../../utils/format.ts'

export default function DashboardPage() {
  const { state } = useAppStore()
  const merchantId = state.currentUser?.merchantId
  const orders = state.orders.filter((item) => item.merchantId === merchantId)
  const products = state.products.filter((item) => item.merchantId === merchantId)
  const skuIds = new Set(products.flatMap((item) => item.skus.map((sku) => sku.skuId)))
  const stocks = Object.values(state.stocks).filter((item) => skuIds.has(item.skuId))
  const revenue = orders.filter((item) => item.paidAt && item.status !== 'REFUNDED').reduce((sum, item) => sum + item.payableAmount, 0)
  const latestTime = orders.length ? Math.max(...orders.map((item) => new Date(item.createdAt).getTime())) : Date.parse('2026-01-01T00:00:00Z')
  const trend: SalesPoint[] = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(latestTime - (6 - index) * 86400000)
    const key = date.toISOString().slice(0, 10)
    const daily = orders.filter((item) => item.createdAt.slice(0, 10) === key && item.status !== 'CANCELLED')
    return { day: key.slice(5).replace('-', '/'), amount: daily.reduce((sum, item) => sum + item.payableAmount, 0), orders: daily.length }
  })
  const lowStock = stocks.filter((item) => item.availableStock <= 5)
  return (
    <div className="merchant-dashboard">
      <div className="merchant-welcome"><div><span className="merchant-overline">OVERVIEW</span><h1>早上好，{state.currentUser?.nickname}</h1><p>这里是 {state.shop.shopName} 的最新经营概况。</p></div><Link className="ant-btn ant-btn-primary ant-btn-lg" to="/merchant/products/new">发布新商品 <ArrowRightOutlined /></Link></div>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} xl={6}><Card><Statistic title="累计有效销售额" value={revenue / 100} precision={2} prefix={<WalletOutlined />} suffix="元" /><span className="metric-note">按已创建且未取消订单统计</span></Card></Col>
        <Col xs={24} sm={12} xl={6}><Card><Statistic title="待发货订单" value={orders.filter((item) => item.status === 'PENDING_SHIPMENT').length} prefix={<ShoppingCartOutlined />} suffix="笔" /><Link to="/merchant/orders?status=PENDING_SHIPMENT">立即处理 →</Link></Card></Col>
        <Col xs={24} sm={12} xl={6}><Card><Statistic title="在售商品" value={products.filter((item) => item.status === 'ON_SALE').length} prefix={<InboxOutlined />} suffix="款" /><Link to="/merchant/products">管理商品 →</Link></Card></Col>
        <Col xs={24} sm={12} xl={6}><Card><Statistic title="低库存 SKU" value={lowStock.length} valueStyle={{ color: lowStock.length ? '#b42318' : undefined }} suffix="个" /><Link to="/merchant/inventory">检查库存 →</Link></Card></Col>
      </Row>
      <Row gutter={[16, 16]} className="dashboard-row"><Col xs={24} xxl={16}><Card title="销售趋势" extra={<Tag>最近 7 天</Tag>}><SalesTrend data={trend} /></Card></Col><Col xs={24} xxl={8}><Card title="库存提醒" extra={<Link to="/merchant/inventory">全部库存</Link>}><Alert message="可售量 ≤ 5 时列入低库存" type="info" showIcon />{lowStock.slice(0, 5).map((stock) => {
        const product = products.find((item) => item.skus.some((sku) => sku.skuId === stock.skuId))
        const sku = product?.skus.find((item) => item.skuId === stock.skuId)
        return <div className="low-stock-row" key={stock.skuId}><img src={product?.coverImage} alt="" width="46" height="46" /><div><strong>{product?.title}</strong><span>{Object.values(sku?.specs ?? {}).join(' · ')}</span></div><Tag color={stock.availableStock === 0 ? 'error' : 'warning'}>{stock.availableStock} 件</Tag></div>
      })}</Card></Col></Row>
      <Card title="最新订单" extra={<Link to="/merchant/orders">查看全部 →</Link>}><div className="table-scroll-region" tabIndex={0} role="region" aria-label="最新订单表格"><Table rowKey="orderNo" pagination={false} scroll={{ x: 720 }} dataSource={orders.slice(0, 5)} columns={[
        { title: '订单号', dataIndex: 'orderNo', render: (value: string) => <Link to={`/merchant/orders/${value}`}>{value}</Link> },
        { title: '商品', render: (_, order) => `${order.items[0].productTitle}${order.items.length > 1 ? ` 等 ${order.items.length} 件` : ''}` },
        { title: '金额', dataIndex: 'payableAmount', render: formatMoney },
        { title: '下单时间', dataIndex: 'createdAt', render: formatDate },
        { title: '状态', dataIndex: 'status', render: (status) => <StatusBadge status={status} /> },
      ]} /></div></Card>
      <p className="dashboard-footnote">页面指标基于当前本地演示数据；真实财务指标应以支付与对账服务为准。{formatMoney(revenue)}</p>
    </div>
  )
}
