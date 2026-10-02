<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi, cartApi, favoriteApi, chatApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import { formatPrice, formatTime } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const product = ref(null)
const loading = ref(true)
const imageIndex = ref(0)
const favorited = ref(false)
const comments = ref([])
const commentText = ref('')
const submittingComment = ref(false)
const qty = ref(1)
const autoTimer = ref(null)
const autoCycles = ref(0)

const images = computed(() => {
  if (!product.value) return []
  if (product.value.imageUrls?.length) return product.value.imageUrls
  if (product.value.imageUrl) return String(product.value.imageUrl).split(',').filter(Boolean)
  return []
})

const isSeller = computed(
  () => store.user && product.value && store.user.username === product.value.sellerUsername
)
const isSold = computed(() => product.value && product.value.status === 'sold')
const salesCount = computed(() => Number(product.value?.salesCount || 0))
const stock = computed(() => Math.max(1, Number(product.value?.quantity ?? 1)))
const maxQty = computed(() => (isSold.value ? 0 : stock.value))

function requireLogin() {
  if (store.user) return true
  confirmDialog({
    title: '请先登录',
    message: '登录后才能进行该操作',
    okText: '去登录',
    cancelText: '取消',
    type: 'info'
  }).then((ok) => {
    if (ok) router.push('/login')
  })
  return false
}

function showImage(i) {
  if (!images.value.length) return
  imageIndex.value = (i + images.value.length) % images.value.length
}

function stopAuto() {
  if (autoTimer.value) {
    clearInterval(autoTimer.value)
    autoTimer.value = null
  }
}

function startAuto() {
  stopAuto()
  if (images.value.length <= 1) return
  autoTimer.value = setInterval(() => {
    if (autoCycles.value >= 1) {
      stopAuto()
      return
    }
    if (imageIndex.value >= images.value.length - 1) {
      autoCycles.value += 1
      showImage(0)
    } else {
      showImage(imageIndex.value + 1)
    }
  }, 5000)
}

function changeQty(delta) {
  const next = qty.value + delta
  if (next < 1 || next > maxQty.value) return
  qty.value = next
}

function clampQty() {
  let n = Number(qty.value) || 1
  n = Math.min(Math.max(1, n), Math.max(1, maxQty.value))
  qty.value = n
}

async function load() {
  loading.value = true
  const id = route.params.id
  const d = await productApi.detail(id)
  if (d.success) {
    product.value = d.product
    imageIndex.value = 0
    autoCycles.value = 0
    qty.value = 1
    startAuto()
    if (store.user) {
      const fav = await favoriteApi.check(id)
      if (fav.success) favorited.value = !!fav.favorited
    }
  } else {
    product.value = null
  }
  await loadComments()
  loading.value = false
}

async function loadComments() {
  const d = await productApi.comments(route.params.id)
  if (d.success) comments.value = d.comments || []
}

async function addComment() {
  if (!requireLogin()) return
  const content = commentText.value.trim()
  if (!content) {
    showToast('请输入评论内容', 'warning')
    return
  }
  submittingComment.value = true
  const d = await productApi.addComment(route.params.id, content)
  submittingComment.value = false
  if (d.success) {
    showToast('评论成功')
    commentText.value = ''
    loadComments()
  } else {
    showToast(d.message || '评论失败', 'error')
  }
}

