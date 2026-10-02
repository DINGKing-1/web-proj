<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { productApi } from '@/api'
import ProductCard from '@/components/ProductCard.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import FilterBar from '@/components/FilterBar.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()

const products = ref([])
const total = ref(0)
const totalPages = ref(0)
const page = ref(0)
const loading = ref(false)
const categoryName = ref('')

const sortBy = ref('newest')
const minPrice = ref('')
const maxPrice = ref('')
const condition = ref('')

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
      </div>

      <FilterBar
        v-model:sort-by="sortBy"
        v-model:min-price="minPrice"
        v-model:max-price="maxPrice"
        v-model:condition="condition"
        @apply="applyFilters"
        @reset="resetFilters"
      />

      <div v-if="loading" class="grid">
        <div v-for="i in 8" :key="i" class="card skeleton-card">
          <div class="skeleton-img"></div>
          <div class="skeleton-line w70"></div>
          <div class="skeleton-line w40"></div>
        </div>
      </div>
      <EmptyState
        v-else-if="!products.length"
        icon="fa-box-open"
        title="暂无商品"
        desc="换个筛选条件试试"
      />
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
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}
.skeleton-card {
  overflow: hidden;
  padding: 0;
}
.skeleton-img {
  aspect-ratio: 1 / 0.92;
  background: linear-gradient(90deg, #f3e7d9 25%, #faf0e4 50%, #f3e7d9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite linear;
}
.skeleton-line {
  height: 12px;
  margin: 12px 14px;
  border-radius: 6px;
  background: linear-gradient(90deg, #f3e7d9 25%, #faf0e4 50%, #f3e7d9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite linear;
}
.skeleton-line.w70 {
  width: 70%;
}
.skeleton-line.w40 {
  width: 40%;
  margin-bottom: 18px;
}
@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
@media (max-width: 1024px) {
  .grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 768px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
}
</style>
