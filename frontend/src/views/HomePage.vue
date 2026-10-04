<script setup>
import { ref, onMounted, onBeforeUnmount, computed, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { productApi, announcementApi } from '@/api'
import { showToast } from '@/composables/ui'
import ProductCard from '@/components/ProductCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'

const PAGE_SIZE = 25 // 5 列 × 5 行
const route = useRoute()
const products = ref([])
const announcements = ref([])
const total = ref(0)
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false) // 首屏
const loadingMore = ref(false) // 追加
const loadFailed = ref(false)
const sentinel = ref(null)
let observer = null

const hasMore = computed(() => {
  if (totalPages.value > 0) return page.value + 1 < totalPages.value
  return products.value.length < total.value
})

async function load(p = 0, append = false) {
  if (append) {
    if (loadingMore.value || !hasMore.value) return
    loadingMore.value = true
  } else {
    loading.value = true
    loadFailed.value = false
    products.value = []
    page.value = 0
  }

  const d = await productApi.list({ page: p, size: PAGE_SIZE })
  if (d.success) {
    const list = d.products || []
    if (append) products.value = [...products.value, ...list]
    else products.value = list
    total.value = d.total || 0
    totalPages.value = d.totalPages || 0
    page.value = p
  } else {
    if (!append) loadFailed.value = true
    showToast(d.message || '商品加载失败', 'error')
  }

  loading.value = false
  loadingMore.value = false
}

function onIntersect(entries) {
  const entry = entries[0]
  if (entry?.isIntersecting && hasMore.value && !loadingMore.value && !loading.value) {
    load(page.value + 1, true)
  }
}

/** hero CTA：页内滚到「最新上架」，不跳分类页 */
function scrollToGoods() {
  const el = document.getElementById('goods')
  if (!el) return
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  el.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' })
}

function setupObserver() {
  if (observer) observer.disconnect()
  if (!sentinel.value) return
  observer = new IntersectionObserver(onIntersect, {
    root: null,
    rootMargin: '0px 0px 280px 0px',
    threshold: 0
  })
  observer.observe(sentinel.value)
}

// 哨兵在商品渲染后才出现，需在 products 就绪后再挂观察器
watch(
  () => products.value.length,
  async () => {
    await nextTick()
    setupObserver()
  }
)

onMounted(async () => {
  if (route.query.kicked) {
    showToast('登录状态已失效，你仍可继续浏览首页', 'warning')
  }
  const [a] = await Promise.all([announcementApi.list().catch(() => null), load(0)])
  if (a?.success) announcements.value = (a.announcements || []).slice(0, 6)
  await nextTick()
  setupObserver()
})

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
})

const FEATURES = [
  { icon: 'fa-shield-halved', title: '正品保障', desc: '严格审核，品质保证' },
  { icon: 'fa-handshake', title: '安全交易', desc: '余额托管，放心买卖' },
  { icon: 'fa-bolt', title: '极速流转', desc: '发布即曝光，成交更快' },
  { icon: 'fa-comments', title: '即时沟通', desc: '买卖双方实时聊天' }
]
</script>