async function removeComment(c) {
  const ok = await confirmDialog({
    title: '删除评论',
    message: '确定删除这条评论吗？',
    okText: '删除',
    type: 'warning'
  })
  if (!ok) return
  const d = await productApi.deleteComment(route.params.id, c.id)
  if (d.success) {
    showToast('删除成功')
    loadComments()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

function canDeleteComment(c) {
  if (!store.user) return false
  return store.user.username === c.userName || isSeller.value
}

async function addToCart() {
  if (!requireLogin()) return
  if (isSold.value) return
  const d = await cartApi.add(product.value.id)
  if (d.success) {
    showToast(qty.value > 1 ? `已加入购物车（按 ${qty.value} 件结算请用立即购买）` : '已加入购物车')
    store.refreshCartCount()
  } else {
    showToast(d.message || '加入失败', 'error')
  }
}

function buyNow() {
  if (!requireLogin()) return
  if (isSold.value) return
  const n = Math.min(Math.max(1, qty.value), maxQty.value || 1)
  sessionStorage.setItem(
    'checkout',
    JSON.stringify({ productId: String(product.value.id), quantity: n })
  )
  sessionStorage.setItem('checkoutProductId', String(product.value.id))
  sessionStorage.setItem('checkoutQuantity', String(n))
  router.push('/checkout')
}

async function toggleFavorite() {
  if (!requireLogin()) return
  const d = await favoriteApi.toggle(product.value.id)
  if (d.success) {
    favorited.value = !!d.favorited
    showToast(d.message || (favorited.value ? '已收藏' : '已取消收藏'))
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function contactSeller() {
  if (!requireLogin()) return
  if (isSeller.value) {
    showToast('不能和自己聊天', 'warning')
    return
  }
  const d = await chatApi.findRoom(product.value.id)
  if (!d.success) {
    showToast(d.message || '操作失败', 'error')
    return
  }
  if (d.exists && d.roomId) {
    router.push({ path: '/messages', query: { roomId: d.roomId } })
  } else {
    router.push({
      path: '/messages',
      query: {
        sellerUsername: d.sellerUsername || product.value.sellerUsername,
        sellerNickname: d.sellerNickname || product.value.sellerName,
        sellerAvatar: d.sellerAvatar || product.value.sellerAvatar || '',
        productId: product.value.id
      }
    })
  }
}

function visitShop() {
  const seller = product.value?.sellerUsername
  if (!seller) return
  router.push({ path: '/category/全部', query: { seller } })
}

async function deleteProduct() {
  const ok = await confirmDialog({
    title: '删除商品',
    message: '删除后不可恢复，确定删除该商品吗？',
    okText: '删除',
    type: 'warning'
  })
  if (!ok) return
  const d = await productApi.remove(product.value.id)
  if (d.success) {
    showToast('删除成功')
    router.back()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

watch(() => route.params.id, load)
onMounted(load)
onBeforeUnmount(stopAuto)
</script>

<template>
  <main class="page jd-page">
    <div class="container jd-container">
      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="!product"
        icon="fa-box-open"
        title="商品不存在"
        desc="该商品可能已下架或被删除"
      >
        <router-link to="/" class="btn btn-primary">返回首页</router-link>
      </EmptyState>

      <template v-else>
        <nav class="crumbs muted text-sm">
          <router-link to="/">首页</router-link>
          <span class="sep">›</span>
          <router-link :to="`/category/${encodeURIComponent(product.category || '全部')}`">
            {{ product.category || '全部' }}
          </router-link>
          <span class="sep">›</span>
          <span class="current">{{ product.name }}</span>
        </nav>

        <!-- 顶部：左图右购 -->
        <div class="top-layout">
          <section class="gallery">
            <div class="thumbs-col" :style="{ '--frame-h': images.length ? 'auto' : '360px' }">
              <button
                v-for="(img, i) in images"
                :key="i"
                type="button"
                class="thumb"
                :class="{ active: i === imageIndex }"
                @click="showImage(i); stopAuto()"
              >
                <img :src="img" alt="" />
              </button>
            </div>
            <div class="main-frame">
              <img
                v-if="images.length"
                :src="images[imageIndex]"
                :alt="product.name"
                @error="$event.target.src = '/user-avatar/default.jpg'"
              />
              <div v-else class="placeholder"><i class="fas fa-image"></i></div>
              <div v-if="isSold" class="sold-watermark">已售出</div>
              <template v-if="images.length > 1">
                <button type="button" class="nav-arrow left" aria-label="上一张" @click="showImage(imageIndex - 1)">
                  <i class="fas fa-chevron-left"></i>
                </button>
                <button type="button" class="nav-arrow right" aria-label="下一张" @click="showImage(imageIndex + 1)">
                  <i class="fas fa-chevron-right"></i>
                </button>
                <div class="counter">{{ imageIndex + 1 }}/{{ images.length }}</div>
              </template>
            </div>
          </section>

          <!-- 右侧吸顶购买卡 -->
          <aside class="buy-card card">
            <div class="buy-card-inner">
              <div class="badges">
                <span class="badge" :class="isSold ? 'badge-danger' : 'badge-success'">
                  {{ isSold ? '已售出' : '在售' }}
                </span>
                <span v-if="product.itemCondition" class="badge badge-muted">{{ product.itemCondition }}</span>
              </div>

              <h1 class="title">{{ product.name }}</h1>

              <div class="price-line">
                <div class="price-main">
                  <span class="yen">¥</span>
                  <span class="num">{{ formatPrice(product.secondHandPrice) }}</span>
                </div>
                <div v-if="product.originalPrice" class="price-sub">
                  原价 <s>¥{{ formatPrice(product.originalPrice) }}</s>
                  <span v-if="product.originalPrice > product.secondHandPrice" class="save">
                    省 ¥{{ formatPrice(Number(product.originalPrice) - Number(product.secondHandPrice)) }}
                  </span>
                </div>
              </div>

              <ul class="meta-rows">
                <li>
                  <span class="k">销量</span>
                  <span class="v">{{ salesCount > 0 ? `已售 ${salesCount} 件` : '暂无销量' }}</span>
                </li>
                <li>
                  <span class="k">库存</span>
                  <span class="v">{{ stock }} 件</span>
                </li>
                <li>
                  <span class="k">分类</span>
                  <span class="v">{{ product.category }}</span>
                </li>
              </ul>

              <div class="qty-row">
                <span class="k">数量</span>
                <div class="stepper">
                  <button type="button" class="step-btn" :disabled="isSold || qty <= 1" @click="changeQty(-1)">−</button>
                  <input
                    v-model.number="qty"
                    class="step-input"
                    type="number"
                    min="1"
                    :max="maxQty"
                    :disabled="isSold"
                    @change="clampQty"
                  />
                  <button type="button" class="step-btn" :disabled="isSold || qty >= maxQty" @click="changeQty(1)">+</button>
                </div>
              </div>

              <div class="buy-actions">
                <template v-if="isSeller">
                  <button type="button" class="btn btn-danger btn-lg btn-block" @click="deleteProduct">
                    <i class="fas fa-trash"></i> 删除商品
                  </button>
                </template>
                <template v-else>
                  <button type="button" class="btn btn-outline btn-lg btn-cart" :disabled="isSold" @click="addToCart">
                    <i class="fas fa-shopping-bag"></i> 加入购物车
                  </button>
                  <button type="button" class="btn btn-primary btn-lg btn-buy" :disabled="isSold" @click="buyNow">
                    {{ isSold ? '已售出' : '立即购买' }}
                  </button>
                  <button
                    type="button"
                    class="btn btn-ghost fav-btn"
                    :class="{ 'fav-active': favorited }"
                    @click="toggleFavorite"
                  >
                    <i :class="favorited ? 'fas fa-heart' : 'far fa-heart'"></i>
                    {{ favorited ? '已收藏' : '收藏' }}
                  </button>
                </template>
              </div>

              <button type="button" class="link-chat" @click="contactSeller">
                <i class="fas fa-comment-dots"></i> 联系卖家
              </button>
            </div>
          </aside>
        </div>

        <!-- 评论 / 买家秀 -->
        <section class="card card-pad block">
          <div class="section-title">
            <h2>买家秀 · 留言</h2>
            <span class="count-pill">{{ comments.length }}</span>
          </div>

          <form class="composer" @submit.prevent="addComment">
            <textarea
              v-model="commentText"
              class="composer-input"
              maxlength="500"
              rows="3"
              placeholder="晒出你的使用体验，帮助更多买家做决定…"
            ></textarea>
            <div class="composer-bar">
              <span class="muted text-xs">{{ commentText.length }}/500</span>
              <div class="composer-actions">
                <button v-if="commentText" type="button" class="btn btn-ghost btn-sm" @click="commentText = ''">清空</button>
                <button
                  type="submit"
                  class="btn btn-primary"
                  :disabled="submittingComment || !commentText.trim()"
                >
                  {{ submittingComment ? '发布中…' : '发布买家秀' }}
                </button>
              </div>
            </div>
          </form>

          <EmptyState v-if="!comments.length" icon="fa-camera" title="还没有买家秀" desc="来发第一条评价吧" />
          <ul v-else class="buyer-list">
            <li v-for="c in comments" :key="c.id" class="buyer-item">
              <img
                :src="c.avatar || '/user-avatar/default.jpg'"
                class="b-avatar"
                alt=""
                @error="$event.target.src = '/user-avatar/default.jpg'"
              />
              <div class="b-body">
                <div class="b-top">
                  <strong>{{ c.displayName || c.userName }}</strong>
                  <span class="muted text-xs">{{ formatTime(c.createdAt) }}</span>
                  <button v-if="canDeleteComment(c)" type="button" class="b-del" @click="removeComment(c)">删除</button>
                </div>
                <p class="b-content">{{ c.content }}</p>
              </div>
            </li>
          </ul>
        </section>

        <!-- 店铺 -->
        <section class="card shop-bar block">
          <img
            :src="product.sellerAvatar || '/user-avatar/default.jpg'"
            class="shop-avatar"
            alt=""
            @error="$event.target.src = '/user-avatar/default.jpg'"
          />
          <div class="shop-meta">
            <div class="shop-name">{{ product.sellerName }}</div>
            <div class="muted text-sm">进店逛逛，发现更多好物</div>
          </div>
          <div class="shop-actions">
            <button type="button" class="btn btn-outline btn-sm" @click="contactSeller">
              <i class="fas fa-comment-dots"></i> 联系卖家
            </button>
            <button type="button" class="btn btn-primary btn-sm" @click="visitShop">
              进店逛逛 <i class="fas fa-arrow-right"></i>
            </button>
          </div>
        </section>

        <!-- 图文详情 -->
        <section class="card card-pad block">
          <div class="section-title">
            <h2>商品详情</h2>
          </div>
          <ul class="detail-grid">
            <li>
              <span class="k">成色</span>
              <span class="v">{{ product.itemCondition || '—' }}</span>
            </li>
            <li>
              <span class="k">库存</span>
              <span class="v">{{ stock }} 件</span>
            </li>
            <li>
              <span class="k">发布时间</span>
              <span class="v">{{ formatTime(product.createdAt) }}</span>
            </li>
            <li>
              <span class="k">分类</span>
              <span class="v">{{ product.category }}</span>
            </li>
          </ul>
          <p class="desc">{{ product.description || '卖家暂未填写详细描述' }}</p>

          <div v-if="images.length" class="detail-gallery">
            <img
              v-for="(img, i) in images"
              :key="'d' + i"
              :src="img"
              :alt="`${product.name} 图 ${i + 1}`"
              loading="lazy"
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
          </div>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
/* 为右侧玻璃坞留出空间，避免购买卡被遮挡 */
.jd-page {
  padding-top: 16px;
  padding-bottom: 72px;
}
.jd-container {
  width: min(100% - 120px, var(--max));
  padding-right: 8px;
}

.crumbs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.crumbs a:hover {
  color: var(--accent-deep);
}
.crumbs .sep {
  opacity: 0.4;
}
.crumbs .current {
  max-width: 30em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink-soft);
}

/* 上：左图右购 */
.top-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 380px);
  gap: 20px;
  align-items: start;
  margin-bottom: 18px;
}

.gallery {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
}

/* 缩略图：整体相对大图垂直居中 */
.thumbs-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  max-height: 100%;
}
.thumb {
  width: 68px;
  height: 68px;
  border-radius: 10px;
  overflow: hidden;
  border: 2px solid transparent;
  padding: 0;
  background: var(--bg-soft);
  flex-shrink: 0;
}
.thumb.active {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px rgba(212, 136, 60, 0.16);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.main-frame {
  position: relative;
  aspect-ratio: 1;
  border-radius: 16px;
  overflow: hidden;
  background: linear-gradient(180deg, #f7eee4, #efe2d2);
  border: 1px solid var(--border);
}
.main-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--muted);
  font-size: 42px;
}
.sold-watermark {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 0.22em;
  color: rgba(214, 69, 69, 0.22);
  background: rgba(255, 255, 255, 0.16);
  pointer-events: none;
}
.nav-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: var(--ink);
  box-shadow: var(--shadow);
}
.nav-arrow.left {
  left: 12px;
}
.nav-arrow.right {
  right: 12px;
}
.counter {
  position: absolute;
  right: 12px;
  bottom: 12px;
  background: rgba(44, 34, 27, 0.62);
  color: #fff;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
}

/* 购买卡 */
.buy-card {
  position: sticky;
  top: calc(var(--header-h) + 12px);
  overflow: hidden;
}
.buy-card-inner {
  padding: 18px 18px 16px;
}
.badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.title {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
}
.price-line {
  padding: 12px 14px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(212, 136, 60, 0.12), rgba(212, 136, 60, 0.04));
  border: 1px solid rgba(212, 136, 60, 0.16);
  margin-bottom: 14px;
}
.price-main {
  display: flex;
  align-items: baseline;
  color: var(--accent-deep);
  font-weight: 800;
}
.price-main .yen {
  font-size: 16px;
}
.price-main .num {
  font-size: 28px;
  letter-spacing: -0.02em;
  line-height: 1.1;
}
.price-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
}
.save {
  color: var(--danger);
  font-weight: 600;
  margin-left: 6px;
}

