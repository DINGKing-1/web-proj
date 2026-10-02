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
    <!-- Hero -->
    <section class="hero">
      <div class="hero-bg" aria-hidden="true">
        <div class="blob b1"></div>
        <div class="blob b2"></div>
        <div class="blob b3"></div>
        <div class="grid-lines"></div>
      </div>
      <div class="container hero-inner">
        <div class="hero-copy">
          <div class="eyebrow">
            <span class="dot"></span>
            PREMIUM SECOND-HAND
          </div>
          <h1 class="display">
            好物不该被闲置
            <span class="accent">让流转更有价值</span>
          </h1>
          <p class="lead">
            优品汇甄选二手好物，从手机数码到家居潮品——安心交易、即时沟通、余额托管。
          </p>
          <div class="hero-actions">
            <router-link to="/upload" class="btn btn-primary btn-lg">
              <i class="fas fa-plus"></i> 发布闲置
            </router-link>
            <router-link to="/category/手机" class="btn btn-outline btn-lg">
              逛逛好物 <i class="fas fa-arrow-right"></i>
            </router-link>
          </div>
          <div class="hero-stats">
            <div class="stat-item">
              <strong>10+</strong>
              <span>精选品类</span>
            </div>
            <div class="stat-item">
              <strong>实时</strong>
              <span>消息推送</span>
            </div>
            <div class="stat-item">
              <strong>100%</strong>
              <span>头像跟随</span>
            </div>
          </div>
        </div>

        <div class="hero-visual" aria-hidden="true">
          <div class="float-card card-a">
            <div class="fc-icon">📱</div>
            <div>
              <div class="fc-title">iPhone 15 Pro</div>
              <div class="fc-price">¥ 4,299</div>
            </div>
            <span class="fc-tag">几乎全新</span>
          </div>
          <div class="float-card card-b">
            <div class="fc-icon">💻</div>
            <div>
              <div class="fc-title">MacBook Air</div>
              <div class="fc-price">¥ 5,200</div>
            </div>
            <span class="fc-tag">轻微使用</span>
          </div>
          <div class="float-card card-c">
            <div class="fc-icon">🎧</div>
            <div>
              <div class="fc-title">AirPods Pro</div>
              <div class="fc-price">¥ 899</div>
            </div>
            <span class="fc-tag">全新</span>
          </div>
          <div class="orb-ring"></div>
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
          <h3>{{ f.title }}</h3>
          <p class="muted text-sm">{{ f.desc }}</p>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
/* Hero */
.hero {
  position: relative;
  padding: 48px 0 28px;
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
  width: 320px;
  height: 320px;
  background: rgba(212, 136, 60, 0.18);
  top: -60px;
  right: 12%;
}
.b2 {
  width: 180px;
  height: 180px;
  background: rgba(255, 190, 120, 0.28);
  bottom: 10%;
  left: 8%;
}
.b3 {
  width: 100px;
  height: 100px;
  background: rgba(47, 158, 98, 0.12);
  top: 30%;
  left: 40%;
}
.grid-lines {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(212, 136, 60, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(212, 136, 60, 0.05) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at 50% 30%, #000 20%, transparent 70%);
}
.hero-inner {
  position: relative;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 28px;
  align-items: center;
}
.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid var(--border);
  color: var(--accent-deep);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  margin-bottom: 16px;
}
.eyebrow .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 4px rgba(212, 136, 60, 0.2);
}
.display {
  font-size: clamp(32px, 5vw, 52px);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.15;
  margin: 0 0 16px;
}
.display .accent {
  display: block;
  background: linear-gradient(120deg, var(--accent), var(--accent-deep) 60%, #d4883c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.lead {
  font-size: 17px;
  color: var(--ink-soft);
  max-width: 34em;
  margin: 0 0 24px;
  line-height: 1.75;
}
.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 28px;
}
.hero-stats {
  display: flex;
  gap: 28px;
  flex-wrap: wrap;
}
.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat-item strong {
  font-size: 22px;
  color: var(--accent-deep);
  letter-spacing: -0.02em;
}
.stat-item span {
  font-size: 12px;
  color: var(--muted);
}

/* Floating cards visual */
.hero-visual {
  position: relative;
  min-height: 340px;
}
.float-card {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(14px);
  border-radius: 18px;
  box-shadow: var(--shadow-lg);
  animation: floatY 5s ease-in-out infinite;
}
.card-a {
  top: 8%;
  right: 8%;
  animation-delay: 0s;
}
.card-b {
  top: 38%;
  left: 4%;
  animation-delay: 1.2s;
}
.card-c {
  bottom: 10%;
  right: 14%;
  animation-delay: 2.2s;
}
.fc-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--accent-soft);
  font-size: 20px;
}
.fc-title {
  font-weight: 700;
  font-size: 13px;
}
.fc-price {
  color: var(--accent-deep);
  font-weight: 800;
  font-size: 15px;
}
.fc-tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--success-soft);
  color: var(--success);
  font-weight: 600;
}
.orb-ring {
  position: absolute;
  width: 220px;
  height: 220px;
  border: 1.5px dashed rgba(212, 136, 60, 0.35);
  border-radius: 50%;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
@keyframes floatY {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

/* Categories */
.section {
  margin-top: 36px;
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 16px;
  margin-bottom: 20px;
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
