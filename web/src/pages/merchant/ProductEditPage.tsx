import { App, Alert, Button, Card, Form, Input, InputNumber, Select, Space, Switch, Upload } from 'antd'
import type { UploadProps } from 'antd'
import { CloudUploadOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { LoadingState } from '../../components/ui/Feedback.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { ProductDetail } from '../../types/domain.ts'

interface ProductFormValues {
  title: string
  subtitle: string
  categoryId: string
  brand: string
  descriptionHtml: string
  imageUrl: string
}

interface EditableSku {
  skuId?: string
  skuCode: string
  specText: string
  price: number
  originalPrice?: number
  enabled: boolean
}

const emptySku = (): EditableSku => ({ skuCode: '', specText: '颜色：默认', price: 0, enabled: true })
const imageOptions = ['headphones', 'watch', 'backpack', 'coffee', 'lamp', 'sneakers', 'keyboard', 'speaker'].map((name) => ({ value: `/images/${name}.svg`, label: `${name} 本地商品插画` }))

function parseSpecs(value: string): Record<string, string> {
  const entries = value.split('/').map((part) => part.trim()).filter(Boolean).map((part) => {
    const separator = part.includes('：') ? '：' : ':'
    const [key, ...rest] = part.split(separator)
    if (!key?.trim() || !rest.join(separator).trim()) throw new Error(`规格“${part}”格式不正确，请使用“名称：值”`)
    return [key.trim(), rest.join(separator).trim()] as const
  })
  if (!entries.length) throw new Error('每个 SKU 至少需要一个规格值')
  return Object.fromEntries(entries)
}

export default function ProductEditPage() {
  const { spuId } = useParams()
  const { state, actions, dataSource } = useAppStore()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [form] = Form.useForm<ProductFormValues>()
  const [product, setProduct] = useState<ProductDetail | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [skus, setSkus] = useState<EditableSku[]>([emptySku()])
  const [pending, setPending] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [loadError, setLoadError] = useState('')
  const imageUrl = Form.useWatch('imageUrl', form)
  const isMock = dataSource === 'mock'

  useEffect(() => {
    if (!spuId) return
    let active = true
    actions.getMerchantProduct(spuId).then((data) => {
      if (!active) return
      setProduct(data)
      form.setFieldsValue({ title: data.title, subtitle: data.subtitle, categoryId: data.categoryId, brand: data.brand, descriptionHtml: data.descriptionHtml, imageUrl: data.coverImage })
      setSkus(data.skus.map((item) => ({ skuId: item.skuId, skuCode: item.skuCode, specText: Object.entries(item.specs).map(([key, value]) => `${key}：${value}`).join(' / '), price: item.price, originalPrice: item.originalPrice, enabled: item.enabled })))
      setLoaded(true)
    }).catch((error) => { if (active) { setLoadError(getErrorMessage(error)); setLoaded(true) } })
    return () => { active = false }
  }, [actions, form, spuId])

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      message.error('仅支持 JPG、PNG、WebP 图片')
      return Upload.LIST_IGNORE
    }
    if (file.size > 5 * 1024 * 1024) {
      message.error('图片大小不能超过 5 MiB')
      return Upload.LIST_IGNORE
    }
    return true
  }

  const uploadImage: UploadProps['customRequest'] = async ({ file, onSuccess, onError }) => {
    setUploading(true)
    try {
      const result = await actions.uploadProductImage(file as File)
      form.setFieldsValue({ imageUrl: result.url })
      onSuccess?.(result)
      message.success('商品图片已上传到阿里云 OSS')
    } catch (error) {
      onError?.(error instanceof Error ? error : new Error(getErrorMessage(error)))
      message.error(getErrorMessage(error))
    } finally {
      setUploading(false)
    }
  }

  const updateSku = (index: number, patch: Partial<EditableSku>) => setSkus((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item))
  const save = async (values: ProductFormValues) => {
    setPending(true)
    try {
      const saved = await actions.saveProduct({
        title: values.title,
        subtitle: values.subtitle,
        categoryId: values.categoryId,
        brand: values.brand,
        descriptionHtml: values.descriptionHtml,
        images: [{ url: values.imageUrl, altText: values.title, sortOrder: 0, isPrimary: true }],
        skus: skus.map((item) => ({ skuId: item.skuId, skuCode: item.skuCode, specs: parseSpecs(item.specText), price: item.price, originalPrice: item.originalPrice, enabled: item.enabled })),
        version: product?.version,
      }, spuId)
      message.success(spuId ? '商品修改已保存并同步到买家端' : `商品 ${saved.spuId} 已创建为草稿`)
      navigate('/merchant/products')
    } catch (error) {
      message.error(getErrorMessage(error))
    } finally {
      setPending(false)
    }
  }

  if (spuId && !loaded) return <LoadingState label="正在加载商家商品" />
  if (loadError) return <Alert type="error" showIcon message="商品无法加载" description={loadError} action={<Link to="/merchant/products">返回商品列表</Link>} />
  return (
    <div className="product-edit-page"><div className="merchant-edit-heading"><div><Link to="/merchant/products">← 返回商品列表</Link><h1>{spuId ? '编辑商品' : '新建商品'}</h1></div>{product && <span>当前状态：{product.status} · 版本 {product.version}</span>}</div><Alert type="info" showIcon message="新建商品默认保存为草稿；上架前请在库存管理中补充库存。" />
      <Form<ProductFormValues> form={form} layout="vertical" requiredMark="optional" initialValues={{ title: '', subtitle: '', categoryId: 'cat-digital', brand: '', descriptionHtml: '', imageUrl: isMock ? '/images/headphones.svg' : '' }} onFinish={save} disabled={pending || uploading}>
        <Card title="基本信息" className="merchant-form-card"><div className="merchant-form-grid"><Form.Item name="title" label="商品名称" rules={[{ required: true, min: 2, max: 120, message: '请输入 2–120 字的商品名称' }]}><Input placeholder="例如：声境 Pro 无线降噪耳机" /></Form.Item><Form.Item name="brand" label="品牌" rules={[{ required: true, message: '请输入品牌' }]}><Input /></Form.Item><Form.Item name="categoryId" label="分类" rules={[{ required: true }]}><Select options={state.categories.map((item) => ({ value: item.categoryId, label: item.name }))} /></Form.Item><Form.Item name="subtitle" label="副标题" rules={[{ max: 200 }]}><Input /></Form.Item></div><Form.Item name="descriptionHtml" label="商品描述" extra="仅保存演示文本；真实富文本需由服务端允许列表净化。" rules={[{ required: true, message: '请输入商品描述' }]}><Input.TextArea rows={4} maxLength={2000} showCount /></Form.Item><div className="image-picker">
          {isMock ? (
            <Form.Item name="imageUrl" label="本地商品插画" rules={[{ required: true }]}><Select options={imageOptions} /></Form.Item>
          ) : (
            <>
              <Form.Item name="imageUrl" label="商品主图（阿里云 OSS）" rules={[{ required: true, message: '请上传商品主图' }]}>
                <Input readOnly placeholder="上传成功后自动填充 OSS 图片地址" />
              </Form.Item>
              <Upload.Dragger
                accept=".jpg,.jpeg,.png,.webp"
                maxCount={1}
                multiple={false}
                showUploadList={false}
                disabled={uploading}
                beforeUpload={beforeUpload}
                customRequest={uploadImage}
              >
                <p className="ant-upload-drag-icon"><CloudUploadOutlined /></p>
                <p className="ant-upload-text">{uploading ? '正在上传到阿里云 OSS…' : '点击或拖拽图片到此处上传'}</p>
                <p className="ant-upload-hint">支持 JPG、PNG、WebP，单张图片不超过 5 MiB。</p>
              </Upload.Dragger>
              <p role="status" aria-live="polite">{!uploading && imageUrl ? '图片上传成功，保存商品后生效。' : ''}</p>
            </>
          )}
          {imageUrl && <img src={imageUrl} alt="商品主图预览" width="140" height="140" />}
        </div></Card>
        <Card title="SKU 规格与价格" className="merchant-form-card" extra={<Button icon={<PlusOutlined />} onClick={() => setSkus((current) => [...current, emptySku()])}>添加 SKU</Button>}><div className="sku-editor" role="region" aria-label="SKU 编辑表格" tabIndex={0}><table><thead><tr><th scope="col">SKU 编码</th><th scope="col">规格（用 / 分隔）</th><th scope="col">售价（元）</th><th scope="col">划线价（元）</th><th scope="col">可售</th><th scope="col">操作</th></tr></thead><tbody>{skus.map((item, index) => <tr key={item.skuId ?? `new-${index}`}><td><Input aria-label={`第 ${index + 1} 行 SKU 编码`} value={item.skuCode} onChange={(event) => updateSku(index, { skuCode: event.target.value })} placeholder="例如 SJ-PRO-BK" /></td><td><Input aria-label={`第 ${index + 1} 行规格`} value={item.specText} onChange={(event) => updateSku(index, { specText: event.target.value })} placeholder="颜色：黑色 / 容量：256GB" /></td><td><InputNumber aria-label={`第 ${index + 1} 行售价`} min={0} precision={2} value={item.price / 100} onChange={(value) => updateSku(index, { price: Math.round((value ?? 0) * 100) })} /></td><td><InputNumber aria-label={`第 ${index + 1} 行划线价`} min={0} precision={2} value={item.originalPrice === undefined ? undefined : item.originalPrice / 100} onChange={(value) => updateSku(index, { originalPrice: value === null ? undefined : Math.round(value * 100) })} /></td><td><Switch aria-label={`第 ${index + 1} 行是否可售`} checked={item.enabled} onChange={(enabled) => updateSku(index, { enabled })} /></td><td><Button type="text" danger aria-label={`删除第 ${index + 1} 个 SKU`} disabled={skus.length === 1} icon={<DeleteOutlined />} onClick={() => setSkus((current) => current.filter((_, itemIndex) => itemIndex !== index))} /></td></tr>)}</tbody></table></div><p className="table-help">价格在界面以元输入，提交时转换为整数分；同一商品的 SKU 编码与规格组合不能重复。</p></Card>
        <Card className="merchant-form-card submit-card"><Space><Button type="primary" htmlType="submit" size="large" loading={pending}>{spuId ? '保存商品修改' : '创建商品草稿'}</Button><Button size="large" onClick={() => navigate('/merchant/products')}>取消</Button></Space></Card>
      </Form>
    </div>
  )
}
