import { defineStore } from 'pinia'
import { authApi, cartApi, chatApi } from '@/api'

function loadCached() {
  try {
    const raw = sessionStorage.getItem('loginUser')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    user: loadCached(),
    cartCount: 0,
    unread: 0
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
      if (!d.success) {
        this.setUser(null)
        window.location.href = '/login?kicked=1'
        return
      }
      if (d.username && local.username && d.username !== local.username) {
        this.setUser(null)
        window.location.href = '/login'
        return
      }
      this.setUser({
        ...local,
        username: d.username,
        nickname: d.nickname,
        avatar: d.avatar,
        role: d.role,
        balance: d.balance,
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
        return
      }
      const d = await chatApi.unread()
      if (d.success) this.unread = d.unread || 0
    }
  }
})
