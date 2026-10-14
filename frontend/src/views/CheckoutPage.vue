<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { productApi, cartApi, orderApi, userApi } from '@/api'
import { imgOf, toCents, formatCents, formatPrice, onImgError } from '@/api/client'
import LoadingBlock from '@/components/LoadingBlock.vue'
import PayCouponModal from '@/components/PayCouponModal.vue'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import payBalanceIcon from '@/assets/pay-balance.svg'
import payAlipayIcon from '@/assets/pay-alipay.svg'
import payWechatIcon from '@/assets/pay-wechat.svg'

const router = useRouter()
const store = useUserStore()

const checkoutItems = ref([])
const addresses = ref([])
const selectedAddressId = ref(null)
const balance = ref(0)
const loading = ref(true)
const submitting = ref(false)
const savingAddr = ref(false)
const showAddrForm = ref(false)
/** 支付方式：balance / alipay / wechat */
const payMethod = ref('balance')
/** 优惠券：结算时选一张，支付阶段核销到金额最高的一单 */
const showCouponModal = ref(false)
const selectedCoupon = ref(null) // { userCouponId, discountAmount }
const addrForm = ref({
  receiverName: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: ''
})

const PAY_METHODS = [
  {
    key: 'balance',
    label: '余额支付',
    desc: '使用账户余额，实时到账',
    color: '#d4883c',
    icon: payBalanceIcon
  },
  {
    key: 'alipay',
    label: '支付宝',
    desc: '推荐支付宝用户使用',
    color: '#1677ff',
    icon: payAlipayIcon
  },
  {
    key: 'wechat',
    label: '微信支付',
    desc: '推荐微信用户使用',
    color: '#07c160',
    icon: payWechatIcon
  }
]

const payMethodMeta = computed(() => PAY_METHODS.find((p) => p.key === payMethod.value) || PAY_METHODS[0])

function payMethodLabel(key) {
  return PAY_METHODS.find((p) => p.key === key)?.label || '余额支付'
}

// 金额一律用“分”整数运算，避免浮点误差
const grossCents = computed(() =>
  checkoutItems.value.reduce(
    (s, it) => s + toCents(it.product?.secondHandPrice || 0) * Math.max(1, Number(it.quantity || 1)),
    0
  )
)
/** 优惠券核销到行金额最高的一单；门槛与后端 consume 同口径 = 该单实付总额 */
const couponBaseCents = computed(() =>
  checkoutItems.value.reduce((m, it) => Math.max(m, itemTotalCents(it)), 0)
)
const discountCents = computed(() =>
  selectedCoupon.value
    ? Math.min(toCents(selectedCoupon.value.discountAmount || 0), couponBaseCents.value)
    : 0
)
const totalCents = computed(() => Math.max(0, grossCents.value - discountCents.value))
const total = computed(() => formatCents(totalCents.value))
const insufficient = computed(() => toCents(balance.value) < totalCents.value)
const selectedAddress = computed(
  () => addresses.value.find((a) => a.id === selectedAddressId.value) || null
)

function itemTotalCents(it) {
  return toCents(it.product?.secondHandPrice || 0) * Math.max(1, Number(it.quantity || 1))
}

async function load() {
  loading.value = true
  const buyId = sessionStorage.getItem('checkoutProductId')
  let buyQty = Number(sessionStorage.getItem('checkoutQuantity') || '1') || 1
  const cartIdsRaw = sessionStorage.getItem('checkoutCartIds')

  // 直开 /checkout 无结算上下文时拦住，避免把整个购物车误下单
  if (!buyId && !cartIdsRaw) {
    loading.value = false
    showToast('请从购物车勾选商品后结算', 'warning')
    router.replace('/cart')
    return
  }

  if (buyId) {
    const d = await productApi.detail(buyId)
    if (d.success && d.product) {
      const maxQ = Math.max(1, Number(d.product.quantity ?? 1))
      buyQty = Math.min(Math.max(1, buyQty), maxQ)
      checkoutItems.value = [{ product: d.product, quantity: buyQty, cartItemId: null }]
    } else {
      showToast('商品不存在', 'error')
    }
  } else {
    // 购物车 DTO 已含下单所需字段，无需再逐件拉商品详情
    // 仅结算勾选项（由 CartPage 写入 checkoutCartIds）
    let selected = null
    try {
      if (cartIdsRaw) selected = new Set(JSON.parse(cartIdsRaw))
    } catch {
      selected = null
    }
    if (!selected || selected.size === 0) {
      loading.value = false
      showToast('请从购物车勾选商品后结算', 'warning')
      router.replace('/cart')
      return
    }
    const d = await cartApi.list()
    checkoutItems.value = (d.items || [])
      .filter((i) => i.available && selected.has(i.id))
      .map((item) => {
        const img = String(item.image || '').split(',')[0].trim()
        return {
          product: {
            id: item.productId,
            name: item.productName,
            secondHandPrice: item.price,
            category: item.category || '',
            imageUrl: img,
            firstImage: img
          },
          quantity: Number(item.quantity || 1),
          cartItemId: item.id
        }
      })
    if (!checkoutItems.value.length) {
      loading.value = false
      showToast('所选商品均已下架或不在购物车中', 'warning')
      router.replace('/cart')
      return
    }
  }

  const [addr, user] = await Promise.all([userApi.addresses(), userApi.info()])
  if (addr.success) {
    addresses.value = addr.addresses || []
    const def = addresses.value.find((a) => a.isDefault) || addresses.value[0]
    selectedAddressId.value = def?.id ?? null
  }
  if (user.success) balance.value = Number(user.balance || 0)

  loading.value = false
}

