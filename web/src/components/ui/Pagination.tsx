export function Pagination({ page, size, total, onChange }: { page: number; size: number; total: number; onChange: (page: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size))
  if (pages <= 1) return null
  return (
    <nav className="pagination" aria-label="搜索结果分页">
      <button type="button" className="button button-secondary" disabled={page <= 1} onClick={() => onChange(page - 1)}>上一页</button>
      <span>第 <strong>{page}</strong> / {pages} 页</span>
      <button type="button" className="button button-secondary" disabled={page >= pages} onClick={() => onChange(page + 1)}>下一页</button>
    </nav>
  )
}