<template>
  <main class="page">
    <!-- Hero：压低高度，首屏尽量露出商品 -->
    <section class="hero">
      <div class="hero-bg" aria-hidden="true">
        <div class="blob b1"></div>
        <div class="blob b2"></div>
        <div class="blob b3"></div>
        <div class="orbit o1"></div>
        <div class="orbit o2"></div>
        <div class="spark s1"></div>
        <div class="spark s2"></div>
        <div class="spark s3"></div>
      </div>
      <div class="container hero-inner">
        <div class="hero-copy">
          <h1 class="display">
            <span class="line l1">好物不该被闲置</span>
            <span class="line l2 accent">让流转更有价值</span>
          </h1>
          <p class="lead">精选二手好物，安心交易、即时沟通、余额托管。</p>
          <div class="hero-actions">
            <button type="button" class="btn btn-primary btn-lg" @click="scrollToGoods">
              逛逛好物 <i class="fas fa-arrow-right"></i>
            </button>
            <router-link to="/upload" class="btn btn-outline btn-lg">
              <i class="fas fa-plus"></i> 发布闲置
            </router-link>
          </div>
          <ul class="trust-row">
            <li><i class="fas fa-shield-halved"></i> 正品保障</li>
            <li><i class="fas fa-bolt"></i> 极速流转</li>
            <li><i class="fas fa-comments"></i> 即时沟通</li>
          </ul>
        </div>
      </div>
    </section>

    <!-- 公告 / 轮播 -->
    <section v-if="announcements.length" class="container section announce-section">
      <div class="announce-grid">
        <component
          :is="a.linkUrl ? 'router-link' : 'div'"
          v-for="a in announcements"
          :key="a.id"
          :to="a.linkUrl || undefined"
          class="announce-card card"
          :class="{ image: a.type === 'image' && a.imageUrl }"
        >
          <img v-if="a.type === 'image' && a.imageUrl" :src="a.imageUrl" class="announce-img" :alt="a.title" />
          <div class="announce-body">
            <div class="announce-tag">{{ a.type === 'image' ? '推荐' : '公告' }}</div>
            <h3 class="announce-title">{{ a.title }}</h3>
            <p v-if="a.content" class="announce-content">{{ a.content }}</p>
          </div>
        </component>
      </div>
    </section>

    <!-- 最新上架：5 列 + 无限滚动 -->
    <section id="goods" class="container section">
      <div class="section-head">
        <div>
          <h2 class="h1">最新上架</h2>
          <p class="muted">看看大家刚刚分享的好东西</p>
        </div>
      </div>

      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="loadFailed"
        icon="fa-triangle-exclamation"
        title="加载失败"
        desc="网络异常，请稍后重试"
      >
        <button type="button" class="btn btn-primary" @click="load(0)">重新加载</button>
      </EmptyState>
      <EmptyState
        v-else-if="!products.length"
        icon="fa-store"
        title="还没有商品"
        desc="成为第一个发布闲置的人吧"
      >
        <router-link to="/upload" class="btn btn-primary">发布商品</router-link>
      </EmptyState>
      <template v-else>
        <div class="grid">
          <div
            v-for="(p, idx) in products"
            :key="p.id"
            class="card-enter"
            :style="{ animationDelay: `${Math.min(idx % PAGE_SIZE, 12) * 35}ms` }"
          >
            <ProductCard :product="p" />
          </div>

          <!-- 追加加载骨架 -->
          <template v-if="loadingMore">
            <div v-for="n in 5" :key="'sk' + n" class="skel card-enter" :style="{ animationDelay: `${n * 40}ms` }">
              <div class="skel-img"></div>
              <div class="skel-line w70"></div>
              <div class="skel-line w40"></div>
            </div>
          </template>
        </div>

        <div ref="sentinel" class="scroll-sentinel">
          <div v-if="loadingMore" class="load-more">
            <span class="spinner"></span>
            <span>正在加载更多好物…</span>
          </div>
          <div v-else-if="!hasMore && products.length" class="load-more end">
            — 已经到底啦 —
          </div>
        </div>
      </template>
    </section>

    <!-- 特性 -->
    <section class="container section features-section">
      <div class="features">
        <div v-for="f in FEATURES" :key="f.title" class="feature">
          <div class="f-icon"><i class="fas" :class="f.icon"></i></div>
          <div>
            <h3>{{ f.title }}</h3>
            <p class="muted text-sm">{{ f.desc }}</p>
          </div>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
/* Hero：紧凑 */
.hero {
  position: relative;
  padding: 28px 0 8px;
  overflow: hidden;
}
.hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  will-change: transform;
}
.b1 {
  width: 220px;
  height: 220px;
  background: rgba(212, 136, 60, 0.12);
  top: -70px;
  right: 8%;
  animation: floatBlob 9s ease-in-out infinite;
}
.b2 {
  width: 140px;
  height: 140px;
  background: rgba(255, 190, 120, 0.16);
  bottom: -20px;
  left: 4%;
  animation: floatBlob 11s ease-in-out infinite reverse;
}
.b3 {
  width: 90px;
  height: 90px;
  background: rgba(184, 111, 40, 0.1);
  top: 30%;
  right: 22%;
  animation: floatBlob 7s ease-in-out 1s infinite;
}
.orbit {
  position: absolute;
  border-radius: 50%;
  border: 1.5px dashed rgba(212, 136, 60, 0.22);
  animation: spinSlow 28s linear infinite;
}
.o1 {
  width: 180px;
  height: 180px;
  top: -40px;
  right: 12%;
}
.o2 {
  width: 110px;
  height: 110px;
  bottom: 10px;
  left: 10%;
  animation-duration: 20s;
  animation-direction: reverse;
}
.spark {
  position: absolute;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  opacity: 0.35;
  animation: sparkle 3.2s ease-in-out infinite;
}
.s1 { top: 18%; right: 28%; animation-delay: 0s; }
.s2 { top: 55%; right: 14%; width: 6px; height: 6px; animation-delay: 1.1s; }
.s3 { bottom: 22%; left: 18%; width: 5px; height: 5px; animation-delay: 2s; }

@keyframes floatBlob {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(14px, -18px) scale(1.06); }
  66% { transform: translate(-10px, 12px) scale(0.96); }
}
@keyframes spinSlow {
  to { transform: rotate(360deg); }
}
@keyframes sparkle {
  0%, 100% { opacity: 0.15; transform: translateY(0) scale(0.8); }
  50% { opacity: 0.55; transform: translateY(-12px) scale(1.15); }
}

