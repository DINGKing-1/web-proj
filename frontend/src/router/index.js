import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'home', component: () => import('@/views/HomePage.vue') },
  { path: '/login', name: 'login', component: () => import('@/views/LoginPage.vue') },
  { path: '/register', name: 'register', component: () => import('@/views/LoginPage.vue'), props: { mode: 'register' } },
  {
    path: '/category/:name',
    name: 'category',
    component: () => import('@/views/CategoryPage.vue'),
    // 「全部」就是首页，不再单独渲染一个全部分类页
    beforeEnter: (to) => (to.params.name === '全部' ? '/' : true)
  },
  { path: '/product/:id', name: 'product', component: () => import('@/views/ProductPage.vue') },
  { path: '/shop/:username', name: 'shop', component: () => import('@/views/ShopPage.vue') },
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
  { path: '/notifications', name: 'notifications', component: () => import('@/views/NotificationsPage.vue'), meta: { auth: true } },
  { path: '/support', name: 'support', component: () => import('@/views/SupportPage.vue'), meta: { auth: true } },
  { path: '/messages', name: 'messages', component: () => import('@/views/MessagesPage.vue'), meta: { auth: true } },
  { path: '/admin', name: 'admin-login', component: () => import('@/views/admin/AdminLoginPage.vue'), meta: { bare: true, guestOnly: true } },
  { path: '/admin/dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/AdminDashboardPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/products', name: 'admin-products', component: () => import('@/views/admin/AdminProductsPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/users', name: 'admin-users', component: () => import('@/views/admin/AdminUsersPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/recycle', name: 'admin-recycle', component: () => import('@/views/admin/AdminRecyclePage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/orders', name: 'admin-orders', component: () => import('@/views/admin/AdminOrdersPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/withdraws', name: 'admin-withdraws', component: () => import('@/views/admin/AdminWithdrawsPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/reports', name: 'admin-reports', component: () => import('@/views/admin/AdminReportsPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/announcements', name: 'admin-announcements', component: () => import('@/views/admin/AdminAnnouncementsPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/categories', name: 'admin-categories', component: () => import('@/views/admin/AdminCategoriesPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/audit', name: 'admin-audit', component: () => import('@/views/admin/AdminAuditPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/coupons', name: 'admin-coupons', component: () => import('@/views/admin/AdminCouponsPage.vue'), meta: { bare: true, auth: true, admin: true } },
  { path: '/admin/support', name: 'admin-support', component: () => import('@/views/admin/AdminSupportPage.vue'), meta: { bare: true, auth: true, admin: true } },
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
  let user
  try {
    const raw = sessionStorage.getItem('loginUser')
    user = raw ? JSON.parse(raw) : null
  } catch {
    user = null
  }
  // 多开标签页：sessionStorage 为空但 Cookie 会话可能仍有效。
  // 放行后由页面/API 校验，401 时 client.js 再跳登录，避免误踢。
  if (to.meta.auth && !user) {
    return true
  }
  if (to.meta.admin && user && user.role !== 'admin') {
    return { path: '/admin' }
  }
  // guestOnly：已登录的管理员访问 /admin 登录页时直接进后台
  if (to.meta.guestOnly && user?.role === 'admin') {
    return { path: '/admin/dashboard' }
  }
  return true
})

export default router
