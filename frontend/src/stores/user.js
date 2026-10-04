import { defineStore } from 'pinia'
import { authApi, cartApi, chatApi, notificationApi } from '@/api'
import { isTransient } from '@/api/client'

function loadCached() {
  try {
    const raw = sessionStorage.getItem('loginUser')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** 需要登录的页面前缀：会话过期时仅这些路径跳登录，公开页（首页等）只清状态 */
const AUTH_PATHS = [
  '/cart', '/checkout', '/orders', '/my-orders', '/order', '/user',
  '/favorites', '/notifications', '/support', '/messages', '/upload',
  '/seller-orders'
]

function isAuthPath(pathname) {
  if (AUTH_PATHS.some((p) => pathname === p || pathname.startsWith(p + '/') || pathname.startsWith(p + '?'))) {
    return true
  }
  // 管理端除登录页 /admin 外全部需要登录
  return pathname === '/admin' ? false : pathname.startsWith('/admin')
}

export const useUserStore = defineStore('user', {
  state: () => ({
    user: loadCached(),
    cartCount: 0,
    unread: 0,
    notifyUnread: 0
  }),
  getters: {
    isLoggedIn: (s) => !!s.user,
    displayName: (s) => s.user?.nickname || s.user?.username || ''
  },
  actions: {
    setUser(user) {
      this.user = user
      if (user) {
        sessionStorage.setItem('loginUser', JSON.stringify(user))
      } else {
        sessionStorage.removeItem('loginUser')
        this.cartCount = 0
        this.unread = 0
        this.notifyUnread = 0
      }
    },
    login(payload) {
      this.setUser({
        username: payload.username,
        nickname: payload.nickname,
        avatar: payload.avatar,
        role: payload.role,
        balance: payload.balance
      })
    },
    async logout() {
      await authApi.logout()
      this.setUser(null)
      window.location.href = '/'
    },
    async refreshSession() {
      const local = this.user
      if (!local) return
      const d = await authApi.session()
      // 网络异常 / 5xx：瞬时故障，静默重试，绝不清会话跳登录
      if (isTransient(d)) return
      // await 期间已登出：丢弃过期响应，防止把用户写回
      if (this.user !== local && !this.user) return
      if (!d.success) {
        // 服务端明确答复未登录（会话过期/被踢）
        this.setUser(null)
        const kicked = d.code === 'SESSION_KICKED'
          || String(d.message || '').includes('其他地方登录')
        const path = window.location.pathname + window.location.search
        const onAuth = isAuthPath(window.location.pathname)
        const back = path && path !== '/login' ? `redirect=${encodeURIComponent(path)}` : ''
        if (kicked && onAuth) {
          window.location.href = back ? `/login?kicked=1&${back}` : '/login?kicked=1'
        } else if (kicked) {
          // 公开页被踢：留在首页，不强制进登录
          window.location.href = '/?kicked=1'
        } else if (onAuth) {
          window.location.href = back ? `/login?${back}` : '/login'
        }
        return
      }
      if (d.username && local.username && d.username !== local.username) {
        this.setUser(null)
        if (isAuthPath(window.location.pathname)) {
          window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
        }
        return
      }
      this.setUser({
        ...local,
        username: d.username,
        nickname: d.nickname,
        avatar: d.avatar,
        role: d.role,
        balance: d.balance,
        pendingAmount: d.pendingAmount || 0,
        buyerOrderCount: d.buyerOrderCount || 0,
        sellerOrderCount: d.sellerOrderCount || 0
      })
    },
    async refreshCartCount() {
      if (!this.user) {
        this.cartCount = 0
        return
      }
      const d = await cartApi.count()
      if (d.success) this.cartCount = d.count || 0
    },
    async refreshUnread() {
      if (!this.user) {
        this.unread = 0
        this.notifyUnread = 0
        return
      }
      const d = await chatApi.unread()
      if (d.success) this.unread = d.unread || 0
      try {
        const n = await notificationApi.unreadCount()
        if (n.success) this.notifyUnread = n.unread || 0
      } catch {
        this.notifyUnread = 0
      }
    }
  }
})