.meta-rows {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.meta-rows li {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
}
.meta-rows .k {
  color: var(--muted);
}
.meta-rows .v {
  color: var(--ink-soft);
  font-weight: 550;
  text-align: right;
}

.qty-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.qty-row .k {
  color: var(--muted);
  font-size: 13px;
}
.stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface);
}
.step-btn {
  width: 32px;
  height: 32px;
  color: var(--ink-soft);
  background: var(--surface-warm);
}
.step-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.step-input {
  width: 48px;
  height: 32px;
  border: none;
  border-left: 1px solid var(--border);
  border-right: 1px solid var(--border);
  text-align: center;
  outline: none;
  background: var(--surface);
  color: var(--ink);
  font-weight: 600;
}
.step-input::-webkit-outer-spin-button,
.step-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.step-input[type='number'] {
  -moz-appearance: textfield;
  appearance: textfield;
}

.buy-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.buy-actions .btn {
  height: 46px;
  border-radius: 12px;
  font-weight: 700;
}
.buy-actions .btn-block {
  grid-column: 1 / -1;
}
.btn-buy {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
}
.fav-btn {
  grid-column: 1 / -1;
  height: 40px !important;
}
.fav-active {
  color: var(--danger) !important;
  background: var(--danger-soft);
}
.link-chat {
  width: 100%;
  margin-top: 12px;
  padding: 10px;
  border-radius: 10px;
  color: var(--accent-deep);
  background: var(--accent-soft);
  font-size: 13px;
  font-weight: 600;
}

