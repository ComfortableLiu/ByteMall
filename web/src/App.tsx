import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import { LoadingState } from './components/ui/Feedback.tsx'
import { RequireAuth, ScrollToTop } from './router.tsx'

const BuyerLayout = lazy(() => import('./layouts/BuyerLayout.tsx'))
const HomePage = lazy(() => import('./pages/buyer/HomePage.tsx'))
const SearchPage = lazy(() => import('./pages/buyer/SearchPage.tsx'))
const ProductPage = lazy(() => import('./pages/buyer/ProductPage.tsx'))
const CartPage = lazy(() => import('./pages/buyer/CartPage.tsx'))
const CheckoutPage = lazy(() => import('./pages/buyer/CheckoutPage.tsx'))
const PaymentPage = lazy(() => import('./pages/buyer/PaymentPage.tsx'))
const OrdersPage = lazy(() => import('./pages/buyer/OrdersPage.tsx'))
const OrderDetailPage = lazy(() => import('./pages/buyer/OrderDetailPage.tsx'))
const AccountPage = lazy(() => import('./pages/buyer/AccountPage.tsx'))
const LoginPage = lazy(() => import('./pages/buyer/LoginPage.tsx'))
const MerchantApplyPage = lazy(() => import('./pages/buyer/MerchantApplyPage.tsx'))
const NotFoundPage = lazy(() => import('./pages/buyer/NotFoundPage.tsx'))
const MerchantLayout = lazy(() => import('./layouts/MerchantLayout.tsx'))
const DashboardPage = lazy(() => import('./pages/merchant/DashboardPage.tsx'))
const MerchantProductsPage = lazy(() => import('./pages/merchant/MerchantProductsPage.tsx'))
const ProductEditPage = lazy(() => import('./pages/merchant/ProductEditPage.tsx'))
const MerchantOrdersPage = lazy(() => import('./pages/merchant/MerchantOrdersPage.tsx'))
const MerchantOrderDetailPage = lazy(() => import('./pages/merchant/MerchantOrderDetailPage.tsx'))
const InventoryPage = lazy(() => import('./pages/merchant/InventoryPage.tsx'))
const ShopPage = lazy(() => import('./pages/merchant/ShopPage.tsx'))

export default function App() {
  return (
    <Suspense fallback={<div className="container page-section"><LoadingState label="正在打开页面" /></div>}>
      <ScrollToTop />
      <Routes>
        <Route element={<BuyerLayout />}>
          <Route index element={<HomePage />} />
          <Route path="search" element={<SearchPage />} />
          <Route path="product/:spuId" element={<ProductPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="cart" element={<RequireAuth><CartPage /></RequireAuth>} />
          <Route path="checkout" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
          <Route path="payment/:orderNo" element={<RequireAuth><PaymentPage /></RequireAuth>} />
          <Route path="orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
          <Route path="orders/:orderNo" element={<RequireAuth><OrderDetailPage /></RequireAuth>} />
          <Route path="account" element={<RequireAuth><AccountPage /></RequireAuth>} />
          <Route path="merchant/apply" element={<RequireAuth><MerchantApplyPage /></RequireAuth>} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
        <Route path="merchant" element={<RequireAuth merchant><MerchantLayout /></RequireAuth>}>
          <Route index element={<DashboardPage />} />
          <Route path="products" element={<MerchantProductsPage />} />
          <Route path="products/new" element={<ProductEditPage />} />
          <Route path="products/:spuId/edit" element={<ProductEditPage />} />
          <Route path="orders" element={<MerchantOrdersPage />} />
          <Route path="orders/:orderNo" element={<MerchantOrderDetailPage />} />
          <Route path="inventory" element={<InventoryPage />} />
          <Route path="shop" element={<ShopPage />} />
        </Route>
      </Routes>
    </Suspense>
  )
}
