<script setup>
import { ref, onMounted } from 'vue'
import { productApi } from '@/api'
import ProductCard from '@/components/ProductCard.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'

const products = ref([])
const total = ref(0)
const totalPages = ref(0)
const page = ref(0)
const loading = ref(false)

async function load(p = 0) {
  loading.value = true
  const d = await productApi.list({ page: p, size: 12 })
  if (d.success) {
    products.value = d.products || []
    total.value = d.total || 0
    totalPages.value = d.totalPages || 0
    page.value = p
  }
  loading.value = false
}

onMounted(() => load(0))

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
      </div>
      <div class="container hero-inner">
        <div class="hero-copy">
          <h1 class="display">
            好物不该被闲置
            <span class="accent">让流转更有价值</span>
          </h1>
          <p class="lead">精选二手好物，安心交易、即时沟通、余额托管。</p>
          <div class="hero-actions">
            <router-link to="/category/全部" class="btn btn-primary btn-lg">
              逛逛好物 <i class="fas fa-arrow-right"></i>
            </router-link>
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

<!-- 最新上架 -->
    <section class="container section">
      <div class="section-head">
        <div>
          <h2 class="h1">最新上架</h2>
          <p class="muted">看看大家刚刚分享的好东西</p>
        </div>
        <router-link to="/search" class="btn btn-outline btn-sm">搜索商品</router-link>
      </div>

      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="!products.length"
        icon="fa-store"
        title="还没有商品"
        desc="成为第一个发布闲置的人吧"
      >
        <router-link to="/upload" class="btn btn-primary">发布商品</router-link>
      </EmptyState>
      <div v-else class="grid">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </div>
      <PaginationBar :page="page" :total-pages="totalPages" @change="load" />
    </section>

    <!-- 特性 -->
    <section class="container section">
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
}
.b1 {
  width: 220px;
  height: 220px;
  background: rgba(212, 136, 60, 0.12);
  top: -70px;
  right: 8%;
}
.b2 {
  width: 140px;
  height: 140px;
  background: rgba(255, 190, 120, 0.16);
  bottom: -20px;
  left: 4%;
}
.hero-inner {
  position: relative;
}
.display {
  font-size: clamp(26px, 3.6vw, 38px);
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.2;
  margin: 0 0 10px;
}
.display .accent {
  background: linear-gradient(120deg, var(--accent), var(--accent-deep) 60%, #d4883c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.lead {
  font-size: 15px;
  color: var(--ink-soft);
  max-width: 36em;
  margin: 0 0 18px;
  line-height: 1.7;
}
.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.trust-row {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 16px 22px;
  color: var(--muted);
  font-size: 13px;
}
.trust-row i {
  color: var(--accent);
  margin-right: 6px;
}

/* Product grid */
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}
.skel {
  height: 260px;
  border-radius: var(--radius-lg);
  background: linear-gradient(90deg, #f2ebe3 25%, #faf3eb 50%, #f2ebe3 75%);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
}
@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
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
}
.feature h3 {
  margin: 0 0 6px;
  font-size: 15px;
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
  .hero-stats {
    gap: 18px;
  }
}
</style>
