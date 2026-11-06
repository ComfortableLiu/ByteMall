import { createSeedState } from '../data/seed.ts'
import type { AppState } from '../types/domain.ts'
import { DomainError, MockAdapter } from './mock-adapter.ts'
import { RemoteAdapter } from './remote-adapter.ts'

export { DomainError } from './mock-adapter.ts'

function migrateState(state: AppState): AppState {
  const legacy = state as AppState & { application?: AppState['applications'][number] | null }
  const applications = Array.isArray(state.applications)
    ? state.applications.map((item) => ({ ...item, userId: item.userId ?? 'buyer-001' }))
    : legacy.application
      ? [{ ...legacy.application, userId: legacy.application.userId ?? 'buyer-001' }]
      : []
  return {
    ...state,
    cart: state.cart.map((item) => ({ ...item, userId: item.userId ?? 'buyer-001' })),
    addresses: state.addresses.map((item) => ({ ...item, userId: item.userId ?? 'buyer-001' })),
    orders: state.orders.map((item) => ({ ...item, version: Number.isInteger(item.version) ? item.version : 1 })),
    applications,
  }
}

export function createMockAdapter(): MockAdapter {
  let initialState: AppState | undefined
  let loadWarning = ''
  try {
    const raw = localStorage.getItem('bytemall-demo-v1')
    if (raw) {
      const parsed = JSON.parse(raw) as AppState
      if (parsed.schemaVersion === 1 && Array.isArray(parsed.products) && Array.isArray(parsed.orders)) {
        initialState = migrateState(parsed)
      } else {
        loadWarning = '本地演示数据版本不兼容，已恢复初始数据'
      }
    }
  } catch {
    initialState = createSeedState()
    loadWarning = '本地演示数据无法读取，已恢复初始数据'
  }
  const adapter = new MockAdapter(initialState, { persist: true, latency: 120 })
  adapter.loadWarning = loadWarning
  return adapter
}

export function createAdapter(options: { useMock: boolean; baseUrl?: string }): MockAdapter | RemoteAdapter {
  return options.useMock ? createMockAdapter() : new RemoteAdapter(options.baseUrl)
}

export function createAppAdapter(): MockAdapter | RemoteAdapter {
  return createAdapter({ useMock: __BYTE_MALL_USE_MOCK__, baseUrl: __BYTE_MALL_API_BASE_URL__ })
}

export const getErrorMessage = (error: unknown): string => {
  if (error instanceof DomainError || error instanceof Error) return error.message
  return '操作未完成，请稍后重试'
}
