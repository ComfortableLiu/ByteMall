export function QuantityStepper({ value, max, onChange, label = '商品数量' }: { value: number; max: number; onChange: (value: number) => void; label?: string }) {
  return (
    <div className="quantity-stepper">
      <button type="button" aria-label={`减少${label}`} disabled={value <= 1} onClick={() => onChange(value - 1)}>−</button>
      <input aria-label={label} type="number" min={1} max={Math.max(1, max)} value={value} onChange={(event) => {
        const next = Number(event.target.value)
        if (Number.isInteger(next) && next >= 1 && next <= max) onChange(next)
      }} />
      <button type="button" aria-label={`增加${label}`} disabled={value >= max} onClick={() => onChange(value + 1)}>+</button>
    </div>
  )
}
