<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, exportApi } from '@/api'
import { isAuthFailure, isTransient, formatTime } from '@/api/client'
import { showToast } from '@/composables/ui'
import AdminShell from '@/components/AdminShell.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const logs = ref([])
const loading = ref(true)
const total = ref(0)
const totalPages = ref(0)
const page = ref(0)
const operator = ref('')
const size = 50

async function load() {
  if (store.user?.role !== 'admin') {
    router.replace('/admin')
    return
  }
  loading.value = true
  const d = await adminApi.auditLogs(page.value, size, operator.value.trim())
  if (d.success) {
    logs.value = d.logs || []
    total.value = d.total || 0
    totalPages.value = d.totalPages || 0
  } else if (isAuthFailure(d)) {
    store.setUser(null)
    router.replace('/admin')
  } else if (!isTransient(d)) {
    showToast(d.message || '加载失败', 'error')
  }
  loading.value = false
}

function search() {
  page.value = 0
  load()
}

function go(delta) {
  const next = page.value + delta
  if (next < 0 || next >= totalPages.value) return
  page.value = next
  load()
}

onMounted(load)
</script>

<template>
  <AdminShell active="audit">
    <template #title>审计日志</template>

    <div class="card pad export-bar">
      <div>
        <h3 class="h3">数据导出</h3>
        <p class="muted text-sm">CSV 格式（带 BOM），Excel 可直接打开中文不乱码。</p>
      </div>
      <div class="export-actions">
        <a class="btn btn-outline btn-sm" :href="exportApi.orders()">导出订单</a>
        <a class="btn btn-outline btn-sm" :href="exportApi.users()">导出用户</a>
        <a class="btn btn-outline btn-sm" :href="exportApi.wallet()">导出资金流水</a>
      </div>
    </div>

    <div class="toolbar">
      <input
        v-model="operator"
        class="form-input search"
        placeholder="按操作人筛选，如 admin"
        @keyup.enter="search"
      />
      <button type="button" class="btn btn-primary" @click="search">查询</button>
      <span class="muted text-sm">共 {{ total }} 条</span>
    </div>

    <div class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>时间</th>
            <th>操作人</th>
            <th>动作</th>
            <th>对象</th>
            <th>详情</th>
            <th>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in logs" :key="row.id">
            <td>{{ formatTime(row.createdAt) }}</td>
            <td>{{ row.operator }}</td>
            <td>{{ row.action }}</td>
            <td>
              <span v-if="row.targetType" class="tag">{{ row.targetType }}</span>
              <span v-if="row.targetId" class="muted">#{{ row.targetId }}</span>
            </td>
            <td class="detail" :title="row.detail">{{ row.detail || '—' }}</td>
            <td>
              <span class="badge" :class="row.success ? 'badge-success' : 'badge-danger'">
                {{ row.success ? '成功' : '失败' }}
              </span>
            </td>
          </tr>
          <tr v-if="!logs.length && !loading">
            <td colspan="6" class="muted center">暂无审计记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="totalPages > 1" class="pager">
      <button type="button" class="btn btn-outline btn-sm" :disabled="page === 0" @click="go(-1)">
        上一页
      </button>
      <span class="muted text-sm">{{ page + 1 }} / {{ totalPages }}</span>
      <button
        type="button"
        class="btn btn-outline btn-sm"
        :disabled="page >= totalPages - 1"
        @click="go(1)"
      >
        下一页
      </button>
    </div>
  </AdminShell>
</template>

<style scoped>
.pad {
  padding: 16px 18px;
}
.export-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.export-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}
.search {
  max-width: 260px;
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
  padding: 11px 14px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  font-size: 13px;
  vertical-align: top;
}
.table th {
  color: var(--muted);
  font-weight: 600;
  background: var(--surface-warm);
}
.detail {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink-soft);
}
.tag {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 6px;
  background: var(--accent-soft, #f7ecdc);
  color: var(--accent-deep, #9a5b1f);
  font-size: 12px;
  margin-right: 6px;
}
.center {
  text-align: center;
}
.pager {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: center;
  margin-top: 16px;
}
</style>
