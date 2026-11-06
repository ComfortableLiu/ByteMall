import { useState } from 'react'
import type { Address, AddressInput } from '../../types/domain.ts'

export function AddressForm({ address, onSave, onCancel, pending }: { address?: Address; onSave: (input: AddressInput, addressId?: string) => Promise<void>; onCancel: () => void; pending: boolean }) {
  const [input, setInput] = useState<AddressInput>({
    receiverName: address?.receiverName ?? '',
    receiverMobile: address?.receiverMobile ?? '',
    province: address?.province ?? '上海市',
    city: address?.city ?? '上海市',
    district: address?.district ?? '',
    detailAddress: address?.detailAddress ?? '',
    postalCode: address?.postalCode ?? '',
    isDefault: address?.isDefault ?? false,
  })
  const update = (field: keyof AddressInput, value: string | boolean) => setInput((current) => ({ ...current, [field]: value }))
  return (
    <form className="address-form" onSubmit={(event) => { event.preventDefault(); void onSave(input, address?.addressId) }}>
      <div className="form-grid">
        <div className="field"><label htmlFor="receiver-name">收件人姓名</label><input id="receiver-name" name="receiverName" autoComplete="name" required maxLength={60} value={input.receiverName} onChange={(event) => update('receiverName', event.target.value)} /></div>
        <div className="field"><label htmlFor="receiver-mobile">手机号码</label><input id="receiver-mobile" name="receiverMobile" type="tel" autoComplete="tel" required minLength={5} maxLength={20} value={input.receiverMobile} onChange={(event) => update('receiverMobile', event.target.value)} /></div>
        <div className="field"><label htmlFor="address-province">省份</label><input id="address-province" name="province" autoComplete="address-level1" required value={input.province} onChange={(event) => update('province', event.target.value)} /></div>
        <div className="field"><label htmlFor="address-city">城市</label><input id="address-city" name="city" autoComplete="address-level2" required value={input.city} onChange={(event) => update('city', event.target.value)} /></div>
        <div className="field"><label htmlFor="address-district">区县</label><input id="address-district" name="district" autoComplete="address-level3" required value={input.district} onChange={(event) => update('district', event.target.value)} /></div>
        <div className="field"><label htmlFor="address-postal">邮政编码（可选）</label><input id="address-postal" name="postalCode" autoComplete="postal-code" inputMode="numeric" maxLength={20} value={input.postalCode} onChange={(event) => update('postalCode', event.target.value)} /></div>
        <div className="field field-full"><label htmlFor="address-detail">详细地址</label><textarea id="address-detail" name="detailAddress" autoComplete="street-address" required maxLength={300} rows={3} value={input.detailAddress} onChange={(event) => update('detailAddress', event.target.value)} /></div>
      </div>
      <label className="checkbox-label"><input type="checkbox" name="isDefault" checked={input.isDefault} onChange={(event) => update('isDefault', event.target.checked)} />设为默认地址</label>
      <div className="form-actions"><button className="button button-primary" type="submit" disabled={pending}>{pending ? '正在保存…' : '保存地址'}</button><button className="button button-secondary" type="button" onClick={onCancel}>取消</button></div>
    </form>
  )
}
