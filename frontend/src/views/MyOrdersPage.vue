<script setup>
import { ref, onMounted } from 'vue'
import { orderApi } from '@/api'
import { formatPrice, formatTime, firstImage, ORDER_STATUS } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/EmptyState.vue'

const store = useUserStore()
const tab = ref('buyer')
const orders = ref([])
const loading = ref(true)
const trackInputs = ref({})

function statusMeta(s) {
  return ORDER_STATUS[s] || { label: s, tone: 'muted' }
}

function imgOf(o) {
  if (!o.imageUrl) return '/user-avatar/default.jpg'
  return String(o.imageUrl).split(',')[0]
}

async function load() {
  loading.value = true
  const d =
    tab.value === 'buyer' ? await orderApi.list() : await orderApi.sellerList()
  if (d.success) orders.value = d.orders || []
  loading.value = false
}

function switchTab(t) {
  if (tab.value === t) return
  tab.value = t
  load()
}

async function ship(o) {
  const trackingNumber = (trackInputs.value[o.id] || '').trim()
  if (!trackingNumber) {
    showToast('请填写快递单号', 'warning')
    return
  }
  const d = await orderApi.update(o.id, { status: 'shipped', trackingNumber })
  if (d.success) {
    showToast('发货成功')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '发货失败', 'error')
  }
}

async function cancel(o) {
  const ok = await confirmDialog({
    title: '取消订单',
    message: '取消后余额将退还，确定取消该订单吗？',
    okText: '取消订单',
    type: 'warning'
  })
  if (!ok) return
  const d = await orderApi.cancel(o.id)
  if (d.success) {
    showToast(d.message || '订单已取消')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '取消失败', 'error')
  }
}

async function receive(o) {
  const ok = await confirmDialog({
    title: '确认收货',
    message: '确认已收到商品吗？',
    okText: '确认收货'
  })
  if (!ok) return
  const d = await orderApi.update(o.id, { status: 'received' })
  if (d.success) {
    showToast('已确认收货')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function complete(o) {
  const ok = await confirmDialog({
    title: '确认完成',
    message: '确认完成该订单吗？',
    okText: '确认完成'
  })
  if (!ok) return
  const d = await orderApi.complete(o.id)
  if (d.success) {
    showToast('订单已完成')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container">
      <h1 class="h1">我的订单</h1>
      <div class="tabs">
        <button class="tab" :class="{ active: tab === 'buyer' }" @click="switchTab('buyer')">
          我买到的
        </button>
        <button class="tab" :class="{ active: tab === 'seller' }" @click="switchTab('seller')">
          我卖出的
        </button>
      </div>

      <div v-if="loading" class="loading"><div class="spinner"></div>加载中…</div>
      <EmptyState
        v-else-if="!orders.length"
        icon="fa-box"
        title="暂无订单"
        :desc="tab === 'buyer' ? '快去挑选心仪好物吧' : '还没有买家下单'"
      />

      <div v-else class="order-list">
        <article v-for="o in orders" :key="o.id" class="order-card card">
          <div class="order-head">
            <div class="mono">订单号 {{ o.orderNumber || `#${o.id}` }}</div>
            <span class="badge" :class="`badge-${statusMeta(o.status).tone}`">
              {{ statusMeta(o.status).label }}
            </span>
          </div>

          <router-link :to="`/order/${o.id}`" class="order-body">
            <img
              :src="imgOf(o)"
              class="thumb"
              alt=""
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
            <div class="info">
              <div class="name">{{ o.productName }}</div>
              <div class="muted text-sm">
                ¥{{ formatPrice(o.productPrice) }} × {{ o.quantity || 1 }}
              </div>
              <div v-if="tab === 'seller'" class="muted text-sm">
                买家：{{ o.receiverName }} · {{ o.receiverPhone }}
              </div>
              <div v-else class="muted text-sm">收货人：{{ o.receiverName }}</div>
              <div v-if="o.trackingNumber" class="muted text-sm">
                快递单号：{{ o.trackingNumber }}
              </div>
            </div>
            <div class="side">
              <div class="muted text-sm">{{ formatTime(o.createdAt) }}</div>
              <div class="total">
                合计 <strong>¥{{ formatPrice(Number(o.productPrice || 0) * Number(o.quantity || 1)) }}</strong>
              </div>
            </div>
          </router-link>

          <div class="order-actions">
            <template v-if="tab === 'seller' && o.status === 'unshipped'">
              <input
                v-model="trackInputs[o.id]"
                class="form-input track"
                placeholder="快递单号"
              />
              <button class="btn btn-primary btn-sm" @click="ship(o)">确认发货</button>
            </template>
            <template v-else-if="tab === 'buyer'">
              <button v-if="o.status === 'unshipped'" class="btn btn-outline btn-sm" @click="cancel(o)">
                取消订单
              </button>
              <button v-if="o.status === 'shipped'" class="btn btn-primary btn-sm" @click="receive(o)">
                确认收货
              </button>
              <button v-if="o.status === 'received'" class="btn btn-success btn-sm" @click="complete(o)">
                确认完成
              </button>
            </template>
            <router-link :to="`/order/${o.id}`" class="btn btn-ghost btn-sm">订单详情</router-link>
          </div>
        </article>
      </div>
    </div>
  </main>
</template>

<style scoped>
.tabs {
  display: inline-flex;
  gap: 8px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 5px;
  margin: 12px 0 20px;
}
.tab {
  padding: 9px 18px;
  border-radius: 999px;
  font-weight: 600;
  font-size: 13.5px;
  color: var(--ink-soft);
}
.tab.active {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
}
.order-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.order-card {
  padding: 18px;
}
.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12.5px;
  color: var(--muted);
}
.order-body {
  display: flex;
  gap: 14px;
  align-items: center;
}
.thumb {
  width: 88px;
  height: 88px;
  border-radius: 12px;
  object-fit: cover;
}
.info {
  flex: 1;
}
.name {
  font-weight: 650;
  margin-bottom: 4px;
}
.side {
  text-align: right;
}
.total {
  margin-top: 8px;
  font-size: 13px;
}
.total strong {
  color: var(--accent-deep);
  font-size: 16px;
}
.order-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 14px;
  flex-wrap: wrap;
  align-items: center;
}
.track {
  width: 180px;
}
@media (max-width: 700px) {
  .order-body {
    flex-wrap: wrap;
  }
  .side {
    width: 100%;
    text-align: left;
  }
  .order-card {
    padding: 14px;
  }
  .thumb {
    width: 72px;
    height: 72px;
  }
  .order-actions {
    justify-content: stretch;
  }
  .order-actions .btn {
    flex: 1;
  }
  .track {
    width: 100%;
    order: -1;
    margin-bottom: 6px;
  }
  .tabs {
    width: 100%;
  }
  .tab {
    flex: 1;
    text-align: center;
  }
}
</style>
