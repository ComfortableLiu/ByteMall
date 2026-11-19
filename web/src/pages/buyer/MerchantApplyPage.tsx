import { CheckCircleOutlined, ShopOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageTitle } from '../../router.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { formatDate } from '../../utils/format.ts'
import { useAppStore } from '../../store/app-store.tsx'

export default function MerchantApplyPage() {
  const { state, actions } = useAppStore()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const isMerchant = Boolean(state.currentUser?.merchantId && state.currentUser.merchantId !== '-1')
  const application = state.applications.find((item) => String(item.userId) === String(state.currentUser?.userId))
  const applicationStatus = application
    ? application.status === 'PENDING'
      ? { label: '审核中', className: 'status-pending' }
      : application.status === 'APPROVED'
        ? { label: '已通过', className: 'status-success' }
        : { label: '已驳回', className: 'status-cancelled' }
    : null

  const submit = async (form: HTMLFormElement) => {
    const data = new FormData(form)
    setPending(true)
    setError('')
    try {
      await actions.submitMerchantApplication({
        shopName: data.get('shopName')?.toString() ?? '',
        contactName: data.get('contactName')?.toString() ?? '',
        contactMobile: data.get('contactMobile')?.toString() ?? '',
        licenseNo: data.get('licenseNo')?.toString() ?? '',
      })
    } catch (reason) {
      setError(getErrorMessage(reason))
    } finally {
      setPending(false)
    }
  }

  const approve = async () => {
    if (!application) return
    setPending(true)
    setError('')
    try {
      await actions.approveMerchantApplication(application.applicationId)
    } catch (reason) {
      setError(getErrorMessage(reason))
    } finally {
      setPending(false)
    }
  }

  const reject = async () => {
    if (!application) return
    const value = window.prompt('请输入驳回原因')
    if (value === null) return
    if (!value.trim()) {
      setError('驳回原因不能为空')
      return
    }
    setPending(true)
    setError('')
    try {
      await actions.rejectMerchantApplication(application.applicationId, value.trim())
    } catch (reason) {
      setError(getErrorMessage(reason))
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="container page-section apply-page">
      <PageTitle title="商家入驻" />
      <div className="apply-grid"><section className="apply-intro"><span className="eyebrow">GROW WITH BYTEMALL</span><h1>把好商品，<br />带给更多懂它的人。</h1><p>清晰的商品与库存工具、可追踪的订单履约，让你的经营更专注。</p><ol><li><span>01</span><div><strong>提交入驻信息</strong><p>填写店铺与联系资料。</p></div></li><li><span>02</span><div><strong>平台审核</strong><p>真实场景由平台运营核验资质。</p></div></li><li><span>03</span><div><strong>开始经营</strong><p>发布 SPU/SKU，管理库存和订单。</p></div></li></ol></section>
        <section className="apply-form-card" aria-labelledby="apply-title">{isMerchant ? <div className="application-status"><CheckCircleOutlined aria-hidden="true" /><h2>你已是演示商家</h2><p>当前身份已具备商家权限，可直接进入工作台。</p><Link className="button button-primary" to="/merchant">进入商家工作台</Link></div> : application && applicationStatus ? <div className="application-status"><ShopOutlined aria-hidden="true" /><span className={`status-badge ${applicationStatus.className}`}>{applicationStatus.label}</span><h2>{application.shopName}</h2><p>申请编号 {application.applicationId}</p><p>联系人 {application.contactName}（{application.contactMobile}）</p><p>证件编号 {application.licenseNo}</p><p>提交时间 {formatDate(application.createdAt)}</p>{application.status === 'REJECTED' && application.rejectReason && <p>驳回原因：{application.rejectReason}</p>}<p>{application.status === 'PENDING' ? '已提交，等待平台审核。' : application.status === 'APPROVED' ? '审核已通过，重新登录后即可进入商家工作台。' : '可修改资料后重新申请。'}</p>{application.status === 'PENDING' && <div className="form-actions"><button className="button button-primary" type="button" disabled={pending} onClick={() => void approve()}>一键审核通过</button><button className="button button-secondary" type="button" disabled={pending} onClick={() => void reject()}>一键驳回</button></div>}{error && <p className="form-error" role="alert">{error}</p>}<button className="button button-secondary" type="button" onClick={() => void actions.reset()}>重置演示后重新申请</button></div> : <><h2 id="apply-title">入驻申请</h2><p>请使用虚构信息体验，不要填写真实证件号码。</p><form onSubmit={(event) => { event.preventDefault(); void submit(event.currentTarget) }}><div className="field"><label htmlFor="apply-shop">店铺名称</label><input id="apply-shop" name="shopName" required minLength={2} maxLength={80} placeholder="例如：日常好物研究所" /></div><div className="field"><label htmlFor="apply-name">联系人姓名</label><input id="apply-name" name="contactName" autoComplete="name" required maxLength={60} /></div><div className="field"><label htmlFor="apply-mobile">联系手机号</label><input id="apply-mobile" name="contactMobile" type="tel" autoComplete="tel" required minLength={5} maxLength={20} /></div><div className="field"><label htmlFor="apply-license">演示营业执照编号</label><input id="apply-license" name="licenseNo" autoComplete="off" required minLength={5} maxLength={64} placeholder="例如：DEMO-BUSINESS-001" spellCheck={false} /></div>{error && <p className="form-error" role="alert">{error}</p>}<button className="button button-primary login-submit" type="submit" disabled={pending}>{pending ? '正在提交…' : '提交入驻申请'}</button></form></>}</section></div>
    </div>
  )
}
