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
const products = ref([])
const loading = ref(true)

async function load() {
  if (store.user?.role !== 'admin') {
    router.replace('/admin')
    return
  }
  loading.value = true
  const d = await adminApi.products()
  if (d.success) {
    products.value = d.products || []
  } else {
    store.setUser(null)
    router.replace('/admin')
  }
  loading.value = false
}

async function removeProduct(p) {
  const ok = await confirmDialog({
    title: '删除商品',
    message: `确定删除「${p.name}」吗？删除后不可恢复。`,
    okText: '删除',
    type: 'warning'
  })
  if (!ok) return
  const d = await adminApi.deleteProduct(p.id)
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
  <AdminShell active="products">
    <template #title>商品管理</template>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="products" stripe style="width: 100%">
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
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'sold' ? 'danger' : 'success'" effect="light">
              {{ row.status === 'sold' ? '已售出' : '在售' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
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
