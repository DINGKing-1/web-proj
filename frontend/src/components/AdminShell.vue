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
      <div class="brand">优品汇管理</div>
      <nav>
        <router-link to="/admin/dashboard" class="nav-item" :class="{ active: props.active === 'dashboard' }">
          仪表盘
        </router-link>
        <router-link to="/admin/products" class="nav-item" :class="{ active: props.active === 'products' }">
          商品管理
        </router-link>
        <router-link to="/admin/users" class="nav-item" :class="{ active: props.active === 'users' }">
          用户管理
        </router-link>
        <a href="/" target="_blank" class="nav-item">查看前台</a>
      </nav>
      <button class="logout" @click="logout">退出登录</button>
    </aside>
    <main class="main">
      <header class="top">
        <h1><slot name="title">管理后台</slot></h1>
        <span class="muted">管理员</span>
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
}
.nav-item {
  display: block;
  padding: 11px 12px;
  border-radius: 10px;
  margin-bottom: 4px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.08);
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
