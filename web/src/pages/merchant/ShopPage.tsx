import { App, Button, Card, Descriptions, Form, Input, Tag } from 'antd'
import { ShopOutlined } from '@ant-design/icons'
import { getErrorMessage } from '../../services/index.ts'
import { useAppStore } from '../../store/app-store.tsx'
import type { MerchantShop } from '../../types/domain.ts'

export default function ShopPage() {
  const { state, actions } = useAppStore()
  const { message } = App.useApp()
  const save = async (values: MerchantShop) => {
    try {
      await actions.updateShop(values)
      message.success('店铺资料已保存')
    } catch (error) {
      message.error(getErrorMessage(error))
    }
  }
  return (
    <div className="shop-page"><Card className="shop-identity"><div className="shop-logo"><ShopOutlined /></div><div><h1>{state.shop.shopName}</h1><p>商家 ID：{state.shop.merchantId}</p><Tag color="success">正常营业 · 演示</Tag></div></Card><div className="shop-grid"><Card title="店铺资料"><Form<MerchantShop> layout="vertical" initialValues={state.shop} onFinish={save}><Form.Item name="merchantId" hidden><Input /></Form.Item><Form.Item name="status" hidden><Input /></Form.Item><Form.Item name="shopName" label="店铺名称" rules={[{ required: true, min: 2, max: 80 }]}><Input /></Form.Item><Form.Item name="contactName" label="联系人" rules={[{ required: true, max: 60 }]}><Input autoComplete="name" /></Form.Item><Form.Item name="contactMobile" label="联系电话" rules={[{ required: true, min: 5, max: 20 }]}><Input type="tel" autoComplete="tel" /></Form.Item><Form.Item name="logoUrl" label="Logo URL（可选）"><Input type="url" placeholder="真实环境请使用受控对象存储地址" /></Form.Item><Button type="primary" htmlType="submit">保存店铺资料</Button></Form></Card><Card title="入驻与权限"><Descriptions column={1} items={[{ key: 'status', label: '入驻状态', children: '已通过（演示种子数据）' }, { key: 'role', label: '当前角色', children: state.currentUser?.roles.join(' / ') }, { key: 'scope', label: '数据范围', children: `仅 ${state.shop.merchantId}` }, { key: 'owner', label: '账号', children: state.currentUser?.userId }]} /><p className="table-help">真实入驻审核由平台运营完成。商家不能修改自己的审核状态或访问其他商家数据。</p></Card></div></div>
  )
}
