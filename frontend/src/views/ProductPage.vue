<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi, cartApi, favoriteApi, chatApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
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
const autoTimer = ref(null)
const autoCycles = ref(0)

const images = computed(() => {
  if (!product.value) return []
  if (product.value.imageUrls?.length) return product.value.imageUrls
  if (product.value.imageUrl) return String(product.value.imageUrl).split(',').filter(Boolean)
  return []
})

const isSeller = computed(
  () =>
    store.user &&
    product.value &&
    store.user.username === product.value.sellerUsername
)
const isSold = computed(() => product.value && product.value.status === 'sold')

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
  const d = await productApi.addComment(route.params.id, content)
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
  <main class="page">
    <div class="container">
      <div v-if="loading" class="loading"><div class="spinner"></div>加载中…</div>
      <EmptyState
        v-else-if="!product"
        icon="fa-box-open"
        title="商品不存在"
        desc="该商品可能已下架或被删除"
      >
        <router-link to="/" class="btn btn-primary">返回首页</router-link>
      </EmptyState>

      <template v-else>
        <div class="detail-layout">
          <section class="gallery card">
            <div class="main-frame">
              <img
                v-if="images.length"
                :src="images[imageIndex]"
                :alt="product.name"
                @error="$event.target.src = '/user-avatar/default.jpg'"
              />
              <div v-else class="placeholder"><i class="fas fa-image"></i></div>
              <div v-if="isSold" class="sold-watermark">SOLD</div>
              <template v-if="images.length > 1">
                <button class="nav-arrow left" @click="showImage(imageIndex - 1)">
                  <i class="fas fa-chevron-left"></i>
                </button>
                <button class="nav-arrow right" @click="showImage(imageIndex + 1)">
                  <i class="fas fa-chevron-right"></i>
                </button>
                <div class="counter">{{ imageIndex + 1 }} / {{ images.length }}</div>
              </template>
            </div>
            <div v-if="images.length > 1" class="thumbs">
              <button
                v-for="(img, i) in images"
                :key="i"
                class="thumb"
                :class="{ active: i === imageIndex }"
                @click="showImage(i); stopAuto()"
              >
                <img :src="img" alt="" />
              </button>
            </div>
          </section>

          <section class="info card card-pad">
            <div class="status-row">
              <span class="badge" :class="isSold ? 'badge-danger' : 'badge-success'">
                {{ isSold ? '已售出' : '在售' }}
              </span>
              <span class="badge">{{ product.category }}</span>
            </div>
            <h1 class="h1">{{ product.name }}</h1>
            <div class="price-row">
              <span class="price-lg">¥{{ formatPrice(product.secondHandPrice) }}</span>
              <span v-if="product.originalPrice" class="price-origin">
                原价 ¥{{ formatPrice(product.originalPrice) }}
              </span>
            </div>

            <div class="meta-grid">
              <div><span class="label">成色</span><span>{{ product.itemCondition || '—' }}</span></div>
              <div><span class="label">库存</span><span>{{ product.quantity ?? 1 }}</span></div>
              <div><span class="label">发布时间</span><span>{{ formatTime(product.createdAt) }}</span></div>
            </div>

            <div class="seller-card">
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
              <button class="btn btn-outline btn-sm" @click="contactSeller">
                <i class="fas fa-comment"></i> 联系卖家
              </button>
            </div>

            <div class="actions">
              <template v-if="isSeller">
                <button class="btn btn-danger btn-lg" @click="deleteProduct">
                  <i class="fas fa-trash"></i> 删除商品
                </button>
              </template>
              <template v-else>
                <button class="btn btn-primary btn-lg" :disabled="isSold" @click="buyNow">
                  {{ isSold ? '已售出' : '立即购买' }}
                </button>
                <button class="btn btn-outline btn-lg" :disabled="isSold" @click="addToCart">
                  <i class="fas fa-shopping-bag"></i> 加入购物车
                </button>
                <button
                  class="btn btn-ghost btn-lg"
                  :class="{ 'fav-active': favorited }"
                  @click="toggleFavorite"
                >
                  <i :class="favorited ? 'fas fa-heart' : 'far fa-heart'"></i>
                  {{ favorited ? '已收藏' : '收藏' }}
                </button>
              </template>
            </div>
          </section>
        </div>

        <section class="card card-pad block">
          <h2 class="h2">商品描述</h2>
          <p class="desc">{{ product.description || '卖家暂未填写描述' }}</p>
        </section>

        <section class="card card-pad block">
          <div class="comment-head">
            <h2 class="h2">留言评价</h2>
            <span class="muted">{{ comments.length }} 条</span>
          </div>
          <form class="comment-form" @submit.prevent="addComment">
            <textarea
              v-model="commentText"
              class="form-textarea"
              maxlength="500"
              placeholder="写下你的想法…"
            ></textarea>
            <button class="btn btn-primary" type="submit">发表评论</button>
          </form>
          <EmptyState v-if="!comments.length" icon="fa-comments" title="还没有评论" desc="来抢沙发吧" />
          <ul v-else class="comment-list">
            <li v-for="c in comments" :key="c.id">
              <img
                :src="c.avatar || '/user-avatar/default.jpg'"
                class="c-avatar"
                alt=""
                @error="$event.target.src = '/user-avatar/default.jpg'"
              />
              <div class="c-body">
                <div class="c-top">
                  <strong>{{ c.displayName || c.userName }}</strong>
                  <span class="muted text-xs">{{ formatTime(c.createdAt) }}</span>
                  <button
                    v-if="canDeleteComment(c)"
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
.detail-layout {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 22px;
  margin-bottom: 22px;
}
.gallery {
  overflow: hidden;
}
.main-frame {
  position: relative;
  aspect-ratio: 1 / 0.92;
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
  font-size: 56px;
  font-weight: 800;
  letter-spacing: 0.12em;
  color: rgba(214, 69, 69, 0.28);
  pointer-events: none;
}
.nav-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.88);
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
  background: rgba(44, 34, 27, 0.65);
  color: #fff;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
}
.thumbs {
  display: flex;
  gap: 10px;
  padding: 14px;
  overflow-x: auto;
}
.thumb {
  width: 64px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  border: 2px solid transparent;
  flex-shrink: 0;
  padding: 0;
}
.thumb.active {
  border-color: var(--accent);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.status-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.price-row {
  margin: 12px 0 18px;
}
.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
}
.meta-grid .label {
  display: block;
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 4px;
}
.seller-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
  background: var(--surface-warm);
  border: 1px solid var(--border);
  margin-bottom: 18px;
}
.seller-avatar {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  object-fit: cover;
}
.seller-meta {
  flex: 1;
}
.seller-name {
  font-weight: 650;
}
.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.fav-active {
  color: var(--danger) !important;
}
.block {
  margin-bottom: 18px;
}
.desc {
  color: var(--ink-soft);
  white-space: pre-wrap;
  line-height: 1.8;
}
.comment-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.comment-form {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}
.comment-form .form-textarea {
  flex: 1;
}
.comment-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.comment-list li {
  display: flex;
  gap: 12px;
}
.c-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}
.c-body {
  flex: 1;
}
.c-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.c-del {
  margin-left: auto;
  color: var(--danger);
  font-size: 12px;
}
.c-content {
  margin: 6px 0 0;
  color: var(--ink-soft);
  white-space: pre-wrap;
}
@media (max-width: 900px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>

