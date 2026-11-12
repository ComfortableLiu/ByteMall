import { createContext, useCallback, useContext, useEffect, useMemo, useReducer, useState } from 'react'
import type { ReactNode } from 'react'
import { ErrorState, LoadingState } from '../components/ui/Feedback.tsx'
import { createAppAdapter } from '../services/index.ts'
import type { MockAdapter } from '../services/mock-adapter.ts'
import type { AppState } from '../types/domain.ts'

type AppAdapter = ReturnType<typeof createAppAdapter>
type Invoke = <T>(operation: () => Promise<T>) => Promise<T>

function buildActions(api: AppAdapter, invoke: Invoke) {
  return {
    login: (...args: Parameters<MockAdapter['login']>) => invoke(() => api.login(...args)),
    logout: () => invoke(() => api.logout()),
    reset: () => invoke(() => api.reset()),
    searchProducts: (query: Parameters<MockAdapter['searchProducts']>[0]) => invoke(() => api.searchProducts(query)),
    getProduct: (spuId: string) => invoke(() => api.getProduct(spuId)),
    searchSuggestions: (q: string) => invoke(() => api.searchSuggestions(q)),
    getCart: () => invoke(() => api.getCart()),
    addCartItem: (skuId: string, quantity: number) => invoke(() => api.addCartItem(skuId, quantity)),
    updateCartItem: (skuId: string, patch: { quantity?: number; selected?: boolean }) => invoke(() => api.updateCartItem(skuId, patch)),
    removeCartItem: (skuId: string) => invoke(() => api.removeCartItem(skuId)),
    setCartSelection: (skuIds: string[], selected: boolean) => invoke(() => api.setCartSelection(skuIds, selected)),
    createOrder: (...args: Parameters<MockAdapter['createOrder']>) => invoke(() => api.createOrder(...args)),
    listOrders: (status?: Parameters<MockAdapter['listOrders']>[0]) => invoke(() => api.listOrders(status)),
    getOrder: (orderNo: string) => invoke(() => api.getOrder(orderNo)),
    cancelOrder: (orderNo: string, reason?: string) => invoke(() => api.cancelOrder(orderNo, reason)),
    confirmOrder: (orderNo: string) => invoke(() => api.confirmOrder(orderNo)),
    createPayment: (...args: Parameters<MockAdapter['createPayment']>) => invoke(() => api.createPayment(...args)),
    getPayment: (payNo: string) => invoke(() => api.getPayment(payNo)),
    completeMockPayment: (payNo: string) => invoke(() => api.completeMockPayment(payNo)),
    mockPaymentCallback: (input: { payNo: string; orderNo: string; amount: number; status: 'SUCCESS' | 'FAILED' }) => invoke(() => api.mockPaymentCallback(input)),
    queryMockPayment: (input: { payNo: string; orderNo: string; amount: number; status: 'SUCCESS' | 'FAILED' }) => invoke(() => api.queryMockPayment(input)),
    closePayment: (payNo: string) => invoke(() => api.closePayment(payNo)),
    uploadProductImage: (file: File) => invoke(() => api.uploadProductImage(file)),
    saveAddress: (...args: Parameters<MockAdapter['saveAddress']>) => invoke(() => api.saveAddress(...args)),
    deleteAddress: (addressId: string) => invoke(() => api.deleteAddress(addressId)),
    setDefaultAddress: (addressId: string) => invoke(() => api.setDefaultAddress(addressId)),
    submitMerchantApplication: (...args: Parameters<MockAdapter['submitMerchantApplication']>) => invoke(() => api.submitMerchantApplication(...args)),
    listMerchantProducts: () => invoke(() => api.listMerchantProducts()),
    getMerchantProduct: (spuId: string) => invoke(() => api.getMerchantProduct(spuId)),
    saveProduct: (...args: Parameters<MockAdapter['saveProduct']>) => invoke(() => api.saveProduct(...args)),
    setProductStatus: (...args: Parameters<MockAdapter['setProductStatus']>) => invoke(() => api.setProductStatus(...args)),
    listMerchantOrders: (status?: Parameters<MockAdapter['listMerchantOrders']>[0]) => invoke(() => api.listMerchantOrders(status)),
    getMerchantOrder: (orderNo: string) => invoke(() => api.getMerchantOrder(orderNo)),
    shipOrder: (...args: Parameters<MockAdapter['shipOrder']>) => invoke(() => api.shipOrder(...args)),
    listMerchantStocks: () => invoke(() => api.listMerchantStocks()),
    adjustStock: (...args: Parameters<MockAdapter['adjustStock']>) => invoke(() => api.adjustStock(...args)),
    updateShop: (...args: Parameters<MockAdapter['updateShop']>) => invoke(() => api.updateShop(...args)),
  }
}

type AppActions = ReturnType<typeof buildActions>

interface AppStoreValue {
  state: AppState
  actions: AppActions
  notice: string
  dataSource: 'mock' | 'remote'
  announce: (message: string) => void
}

const AppStoreContext = createContext<AppStoreValue | null>(null)
const syncReducer = (_state: AppState, next: AppState): AppState => next

export function AppStoreProvider({ children }: { children: ReactNode }) {
  const [api] = useState(() => createAppAdapter())
  const [state, dispatch] = useReducer(syncReducer, api.snapshot)
  const [notice, setNotice] = useState(api.loadWarning)
  const [ready, setReady] = useState(false)
  const [initError, setInitError] = useState<unknown>(null)

  const invoke = useCallback<Invoke>(async (operation) => {
    const result = await operation()
    dispatch(api.snapshot)
    return result
  }, [api])
  const actions = useMemo(() => buildActions(api, invoke), [api, invoke])
  const announce = useCallback((message: string) => setNotice(message), [])

  useEffect(() => {
    let active = true
    api.initialize().then(() => {
      if (active) dispatch(api.snapshot)
    }).catch((error) => {
      if (active) setInitError(error)
    }).finally(() => {
      if (active) setReady(true)
    })
    return () => { active = false }
  }, [api])

  useEffect(() => {
    if (!notice) return
    const timer = window.setTimeout(() => setNotice(''), 4500)
    return () => window.clearTimeout(timer)
  }, [notice])

  if (initError) return <main className="container page-section"><ErrorState error={initError} onRetry={() => window.location.reload()} /></main>
  if (!ready) return <main className="container page-section"><LoadingState label={api.dataSource === 'mock' ? '正在加载本地演示数据' : '正在连接线上 API'} /></main>

  return (
    <AppStoreContext.Provider value={{ state, actions, notice, dataSource: api.dataSource, announce }}>
      {children}
      <div className="visually-hidden" aria-live="polite" aria-atomic="true">{notice}</div>
    </AppStoreContext.Provider>
  )
}

export function useAppStore(): AppStoreValue {
  const value = useContext(AppStoreContext)
  if (!value) throw new Error('useAppStore 必须在 AppStoreProvider 内使用')
  return value
}