.hero-inner {
  position: relative;
}
.display {
  font-size: clamp(32px, 4.2vw, 46px);
  font-weight: 800;
  letter-spacing: -0.025em;
  line-height: 1.12;
  margin: 0 0 12px;
}
.display .line {
  display: block;
}
.display .l1 {
  animation: riseIn 0.7s ease both;
}
.display .l2 {
  animation: riseIn 0.7s ease 0.12s both;
}
.display .accent {
  background: linear-gradient(120deg, var(--accent), var(--accent-deep) 60%, #d4883c);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: riseIn 0.7s ease 0.12s both, shimmerText 5s ease-in-out infinite;
}
.lead {
  font-size: 17px;
  font-weight: 500;
  color: var(--ink-soft);
  max-width: 36em;
  margin: 0 0 20px;
  line-height: 1.75;
  animation: riseIn 0.7s ease 0.22s both;
}
.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  animation: riseIn 0.7s ease 0.32s both;
}
.hero-actions .btn {
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}
.hero-actions .btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(212, 136, 60, 0.22);
}
.trust-row {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 16px 22px;
  color: var(--muted);
  font-size: 14px;
  font-weight: 600;
  animation: riseIn 0.7s ease 0.42s both;
}
.trust-row i {
  color: var(--accent);
  margin-right: 6px;
}

@keyframes riseIn {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes shimmerText {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

/* 分区间距：商品区与特性卡片拉开 */
.section {
  margin-bottom: 40px;
}
/* hero 滚动锚点：避开 sticky 顶栏 + 分类栏 */
#goods {
  scroll-margin-top: calc(var(--header-h) + var(--nav-h) + 16px);
}
.features-section {
  margin-bottom: 24px;
  padding-top: 8px;
}

/* Product grid：桌面 5 列 */
.grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

/* 入场动画 */
.card-enter {
  animation: cardIn 0.38s ease both;
}
@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 加载骨架 */
.skel {
  border-radius: var(--radius-lg);
  background: var(--surface);
  border: 1px solid var(--border);
  overflow: hidden;
}
.skel-img {
  aspect-ratio: 1 / 0.92;
  background: linear-gradient(90deg, #f2ebe3 25%, #faf3eb 50%, #f2ebe3 75%);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
}
.skel-line {
  height: 12px;
  margin: 12px 14px;
  border-radius: 6px;
  background: linear-gradient(90deg, #f2ebe3 25%, #faf3eb 50%, #f2ebe3 75%);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
}
.skel-line.w70 {
  width: 70%;
}
.skel-line.w40 {
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

/* 底部加载指示 */
.scroll-sentinel {
  min-height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px 0 4px;
}
.load-more {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--muted);
  font-size: 13px;
}
.announce-section {
  padding-top: 0;
}
.announce-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.announce-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  min-height: 110px;
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}
.announce-card:hover {
  border-color: var(--accent);
  transform: translateY(-3px);
  box-shadow: 0 12px 28px rgba(120, 70, 25, 0.1);
}
.announce-img {
  width: 100%;
  height: 120px;
  object-fit: cover;
}
.announce-body {
  padding: 12px 14px;
}
.announce-tag {
  display: inline-block;
  font-size: 11px;
  color: var(--accent-deep);
  background: var(--accent-soft);
  border-radius: 999px;
  padding: 2px 8px;
  margin-bottom: 6px;
}
.announce-title {
  font-size: 14px;
  font-weight: 650;
  margin: 0 0 4px;
}
.announce-content {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.load-more.end {
  opacity: 0.65;
  letter-spacing: 0.08em;
}
.spinner {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid rgba(212, 136, 60, 0.25);
  border-top-color: var(--accent);
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* Features */
.features {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.feature {
  text-align: center;
  padding: 28px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, #fffaf5, #fff);
  border: 1px solid var(--border);
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  animation: riseIn 0.55s ease both;
}
.feature:nth-child(1) { animation-delay: 0.05s; }
.feature:nth-child(2) { animation-delay: 0.12s; }
.feature:nth-child(3) { animation-delay: 0.19s; }
.feature:nth-child(4) { animation-delay: 0.26s; }
.feature:hover {
  transform: translateY(-6px);
  border-color: var(--accent);
  box-shadow: 0 16px 36px rgba(120, 70, 25, 0.1);
}
.f-icon {
  width: 52px;
  height: 52px;
  margin: 0 auto 12px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: var(--accent-soft);
  color: var(--accent-deep);
  font-size: 20px;
  transition: transform 0.25s ease;
}
.feature:hover .f-icon {
  transform: scale(1.08) rotate(-4deg);
}
.feature h3 {
  margin: 0 0 6px;
  font-size: 17px;
  font-weight: 750;
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
  .features {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 768px) {
  .hero-inner {
    grid-template-columns: 1fr;
  }
  .hero-visual {
    display: none;
  }
  .hero {
    padding-top: 28px;
  }
  .grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
  .features {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
