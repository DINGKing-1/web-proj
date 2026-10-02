<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi, cartApi, favoriteApi, chatApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import { firstImage, formatPrice, formatTime } from '@/api/client'
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

async function load() {
  loading.value = true
  const id = route.params.id
  const d = await productApi.detail(id)
  if (d.success) {
    product.value = d.product
    imageIndex.value = 0
    autoCycles.value = 0
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
    showToast('已加入购物车')
    store.refreshCartCount()
  } else {
    showToast(d.message || '加入失败', 'error')
  }
}

function buyNow() {
  if (!requireLogin()) return
  if (isSold.value) return
  sessionStorage.setItem('checkoutProductId', String(product.value.id))
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
  <main class="page detail-page">
    <div class="container">
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
        <nav class="crumbs muted text-sm" aria-label="面包屑">
          <router-link to="/">首页</router-link>
          <span class="sep">/</span>
          <router-link :to="`/category/${encodeURIComponent(product.category || '全部')}`">
            {{ product.category || '全部' }}
          </router-link>
          <span class="sep">/</span>
          <span class="current">{{ product.name }}</span>
        </nav>

        <div class="detail-layout">
          <!-- 图集 -->
          <section class="gallery">
            <div class="main-frame card">
              <img
                v-if="images.length"
                :src="images[imageIndex]"
                :alt="product.name"
                @error="$event.target.src = '/user-avatar/default.jpg'"
              />
              <div v-else class="placeholder"><i class="fas fa-image"></i></div>
              <div v-if="isSold" class="sold-watermark">已售出</div>
              <template v-if="images.length > 1">
                <button class="nav-arrow left" type="button" aria-label="上一张" @click="showImage(imageIndex - 1)">
                  <i class="fas fa-chevron-left"></i>
                </button>
                <button class="nav-arrow right" type="button" aria-label="下一张" @click="showImage(imageIndex + 1)">
                  <i class="fas fa-chevron-right"></i>
                </button>
                <div class="counter">{{ imageIndex + 1 }} / {{ images.length }}</div>
              </template>
            </div>
            <div v-if="images.length > 1" class="thumbs">
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
          </section>

          <!-- 信息 + 购买 -->
          <section class="info card card-pad">
            <div class="badges">
              <span class="badge" :class="isSold ? 'badge-danger' : 'badge-success'">
                {{ isSold ? '已售出' : '在售' }}
              </span>
              <span class="badge badge-muted">{{ product.category }}</span>
              <span v-if="product.itemCondition" class="badge">{{ product.itemCondition }}</span>
            </div>

            <h1 class="title">{{ product.name }}</h1>

            <div class="price-box">
              <div class="price-main">
                <span class="yen">¥</span>
                <span class="num">{{ formatPrice(product.secondHandPrice) }}</span>
              </div>
              <div v-if="product.originalPrice" class="price-sub">
                原价 <s>¥{{ formatPrice(product.originalPrice) }}</s>
                <span v-if="product.originalPrice > product.secondHandPrice" class="save">
                  省 ¥{{ formatPrice(product.originalPrice - product.secondHandPrice) }}
                </span>
              </div>
            </div>

            <ul class="meta-list">
              <li>
                <span class="k">销量</span>
                <span class="v">{{ salesCount > 0 ? `已售 ${salesCount} 件` : '暂无销量' }}</span>
              </li>
              <li>
                <span class="k">库存</span>
                <span class="v">{{ product.quantity ?? 1 }} 件</span>
              </li>
              <li>
                <span class="k">发布时间</span>
                <span class="v">{{ formatTime(product.createdAt) }}</span>
              </li>
            </ul>

            <div class="seller-row">
              <img
                :src="product.sellerAvatar || '/user-avatar/default.jpg'"
                class="seller-avatar"
                alt=""
                @error="$event.target.src = '/user-avatar/default.jpg'"
              />
              <div class="seller-meta">
                <div class="seller-name">{{ product.sellerName }}</div>
                <div class="muted text-sm">卖家</div>
              </div>
              <button type="button" class="btn btn-outline btn-sm" @click="contactSeller">
                <i class="fas fa-comment-dots"></i> 联系卖家
              </button>
            </div>

            <div class="buy-actions">
              <template v-if="isSeller">
                <button type="button" class="btn btn-danger btn-lg btn-block" @click="deleteProduct">
                  <i class="fas fa-trash"></i> 删除商品
                </button>
              </template>
              <template v-else>
                <button type="button" class="btn btn-primary btn-lg buy-main" :disabled="isSold" @click="buyNow">
                  <i class="fas fa-bolt"></i>
                  {{ isSold ? '已售出' : '立即购买' }}
                </button>
                <button type="button" class="btn btn-outline btn-lg" :disabled="isSold" @click="addToCart">
                  <i class="fas fa-shopping-bag"></i> 加入购物车
                </button>
                <button
                  type="button"
                  class="btn btn-ghost btn-lg icon-fav"
                  :class="{ 'fav-active': favorited }"
                  :title="favorited ? '取消收藏' : '收藏'"
                  @click="toggleFavorite"
                >
                  <i :class="favorited ? 'fas fa-heart' : 'far fa-heart'"></i>
                </button>
              </template>
            </div>
          </section>
        </div>

        <!-- 描述 -->
        <section class="card card-pad block">
          <div class="section-title">
            <h2>商品描述</h2>
          </div>
          <p class="desc">{{ product.description || '卖家暂未填写描述' }}</p>
        </section>

        <!-- 评论 -->
        <section class="card card-pad block">
          <div class="section-title">
            <h2>留言评价</h2>
            <span class="count-pill">{{ comments.length }}</span>
          </div>

          <form class="composer card-inner" @submit.prevent="addComment">
            <textarea
              v-model="commentText"
              class="composer-input"
              maxlength="500"
              rows="3"
              placeholder="说点什么，帮助更多人了解这件商品…"
            ></textarea>
            <div class="composer-bar">
              <span class="muted text-xs">{{ commentText.length }}/500</span>
              <div class="composer-actions">
                <button
                  v-if="commentText"
                  type="button"
                  class="btn btn-ghost btn-sm"
                  @click="commentText = ''"
                >
                  清空
                </button>
                <button
                  type="submit"
                  class="btn btn-primary"
                  :disabled="submittingComment || !commentText.trim()"
                >
                  {{ submittingComment ? '发布中…' : '发布评论' }}
                </button>
              </div>
            </div>
          </form>

          <EmptyState v-if="!comments.length" icon="fa-comments" title="还没有评论" desc="来抢沙发吧" />

          <ul v-else class="comment-list">
            <li v-for="c in comments" :key="c.id" class="comment-item">
              <img
                :src="c.avatar || '/user-avatar/default.jpg'"
                class="c-avatar"
                alt=""
                @error="$event.target.src = '/user-avatar/default.jpg'"
              />
              <div class="c-body">
                <div class="c-top">
                  <strong class="c-name">{{ c.displayName || c.userName }}</strong>
                  <span class="muted text-xs">{{ formatTime(c.createdAt) }}</span>
                  <button
                    v-if="canDeleteComment(c)"
                    type="button"
                    class="c-del"
                    @click="removeComment(c)"
                  >
                    删除
                  </button>
                </div>
                <p class="c-content">{{ c.content }}</p>
              </div>
            </li>
          </ul>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
.detail-page {
  padding-top: 20px;
}

.crumbs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.crumbs a:hover {
  color: var(--accent-deep);
}
.crumbs .sep {
  opacity: 0.45;
}
.crumbs .current {
  max-width: 28em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink-soft);
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(320px, 0.95fr);
  gap: 20px;
  align-items: start;
  margin-bottom: 18px;
}

