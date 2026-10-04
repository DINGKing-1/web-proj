<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderApi, productApi, chatApi, uploadApi } from '@/api'
import { formatPrice, formatTime, imgOf, toCents, formatCents, onImgError, ORDER_STATUS } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import OrderTimeline from '@/components/OrderTimeline.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import AppModal from '@/components/AppModal.vue'
import PayCouponModal from '@/components/PayCouponModal.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const order = ref(null)
const product = ref(null)
const loading = ref(true)
const reviewing = ref(false)
const reviewForm = ref({
  ratingGoods: 5,
  ratingService: 5,
  ratingDelivery: 5,
  content: '',
  images: [],
  videos: []
})
const submittingReview = ref(false)
const uploadingMedia = ref(false)
const reviewMediaInput = ref(null)
const reviewVideoInput = ref(null)
/** 操作中（按钮 loading / 防重复提交） */
const acting = ref(false)
/** 竞态防护：路由切换后丢弃过期响应 */
let loadSeq = 0
/** 物流轨迹 */
const tracking = ref(null)
const trackingLoading = ref(false)

const isBuyer = computed(
  () => store.user && order.value && store.user.username === order.value.username
)
const isSeller = computed(
  () => store.user && order.value && store.user.username === order.value.sellerUsername
)
/** 售后链路中的任一状态 */
const inAfterSale = computed(() =>
  ['after_sale', 'return_shipping', 'return_sent'].includes(order.value?.status)
)
const statusLabel = computed(() =>
  order.value ? (ORDER_STATUS[order.value.status]?.label || order.value.status) : ''
)
const amountCents = computed(() => {
  if (!order.value) return 0
  const gross = toCents(order.value.productPrice || 0) * Math.max(1, Number(order.value.quantity || 1))
  const discount = toCents(order.value.discountAmount || 0)
  return Math.max(0, gross - discount)
})

async function load() {
  const seq = ++loadSeq
  loading.value = true
  const d = await orderApi.detail(route.params.id)
  if (seq !== loadSeq) return // 路由已切换，丢弃过期响应
  if (d.success && d.order) {
    order.value = d.order
    if (d.order.productId) {
      const p = await productApi.detail(d.order.productId)
      if (seq === loadSeq && p.success) product.value = p.product
    }
  } else {
    order.value = null
    showToast(d.message || '订单加载失败', 'error')
  }
  loading.value = false
  if (order.value && (order.value.status === 'shipped' || order.value.status === 'after_sale'
      || order.value.status === 'received' || order.value.status === 'completed')
      && order.value.trackingNumber) {
    loadTracking()
  }
}

async function loadTracking() {
  if (!order.value) return
  trackingLoading.value = true
  const d = await orderApi.tracking(order.value.id)
  trackingLoading.value = false
  if (d.success) tracking.value = d
}

/** 支付前先选优惠券 */
const couponModal = ref(false)

function payOrder() {
  if (acting.value) return
  couponModal.value = true
}

async function onPayConfirm(userCouponId) {
  if (acting.value) return
  acting.value = true
  const d = await orderApi.payWithCoupon(order.value.id, userCouponId)
  acting.value = false
  couponModal.value = false
  if (d.success) {
    showToast('支付成功，等待卖家发货')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '支付失败', 'error')
  }
}

async function cancelOrder() {
  const isPending = order.value?.status === 'pending_payment'
  const ok = await confirmDialog({
    title: '取消订单',
    message: isPending ? '确定取消该待付款订单吗？' : '取消后余额将退还，确定取消吗？',
    okText: '取消订单',
    type: 'warning'
  })
  if (!ok || acting.value) return
  acting.value = true
  const d = await orderApi.cancel(order.value.id)
  acting.value = false
  if (d.success) {
    showToast(d.message || '订单已取消')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '取消失败', 'error')
  }
}

