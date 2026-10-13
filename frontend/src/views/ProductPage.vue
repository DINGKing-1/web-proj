<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi, cartApi, favoriteApi, chatApi, reportApi, offerApi, reviewApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import AppModal from '@/components/AppModal.vue'
import { formatPrice, formatTime, pushBrowseHistory, onImgError } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const product = ref(null)
const loading = ref(true)
const imageIndex = ref(0)
const favorited = ref(false)
const addingCart = ref(false)
const qty = ref(1)
const autoTimer = ref(null)
const autoCycles = ref(0)
const orderReviews = ref([])
const avgRating = ref(null)
const relatedProducts = ref([])
/** 商品留言 */
const comments = ref([])
const commentText = ref('')
const commentActing = ref(false)
/** 举报弹窗 */
const showReport = ref(false)
const reportReason = ref('商品违规')
const reportDetail = ref('')
const submittingReport = ref(false)
/** 议价弹窗 */
const showOffer = ref(false)
const offerPrice = ref('')
const offerMessage = ref('')
const submittingOffer = ref(false)

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
// 库存口径统一：Product.quantity 即库存，库存 0 = 已售罄
const stock = computed(() => Math.max(0, Number(product.value?.quantity ?? 0)))
const soldOut = computed(() => isSold.value || stock.value <= 0)
const maxQty = computed(() => (soldOut.value ? 0 : stock.value))

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

/** 竞态防护：快速切换商品时丢弃过期响应 */
let loadSeq = 0

async function load() {
  const seq = ++loadSeq
  loading.value = true
  const id = route.params.id
  const d = await productApi.detail(id)
  if (seq !== loadSeq) return // 已切换到别的商品，丢弃
  if (d.success) {
    product.value = d.product
    pushBrowseHistory(d.product)
    imageIndex.value = 0
    autoCycles.value = 0
    qty.value = 1
    startAuto()
    if (store.user) {
      const fav = await favoriteApi.check(id)
      if (seq === loadSeq && fav.success) favorited.value = !!fav.favorited
    }
  } else {
    product.value = null
    showToast(d.message || '商品加载失败', 'error')
  }
  await loadReviews()
  await loadComments()
  loadRelated()
  if (seq === loadSeq) loading.value = false
}

async function loadRelated() {
  const seq = loadSeq
  const d = await productApi.related(route.params.id, 8)
  if (seq !== loadSeq) return
  if (d.success) relatedProducts.value = d.products || []
}

async function loadReviews() {
  const seq = loadSeq
  const d = await productApi.reviews(route.params.id)
  if (seq !== loadSeq) return
  if (d.success) {
    orderReviews.value = d.reviews || []
    avgRating.value = d.avgRating
  }
}

async function loadComments() {
  const seq = loadSeq
  const d = await productApi.comments(route.params.id)
  if (seq !== loadSeq) return
  if (d.success) comments.value = d.comments || []
}

async function submitComment() {
  if (!requireLogin() || commentActing.value) return
  const content = commentText.value.trim()
  if (!content) {
    showToast('请填写留言内容', 'warning')
    return
  }
  if (content.length > 500) {
    showToast('留言不能超过 500 字', 'warning')
    return
  }
  commentActing.value = true
  const d = await productApi.addComment(route.params.id, content)
  commentActing.value = false
  if (d.success) {
    showToast('留言成功')
    commentText.value = ''
    await loadComments()
  } else {
    showToast(d.message || '留言失败', 'error')
  }
}

