<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { userApi, productApi, uploadApi } from '@/api'
import { formatPrice, formatTime, firstImage } from '@/api/client'
import { showToast } from '@/composables/ui'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()

const panel = ref('profile')
const profile = ref({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  balance: 0,
  avatar: '',
  role: 'user',
  createdAt: '',
  lastActiveTime: ''
})
const form = ref({ nickname: '', email: '', phone: '' })
const pwdForm = ref({ currentPassword: '', password: '', confirmPassword: '' })
const myProducts = ref([])
const saving = ref(false)
const changingPwd = ref(false)
const uploadingAvatar = ref(false)
const avatarInput = ref(null)

const roleLabel = computed(() => {
  if (profile.value.role === 'admin') return '管理员'
  return '普通用户'
})

async function load() {
  const d = await userApi.info()
  if (d.success) {
    profile.value = {
      username: d.username,
      nickname: d.nickname || '',
      email: d.email || '',
      phone: d.phone || '',
      balance: Number(d.balance || 0),
      avatar: d.avatar || '/user-avatar/default.jpg',
      role: d.role || 'user',
      createdAt: d.createdAt,
      lastActiveTime: d.lastActiveTime
    }
    form.value = {
      nickname: profile.value.nickname,
      email: profile.value.email,
      phone: profile.value.phone
    }
    // 同步导航
    store.setUser({
      ...(store.user || {}),
      username: d.username,
      nickname: d.nickname,
      avatar: d.avatar,
      role: d.role,
      balance: d.balance
    })
  }
  const p = await productApi.mine()
  if (p.success) myProducts.value = p.products || []
}

async function saveProfile() {
  saving.value = true
  const d = await userApi.update({
    nickname: form.value.nickname,
    email: form.value.email,
    phone: form.value.phone
  })
  saving.value = false
  if (d.success) {
    showToast('资料已更新')
    load()
  } else {
    showToast(d.message || '保存失败', 'error')
  }
}

async function changePassword() {
  const f = pwdForm.value
  if (!f.currentPassword || !f.password || !f.confirmPassword) {
    showToast('请填写完整密码信息', 'warning')
    return
  }
  if (f.password.length < 6) {
    showToast('新密码至少 6 位', 'warning')
    return
  }
  if (f.password !== f.confirmPassword) {
    showToast('两次密码不一致', 'warning')
    return
  }
  changingPwd.value = true
  const d = await userApi.update({
    currentPassword: f.currentPassword,
    password: f.password
  })
  changingPwd.value = false
  if (d.success) {
    showToast('密码修改成功')
    pwdForm.value = { currentPassword: '', password: '', confirmPassword: '' }
  } else {
    showToast(d.message || '修改失败', 'error')
  }
}

