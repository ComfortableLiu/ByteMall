import { App, Alert, Button, Card, Form, Input, InputNumber, Switch, Table } from 'antd'
import { useMemo, useState } from 'react'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { MerchantStockItem } from '../../types/domain.ts'

interface AdjustForm {
  availableDelta: number
  reason: string
}

export default function InventoryPage() {
  const { state, actions } = useAppStore()
  const { message } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [lowOnly, setLowOnly] = useState(false)
  const [selected, setSelected] = useState<MerchantStockItem | null>(null)
  const [pending, setPending] = useState(false)
  const [form] = Form.useForm<AdjustForm>()
  const rows = useMemo(() => {
    const result: MerchantStockItem[] = []
    for (const product of state.products.filter((item) => item.merchantId === state.currentUser?.merchantId)) {
      for (const sku of product.skus) {
        const stock = state.stocks[sku.skuId]
        if (stock) result.push({ ...stock, spuId: product.spuId, productTitle: product.title, productImage: product.coverImage, skuCode: sku.skuCode, specs: sku.specs })
      }
    }
    return result.filter((item) => (!lowOnly || item.availableStock <= 5) && (!keyword.trim() || `${item.productTitle}${item.skuCode}`.toLowerCase().includes(keyword.trim().toLowerCase())))
  }, [state.products, state.stocks, state.currentUser, keyword, lowOnly])

  const adjust = async (values: AdjustForm) => {
    if (!selected) return
    setPending(true)
    try {
      await actions.adjustStock(selected.skuId, values.availableDelta, values.reason, selected.version)
      message.success('库存已调整，买家端可售量同步更新')
      setSelected(null)
    } catch (error) {
      message.error(getErrorMessage(error))
    } finally {
      setPending(false)
    }
  }

  return (
    <Card title="库存管理"><Alert message="可售库存 = 买家当前可购买数量；预扣库存来自待支付订单，不能通过普通调整释放。" type="info" showIcon /><div className="merchant-toolbar"><Input.Search allowClear placeholder="商品名称或 SKU 编码" aria-label="搜索库存" value={keyword} onChange={(event) => setKeyword(event.target.value)} style={{ maxWidth: 360 }} /><label className="merchant-switch-label"><Switch checked={lowOnly} onChange={setLowOnly} />仅看低库存（≤ 5）</label></div><div className="table-scroll-region" tabIndex={0} role="region" aria-label="库存表格"><Table<MerchantStockItem> rowKey="skuId" dataSource={rows} scroll={{ x: 1000 }} pagination={{ pageSize: 8, showSizeChanger: false }} columns={[
      { title: '商品 / SKU', width: 280, render: (_, item) => <div className="merchant-product-cell"><img src={item.productImage} alt="" width="50" height="50" /><div><strong>{item.productTitle}</strong><span>{item.skuCode}</span></div></div> },
      { title: '规格', render: (_, item) => Object.entries(item.specs).map(([key, value]) => `${key}：${value}`).join(' · ') },
      { title: '可售', dataIndex: 'availableStock', sorter: (a, b) => a.availableStock - b.availableStock, render: (value) => <strong className={value <= 5 ? 'inventory-low' : ''}>{value}</strong> },
      { title: '预扣', dataIndex: 'reservedStock' },
      { title: '现存总量', dataIndex: 'total' },
      { title: '版本', dataIndex: 'version' },
      { title: '操作', fixed: 'right', render: (_, item) => <Button type="link" onClick={() => { setSelected(item); form.setFieldsValue({ availableDelta: 1, reason: '演示盘点调整' }) }}>调整库存</Button> },
    ]} /></div>
      <ModalShim open={!!selected} title={selected ? `调整库存：${selected.productTitle}` : ''} onCancel={() => setSelected(null)}>
        {selected && <div className="inventory-modal-content"><p>当前可售 <strong>{selected.availableStock}</strong>，预扣 <strong>{selected.reservedStock}</strong>，调整后总量将自动保持“可售 + 预扣”。</p><Form<AdjustForm> form={form} layout="vertical" onFinish={adjust}><Form.Item name="availableDelta" label="可售库存变化量" extra="正数增加，负数减少；结果不能小于 0。" rules={[{ required: true, message: '请输入变化量' }]}><InputNumber precision={0} style={{ width: '100%' }} /></Form.Item><Form.Item name="reason" label="调整原因" rules={[{ required: true, max: 200 }]}><Input /></Form.Item><Button type="primary" htmlType="submit" block loading={pending}>保存库存调整</Button></Form></div>}
      </ModalShim>
    </Card>
  )
}

import { Modal } from 'antd'
import type { ReactNode } from 'react'
function ModalShim({ open, title, onCancel, children }: { open: boolean; title: string; onCancel: () => void; children: ReactNode }) {
  return <Modal open={open} title={title} onCancel={onCancel} footer={null} destroyOnHidden focusTriggerAfterClose>{children}</Modal>
}
