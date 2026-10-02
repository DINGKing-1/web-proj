<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderApi, productApi, chatApi } from '@/api'
import { formatPrice, formatTime, ORDER_STATUS } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import OrderTimeline from '@/components/OrderTimeline.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const order = ref(null)
const product = ref(null)
const loading = ref(true)

const isBuyer = computed(
  () => store.user && order.value && store.user.username === order.value.username
)
const statusLabel = computed(() =>
  order.value ? (ORDER_STATUS[order.value.status]?.label || order.value.status) : ''
)

function imgOf(o) {
  if (!o.imageUrl) return '/user-avatar/default.jpg'
  return String(o.imageUrl).split(',')[0]
}

async function load() {
  loading.value = true
  const d = await orderApi.detail(route.params.id)
  if (d.success && d.order) {
    order.value = d.order
    if (d.order.productId) {
      const p = await productApi.detail(d.order.productId)
      if (p.success) product.value = p.product
    }
  } else {
    order.value = null
  }
  loading.value = false
}

async function cancelOrder() {
  const ok = await confirmDialog({
    title: '取消订单',
    message: '取消后余额将退还，确定取消吗？',
    okText: '取消订单',
    type: 'warning'
  })
  if (!ok) return
  const d = await orderApi.cancel(order.value.id)
  if (d.success) {
    showToast(d.message || '订单已取消')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '取消失败', 'error')
  }
}

async function receiveOrder() {
  const ok = await confirmDialog({ title: '确认收货', message: '确认已收到商品吗？', okText: '确认收货' })
  if (!ok) return
  const d = await orderApi.update(order.value.id, { status: 'received' })
  if (d.success) {
    showToast('已确认收货')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function completeOrder() {
  const ok = await confirmDialog({ title: '确认完成', message: '确认完成该订单吗？', okText: '确认完成' })
  if (!ok) return
  const d = await orderApi.complete(order.value.id)
  if (d.success) {
    showToast('订单已完成')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function contactSeller() {
  if (!store.user) {
    router.push('/login')
    return
  }
  const productId = order.value.productId
  if (!productId) {
    showToast('无法联系卖家', 'warning')
    return
  }
  const d = await chatApi.findRoom(productId)
  if (d.success) {
    if (d.exists && d.roomId) {
      router.push({ path: '/messages', query: { roomId: d.roomId } })
    } else {
      router.push({
        path: '/messages',
        query: {
          sellerUsername: d.sellerUsername || order.value.sellerUsername,
          sellerNickname: d.sellerNickname || product.value?.sellerName || order.value.sellerUsername,
          sellerAvatar: d.sellerAvatar || product.value?.sellerAvatar || '',
          productId
        }
      })
    }
  } else {
    showToast(d.message || '联系失败', 'error')
  }
}

watch(() => route.params.id, load)
onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container narrow">
      <div class="head-row">
        <h1 class="h1">订单详情</h1>
        <router-link to="/my-orders" class="btn btn-outline btn-sm">
          <i class="fas fa-arrow-left"></i> 返回订单列表
        </router-link>
      </div>

      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="!order"
        icon="fa-box-open"
        title="订单不存在"
        desc="请确认订单号是否正确"
      >
        <router-link to="/my-orders" class="btn btn-primary">我的订单</router-link>
      </EmptyState>

      <template v-else>
        <section class="card card-pad block">
          <OrderTimeline :status="order.status" />
        </section>

        <section class="card card-pad block">
          <div class="product-row">
            <router-link v-if="order.productId" :to="`/product/${order.productId}`" class="p-link">
              <img
                :src="imgOf(order)"
                class="thumb"
                alt=""
                @error="$event.target.src = '/user-avatar/default.jpg'"
              />
            </router-link>
            <img
              v-else
              :src="imgOf(order)"
              class="thumb"
              alt=""
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
            <div class="info">
              <router-link v-if="order.productId" :to="`/product/${order.productId}`" class="name">
                {{ order.productName }}
              </router-link>
              <div v-else class="name">{{ order.productName }}</div>
              <div class="muted text-sm">{{ order.category }}</div>
              <div class="muted text-sm">
                ¥{{ formatPrice(order.productPrice) }} × {{ order.quantity || 1 }}
              </div>
            </div>
            <div class="amount">
              <div class="muted text-sm">订单金额</div>
              <div class="price">
                ¥{{ formatPrice(Number(order.productPrice || 0) * Number(order.quantity || 1)) }}
              </div>
            </div>
          </div>
          <div v-if="order.sellerUsername" class="seller-line">
            <span class="muted">卖家</span>
            <button class="btn btn-outline btn-sm" @click="contactSeller">
              <i class="fas fa-comment"></i> 联系卖家
            </button>
          </div>
        </section>

        <div class="two-col">
          <section class="card card-pad">
            <h2 class="h3 mb-3">收货信息</h2>
            <div class="kv"><span>收货人</span><strong>{{ order.receiverName }}</strong></div>
            <div class="kv"><span>手机号</span><strong>{{ order.receiverPhone }}</strong></div>
            <div class="kv"><span>地址</span><strong>{{ order.receiverAddress }}</strong></div>
          </section>

          <section class="card card-pad">
            <h2 class="h3 mb-3">订单信息</h2>
            <div class="kv"><span>订单号</span><strong>{{ order.orderNumber || `#${order.id}` }}</strong></div>
            <div class="kv"><span>下单时间</span><strong>{{ formatTime(order.createdAt) }}</strong></div>
            <div class="kv"><span>数量</span><strong>{{ order.quantity || 1 }} 件</strong></div>
            <div class="kv"><span>支付方式</span><strong>{{ order.paymentMethod === 'balance' ? '余额支付' : (order.paymentMethod || '余额支付') }}</strong></div>
            <div class="kv"><span>状态</span><strong>{{ statusLabel }}</strong></div>
            <div v-if="order.trackingNumber" class="kv">
              <span>快递单号</span><strong>{{ order.trackingNumber }}</strong>
            </div>
          </section>
        </div>

        <div class="actions">
          <button
            v-if="isBuyer && order.status === 'unshipped'"
            class="btn btn-danger btn-lg"
            @click="cancelOrder"
          >
            取消订单
          </button>
          <button
            v-if="isBuyer && order.status === 'shipped'"
            class="btn btn-primary btn-lg"
            @click="receiveOrder"
          >
            确认收货
          </button>
          <button
            v-if="isBuyer && order.status === 'received'"
            class="btn btn-success btn-lg"
            @click="completeOrder"
          >
            确认完成
          </button>
        </div>
      </template>
    </div>
  </main>
</template>

<style scoped>
.narrow {
  max-width: 860px;
}
.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.block {
  margin-bottom: 16px;
}
.product-row {
  display: flex;
  gap: 14px;
  align-items: center;
}
.thumb {
  width: 86px;
  height: 86px;
  border-radius: 12px;
  object-fit: cover;
}
.info {
  flex: 1;
}
.name {
  font-weight: 650;
}
.amount {
  text-align: right;
}
.seller-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.kv {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--border);
  font-size: 13.5px;
}
.kv:last-child {
  border-bottom: none;
}
.kv span {
  color: var(--muted);
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}
@media (max-width: 760px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>