async function onAvatarChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    showToast('头像不能超过 10MB', 'warning')
    return
  }
  uploadingAvatar.value = true
  const dataUrl = await new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
  // 先走通用图片上传，再更新用户头像（与旧版逻辑一致）
  const up = await uploadApi.images(dataUrl)
  if (!up.success) {
    uploadingAvatar.value = false
    showToast(up.message || '上传失败', 'error')
    return
  }
  const avatar = up.imageUrl || up.imageUrls?.[0]
  const d = await userApi.update({ avatar })
  uploadingAvatar.value = false
  if (d.success) {
    showToast('头像已更新')
    load()
  } else {
    showToast(d.message || '更新失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container">
      <h1 class="h1">个人中心</h1>

      <div class="layout">
        <aside class="side card card-pad">
          <div class="profile-mini">
            <img
              :src="profile.avatar || '/user-avatar/default.jpg'"
              class="avatar"
              alt=""
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
            <div>
              <div class="h3">{{ profile.nickname || profile.username }}</div>
              <div class="muted text-sm">{{ roleLabel }}</div>
            </div>
          </div>
          <nav class="nav">
            <button class="nav-item" :class="{ active: panel === 'profile' }" @click="panel = 'profile'">
              <i class="fas fa-user"></i> 个人信息
            </button>
            <button class="nav-item" :class="{ active: panel === 'products' }" @click="panel = 'products'">
              <i class="fas fa-box"></i> 我的商品
            </button>
            <button class="nav-item" :class="{ active: panel === 'security' }" @click="panel = 'security'">
              <i class="fas fa-shield-halved"></i> 账号安全
            </button>
          </nav>
        </aside>

        <section class="content">
          <div v-if="panel === 'profile'" class="card card-pad">
            <div class="avatar-block">
              <button class="avatar-btn" @click="avatarInput?.click()">
                <img
                  :src="profile.avatar || '/user-avatar/default.jpg'"
                  class="big-avatar"
                  alt=""
                  @error="$event.target.src = '/user-avatar/default.jpg'"
                />
                <span class="cam"><i class="fas fa-camera"></i></span>
              </button>
              <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarChange" />
              <div>
                <h2 class="h2">{{ profile.nickname || profile.username }}</h2>
                <div class="muted text-sm">@{{ profile.username }}</div>
                <div class="balance">余额 ¥{{ formatPrice(profile.balance) }}</div>
              </div>
            </div>

            <div class="row">
              <div class="form-group">
                <label>昵称</label>
                <input v-model="form.nickname" class="form-input" />
              </div>
              <div class="form-group">
                <label>用户名</label>
                <input class="form-input" :value="profile.username" disabled />
              </div>
            </div>
            <div class="row">
              <div class="form-group">
                <label>邮箱</label>
                <input v-model="form.email" type="email" class="form-input" />
              </div>
              <div class="form-group">
                <label>手机号</label>
                <input v-model="form.phone" class="form-input" />
              </div>
            </div>
            <button class="btn btn-primary" :disabled="saving" @click="saveProfile">
              {{ saving ? '保存中…' : '保存修改' }}
            </button>
          </div>

          <div v-else-if="panel === 'products'" class="card card-pad">
            <div class="section-head">
              <h2 class="h2">我的商品</h2>
              <router-link to="/upload" class="btn btn-primary btn-sm">发布新商品</router-link>
            </div>
            <div v-if="!myProducts.length" class="muted">还没有发布商品</div>
            <div v-else class="prod-grid">
              <router-link
                v-for="p in myProducts"
                :key="p.id"
                :to="`/product/${p.id}`"
                class="prod-card"
              >
                <div class="thumb">
                  <img
                    :src="firstImage(p) || '/user-avatar/default.jpg'"
                    alt=""
                    @error="$event.target.src = '/user-avatar/default.jpg'"
                  />
                  <span class="chip" :class="p.status === 'sold' ? 'sold' : 'on'">
                    {{ p.status === 'sold' ? '已售出' : '在售' }}
                  </span>
                </div>
                <div class="prod-body">
                  <div class="name truncate">{{ p.name }}</div>
                  <div>
                    <span class="price">¥{{ formatPrice(p.secondHandPrice) }}</span>
                    <span class="price-origin">¥{{ formatPrice(p.originalPrice) }}</span>
                  </div>
                </div>
              </router-link>
            </div>
          </div>

          <div v-else class="card card-pad">
            <h2 class="h2 mb-3">修改密码</h2>
            <div class="form-group">
              <label>当前密码</label>
              <input v-model="pwdForm.currentPassword" type="password" class="form-input" autocomplete="current-password" />
            </div>
            <div class="form-group">
              <label>新密码</label>
              <input v-model="pwdForm.password" type="password" class="form-input" autocomplete="new-password" />
            </div>
            <div class="form-group">
              <label>确认新密码</label>
              <input v-model="pwdForm.confirmPassword" type="password" class="form-input" autocomplete="new-password" />
            </div>
            <button class="btn btn-primary" :disabled="changingPwd" @click="changePassword">
              {{ changingPwd ? '提交中…' : '修改密码' }}
            </button>
          </div>
        </section>
      </div>
    </div>
  </main>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 18px;
  margin-top: 12px;
}
.profile-mini {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 18px;
}
.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  object-fit: cover;
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 12px;
  text-align: left;
  color: var(--ink-soft);
  font-weight: 550;
}
.nav-item:hover {
  background: var(--accent-soft);
}
.nav-item.active {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
}
.avatar-block {
  display: flex;
  gap: 18px;
  align-items: center;
  margin-bottom: 22px;
}
.avatar-btn {
  position: relative;
}
.big-avatar {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid var(--border);
}
.cam {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
}
.balance {
  margin-top: 8px;
  color: var(--accent-deep);
  font-weight: 700;
}
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.prod-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.prod-card {
  border: 1px solid var(--border);
  border-radius: 14px;
  overflow: hidden;
}
.thumb {
  position: relative;
  aspect-ratio: 1;
  background: var(--bg-soft);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.chip {
  position: absolute;
  top: 8px;
  left: 8px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  color: #fff;
}
.chip.on {
  background: var(--success);
}
.chip.sold {
  background: var(--danger);
}
.prod-body {
  padding: 10px;
}
.name {
  font-weight: 600;
  margin-bottom: 6px;
}
@media (max-width: 860px) {
  .layout {
    grid-template-columns: 1fr;
  }
  .prod-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

