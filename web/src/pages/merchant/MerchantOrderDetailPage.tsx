import { App, Alert, Button, Card, Descriptions, Form, Input, Timeline } from 'antd'
import { Link, useParams } from 'react-router-dom'
import { StatusBadge } from '../../components/ui/StatusBadge.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import { formatDate, formatMoney, formatSpec, orderStatusLabel } from '../../utils/format.ts'

interface ShipmentForm {
  carrier: string
  trackingNo: string
}

export default function MerchantOrderDetailPage() {
  const { orderNo = '' } = useParams()
  const { state, actions } = useAppStore()
  const { message } = App.useApp()
  const order = state.orders.find((item) => item.orderNo === orderNo && item.merchantId === state.currentUser?.merchantId)

  const ship = async (values: ShipmentForm) => {
    try {
      await actions.shipOrder(orderNo, values.carrier, values.trackingNo, order?.version)
      message.success('发货成功，买家端订单已更新为运输中')
    } catch (error) {
      message.error(getErrorMessage(error))
    }
  }

  if (!order) return <Alert type="error" showIcon message="订单不存在" description="订单不存在或不属于当前商家。" action={<Link to="/merchant/orders">返回订单列表</Link>} />
  return (
    <div className="merchant-order-detail"><div className="merchant-edit-heading"><div><Link to="/merchant/orders">← 返回订单列表</Link><h1>订单 {order.orderNo}</h1></div><StatusBadge status={order.status} /></div>
      <div className="merchant-order-grid"><div><Card title="商品快照"><div className="merchant-order-lines">{order.items.map((item) => <div className="merchant-order-line" key={item.skuId}><img src={item.productImage} alt={item.productTitle} width="72" height="72" /><div><strong>{item.productTitle}</strong><span>{formatSpec(item.specs)}</span><small>{formatMoney(item.unitPrice)} × {item.quantity}</small></div><strong>{formatMoney(item.totalPrice)}</strong></div>)}</div><div className="merchant-order-total">订单应付：<strong>{formatMoney(order.payableAmount)}</strong></div></Card><Card title="状态记录"><Timeline items={[...order.history].reverse().map((event) => ({ key: `${event.createdAt}-${event.toStatus}`, children: <><strong>{orderStatusLabel[event.toStatus]}</strong><p>{event.reason}</p><time dateTime={event.createdAt}>{formatDate(event.createdAt)}</time></> }))} /></Card></div>
        <div><Card title="履约信息"><Descriptions column={1} size="small" items={[{ key: 'buyer', label: '买家', children: order.userId }, { key: 'receiver', label: '收件人', children: `${order.address.receiverName} ${order.address.receiverMobile}` }, { key: 'address', label: '地址', children: `${order.address.province}${order.address.city}${order.address.district}${order.address.detailAddress}` }, { key: 'created', label: '下单时间', children: formatDate(order.createdAt) }, { key: 'remark', label: '备注', children: order.remark || '无' }]} /></Card>
          {order.status === 'PENDING_SHIPMENT' && <Card title="确认发货" className="shipment-card"><Alert message="仅待发货订单可执行。提交后状态变为运输中。" type="info" showIcon /><Form<ShipmentForm> layout="vertical" onFinish={ship} initialValues={{ carrier: '顺丰速运', trackingNo: '' }}><Form.Item name="carrier" label="物流公司" rules={[{ required: true, max: 60 }]}><Input autoComplete="organization" /></Form.Item><Form.Item name="trackingNo" label="物流单号" rules={[{ required: true, min: 5, max: 64 }]}><Input placeholder="请使用虚构演示单号" spellCheck={false} /></Form.Item><Button type="primary" htmlType="submit" block>确认发货</Button></Form></Card>}
          {order.shippedAt && <Card title="物流信息"><Descriptions column={1} items={[{ key: 'carrier', label: '物流公司', children: order.carrier }, { key: 'tracking', label: '物流单号', children: order.trackingNo }, { key: 'time', label: '发货时间', children: formatDate(order.shippedAt) }]} /></Card>}
        </div></div>
    </div>
  )
}
