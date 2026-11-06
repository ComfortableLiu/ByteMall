import type { ReactNode } from 'react'
import { getErrorMessage } from '../../services/index.ts'

export function LoadingState({ label = '正在加载内容' }: { label?: string }) {
  return <div className="ui-state" role="status"><span className="loading-spinner" aria-hidden="true" /><p>{label}</p></div>
}

export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <div className="ui-state"><span className="empty-symbol" aria-hidden="true">○</span><h2>{title}</h2><p>{description}</p>{action}</div>
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return <div className="ui-state ui-error" role="alert"><h2>暂时无法完成</h2><p>{getErrorMessage(error)}</p>{onRetry && <button className="button button-secondary" type="button" onClick={onRetry}>重新尝试</button>}</div>
}
