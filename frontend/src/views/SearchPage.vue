<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '@/api'
import ProductCard from '@/components/ProductCard.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import EmptyState from '@/components/EmptyState.vue'

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
const CONDITIONS = ['', '全新', '几乎全新', '轻微使用', '明显使用', '重度使用']

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
        <div class="filter-panel card card-pad">
          <div class="filter-row">
            <div class="field">
              <label>排序</label>
              <select v-model="sortBy" class="form-select">
                <option value="newest">最新发布</option>
                <option value="price-asc">价格从低到高</option>
                <option value="price-desc">价格从高到低</option>
              </select>
            </div>
            <div class="field">
              <label>最低价</label>
              <input v-model="minPrice" type="number" min="0" step="0.01" class="form-input" placeholder="不限" />
            </div>
            <div class="field">
              <label>最高价</label>
              <input v-model="maxPrice" type="number" min="0" step="0.01" class="form-input" placeholder="不限" />
            </div>
            <div class="field">
              <label>成色</label>
              <select v-model="condition" class="form-select">
                <option v-for="c in CONDITIONS" :key="c" :value="c">{{ c || '不限' }}</option>
              </select>
            </div>
            <div class="actions">
              <button class="btn btn-primary" type="button" @click="applyFilters">筛选</button>
              <button class="btn btn-outline" type="button" @click="resetFilters">重置</button>
            </div>
          </div>
        </div>

        <div v-if="loading" class="loading"><div class="spinner"></div>加载中…</div>
        <el-empty
          v-else-if="!products.length"
          :description="`没有找到与「${query}」相关的商品`"
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
.filter-panel {
  margin-bottom: 22px;
}
.filter-row {
  display: flex;
  gap: 14px;
  align-items: end;
  flex-wrap: wrap;
}
.field {
  min-width: 140px;
}
.field label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 6px;
}
.actions {
  display: flex;
  gap: 8px;
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
@media (max-width: 700px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
}
</style>