async function addAddress() {
  if (savingAddr.value) return
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
  const d = await userApi.addAddress({
    receiverName: f.receiverName.trim(),
    phone: f.phone.trim(),
    province: f.province.trim(),
    city: f.city.trim(),
    district: f.district.trim(),
    detail: f.detail.trim(),
    isDefault: addresses.value.length === 0
  })
  savingAddr.value = false
  if (d.success) {
    showToast('地址添加成功')
    showAddrForm.value = false
    addrForm.value = { receiverName: '', phone: '', province: '', city: '', district: '', detail: '' }
    const list = await userApi.addresses()
    if (list.success) {
      addresses.value = list.addresses || []
      selectedAddressId.value = d.addressId || addresses.value[0]?.id
    }
  } else {
    showToast(d.message || '添加失败', 'error')
  }
}

async function submitOrders() {
  if (!checkoutItems.value.length) {
    showToast('没有可购买的商品', 'warning')
    return
  }
  if (!selectedAddress.value) {
    showToast('请选择收货地址', 'warning')
    return
  }
  // 所有支付通道演示环境统一走余额扣款，一律预检余额
  if (insufficient.value) {
    showToast('余额不足，请先充值（下单统一从账户余额扣款）', 'warning')
    return
  }

  const methodLabel = payMethodLabel(payMethod.value)
  const couponNote = selectedCoupon.value
    ? `，优惠券抵扣 ¥${formatCents(discountCents.value)}`
    : ''
  const ok = await confirmDialog({
    title: '确认支付',
    message: `共 ${checkoutItems.value.length} 件商品，合计 ¥${total.value}${couponNote}，确认使用${methodLabel}吗？（演示环境从账户余额扣款）`,
    okText: '确认支付',
    type: 'info'
  })
  if (!ok) return

  submitting.value = true
  const addr = selectedAddress.value
  const receiverAddress = `${addr.province || ''}${addr.city || ''}${addr.district || ''} ${addr.detail}`.trim()
  // 只传业务字段：价格/商品名/图片等快照一律由服务端计算
  const items = checkoutItems.value.map((it) => ({
    productId: it.product.id,
    quantity: Math.max(1, Number(it.quantity || 1))
  }))
  const cartItemIds = checkoutItems.value
    .map((it) => it.cartItemId)
    .filter((id) => id != null)

  const d = await orderApi.createBatch({
    items,
    cartItemIds,
    receiverName: addr.receiverName,
    receiverPhone: addr.phone,
    receiverAddress,
    paymentMethod: methodLabel
  })

  if (!d.success) {
    submitting.value = false
    showToast(d.message || '下单失败', 'error')
    return
  }

  // 下单成功后立刻支付（扣款才真正完成）
  const orderIds = d.orderIds || []
  let paidCount = 0
  let failedMsg = ''
  // 优惠券只能核销到一单：取行金额（单价×数量）最高的一单，与门槛口径一致
  let couponOrderId = null
  if (selectedCoupon.value?.userCouponId && orderIds.length) {
    couponOrderId = orderIds[0]
    if (orderIds.length > 1) {
      let bestIdx = 0
      let bestLine = -1
      checkoutItems.value.forEach((it, i) => {
        const p = itemTotalCents(it)
        if (p > bestLine) {
          bestLine = p
          bestIdx = i
        }
      })
      couponOrderId = orderIds[bestIdx] || orderIds[0]
    }
  }

  for (const id of orderIds) {
    const useCoupon = id === couponOrderId ? selectedCoupon.value?.userCouponId : null
    const payRes = await orderApi.payWithCoupon(id, useCoupon)
    if (payRes.success) paidCount++
    else failedMsg = payRes.message || '部分订单支付失败'
  }

  submitting.value = false

  // 订单已创建就必须清结算上下文：否则后退/重进结算页再提交会重复下单
  sessionStorage.removeItem('checkoutProductId')
  sessionStorage.removeItem('checkoutQuantity')
  sessionStorage.removeItem('checkout')
  sessionStorage.removeItem('checkoutCartIds')

  if (paidCount === orderIds.length) {
    store.refreshCartCount()
    store.refreshSession()
    showToast(`${methodLabel}成功！`)
    setTimeout(() => router.push('/my-orders'), 800)
  } else if (paidCount > 0) {
    store.refreshCartCount()
    showToast(`已支付 ${paidCount}/${orderIds.length} 单，${failedMsg}，请到订单页完成剩余支付`, 'warning')
    setTimeout(() => router.push('/my-orders'), 1200)
  } else {
    showToast(failedMsg || '支付失败，订单已创建，请到我的订单完成支付', 'error')
    setTimeout(() => router.push('/my-orders'), 1200)
  }
}

