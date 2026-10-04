<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import { formatTime, isAuthFailure, isTransient } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import AdminShell from '@/components/AdminShell.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const reports = ref([])
const loading = ref(true)

const STATUS = {
  pending: { label: '待处理', tone: 'warning' },
  resolved: { label: '已处理', tone: 'success' },
  rejected: { label: '已驳回', tone: 'muted' }
}

const ACTION_LABEL = {
  none: '仅标记处理',
  offline: '下架商品',
  delete: '商品移入回收站'
}

/** 每条举报选定的处置动作，默认仅标记 */
const actions = ref({})

async function load() {
  loading.value = true
  const d = await adminApi.reports()
  if (d.success) {
    reports.value = d.reports || []
    reports.value.forEach((r) => {
      if (!actions.value[r.id]) actions.value[r.id] = 'none'
    })
  } else if (isAuthFailure(d)) {
    store.setUser(null)
    router.replace('/admin')
  } else if (!isTransient(d)) {
    showToast(d.message || '举报加载失败', 'error')
  }
  loading.value = false
}

async function resolve(r, status) {
  const action = status === 'resolved' ? (actions.value[r.id] || 'none') : 'none'
  const ok = await confirmDialog({
    title: status === 'resolved' ? '采纳举报' : '驳回举报',
    message:
      status === 'resolved'
        ? action === 'none'
          ? '确认处理该举报？（仅标记，不改动商品）'
          : `确认采纳举报并「${ACTION_LABEL[action]}」？商品状态会立即变更。`
        : '确认驳回该举报？',
    okText: '确定',
    type: 'warning'
  })
  if (!ok) return
  const d = await adminApi.resolveReport(r.id, status, action)
  if (d.success) {
    showToast('已更新')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <AdminShell active="reports">
    <template #title>举报处理</template>
    <LoadingBlock v-if="loading" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>商品</th>
            <th>举报人</th>
            <th>原因</th>
            <th>说明</th>
            <th>状态</th>
            <th>处置</th>
            <th>时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in reports" :key="r.id">
            <td>
              <router-link :to="`/product/${r.productId}`" class="link">
                {{ r.productName || `#${r.productId}` }}
              </router-link>
            </td>
            <td>{{ r.reporterUsername }}</td>
            <td>{{ r.reason }}</td>
            <td class="muted">{{ r.detail || '—' }}</td>
            <td>
              <span class="badge" :class="`badge-${STATUS[r.status]?.tone || 'muted'}`">
                {{ STATUS[r.status]?.label || r.status }}
              </span>
            </td>
            <td class="muted text-sm">
              <template v-if="r.status === 'pending'">—</template>
              <template v-else>
                {{ ACTION_LABEL[r.action] || '仅标记处理' }}
                <div class="muted text-xs">{{ r.handledBy || '' }} {{ formatTime(r.handledAt) }}</div>
              </template>
            </td>
            <td class="muted text-sm">{{ formatTime(r.createdAt) }}</td>
            <td>
              <template v-if="r.status === 'pending'">
                <select v-model="actions[r.id]" class="action-select" @click.stop>
                  <option value="none">仅标记处理</option>
                  <option value="offline">下架商品</option>
                  <option value="delete">移入回收站</option>
                </select>
                <button class="btn btn-primary btn-sm" @click="resolve(r, 'resolved')">采纳</button>
                <button class="btn btn-outline btn-sm" @click="resolve(r, 'rejected')">驳回</button>
              </template>
              <span v-else class="muted text-sm">—</span>
            </td>
          </tr>
          <tr v-if="!reports.length">
            <td colspan="8" class="muted center">暂无举报</td>
          </tr>
        </tbody>
      </table>
    </div>
  </AdminShell>
</template>

<style scoped>
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
}
.table th {
  color: var(--muted);
  font-weight: 600;
  background: var(--surface-warm);
}
.center {
  text-align: center;
}
.link {
  color: var(--accent-deep);
}
.action-select {
  margin-right: 6px;
  padding: 5px 6px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  font-size: 12.5px;
}
.text-xs {
  font-size: 11px;
}
</style>
