<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { shopApi, chatApi, reviewApi } from '@/api'
import { formatPrice, formatTime } from '@/api/client'
import { showToast, confirmDialog, promptDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import ProductCard from '@/components/ProductCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const seller = ref(null)
const products = ref([])
const reviews = ref([])
const loading = ref(true)
const sortBy = ref('newest')

const sortedProducts = computed(() => {
  const list = [...products.value]
  if (sortBy.value === 'price-asc') list.sort((a, b) => a.secondHandPrice - b.secondHandPrice)
  else if (sortBy.value === 'price-desc') list.sort((a, b) => b.secondHandPrice - a.secondHandPrice)
  else if (sortBy.value === 'sales') list.sort((a, b) => (b.salesCount || 0) - (a.salesCount || 0))
  else list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  return list
})

function isSelf() {
  return store.user && seller.value && store.user.username === seller.value.username
}

/** 竞态防护：快速切换店铺时丢弃过期响应 */
let loadSeq = 0

async function load() {
  const seq = ++loadSeq
  loading.value = true
  const username = route.params.username
  const d = await shopApi.detail(username)
  if (seq !== loadSeq) return // 已切换到别的店铺，丢弃
  if (d.success) {
    seller.value = d.seller
    products.value = d.products || []
  } else {
    seller.value = null
    products.value = []
    showToast(d.message || '店铺加载失败', 'error')
  }
  const r = await shopApi.reviews(username)
  if (seq !== loadSeq) return
  if (r.success) reviews.value = r.reviews || []
  loading.value = false
}

/** 店铺主人可以在这里回复评价 */
async function replyToReview(r) {
  const text = await promptDialog({
    title: r.reply ? '修改回复' : '回复评价',
    message: `回复 ${r.buyerUsername} 的评价：`,
    placeholder: r.reply || '请输入回复内容（500 字内）',
    required: true,
    okText: '提交'
  })
  if (text == null) return
  const d = await reviewApi.reply(r.id, text)
  if (d.success) {
    showToast('回复成功')
    load()
  } else {
    showToast(d.message || '回复失败', 'error')
  }
}

async function contactSeller() {
  if (!store.user) {
    const ok = await confirmDialog({
      title: '请先登录',
      message: '登录后才能联系卖家',
      okText: '去登录',
      type: 'info'
    })
    if (ok) router.push('/login')
    return
  }
  if (isSelf()) {
    showToast('不能和自己聊天', 'warning')
    return
  }
  const d = await chatApi.createRoom(seller.value.username, products.value[0]?.id)
  if (d.success && d.roomId) {
    router.push({ path: '/messages', query: { roomId: d.roomId } })
  } else if (d.success) {
    router.push({
      path: '/messages',
      query: {
        sellerUsername: seller.value.username,
        sellerNickname: seller.value.nickname,
        sellerAvatar: seller.value.avatar || '',
        productId: products.value[0]?.id || ''
      }
    })
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

function stars(n) {
  const v = Math.round(Number(n) || 0)
  return '★★★★★'.slice(0, v) + '☆☆☆☆☆'.slice(0, 5 - v)
}

watch(() => route.params.username, load)
onMounted(load)
</script>

<template>
  <main class="page shop-page">
    <div class="container">
      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="!seller"
        icon="fa-store-slash"
        title="找不到该店铺"
        desc="可能链接有误，或卖家已注销。可去逛逛其他好物"
      >
        <router-link to="/" class="btn btn-primary">返回首页</router-link>
      </EmptyState>

      <template v-else>
        <!-- 店铺头 -->
        <section class="shop-hero card">
          <div class="shop-bg" aria-hidden="true"></div>
          <div class="shop-hero-inner">
            <img
              :src="seller.avatar || '/user-avatar/default.jpg'"
              class="shop-avatar"
              alt=""
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
            <div class="shop-info">
              <h1 class="shop-name">{{ seller.nickname || seller.username }}</h1>
              <div class="shop-sub muted text-sm">
                @{{ seller.username }}
                <span class="dot">·</span>
                开店于 {{ formatTime(seller.createdAt) }}
              </div>
              <div class="shop-stats">
                <div class="stat">
                  <strong>{{ seller.productCount }}</strong>
                  <span>在售商品</span>
                </div>
                <div class="stat">
                  <strong>{{ seller.avgRating || '—' }}</strong>
                  <span>综合评分</span>
                </div>
                <div class="stat">
                  <strong>{{ seller.reviewCount }}</strong>
                  <span>累计评价</span>
                </div>
              </div>
            </div>
            <div class="shop-actions">
              <button v-if="!isSelf()" type="button" class="btn btn-primary" @click="contactSeller">
                <i class="fas fa-comment-dots"></i> 联系卖家
              </button>
              <router-link v-else to="/user" class="btn btn-outline">管理我的商品</router-link>
            </div>
          </div>
        </section>

        <!-- 商品 -->
        <section class="section">
          <div class="section-head">
            <div>
              <h2 class="h1">全部商品</h2>
              <p class="muted">共 {{ products.length }} 件在售</p>
            </div>
            <div class="sort-tabs">
              <button
                v-for="t in [
                  { key: 'newest', label: '最新' },
                  { key: 'sales', label: '销量' },
                  { key: 'price-asc', label: '低价' },
                  { key: 'price-desc', label: '高价' }
                ]"
                :key="t.key"
                type="button"
                class="tab"
                :class="{ on: sortBy === t.key }"
                @click="sortBy = t.key"
              >
                {{ t.label }}
              </button>
            </div>
          </div>

          <EmptyState
            v-if="!products.length"
            icon="fa-box-open"
            title="暂无在售商品"
            desc="店主还没有上架商品"
          />
          <div v-else class="grid">
            <ProductCard v-for="p in sortedProducts" :key="p.id" :product="p" />
          </div>
        </section>

        <!-- 店铺评价 -->
        <section class="section">
          <div class="section-head">
            <div>
              <h2 class="h1">店铺评价</h2>
              <p class="muted">
                <span class="star-text">{{ stars(seller.avgRating) }}</span>
                {{ seller.avgRating ? `${seller.avgRating} 分` : '暂无评分' }}
                · {{ seller.reviewCount }} 条评价
              </p>
            </div>
          </div>

          <EmptyState
            v-if="!reviews.length"
            icon="fa-star"
            title="还没有评价"
            desc="完成交易后可以给卖家打分"
          />
          <ul v-else class="review-list card">
            <li v-for="r in reviews" :key="r.id" class="review-item">
              <div class="r-top">
                <strong>{{ r.buyerUsername }}</strong>
                <span class="stars">{{ stars(r.rating) }}</span>
                <span class="muted text-xs">{{ formatTime(r.createdAt) }}</span>
              </div>
              <p v-if="r.content" class="r-content">{{ r.content }}</p>
              <div v-if="r.reply" class="r-reply">
                <span class="r-tag">卖家回复</span>
                <p>{{ r.reply }}</p>
              </div>
              <div v-if="r.followUpContent" class="r-follow">
                <span class="r-tag follow">追评</span>
                <p>{{ r.followUpContent }}</p>
                <span class="muted text-xs">{{ formatTime(r.followUpAt) }}</span>
              </div>
              <button
                v-if="isSelf()"
                type="button"
                class="btn btn-ghost btn-sm"
                @click="replyToReview(r)"
              >
                {{ r.reply ? '修改回复' : '回复评价' }}
              </button>
            </li>
          </ul>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
.shop-hero {
  position: relative;
  overflow: hidden;
  padding: 28px 28px 24px;
  margin-bottom: 28px;
}
.shop-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(600px 200px at 10% 0%, rgba(212, 136, 60, 0.16), transparent 60%),
    radial-gradient(500px 180px at 90% 100%, rgba(255, 190, 120, 0.18), transparent 55%);
  pointer-events: none;
}
.shop-hero-inner {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
.shop-avatar {
  width: 88px;
  height: 88px;
  border-radius: 22px;
  object-fit: cover;
  border: 3px solid #fff;
  box-shadow: var(--shadow);
}
.shop-info {
  flex: 1;
  min-width: 220px;
}
.shop-name {
  margin: 0 0 4px;
  font-size: 26px;
  font-weight: 800;
}
.shop-sub .dot {
  margin: 0 6px;
  opacity: 0.5;
}
.shop-stats {
  display: flex;
  gap: 28px;
  margin-top: 12px;
}
.stat {
  display: flex;
  flex-direction: column;
}
.stat strong {
  font-size: 22px;
  font-weight: 800;
  color: var(--accent-deep);
  line-height: 1.2;
}
.stat span {
  font-size: 12px;
  color: var(--muted);
}
.shop-actions {
  display: flex;
  gap: 10px;
}
.section {
  margin-bottom: 32px;
}
.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.sort-tabs {
  display: flex;
  gap: 6px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 4px;
}
.tab {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  color: var(--muted);
}
.tab.on {
  background: var(--accent-soft);
  color: var(--accent-deep);
  font-weight: 600;
}
.grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}
.review-list {
  list-style: none;
  margin: 0;
  padding: 8px 18px;
}
.review-item {
  padding: 14px 0;
  border-top: 1px solid var(--border);
}
.review-item:first-child {
  border-top: none;
}
.r-reply,
.r-follow {
  margin-top: 8px;
  padding: 9px 12px;
  border-radius: 10px;
  background: var(--surface-warm, #fbf7f2);
  border: 1px solid var(--border);
  font-size: 13px;
  line-height: 1.6;
}
.r-follow {
  background: transparent;
  border-style: dashed;
}
.r-reply p,
.r-follow p {
  margin: 4px 0 0;
  white-space: pre-wrap;
}
.r-tag {
  display: inline-block;
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 6px;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
}
.r-tag.follow {
  background: var(--ink-soft);
}
.r-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.stars,
.star-text {
  color: var(--accent);
  letter-spacing: 1px;
}
.r-content {
  margin: 8px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
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
  .shop-avatar {
    width: 68px;
    height: 68px;
  }
  .shop-stats {
    gap: 18px;
  }
}
</style>
