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
const isReset = ref(false)
const resetForm = reactive({ username: '', email: '', code: '', password: '' })
const sendingCode = ref(false)
const codeCooldown = ref(0)
let cooldownTimer = null
const loading = ref(false)
const showLoginPwd = ref(false)
const showRegPwd = ref(false)
const showRegPwd2 = ref(false)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: '',
  phone: ''
})

const loginErrors = reactive({ username: '', password: '' })
const registerErrors = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: ''
})

function validateLogin() {
  loginErrors.username = loginForm.username.trim() ? '' : '请输入用户名'
  loginErrors.password = loginForm.password ? '' : '请输入密码'
  return !loginErrors.username && !loginErrors.password
}

async function sendResetCode() {
  if (!resetForm.username.trim() || !resetForm.email.trim()) {
    showToast('请先填写用户名和邮箱', 'warning')
    return
  }
  if (codeCooldown.value > 0) return
  sendingCode.value = true
  const d = await authApi.sendResetCode({
    username: resetForm.username.trim(),
    email: resetForm.email.trim()
  })
  sendingCode.value = false
  if (d.success) {
    showToast(d.message || '验证码已发送')
    codeCooldown.value = 60
    cooldownTimer && clearInterval(cooldownTimer)
    cooldownTimer = setInterval(() => {
      codeCooldown.value -= 1
      if (codeCooldown.value <= 0) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }, 1000)
  } else {
    showToast(d.message || '发送失败', 'error')
  }
}

async function submitReset() {
  if (!resetForm.username.trim() || !resetForm.email.trim() || !resetForm.code.trim() || !resetForm.password) {
    showToast('请填写完整信息', 'warning')
    return
  }
  if (resetForm.password.length < 6) {
    showToast('新密码至少 6 位', 'warning')
    return
  }
  loading.value = true
  const d = await authApi.resetPassword({
    username: resetForm.username.trim(),
    email: resetForm.email.trim(),
    code: resetForm.code.trim(),
    password: resetForm.password
  })
  loading.value = false
  if (d.success) {
    showToast(d.message || '密码重置成功')
    isReset.value = false
    resetForm.username = ''
    resetForm.email = ''
    resetForm.code = ''
    resetForm.password = ''
  } else {
    showToast(d.message || '重置失败', 'error')
  }
}

function validateRegister() {
  const u = registerForm.username.trim()
  registerErrors.username = !u ? '请输入用户名' : u.length < 3 || u.length > 50 ? '3-50 个字符' : ''
  registerErrors.password = !registerForm.password
    ? '请输入密码'
    : registerForm.password.length < 6
      ? '密码至少 6 位'
      : ''
  registerErrors.confirmPassword = !registerForm.confirmPassword
    ? '请再次输入密码'
    : registerForm.confirmPassword !== registerForm.password
      ? '两次密码不一致'
      : ''
  registerErrors.email =
    registerForm.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email) ? '邮箱格式不正确' : ''
  registerErrors.phone =
    registerForm.phone && !/^1\d{10}$/.test(registerForm.phone) ? '手机号格式不正确' : ''
  return !Object.values(registerErrors).some(Boolean)
}

onMounted(() => {
  if (route.query.kicked) {
    showToast('登录状态已失效，请重新登录', 'warning')
  }
})

async function submitLogin() {
  if (loading.value) return
  if (!validateLogin()) return
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
    // 仅允许站内相对路径跳转，排除 "//host" 等开放重定向
    const redirect = route.query.redirect
    const safe =
      typeof redirect === 'string' &&
      redirect.startsWith('/') &&
      !redirect.startsWith('//') &&
      !redirect.startsWith('/\\')
    router.replace(safe ? redirect : '/')
  } else {
    showToast(d.message || '登录失败', 'error')
  }
}

