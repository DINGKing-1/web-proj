<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '@/api'
import ProductCard from '@/components/ProductCard.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import EmptyState from '@/components/EmptyState.vue'
import FilterBar from '@/components/FilterBar.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'

const route = useRoute()
const router = useRouter()

const products = ref([])
const total = ref(0)
const totalPages = ref(0)
const page = ref(0)
const loading = ref(false)
const query = ref(route.query.q || '')

const sortBy = ref('newest')
const minPrice = ref('')
const maxPrice = ref('')
const condition = ref('')

async function load(p = 0) {
  const q = (route.query.q || '').trim()
  query.value = q
  if (!q) {
    products.value = []
    total.value = 0
    totalPages.value = 0
    return
  }
  loading.value = true
  const d = await productApi.search({
    q,
    page: p,
    size: 12,
    sortBy: sortBy.value || undefined,
    minPrice: minPrice.value === '' ? undefined : minPrice.value,
    maxPrice: maxPrice.value === '' ? undefined : maxPrice.value,
    condition: condition.value || undefined
  })
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

function doSearch() {
  const q = query.value.trim()
  if (!q) return
  router.push({ path: '/search', query: { q } })
}

watch(
  () => route.query.q,
  () => load(0)
)

onMounted(() => load(0))
</script>

<template>
  <main class="page">
    <div class="container">
      <div class="page-head">
        <div>
          <h1 class="h1">搜索结果</h1>
          <p class="muted">
            <template v-if="query">关键词「{{ query }}」 · 共 {{ total }} 件</template>
            <template v-else>输入关键词搜索你想要的商品</template>
          </p>
        </div>
        <form class="inline-search" @submit.prevent="doSearch">
          <input v-model="query" class="form-input" placeholder="搜索商品…" />
          <button class="btn btn-primary" type="submit">搜索</button>
        </form>
      </div>

      <template v-if="query">
        <FilterBar
          v-model:sort-by="sortBy"
          v-model:min-price="minPrice"
          v-model:max-price="maxPrice"
          v-model:condition="condition"
          @apply="applyFilters"
          @reset="resetFilters"
        />

        <LoadingBlock v-if="loading" />
        <EmptyState
          v-else-if="!products.length"
          icon="fa-magnifying-glass"
          title="没有找到相关商品"
          :desc="`没有与「${query}」匹配的商品，试试别的条件`"
        />
        <div v-else class="grid">
          <ProductCard v-for="p in products" :key="p.id" :product="p" />
        </div>
        <PaginationBar :page="page" :total-pages="totalPages" @change="load" />
      </template>

      <EmptyState
        v-else
        icon="fa-magnifying-glass"
        title="开始搜索"
        desc="输入关键词搜索你想要的商品"
      />
    </div>
  </main>
</template>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: end;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.inline-search {
  display: flex;
  gap: 8px;
  min-width: min(100%, 340px);
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
