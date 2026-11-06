import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AppErrorBoundary } from './components/ui/AppErrorBoundary.tsx'
import { AppStoreProvider } from './store/app-store.tsx'
import App from './App.tsx'
import './styles/global.css'
import './styles/buyer.css'
import './styles/merchant.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppErrorBoundary>
      <AppStoreProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AppStoreProvider>
    </AppErrorBoundary>
  </StrictMode>,
)
