<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { announcementApi, uploadApi } from '@/api'
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
const uploadingImg = ref(false)
const imgInput = ref(null)

/** 上传公告图（复用商品图上传接口），成功后回填 imageUrl */
async function onAnnImageChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || uploadingImg.value) return
  if (!file.type.startsWith('image/')) {
    showToast('请选择图片文件', 'warning')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    showToast('图片不能超过 10MB', 'warning')
    return
  }
  uploadingImg.value = true
  try {
    const dataUrl = await new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result)
      reader.onerror = () => reject(new Error('read-failed'))
      reader.readAsDataURL(file)
    })
    const d = await uploadApi.images([dataUrl])
    if (d.success && d.imageUrls?.length) {
      form.value.imageUrl = d.imageUrls[0]
      showToast('图片已上传')
    } else {
      showToast(d.message || '图片上传失败', 'error')
    }
  } catch {
    showToast('图片读取失败，请重试', 'error')
  } finally {
    uploadingImg.value = false
  }
}
const form = ref({ title: '', content: '', type: 'notice', imageUrl: '', linkUrl: '', sortOrder: 0, enabled: true })

async function load() {
  loading.value = true
  const d = await announcementApi.adminList()
  if (d.success) {
    items.value = d.announcements || []
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
  form.value = { title: '', content: '', type: 'notice', imageUrl: '', linkUrl: '', sortOrder: 0, enabled: true }
}

function openEdit(row) {
  editing.value = row.id
  form.value = {
    title: row.title || '',
    content: row.content || '',
    type: row.type || 'notice',
    imageUrl: row.imageUrl || '',
    linkUrl: row.linkUrl || '',
    sortOrder: row.sortOrder ?? 0,
    enabled: !!row.enabled
  }
}

async function save() {
  if (saving.value) return
  if (!form.value.title.trim()) {
    showToast('请填写标题', 'warning')
    return
  }
  const payload = { ...form.value, sortOrder: Number(form.value.sortOrder) || 0 }
  saving.value = true
  const d =
    editing.value === 'new'
      ? await announcementApi.create(payload)
      : await announcementApi.update(editing.value, payload)
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
    title: '删除公告',
    message: `确定删除「${row.title}」吗？`,
    okText: '删除',
    type: 'warning'
  })
  if (!ok) return
  const d = await announcementApi.remove(row.id)
  if (d.success) {
    showToast('已删除')
    load()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

async function toggle(row) {
  const d = await announcementApi.update(row.id, { enabled: !row.enabled })
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
  <AdminShell active="announcements">
    <template #title>公告管理</template>
    <div class="toolbar">
      <button type="button" class="btn btn-primary" @click="openNew">新建公告</button>
    </div>

    <div v-if="editing !== null" class="card form-card">
      <h3 class="h3 mb-3">{{ editing === 'new' ? '新建公告' : '编辑公告' }}</h3>
      <div class="form-group">
        <label>标题</label>
        <input v-model="form.title" class="form-input" maxlength="100" />
      </div>
      <div class="form-group">
        <label>内容</label>
        <textarea v-model="form.content" class="form-textarea" rows="3" maxlength="500"></textarea>
      </div>
      <div class="row">
        <div class="form-group">
          <label>类型</label>
          <select v-model="form.type" class="form-input">
            <option value="notice">公告</option>
            <option value="image">图片推荐</option>
          </select>
        </div>
        <div class="form-group">
          <label>排序（小的在前）</label>
          <input v-model.number="form.sortOrder" type="number" class="form-input" />
        </div>
      </div>
      <div class="form-group">
        <label>图片 URL（图片推荐用）</label>
        <div class="code-row">
          <input v-model="form.imageUrl" class="form-input" placeholder="/product-images/..." />
          <input
            ref="imgInput"
            type="file"
            accept="image/*"
            style="display: none"
            @change="onAnnImageChange"
          />
          <button type="button" class="btn btn-outline" :disabled="uploadingImg" @click="imgInput?.click()">
            {{ uploadingImg ? '上传中…' : '上传图片' }}
          </button>
        </div>
      </div>
      <div class="form-group">
        <label>跳转链接（站内路径，选填）</label>
        <input v-model="form.linkUrl" class="form-input" placeholder="/category/手机" />
      </div>
      <label class="check-line">
        <input v-model="form.enabled" type="checkbox" />
        启用
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
            <th>标题</th>
            <th>类型</th>
            <th>排序</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.id }}</td>
            <td>{{ row.title }}</td>
            <td>{{ row.type === 'image' ? '图片推荐' : '公告' }}</td>
            <td>{{ row.sortOrder }}</td>
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
            <td colspan="6" class="muted center">暂无公告</td>
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
.code-row {
  display: flex;
  gap: 8px;
}
.code-row .form-input {
  flex: 1;
}
.code-row .btn {
  flex-shrink: 0;
  white-space: nowrap;
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