function onCouponConfirm(userCouponId, discountAmount) {
  showCouponModal.value = false
  if (!userCouponId) {
    selectedCoupon.value = null
    return
  }
  selectedCoupon.value = { userCouponId, discountAmount: Number(discountAmount) || 0 }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container narrow">
      <h1 class="h1">确认订单</h1>
      <p class="muted mb-4">选择支付方式 · 确认无误后提交</p>

      <LoadingBlock v-if="loading" />
      <template v-else>
        <section class="card card-pad block">
          <h2 class="h3 mb-3">商品清单</h2>
          <div v-if="!checkoutItems.length" class="muted">暂无商品</div>
          <div v-for="it in checkoutItems" :key="it.cartItemId || `${it.product.id}-${it.quantity}`" class="item">
            <img
              :src="imgOf(it.product)"
              class="thumb"
              alt=""
              @error="onImgError"
            />
            <div class="meta">
              <router-link :to="`/product/${it.product.id}`" class="name">{{ it.product.name }}</router-link>
              <div class="muted text-sm">{{ it.product.category }} × {{ it.quantity }}</div>
            </div>
            <div class="price">¥{{ formatCents(itemTotalCents(it)) }}</div>
          </div>
        </section>

        <section class="card card-pad block">
          <div class="section-head">
            <h2 class="h3">收货地址</h2>
            <button class="btn btn-outline btn-sm" @click="showAddrForm = !showAddrForm">
              {{ showAddrForm ? '取消' : '新增地址' }}
            </button>
          </div>

          <div v-if="showAddrForm" class="addr-form">
            <div class="row">
              <div class="form-group">
                <label>收货人 <span class="required">*</span></label>
                <input v-model="addrForm.receiverName" class="form-input" />
              </div>
              <div class="form-group">
                <label>手机号 <span class="required">*</span></label>
                <input v-model="addrForm.phone" class="form-input" />
              </div>
            </div>
            <div class="row">
              <div class="form-group">
                <label>省份</label>
                <input v-model="addrForm.province" class="form-input" />
              </div>
              <div class="form-group">
                <label>城市</label>
                <input v-model="addrForm.city" class="form-input" />
              </div>
              <div class="form-group">
                <label>区县</label>
                <input v-model="addrForm.district" class="form-input" />
              </div>
            </div>
            <div class="form-group">
              <label>详细地址 <span class="required">*</span></label>
              <input v-model="addrForm.detail" class="form-input" />
            </div>
            <button class="btn btn-primary" :disabled="savingAddr" @click="addAddress">{{ savingAddr ? '保存中…' : '保存地址' }}</button>
          </div>

          <div v-if="!addresses.length && !showAddrForm" class="muted">
            还没有地址，点击右上角「新增地址」
          </div>
          <div class="addr-list">
            <label
              v-for="a in addresses"
              :key="a.id"
              class="addr-card"
              :class="{ selected: selectedAddressId === a.id }"
            >
              <input v-model="selectedAddressId" type="radio" :value="a.id" />
              <div>
                <div class="addr-top">
                  <strong>{{ a.receiverName }}</strong>
                  <span class="muted">{{ a.phone }}</span>
                  <span v-if="a.isDefault" class="badge">默认</span>
                </div>
                <div class="muted text-sm">
                  {{ a.province }}{{ a.city }}{{ a.district }} {{ a.detail }}
                </div>
              </div>
            </label>
          </div>
        </section>

        <section class="card card-pad block">
          <h2 class="h3 mb-3">支付方式</h2>
          <div class="pay-methods">
            <label
              v-for="m in PAY_METHODS"
              :key="m.key"
              class="pay-option"
              :class="{ selected: payMethod === m.key }"
              :style="payMethod === m.key ? { borderColor: m.color, background: m.color + '0f' } : null"
            >
              <input v-model="payMethod" type="radio" :value="m.key" />
              <img class="pay-logo" :src="m.icon" :alt="m.label" />
              <div class="pay-info">
                <div class="pay-name">{{ m.label }}</div>
                <div class="pay-desc">{{ m.desc }}</div>
              </div>
              <span v-if="m.key === 'balance' && insufficient" class="badge badge-warning">余额不足</span>
              <span v-if="payMethod === m.key" class="pay-check" :style="{ color: m.color }">✓</span>
            </label>
          </div>
          <p v-if="payMethod !== 'balance'" class="muted text-sm mt-2">
            {{ payMethodMeta.label }}为模拟支付通道（演示环境），实际扣款仍走账户余额。
          </p>
          <div class="coupon-row">
            <span>优惠券</span>
            <button type="button" class="btn btn-outline btn-sm" @click="showCouponModal = true">
              {{ selectedCoupon ? `已抵扣 ¥${formatCents(discountCents)}` : '选择优惠券' }}
            </button>
          </div>
          <div v-if="selectedCoupon && checkoutItems.length > 1" class="muted text-sm mb-2">
            优惠券将核销到金额最高的一件商品对应订单
          </div>
          <div class="total-row">
            <span>应付金额</span>
            <span class="price-lg">¥{{ total }}</span>
          </div>
          <button
            class="btn btn-lg btn-block pay-submit"
            :class="{
              'btn-primary': payMethod === 'balance',
              'btn-alipay': payMethod === 'alipay',
              'btn-wechat': payMethod === 'wechat'
            }"
            :disabled="submitting || !checkoutItems.length || insufficient"
            @click="submitOrders"
          >
            <img class="pay-logo-sm" :src="payMethodMeta.icon" :alt="payMethodMeta.label" />
            {{ submitting ? '支付中…' : `使用${payMethodMeta.label}支付 ¥${total}` }}
          </button>
        </section>
      </template>

      <PayCouponModal
        :show="showCouponModal"
        :gross="couponBaseCents / 100"
        @close="showCouponModal = false"
        @confirm="onCouponConfirm"
      />
    </div>
  </main>
