import { SearchOutlined, ShoppingCartOutlined, UserOutlined } from '@ant-design/icons'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAppStore } from '../store/app-store.tsx'

export default function BuyerLayout() {
  const { state } = useAppStore()
  const navigate = useNavigate()
  const cartQuantity = state.cart.filter((item) => item.userId === state.currentUser?.userId).reduce((sum, item) => sum + item.quantity, 0)
  return (
    <div className="buyer-shell">
      <a className="skip-link" href="#main-content">跳到主要内容</a>
      <div className="announcement-bar">本地演示商城 · 全场免运费 · 模拟支付不会真实扣款</div>
      <header className="buyer-header">
        <div className="container buyer-header-inner">
          <Link to="/" className="brand-logo" aria-label="ByteMall 首页"><span className="brand-mark">B</span><span>ByteMall<small>BYTE YOUR DAY</small></span></Link>
          <nav className="buyer-nav" aria-label="主导航">
            <NavLink to="/">首页</NavLink>
            <NavLink to="/search">全部好物</NavLink>
            <NavLink to="/orders">我的订单</NavLink>
            <NavLink to="/merchant/apply">商家入驻</NavLink>
          </nav>
          <search className="header-search">
            <form action="/search" method="GET" onSubmit={(event) => {
              event.preventDefault()
              const query = new FormData(event.currentTarget).get('q')?.toString().trim() ?? ''
              navigate(`/search${query ? `?q=${encodeURIComponent(query)}` : ''}`)
            }}>
              <label className="visually-hidden" htmlFor="site-search">搜索商品</label>
              <input id="site-search" name="q" type="search" placeholder="搜索好物" autoComplete="off" spellCheck={false} />
              <button type="submit" aria-label="提交搜索"><SearchOutlined aria-hidden="true" /></button>
            </form>
          </search>
          <div className="header-actions">
            <Link className="icon-button" to="/account" aria-label={state.currentUser ? `${state.currentUser.nickname}的个人中心` : '登录或进入个人中心'}><UserOutlined aria-hidden="true" /></Link>
            <Link className="icon-button cart-link" to="/cart" aria-label={`购物车，共 ${cartQuantity} 件商品`}><ShoppingCartOutlined aria-hidden="true" />{cartQuantity > 0 && <span>{cartQuantity > 99 ? '99+' : cartQuantity}</span>}</Link>
          </div>
        </div>
      </header>
      <main id="main-content" tabIndex={-1}><Outlet /></main>
      <footer className="buyer-footer">
        <div className="container footer-grid">
          <div><Link to="/" className="brand-logo footer-logo"><span className="brand-mark">B</span><span>ByteMall</span></Link><p>把认真挑选的好物，带进每一个普通日常。</p></div>
          <nav aria-label="购物支持"><h2>购物支持</h2><Link to="/search">全部商品</Link><Link to="/orders">订单查询</Link><Link to="/account">收货地址</Link></nav>
          <nav aria-label="商家服务"><h2>商家服务</h2><Link to="/merchant/apply">申请入驻</Link><Link to="/login?role=merchant">商家工作台</Link></nav>
          <div><h2>演示说明</h2><p>页面数据保存在当前浏览器。真实交易需接入文档定义的服务端。</p></div>
        </div>
        <div className="container footer-bottom">© 2026 ByteMall · 设计与接口演示项目</div>
      </footer>
    </div>
  )
}
