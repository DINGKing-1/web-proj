<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import { formatPrice, formatTime, ORDER_STATUS, isAuthFailure, isTransient } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import AdminShell from '@/components/AdminShell.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const orders = ref([])
const loading = ref(true)
const statusFilter = ref('all')
const keyword = ref('')
const actingId = ref(null)

const filtered = computed(() => {
  return orders.value.filter((o) => {
    if (statusFilter.value !== 'all' && o.status !== statusFilter.value) return false
    const k = keyword.value.trim().toLowerCase()
    if (!k) return true
    return (
      String(o.orderNumber || '').includes(k) ||
      String(o.productName || '').toLowerCase().includes(k) ||
      String(o.username || '').toLowerCase().includes(k) ||
      String(o.sellerUsername || '').toLowerCase().includes(k)
    )
  })
})

function statusMeta(s) {
  return ORDER_STATUS[s] || { label: s, tone: 'muted' }
}

async function load() {
  loading.value = true
  const d = await adminApi.orders()
  if (d.success) {
    orders.value = d.orders || []
  } else if (isAuthFailure(d)) {
    store.setUser(null)
    router.replace('/admin')
  } else if (!isTransient(d)) {
    showToast(d.message || '订单加载失败', 'error')
  }
  loading.value = false
}

async function forceCancel(o) {
  const ok = await confirmDialog({
    title: '强制取消订单',
    message: `确定强制取消订单「${o.orderNumber || o.id}」并退款给买家吗？`,
    okText: '强制取消并退款',
    type: 'warning'
  })
  if (!ok) return
  actingId.value = o.id
  const d = await adminApi.forceCancelOrder(o.id)
  actingId.value = null
  if (d.success) {
    showToast(d.message || '已强制取消')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <AdminShell active="orders">
    <template #title>订单管理</template>
    <div class="toolbar">
      <div class="filters">
        <button
          v-for="t in [
            { key: 'all', label: '全部' },
            { key: 'unshipped', label: '待发货' },
            { key: 'shipped', label: '已发货' },
            { key: 'received', label: '待评价' },
            { key: 'after_sale', label: '售后中' },
            { key: 'return_shipping', label: '待寄回' },
            { key: 'return_sent', label: '退货已寄出' },
            { key: 'refunded', label: '已退款' },
            { key: 'completed', label: '已完成' },
            { key: 'cancelled', label: '已取消' }
          ]"
          :key="t.key"
          type="button"
          class="chip"
          :class="{ on: statusFilter === t.key }"
          @click="statusFilter = t.key"
        >
          {{ t.label }}
        </button>
      </div>
      <input v-model="keyword" class="form-input search" placeholder="搜索订单号 / 商品 / 用户" />
    </div>

    <LoadingBlock v-if="loading" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>商品</th>
            <th>买家</th>
            <th>卖家</th>
            <th>金额</th>
            <th>状态</th>
            <th>评分</th>
            <th>时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in filtered" :key="o.id">
            <td class="mono">{{ o.orderNumber || `#${o.id}` }}</td>
            <td>{{ o.productName }}</td>
            <td>{{ o.username }}</td>
            <td>{{ o.sellerUsername }}</td>
            <td>¥{{ formatPrice(Number(o.productPrice || 0) * Number(o.quantity || 1)) }}</td>
            <td>
              <span class="badge" :class="`badge-${statusMeta(o.status).tone}`">
                {{ statusMeta(o.status).label }}
              </span>
            </td>
            <td>{{ o.rating ? `${o.rating} 星` : '—' }}</td>
            <td class="muted text-sm">{{ formatTime(o.createdAt) }}</td>
            <td>
              <button
                v-if="o.status !== 'completed' && o.status !== 'cancelled'"
                type="button"
                class="btn btn-danger btn-sm"
                :disabled="actingId === o.id"
                @click="forceCancel(o)"
              >强制取消</button>
            </td>
          </tr>
          <tr v-if="!filtered.length">
            <td colspan="9" class="muted center">暂无订单</td>
          </tr>
        </tbody>
      </table>
    </div>
  </AdminShell>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: #fff;
  font-size: 13px;
}
.chip.on {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
.search {
  width: min(100%, 280px);
}
.table-wrap {
  overflow-x: auto;
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th,
.table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  font-size: 13px;
  white-space: nowrap;
}
.table th {
  color: var(--muted);
  font-weight: 600;
  background: var(--surface-warm);
}
.center {
  text-align: center;
}
</style>