async function removeComment(c) {
  const ok = await confirmDialog({
    title: '删除留言',
    message: '确定删除这条留言吗？',
    okText: '删除',
    type: 'warning'
  })
  if (!ok) return
  const d = await productApi.deleteComment(route.params.id, c.id)
  if (d.success) {
    showToast('已删除')
    await loadComments()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

const canDeleteComment = (c) =>
  store.user && (store.user.username === c.userName || store.user.role === 'admin')

/** 评价互动：卖家回复 / 买家追评 */
const replyingId = ref(null)
const replyText = ref('')
const followId = ref(null)
const followText = ref('')

function startReply(r) {
  replyingId.value = r.id
  replyText.value = r.reply || ''
  followId.value = null
}

function startFollow(r) {
  followId.value = r.id
  followText.value = ''
  replyingId.value = null
}

async function submitReply(r) {
  if (!replyText.value.trim()) {
    showToast('请填写回复内容', 'warning')
    return
  }
  const d = await reviewApi.reply(r.id, replyText.value.trim())
  if (d.success) {
    showToast('回复成功')
    replyingId.value = null
    loadReviews()
  } else {
    showToast(d.message || '回复失败', 'error')
  }
}

async function submitFollow(r) {
  if (!followText.value.trim()) {
    showToast('请填写追评内容', 'warning')
    return
  }
  const d = await reviewApi.followUp(r.id, followText.value.trim())
  if (d.success) {
    showToast('追评成功')
    followId.value = null
    loadReviews()
  } else {
    showToast(d.message || '追评失败', 'error')
  }
}

const isReviewSeller = (r) => store.user && store.user.username === r.sellerUsername
const isReviewBuyer = (r) => store.user && store.user.username === r.buyerUsername

async function reportProduct() {
  if (!requireLogin()) return
  reportReason.value = '商品违规'
  reportDetail.value = ''
  showReport.value = true
}

async function submitReport() {
  submittingReport.value = true
  const d = await reportApi.create({
    productId: product.value.id,
    productName: product.value.name,
    reason: reportReason.value || '商品违规',
    detail: reportDetail.value.trim() || '用户在商品详情页举报'
  })
  submittingReport.value = false
  showReport.value = false
  showToast(d.message || (d.success ? '举报已提交' : '举报失败'), d.success ? 'success' : 'error')
}

async function shareProduct() {
  const url = window.location.href
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(url)
    } else {
      const ta = document.createElement('textarea')
      ta.value = url
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      ta.remove()
    }
    showToast('商品链接已复制，快去分享吧')
  } catch {
    showToast('复制失败，请手动复制地址栏链接', 'error')
  }
}

function stars(n) {
  const v = Math.round(Number(n) || 0)
  return '★★★★★'.slice(0, v) + '☆☆☆☆☆'.slice(0, 5 - v)
}

