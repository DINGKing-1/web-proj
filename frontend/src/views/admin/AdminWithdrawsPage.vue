<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import { formatPrice, formatTime, isAuthFailure, isTransient } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import AdminShell from '@/components/AdminShell.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const items = ref([])
const loading = ref(true)
const statusFilter = ref('all')
const actingId = ref(null)

const STATUS = {
  pending: { label: '待审核', tone: 'warning' },
  approved: { label: '已通过', tone: 'success' },
  rejected: { label: '已驳回', tone: 'muted' }
}

const filtered = computed(() =>
  statusFilter.value === 'all'
    ? items.value
    : items.value.filter((w) => w.status === statusFilter.value)
)

async function load() {
  loading.value = true
  const d = await adminApi.withdraws()
  if (d.success) {
    items.value = d.withdraws || []
  } else if (isAuthFailure(d)) {
    store.setUser(null)
    router.replace('/admin')
  } else if (!isTransient(d)) {
    showToast(d.message || '提现加载失败', 'error')
  }
  loading.value = false
}

async function approve(w) {
  const ok = await confirmDialog({
    title: '通过提现',
    message: `确认通过「${w.username}」¥${w.amount} 的提现申请？请确保已完成线下打款。`,
    okText: '确认已打款',
    type: 'warning'
  })
  if (!ok) return
  actingId.value = w.id
  const d = await adminApi.approveWithdraw(w.id)
  actingId.value = null
  if (d.success) {
    showToast(d.message || '已通过')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function reject(w) {
  const input = window.prompt('驳回原因（将退回余额）', '信息有误')
  // prompt 返回 null = 管理员点了取消：必须放弃，不能落默认理由直接驳回
  if (input === null) return
  const reason = input.trim() || '审核未通过'
  actingId.value = w.id
  const d = await adminApi.rejectWithdraw(w.id, reason)
  actingId.value = null
  if (d.success) {
    showToast(d.message || '已驳回')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <AdminShell active="withdraws">
    <template #title>提现审核</template>
    <div class="toolbar">
      <div class="filters">
        <button
          v-for="t in [
            { key: 'all', label: '全部' },
            { key: 'pending', label: '待审核' },
            { key: 'approved', label: '已通过' },
            { key: 'rejected', label: '已驳回' }
          ]"
          :key="t.key"
          type="button"
          class="chip"
          :class="{ on: statusFilter === t.key }"
          @click="statusFilter = t.key"
        >{{ t.label }}</button>
      </div>
    </div>

    <div class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户</th>
            <th>金额</th>
            <th>收款账号</th>
            <th>状态</th>
            <th>申请时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="w in filtered" :key="w.id">
            <td>{{ w.id }}</td>
            <td>{{ w.username }}</td>
            <td>¥{{ formatPrice(w.amount) }}</td>
            <td>{{ w.account }}</td>
            <td>
              <span class="badge" :class="`badge-${STATUS[w.status]?.tone || 'muted'}`">
                {{ STATUS[w.status]?.label || w.status }}
              </span>
              <div v-if="w.rejectReason" class="muted text-xs">{{ w.rejectReason }}</div>
            </td>
            <td class="muted text-sm">{{ formatTime(w.createdAt) }}</td>
            <td>
              <template v-if="w.status === 'pending'">
                <button
                  type="button"
                  class="btn btn-primary btn-sm"
                  :disabled="actingId === w.id"
                  @click="approve(w)"
                >通过打款</button>
                <button
                  type="button"
                  class="btn btn-danger btn-sm"
                  :disabled="actingId === w.id"
                  @click="reject(w)"
                >驳回</button>
              </template>
            </td>
          </tr>
          <tr v-if="!filtered.length && !loading">
            <td colspan="7" class="muted center">暂无提现记录</td>
          </tr>
          <tr v-else-if="loading">
            <td colspan="7" class="muted center">加载中…</td>
          </tr>
        </tbody>
      </table>
    </div>
  </AdminShell>
</template>

<style scoped>
.toolbar {
  margin-bottom: 14px;
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