async function submitRegister() {
  if (loading.value) return
  if (!validateRegister()) return
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

      <!-- 忘记密码 -->
      <form v-if="isReset && !isRegister" class="auth-form" @submit.prevent="submitReset">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="resetForm.username" class="form-input" placeholder="请输入用户名" />
        </div>
        <div class="form-group">
          <label>注册邮箱</label>
          <input v-model="resetForm.email" class="form-input" type="email" placeholder="请输入注册邮箱" />
        </div>
        <div class="form-group">
          <label>邮箱验证码</label>
          <div class="code-row">
            <input v-model="resetForm.code" class="form-input" inputmode="numeric" maxlength="6" placeholder="6 位验证码" />
            <button type="button" class="btn btn-outline code-btn" :disabled="sendingCode || codeCooldown > 0" @click="sendResetCode">
              {{ codeCooldown > 0 ? `${codeCooldown}s` : sendingCode ? '发送中…' : '获取验证码' }}
            </button>
          </div>
        </div>
        <div class="form-group">
          <label>新密码</label>
          <input v-model="resetForm.password" class="form-input" type="password" placeholder="至少 6 位" />
        </div>
        <button type="submit" class="btn btn-primary btn-lg btn-block" :disabled="loading">
          {{ loading ? '提交中…' : '重置密码' }}
        </button>
        <button type="button" class="link-btn" @click="isReset = false">返回登录</button>
      </form>

      <!-- 登录 -->
      <form v-if="!isRegister && !isReset" class="auth-form" @submit.prevent="submitLogin">
        <div class="form-group">
          <label for="login-username">用户名</label>
          <input
            id="login-username"
            v-model="loginForm.username"
            class="form-input"
            :class="{ invalid: loginErrors.username }"
            placeholder="请输入用户名"
            autocomplete="username"
            @input="loginErrors.username = ''"
          />
          <p v-if="loginErrors.username" class="field-error">{{ loginErrors.username }}</p>
        </div>
        <div class="form-group">
          <label for="login-password">密码</label>
          <div class="input-wrap">
            <input
              id="login-password"
              v-model="loginForm.password"
              class="form-input has-toggle"
              :class="{ invalid: loginErrors.password }"
              :type="showLoginPwd ? 'text' : 'password'"
              placeholder="请输入密码"
              autocomplete="current-password"
              @input="loginErrors.password = ''"
            />
            <button
              type="button"
              class="pwd-toggle"
              :aria-label="showLoginPwd ? '隐藏密码' : '显示密码'"
              @click="showLoginPwd = !showLoginPwd"
            >
              <i class="fas" :class="showLoginPwd ? 'fa-eye-slash' : 'fa-eye'"></i>
            </button>
          </div>
          <p v-if="loginErrors.password" class="field-error">{{ loginErrors.password }}</p>
        </div>
        <button type="submit" class="btn btn-primary btn-lg btn-block" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
        <button v-if="!isReset" type="button" class="link-btn" @click="isReset = true">忘记密码？</button>
      </form>

      <!-- 注册 -->
      <form v-else class="auth-form" @submit.prevent="submitRegister">
        <div class="form-group">
          <label for="reg-username">用户名</label>
          <input
            id="reg-username"
            v-model="registerForm.username"
            class="form-input"
            :class="{ invalid: registerErrors.username }"
            placeholder="3-50 位用户名"
            autocomplete="username"
            @input="registerErrors.username = ''"
          />
          <p v-if="registerErrors.username" class="field-error">{{ registerErrors.username }}</p>
        </div>
        <div class="form-group">
          <label for="reg-password">密码</label>
          <div class="input-wrap">
            <input
              id="reg-password"
              v-model="registerForm.password"
              class="form-input has-toggle"
              :class="{ invalid: registerErrors.password }"
              :type="showRegPwd ? 'text' : 'password'"
              placeholder="至少 6 位"
              autocomplete="new-password"
              @input="registerErrors.password = ''"
            />
            <button
              type="button"
              class="pwd-toggle"
              :aria-label="showRegPwd ? '隐藏密码' : '显示密码'"
              @click="showRegPwd = !showRegPwd"
            >
              <i class="fas" :class="showRegPwd ? 'fa-eye-slash' : 'fa-eye'"></i>
            </button>
          </div>
          <p v-if="registerErrors.password" class="field-error">{{ registerErrors.password }}</p>
        </div>
        <div class="form-group">
          <label for="reg-password2">确认密码</label>
          <div class="input-wrap">
            <input
              id="reg-password2"
              v-model="registerForm.confirmPassword"
              class="form-input has-toggle"
              :class="{ invalid: registerErrors.confirmPassword }"
              :type="showRegPwd2 ? 'text' : 'password'"
              placeholder="再次输入密码"
              autocomplete="new-password"
              @input="registerErrors.confirmPassword = ''"
            />
            <button
              type="button"
              class="pwd-toggle"
              :aria-label="showRegPwd2 ? '隐藏密码' : '显示密码'"
              @click="showRegPwd2 = !showRegPwd2"
            >
              <i class="fas" :class="showRegPwd2 ? 'fa-eye-slash' : 'fa-eye'"></i>
            </button>
          </div>
          <p v-if="registerErrors.confirmPassword" class="field-error">{{ registerErrors.confirmPassword }}</p>
        </div>
        <div class="form-group">
          <label for="reg-nickname">昵称 <span class="optional">选填</span></label>
          <input
            id="reg-nickname"
            v-model="registerForm.nickname"
            class="form-input"
            placeholder="怎么称呼你"
          />
        </div>
        <div class="row">
          <div class="form-group">
            <label for="reg-email">邮箱 <span class="optional">选填</span></label>
            <input
              id="reg-email"
              v-model="registerForm.email"
              class="form-input"
              :class="{ invalid: registerErrors.email }"
              type="email"
              placeholder="you@example.com"
              @input="registerErrors.email = ''"
            />
            <p v-if="registerErrors.email" class="field-error">{{ registerErrors.email }}</p>
          </div>
          <div class="form-group">
            <label for="reg-phone">手机号 <span class="optional">选填</span></label>
            <input
              id="reg-phone"
              v-model="registerForm.phone"
              class="form-input"
              :class="{ invalid: registerErrors.phone }"
              type="tel"
              placeholder="11 位手机号"
              @input="registerErrors.phone = ''"
            />
            <p v-if="registerErrors.phone" class="field-error">{{ registerErrors.phone }}</p>
          </div>
        </div>
        <button type="submit" class="btn btn-primary btn-lg btn-block" :disabled="loading">
          {{ loading ? '提交中…' : '注册并登录' }}
        </button>
      </form>

      <div class="switch">
        <template v-if="isRegister">
          已有账号？
          <router-link to="/login">直接登录</router-link>
        </template>
        <template v-else>
          还没有账号？
          <router-link to="/register">立即注册</router-link>
        </template>
        <div class="to-home">
          <router-link to="/"><i class="fas fa-store"></i> 先去首页逛逛</router-link>
        </div>
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
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.code-row {
  display: flex;
  gap: 8px;
}
.code-row .form-input {
  flex: 1;
}
.code-btn {
  flex-shrink: 0;
  white-space: nowrap;
  min-width: 110px;
}
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.optional {
  font-weight: 500;
  color: var(--muted);
  font-size: 12px;
}
.input-wrap {
  position: relative;
}
.form-input.has-toggle {
  padding-right: 44px;
}
.pwd-toggle {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: var(--muted);
  display: grid;
  place-items: center;
}
.pwd-toggle:hover {
  color: var(--accent-deep);
  background: var(--accent-soft);
}
.form-input.invalid {
  border-color: var(--danger);
  box-shadow: 0 0 0 3px rgba(214, 69, 69, 0.12);
}
.link-btn {
  display: block;
  width: 100%;
  margin-top: 12px;
  color: var(--muted);
  font-size: 13px;
  text-align: center;
}
.link-btn:hover {
  color: var(--accent-deep);
}
.field-error {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--danger);
}
.switch {
  text-align: center;
  margin-top: 18px;
  color: var(--muted);
  font-size: 14px;
}
.to-home {
  margin-top: 12px;
}
.to-home a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--accent-deep);
  font-weight: 600;
  font-size: 13px;
  transition: transform 0.2s ease;
}
.to-home a:hover {
  transform: translateY(-1px);
}
.switch a {
  color: var(--accent-deep);
  font-weight: 600;
}
@media (max-width: 520px) {
  .row {
    grid-template-columns: 1fr;
  }
}
</style>
