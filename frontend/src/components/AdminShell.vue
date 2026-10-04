<script setup>
import { useRouter } from 'vue-router'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const props = defineProps({
  active: { type: String, default: 'dashboard' }
})

async function logout() {
  try {
    await authApi.logout()
  } catch {
    // ignore network errors, still clear local state
  }
  store.setUser(null)
  router.replace('/admin')
}
</script>

<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="brand">
        <span class="logo-avatar" aria-hidden="true">优</span>
        <span>优品汇管理</span>
      </div>
      <nav>
        <router-link to="/admin/dashboard" class="nav-item" :class="{ active: props.active === 'dashboard' }">
          仪表盘
        </router-link>
        <router-link to="/admin/products" class="nav-item" :class="{ active: props.active === 'products' }">
          商品管理
        </router-link>
        <router-link to="/admin/categories" class="nav-item" :class="{ active: props.active === 'categories' }">
          分类管理
        </router-link>
        <router-link to="/admin/users" class="nav-item" :class="{ active: props.active === 'users' }">
          用户管理
        </router-link>
        <router-link to="/admin/recycle" class="nav-item" :class="{ active: props.active === 'recycle' }">
          回收站
        </router-link>
        <router-link to="/admin/orders" class="nav-item" :class="{ active: props.active === 'orders' }">
          订单管理
        </router-link>
        <router-link to="/admin/withdraws" class="nav-item" :class="{ active: props.active === 'withdraws' }">
          提现审核
        </router-link>
        <router-link to="/admin/coupons" class="nav-item" :class="{ active: props.active === 'coupons' }">
          优惠券
        </router-link>
        <router-link to="/admin/reports" class="nav-item" :class="{ active: props.active === 'reports' }">
          举报处理
        </router-link>
        <router-link to="/admin/announcements" class="nav-item" :class="{ active: props.active === 'announcements' }">
          公告管理
        </router-link>
        <router-link to="/admin/support" class="nav-item" :class="{ active: props.active === 'support' }">
          客服咨询
        </router-link>
        <router-link to="/admin/audit" class="nav-item" :class="{ active: props.active === 'audit' }">
          审计日志
        </router-link>
        <a href="/" target="_blank" class="nav-item">查看前台</a>
      </nav>
      <button class="logout" @click="logout">退出登录</button>
    </aside>
    <main class="main">
      <header class="top">
        <h1><slot name="title">管理后台</slot></h1>
        <div class="admin-profile">
          <span class="logo-avatar" aria-hidden="true">优</span>
          <span class="muted">管理员</span>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  min-height: 100vh;
}
.sidebar {
  background: #22170f;
  color: #f5ebe0;
  padding: 22px 16px;
  display: flex;
  flex-direction: column;
}
.brand {
  font-weight: 700;
  margin-bottom: 22px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.logo-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 16px;
  font-weight: 800;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  box-shadow: 0 6px 16px rgba(212, 136, 60, 0.35);
  flex-shrink: 0;
  animation: logoPulse 3.5s ease-in-out infinite;
}
@keyframes logoPulse {
  0%, 100% { box-shadow: 0 6px 16px rgba(212, 136, 60, 0.35); }
  50% { box-shadow: 0 8px 22px rgba(212, 136, 60, 0.55); }
}
.admin-profile {
  display: flex;
  align-items: center;
  gap: 10px;
}
.nav-item {
  display: block;
  padding: 11px 12px;
  border-radius: 10px;
  margin-bottom: 4px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.08);
  transform: translateX(3px);
}
.nav-item.active {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
}
.logout {
  margin-top: auto;
  text-align: left;
  color: rgba(255, 255, 255, 0.65);
  padding: 12px;
}
.main {
  padding: 28px;
  background: var(--bg);
}
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 22px;
}
.top h1 {
  margin: 0;
  font-size: 24px;
}
@media (max-width: 800px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }
}
</style>
