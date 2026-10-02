<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, authApi } from '@/api'
import { showToast } from '@/composables/ui'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const username = ref('')
const password = ref('')
const errorMsg = ref('')
const loading = ref(false)

async function submit() {
  errorMsg.value = ''
  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  const d = await adminApi.login(username.value.trim(), password.value)
  if (d.success) {
    // 同步完整登录态（含 role=admin），供路由守卫与前台导航共用
    const s = await authApi.session()
    if (s.success) {
      store.login({
        username: s.username,
        nickname: s.nickname,
        avatar: s.avatar,
        role: s.role,
        balance: s.balance
      })
    } else {
      store.login({ username: d.username, nickname: d.username, role: 'admin', avatar: '', balance: 0 })
    }
    loading.value = false
    showToast('登录成功')
    router.replace('/admin/dashboard')
    return
  }
  loading.value = false
  errorMsg.value = d.message || '登录失败'
}
</script>

<template>
  <main class="admin-login">
    <form class="card login-card" @submit.prevent="submit">
      <h1>优品汇 · 管理后台</h1>
      <p class="muted">请使用管理员账号登录</p>
      <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
      <div class="form-group">
        <label>用户名</label>
        <input v-model="username" class="form-input" autocomplete="username" />
      </div>
      <div class="form-group">
        <label>密码</label>
        <input v-model="password" type="password" class="form-input" autocomplete="current-password" />
      </div>
      <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>
      <router-link to="/" class="back">返回前台</router-link>
    </form>
  </main>
</template>

<style scoped>
.admin-login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(900px 500px at 20% -10%, #3a2a1c 0%, transparent 55%),
    radial-gradient(700px 400px at 100% 0%, #2a1c12 0%, transparent 50%),
    #22170f;
}
.login-card {
  width: min(100%, 420px);
  padding: 36px 30px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.login-card h1 {
  margin: 0 0 8px;
  font-size: 22px;
}
.error {
  background: var(--danger-soft);
  color: var(--danger);
  padding: 10px 12px;
  border-radius: 10px;
  margin-bottom: 14px;
  font-size: 13px;
}
.back {
  display: block;
  text-align: center;
  margin-top: 16px;
  color: var(--muted);
  font-size: 13px;
}
.back:hover {
  color: var(--accent);
}
</style>
