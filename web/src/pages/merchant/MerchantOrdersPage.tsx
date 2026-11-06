import { Card, Input, Select, Table, Tag } from 'antd'
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { StatusBadge } from '../../components/ui/StatusBadge.tsx'
import { useAppStore } from '../../store/app-store.tsx'
import type { Order, OrderStatus } from '../../types/domain.ts'
import { formatDate, formatMoney, orderStatusLabel } from '../../utils/format.ts'

export default function MerchantOrdersPage() {
  const { state } = useAppStore()
  const [searchParams, setSearchParams] = useSearchParams()
  const [keyword, setKeyword] = useState('')
  const status = (searchParams.get('status') ?? '') as OrderStatus | ''
  const orders = useMemo(() => state.orders.filter((item) => item.merchantId === state.currentUser?.merchantId && (!status || item.status === status) && (!keyword.trim() || `${item.orderNo}${item.address.receiverName}${item.items.map((line) => line.productTitle).join('')}`.toLowerCase().includes(keyword.trim().toLowerCase()))), [state.orders, state.currentUser, keyword, status])
  return (
    <Card title="订单管理"><div className="merchant-toolbar"><Input.Search allowClear placeholder="订单号、收件人或商品" aria-label="搜索订单" value={keyword} onChange={(event) => setKeyword(event.target.value)} style={{ maxWidth: 360 }} /><Select aria-label="按订单状态筛选" value={status} onChange={(value) => setSearchParams(value ? { status: value } : {})} style={{ width: 160 }} options={[{ value: '', label: '全部状态' }, ...Object.entries(orderStatusLabel).map(([value, label]) => ({ value, label }))]} /></div><div className="table-scroll-region" tabIndex={0} role="region" aria-label="商家订单表格"><Table<Order> rowKey="orderNo" dataSource={orders} scroll={{ x: 1000 }} pagination={{ pageSize: 8, showSizeChanger: false }} locale={{ emptyText: '暂无符合条件的订单' }} columns={[
      { title: '订单号', dataIndex: 'orderNo', render: (value) => <Link to={`/merchant/orders/${value}`}>{value}</Link> },
      { title: '商品', render: (_, order) => <div className="merchant-order-product"><img src={order.items[0].productImage} alt="" width="44" height="44" /><span>{order.items[0].productTitle}{order.items.length > 1 && ` 等 ${order.items.length} 件`}</span></div> },
      { title: '收件人', render: (_, order) => order.address.receiverName },
      { title: '应付金额', dataIndex: 'payableAmount', render: formatMoney },
      { title: '下单时间', dataIndex: 'createdAt', render: formatDate },
      { title: '状态', dataIndex: 'status', render: (value) => <StatusBadge status={value} /> },
      { title: '操作', fixed: 'right', render: (_, order) => <Link to={`/merchant/orders/${order.orderNo}`}>{order.status === 'PENDING_SHIPMENT' ? <Tag color="processing">去发货</Tag> : '查看详情'}</Link> },
    ]} /></div><p className="table-help">仅显示当前商家的订单。发货操作必须通过订单状态机校验。</p></Card>
  )
}
