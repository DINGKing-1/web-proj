<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import { formatPrice } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import AdminShell from '@/components/AdminShell.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const users = ref([])
const loading = ref(true)

async function load() {
  if (store.user?.role !== 'admin') {
    router.replace('/admin')
    return
  }
  loading.value = true
  const d = await adminApi.users()
  if (d.success) {
    users.value = d.users || []
  } else {
    store.setUser(null)
    router.replace('/admin')
  }
  loading.value = false
}

async function removeUser(u) {
  const ok = await confirmDialog({
    title: '删除用户',
    message: `确定删除用户「${u.username}」吗？相关数据可能受影响。`,
    okText: '删除',
    type: 'warning'
  })
  if (!ok) return
  const d = await adminApi.deleteUser(u.id)
  if (d.success) {
    showToast('删除成功')
    load()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <AdminShell active="users">
    <template #title>用户管理</template>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="users" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column label="昵称" width="140">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column label="余额" width="120">
          <template #default="{ row }">¥{{ formatPrice(row.balance) }}</template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'warning' : 'info'" effect="light">
              {{ row.role === 'admin' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="removeUser(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户" />
        </template>
      </el-table>
    </el-card>
  </AdminShell>
</template>
