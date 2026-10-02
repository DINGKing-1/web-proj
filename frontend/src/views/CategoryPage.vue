<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useWindowSize } from '@vueuse/core'
import { productApi } from '@/api'
import ProductCard from '@/components/ProductCard.vue'
import PaginationBar from '@/components/PaginationBar.vue'

const route = useRoute()
const { width } = useWindowSize()
const isNarrow = computed(() => width.value < 768)

const products = ref([])
const total = ref(0)
const totalPages = ref(0)
const page = ref(0)
const loading = ref(false)
const categoryName = ref('')
const filterVisible = ref(false)

const sortBy = ref('newest')
const minPrice = ref('')
const maxPrice = ref('')
const condition = ref('')

const CONDITIONS = ['全新', '几乎全新', '轻微使用', '明显使用', '重度使用']
const SORTS = [
  { label: '最新发布', value: 'newest' },
  { label: '价格从低到高', value: 'price-asc' },
  { label: '价格从高到低', value: 'price-desc' }
]

async function load(p = 0) {
  loading.value = true
  categoryName.value = route.params.name || '全部'
  const params = {
    page: p,
    size: 12,
    sortBy: sortBy.value || undefined,
    minPrice: minPrice.value === '' ? undefined : minPrice.value,
    maxPrice: maxPrice.value === '' ? undefined : maxPrice.value,
    condition: condition.value || undefined
  }
  if (categoryName.value && categoryName.value !== '全部') {
    params.category = categoryName.value
  }
  const d = await productApi.list(params)
  if (d.success) {
    products.value = d.products || []
    total.value = d.total || 0
    totalPages.value = d.totalPages || 0
    page.value = p
  }
  loading.value = false
}

function applyFilters() {
  filterVisible.value = false
  load(0)
}

function resetFilters() {
  sortBy.value = 'newest'
  minPrice.value = ''
  maxPrice.value = ''
  condition.value = ''
  load(0)
}

watch(
  () => route.params.name,
  () => {
    sortBy.value = 'newest'
    minPrice.value = ''
    maxPrice.value = ''
    condition.value = ''
    load(0)
  }
)

onMounted(() => load(0))
</script>

<template>
  <main class="page">
    <div class="container">
      <div class="page-head">
        <div>
          <h1 class="h1">{{ categoryName }}</h1>
          <p class="muted">共 {{ total }} 件商品</p>
        </div>
        <button class="btn btn-outline filter-btn" @click="filterVisible = true">
          <i class="fas fa-sliders"></i> 筛选
        </button>
      </div>

      <!-- 桌面筛选 -->
      <el-card v-if="!isNarrow" shadow="never" class="filter-panel">
        <el-form inline>
          <el-form-item label="排序">
            <el-select v-model="sortBy" style="width: 150px">
              <el-option v-for="s in SORTS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="最低价">
            <el-input v-model="minPrice" type="number" placeholder="不限" style="width: 110px" />
          </el-form-item>
          <el-form-item label="最高价">
            <el-input v-model="maxPrice" type="number" placeholder="不限" style="width: 110px" />
          </el-form-item>
          <el-form-item label="成色">
            <el-select v-model="condition" style="width: 140px">
              <el-option label="不限" value="" />
              <el-option v-for="c in CONDITIONS" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="applyFilters">筛选</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 移动端筛选抽屉 -->
      <el-drawer v-model="filterVisible" title="筛选" size="86%" direction="btt">
        <el-form label-position="top">
          <el-form-item label="排序">
            <el-select v-model="sortBy" class="w-full">
              <el-option v-for="s in SORTS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="最低价">
                <el-input v-model="minPrice" type="number" placeholder="不限" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="最高价">
                <el-input v-model="maxPrice" type="number" placeholder="不限" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="成色">
            <el-select v-model="condition" class="w-full">
              <el-option label="不限" value="" />
              <el-option v-for="c in CONDITIONS" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <div class="drawer-actions">
            <el-button class="flex1" @click="resetFilters">重置</el-button>
            <el-button class="flex1" type="primary" @click="applyFilters">应用</el-button>
          </div>
        </el-form>
      </el-drawer>

      <div v-if="loading" class="grid">
        <el-skeleton v-for="i in 8" :key="i" animated>
          <template #template>
            <el-skeleton-item variant="image" style="width: 100%; height: 180px" />
            <div style="padding: 12px">
              <el-skeleton-item variant="h3" style="width: 70%" />
              <el-skeleton-item variant="text" style="width: 40%" />
            </div>
          </template>
        </el-skeleton>
      </div>
      <el-empty v-else-if="!products.length" description="暂无商品，换个筛选条件试试" />
      <div v-else class="grid">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </div>
      <PaginationBar :page="page" :total-pages="totalPages" @change="load" />
    </div>
  </main>
</template>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.filter-btn {
  display: none;
}
.filter-panel {
  margin-bottom: 22px;
}
.drawer-actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}
.flex1 {
  flex: 1;
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}
@media (max-width: 1024px) {
  .grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 768px) {
  .filter-btn {
    display: inline-flex;
  }
  .grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
}
</style>
