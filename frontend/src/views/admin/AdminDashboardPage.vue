<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import VChart from '@/components/chart.js'
import AdminShell from '@/components/AdminShell.vue'

const router = useRouter()
const stats = reactive({ totalProducts: 0, totalUsers: 0, totalComments: 0 })
const products = ref([])
const loading = ref(true)

const categoryData = computed(() => {
  const map = {}
  products.value.forEach((p) => {
    const key = p.category || '其他'
    map[key] = (map[key] || 0) + 1
  })
  return Object.entries(map).map(([name, value]) => ({ name, value }))
})

const statusData = computed(() => {
  let sold = 0
  let onSale = 0
  products.value.forEach((p) => {
    if (p.status === 'sold') sold += 1
    else onSale += 1
  })
  return [
    { name: '在售', value: onSale },
    { name: '已售出', value: sold }
  ]
})

const pieOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, icon: 'circle' },
  color: ['#d4883c', '#e0a66a', '#2f9e62', '#3b82c4', '#b86f28', '#8a7a6b', '#d4a017', '#d64545', '#5c4d40'],
  series: [
    {
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '46%'],
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{c}' },
      data: categoryData.value
    }
  ]
}))

const barOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 16, top: 24, bottom: 32 },
  xAxis: {
    type: 'category',
    data: statusData.value.map((x) => x.name),
    axisLine: { lineStyle: { color: '#e0d0bc' } }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#f2ebe3' } }
  },
  series: [
    {
      type: 'bar',
      barWidth: 36,
      itemStyle: {
        borderRadius: [8, 8, 0, 0],
        color: (p) => (p.dataIndex === 0 ? '#d4883c' : '#2f9e62')
      },
      data: statusData.value.map((x) => x.value)
    }
  ]
}))

const topProducts = computed(() =>
  [...products.value]
    .sort((a, b) => Number(b.secondHandPrice || 0) - Number(a.secondHandPrice || 0))
    .slice(0, 5)
)

async function load() {
  if (!sessionStorage.getItem('adminUser')) {
    router.replace('/admin')
    return
  }
  loading.value = true
  const [s, p] = await Promise.all([adminApi.stats(), adminApi.products()])
  if (s.success) {
    stats.totalProducts = s.totalProducts || 0
    stats.totalUsers = s.totalUsers || 0
    stats.totalComments = s.totalComments || 0
    products.value = p.success ? p.products || [] : []
  } else {
    sessionStorage.removeItem('adminUser')
    router.replace('/admin')
  }
  loading.value = false
}

onMounted(load)
</script>

<template>
  <AdminShell active="dashboard">
    <template #title>仪表盘</template>

    <el-row :gutter="16" class="mb">
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat">
          <div class="label">商品总数</div>
          <div class="value">{{ stats.totalProducts }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat">
          <div class="label">注册用户</div>
          <div class="value">{{ stats.totalUsers }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat">
          <div class="label">评论总数</div>
          <div class="value">{{ stats.totalComments }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header>商品分类分布</template>
          <div v-if="loading" class="chart-loading">加载中…</div>
          <VChart v-else class="chart" :option="pieOption" autoresize />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header>在售 / 已售出</template>
          <div v-if="loading" class="chart-loading">加载中…</div>
          <VChart v-else class="chart" :option="barOption" autoresize />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt">
      <template #header>高价商品 TOP5</template>
      <el-table :data="topProducts" size="small" stripe>
        <el-table-column prop="name" label="商品" min-width="180" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">
            <span style="color: var(--accent-deep); font-weight: 700">¥{{ row.secondHandPrice }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sellerName" label="卖家" width="120" />
      </el-table>
    </el-card>
  </AdminShell>
</template>

<style scoped>
.stat {
  margin-bottom: 16px;
}
.label {
  color: var(--muted);
  font-size: 13px;
}
.value {
  font-size: 32px;
  font-weight: 700;
  color: var(--accent-deep);
  margin-top: 8px;
}
.chart-card {
  margin-bottom: 16px;
}
.chart {
  height: 300px;
  width: 100%;
}
.chart-loading {
  height: 300px;
  display: grid;
  place-items: center;
  color: var(--muted);
}
.mb {
  margin-bottom: 4px;
}
.mt {
  margin-top: 8px;
}
</style>
