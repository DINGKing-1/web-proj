<script setup>
import { ref, onMounted } from 'vue'
import { orderApi } from '@/api'
import { formatPrice, formatTime, ORDER_STATUS } from '@/api/client'
import { showToast } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'

const store = useUserStore()
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
  const d = await orderApi.sellerList()
  if (d.success) orders.value = d.orders || []
  loading.value = false
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

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container">
      <h1 class="h1">卖家发货</h1>
      <p class="muted mb-4">处理待发货订单，填写快递单号后发货</p>

      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="!orders.length"
        icon="fa-store"
        title="暂无订单"
        desc="还没有买家下单"
      />

      <div v-else class="order-list">
        <article v-for="o in orders" :key="o.id" class="card card-pad">
          <div class="head">
            <div class="muted text-sm">订单号 {{ o.orderNumber || `#${o.id}` }}</div>
            <span class="badge" :class="`badge-${statusMeta(o.status).tone}`">
              {{ statusMeta(o.status).label }}
            </span>
          </div>
          <router-link :to="`/order/${o.id}`" class="body">
            <img
              :src="imgOf(o)"
              class="thumb"
              alt=""
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
            <div class="info">
              <div class="name">{{ o.productName }}</div>
              <div class="muted text-sm">¥{{ formatPrice(o.productPrice) }} × {{ o.quantity || 1 }}</div>
              <div class="muted text-sm">买家：{{ o.receiverName }} · {{ o.receiverPhone }}</div>
              <div class="muted text-sm">地址：{{ o.receiverAddress }}</div>
              <div v-if="o.trackingNumber" class="muted text-sm">快递单号：{{ o.trackingNumber }}</div>
              <div class="muted text-sm">{{ formatTime(o.createdAt) }}</div>
            </div>
          </router-link>
          <div v-if="o.status === 'unshipped'" class="actions">
            <input
              v-model="trackInputs[o.id]"
              class="form-input track"
              placeholder="请输入快递单号"
            />
            <button class="btn btn-primary" @click="ship(o)">确认发货</button>
          </div>
        </article>
      </div>
    </div>
  </main>
</template>

<style scoped>
.order-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.body {
  display: flex;
  gap: 14px;
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
.actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 14px;
}
.track {
  width: 220px;
}
</style>