async function receiveOrder() {
  const ok = await confirmDialog({ title: '确认收货', message: '确认已收到商品吗？', okText: '确认收货' })
  if (!ok || acting.value) return
  acting.value = true
  const d = await orderApi.update(order.value.id, { status: 'received' })
  acting.value = false
  if (d.success) {
    showToast('已确认收货')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function completeOrder() {
  const ok = await confirmDialog({ title: '确认完成', message: '确认完成该订单吗？', okText: '确认完成' })
  if (!ok || acting.value) return
  acting.value = true
  const d = await orderApi.complete(order.value.id)
  acting.value = false
  if (d.success) {
    showToast('订单已完成')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

/** 申请售后 */
const afterSaleModal = ref(false)
const afterSaleReason = ref('')
const returnTrackingNo = ref('')

function openAfterSale() {
  afterSaleReason.value = ''
  afterSaleModal.value = true
}

async function submitAfterSale() {
  if (acting.value) return
  acting.value = true
  const d = await orderApi.applyAfterSale(order.value.id, afterSaleReason.value.trim())
  acting.value = false
  if (d.success) {
    showToast('售后申请已提交，等待卖家处理')
    afterSaleModal.value = false
    load()
  } else {
    showToast(d.message || '提交失败', 'error')
  }
}

async function cancelAfterSale() {
  const ok = await confirmDialog({
    title: '撤销售后申请',
    message: '确定撤回这次售后申请吗？订单会回到申请前的状态。',
    okText: '撤回',
    type: 'warning'
  })
  if (!ok || acting.value) return
  acting.value = true
  const d = await orderApi.cancelAfterSale(order.value.id)
  acting.value = false
  if (d.success) {
    showToast('售后申请已撤回')
    load()
  } else {
    showToast(d.message || '撤回失败', 'error')
  }
}

async function submitReturnTracking() {
  if (acting.value) return
  if (!returnTrackingNo.value.trim()) {
    showToast('请填写退货运单号', 'warning')
    return
  }
  acting.value = true
  const d = await orderApi.submitReturnTracking(order.value.id, returnTrackingNo.value.trim())
  acting.value = false
  if (d.success) {
    showToast('退货已寄出，等待卖家确认收货')
    load()
  } else {
    showToast(d.message || '提交失败', 'error')
  }
}

async function approveAfterSale() {
  const ok = await confirmDialog({
    title: '同意退货',
    message: '同意后买家需寄回商品，你确认收货后货款才会退回买家。',
    okText: '同意退货'
  })
  if (!ok || acting.value) return
  acting.value = true
  const d = await orderApi.approveAfterSale(order.value.id)
  acting.value = false
  if (d.success) {
    showToast('已同意退货，等待买家寄回')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function rejectAfterSale() {
  const ok = await confirmDialog({
    title: '拒绝售后',
    message: '拒绝后订单将回到售后前状态，买家可联系平台客服介入。',
    okText: '拒绝',
    type: 'warning'
  })
  if (!ok || acting.value) return
  acting.value = true
  const d = await orderApi.rejectAfterSale(order.value.id)
  acting.value = false
  if (d.success) {
    showToast('已拒绝售后申请')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function confirmReturnReceived() {
  const ok = await confirmDialog({
    title: '确认收到退货',
    message: '确认已收到买家寄回的商品？确认后货款将立即退回买家，不可撤销。',
    okText: '确认并退款',
    type: 'warning'
  })
  if (!ok || acting.value) return
  acting.value = true
  const d = await orderApi.confirmReturnReceived(order.value.id)
  acting.value = false
  if (d.success) {
    showToast('已确认收货，货款已退回买家')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

function openReview() {
  reviewForm.value = {
    ratingGoods: 5,
    ratingService: 5,
    ratingDelivery: 5,
    content: '',
    images: [],
    videos: []
  }
  reviewing.value = true
}

function readFileAsDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('read-failed'))
    reader.readAsDataURL(file)
  })
}

async function onReviewMedia(e, kind) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length || uploadingMedia.value) return

  const isVideo = kind === 'video'
  const maxSize = isVideo ? 30 * 1024 * 1024 : 5 * 1024 * 1024
  const valid = []
  for (const f of files) {
    if (isVideo && !f.type.startsWith('video/')) {
      showToast(`「${f.name}」不是视频`, 'warning')
      continue
    }
    if (!isVideo && !f.type.startsWith('image/')) {
      showToast(`「${f.name}」不是图片`, 'warning')
      continue
    }
    if (f.size > maxSize) {
      showToast(`「${f.name}」超过 ${isVideo ? 30 : 5}MB`, 'warning')
      continue
    }
    valid.push(f)
  }
  if (!valid.length) return

  const limit = isVideo ? 1 : 6
  const current = isVideo ? reviewForm.value.videos : reviewForm.value.images
  if (current.length + valid.length > limit) {
    showToast(isVideo ? '最多 1 个视频' : '最多 6 张图片', 'warning')
    return
  }

  uploadingMedia.value = true
  try {
    const bases = []
    for (const f of valid) bases.push(await readFileAsDataURL(f))
    const d = await uploadApi.reviewMedia(bases)
    if (d.success && d.urls?.length) {
      if (isVideo) reviewForm.value.videos = [...reviewForm.value.videos, ...d.urls]
      else reviewForm.value.images = [...reviewForm.value.images, ...d.urls]
      showToast(isVideo ? '视频已上传' : `已上传 ${d.urls.length} 张图片`)
    } else {
      showToast(d.message || '上传失败', 'error')
    }
  } catch {
    showToast('文件读取失败，请重试', 'error')
  } finally {
    uploadingMedia.value = false
  }
}

function removeReviewMedia(kind, index) {
  if (kind === 'video') reviewForm.value.videos.splice(index, 1)
  else reviewForm.value.images.splice(index, 1)
}

async function submitReview() {
  if (!order.value) return
  submittingReview.value = true
  const d = await orderApi.review(order.value.id, {
    ratingGoods: reviewForm.value.ratingGoods,
    ratingService: reviewForm.value.ratingService,
    ratingDelivery: reviewForm.value.ratingDelivery,
    content: reviewForm.value.content.trim(),
    images: reviewForm.value.images,
    videos: reviewForm.value.videos
  })
  submittingReview.value = false
  if (d.success) {
    showToast('评价成功')
    reviewing.value = false
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '评价失败', 'error')
  }
}

async function contactSeller() {
  if (!store.user) {
    router.push('/login')
    return
  }
  const productId = order.value.productId
  if (!productId) {
    showToast('无法联系卖家', 'warning')
    return
  }
  const d = await chatApi.findRoom(productId)
  if (d.success) {
    if (d.exists && d.roomId) {
      router.push({ path: '/messages', query: { roomId: d.roomId } })
    } else {
      router.push({
        path: '/messages',
        query: {
          sellerUsername: d.sellerUsername || order.value.sellerUsername,
          sellerNickname: d.sellerNickname || product.value?.sellerName || order.value.sellerUsername,
          sellerAvatar: d.sellerAvatar || product.value?.sellerAvatar || '',
          productId
        }
      })
    }
  } else {
    showToast(d.message || '联系失败', 'error')
  }
}

watch(() => route.params.id, load)
onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container narrow">
      <div class="head-row">
        <h1 class="h1">订单详情</h1>
        <router-link to="/my-orders" class="btn btn-outline btn-sm">
          <i class="fas fa-arrow-left"></i> 返回订单列表
        </router-link>
      </div>

      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="!order"
        icon="fa-box-open"
        title="订单不存在"
        desc="请确认订单号是否正确"
      >
        <router-link to="/my-orders" class="btn btn-primary">我的订单</router-link>
      </EmptyState>

      <template v-else>
        <section class="card card-pad block">
          <OrderTimeline :status="order.status" />
        </section>

        <section class="card card-pad block">
          <div class="product-row">
            <router-link v-if="order.productId" :to="`/product/${order.productId}`" class="p-link">
              <img
                :src="imgOf(order)"
                class="thumb"
                alt=""
                @error="onImgError"
              />
            </router-link>
            <img
              v-else
              :src="imgOf(order)"
              class="thumb"
              alt=""
              @error="onImgError"
            />
            <div class="info">
              <router-link v-if="order.productId" :to="`/product/${order.productId}`" class="name">
                {{ order.productName }}
              </router-link>
              <div v-else class="name">{{ order.productName }}</div>
              <div class="muted text-sm">{{ order.category }}</div>
              <div class="muted text-sm">
                ¥{{ formatPrice(order.productPrice) }} × {{ order.quantity || 1 }}
              </div>
            </div>
            <div class="amount">
              <div class="muted text-sm">订单金额</div>
              <div class="price">
                ¥{{ formatCents(amountCents) }}
              </div>
            </div>
          </div>
          <div v-if="order.sellerUsername" class="seller-line">
            <span class="muted">卖家</span>
            <button class="btn btn-outline btn-sm" @click="contactSeller">
              <i class="fas fa-comment"></i> 联系卖家
            </button>
          </div>
        </section>

        <div class="two-col">
          <section class="card card-pad">
            <h2 class="h3 mb-3">收货信息</h2>
            <div class="kv"><span>收货人</span><strong>{{ order.receiverName }}</strong></div>
            <div class="kv"><span>手机号</span><strong>{{ order.receiverPhone }}</strong></div>
            <div class="kv"><span>地址</span><strong>{{ order.receiverAddress }}</strong></div>
          </section>

          <section class="card card-pad">
            <h2 class="h3 mb-3">订单信息</h2>
            <div class="kv"><span>订单号</span><strong>{{ order.orderNumber || `#${order.id}` }}</strong></div>
            <div class="kv"><span>下单时间</span><strong>{{ formatTime(order.createdAt) }}</strong></div>
            <div class="kv"><span>数量</span><strong>{{ order.quantity || 1 }} 件</strong></div>
            <div class="kv"><span>支付方式</span><strong>{{ order.paymentMethod === 'balance' ? '余额支付' : (order.paymentMethod || '余额支付') }}</strong></div>
            <div class="kv"><span>状态</span><strong>{{ statusLabel }}</strong></div>
            <div v-if="order.trackingNumber" class="kv">
              <span>快递单号</span><strong>{{ order.trackingNumber }}</strong>
            </div>
          </section>

          <section v-if="tracking && tracking.tracks && tracking.tracks.length" class="card card-pad">
            <h2 class="h3 mb-2">物流轨迹</h2>
            <p class="muted text-sm mb-3">
              {{ tracking.company || '快递' }} · 单号 {{ tracking.trackingNumber }}
              · <strong>{{ tracking.statusText }}</strong>
            </p>
            <ul class="track-list">
              <li v-for="(t, i) in tracking.tracks" :key="i" class="track-item" :class="{ latest: i === 0 }">
                <div class="track-dot"></div>
                <div class="track-body">
                  <p class="track-desc">{{ t.desc }}</p>
                  <p class="muted text-xs">{{ t.time }} · {{ t.company }}</p>
                </div>
              </li>
            </ul>
            <p class="muted text-xs mt-2">演示物流轨迹：按快递单号与发货时间生成，供答辩展示</p>
          </section>
        </div>

        <section v-if="inAfterSale" class="card card-pad">
          <h2 class="h3 mb-2">售后进度</h2>
          <p class="muted text-sm mb-3">申请原因：{{ order.afterSaleReason || '买家申请退款' }}</p>

          <div v-if="order.status === 'after_sale'" class="as-box">
            <p>等待卖家处理。卖家超过 72 小时未响应，平台将自动同意退货。</p>
            <div class="as-actions">
              <template v-if="isSeller">
                <button class="btn btn-primary btn-sm" :disabled="acting" @click="approveAfterSale">
                  同意退货
                </button>
                <button class="btn btn-outline btn-sm" :disabled="acting" @click="rejectAfterSale">
                  拒绝
                </button>
              </template>
              <button v-if="isBuyer" class="btn btn-ghost btn-sm" :disabled="acting" @click="cancelAfterSale">
                撤销售后申请
              </button>
            </div>
          </div>

          <div v-else-if="order.status === 'return_shipping'" class="as-box">
            <p>卖家已同意退货。请把商品寄回，并在下方填写退货运单号。</p>
            <div v-if="isBuyer" class="return-form">
              <input
                v-model="returnTrackingNo"
                class="form-input"
                placeholder="退货运单号"
                maxlength="100"
              />
              <button class="btn btn-primary btn-sm" :disabled="acting" @click="submitReturnTracking">
                提交单号
              </button>
              <button class="btn btn-ghost btn-sm" :disabled="acting" @click="cancelAfterSale">
                撤销售后申请
              </button>
            </div>
            <p v-if="isSeller" class="muted text-sm">等待买家寄回并填写退货运单号。</p>
          </div>

          <div v-else-if="order.status === 'return_sent'" class="as-box">
            <p>
              买家已寄回 · 退货运单号：
              <strong>{{ order.returnTrackingNumber || '—' }}</strong>
            </p>
            <div v-if="isSeller" class="as-actions">
              <button class="btn btn-primary btn-sm" :disabled="acting" @click="confirmReturnReceived">
                确认收到退货并退款
              </button>
            </div>
            <p v-if="isBuyer" class="muted text-sm">等待卖家确认收货后退款。</p>
          </div>

          <div v-else-if="order.status === 'refunded'" class="as-box">
            <p>退货已确认，货款已退回买家余额。</p>
          </div>
        </section>

        <div class="actions">
          <button
            v-if="isBuyer && order.status === 'pending_payment'"
            class="btn btn-primary btn-lg"
            :disabled="acting"
            @click="payOrder"
          >
            <i class="fas fa-wallet"></i> 立即付款
          </button>
          <button
            v-if="isBuyer && (order.status === 'pending_payment' || order.status === 'unshipped')"
            class="btn btn-danger btn-lg"
            :disabled="acting"
            @click="cancelOrder"
          >
            取消订单
          </button>
          <button
            v-if="isBuyer && order.status === 'shipped'"
            class="btn btn-primary btn-lg"
            :disabled="acting"
            @click="receiveOrder"
          >
            确认收货
          </button>
          <button
            v-if="isBuyer && (order.status === 'shipped' || order.status === 'received')"
            class="btn btn-outline btn-lg"
            :disabled="acting"
            @click="openAfterSale"
          >
            申请售后
          </button>
          <button
            v-if="isBuyer && order.status === 'received' && !order.rating"
            class="btn btn-success btn-lg"
            :disabled="acting"
            @click="openReview"
          >
            评价并完成
          </button>          <button
            v-else-if="isBuyer && order.status === 'received'"
            class="btn btn-success btn-lg"
            :disabled="acting"
            @click="completeOrder"
          >
            确认完成
          </button>
          <span v-if="order.rating" class="reviewed">
            已评 {{ '★'.repeat(order.rating) }}{{ '☆'.repeat(5 - order.rating) }}
          </span>
        </div>

        <PayCouponModal
          :show="couponModal"
          :gross="amountCents / 100"
          :order-id="order.id"
          @close="couponModal = false"
          @confirm="onPayConfirm"
        />

        <AppModal :show="afterSaleModal" title="申请售后" width="480px" @close="afterSaleModal = false">
          <p class="muted text-sm mb-3">说明遇到的问题，卖家需在 72 小时内处理，超时平台将自动同意退货。</p>
          <textarea
            v-model="afterSaleReason"
            class="form-textarea"
            rows="4"
            maxlength="200"
            placeholder="如：商品与描述不符，收到后发现屏幕有划痕"
          ></textarea>
          <div class="as-actions" style="justify-content: flex-end; margin-top: 14px">
            <button class="btn btn-outline" @click="afterSaleModal = false">取消</button>
            <button class="btn btn-primary" :disabled="acting" @click="submitAfterSale">提交申请</button>
          </div>
        </AppModal>

        <AppModal :show="reviewing" title="评价订单" width="560px" @close="reviewing = false">
          <p class="muted text-sm mb-3">为「{{ order?.productName }}」打分（确认收货后可评价）</p>
          <div class="score-block">
            <div v-for="row in [
              { key: 'ratingGoods', label: '商品质量' },
              { key: 'ratingService', label: '卖家服务' },
              { key: 'ratingDelivery', label: '物流速度' }
            ]" :key="row.key" class="rating-row">
              <span class="score-label">{{ row.label }}</span>
              <button
                v-for="n in 5"
                :key="n"
                type="button"
                class="star-btn"
                :class="{ on: n <= reviewForm[row.key] }"
                @click="reviewForm[row.key] = n"
              >★</button>
              <span class="muted text-sm">{{ reviewForm[row.key] }} 星</span>
            </div>
          </div>
          <div class="form-group mt-3">
            <label>评价内容（选填）</label>
            <textarea v-model="reviewForm.content" class="form-input" rows="3" maxlength="500"></textarea>
          </div>
          <div class="form-group">
            <label>晒图 / 视频（选填）</label>
            <div class="media-actions">
              <button type="button" class="btn btn-outline btn-sm" :disabled="uploadingMedia" @click="reviewMediaInput?.click()">
                <i class="fas fa-image"></i> 图片（{{ reviewForm.images.length }}/6）
              </button>
              <button type="button" class="btn btn-outline btn-sm" :disabled="uploadingMedia || reviewForm.videos.length >= 1" @click="reviewVideoInput?.click()">
                <i class="fas fa-video"></i> 视频（{{ reviewForm.videos.length }}/1）
              </button>
              <span v-if="uploadingMedia" class="muted text-sm">上传中…</span>
            </div>
            <input ref="reviewMediaInput" type="file" accept="image/*" multiple hidden @change="onReviewMedia($event, 'image')" />
            <input ref="reviewVideoInput" type="file" accept="video/*" hidden @change="onReviewMedia($event, 'video')" />
            <div v-if="reviewForm.images.length || reviewForm.videos.length" class="media-preview">
              <div v-for="(img, i) in reviewForm.images" :key="'img' + i" class="media-cell">
                <img :src="img" alt="" />
                <button type="button" class="media-x" @click="removeReviewMedia('image', i)">×</button>
              </div>
              <div v-for="(vid, i) in reviewForm.videos" :key="'vid' + i" class="media-cell">
                <video :src="vid" muted></video>
                <button type="button" class="media-x" @click="removeReviewMedia('video', i)">×</button>
              </div>
            </div>
          </div>
          <template #actions>
            <button class="btn btn-primary" :disabled="submittingReview || uploadingMedia" @click="submitReview">
              {{ submittingReview ? '提交中…' : '提交评价' }}
            </button>
          </template>
        </AppModal>
      </template>
    </div>
  </main>
</template>

<style scoped>
.narrow {
  max-width: 860px;
}
.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.block {
  margin-bottom: 16px;
}
.product-row {
  display: flex;
  gap: 14px;
  align-items: center;
}
.thumb {
  width: 86px;
  height: 86px;
  border-radius: 12px;
  object-fit: cover;
}
.info {
  flex: 1;
}
.name {
  font-weight: 650;
}
.amount {
  text-align: right;
}
.seller-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.kv {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--border);
  font-size: 13.5px;
}
.kv:last-child {
  border-bottom: none;
}
.kv span {
  color: var(--muted);
}
.track-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.track-item {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-left: 2px solid var(--border-strong);
  margin-left: 6px;
  padding-left: 16px;
  position: relative;
}
.track-item.latest {
  border-left-color: var(--accent);
}
.track-dot {
  position: absolute;
  left: -5px;
  top: 16px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--border-strong);
}
.track-item.latest .track-dot {
  background: var(--accent);
  box-shadow: 0 0 0 3px rgba(212, 136, 60, 0.25);
}
.track-desc {
  font-size: 14px;
  margin: 0 0 2px;
}
.track-item.latest .track-desc {
  color: var(--accent-deep);
  font-weight: 600;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
  align-items: center;
}
.reviewed {
  color: var(--accent-deep);
  font-size: 13px;
}
.rating-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.score-label {
  width: 64px;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 650;
  color: var(--ink-soft);
}
.score-block {
  background: var(--surface-warm);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 12px;
}
.star-btn {
  font-size: 26px;
  color: var(--border-strong);
  line-height: 1;
}
.star-btn.on {
  color: var(--accent);
}
.media-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.media-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.media-cell {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: var(--bg-soft);
}
.media-cell img,
.media-cell video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.media-x {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(44, 34, 27, 0.72);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  display: grid;
  place-items: center;
}
.as-box {
  background: var(--surface-warm, #fbf7f2);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 14px;
  font-size: 13.5px;
  line-height: 1.6;
}
.as-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.return-form {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  margin-top: 10px;
}
.return-form .form-input {
  flex: 1;
  min-width: 200px;
  padding: 9px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  font-size: 13.5px;
}
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  font-size: 13.5px;
  resize: vertical;
}
@media (max-width: 760px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>

