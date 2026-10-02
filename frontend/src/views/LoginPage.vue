<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '@/api'
import { showToast } from '@/composables/ui'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  mode: { type: String, default: 'login' }
})

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const isRegister = computed(() => props.mode === 'register' || route.path === '/register')
const loading = ref(false)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: '',
  phone: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '3-50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    {
      validator: (_r, value, cb) => {
        if (!value) cb(new Error('请再次输入密码'))
        else if (value !== registerForm.password) cb(new Error('两次密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const loginRef = ref()
const registerRef = ref()

onMounted(() => {
  if (route.query.kicked) {
    showToast('登录状态已失效，请重新登录', 'warning')
  }
})

async function submitLogin() {
  const formEl = loginRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    const d = await authApi.login(loginForm.username.trim(), loginForm.password)
    loading.value = false
    if (d.success) {
      store.login({
        username: d.username,
        nickname: d.nickname,
        avatar: d.avatar,
        role: d.role,
        balance: d.balance
      })
      showToast(d.message || '登录成功')
      const redirect = route.query.redirect
      router.replace(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/')
    } else {
      showToast(d.message || '登录失败', 'error')
    }
  })
}

async function submitRegister() {
  const formEl = registerRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    const d = await authApi.register({
      username: registerForm.username.trim(),
      password: registerForm.password,
      nickname: registerForm.nickname.trim() || undefined,
      email: registerForm.email.trim() || undefined,
      phone: registerForm.phone.trim() || undefined
    })
    loading.value = false
    if (d.success) {
      store.login({
        username: d.username,
        nickname: d.nickname,
        avatar: d.avatar,
        role: d.role,
        balance: d.balance
      })
      showToast(d.message || '注册成功')
      router.replace('/')
    } else {
      showToast(d.message || '注册失败', 'error')
    }
  })
}
</script>

<template>
  <main class="auth-page">
    <div class="auth-card card">
      <div class="auth-head">
        <div class="logo">优</div>
        <h1 class="h1">{{ isRegister ? '创建账号' : '欢迎回来' }}</h1>
        <p class="muted">{{ isRegister ? '注册即赠 ¥10,000 体验余额' : '登录优品汇，继续你的闲置流转' }}</p>
      </div>

      <!-- 登录 -->
      <el-form
        v-if="!isRegister"
        ref="loginRef"
        :model="loginForm"
        :rules="loginRules"
        label-position="top"
        size="large"
        @submit.prevent="submitLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="submitLogin"
          />
        </el-form-item>
        <el-button type="primary" class="w-full" size="large" :loading="loading" @click="submitLogin">
          登录
        </el-button>
      </el-form>

      <!-- 注册 -->
      <el-form
        v-else
        ref="registerRef"
        :model="registerForm"
        :rules="registerRules"
        label-position="top"
        size="large"
        @submit.prevent="submitRegister"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="registerForm.username" placeholder="3-50 位用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="registerForm.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="registerForm.nickname" placeholder="选填" />
        </el-form-item>
        <div class="row">
          <el-form-item label="邮箱">
            <el-input v-model="registerForm.email" type="email" placeholder="选填" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="registerForm.phone" placeholder="选填" />
          </el-form-item>
        </div>
        <el-button type="primary" class="w-full" size="large" :loading="loading" @click="submitRegister">
          注册并登录
        </el-button>
      </el-form>

      <div class="switch">
        <template v-if="isRegister">
          已有账号？
          <router-link to="/login">直接登录</router-link>
        </template>
        <template v-else>
          还没有账号？
          <router-link to="/register">立即注册</router-link>
        </template>
      </div>

      <div class="admin-tip muted text-sm">
        管理员入口：
        <router-link to="/admin">后台登录</router-link>
      </div>
    </div>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: calc(100vh - var(--header-h) - 80px);
  display: grid;
  place-items: center;
  padding: 40px 16px 64px;
}
.auth-card {
  width: min(100%, 460px);
  padding: 36px 32px;
}
.auth-head {
  text-align: center;
  margin-bottom: 26px;
}
.logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 14px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  box-shadow: 0 12px 28px rgba(212, 136, 60, 0.3);
}
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.switch {
  text-align: center;
  margin-top: 18px;
  font-size: 14px;
  color: var(--ink-soft);
}
.switch a {
  color: var(--accent-deep);
  font-weight: 600;
}
.admin-tip {
  text-align: center;
  margin-top: 14px;
}
.admin-tip a {
  color: var(--accent-deep);
}
</style>
