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
const tab = ref('products')
const products = ref([])
const users = ref([])
const loading = ref(true)

const list = computed(() => (tab.value === 'products' ? products.value : users.value))

async function load() {
  if (store.user?.role !== 'admin') {
    router.replace('/admin')
    return
  }
  loading.value = true
  const [pd, ud] = await Promise.all([adminApi.recycleProducts(), adminApi.recycleUsers()])
  if (pd.success) products.value = pd.products || []
  else if (isAuthFailure(pd)) {
    store.setUser(null)
    router.replace('/admin')
    loading.value = false
    return
  }
  if (ud.success) users.value = ud.users || []
  else if (isAuthFailure(ud)) {
    store.setUser(null)
    router.replace('/admin')
    loading.value = false
    return
  }
  if ((!pd.success && !isTransient(pd)) || (!ud.success && !isTransient(ud))) {
    showToast('回收站加载失败', 'error')
  }
  loading.value = false
}

async function restoreProduct(p) {
  const ok = await confirmDialog({
    title: '恢复商品',
    message: `确定恢复「${p.name}」吗？恢复后将重新在售。`,
    okText: '恢复',
    type: 'info'
  })
  if (!ok) return
  const d = await adminApi.restoreProduct(p.id)
  if (d.success) {
    showToast(d.message || '商品已恢复')
    load()
  } else {
    showToast(d.message || '恢复失败', 'error')
  }
}

async function purgeProduct(p) {
  const ok = await confirmDialog({
    title: '彻底删除',
    message: `确定彻底删除「${p.name}」吗？此操作不可恢复。`,
    okText: '彻底删除',
    type: 'warning'
  })
  if (!ok) return
  const d = await adminApi.purgeProduct(p.id)
  if (d.success) {
    showToast(d.message || '已彻底删除')
    load()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

async function restoreUser(u) {
  const ok = await confirmDialog({
    title: '恢复用户',
    message: `确定恢复用户「${u.username}」吗？其名下商品也会一并恢复。`,
    okText: '恢复',
    type: 'info'
  })
  if (!ok) return
  const d = await adminApi.restoreUser(u.id)
  if (d.success) {
    showToast(d.message || '用户已恢复')
    load()
  } else {
    showToast(d.message || '恢复失败', 'error')
  }
}

async function purgeUser(u) {
  const ok = await confirmDialog({
    title: '彻底删除',
    message: `确定彻底删除用户「${u.username}」吗？其业务数据将被清理，不可恢复。`,
    okText: '彻底删除',
    type: 'warning'
  })
  if (!ok) return
  const d = await adminApi.purgeUser(u.id)
  if (d.success) {
    showToast(d.message || '已彻底删除')
    load()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <AdminShell active="recycle">
    <template #title>回收站</template>
    <el-card shadow="never">
      <div class="recycle-head">
        <el-radio-group v-model="tab">
          <el-radio-button value="products">已删商品（{{ products.length }}）</el-radio-button>
          <el-radio-button value="users">已注销用户（{{ users.length }}）</el-radio-button>
        </el-radio-group>
        <p class="muted tip">删除的条目会先存放在此，可恢复或彻底清除。</p>
      </div>

      <!-- 商品 -->
      <el-table v-if="tab === 'products'" v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">¥{{ formatPrice(row.secondHandPrice) }}</template>
        </el-table-column>
        <el-table-column prop="sellerName" label="卖家" width="120" />
        <el-table-column label="删除时间" width="160">
          <template #default="{ row }">
            <span class="muted">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="restoreProduct(row)">恢复</el-button>
            <el-button type="danger" size="small" @click="purgeProduct(row)">彻底删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="回收站没有商品" />
        </template>
      </el-table>

      <!-- 用户 -->
      <el-table v-else v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column label="昵称" width="140">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag :type="row.deletedBy === 'self' ? 'info' : 'warning'" effect="light">
              {{ row.deletedBy === 'self' ? '自助注销' : '管理员删除' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="删除时间" width="160">
          <template #default="{ row }">
            <span class="muted">{{ formatTime(row.deletedAt) || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="restoreUser(row)">恢复</el-button>
            <el-button type="danger" size="small" @click="purgeUser(row)">彻底删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="回收站没有用户" />
        </template>
      </el-table>
    </el-card>
  </AdminShell>
</template>

<style scoped>
.recycle-head {
  margin-bottom: 16px;
}
.tip {
  margin: 10px 0 0;
  font-size: 13px;
}
</style>