/* 图集 */
.gallery {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.main-frame {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  background: linear-gradient(180deg, #f7eee4, #efe2d2);
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
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0.2em;
  color: rgba(214, 69, 69, 0.22);
  background: rgba(255, 255, 255, 0.18);
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
.thumbs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}
.thumb {
  width: 60px;
  height: 60px;
  border-radius: 10px;
  overflow: hidden;
  border: 2px solid transparent;
  flex-shrink: 0;
  padding: 0;
  background: var(--bg-soft);
}
.thumb.active {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px rgba(212, 136, 60, 0.18);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 信息卡 */
.info {
  position: sticky;
  top: calc(var(--header-h) + 16px);
}
.badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.title {
  margin: 0 0 14px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.01em;
}
.price-box {
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(212, 136, 60, 0.1), rgba(212, 136, 60, 0.04));
  border: 1px solid rgba(212, 136, 60, 0.18);
  margin-bottom: 16px;
}
.price-main {
  display: flex;
  align-items: baseline;
  color: var(--accent-deep);
  font-weight: 800;
}
.price-main .yen {
  font-size: 18px;
  margin-right: 2px;
}
.price-main .num {
  font-size: 32px;
  letter-spacing: -0.02em;
  line-height: 1.1;
}
.price-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--muted);
}
.price-sub s {
  margin-right: 8px;
}
.save {
  color: var(--danger);
  font-weight: 600;
}