/* 评论 / 买家秀 */
.block {
  margin-bottom: 16px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.section-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}
.count-pill {
  min-width: 22px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent-deep);
  font-size: 12px;
  font-weight: 700;
  display: inline-grid;
  place-items: center;
}
.composer {
  background: var(--surface-warm);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 12px;
  margin-bottom: 16px;
}
.composer-input {
  width: 100%;
  min-height: 84px;
  resize: vertical;
  border: 1px solid var(--border-strong);
  border-radius: 12px;
  padding: 12px 14px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  line-height: 1.6;
  outline: none;
}
.composer-input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(212, 136, 60, 0.15);
}
.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}
.composer-actions {
  display: flex;
  gap: 8px;
}
.buyer-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.buyer-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid var(--border);
}
.buyer-item:first-child {
  border-top: none;
  padding-top: 0;
}
.b-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}
.b-body {
  flex: 1;
  min-width: 0;
}
.b-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.b-del {
  margin-left: auto;
  color: var(--muted);
  font-size: 12px;
}
.b-del:hover {
  color: var(--danger);
}
.b-content {
  margin: 6px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
  white-space: pre-wrap;
}

/* 店铺条 */
.shop-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  margin-bottom: 16px;
}
.shop-avatar {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  object-fit: cover;
}
.shop-meta {
  flex: 1;
  min-width: 0;
}
.shop-name {
  font-weight: 700;
  font-size: 16px;
}
.shop-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* 图文详情 */
.detail-grid {
  list-style: none;
  margin: 0 0 14px;
  padding: 12px 14px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 16px;
  background: var(--surface-warm);
  border-radius: 12px;
  border: 1px solid var(--border);
}
.detail-grid .k {
  display: block;
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 2px;
}
.detail-grid .v {
  font-size: 14px;
  font-weight: 550;
  color: var(--ink-soft);
}
.desc {
  margin: 0 0 18px;
  color: var(--ink-soft);
  white-space: pre-wrap;
  line-height: 1.85;
  font-size: 15px;
}
.detail-gallery {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-gallery img {
  width: 100%;
  border-radius: 12px;
  border: 1px solid var(--border);
}

@media (max-width: 1100px) {
  .jd-container {
    width: min(100% - 48px, var(--max));
  }
}
@media (max-width: 960px) {
  .top-layout {
    grid-template-columns: 1fr;
  }
  .buy-card {
    position: static;
  }
  .gallery {
    grid-template-columns: 56px minmax(0, 1fr);
  }
  .thumb {
    width: 56px;
    height: 56px;
  }
  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 560px) {
  .jd-container {
    width: min(100% - 24px, var(--max));
  }
  .shop-bar {
    flex-wrap: wrap;
  }
}
</style>
