<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { categoryApi } from '@/api'
import { isAuthFailure, isTransient } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import AdminShell from '@/components/AdminShell.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const items = ref([])
const loading = ref(true)
const editing = ref(null) // null | 'new' | id
const saving = ref(false)
const form = ref({ name: '', sort: 0, enabled: true })

async function load() {
  loading.value = true
  const d = await categoryApi.adminList()
  if (d.success) {
    items.value = d.categories || []
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
  form.value = { name: '', sort: (items.value.at(-1)?.sort ?? 0) + 1, enabled: true }
}

function openEdit(row) {
  editing.value = row.id
  form.value = { name: row.name || '', sort: row.sort ?? 0, enabled: !!row.enabled }
}

async function save() {
  if (saving.value) return
  if (!form.value.name.trim()) {
    showToast('请填写分类名称', 'warning')
    return
  }
  const payload = { ...form.value, sort: Number(form.value.sort) || 0 }
  saving.value = true
  const d =
    editing.value === 'new'
      ? await categoryApi.create(payload)
      : await categoryApi.update(editing.value, payload)
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
    title: '删除分类',
    message: `确定删除「${row.name}」吗？`,
    okText: '删除',
    type: 'warning'
  })
  if (!ok) return
  const d = await categoryApi.remove(row.id)
  if (d.success) {
    showToast('已删除')
    load()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

async function toggle(row) {
  const d = await categoryApi.update(row.id, { name: row.name, sort: row.sort, enabled: !row.enabled })
  if (d.success) {
    row.enabled = !row.enabled
    showToast(row.enabled ? '已启用' : '已停用')
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function move(row, delta) {
  const target = Number(row.sort) + delta
  if (target < 0) return
  const d = await categoryApi.update(row.id, { name: row.name, sort: target, enabled: row.enabled })
  if (d.success) load()
  else showToast(d.message || '排序失败', 'error')
}

onMounted(load)
</script>

<template>
  <AdminShell active="categories">
    <template #title>分类管理</template>
    <p class="hint">前台分类导航与筛选都读这里；改名会同步改写存量商品，分类下还有商品时不能删除。</p>

    <div class="toolbar">
      <button type="button" class="btn btn-primary" @click="openNew">新建分类</button>
    </div>

    <div v-if="editing !== null" class="card form-card">
      <h3 class="h3 mb-3">{{ editing === 'new' ? '新建分类' : '编辑分类' }}</h3>
      <div class="row">
        <div class="form-group">
          <label>名称</label>
          <input v-model="form.name" class="form-input" maxlength="100" placeholder="如：手机" />
        </div>
        <div class="form-group">
          <label>排序（小的在前）</label>
          <input v-model.number="form.sort" type="number" class="form-input" />
        </div>
      </div>
      <label class="check-line">
        <input v-model="form.enabled" type="checkbox" />
        启用（停用后前台不再展示，商品保留原分类）
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
            <th>ID</th>
            <th>名称</th>
            <th>商品数</th>
            <th>排序</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.id }}</td>
            <td>{{ row.name }}</td>
            <td>{{ row.productCount }}</td>
            <td>{{ row.sort }}</td>
            <td>
              <span class="badge" :class="row.enabled ? 'badge-success' : 'badge-muted'">
                {{ row.enabled ? '启用' : '停用' }}
              </span>
            </td>
            <td>
              <button type="button" class="btn btn-ghost btn-sm" @click="move(row, -1)">上移</button>
              <button type="button" class="btn btn-ghost btn-sm" @click="move(row, 1)">下移</button>
              <button type="button" class="btn btn-ghost btn-sm" @click="openEdit(row)">编辑</button>
              <button type="button" class="btn btn-ghost btn-sm" @click="toggle(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </button>
              <button type="button" class="btn btn-danger btn-sm" @click="remove(row)">删除</button>
            </td>
          </tr>
          <tr v-if="!items.length && !loading">
            <td colspan="6" class="muted center">暂无分类</td>
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
