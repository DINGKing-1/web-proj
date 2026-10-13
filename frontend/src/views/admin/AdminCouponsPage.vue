<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { couponApi } from '@/api'
import { formatPrice, isAuthFailure, isTransient, formatTime } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import AdminShell from '@/components/AdminShell.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const items = ref([])
const loading = ref(true)
const editing = ref(null) // null | 'new' | id
const saving = ref(false)
const form = ref({})

const EMPTY = () => ({
  code: '',
  name: '',
  discountAmount: 10,
  minAmount: 100,
  totalQuantity: 0,
  perUserLimit: 1,
  startAt: '2026-10-01T00:00',
  endAt: '2026-12-31T23:59',
  enabled: true
})

async function load() {
  loading.value = true
  const d = await couponApi.adminList()
  if (d.success) {
    items.value = d.coupons || []
  } else if (isAuthFailure(d)) {
    store.setUser(null)
    router.replace('/admin')
  } else if (!isTransient(d)) {
    showToast(d.message || '加载失败', 'error')
  }
  loading.value = false
}

function openNew() {
  editing.value = 'new'
  form.value = EMPTY()
}

function openEdit(row) {
  editing.value = row.id
  form.value = {
    ...EMPTY(),
    ...row,
    startAt: toLocal(row.startAt),
    endAt: toLocal(row.endAt)
  }
}

/** 2026-10-04T18:00:00 → 2026-10-04T18:00（datetime-local 需要的格式） */
function toLocal(v) {
  if (!v) return ''
  return String(v).slice(0, 16)
}

function toIso(v) {
  if (!v) return null
  return v.length === 16 ? `${v}:00` : v
}

async function save() {
  if (saving.value) return
  if (!form.value.code?.trim() && editing.value === 'new') {
    showToast('请填写券码', 'warning')
    return
  }
  const payload = {
    ...form.value,
    code: (form.value.code || '').trim().toUpperCase(),
    discountAmount: Number(form.value.discountAmount),
    minAmount: Number(form.value.minAmount),
    totalQuantity: Number(form.value.totalQuantity) || 0,
    perUserLimit: Number(form.value.perUserLimit) || 1,
    startAt: toIso(form.value.startAt),
    endAt: toIso(form.value.endAt)
  }
  saving.value = true
  const d =
    editing.value === 'new'
      ? await couponApi.create(payload)
      : await couponApi.update(editing.value, payload)
  saving.value = false
  if (d.success) {
    showToast('已保存')
    editing.value = null
    load()
  } else {
    showToast(d.message || '保存失败', 'error')
  }
}

async function remove(row) {
  const ok = await confirmDialog({
    title: '删除优惠券',
    message: `确定删除「${row.name}」吗？已发放的券会自动转为停用。`,
    okText: '删除',
    type: 'warning'
  })
  if (!ok) return
  const d = await couponApi.remove(row.id)
  if (d.success) {
    showToast(d.message || '已删除')
    load()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

async function toggle(row) {
  const d = await couponApi.update(row.id, { enabled: !row.enabled })
  if (d.success) {
    row.enabled = !row.enabled
    showToast(row.enabled ? '已启用' : '已停用')
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <AdminShell active="coupons">
    <template #title>优惠券</template>
    <p class="hint">用户在「个人中心 → 优惠券」领取，支付订单时核销；订单取消或全额退款会自动退券。</p>

    <div class="toolbar">
      <button type="button" class="btn btn-primary" @click="openNew">新建优惠券</button>
    </div>

    <div v-if="editing !== null" class="card form-card">
      <h3 class="h3 mb-3">{{ editing === 'new' ? '新建优惠券' : '编辑优惠券' }}</h3>
      <div class="row">
        <div class="form-group">
          <label>券码（唯一）</label>
          <input v-model="form.code" class="form-input" :disabled="editing !== 'new'" placeholder="如 SUMMER10" />
        </div>
        <div class="form-group">
          <label>名称</label>
          <input v-model="form.name" class="form-input" maxlength="100" placeholder="如 满100减10" />
        </div>
      </div>
      <div class="row">
        <div class="form-group">
          <label>立减金额（元）</label>
          <input v-model.number="form.discountAmount" type="number" min="0.01" step="0.01" class="form-input" />
        </div>
        <div class="form-group">
          <label>使用门槛（元，0 表示无门槛）</label>
          <input v-model.number="form.minAmount" type="number" min="0" step="0.01" class="form-input" />
        </div>
      </div>
      <div class="row">
        <div class="form-group">
          <label>发放总量（0 = 不限量）</label>
          <input v-model.number="form.totalQuantity" type="number" min="0" class="form-input" />
        </div>
        <div class="form-group">
          <label>每人限领</label>
          <input v-model.number="form.perUserLimit" type="number" min="1" class="form-input" />
        </div>
      </div>
      <div class="row">
        <div class="form-group">
          <label>开始时间</label>
          <input v-model="form.startAt" type="datetime-local" class="form-input" />
        </div>
        <div class="form-group">
          <label>结束时间</label>
          <input v-model="form.endAt" type="datetime-local" class="form-input" />
        </div>
      </div>
      <label class="check-line">
        <input v-model="form.enabled" type="checkbox" />
        启用（停用后不再可领取）
      </label>
      <div class="actions">
        <button type="button" class="btn btn-outline" @click="editing = null">取消</button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </div>
    </div>

    <div class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>券码</th>
            <th>名称</th>
            <th>优惠</th>
            <th>门槛</th>
            <th>已领/总量</th>
            <th>有效期</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td><code>{{ row.code }}</code></td>
            <td>{{ row.name }}</td>
            <td class="off">-¥{{ formatPrice(row.discountAmount) }}</td>
            <td>{{ Number(row.minAmount) > 0 ? `满¥${formatPrice(row.minAmount)}` : '无门槛' }}</td>
            <td>{{ row.issuedCount }} / {{ Number(row.totalQuantity) > 0 ? row.totalQuantity : '不限' }}</td>
            <td>{{ formatTime(row.startAt) }} ~ {{ formatTime(row.endAt) }}</td>
            <td>
              <span class="badge" :class="row.enabled ? 'badge-success' : 'badge-muted'">
                {{ row.enabled ? '启用' : '停用' }}
              </span>
            </td>
            <td>
              <button type="button" class="btn btn-ghost btn-sm" @click="openEdit(row)">编辑</button>
              <button type="button" class="btn btn-ghost btn-sm" @click="toggle(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </button>
              <button type="button" class="btn btn-danger btn-sm" @click="remove(row)">删除</button>
            </td>
          </tr>
          <tr v-if="!items.length && !loading">
            <td colspan="8" class="muted center">暂无优惠券</td>
          </tr>
        </tbody>
      </table>
    </div>
  </AdminShell>
</template>

<style scoped>
.hint {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--muted);
}
.toolbar {
  margin-bottom: 14px;
}
.form-card {
  padding: 18px;
  margin-bottom: 16px;
}
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.check-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0 16px;
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
.off {
  color: var(--danger);
  font-weight: 700;
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
