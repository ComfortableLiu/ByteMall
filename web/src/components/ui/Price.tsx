import { formatMoney } from '../../utils/format.ts'

export function Price({ amount, original, large = false }: { amount: number; original?: number; large?: boolean }) {
  return (
    <span className={large ? 'price price-large' : 'price'}>
      <span>{formatMoney(amount)}</span>
      {original && original > amount && <del>{formatMoney(original)}</del>}
    </span>
  )
}
