import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AddressForm } from '../../components/buyer/AddressForm.tsx'
import { PageTitle } from '../../router.tsx'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { Address } from '../../types/domain.ts'

export default function AccountPage() {
  const { state, actions, announce } = useAppStore()
  const navigate = useNavigate()
  const [editing, setEditing] = useState<Address | null | 'new' | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')

  const save = async (input: Parameters<typeof actions.saveAddress>[0], addressId?: string) => {
    setPending(true)
    setError('')
    try {
      await actions.saveAddress(input, addressId)
      setEditing(null)
      announce('地址已保存')
    } catch (reason) {
      setError(getErrorMessage(reason))
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="container page-section account-page">
      <PageTitle title="个人中心" />
      <header className="page-heading compact"><span className="eyebrow">YOUR ACCOUNT</span><h1>你好，{state.currentUser?.nickname}</h1><p>管理收货信息、演示身份与本地数据。</p></header>
      <div className="account-grid"><aside className="account-card"><div className="account-avatar" aria-hidden="true">{state.currentUser?.nickname.slice(0, 1)}</div><h2>{state.currentUser?.nickname}</h2><p>用户 ID：{state.currentUser?.userId}</p><div className="role-tags">{state.currentUser?.roles.map((role) => <span key={role}>{role}</span>)}</div><button className="button button-secondary" type="button" onClick={() => { void actions.logout().then(() => navigate('/')) }}>退出当前身份</button><button className="text-button reset-link" type="button" onClick={() => { if (window.confirm('确定重置所有演示数据吗？本地下单和编辑记录将被清除。')) void actions.reset().then(() => navigate('/')) }}>重置演示数据</button></aside>
        <section className="account-content" aria-labelledby="address-management"><div className="section-title-row"><div><span className="eyebrow">ADDRESS BOOK</span><h2 id="address-management">收货地址</h2></div><button className="button button-primary button-small" type="button" onClick={() => setEditing('new')}><PlusOutlined aria-hidden="true" />新增地址</button></div>
          {error && <p className="form-error" role="alert">{error}</p>}
          {editing && <div className="address-form-panel"><h3>{editing === 'new' ? '新增收货地址' : '编辑收货地址'}</h3><AddressForm key={editing === 'new' ? 'new' : editing.addressId} address={editing === 'new' ? undefined : editing} onSave={save} onCancel={() => setEditing(null)} pending={pending} /></div>}
          <div className="account-address-list">{state.addresses.filter((item) => item.userId === state.currentUser?.userId).map((address) => <article className="account-address" key={address.addressId}><header><strong>{address.receiverName}</strong><span>{address.receiverMobile}</span>{address.isDefault && <small>默认地址</small>}</header><p>{address.province}{address.city}{address.district}{address.detailAddress}</p><footer><button className="text-button" type="button" disabled={address.isDefault} onClick={() => void actions.setDefaultAddress(address.addressId)}>设为默认</button><button className="icon-button" type="button" aria-label={`编辑${address.receiverName}的地址`} onClick={() => setEditing(address)}><EditOutlined aria-hidden="true" /></button><button className="icon-button" type="button" aria-label={`删除${address.receiverName}的地址`} onClick={() => { if (window.confirm('确定删除这个地址吗？历史订单不受影响。')) void actions.deleteAddress(address.addressId) }}><DeleteOutlined aria-hidden="true" /></button></footer></article>)}</div>
        </section></div>
    </div>
  )
}
