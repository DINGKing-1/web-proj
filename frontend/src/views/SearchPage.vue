<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi, shopApi } from '@/api'
import { getSearchHistory, pushSearchHistory, clearSearchHistory, getBrowseHistory, clearBrowseHistory, formatPrice, onImgError } from '@/api/client'
import { showToast } from '@/composables/ui'
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
const category = ref('')
const history = ref(getSearchHistory())
const hotWords = ref([])
const browseHistory = ref(getBrowseHistory())
/** 竞态防护：快速改筛选/翻页时丢弃过期响应 */
let loadSeq = 0

async function load(p = 0) {
  const seq = ++loadSeq
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
    condition: condition.value || undefined,
    category: category.value || undefined
  })
  if (seq !== loadSeq) return
  if (d.success) {
    products.value = d.products || []
    total.value = d.total || 0
    totalPages.value = d.totalPages || 0
    page.value = p
  } else {
    showToast(d.message || '搜索失败，请稍后重试', 'error')
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
  category.value = ''
  load(0)
}

function doSearch() {
  const q = query.value.trim()
  if (!q) return
  pushSearchHistory(q)
  history.value = getSearchHistory()
  router.push({ path: '/search', query: { q } })
}

function clearHistory() {
  clearSearchHistory()
  history.value = []
}

function clearBrowse() {
  clearBrowseHistory()
  browseHistory.value = []
}

function useWord(w) {
  query.value = w
  doSearch()
}

watch(
  () => route.query.q,
  () => load(0)
)

onMounted(() => {
  load(0)
  shopApi.hotWords().then((d) => {
    if (d.success) hotWords.value = d.words || []
  })
  if (route.query.q) pushSearchHistory(String(route.query.q))
  history.value = getSearchHistory()
})
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
          v-model:category="category"
          show-category
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

      <template v-else>
        <section v-if="hotWords.length" class="chip-section">
          <div class="chip-head">
            <h2 class="h3">热门搜索</h2>
          </div>
          <div class="chips">
            <button
              v-for="w in hotWords"
              :key="w"
              type="button"
              class="chip"
              @click="useWord(w)"
            >
              {{ w }}
            </button>
          </div>
        </section>

        <section v-if="history.length" class="chip-section">
          <div class="chip-head">
            <h2 class="h3">搜索历史</h2>
            <button type="button" class="link-btn" @click="clearHistory">清空</button>
          </div>
          <div class="chips">
            <button
              v-for="w in history"
              :key="w"
              type="button"
              class="chip"
              @click="useWord(w)"
            >
              {{ w }}
            </button>
          </div>
        </section>

        <section v-if="browseHistory.length" class="chip-section">
          <div class="chip-head">
            <h2 class="h3">浏览过的商品</h2>
            <button type="button" class="link-btn" @click="clearBrowse">清空</button>
          </div>
          <div class="browse-grid">
            <router-link
              v-for="b in browseHistory.slice(0, 8)"
              :key="'b' + b.id"
              :to="`/product/${b.id}`"
              class="browse-card"
            >
              <img :src="b.image" alt="" @error="onImgError" />
              <div class="b-name">{{ b.name }}</div>
              <div class="b-price">¥{{ formatPrice(b.price) }}</div>
            </router-link>
          </div>
        </section>

        <EmptyState
          v-if="!hotWords.length && !history.length && !browseHistory.length"
          icon="fa-magnifying-glass"
          title="开始搜索"
          desc="输入关键词搜索你想要的商品"
        />
      </template>
    </div>
  </main>
</template>

<style scoped>
.chip-section {
  margin-bottom: 22px;
}
.chip-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  padding: 7px 14px;
  border-radius: 999px;
  background: var(--surface);
  border: 1px solid var(--border);
  font-size: 13px;
  color: var(--ink-soft);
}
.chip:hover {
  border-color: var(--accent);
  color: var(--accent-deep);
  background: var(--accent-soft);
}
.link-btn {
  color: var(--muted);
  font-size: 12px;
}
.browse-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.browse-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  padding-bottom: 10px;
}
.browse-card img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
}
.b-name {
  padding: 8px 10px 2px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.b-price {
  padding: 0 10px;
  color: var(--accent-deep);
  font-weight: 700;
  font-size: 13px;
}
.link-btn:hover {
  color: var(--danger);
}
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
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
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
@media (max-width: 1200px) {
  .grid {
    grid-template-columns: repeat(4, 1fr);
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
