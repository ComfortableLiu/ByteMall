import { App, Button, Card, Input, Select, Space, Table } from 'antd'
import { EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { StatusBadge } from '../../components/ui/StatusBadge.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { ProductDetail, ProductStatus } from '../../types/domain.ts'
import { formatMoney } from '../../utils/format.ts'

export default function MerchantProductsPage() {
  const { state, actions } = useAppStore()
  const { message, modal } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<ProductStatus | ''>('')
  const [pendingId, setPendingId] = useState('')
  const products = useMemo(() => state.products.filter((item) => item.merchantId === state.currentUser?.merchantId && (!status || item.status === status) && (!keyword.trim() || `${item.title}${item.brand}`.toLowerCase().includes(keyword.trim().toLowerCase()))), [state.products, state.currentUser, keyword, status])

  const changeStatus = async (product: ProductDetail) => {
    const next = product.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
    if (next === 'OFF_SALE') {
      modal.confirm({ title: `下架“${product.title}”？`, content: '下架后买家不能再搜索或下单，历史订单不受影响。', okText: '确认下架', cancelText: '保留在售', onOk: () => updateStatus(product, next) })
      return
    }
    await updateStatus(product, next)
  }
  const updateStatus = async (product: ProductDetail, next: ProductStatus) => {
    setPendingId(product.spuId)
    try {
      await actions.setProductStatus(product.spuId, next, product.version)
      message.success(next === 'ON_SALE' ? '商品已上架，买家端现在可见' : '商品已下架')
    } catch (error) {
      message.error(getErrorMessage(error))
    } finally {
      setPendingId('')
    }
  }

  return (
    <Card title="商品管理" extra={<Link className="ant-btn ant-btn-primary" to="/merchant/products/new"><PlusOutlined /> 新建商品</Link>}>
      <div className="merchant-toolbar"><Input.Search allowClear placeholder="搜索商品名称或品牌" aria-label="搜索商品名称或品牌" value={keyword} onChange={(event) => setKeyword(event.target.value)} style={{ maxWidth: 360 }} /><Select aria-label="按商品状态筛选" value={status} onChange={setStatus} style={{ width: 160 }} options={[{ value: '', label: '全部状态' }, { value: 'DRAFT', label: '草稿' }, { value: 'ON_SALE', label: '销售中' }, { value: 'OFF_SALE', label: '已下架' }]} /></div>
      <div className="table-scroll-region" tabIndex={0} role="region" aria-label="商家商品表格"><Table<ProductDetail> rowKey="spuId" dataSource={products} scroll={{ x: 1050 }} pagination={{ pageSize: 6, showSizeChanger: false }} locale={{ emptyText: '暂无符合条件的商品' }} columns={[
        { title: '商品', width: 280, render: (_, product) => <div className="merchant-product-cell"><img src={product.coverImage} alt="" width="54" height="54" /><div><Link to={`/merchant/products/${product.spuId}/edit`}>{product.title}</Link><span>{product.brand} · {product.spuId}</span></div></div> },
        { title: 'SKU', render: (_, product) => `${product.skus.length} 个规格` },
        { title: '价格', render: (_, product) => product.minPrice === product.maxPrice ? formatMoney(product.minPrice) : `${formatMoney(product.minPrice)} 起` },
        { title: '可售库存', render: (_, product) => product.skus.reduce((sum, sku) => sum + (state.stocks[sku.skuId]?.availableStock ?? 0), 0) },
        { title: '状态', dataIndex: 'status', render: (value) => <StatusBadge status={value} /> },
        { title: '操作', fixed: 'right', width: 180, render: (_, product) => <Space><Link to={`/merchant/products/${product.spuId}/edit`}><EditOutlined /> 编辑</Link><Button type="link" size="small" loading={pendingId === product.spuId} onClick={() => void changeStatus(product)}>{product.status === 'ON_SALE' ? '下架' : '上架'}</Button></Space> },
      ]} /></div>
      <p className="table-help">商品修改会同步到买家演示页面；搜索索引在真实架构中通过事件最终一致。</p>
    </Card>
  )
}