.meta-list {
  list-style: none;
  margin: 0 0 16px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.meta-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
}
.meta-list .k {
  color: var(--muted);
}
.meta-list .v {
  color: var(--ink-soft);
  font-weight: 550;
  text-align: right;
}

.seller-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--surface-warm);
  border: 1px solid var(--border);
  margin-bottom: 18px;
}
.seller-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
}
.seller-meta {
  flex: 1;
  min-width: 0;
}
.seller-name {
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.buy-actions {
  display: grid;
  grid-template-columns: 1.4fr 1fr auto;
  gap: 10px;
  align-items: center;
}
.buy-actions .btn-lg {
  padding: 13px 16px;
}
.buy-actions .btn-block {
  grid-column: 1 / -1;
}
.icon-fav {
  width: 52px;
  height: 48px;
  border-radius: 14px;
}
.fav-active {
  color: var(--danger) !important;
  background: var(--danger-soft);
}

/* 通用区块 */
.block {
  margin-bottom: 18px;
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
.desc {
  margin: 0;
  color: var(--ink-soft);
  white-space: pre-wrap;
  line-height: 1.85;
  font-size: 15px;
}

/* 评论 */
.card-inner {
  background: var(--surface-warm);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 14px;
  margin-bottom: 20px;
}
.composer-input {
  width: 100%;
  min-height: 88px;
  resize: vertical;
  border: 1px solid var(--border-strong);
  border-radius: 12px;
  padding: 12px 14px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  line-height: 1.6;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
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
  align-items: center;
}

.comment-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.comment-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid var(--border);
}
.comment-item:first-child {
  border-top: none;
  padding-top: 0;
}
.c-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.c-body {
  flex: 1;
  min-width: 0;
}
.c-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.c-name {
  font-size: 14px;
}
.c-del {
  margin-left: auto;
  color: var(--muted);
  font-size: 12px;
}
.c-del:hover {
  color: var(--danger);
}
.c-content {
  margin: 6px 0 0;
  color: var(--ink-soft);
  white-space: pre-wrap;
  line-height: 1.7;
}

@media (max-width: 960px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
  .info {
    position: static;
  }
}
@media (max-width: 560px) {
  .title {
    font-size: 19px;
  }
  .price-main .num {
    font-size: 26px;
  }
  .buy-actions {
    grid-template-columns: 1fr 1fr;
  }
  .buy-actions .buy-main {
    grid-column: 1 / -1;
  }
  .icon-fav {
    width: 100%;
  }
}
</style>
