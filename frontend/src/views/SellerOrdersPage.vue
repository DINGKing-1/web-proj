<script setup>
import { ref, onMounted } from 'vue'
import { orderApi } from '@/api'
import { formatPrice, formatTime, imgOf, onImgError, ORDER_STATUS } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'

const store = useUserStore()
const orders = ref([])
const loading = ref(true)
const trackInputs = ref({})
/** 发货中（按钮 loading / 防重复提交） */
const actingId = ref(null)
/** 买家手机号默认脱敏，由卖家主动展开 */
const revealed = ref({})

function statusMeta(s) {
  return ORDER_STATUS[s] || { label: s, tone: 'muted' }
}

function maskPhone(p) {
  const s = String(p || '')
  if (s.length < 7) return s
  return `${s.slice(0, 3)}****${s.slice(-4)}`
}

function phoneOf(o) {
  return revealed.value[o.id] ? String(o.receiverPhone || '') : maskPhone(o.receiverPhone)
}

function toggleReveal(o) {
  revealed.value = { ...revealed.value, [o.id]: !revealed.value[o.id] }
}

async function load() {
  loading.value = true
  const d = await orderApi.sellerList()
  if (d.success) orders.value = d.orders || []
  else showToast(d.message || '订单加载失败', 'error')
  loading.value = false
}

/** 经营看板 */
const stats = ref(null)

async function loadStats() {
  const d = await orderApi.sellerStats()
  if (d.success) stats.value = d.stats
}

const trendMax = () => {
  const t = stats.value?.trend || []
  return Math.max(1, ...t.map((r) => Number(r.count) || 0))
}

async function ship(o) {
  const trackingNumber = (trackInputs.value[o.id] || '').trim()
  if (!trackingNumber) {
    showToast('请填写快递单号', 'warning')
    return
  }
  const isUpdate = o.status === 'shipped'
  const ok = await confirmDialog({
    title: isUpdate ? '修改运单号' : '确认发货',
    message: isUpdate
      ? `确定修改快递单号为：${trackingNumber} 吗？`
      : `确定发货吗？快递单号：${trackingNumber}`,
    okText: isUpdate ? '保存' : '确认发货'
  })
  if (!ok || actingId.value) return
  actingId.value = o.id
  const d = await orderApi.update(o.id, { status: 'shipped', trackingNumber })
  actingId.value = null
  if (d.success) {
    showToast(isUpdate ? '运单号已更新' : '发货成功')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || (isUpdate ? '更新失败' : '发货失败'), 'error')
  }
}

