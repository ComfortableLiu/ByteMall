import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAppStore } from './store/app-store.tsx'

export function PageTitle({ title }: { title: string }) {
  useEffect(() => {
    document.title = `${title}｜ByteMall`
  }, [title])
  return null
}

export function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [pathname])
  return null
}

export function RequireAuth({ children, merchant = false }: { children: ReactNode; merchant?: boolean }) {
  const { state } = useAppStore()
  const location = useLocation()
  if (!state.currentUser) {
    return <Navigate to="/login" state={{ from: `${location.pathname}${location.search}` }} replace />
  }
  if (merchant && (!state.currentUser.roles.includes('MERCHANT') || !state.currentUser.merchantId)) {
    return <Navigate to="/merchant/apply" replace />
  }
  return children
}
