import { CheckCircleFilled, CloseCircleFilled, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../components/ui/Feedback.tsx'
import { Price } from '../../components/ui/Price.tsx'
import { PageTitle } from '../../router.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { Payment, PaymentStatus } from '../../types/domain.ts'

export default function ProviderPaymentPage() {
  const { payNo = '' } = useParams()
  const { actions } = useAppStore()
  const [payment, setPayment] = useState<Payment | null>(null)
  const [resultStatus, setResultStatus] = useState<PaymentStatus | null>(null)
  const [processing, setProcessing] = useState(false)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    let active = true
    actions.getPayment(payNo)
      .then((data) => { if (active) setPayment(data) })
      .catch((reason) => { if (active) setError(reason) })
    return () => { active = false }
  }, [actions, payNo])

  const simulatePayment = async (status: 'SUCCESS' | 'FAILED') => {
    if (!payment) return
    setProcessing(true)
    setError(null)
    try {
      await actions.mockPaymentCallback({
        payNo,
        orderNo: payment.orderNo,
        amount: payment.amount,
        status,
      })
      await new Promise((resolve) => setTimeout(resolve, 800))
      const result = await actions.queryMockPayment({
        payNo,
        orderNo: payment.orderNo,
        amount: payment.amount,
        status,
      })
      setResultStatus(result.status)
    } catch (reason) {
      setError(reason)
    } finally {
      setProcessing(false)
    }
  }

  if (!payment) {
    return <div className="container page-section">{error ? <ErrorState error={error} /> : <LoadingState label="正在连接三方沙箱收银台" />}</div>
  }

  const displayStatus = resultStatus ?? payment.status
  const paid = displayStatus === 'SUCCESS'
  const failed = displayStatus === 'FAILED'
  return (
    <div className="container page-section provider-page">
      <PageTitle title="支付宝沙箱" />
      <section className="provider-shell" aria-labelledby="provider-title">
        <header className="provider-topbar">
          <div><LockOutlined aria-hidden="true" /><strong id="provider-title">ALIPAY SANDBOX</strong></div>
          <span className="demo-badge">模拟三方</span>
        </header>
        <div className="provider-merchant">
          <span>收款方</span>
          <strong>ByteMall 商城</strong>
          <small>订单 {payment.orderNo}</small>
        </div>
        <ol className="provider-steps" aria-label="支付进度">
          <li className={paid ? 'done' : 'active'}>01 确认订单</li>
          <li className={paid ? 'done' : 'active'}>02 渠道确认</li>
          <li className={paid ? 'done' : ''}>03 返回商户</li>
        </ol>
        {paid ? (
          <div className="provider-success" role="status">
            <CheckCircleFilled aria-hidden="true" />
            <h2>支付成功</h2>
            <p>三方回调已完成，模拟查单返回 SUCCESS，支付单 {payment.payNo}</p>
            <Price amount={payment.amount} large />
            <Link className="button button-primary" to={`/payment/${payment.orderNo}`}>返回 ByteMall</Link>
          </div>
        ) : failed ? (
          <div className="provider-success provider-failed" role="status">
            <CloseCircleFilled aria-hidden="true" />
            <h2>支付失败</h2>
            <p>三方回调已完成，模拟查单返回 FAILED。你可以重新尝试支付。</p>
            <Price amount={payment.amount} large />
            <button className="button button-primary" type="button" onClick={() => setResultStatus(null)}>重新支付</button>
            <Link className="provider-back" to={`/payment/${payment.orderNo}`}>返回商户</Link>
          </div>
        ) : (
          <>
            <div className="provider-amount">
              <span>支付金额</span>
              <Price amount={payment.amount} large />
            </div>
            <dl className="provider-order">
              <div><dt>支付单号</dt><dd>{payment.payNo}</dd></div>
              <div><dt>支付方式</dt><dd>支付宝沙箱</dd></div>
            </dl>
            <p className="provider-notice"><SafetyCertificateOutlined aria-hidden="true" />仅模拟三方支付过程，不会真实扣款</p>
            <form onSubmit={(event) => { event.preventDefault(); void simulatePayment('SUCCESS') }}>
              <button className="button button-primary provider-pay-button" type="submit" disabled={processing || payment.status !== 'PENDING'}>
                {processing ? '正在模拟渠道确认…' : `确认支付 ¥${(payment.amount / 100).toFixed(2)}`}
              </button>
            </form>
            <button className="button button-secondary provider-failure-button" type="button" disabled={processing || payment.status !== 'PENDING'} onClick={() => void simulatePayment('FAILED')}>模拟支付失败</button>
            {error !== null && <p className="form-error" role="alert">{getErrorMessage(error)}</p>}
            <p className="provider-status" role="status" aria-live="polite">{processing ? '正在模拟三方回调并查询结果…' : ''}</p>
            <Link className="provider-back" to={`/payment/${payment.orderNo}`}>取消并返回商户</Link>
          </>
        )}
      </section>
    </div>
  )
}