async function approveAfterSale(o) {
  const ok = await confirmDialog({
    title: '同意退货',
    message: '同意后买家需寄回商品，你确认收货后货款才会退回买家。确定同意吗？',
    okText: '同意退货'
  })
  if (!ok || actingId.value) return
  actingId.value = o.id
  const d = await orderApi.approveAfterSale(o.id)
  actingId.value = null
  if (d.success) {
    showToast('已同意退货，等待买家寄回')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

/** 确认收到买家寄回的退货 → 退款 */
async function confirmReturn(o) {
  const ok = await confirmDialog({
    title: '确认收到退货',
    message: '确认已收到买家寄回的商品？确认后货款立即退回买家，不可撤销。',
    okText: '确认并退款',
    type: 'warning'
  })
  if (!ok || actingId.value) return
  actingId.value = o.id
  const d = await orderApi.confirmReturnReceived(o.id)
  actingId.value = null
  if (d.success) {
    showToast('已确认收货，货款已退回买家')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function rejectAfterSale(o) {
  const ok = await confirmDialog({
    title: '拒绝售后',
    message: '拒绝后订单将回到售后前状态，确定拒绝吗？',
    okText: '拒绝',
    type: 'warning'
  })
  if (!ok || actingId.value) return
  actingId.value = o.id
  const d = await orderApi.rejectAfterSale(o.id)
  actingId.value = null
  if (d.success) {
    showToast(d.message || '已拒绝售后')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

onMounted(() => {
  load()
  loadStats()
})
</script>

<template>
  <main class="page">
    <div class="container">
      <h1 class="h1">卖家中心</h1>
      <p class="muted mb-4">经营数据一览，下方处理待发货订单</p>

      <section v-if="stats" class="kpi-section">
        <div class="kpi-grid">
          <div class="kpi">
            <div class="kpi-label">在售商品</div>
            <div class="kpi-value">{{ stats.activeProducts }}</div>
            <div class="kpi-sub">共 {{ stats.totalProducts }} 件 · 已售 {{ stats.soldProducts }}</div>
          </div>
          <div class="kpi">
            <div class="kpi-label">成交笔数</div>
            <div class="kpi-value">{{ stats.salesCount }}</div>
            <div class="kpi-sub">不含取消与待付款</div>
          </div>
          <div class="kpi">
            <div class="kpi-label">已到账</div>
            <div class="kpi-value">¥{{ formatPrice(stats.settledIncome) }}</div>
            <div class="kpi-sub">订单完成后结算</div>
          </div>
          <div class="kpi">
            <div class="kpi-label">待结算</div>
            <div class="kpi-value accent">¥{{ formatPrice(stats.pendingIncome) }}</div>
            <div class="kpi-sub">确认收货后到账</div>
          </div>
        </div>

        <div class="trend card card-pad">
          <div class="trend-head">
            <span class="h3">近 14 天成交</span>
            <span class="muted text-sm">按天下单笔数</span>
          </div>
          <div class="bars">
            <div v-for="r in (stats.trend || [])" :key="r.date" class="bar-col" :title="`${r.date}：${r.count} 笔`">
              <div class="bar" :style="{ height: `${(Number(r.count) / trendMax()) * 100}%` }"></div>
              <span class="bar-x">{{ r.date.slice(8) }}</span>
            </div>
          </div>
        </div>
      </section>

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
              @error="onImgError"
            />
            <div class="info">
              <div class="name">{{ o.productName }}</div>
              <div class="muted text-sm">¥{{ formatPrice(o.productPrice) }} × {{ o.quantity || 1 }}</div>
              <div class="muted text-sm">
                买家：{{ o.receiverName }} · {{ phoneOf(o) }}
                <button type="button" class="link-btn" @click.prevent="toggleReveal(o)">
                  {{ revealed[o.id] ? '隐藏' : '查看' }}
                </button>
              </div>
              <div class="muted text-sm">地址：{{ o.receiverAddress }}</div>
              <div v-if="o.trackingNumber" class="muted text-sm">快递单号：{{ o.trackingNumber }}</div>
              <div class="muted text-sm">{{ formatTime(o.createdAt) }}</div>
            </div>
          </router-link>
          <div v-if="o.status === 'unshipped' || o.status === 'shipped'" class="actions">
            <input
              v-model="trackInputs[o.id]"
              class="form-input track"
              :placeholder="o.status === 'shipped' ? (o.trackingNumber || '修改快递单号') : '请输入快递单号'"
            />
            <button class="btn btn-primary" :disabled="actingId === o.id" @click="ship(o)">
              {{ actingId === o.id ? '提交中…' : (o.status === 'shipped' ? '修改运单' : '确认发货') }}
            </button>
          </div>
          <div v-else-if="o.status === 'after_sale'" class="actions">
            <span class="muted text-sm">售后：{{ o.afterSaleReason || '买家申请退款' }}</span>
            <button class="btn btn-primary" :disabled="actingId === o.id" @click="approveAfterSale(o)">
              同意退货
            </button>
            <button class="btn btn-outline" :disabled="actingId === o.id" @click="rejectAfterSale(o)">
              拒绝
            </button>
          </div>
          <div v-else-if="o.status === 'return_shipping'" class="actions">
            <span class="muted text-sm">已同意退货，等待买家寄回</span>
          </div>
          <div v-else-if="o.status === 'return_sent'" class="actions">
            <span class="muted text-sm">退货运单号：{{ o.returnTrackingNumber || '—' }}</span>
            <button class="btn btn-primary" :disabled="actingId === o.id" @click="confirmReturn(o)">
              确认收货并退款
            </button>
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
.link-btn {
  border: none;
  background: none;
  color: var(--accent-deep);
  font-size: 12px;
  cursor: pointer;
  padding: 0 4px;
  text-decoration: underline;
}
.kpi-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 22px;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.kpi {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 14px 16px;
}
.kpi-label {
  font-size: 12.5px;
  color: var(--muted);
}
.kpi-value {
  font-size: 24px;
  font-weight: 800;
  margin-top: 6px;
  color: var(--ink);
}
.kpi-value.accent {
  color: var(--accent-deep);
}
.kpi-sub {
  font-size: 11.5px;
  color: var(--muted);
  margin-top: 4px;
}
.trend-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 14px;
}
.bars {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 120px;
}
.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
  gap: 6px;
  min-width: 0;
}
.bar {
  width: 100%;
  max-width: 26px;
  min-height: 3px;
  border-radius: 6px 6px 2px 2px;
  background: linear-gradient(180deg, var(--accent), var(--accent-deep));
  transition: height 0.3s ease;
}
.bar-x {
  font-size: 10px;
  color: var(--muted);
}
</style>
