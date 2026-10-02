import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'home', component: () => import('@/views/HomePage.vue') },
  { path: '/login', name: 'login', component: () => import('@/views/LoginPage.vue') },
  { path: '/register', name: 'register', component: () => import('@/views/LoginPage.vue'), props: { mode: 'register' } },
  { path: '/category/:name', name: 'category', component: () => import('@/views/CategoryPage.vue') },
  { path: '/product/:id', name: 'product', component: () => import('@/views/ProductPage.vue') },
  { path: '/search', name: 'search', component: () => import('@/views/SearchPage.vue') },
  { path: '/upload', name: 'upload', component: () => import('@/views/UploadPage.vue'), meta: { auth: true } },
  { path: '/cart', name: 'cart', component: () => import('@/views/CartPage.vue'), meta: { auth: true } },
  { path: '/checkout', name: 'checkout', component: () => import('@/views/CheckoutPage.vue'), meta: { auth: true } },
  { path: '/orders', name: 'orders', component: () => import('@/views/MyOrdersPage.vue'), meta: { auth: true } },
  { path: '/my-orders', name: 'my-orders', component: () => import('@/views/MyOrdersPage.vue'), meta: { auth: true } },
  { path: '/order/:id', name: 'order-detail', component: () => import('@/views/OrderDetailPage.vue'), meta: { auth: true } },
  { path: '/seller-orders', name: 'seller-orders', component: () => import('@/views/SellerOrdersPage.vue'), meta: { auth: true } },
  { path: '/user', name: 'user', component: () => import('@/views/UserPage.vue'), meta: { auth: true } },
  { path: '/favorites', name: 'favorites', component: () => import('@/views/FavoritesPage.vue'), meta: { auth: true } },
  { path: '/messages', name: 'messages', component: () => import('@/views/MessagesPage.vue'), meta: { auth: true } },
  { path: '/admin', name: 'admin-login', component: () => import('@/views/admin/AdminLoginPage.vue'), meta: { bare: true, guestOnly: true } },
  { path: '/admin/dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/AdminDashboardPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/products', name: 'admin-products', component: () => import('@/views/admin/AdminProductsPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/users', name: 'admin-users', component: () => import('@/views/admin/AdminUsersPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  let user = null
  try {
    const raw = sessionStorage.getItem('loginUser')
    user = raw ? JSON.parse(raw) : null
  } catch {
    user = null
  }
  if (to.meta.auth && !user) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && user?.role !== 'admin') {
    return { path: '/admin' }
  }
  return true
})

export default router
