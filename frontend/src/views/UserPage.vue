<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { userApi, productApi, offerApi, couponApi } from '@/api'
import { formatPrice, formatTime, imgOf, onImgError, getBrowseHistory, clearBrowseHistory } from '@/api/client'
import { useCategories } from '@/composables/categories'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import AppModal from '@/components/AppModal.vue'
import AppSelect from '@/components/AppSelect.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'

const router = useRouter()
const store = useUserStore()

const panel = ref('profile')
const browseHistory = ref(getBrowseHistory())
const profile = ref({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  balance: 0,
  pendingAmount: 0,
  avatar: '',
  role: 'user',
  createdAt: '',
  lastActiveTime: ''
})
const form = ref({ nickname: '', email: '', phone: '' })
const pwdForm = ref({ currentPassword: '', password: '', confirmPassword: '', code: '' })
const myProducts = ref([])
const saving = ref(false)
const changingPwd = ref(false)
const sendingPwdCode = ref(false)
const pwdCodeCooldown = ref(0)
let pwdCooldownTimer = null
const uploadingAvatar = ref(false)
const avatarInput = ref(null)
const CONDITIONS = ['全新', '几乎全新', '轻微使用', '明显使用', '重度使用']
const { categories } = useCategories()
const categoryOptions = computed(() =>
  categories.value.filter((x) => x !== '全部').map((c) => ({ label: c, value: c }))
)
const conditionOptions = CONDITIONS.map((c) => ({ label: c, value: c }))
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
/** 议价 */
const offerTab = ref('received')
const receivedOffers = ref([])
const myOffers = ref([])
const offerActing = ref(false)
const offerLoading = ref(false)
const editing = ref(null)
/** 提现 */
const showWithdraw = ref(false)
const withdrawing = ref(false)
const withdrawForm = ref({ amount: '', account: '' })
const withdrawList = ref([])
const loadingWithdraw = ref(false)
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
const showRecharge = ref(false)
const showWallet = ref(false)
const walletBills = ref([])
const showDelete = ref(false)
const deletePassword = ref('')
const rechargeAmount = ref('')
const recharging = ref(false)

const roleLabel = computed(() => {
  if (profile.value.role === 'admin') return '管理员'
  return '普通用户'
})

function refreshBrowseHistory() {
  browseHistory.value = getBrowseHistory()
}

function clearMyBrowseHistory() {
  clearBrowseHistory()
  browseHistory.value = []
  showToast('浏览历史已清空')
}

function openPanel(name) {
  panel.value = name
  if (name === 'history') refreshBrowseHistory()
  if (name === 'offers') loadOffers()
  if (name === 'coupons') loadCoupons()
}

/** 优惠券 */
const myCoupons = ref([])
const claimableCoupons = ref([])
const couponLoading = ref(false)
const couponActingId = ref(null)

async function loadCoupons() {
  couponLoading.value = true
  const [mine, claimable] = await Promise.all([couponApi.mine(), couponApi.claimable()])
  couponLoading.value = false
  if (mine.success) myCoupons.value = mine.coupons || []
  if (claimable.success) claimableCoupons.value = claimable.coupons || []
}

async function claimCoupon(c) {
  couponActingId.value = c.id
  const d = await couponApi.claim(c.id)
  couponActingId.value = null
  if (d.success) {
    showToast('领取成功，支付时可用')
    loadCoupons()
  } else {
    showToast(d.message || '领取失败', 'error')
  }
}

const COUPON_STATUS = {
  unused: { label: '未使用', cls: 'on' },
  used: { label: '已使用', cls: 'off' },
  expired: { label: '已过期', cls: 'sold' }
}

async function loadOffers() {
  offerLoading.value = true
  const [recv, mine] = await Promise.all([offerApi.received(), offerApi.mine()])
  offerLoading.value = false
  if (recv.success) receivedOffers.value = recv.offers || []
  if (mine.success) myOffers.value = mine.offers || []
}

async function acceptOffer(o) {
  const ok = await confirmDialog({
    title: '接受出价',
    message: `接受买家出价 ¥${o.offerPrice}？将按此价格生成待付款订单。`,
    okText: '接受'
  })
  if (!ok) return
  offerActing.value = true
  const d = await offerApi.accept(o.id)
  offerActing.value = false
  showToast(d.message || (d.success ? '已接受' : '操作失败'), d.success ? 'success' : 'error')
  if (d.success) loadOffers()
}

