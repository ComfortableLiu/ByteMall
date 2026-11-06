import { ShopOutlined, UserOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { PageTitle } from '../../router.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { DemoRole } from '../../types/domain.ts'

export default function LoginPage() {
  const { actions, announce, dataSource } = useAppStore()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [role, setRole] = useState<DemoRole>(searchParams.get('role') === 'merchant' ? 'merchant' : 'buyer')
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const isMock = dataSource === 'mock'

  const submit = async (form: HTMLFormElement) => {
    const data = new FormData(form)
    setPending(true)
    setError('')
    try {
      await actions.login(role, {
        mobile: data.get('mobile')?.toString() ?? '',
        password: data.get('password')?.toString() ?? '',
        nickname: data.get('nickname')?.toString(),
        mode,
      })
      announce(isMock ? `${role === 'merchant' ? '商家' : '买家'}演示身份登录成功` : '线上账号登录成功')
      const from = (location.state as { from?: string } | null)?.from
      navigate(role === 'merchant' ? '/merchant' : from || '/', { replace: true })
    } catch (reason) {
      setError(getErrorMessage(reason))
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="login-page">
      <PageTitle title={mode === 'login' ? '登录' : '注册'} />
      <section className="login-story"><div><span className="eyebrow">WELCOME TO BYTEMALL</span><h1>每一种日常，<br />都值得认真对待。</h1><p>登录后管理购物车、订单与收货地址，或进入商家后台体验经营流程。</p><div className="login-story-note">{isMock ? '本地 mock 模式仅使用虚构数据，无需真实账号信息。' : '当前为线上模式，登录信息将发送到配置的 API 网关。'}</div></div><img src="/images/backpack.svg" alt="原点森野绿城市双肩包" width="800" height="800" fetchPriority="high" /></section>
      <section className="login-form-panel" aria-labelledby="login-title"><span className="eyebrow">YOUR BYTEMALL ID</span><h2 id="login-title">{mode === 'login' ? '欢迎回来' : isMock ? '创建演示账号' : '注册账号'}</h2><p>{mode === 'login' ? '选择身份，开始你的 ByteMall 之旅。' : isMock ? '填写演示信息即可体验完整流程。' : '填写账号信息并提交至线上用户服务。'}</p><div className="role-selector" role="group" aria-label="选择登录入口"><button type="button" className={role === 'buyer' ? 'selected' : ''} aria-pressed={role === 'buyer'} onClick={() => setRole('buyer')}><UserOutlined aria-hidden="true" /><strong>我是买家</strong><span>购物与订单</span></button><button type="button" className={role === 'merchant' ? 'selected' : ''} aria-pressed={role === 'merchant'} onClick={() => setRole('merchant')}><ShopOutlined aria-hidden="true" /><strong>我是商家</strong><span>商品与履约</span></button></div>
        <form key={`${role}-${mode}-${dataSource}`} onSubmit={(event) => { event.preventDefault(); void submit(event.currentTarget) }}>
          {mode === 'register' && <div className="field"><label htmlFor="login-nickname">昵称</label><input id="login-nickname" name="nickname" autoComplete="nickname" required minLength={1} maxLength={60} defaultValue={isMock ? role === 'merchant' ? '演示店主' : '新买家' : ''} /></div>}
          <div className="field"><label htmlFor="login-mobile">{isMock ? '演示手机号' : '手机号'}</label><input id="login-mobile" name="mobile" type="tel" autoComplete="username" required minLength={5} maxLength={20} defaultValue={isMock ? role === 'merchant' ? '13900139000' : '13800138000' : ''} /></div>
          <div className="field"><label htmlFor="login-password">{isMock ? '演示密码' : '密码'}</label><input id="login-password" name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={8} maxLength={128} defaultValue={isMock ? 'demo1234' : ''} aria-describedby="password-hint" /><small id="password-hint">{isMock ? '仅用于本地表单演示，不会发送或验证真实凭据。' : '线上模式会通过 HTTPS 发送至配置的网关，请使用有效的开发账号。'}</small></div>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="button button-primary login-submit" type="submit" disabled={pending}>{pending ? '正在登录…' : mode === 'login' ? `进入${role === 'merchant' ? '商家' : '买家'}${isMock ? '演示' : '线上'}商城` : isMock ? '注册并进入演示商城' : '注册并登录'}</button>
        </form><button className="text-button login-mode-switch" type="button" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>{mode === 'login' ? isMock ? '没有账号？创建演示账号' : '没有账号？注册线上账号' : '已有账号？返回登录'}</button>
      </section>
    </div>
  )
}