function mediaList(str) {
  if (!str) return []
  return String(str)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

function isVideoUrl(url) {
  return /\.(mp4|webm|mov|avi)(\?|$)/i.test(String(url || ''))
}

async function addToCart() {
  if (!requireLogin()) return
  if (soldOut.value) {
    showToast('该商品已售罄', 'warning')
    return
  }
  if (addingCart.value) return
  addingCart.value = true
  try {
    // 尊重已选数量
    const d = await cartApi.add(product.value.id, Math.max(1, Number(qty.value) || 1))
    if (d.success) {
      showToast(qty.value > 1 ? `已加入购物车 × ${qty.value}` : '已加入购物车')
      store.refreshCartCount()
    } else {
      showToast(d.message || '加入失败', 'error')
    }
  } finally {
    addingCart.value = false
  }
}

function buyNow() {
  if (!requireLogin()) return
  if (soldOut.value) {
    showToast('该商品已售罄', 'warning')
    return
  }
  const n = Math.min(Math.max(1, qty.value), maxQty.value || 1)
  sessionStorage.setItem('checkoutProductId', String(product.value.id))
  sessionStorage.setItem('checkoutQuantity', String(n))
  router.push('/checkout')
}

const favActing = ref(false)
async function toggleFavorite() {
  if (!requireLogin() || favActing.value) return
  favActing.value = true
  try {
    const d = await favoriteApi.toggle(product.value.id)
    if (d.success) {
      favorited.value = !!d.favorited
      showToast(d.message || (favorited.value ? '已收藏' : '已取消收藏'))
    } else {
      showToast(d.message || '操作失败', 'error')
    }
  } finally {
    favActing.value = false
  }
}

function openOffer() {
  if (!requireLogin()) return
  if (isSeller.value) {
    showToast('不能对自己商品出价', 'warning')
    return
  }
  if (soldOut.value) {
    showToast('该商品已售罄', 'warning')
    return
  }
  offerPrice.value = ''
  offerMessage.value = ''
  showOffer.value = true
}

async function submitOffer() {
  const price = Number(offerPrice.value)
  if (!price || price <= 0) {
    showToast('请填写有效出价', 'warning')
    return
  }
  submittingOffer.value = true
  const d = await offerApi.create({
    productId: product.value.id,
    offerPrice: price,
    message: offerMessage.value.trim() || undefined
  })
  submittingOffer.value = false
  if (d.success) {
    showToast('出价已发送，等待卖家回应')
    showOffer.value = false
  } else {
    showToast(d.message || '出价失败', 'error')
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
  router.push({ path: `/shop/${encodeURIComponent(seller)}` })
}

async function toggleShelf() {
  if (!product.value || !requireLogin()) return
  const next = product.value.status === 'active' ? 'inactive' : 'active'
  const label = next === 'active' ? '重新上架' : '下架'
  const ok = await confirmDialog({
    title: label,
    message: `确定${label}「${product.value.name}」吗？`,
    type: 'warning'
  })
  if (!ok) return
  const d = await productApi.update(product.value.id, { status: next })
  if (d.success) {
    showToast(next === 'active' ? '已重新上架' : '已下架')
    await load()
  } else {
    showToast(d.message || '操作失败', 'error')
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

        <!-- 布局：左缩略图 | 中大图 | 右购买卡；评论等与大图左对齐 -->
        <div class="detail-layout">
          <!-- 缩略图（大图左侧外侧） -->
          <div class="thumbs-col">
            <button
              v-for="(img, i) in images"
              :key="i"
              type="button"
              class="thumb"
              :class="{ active: i === imageIndex }"
              @click="showImage(i); stopAuto()"
            >
              <img :src="img" alt="" @error="onImgError" />
            </button>
          </div>

          <!-- 大图 -->
          <div class="main-frame">
            <img
              v-if="images.length"
              :src="images[imageIndex]"
              :alt="product.name"
              @error="onImgError"
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

          <!-- 右侧吸顶购买卡（独占右栏） -->
          <aside class="buy-card card">
            <div class="buy-card-inner">
              <div class="badges">
                <span class="badge" :class="soldOut ? 'badge-danger' : 'badge-success'">
                  {{ isSold ? '已售出' : stock <= 0 ? '已售罄' : '在售' }}
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
                  <span class="v">{{ soldOut ? '已售罄' : `${stock} 件` }}</span>
                </li>
                <li>
                  <span class="k">分类</span>
                  <span class="v">{{ product.category }}</span>
                </li>
              </ul>

              <div class="qty-row">
                <span class="k">数量</span>
                <div class="stepper">
                  <button type="button" class="step-btn" :disabled="soldOut || qty <= 1" @click="changeQty(-1)">−</button>
                  <input
                    v-model.number="qty"
                    class="step-input"
                    type="number"
                    min="1"
                    :max="maxQty"
                    :disabled="soldOut"
                    @change="clampQty"
                  />
                  <button type="button" class="step-btn" :disabled="soldOut || qty >= maxQty" @click="changeQty(1)">+</button>
                </div>
              </div>

              <div class="buy-actions">
                <template v-if="isSeller">
                  <button
                    type="button"
                    class="btn btn-primary btn-lg btn-block"
                    @click="router.push(`/upload?edit=${product.id}`)"
                  >
                    <i class="fas fa-pen"></i> 编辑商品
                  </button>
                  <button
                    v-if="product.status !== 'sold'"
                    type="button"
                    class="btn btn-outline btn-lg btn-block"
                    @click="toggleShelf"
                  >
                    <i :class="product.status === 'active' ? 'fas fa-eye-slash' : 'fas fa-eye'"></i>
                    {{ product.status === 'active' ? '下架商品' : '重新上架' }}
                  </button>
                  <button type="button" class="btn btn-danger btn-lg btn-block" @click="deleteProduct">
                    <i class="fas fa-trash"></i> 删除商品
                  </button>
                </template>
                <template v-else>
                  <button
                    type="button"
                    class="btn btn-outline btn-lg btn-cart"
                    :disabled="soldOut || addingCart"
                    @click="addToCart"
                  >
                    <i class="fas fa-shopping-bag"></i> {{ soldOut ? '已售罄' : '加入购物车' }}
                  </button>
                  <button type="button" class="btn btn-primary btn-lg btn-buy" :disabled="soldOut" @click="buyNow">
                    {{ soldOut ? '已售罄' : '立即购买' }}
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
                  <button type="button" class="btn btn-outline btn-lg btn-block" :disabled="soldOut" @click="openOffer">
                    <i class="fas fa-hand-holding-usd"></i> 议价出价
                  </button>
                </template>
              </div>

              <button type="button" class="link-chat" @click="contactSeller">
                <i class="fas fa-comment-dots"></i> 联系卖家
              </button>
              <button type="button" class="link-chat" @click="shareProduct">
                <i class="fas fa-share-nodes"></i> 分享商品
              </button>
              <button v-if="!isSeller" type="button" class="link-report" @click="reportProduct">
                <i class="fas fa-flag"></i> 举报商品
              </button>
            </div>
          </aside>

          <!-- 下方内容：与大图左对齐 -->
          <div class="below-col">
            <!-- 订单评价（仅展示；评价入口在确认收货后的订单页） -->
            <section class="card card-pad block">
              <div class="section-title">
                <h2>成交评价</h2>
                <span class="count-pill">{{ orderReviews.length }}</span>
                <span v-if="avgRating" class="star-text">{{ stars(avgRating) }} {{ avgRating }} 分</span>
              </div>
              <EmptyState
                v-if="!orderReviews.length"
                icon="fa-star"
                title="还没有成交评价"
                desc="确认收货后可对订单打分并晒图"
              />
              <ul v-else class="buyer-list">
                <li v-for="r in orderReviews" :key="'r' + r.id" class="buyer-item">
                  <div class="b-body">
                    <div class="b-top">
                      <strong>{{ r.buyerUsername }}</strong>
                      <span class="star-text">{{ stars(r.rating) }}</span>
                      <span class="muted text-xs">{{ formatTime(r.createdAt) }}</span>
                    </div>
                    <div class="score-rows">
                      <span class="score-item">商品质量 {{ stars(r.ratingGoods ?? r.rating) }}</span>
                      <span class="score-item">卖家服务 {{ stars(r.ratingService ?? r.rating) }}</span>
                      <span class="score-item">物流速度 {{ stars(r.ratingDelivery ?? r.rating) }}</span>
                    </div>
                    <p v-if="r.content" class="b-content">{{ r.content }}</p>
                    <div v-if="mediaList(r.images).length || mediaList(r.videos).length" class="review-media">
                      <template v-for="(img, i) in mediaList(r.images)" :key="'img' + i">
                        <img :src="img" alt="评价图片" @error="onImgError" />
                      </template>
                      <video
                        v-for="(vid, i) in mediaList(r.videos)"
                        :key="'vid' + i"
                        :src="vid"
                        controls
                        preload="metadata"
                      ></video>
                    </div>

                    <div v-if="r.reply" class="seller-reply">
                      <span class="reply-tag">卖家回复</span>
                      <p>{{ r.reply }}</p>
                      <span class="muted text-xs">{{ formatTime(r.repliedAt) }}</span>
                    </div>

                    <div v-if="r.followUpContent" class="buyer-follow">
                      <span class="follow-tag">追评</span>
                      <p>{{ r.followUpContent }}</p>
                      <span class="muted text-xs">{{ formatTime(r.followUpAt) }}</span>
                    </div>

                    <div class="review-actions">
                      <button
                        v-if="isReviewSeller(r) && replyingId !== r.id"
                        type="button"
                        class="btn btn-ghost btn-sm"
                        @click="startReply(r)"
                      >
                        {{ r.reply ? '修改回复' : '回复评价' }}
                      </button>
                      <button
                        v-if="isReviewBuyer(r) && !r.followUpContent && followId !== r.id"
                        type="button"
                        class="btn btn-ghost btn-sm"
                        @click="startFollow(r)"
                      >
                        追评
                      </button>
                    </div>

                    <div v-if="replyingId === r.id" class="reply-box">
                      <textarea
                        v-model="replyText"
                        class="reply-input"
                        rows="2"
                        maxlength="500"
                        placeholder="回复买家评价（500 字内）"
                      ></textarea>
                      <div class="reply-btns">
                        <button type="button" class="btn btn-outline btn-sm" @click="replyingId = null">取消</button>
                        <button type="button" class="btn btn-primary btn-sm" @click="submitReply(r)">提交回复</button>
                      </div>
                    </div>

                    <div v-if="followId === r.id" class="reply-box">
                      <textarea
                        v-model="followText"
                        class="reply-input"
                        rows="2"
                        maxlength="500"
                        placeholder="追评：用了一段时间后的真实感受"
                      ></textarea>
                      <div class="reply-btns">
                        <button type="button" class="btn btn-outline btn-sm" @click="followId = null">取消</button>
                        <button type="button" class="btn btn-primary btn-sm" @click="submitFollow(r)">提交追评</button>
                      </div>
                    </div>
                  </div>
                </li>
              </ul>
            </section>

            <!-- 商品留言 -->
            <section class="card card-pad block">
              <div class="section-title">
                <h2>商品留言</h2>
                <span class="count-pill">{{ comments.length }}</span>
              </div>
              <div class="comment-editor">
                <textarea
                  v-model="commentText"
                  class="comment-input"
                  rows="3"
                  maxlength="500"
                  placeholder="对这件商品有疑问？给卖家留言吧（500 字内）"
                ></textarea>
                <button
                  type="button"
                  class="btn btn-primary"
                  :disabled="commentActing"
                  @click="submitComment"
                >
                  {{ commentActing ? '提交中…' : '发表留言' }}
                </button>
              </div>
              <EmptyState
                v-if="!comments.length"
                icon="fa-comment"
                title="还没有留言"
                desc="第一个留言，向卖家了解更多细节"
              />
              <ul v-else class="buyer-list">
                <li v-for="c in comments" :key="'c' + c.id" class="buyer-item">
                  <img
                    :src="c.avatar || '/user-avatar/default.jpg'"
                    class="c-avatar"
                    alt=""
                    @error="$event.target.src = '/user-avatar/default.jpg'"
                  />
                  <div class="b-body">
                    <div class="b-top">
                      <strong>{{ c.displayName || c.userName }}</strong>
                      <span class="muted text-xs">{{ formatTime(c.createdAt) }}</span>
                      <button
                        v-if="canDeleteComment(c)"
                        type="button"
                        class="btn btn-ghost btn-sm c-del"
                        @click="removeComment(c)"
                      >
                        删除
                      </button>
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
                  <span class="v">{{ soldOut ? '已售罄' : `${stock} 件` }}</span>
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
                  @error="onImgError"
                />
              </div>
            </section>

            <!-- 相关推荐 -->
            <section v-if="relatedProducts.length" class="card card-pad block">
              <div class="section-title">
                <h2>相关推荐</h2>
              </div>
              <div class="related-grid">
                <router-link
                  v-for="rp in relatedProducts"
                  :key="rp.id"
                  :to="`/product/${rp.id}`"
                  class="related-card"
                >
                  <img :src="rp.firstImage || (String(rp.imageUrl || '').split(',')[0])" alt="" @error="onImgError" />
                  <div class="related-name">{{ rp.name }}</div>
                  <div class="related-price">¥{{ formatPrice(rp.secondHandPrice) }}</div>
                </router-link>
              </div>
            </section>
          </div>
        </div>
      </template>
    </div>

    <AppModal :show="showOffer" title="向卖家出价" width="420px" @close="showOffer = false">
      <div class="form-group">
        <label>当前售价</label>
        <p class="offer-price">¥{{ formatPrice(product?.secondHandPrice) }}</p>
      </div>
      <div class="form-group">
        <label>我的出价（元）</label>
        <input v-model="offerPrice" class="form-input" type="number" min="0.01" step="0.01" placeholder="需低于售价" />
      </div>
      <div class="form-group">
        <label>留言（选填）</label>
        <textarea v-model="offerMessage" class="form-textarea" rows="2" maxlength="200" placeholder="例如：诚心要，能否包邮…"></textarea>
      </div>
      <template #actions>
        <button type="button" class="btn btn-outline" @click="showOffer = false">取消</button>
        <button type="button" class="btn btn-primary" :disabled="submittingOffer" @click="submitOffer">
          {{ submittingOffer ? '提交中…' : '发送出价' }}
        </button>
      </template>
    </AppModal>

    <AppModal :show="showReport" title="举报商品" width="480px" @close="showReport = false">
      <div class="form-group">
        <label>举报原因</label>
        <select v-model="reportReason" class="form-input">
          <option>商品违规</option>
          <option>虚假描述</option>
          <option>疑似诈骗</option>
          <option>价格异常</option>
          <option>禁售商品</option>
          <option>其他</option>
        </select>
      </div>
      <div class="form-group mt-3">
        <label>补充说明（选填）</label>
        <textarea v-model="reportDetail" class="form-textarea" rows="3" maxlength="200" placeholder="请描述具体问题…"></textarea>
      </div>
      <template #actions>
        <button class="btn btn-outline" @click="showReport = false">取消</button>
        <button class="btn btn-danger" :disabled="submittingReport" @click="submitReport">
          {{ submittingReport ? '提交中…' : '提交举报' }}
        </button>
      </template>
    </AppModal>
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

/* 三栏：左缩略图 | 中大图 / 下方内容 | 右购买卡 */
.detail-layout {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr) minmax(300px, 360px);
  column-gap: 16px;
  row-gap: 18px;
  align-items: start;
}

/* 缩略图：大图左侧外侧，相对大图垂直居中 */
.thumbs-col {
  grid-column: 1;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  align-self: stretch;
}

/* 下方内容：与大图左对齐 */
.below-col {
  grid-column: 2;
  grid-row: 2;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
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
  grid-column: 2;
  grid-row: 1;
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

/* 购买卡：独占右栏，吸顶在 Header + 分类栏之下 */
.buy-card {
  grid-column: 3;
  grid-row: 1 / 3;
  position: sticky;
  top: calc(var(--header-h) + var(--nav-h) + 10px);
  align-self: start;
  overflow: hidden;
  z-index: 2;
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
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.015em;
  line-height: 1.35;
}
.price-line {
  padding: 14px 16px;
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
  font-size: 18px;
  font-weight: 800;
}
.price-main .num {
  font-size: 36px;
  letter-spacing: -0.03em;
  line-height: 1.05;
  font-variant-numeric: tabular-nums;
}
.price-sub {
  margin-top: 4px;
  font-size: 13.5px;
  font-weight: 550;
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
.offer-price {
  font-size: 22px;
  font-weight: 700;
  color: var(--accent-deep);
  margin: 4px 0;
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
.link-report {
  width: 100%;
  margin-top: 8px;
  padding: 8px;
  border-radius: 10px;
  color: var(--muted);
  font-size: 12px;
}
.link-report:hover {
  color: var(--danger);
  background: var(--danger-soft);
}
.star-text {
  color: var(--accent);
  letter-spacing: 1px;
  font-size: 13px;
}

/* 评论 / 买家秀 */
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
.score-rows {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin: 6px 0 4px;
}
.score-item {
  font-size: 12.5px;
  color: var(--ink-soft);
  background: var(--accent-soft);
  border-radius: 999px;
  padding: 3px 10px;
}
.review-media {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.review-media img {
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid var(--border);
}
.review-media video {
  width: 160px;
  max-height: 120px;
  border-radius: 10px;
  background: #000;
  border: 1px solid var(--border);
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
.b-body {
  flex: 1;
  min-width: 0;
}
.b-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.b-content {
  margin: 6px 0 0;
  color: var(--ink-soft);
  line-height: 1.7;
  white-space: pre-wrap;
}
.comment-editor {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 8px;
}
.comment-input {
  flex: 1;
  resize: vertical;
  min-height: 72px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  font: inherit;
  color: var(--ink);
  background: var(--surface-2, transparent);
}
.c-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.c-del {
  margin-left: auto;
  font-size: 12px;
}
.seller-reply,
.buyer-follow {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--surface-warm, #fbf7f2);
  border: 1px solid var(--border);
  font-size: 13px;
  line-height: 1.6;
}
.buyer-follow {
  background: transparent;
  border-style: dashed;
}
.seller-reply p,
.buyer-follow p {
  margin: 4px 0;
  white-space: pre-wrap;
}
.reply-tag,
.follow-tag {
  display: inline-block;
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 6px;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
}
.follow-tag {
  background: var(--ink-soft);
}
.review-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.reply-box {
  margin-top: 8px;
}
.reply-input {
  width: 100%;
  padding: 9px 11px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  font-size: 13px;
  resize: vertical;
  font-family: inherit;
}
.reply-btns {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 8px;
}
.related-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.related-card {
  text-decoration: none;
  color: inherit;
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--surface);
}
.related-card:hover {
  border-color: var(--accent);
}
.related-card img {
  width: 100%;
  height: 120px;
  object-fit: cover;
  display: block;
  background: var(--bg-soft);
}
.related-name {
  padding: 8px 10px 0;
  font-size: 13px;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.related-price {
  padding: 4px 10px 10px;
  color: var(--accent-deep);
  font-weight: 700;
  font-size: 14px;
}

/* 店铺条 */
.shop-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
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
  .detail-layout {
    grid-template-columns: 56px minmax(0, 1fr);
  }
  .thumbs-col {
    grid-column: 1;
    grid-row: 1;
  }
  .main-frame {
    grid-column: 2;
    grid-row: 1;
  }
  .buy-card {
    grid-column: 1 / -1;
    grid-row: 2;
    position: static;
  }
  .below-col {
    grid-column: 1 / -1;
    grid-row: 3;
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