async function rejectOffer(o) {
  const ok = await confirmDialog({
    title: '拒绝出价',
    message: `确定拒绝 ¥${o.offerPrice} 的出价吗？`,
    okText: '拒绝',
    type: 'warning'
  })
  if (!ok) return
  offerActing.value = true
  const d = await offerApi.reject(o.id)
  offerActing.value = false
  showToast(d.message || (d.success ? '已拒绝' : '操作失败'), d.success ? 'success' : 'error')
  if (d.success) loadOffers()
}

async function cancelOffer(o) {
  const ok = await confirmDialog({
    title: '撤销出价',
    message: `确定撤销对「${o.productName || '该商品'}」的出价吗？撤销后不可恢复。`,
    okText: '撤销出价',
    type: 'warning'
  })
  if (!ok) return
  offerActing.value = true
  const d = await offerApi.cancel(o.id)
  offerActing.value = false
  showToast(d.message || (d.success ? '已撤销' : '操作失败'), d.success ? 'success' : 'error')
  if (d.success) loadOffers()
}

function offerStatusText(s) {
  return { pending: '待回应', accepted: '已接受', rejected: '已拒绝', cancelled: '已撤销' }[s] || s
}

async function load() {
  const d = await userApi.info()
  if (d.success) {
    profile.value = {
      username: d.username,
      nickname: d.nickname || '',
      email: d.email || '',
      phone: d.phone || '',
      balance: Number(d.balance || 0),
      pendingAmount: Number(d.pendingAmount || 0),
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

async function openWallet() {
  showWallet.value = true
  walletBills.value = []
  const d = await userApi.wallet()
  if (d.success) walletBills.value = d.bills || []
}

async function doDeleteAccount() {
  if (!deletePassword.value) {
    showToast('请输入密码', 'warning')
    return
  }
  const ok = await confirmDialog({
    title: '注销账号',
    message: '注销后账号将进入回收站，可在管理员处申请恢复；确定继续吗？',
    okText: '确认注销',
    type: 'warning'
  })
  if (!ok) return
  const d = await userApi.deleteAccount(deletePassword.value)
  if (d.success) {
    showToast('账号已注销')
    showDelete.value = false
    store.logout()
  } else {
    showToast(d.message || '注销失败', 'error')
  }
}

async function doRecharge() {
  const amount = Number(rechargeAmount.value)
  if (!amount || amount <= 0) {
    showToast('请输入有效充值金额', 'warning')
    return
  }
  recharging.value = true
  const d = await userApi.recharge(amount)
  recharging.value = false
  if (d.success) {
    showToast('充值成功')
    showRecharge.value = false
    rechargeAmount.value = ''
    profile.value.balance = d.balance
    store.setUser({ ...(store.user || {}), balance: d.balance })
    load()
  } else {
    showToast(d.message || '充值失败', 'error')
  }
}

async function openWithdraw() {
  showWithdraw.value = true
  withdrawForm.value = { amount: '', account: '' }
  loadingWithdraw.value = true
  const d = await userApi.withdrawList()
  loadingWithdraw.value = false
  if (d.success) withdrawList.value = d.withdraws || []
}

function withdrawStatusMeta(s) {
  return (
    {
      pending: { label: '审核中', tone: 'warning' },
      approved: { label: '已打款', tone: 'success' },
      rejected: { label: '已驳回', tone: 'muted' }
    }[s] || { label: s, tone: 'muted' }
  )
}

async function doWithdraw() {
  const amount = Number(withdrawForm.value.amount)
  if (!amount || amount < 10) {
    showToast('提现金额不能低于 ¥10', 'warning')
    return
  }
  if (!withdrawForm.value.account.trim()) {
    showToast('请填写收款账号', 'warning')
    return
  }
  withdrawing.value = true
  const d = await userApi.withdraw({
    amount,
    account: withdrawForm.value.account.trim()
  })
  withdrawing.value = false
  if (d.success) {
    showToast(d.message || '提现申请已提交')
    showWithdraw.value = false
    load()
  } else {
    showToast(d.message || '提现失败', 'error')
  }
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
  if (!profile.value.email) {
    showToast('请先绑定邮箱后再修改密码', 'warning')
    openPanel('profile')
    return
  }
  if (!f.currentPassword || !f.password || !f.confirmPassword || !f.code.trim()) {
    showToast('请填写完整密码信息与邮箱验证码', 'warning')
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
  const d = await userApi.changePassword({
    currentPassword: f.currentPassword,
    password: f.password,
    code: f.code.trim()
  })
  changingPwd.value = false
  if (d.success) {
    showToast(d.message || '密码修改成功')
    pwdForm.value = { currentPassword: '', password: '', confirmPassword: '', code: '' }
  } else {
    showToast(d.message || '修改失败', 'error')
  }
}

/** 向绑定邮箱发送改密验证码 */
async function sendPwdCode() {
  if (!profile.value.email) {
    showToast('请先绑定邮箱后再修改密码', 'warning')
    openPanel('profile')
    return
  }
  if (pwdCodeCooldown.value > 0 || sendingPwdCode.value) return
  sendingPwdCode.value = true
  const d = await userApi.sendChangePasswordCode()
  sendingPwdCode.value = false
  if (d.success) {
    showToast(d.message || '验证码已发送')
    pwdCodeCooldown.value = 60
    pwdCooldownTimer && clearInterval(pwdCooldownTimer)
    pwdCooldownTimer = setInterval(() => {
      pwdCodeCooldown.value -= 1
      if (pwdCodeCooldown.value <= 0) {
        clearInterval(pwdCooldownTimer)
        pwdCooldownTimer = null
      }
    }, 1000)
  } else {
    showToast(d.message || '发送失败', 'error')
    if (String(d.message || '').includes('绑定邮箱')) openPanel('profile')
  }
}

onUnmounted(() => {
  pwdCooldownTimer && clearInterval(pwdCooldownTimer)
})

async function onAvatarChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || uploadingAvatar.value) return
  if (file.size > 10 * 1024 * 1024) {
    showToast('头像不能超过 10MB', 'warning')
    return
  }
  uploadingAvatar.value = true
  try {
    // 走专用头像接口（存入 user-avatar/，按魔数校验），不要落到商品图目录
    const formData = new FormData()
    formData.append('file', file)
    const d = await userApi.uploadAvatar(formData)
    if (d.success) {
      showToast('头像已更新')
      load()
    } else {
      showToast(d.message || '上传失败', 'error')
    }
  } finally {
    uploadingAvatar.value = false // 无论成败都复位，避免卡死
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
  if (p.status === 'pending') {
    showToast('商品正在审核中，通过后会自动上架', 'info')
    return
  }
  if (p.status === 'rejected') {
    showToast('商品未通过审核，修改内容后会重新提交', 'warning')
    return
  }
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
  if (status === 'pending') return { label: '审核中', cls: 'pending' }
  if (status === 'rejected') return { label: '已驳回', cls: 'rejected' }
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
  if (!/^1\d{10}$/.test(f.phone.trim())) {
    showToast('手机号格式不正确', 'warning')
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
            <button class="nav-item" :class="{ active: panel === 'profile' }" @click="openPanel('profile')">
              <i class="fas fa-user"></i> 个人信息
            </button>
            <button class="nav-item" :class="{ active: panel === 'products' }" @click="openPanel('products')">
              <i class="fas fa-box"></i> 我的商品
            </button>
            <button class="nav-item" :class="{ active: panel === 'offers' }" @click="openPanel('offers')">
              <i class="fas fa-hand-holding-usd"></i> 议价管理
            </button>
            <button class="nav-item" :class="{ active: panel === 'coupons' }" @click="openPanel('coupons')">
              <i class="fas fa-ticket"></i> 优惠券
            </button>
            <button class="nav-item" :class="{ active: panel === 'addresses' }" @click="openPanel('addresses')">
              <i class="fas fa-location-dot"></i> 收货地址
            </button>
            <button class="nav-item" :class="{ active: panel === 'history' }" @click="openPanel('history')">
              <i class="fas fa-clock-rotate-left"></i> 浏览历史
            </button>
            <button class="nav-item" :class="{ active: panel === 'security' }" @click="openPanel('security')">
              <i class="fas fa-shield-halved"></i> 账号安全
            </button>
            <router-link to="/my-orders" class="nav-item">
              <i class="fas fa-receipt"></i> 我的订单
            </router-link>
            <router-link to="/seller-orders" class="nav-item">
              <i class="fas fa-store"></i> 卖家发货
            </router-link>
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
                <div class="balance-row">
                  <div class="balance">余额 ¥{{ formatPrice(profile.balance) }}</div>
                  <div v-if="profile.pendingAmount > 0" class="pending">
                    待结算 ¥{{ formatPrice(profile.pendingAmount) }}
                  </div>
                  <button type="button" class="btn btn-primary btn-sm" @click="showRecharge = true">
                    <i class="fas fa-wallet"></i> 充值
                  </button>
                  <button type="button" class="btn btn-outline btn-sm" @click="openWithdraw">
                    提现
                  </button>
                  <button type="button" class="btn btn-outline btn-sm" @click="openWallet">
                    账单
                  </button>
                </div>
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
                <label>邮箱 <span class="muted text-xs">（改密需绑定）</span></label>
                <input v-model="form.email" type="email" class="form-input" placeholder="用于修改密码时接收验证码" />
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
                      :src="imgOf(p)"
                      alt=""
                      @error="onImgError"
                    />
                    <span class="chip" :class="statusMeta(p.status).cls">
                      {{ statusMeta(p.status).label }}
                    </span>
                  </div>
                  <div class="prod-body">
                    <div class="name truncate">{{ p.name }}</div>
                    <div v-if="p.status === 'rejected' && p.reviewNote" class="reject-note">
                      {{ p.reviewNote }}
                    </div>
                    <div>
                      <span class="price">¥{{ formatPrice(p.secondHandPrice) }}</span>
                      <span class="price-origin">¥{{ formatPrice(p.originalPrice) }}</span>
                    </div>
                  </div>
                </router-link>
                <div class="prod-actions">
                  <button class="btn btn-outline btn-sm" @click="openEdit(p)">
                    {{ p.status === 'rejected' ? '修改重提' : '编辑' }}
                  </button>
                  <button
                    v-if="p.status === 'active' || p.status === 'inactive'"
                    class="btn btn-outline btn-sm"
                    @click="toggleShelf(p)"
                  >
                    {{ p.status === 'active' ? '下架' : '上架' }}
                  </button>
                  <button class="btn btn-danger btn-sm" @click="removeProduct(p)">删除</button>
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="panel === 'offers'" class="card card-pad">
            <div class="section-head">
              <h2 class="h2">议价管理</h2>
              <div class="tabs">
                <button class="chip" :class="{ on: offerTab === 'received' }" @click="offerTab = 'received'">收到的出价</button>
                <button class="chip" :class="{ on: offerTab === 'mine' }" @click="offerTab = 'mine'">我的出价</button>
              </div>
            </div>
            <LoadingBlock v-if="offerLoading" />
            <template v-else-if="offerTab === 'received'">
              <div v-if="!receivedOffers.length" class="muted">暂无收到的出价</div>
              <div v-for="o in receivedOffers" :key="o.id" class="offer-row">
                <div class="offer-info">
                  <strong>{{ o.productName }}</strong>
                  <div class="muted text-sm">买家 {{ o.buyerUsername }} 出价 <b class="price">¥{{ formatPrice(o.offerPrice) }}</b></div>
                  <div v-if="o.message" class="muted text-sm">「{{ o.message }}」</div>
                  <div class="muted text-xs">{{ formatTime(o.createdAt) }} · {{ offerStatusText(o.status) }}</div>
                </div>
                <div class="offer-actions" v-if="o.status === 'pending'">
                  <button class="btn btn-primary btn-sm" :disabled="offerActing" @click="acceptOffer(o)">接受</button>
                  <button class="btn btn-outline btn-sm" :disabled="offerActing" @click="rejectOffer(o)">拒绝</button>
                </div>
                <router-link v-else-if="o.orderId" :to="`/order/${o.orderId}`" class="btn btn-outline btn-sm">查看订单</router-link>
              </div>
            </template>
            <template v-else>
              <div v-if="!myOffers.length" class="muted">暂无出价记录</div>
              <div v-for="o in myOffers" :key="o.id" class="offer-row">
                <div class="offer-info">
                  <strong>{{ o.productName }}</strong>
                  <div class="muted text-sm">出价 <b class="price">¥{{ formatPrice(o.offerPrice) }}</b></div>
                  <div class="muted text-xs">{{ formatTime(o.createdAt) }} · {{ offerStatusText(o.status) }}</div>
                </div>
                <div class="offer-actions">
                  <button v-if="o.status === 'pending'" class="btn btn-outline btn-sm" :disabled="offerActing" @click="cancelOffer(o)">撤销</button>
                  <router-link v-if="o.orderId" :to="`/order/${o.orderId}`" class="btn btn-primary btn-sm">去付款</router-link>
                </div>
              </div>
            </template>
          </div>
          <div v-else-if="panel === 'coupons'" class="card card-pad">
            <div class="section-head">
              <h2 class="h2">优惠券</h2>
              <span class="muted text-sm">支付订单时选择可用券自动抵扣</span>
            </div>

            <LoadingBlock v-if="couponLoading" />

            <template v-else>
              <h3 class="h3 sub">可领取</h3>
              <div v-if="claimableCoupons.length" class="coupon-grid">
                <div v-for="c in claimableCoupons" :key="c.id" class="coupon-card">
                  <div class="coupon-face">
                    <span class="coupon-num">¥{{ formatPrice(c.discountAmount) }}</span>
                    <span class="coupon-cond">满{{ formatPrice(c.minAmount) }}可用</span>
                  </div>
                  <div class="coupon-info">
                    <div class="name truncate">{{ c.name }}</div>
                    <div class="muted text-xs">有效期至 {{ (c.endAt || '').slice(0, 10) }}</div>
                    <button
                      class="btn btn-primary btn-sm"
                      :disabled="couponActingId === c.id"
                      @click="claimCoupon(c)"
                    >
                      {{ couponActingId === c.id ? '领取中…' : '立即领取' }}
                    </button>
                  </div>
                </div>
              </div>
              <p v-else class="muted text-sm">暂时没有可领取的优惠券</p>

              <h3 class="h3 sub">我的优惠券</h3>
              <div v-if="myCoupons.length" class="coupon-grid">
                <div
                  v-for="c in myCoupons"
                  :key="c.userCouponId"
                  class="coupon-card"
                  :class="{ dim: c.status !== 'unused' }"
                >
                  <div class="coupon-face">
                    <span class="coupon-num">¥{{ formatPrice(c.discountAmount) }}</span>
                    <span class="coupon-cond">满{{ formatPrice(c.minAmount) }}可用</span>
                  </div>
                  <div class="coupon-info">
                    <div class="name truncate">{{ c.name }}</div>
                    <div class="muted text-xs">有效期至 {{ (c.endAt || '').slice(0, 10) }}</div>
                    <span class="chip" :class="COUPON_STATUS[c.status]?.cls || 'off'">
                      {{ COUPON_STATUS[c.status]?.label || c.status }}
                    </span>
                  </div>
                </div>
              </div>
              <p v-else class="muted text-sm">还没有领取优惠券</p>
            </template>
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
                  <button class="btn btn-danger btn-sm" @click="removeAddress(a)">删除</button>
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="panel === 'history'" class="card card-pad">
            <div class="section-head">
              <h2 class="h2">浏览历史</h2>
              <button v-if="browseHistory.length" class="btn btn-outline btn-sm" @click="clearMyBrowseHistory">
                清空
              </button>
            </div>
            <div v-if="!browseHistory.length" class="muted">还没有浏览记录，去逛逛吧</div>
            <div v-else class="prod-grid">
              <div v-for="h in browseHistory" :key="h.id" class="prod-card">
                <router-link :to="`/product/${h.id}`" class="prod-link">
                  <div class="thumb">
                    <img :src="h.image" alt="" @error="onImgError" />
                  </div>
                  <div class="prod-body">
                    <div class="name truncate">{{ h.name }}</div>
                    <div class="price">¥{{ formatPrice(h.price) }}</div>
                    <div class="muted text-xs">{{ formatTime(h.at) }}</div>
                  </div>
                </router-link>
              </div>
            </div>
          </div>

          <div v-else class="card card-pad">
            <h2 class="h2 mb-3">修改密码</h2>
            <p v-if="!profile.email" class="muted text-sm mb-3">
              修改密码需先绑定邮箱，并通过邮箱验证码验证。请先到「基本资料」填写邮箱并保存。
            </p>
            <p v-else class="muted text-sm mb-3">
              为保障账号安全，修改密码需验证当前密码，并输入发送至绑定邮箱
              <strong>{{ profile.email }}</strong> 的验证码。
            </p>
            <button
              v-if="!profile.email"
              class="btn btn-primary mb-3"
              type="button"
              @click="openPanel('profile')"
            >
              去绑定邮箱
            </button>
            <template v-else>
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
              <div class="form-group">
                <label>邮箱验证码</label>
                <div class="code-row">
                  <input
                    v-model="pwdForm.code"
                    class="form-input"
                    inputmode="numeric"
                    maxlength="6"
                    placeholder="6 位验证码"
                  />
                  <button
                    type="button"
                    class="btn btn-outline code-btn"
                    :disabled="sendingPwdCode || pwdCodeCooldown > 0"
                    @click="sendPwdCode"
                  >
                    {{ pwdCodeCooldown > 0 ? `${pwdCodeCooldown}s` : sendingPwdCode ? '发送中…' : '获取验证码' }}
                  </button>
                </div>
              </div>
              <button class="btn btn-primary" :disabled="changingPwd" @click="changePassword">
                {{ changingPwd ? '提交中…' : '修改密码' }}
              </button>
            </template>
            <hr class="divider" />
            <h3 class="h3 mb-2">注销账号</h3>
            <p class="muted text-sm mb-3">注销后账号进入回收站，可联系管理员恢复。待付款/未发货订单会关单退款并释放库存；已发货、已收货或售后中订单需先完成售后或确认收货，否则无法注销。</p>
            <button class="btn btn-danger" @click="showDelete = true">
              <i class="fas fa-user-slash"></i> 注销账号
            </button>
          </div>
        </section>
      </div>
      <AppModal
        :show="showWallet"
        title="余额账单"
        width="520px"
        @close="showWallet = false"
      >
        <div v-if="!walletBills.length" class="muted">暂无账单记录</div>
        <ul v-else class="bill-list">
          <li v-for="(b, i) in walletBills" :key="i" class="bill-item">
            <div>
              <strong>{{ b.title }}</strong>
              <div class="muted text-xs">{{ b.createdAt }} · {{ b.status }}</div>
            </div>
            <span class="bill-amt" :class="{ plus: String(b.amount).startsWith('+') }">{{ b.amount }}</span>
          </li>
        </ul>
      </AppModal>

      <AppModal
        :show="showDelete"
        title="注销账号"
        width="420px"
        @close="showDelete = false"
      >
        <p class="muted text-sm">请输入登录密码确认注销。注销后账号进入回收站，可申请恢复。</p>
        <div class="form-group mt-3">
          <label>登录密码</label>
          <input v-model="deletePassword" class="form-input" type="password" />
        </div>
        <template #actions>
          <button class="btn btn-danger" @click="doDeleteAccount">确认注销</button>
        </template>
      </AppModal>

      <AppModal
        :show="showRecharge"
        title="余额充值"
        width="420px"
        @close="showRecharge = false"
      >
        <div class="form-group">
          <label>充值金额（元）</label>
          <input
            v-model="rechargeAmount"
            class="form-input"
            type="number"
            min="1"
            max="50000"
            placeholder="请输入金额"
          />
        </div>
        <div class="recharge-presets">
          <button
            v-for="a in [50, 100, 200, 500, 1000]"
            :key="a"
            type="button"
            class="btn btn-outline btn-sm"
            @click="rechargeAmount = String(a)"
          >
            ¥{{ a }}
          </button>
        </div>
        <p class="muted text-sm mt-2">模拟支付，直接计入账户余额</p>
        <template #actions>
          <button class="btn btn-primary" :disabled="recharging" @click="doRecharge">
            {{ recharging ? '充值中…' : '确认充值' }}
          </button>
        </template>
      </AppModal>

      <AppModal
        :show="showWithdraw"
        title="余额提现"
        width="520px"
        @close="showWithdraw = false"
      >
        <div class="form-group">
          <label>提现金额（元）</label>
          <input
            v-model="withdrawForm.amount"
            class="form-input"
            type="number"
            min="10"
            step="0.01"
            placeholder="最低 ¥10"
          />
          <p class="muted text-sm mt-1">可提余额 ¥{{ formatPrice(profile.balance) }}</p>
        </div>
        <div class="form-group">
          <label>收款账号（支付宝 / 银行卡）</label>
          <input v-model="withdrawForm.account" class="form-input" placeholder="如：支付宝 138****0000" />
        </div>
        <p class="muted text-sm">申请后余额将冻结，管理员审核通过后线下打款；驳回则退回余额。</p>
        <div class="mt-3">
          <h4 class="h3 mb-2">提现记录</h4>
          <LoadingBlock v-if="loadingWithdraw" />
          <div v-else-if="!withdrawList.length" class="muted text-sm">暂无提现记录</div>
          <ul v-else class="bill-list">
            <li v-for="w in withdrawList" :key="w.id" class="bill-item">
              <div>
                <strong>¥{{ formatPrice(w.amount) }}</strong>
                <div class="muted text-xs">{{ w.account }} · {{ formatTime(w.createdAt) }}</div>
                <div v-if="w.rejectReason" class="muted text-xs">原因：{{ w.rejectReason }}</div>
              </div>
              <span class="badge" :class="`badge-${withdrawStatusMeta(w.status).tone}`">
                {{ withdrawStatusMeta(w.status).label }}
              </span>
            </li>
          </ul>
        </div>
        <template #actions>
          <button class="btn btn-outline" @click="showWithdraw = false">关闭</button>
          <button class="btn btn-primary" :disabled="withdrawing" @click="doWithdraw">
            {{ withdrawing ? '提交中…' : '提交申请' }}
          </button>
        </template>
      </AppModal>

      <AppModal
        :show="!!addrEditing"
        :title="addrEditing === 'new' ? '新增地址' : '编辑地址'"
        width="560px"
        @close="addrEditing = null"
      >
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
        <template #actions>
          <button class="btn btn-outline" @click="addrEditing = null">取消</button>
          <button class="btn btn-primary" :disabled="savingAddr" @click="saveAddress">
            {{ savingAddr ? '保存中…' : '保存' }}
          </button>
        </template>
      </AppModal>

      <AppModal
        :show="!!editing"
        title="编辑商品"
        width="560px"
        @close="editing = null"
      >
          <div class="form-group">
            <label>商品名称</label>
            <input v-model="editForm.name" class="form-input" />
          </div>
          <div class="row">
            <div class="form-group">
              <label>分类</label>
              <AppSelect
                v-model="editForm.category"
                :options="categoryOptions"
                placeholder="请选择分类"
              />
            </div>
            <div class="form-group">
              <label>成色</label>
              <AppSelect
                v-model="editForm.condition"
                :options="conditionOptions"
                placeholder="请选择成色"
              />
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
            <textarea v-model="editForm.description" class="form-textarea" rows="3"></textarea>
          </div>
        <template #actions>
          <button class="btn btn-outline" @click="editing = null">取消</button>
          <button class="btn btn-primary" :disabled="savingEdit" @click="saveEdit">
            {{ savingEdit ? '保存中…' : '保存' }}
          </button>
        </template>
      </AppModal>
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
  font-weight: 650;
  font-size: 14.5px;
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
.divider {
  border: none;
  border-top: 1px solid var(--border);
  margin: 22px 0 18px;
}
.balance-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.balance {
  color: var(--accent-deep);
  font-weight: 700;
}
.pending {
  color: var(--ink-soft);
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--surface-warm);
  border: 1px solid var(--border);
}
.bill-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 360px;
  overflow: auto;
}
.bill-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.bill-amt {
  font-weight: 700;
  color: var(--danger);
}
.bill-amt.plus {
  color: var(--success);
}
.recharge-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.offer-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.offer-row:last-child {
  border-bottom: none;
}
.offer-info .price {
  color: var(--accent-deep);
}
.offer-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
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
.chip.pending {
  background: var(--warning, #d97706);
}
.chip.rejected {
  background: #b91c1c;
}
.reject-note {
  font-size: 12px;
  color: var(--danger);
  margin-bottom: 6px;
  line-height: 1.4;
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

.sub {
  margin: 18px 0 12px;
}
.coupon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}
.coupon-card {
  display: flex;
  gap: 12px;
  border: 1px solid var(--border);
  border-radius: 14px;
  overflow: hidden;
  background: var(--surface);
}
.coupon-card.dim {
  opacity: 0.55;
}
.coupon-face {
  width: 96px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
}
.coupon-num {
  font-size: 22px;
  font-weight: 800;
}
.coupon-cond {
  font-size: 11px;
  opacity: 0.9;
}
.coupon-info {
  padding: 12px 12px 12px 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  flex: 1;
}
.coupon-info .name {
  font-weight: 600;
  font-size: 14px;
}
.coupon-info .btn {
  align-self: flex-start;
}
.coupon-info .chip {
  align-self: flex-start;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
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
</style>

