<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import { formatPrice, isAuthFailure, isTransient } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import AdminShell from '@/components/AdminShell.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const products = ref([])
const loading = ref(true)
const tab = ref('pending')
const rejecting = ref(null) // 待驳回的商品
const rejectReason = ref('')

const STATUS_META = {
  pending: { label: '待审核', type: 'warning' },
  rejected: { label: '已驳回', type: 'danger' },
  active: { label: '在售', type: 'success' },
  sold: { label: '已售出', type: 'info' },
  inactive: { label: '已下架', type: 'info' }
}

const TABS = [
  { key: 'pending', label: '待审核' },
  { key: 'active', label: '在售' },
  { key: 'inactive', label: '已下架' },
  { key: 'rejected', label: '已驳回' },
  { key: 'sold', label: '已售出' },
  { key: 'all', label: '全部' }
]

const shown = computed(() =>
  tab.value === 'all' ? products.value : products.value.filter((p) => p.status === tab.value)
)

const pendingCount = computed(() => products.value.filter((p) => p.status === 'pending').length)

async function load() {
  if (store.user?.role !== 'admin') {
    router.replace('/admin')
    return
  }
  loading.value = true
  const d = await adminApi.products()
  if (d.success) {
    // 后端已排除回收站(deleted)；前端再兜底过滤，避免删除后残留
    products.value = (d.products || []).filter((p) => p.status !== 'deleted')
  } else if (isAuthFailure(d)) {
    store.setUser(null)
    router.replace('/admin')
  } else if (!isTransient(d)) {
    showToast(d.message || '加载商品失败', 'error')
  } else {
    showToast('网络异常，已保留当前数据', 'warning')
  }
  loading.value = false
}

async function removeProduct(p) {
  const ok = await confirmDialog({
    title: '删除商品',
    message: `确定删除「${p.name}」吗？将移入回收站，可稍后恢复。`,
    okText: '移入回收站',
    type: 'warning'
  })
  if (!ok) return
  const d = await adminApi.deleteProduct(p.id)
  if (d.success) {
    showToast(d.message || '已移入回收站')
    load()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

async function offlineProduct(p) {
  const ok = await confirmDialog({
    title: '下架商品',
    message: `确定下架「${p.name}」吗？下架后前台不可见。`,
    okText: '下架',
    type: 'warning'
  })
  if (!ok) return
  const d = await adminApi.offlineProduct(p.id)
  if (d.success) {
    showToast('已下架')
    load()
  } else {
    showToast(d.message || '下架失败', 'error')
  }
}

async function onlineProduct(p) {
  const d = await adminApi.onlineProduct(p.id)
  if (d.success) {
    showToast('已上架')
    load()
  } else {
    showToast(d.message || '上架失败', 'error')
  }
}

async function approveProduct(p) {
  const ok = await confirmDialog({
    title: '审核通过',
    message: `通过「${p.name}」并立即上架销售？`,
    okText: '通过并上架',
    type: 'info'
  })
  if (!ok) return
  const d = await adminApi.approveProduct(p.id)
  if (d.success) {
    showToast('已通过审核并上架')
    load()
  } else {
    showToast(d.message || '审核失败', 'error')
  }
}

function openReject(p) {
  rejecting.value = p
  rejectReason.value = ''
}

async function submitReject() {
  if (!rejectReason.value.trim()) {
    showToast('请填写驳回理由，卖家需要据此修改', 'warning')
    return
  }
  const d = await adminApi.rejectProduct(rejecting.value.id, rejectReason.value.trim())
  if (d.success) {
    showToast('已驳回并通知卖家')
    rejecting.value = null
    load()
  } else {
    showToast(d.message || '驳回失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <AdminShell active="products">
    <template #title>商品管理</template>

    <div class="tabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        type="button"
        class="tab"
        :class="{ active: tab === t.key }"
        @click="tab = t.key"
      >
        {{ t.label }}
        <span v-if="t.key === 'pending' && pendingCount" class="dot">{{ pendingCount }}</span>
      </button>
    </div>

    <div v-if="rejecting" class="card reject-card">
      <h3 class="h3 mb-3">驳回「{{ rejecting.name }}」</h3>
      <p class="hint">理由会通知卖家，卖家修改后可重新提交审核。</p>
      <textarea
        v-model="rejectReason"
        class="form-textarea"
        rows="3"
        maxlength="255"
        placeholder="如：商品图片含联系方式，请删除水印后重新提交"
      ></textarea>
      <div class="actions">
        <button type="button" class="btn btn-outline" @click="rejecting = null">取消</button>
        <button type="button" class="btn btn-danger" @click="submitReject">确认驳回</button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="shown" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">
            <span style="color: var(--accent-deep); font-weight: 700">
              ¥{{ formatPrice(row.secondHandPrice) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="sellerName" label="卖家" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.status]?.type || 'info'" effect="light">
              {{ STATUS_META[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.reviewNote" class="note">{{ row.reviewNote }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'pending' || row.status === 'rejected'">
              <el-button type="success" size="small" @click="approveProduct(row)">通过</el-button>
              <el-button type="danger" size="small" @click="openReject(row)">驳回</el-button>
            </template>
            <el-button
              v-else-if="row.status === 'active'"
              size="small"
              @click="offlineProduct(row)"
            >下架</el-button>
            <el-button
              v-else-if="row.status === 'inactive'"
              type="success"
              size="small"
              @click="onlineProduct(row)"
            >上架</el-button>
            <el-button type="danger" size="small" @click="removeProduct(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无商品" />
        </template>
      </el-table>
    </el-card>
  </AdminShell>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.tab {
  position: relative;
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  font-size: 13px;
  color: var(--ink-soft);
  transition: all 0.15s ease;
}
.tab:hover {
  border-color: var(--accent);
  color: var(--accent-deep);
}
.tab.active {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  border-color: transparent;
  color: #fff;
}
.dot {
  display: inline-block;
  min-width: 18px;
  padding: 0 5px;
  margin-left: 6px;
  border-radius: 999px;
  background: #e5484d;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}
.tab.active .dot {
  background: rgba(255, 255, 255, 0.3);
}
.reject-card {
  padding: 18px;
  margin-bottom: 16px;
}
.hint {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--muted);
}
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  font-size: 13.5px;
  resize: vertical;
}
.actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 12px;
}
.note {
  color: #c2410c;
  font-size: 12.5px;
}
.muted {
  color: var(--muted);
}
</style>
