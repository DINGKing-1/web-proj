<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { userApi, productApi, uploadApi } from '@/api'
import { formatPrice, formatTime, firstImage, CATEGORIES } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
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
const CONDITIONS = ['全新', '几乎全新', '轻微使用', '明显使用', '重度使用']
const addresses = ref([])
const addrEditing = ref(null) // null | 'new' | address object
const addrForm = ref({
  receiverName: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false
})
const savingAddr = ref(false)
const editing = ref(null)
const editForm = ref({
  name: '',
  category: '',
  secondHandPrice: '',
  originalPrice: '',
  condition: '',
  quantity: 1,
  description: ''
})
const savingEdit = ref(false)

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
  await loadAddresses()
}

async function loadAddresses() {
  const d = await userApi.addresses()
  if (d.success) addresses.value = d.addresses || []
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

function openEdit(p) {
  editing.value = p
  editForm.value = {
    name: p.name || '',
    category: p.category || '',
    secondHandPrice: p.secondHandPrice ?? '',
    originalPrice: p.originalPrice ?? '',
    condition: p.itemCondition || p.condition || '',
    quantity: p.quantity ?? 1,
    description: p.description || ''
  }
}

async function saveEdit() {
  const p = editing.value
  const f = editForm.value
  if (!p) return
  if (!f.name.trim() || !f.category) {
    showToast('请填写名称并选择分类', 'warning')
    return
  }
  if (!(Number(f.secondHandPrice) > 0)) {
    showToast('售价必须大于 0', 'warning')
    return
  }
  if (f.originalPrice !== '' && Number(f.secondHandPrice) > Number(f.originalPrice)) {
    showToast('二手价不能高于原价', 'warning')
    return
  }
  savingEdit.value = true
  const payload = {
    name: f.name.trim(),
    category: f.category,
    secondHandPrice: Number(f.secondHandPrice),
    condition: f.condition || undefined,
    quantity: Number(f.quantity) || 1,
    description: f.description
  }
  if (f.originalPrice !== '' && f.originalPrice !== null) {
    payload.originalPrice = Number(f.originalPrice)
  }
  const d = await productApi.update(p.id, payload)
  savingEdit.value = false
  if (d.success) {
    showToast('商品已更新')
    editing.value = null
    load()
  } else {
    showToast(d.message || '更新失败', 'error')
  }
}

async function toggleShelf(p) {
  const next = p.status === 'active' ? 'inactive' : 'active'
  const label = next === 'active' ? '重新上架' : '下架'
  const ok = await confirmDialog({
    title: label,
    message: `确定${label}「${p.name}」吗？`,
    type: 'warning'
  })
  if (!ok) return
  const d = await productApi.update(p.id, { status: next })
  if (d.success) {
    showToast(next === 'active' ? '已重新上架' : '已下架')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function removeProduct(p) {
  const ok = await confirmDialog({
    title: '删除商品',
    message: `确定删除「${p.name}」吗？删除后前台将不可见。`,
    type: 'warning',
    okText: '删除'
  })
  if (!ok) return
  const d = await productApi.remove(p.id)
  if (d.success) {
    showToast('商品已删除')
    load()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

function statusMeta(status) {
  if (status === 'sold') return { label: '已售出', cls: 'sold' }
  if (status === 'active') return { label: '在售', cls: 'on' }
  return { label: '已下架', cls: 'off' }
}

function openAddrCreate() {
  addrEditing.value = 'new'
  addrForm.value = {
    receiverName: '',
    phone: '',
    province: '',
    city: '',
    district: '',
    detail: '',
    isDefault: addresses.value.length === 0
  }
}

function openAddrEdit(a) {
  addrEditing.value = a
  addrForm.value = {
    receiverName: a.receiverName || '',
    phone: a.phone || '',
    province: a.province || '',
    city: a.city || '',
    district: a.district || '',
    detail: a.detail || '',
    isDefault: !!a.isDefault
  }
}

async function saveAddress() {
  const f = addrForm.value
  if (!f.receiverName.trim() || !f.phone.trim() || !f.detail.trim()) {
    showToast('请填写收货人、手机号和详细地址', 'warning')
    return
  }
  savingAddr.value = true
  const payload = {
    receiverName: f.receiverName.trim(),
    phone: f.phone.trim(),
    province: f.province.trim(),
    city: f.city.trim(),
    district: f.district.trim(),
    detail: f.detail.trim(),
    isDefault: !!f.isDefault
  }
  const isNew = addrEditing.value === 'new'
  const d = isNew
    ? await userApi.addAddress(payload)
    : await userApi.updateAddress(addrEditing.value.id, payload)
  savingAddr.value = false
  if (d.success) {
    showToast(isNew ? '地址已添加' : '地址已更新')
    addrEditing.value = null
    await loadAddresses()
  } else {
    showToast(d.message || '保存失败', 'error')
  }
}

async function setDefaultAddress(a) {
  const d = await userApi.updateAddress(a.id, { isDefault: true })
  if (d.success) {
    showToast('已设为默认地址')
    await loadAddresses()
  } else {
    showToast(d.message || '设置失败', 'error')
  }
}

async function removeAddress(a) {
  const ok = await confirmDialog({
    title: '删除地址',
    message: `确定删除「${a.receiverName}」的收货地址吗？`,
    type: 'warning',
    okText: '删除'
  })
  if (!ok) return
  const d = await userApi.deleteAddress(a.id)
  if (d.success) {
    showToast('地址已删除')
    await loadAddresses()
  } else {
    showToast(d.message || '删除失败', 'error')
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
            <button class="nav-item" :class="{ active: panel === 'addresses' }" @click="panel = 'addresses'">
              <i class="fas fa-location-dot"></i> 收货地址
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
              <div v-for="p in myProducts" :key="p.id" class="prod-card">
                <router-link :to="`/product/${p.id}`" class="prod-link">
                  <div class="thumb">
                    <img
                      :src="firstImage(p) || '/user-avatar/default.jpg'"
                      alt=""
                      @error="$event.target.src = '/user-avatar/default.jpg'"
                    />
                    <span class="chip" :class="statusMeta(p.status).cls">
                      {{ statusMeta(p.status).label }}
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
                <div class="prod-actions">
                  <button class="btn btn-outline btn-sm" @click="openEdit(p)">编辑</button>
                  <button
                    v-if="p.status !== 'sold'"
                    class="btn btn-outline btn-sm"
                    @click="toggleShelf(p)"
                  >
                    {{ p.status === 'active' ? '下架' : '上架' }}
                  </button>
                  <button class="btn btn-outline btn-sm danger" @click="removeProduct(p)">删除</button>
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="panel === 'addresses'" class="card card-pad">
            <div class="section-head">
              <h2 class="h2">收货地址</h2>
              <button class="btn btn-primary btn-sm" @click="openAddrCreate">新增地址</button>
            </div>
            <div v-if="!addresses.length" class="muted">还没有收货地址</div>
            <div v-else class="addr-list">
              <div v-for="a in addresses" :key="a.id" class="addr-item">
                <div class="addr-main">
                  <div class="addr-line">
                    <strong>{{ a.receiverName }}</strong>
                    <span class="muted">{{ a.phone }}</span>
                    <span v-if="a.isDefault" class="chip on">默认</span>
                  </div>
                  <div class="muted text-sm">
                    {{ [a.province, a.city, a.district].filter(Boolean).join(' ') }} {{ a.detail }}
                  </div>
                </div>
                <div class="addr-actions">
                  <button v-if="!a.isDefault" class="btn btn-outline btn-sm" @click="setDefaultAddress(a)">设为默认</button>
                  <button class="btn btn-outline btn-sm" @click="openAddrEdit(a)">编辑</button>
                  <button class="btn btn-outline btn-sm danger" @click="removeAddress(a)">删除</button>
                </div>
              </div>
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
      <div v-if="addrEditing" class="modal-backdrop" @click.self="addrEditing = null">
        <div class="modal card">
          <h2 class="h2 mb-3">{{ addrEditing === 'new' ? '新增地址' : '编辑地址' }}</h2>
          <div class="row">
            <div class="form-group">
              <label>收货人</label>
              <input v-model="addrForm.receiverName" class="form-input" />
            </div>
            <div class="form-group">
              <label>手机号</label>
              <input v-model="addrForm.phone" class="form-input" />
            </div>
          </div>
          <div class="row">
            <div class="form-group">
              <label>省</label>
              <input v-model="addrForm.province" class="form-input" />
            </div>
            <div class="form-group">
              <label>市</label>
              <input v-model="addrForm.city" class="form-input" />
            </div>
          </div>
          <div class="row">
            <div class="form-group">
              <label>区/县</label>
              <input v-model="addrForm.district" class="form-input" />
            </div>
            <div class="form-group">
              <label>详细地址</label>
              <input v-model="addrForm.detail" class="form-input" />
            </div>
          </div>
          <label class="check-line">
            <input v-model="addrForm.isDefault" type="checkbox" />
            设为默认地址
          </label>
          <div class="modal-actions">
            <button class="btn btn-outline" @click="addrEditing = null">取消</button>
            <button class="btn btn-primary" :disabled="savingAddr" @click="saveAddress">
              {{ savingAddr ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="editing" class="modal-backdrop" @click.self="editing = null">
        <div class="modal card">
          <h2 class="h2 mb-3">编辑商品</h2>
          <div class="form-group">
            <label>商品名称</label>
            <input v-model="editForm.name" class="form-input" />
          </div>
          <div class="row">
            <div class="form-group">
              <label>分类</label>
              <select v-model="editForm.category" class="form-select">
                <option v-for="c in CATEGORIES.filter((x) => x !== '全部')" :key="c" :value="c">{{ c }}</option>
              </select>
            </div>
            <div class="form-group">
              <label>成色</label>
              <select v-model="editForm.condition" class="form-select">
                <option v-for="c in CONDITIONS" :key="c" :value="c">{{ c }}</option>
              </select>
            </div>
          </div>
          <div class="row">
            <div class="form-group">
              <label>售价（元）</label>
              <input v-model="editForm.secondHandPrice" type="number" min="0" step="0.01" class="form-input" />
            </div>
            <div class="form-group">
              <label>原价（元）</label>
              <input v-model="editForm.originalPrice" type="number" min="0" step="0.01" class="form-input" />
            </div>
          </div>
          <div class="form-group">
            <label>库存</label>
            <input v-model="editForm.quantity" type="number" min="0" class="form-input" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="editForm.description" class="form-input" rows="3"></textarea>
          </div>
          <div class="modal-actions">
            <button class="btn btn-outline" @click="editing = null">取消</button>
            <button class="btn btn-primary" :disabled="savingEdit" @click="saveEdit">
              {{ savingEdit ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
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

.addr-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.addr-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
}
.addr-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.addr-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.check-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0 4px;
  font-size: 14px;
}
.prod-link {
  display: block;
  color: inherit;
  text-decoration: none;
}
.prod-actions {
  display: flex;
  gap: 6px;
  padding: 0 10px 10px;
  flex-wrap: wrap;
}
.prod-actions .btn-sm {
  padding: 5px 10px;
  font-size: 12px;
}
.prod-actions .danger {
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 35%, white);
}
.chip.off {
  background: var(--ink-soft);
}
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.45);
  display: grid;
  place-items: center;
  z-index: var(--z-modal-backdrop, 400);
  padding: 20px;
}
.modal {
  width: min(100%, 520px);
  max-height: 90vh;
  overflow: auto;
  padding: 24px;
  z-index: var(--z-modal, 500);
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
}
</style>

