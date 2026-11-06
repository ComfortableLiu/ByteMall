import { Link } from 'react-router-dom'
import { PageTitle } from '../../router.tsx'

export default function NotFoundPage() {
  return <div className="container page-section not-found"><PageTitle title="页面不存在" /><span className="eyebrow">404 · LOST IN THE MALL</span><h1>这件好物不在原处了</h1><p>你访问的页面不存在或已经移动。可以返回首页，或重新搜索。</p><div><Link className="button button-primary" to="/">返回首页</Link><Link className="button button-secondary" to="/search">搜索商品</Link></div></div>
}