</template>

<style scoped>
.narrow {
  max-width: 760px;
}
.block {
  margin-bottom: 16px;
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
.item:last-child {
  border-bottom: none;
}
.thumb {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  object-fit: cover;
}
.meta {
  flex: 1;
}
.name {
  font-weight: 600;
}
.price {
  font-weight: 700;
  color: var(--accent-deep);
}
.addr-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.addr-card {
  display: flex;
  gap: 12px;
  padding: 14px;
  border-radius: 14px;
  border: 1.5px solid var(--border);
  cursor: pointer;
}
.addr-card.selected {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.addr-card input {
  margin-top: 4px;
}
.addr-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.addr-form {
  background: var(--surface-warm);
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 14px;
}
.row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}
.pay-methods {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 8px;
}
.pay-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1.5px solid var(--border);
  border-radius: 14px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
  background: var(--surface);
}
.pay-option:hover {
  border-color: var(--border-strong);
}
.pay-option.selected {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.pay-option input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}
.pay-logo {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  flex-shrink: 0;
  object-fit: contain;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.pay-logo-sm {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  object-fit: contain;
  background: rgba(255, 255, 255, 0.2);
}
.pay-info {
  flex: 1;
  min-width: 0;
}
.pay-name {
  font-weight: 650;
  font-size: 15px;
}
.pay-desc {
  font-size: 12px;
  color: var(--muted);
  margin-top: 2px;
}
.pay-check {
  font-size: 18px;
  font-weight: 700;
  margin-left: 4px;
}
.pay-submit.btn-alipay {
  background: linear-gradient(135deg, #1677ff 0%, #0e5fd8 100%);
  color: #fff;
  box-shadow: 0 8px 20px rgba(22, 119, 255, 0.28);
}
.pay-submit.btn-alipay:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(22, 119, 255, 0.34);
}
.pay-submit.btn-wechat {
  background: linear-gradient(135deg, #07c160 0%, #059a4c 100%);
  color: #fff;
  box-shadow: 0 8px 20px rgba(7, 193, 96, 0.28);
}
.pay-submit.btn-wechat:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(7, 193, 96, 0.34);
}
.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-weight: 600;
}
.coupon-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0 4px;
  border-top: 1px solid var(--border);
  margin-top: 8px;
}
</style>
