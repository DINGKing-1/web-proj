<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { productApi, cartApi, orderApi, userApi } from '@/api'
import { formatPrice, firstImage } from '@/api/client'
import LoadingBlock from '@/components/LoadingBlock.vue'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()

const checkoutItems = ref([])
const addresses = ref([])
const selectedAddressId = ref(null)
const balance = ref(0)
const loading = ref(true)
const submitting = ref(false)
const showAddrForm = ref(false)
const addrForm = ref({
  receiverName: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: ''
})

const total = computed(() =>
  checkoutItems.value.reduce((s, it) => s + Number(it.product?.secondHandPrice || 0), 0)
)
const selectedAddress = computed(
  () => addresses.value.find((a) => a.id === selectedAddressId.value) || null
)

function imgOf(it) {
  return firstImage(it.product) || '/user-avatar/default.jpg'
}

async function load() {
  loading.value = true
  const buyId = sessionStorage.getItem('checkoutProductId')
  let buyQty = Number(sessionStorage.getItem('checkoutQuantity') || '1') || 1

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
    const d = await cartApi.list()
    checkoutItems.value = (d.items || [])
      .filter((i) => i.available)
      .map((item) => ({
        product: {
          id: item.productId,
          name: item.productName,
          secondHandPrice: item.price,
          category: item.category || '',
          imageUrl: item.image,
          firstImage: item.image
        },
        quantity: 1,
        cartItemId: item.id
      }))
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
  const f = addrForm.value
  if (!f.receiverName.trim() || !f.phone.trim() || !f.detail.trim()) {
    showToast('请填写收货人、手机号和详细地址', 'warning')
    return
  }
  const d = await userApi.addAddress({
    receiverName: f.receiverName.trim(),
    phone: f.phone.trim(),
    province: f.province.trim(),
    city: f.city.trim(),
    district: f.district.trim(),
    detail: f.detail.trim(),
    isDefault: addresses.value.length === 0
  })
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

  const ok = await confirmDialog({
    title: '确认支付',
    message: `共 ${checkoutItems.value.length} 件商品，合计 ¥${formatPrice(total.value)}，确认使用余额支付吗？`,
    okText: '确认支付',
    type: 'info'
  })
  if (!ok) return

  submitting.value = true
  const addr = selectedAddress.value
  const receiverAddress = `${addr.province || ''}${addr.city || ''}${addr.district || ''} ${addr.detail}`.trim()
  const items = checkoutItems.value.map((it) => ({
    productId: it.product.id,
    productName: it.product.name,
    productPrice: it.product.secondHandPrice,
    category: it.product.category,
    imageUrl: firstImage(it.product),
    quantity: it.quantity || 1
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
    paymentMethod: '余额支付'
  })

  submitting.value = false
  sessionStorage.removeItem('checkoutProductId')
  sessionStorage.removeItem('checkoutQuantity')
  sessionStorage.removeItem('checkout')
  store.refreshCartCount()

  if (d.success) {
    showToast('购买成功！')
    setTimeout(() => router.push('/my-orders'), 800)
  } else {
    showToast(d.message || '下单失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container narrow">
      <h1 class="h1">确认订单</h1>
      <p class="muted mb-4">余额支付 · 确认无误后提交</p>

      <LoadingBlock v-if="loading" />
      <template v-else>
        <section class="card card-pad block">
          <h2 class="h3 mb-3">商品清单</h2>
          <div v-if="!checkoutItems.length" class="muted">暂无商品</div>
          <div v-for="it in checkoutItems" :key="it.product.id" class="item">
            <img
              :src="imgOf(it)"
              class="thumb"
              alt=""
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
            <div class="meta">
              <router-link :to="`/product/${it.product.id}`" class="name">{{ it.product.name }}</router-link>
              <div class="muted text-sm">{{ it.product.category }} × {{ it.quantity }}</div>
            </div>
            <div class="price">¥{{ formatPrice(it.product.secondHandPrice) }}</div>
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
            <button class="btn btn-primary" @click="addAddress">保存地址</button>
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
          <div class="pay-row">
            <span class="badge badge-success">余额支付</span>
            <span class="muted">当前余额 ¥{{ formatPrice(balance) }}</span>
          </div>
          <div class="total-row">
            <span>应付金额</span>
            <span class="price-lg">¥{{ formatPrice(total) }}</span>
          </div>
          <button
            class="btn btn-primary btn-lg btn-block"
            :disabled="submitting || !checkoutItems.length"
            @click="submitOrders"
          >
            {{ submitting ? '支付中…' : '确认支付' }}
          </button>
        </section>
      </template>
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
.pay-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-weight: 600;
}
</style>
