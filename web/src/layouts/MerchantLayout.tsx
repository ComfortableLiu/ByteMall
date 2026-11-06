import { App as AntApp, Avatar, Button, ConfigProvider, Layout, Menu, Space, Tag } from 'antd'
import { DashboardOutlined, HomeOutlined, InboxOutlined, MenuFoldOutlined, MenuUnfoldOutlined, ShopOutlined, ShoppingCartOutlined, UnorderedListOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { PageTitle } from '../router.tsx'
import { useAppStore } from '../store/app-store.tsx'

const titles: Record<string, string> = {
  '/merchant': '经营概览',
  '/merchant/products': '商品管理',
  '/merchant/products/new': '新建商品',
  '/merchant/orders': '订单管理',
  '/merchant/inventory': '库存管理',
  '/merchant/shop': '店铺设置',
}

export default function MerchantLayout() {
  const { state, actions } = useAppStore()
  const location = useLocation()
  const navigate = useNavigate()
  const [collapsed, setCollapsed] = useState(false)
  const title = titles[location.pathname] ?? (location.pathname.includes('/edit') ? '编辑商品' : location.pathname.includes('/orders/') ? '订单详情' : '商家工作台')
  const selected = location.pathname.includes('/products') ? '/merchant/products' : location.pathname.includes('/orders') ? '/merchant/orders' : titles[location.pathname] ? location.pathname : '/merchant'
  return (
    <ConfigProvider theme={{ token: { colorPrimary: '#3155e7', colorInfo: '#3155e7', borderRadius: 10, colorBgLayout: '#f4f6f9', fontFamily: 'Inter, PingFang SC, Microsoft YaHei, system-ui, sans-serif' }, components: { Layout: { siderBg: '#172033', headerBg: '#ffffff' }, Menu: { darkItemBg: '#172033', darkSubMenuItemBg: '#172033' } } }}>
      <AntApp>
        <Layout className="merchant-shell">
          <a className="skip-link" href="#merchant-main">跳到主要内容</a>
          <Layout.Sider width={236} collapsed={collapsed} collapsedWidth={0} breakpoint="lg" trigger={null} onBreakpoint={(broken) => setCollapsed(broken)}>
            <Link to="/merchant" className="merchant-brand"><span className="brand-mark">B</span>{!collapsed && <span>ByteMall<small>MERCHANT CENTER</small></span>}</Link>
            <Menu theme="dark" mode="inline" selectedKeys={[selected]} onClick={({ key }) => navigate(key)} items={[
              { key: '/merchant', icon: <DashboardOutlined />, label: '经营概览' },
              { key: '/merchant/products', icon: <InboxOutlined />, label: '商品管理' },
              { key: '/merchant/orders', icon: <ShoppingCartOutlined />, label: '订单管理' },
              { key: '/merchant/inventory', icon: <UnorderedListOutlined />, label: '库存管理' },
              { key: '/merchant/shop', icon: <ShopOutlined />, label: '店铺设置' },
            ]} />
            {!collapsed && <div className="merchant-sidebar-note"><span>演示经营环境</span><p>数据保存在本地浏览器，操作会同步到买家端。</p></div>}
          </Layout.Sider>
          <Layout>
            <Layout.Header className="merchant-header"><Space><Button type="text" aria-label={collapsed ? '展开导航' : '收起导航'} icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} onClick={() => setCollapsed(!collapsed)} /><div className="merchant-page-title"><span>商家工作台</span><strong>{title}</strong></div></Space><Space size="middle"><Link to="/" className="merchant-back-store"><HomeOutlined /> 买家商城</Link><Tag color="blue">演示账号</Tag><Avatar>{state.currentUser?.nickname.slice(0, 1)}</Avatar><span className="merchant-user-name">{state.currentUser?.nickname}</span><Button type="text" onClick={() => { void actions.logout().then(() => navigate('/')) }}>退出</Button></Space></Layout.Header>
            <PageTitle title={title} />
            <Layout.Content id="merchant-main" className="merchant-main" tabIndex={-1}><Outlet /></Layout.Content>
            <Layout.Footer className="merchant-footer">ByteMall Merchant · 商品 / 库存 / 订单统一演示数据</Layout.Footer>
          </Layout>
        </Layout>
      </AntApp>
    </ConfigProvider>
  )
}
