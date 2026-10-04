<script setup>
import { ref, onMounted } from 'vue'
import { orderApi, uploadApi } from '@/api'
import { formatPrice, formatTime, imgOf, toCents, formatCents, onImgError, ORDER_STATUS } from '@/api/client'
import { showToast, confirmDialog, promptDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import AppModal from '@/components/AppModal.vue'
import PayCouponModal from '@/components/PayCouponModal.vue'

const store = useUserStore()
const tab = ref('buyer')
const statusFilter = ref('all')
const orders = ref([])
const loading = ref(true)
const trackInputs = ref({})
const reviewing = ref(null)
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
/** 正在操作中的订单 id（按钮 loading/防重复提交） */
const actingId = ref(null)
/** 竞态防护：只接受最后一次请求的响应 */
let loadSeq = 0

function statusMeta(s) {
  return ORDER_STATUS[s] || { label: s, tone: 'muted' }
}

function itemTotalCents(o) {
  const gross = toCents(o.productPrice || 0) * Math.max(1, Number(o.quantity || 1))
  const discount = toCents(o.discountAmount || 0)
  return Math.max(0, gross - discount)
}

const STATUS_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'pending_payment', label: '待付款' },
  { value: 'unshipped', label: '待发货' },
  { value: 'shipped', label: '已发货' },
  { value: 'received', label: '待评价' },
  { value: 'after_sale', label: '售后中' },
  { value: 'return_shipping', label: '待寄回' },
  { value: 'return_sent', label: '退货已寄出' },
  { value: 'refunded', label: '已退款' },
  { value: 'completed', label: '已完成' },
  { value: 'cancelled', label: '已取消' }
]

async function load() {
  const seq = ++loadSeq
  loading.value = true
  const status = statusFilter.value === 'all' ? undefined : statusFilter.value
  const d =
    tab.value === 'buyer' ? await orderApi.list(status) : await orderApi.sellerList(status)
  if (seq !== loadSeq) return // 过期响应丢弃
  if (d.success) orders.value = d.orders || []
  else showToast(d.message || '订单加载失败', 'error')
  loading.value = false
}

function switchTab(t) {
  if (tab.value === t) return
  tab.value = t
  load()
}

function switchStatus(s) {
  if (statusFilter.value === s) return
  statusFilter.value = s
  load()
}

async function removeOrder(o) {
  const ok = await confirmDialog({
    title: '删除订单',
    message: '确定删除该订单吗？删除后不可恢复。',
    okText: '删除',
    type: 'warning'
  })
  if (!ok) return
  actingId.value = o.id
  const d = await orderApi.remove(o.id)
  actingId.value = null
  if (d.success) {
    showToast('订单已删除')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

/** 支付前先选优惠券（PayCouponModal） */
const payTarget = ref(null)
const payGross = ref(0)

function payOrder(o) {
  payTarget.value = o
  payGross.value = itemTotalCents(o) / 100
}

async function onPayConfirm(userCouponId) {
  const o = payTarget.value
  if (!o) return
  actingId.value = o.id
  const d = await orderApi.payWithCoupon(o.id, userCouponId)
  actingId.value = null
  payTarget.value = null
  if (d.success) {
    showToast('支付成功，等待卖家发货')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '支付失败', 'error')
  }
}

async function ship(o) {
  const trackingNumber = (trackInputs.value[o.id] || '').trim()
  if (!trackingNumber) {
    showToast('请填写快递单号', 'warning')
    return
  }
  const isUpdate = o.status === 'shipped'
  const ok = await confirmDialog({
    title: isUpdate ? '修改运单号' : '确认发货',
    message: isUpdate
      ? `确定修改快递单号为：${trackingNumber} 吗？`
      : `确定发货吗？快递单号：${trackingNumber}`,
    okText: isUpdate ? '保存' : '确认发货'
  })
  if (!ok) return
  actingId.value = o.id
  const d = await orderApi.update(o.id, { status: 'shipped', trackingNumber })
  actingId.value = null
  if (d.success) {
    showToast(isUpdate ? '运单号已更新' : '发货成功')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || (isUpdate ? '更新失败' : '发货失败'), 'error')
  }
}

async function cancel(o) {
  const ok = await confirmDialog({
    title: '取消订单',
    message: '取消后余额将退还，确定取消该订单吗？',
    okText: '取消订单',
    type: 'warning'
  })
  if (!ok) return
  actingId.value = o.id
  const d = await orderApi.cancel(o.id)
  actingId.value = null
  if (d.success) {
    showToast(d.message || '订单已取消')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '取消失败', 'error')
  }
}

async function receive(o) {
  const ok = await confirmDialog({
    title: '确认收货',
    message: '确认已收到商品吗？',
    okText: '确认收货'
  })
  if (!ok) return
  actingId.value = o.id
  const d = await orderApi.update(o.id, { status: 'received' })
  actingId.value = null
  if (d.success) {
    showToast('已确认收货')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function applyAfterSale(o) {
  const reason = await promptDialog({
    title: '申请售后',
    message: '请填写退款原因。卖家同意后需寄回商品，确认收货后才会退款。',
    placeholder: '例如：商品与描述不符 / 不想要了…',
    required: true,
    okText: '提交申请'
  })
  if (reason == null) return
  actingId.value = o.id
  const d = await orderApi.applyAfterSale(o.id, reason)
  actingId.value = null
  if (d.success) {
    showToast(d.message || '售后申请已提交')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '申请失败', 'error')
  }
}

async function approveAfterSale(o) {
  const ok = await confirmDialog({
    title: '同意退货',
    message: '同意后买家需寄回商品，你确认收货后货款才会退回买家。确定同意吗？',
    okText: '同意退货'
  })
  if (!ok) return
  actingId.value = o.id
  const d = await orderApi.approveAfterSale(o.id)
  actingId.value = null
  if (d.success) {
    showToast('已同意退货，等待买家寄回')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

/** 卖家确认收到买家寄回的退货 → 退款 */
async function confirmReturn(o) {
  const ok = await confirmDialog({
    title: '确认收到退货',
    message: '确认已收到买家寄回的商品？确认后货款立即退回买家，不可撤销。',
    okText: '确认并退款',
    type: 'warning'
  })
  if (!ok) return
  actingId.value = o.id
  const d = await orderApi.confirmReturnReceived(o.id)
  actingId.value = null
  if (d.success) {
    showToast('已确认收货，货款已退回买家')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

/** 买家填写退货运单号 */
async function submitReturn(o) {
  const no = await promptDialog({
    title: '填写退货运单号',
    message: '请填写寄回商品的快递单号，卖家确认收货后货款才会退回。',
    placeholder: '如 SF1234567890',
    required: true,
    okText: '提交'
  })
  if (no == null) return
  actingId.value = o.id
  const d = await orderApi.submitReturnTracking(o.id, no)
  actingId.value = null
  if (d.success) {
    showToast('退货已寄出')
    load()
  } else {
    showToast(d.message || '提交失败', 'error')
  }
}

/** 买家撤销售后申请 */
async function cancelAfterSale(o) {
  const ok = await confirmDialog({
    title: '撤销售后申请',
    message: '确定撤回这次售后申请吗？订单会回到申请前的状态。',
    okText: '撤回',
    type: 'warning'
  })
  if (!ok) return
  actingId.value = o.id
  const d = await orderApi.cancelAfterSale(o.id)
  actingId.value = null
  if (d.success) {
    showToast('售后申请已撤回')
    load()
  } else {
    showToast(d.message || '撤回失败', 'error')
  }
}

async function rejectAfterSale(o) {
  const ok = await confirmDialog({
    title: '拒绝售后',
    message: '拒绝后订单将回到售后前状态，确定拒绝吗？',
    okText: '拒绝',
    type: 'warning'
  })
  if (!ok) return
  actingId.value = o.id
  const d = await orderApi.rejectAfterSale(o.id)
  actingId.value = null
  if (d.success) {
    showToast(d.message || '已拒绝售后')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function complete(o) {
  const ok = await confirmDialog({
    title: '确认完成',
    message: '确认完成该订单吗？',
    okText: '确认完成'
  })
  if (!ok) return
  actingId.value = o.id
  const d = await orderApi.complete(o.id)
  actingId.value = null
  if (d.success) {
    showToast('订单已完成')
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

function openReview(o) {
  reviewing.value = o
  reviewForm.value = {
    ratingGoods: 5,
    ratingService: 5,
    ratingDelivery: 5,
    content: '',
    images: [],
    videos: []
  }
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
  if (!reviewing.value) return
  submittingReview.value = true
  const d = await orderApi.review(reviewing.value.id, {
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
    reviewing.value = null
    load()
    store.refreshSession()
  } else {
    showToast(d.message || '评价失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container">
      <h1 class="h1">我的订单</h1>
      <div class="tabs">
        <button class="tab" :class="{ active: tab === 'buyer' }" @click="switchTab('buyer')">
          我买到的
        </button>
        <button class="tab" :class="{ active: tab === 'seller' }" @click="switchTab('seller')">
          我卖出的
        </button>
      </div>
      <div class="status-filters">
        <button
          v-for="s in STATUS_OPTIONS"
          :key="s.value"
          type="button"
          class="status-chip-btn"
          :class="{ on: statusFilter === s.value }"
          @click="switchStatus(s.value)"
        >{{ s.label }}</button>
      </div>

      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="!orders.length"
        icon="fa-box"
        title="暂无订单"
        :desc="tab === 'buyer' ? '快去挑选心仪好物吧' : '还没有买家下单'"
      />

      <div v-else class="order-list">
        <article v-for="o in orders" :key="o.id" class="order-card card">
          <div class="order-head">
            <div class="mono">订单号 {{ o.orderNumber || `#${o.id}` }}</div>
            <span class="badge" :class="`badge-${statusMeta(o.status).tone}`">
              {{ statusMeta(o.status).label }}
            </span>
          </div>

          <router-link :to="`/order/${o.id}`" class="order-body">
            <img
              :src="imgOf(o)"
              class="thumb"
              alt=""
              @error="onImgError"
            />
            <div class="info">
              <div class="name">{{ o.productName }}</div>
              <div class="muted text-sm">
                ¥{{ formatPrice(o.productPrice) }} × {{ o.quantity || 1 }}
              </div>
              <div v-if="tab === 'seller'" class="muted text-sm">
                买家：{{ o.receiverName }} · {{ o.receiverPhone }}
              </div>
              <div v-else class="muted text-sm">收货人：{{ o.receiverName }}</div>
              <div v-if="o.trackingNumber" class="muted text-sm">
                快递单号：{{ o.trackingNumber }}
              </div>
            </div>
            <div class="side">
              <div class="muted text-sm">{{ formatTime(o.createdAt) }}</div>
              <div class="total">
                合计 <strong>¥{{ formatCents(itemTotalCents(o)) }}</strong>
              </div>
            </div>
          </router-link>

          <div class="order-actions">
            <template v-if="tab === 'seller' && (o.status === 'unshipped' || o.status === 'shipped')">
              <input
                v-model="trackInputs[o.id]"
                class="form-input track"
                :placeholder="o.status === 'shipped' ? (o.trackingNumber || '快递单号') : '快递单号'"
              />
              <button
                class="btn btn-primary btn-sm"
                :disabled="actingId === o.id"
                @click="ship(o)"
              >
                {{ actingId === o.id ? '提交中…' : (o.status === 'shipped' ? '修改运单' : '确认发货') }}
              </button>
            </template>
            <template v-else-if="tab === 'seller' && o.status === 'after_sale'">
              <span class="muted text-sm">售后原因：{{ o.afterSaleReason || '买家申请退款' }}</span>
              <button
                class="btn btn-primary btn-sm"
                :disabled="actingId === o.id"
                @click="approveAfterSale(o)"
              >同意退货</button>
              <button
                class="btn btn-outline btn-sm"
                :disabled="actingId === o.id"
                @click="rejectAfterSale(o)"
              >拒绝</button>
            </template>
            <template v-else-if="tab === 'seller' && o.status === 'return_shipping'">
              <span class="muted text-sm">已同意退货，等待买家寄回（单号 {{ o.returnTrackingNumber || '待填写' }}）</span>
            </template>
            <template v-else-if="tab === 'seller' && o.status === 'return_sent'">
              <span class="muted text-sm">退货单号 {{ o.returnTrackingNumber || '—' }}</span>
              <button
                class="btn btn-primary btn-sm"
                :disabled="actingId === o.id"
                @click="confirmReturn(o)"
              >确认收货并退款</button>
            </template>
            <template v-else-if="tab === 'buyer'">
              <button
                v-if="o.status === 'pending_payment'"
                class="btn btn-primary btn-sm"
                :disabled="actingId === o.id"
                @click="payOrder(o)"
              >
                {{ actingId === o.id ? '支付中…' : '立即付款' }}
              </button>
              <button
                v-if="o.status === 'pending_payment' || o.status === 'unshipped'"
                class="btn btn-outline btn-sm"
                :disabled="actingId === o.id"
                @click="cancel(o)"
              >
                取消订单
              </button>
              <button
                v-if="o.status === 'shipped'"
                class="btn btn-primary btn-sm"
                :disabled="actingId === o.id"
                @click="receive(o)"
              >
                确认收货
              </button>
              <button
                v-if="o.status === 'shipped' || o.status === 'received'"
                class="btn btn-outline btn-sm"
                :disabled="actingId === o.id"
                @click="applyAfterSale(o)"
              >
                申请售后
              </button>
              <button
                v-if="o.status === 'received' && !o.rating"
                class="btn btn-success btn-sm"
                @click="openReview(o)"
              >
                评价并完成
              </button>
              <span v-if="o.status === 'after_sale'" class="muted text-sm">
                售后处理中：{{ o.afterSaleReason || '等待卖家处理' }}
              </span>
              <button
                v-if="o.status === 'after_sale'"
                class="btn btn-ghost btn-sm"
                :disabled="actingId === o.id"
                @click="cancelAfterSale(o)"
              >撤销售后</button>
              <template v-if="o.status === 'return_shipping'">
                <span class="muted text-sm">卖家已同意退货，请寄回后填写运单号</span>
                <button
                  class="btn btn-primary btn-sm"
                  :disabled="actingId === o.id"
                  @click="submitReturn(o)"
                >填写退货单号</button>
                <button
                  class="btn btn-ghost btn-sm"
                  :disabled="actingId === o.id"
                  @click="cancelAfterSale(o)"
                >撤销售后</button>
              </template>
              <span v-if="o.status === 'return_sent'" class="muted text-sm">
                已寄回（{{ o.returnTrackingNumber || '—' }}），等待卖家确认收货退款
              </span>
              <span v-if="o.status === 'refunded'" class="muted text-sm">
                退货已确认，货款已退回余额
              </span>
              <button
                v-if="o.status === 'received'"
                class="btn btn-success btn-sm"
                :disabled="actingId === o.id"
                @click="complete(o)"
              >
                确认完成
              </button>
              <span v-if="o.rating" class="reviewed-tag">
                已评 {{ '★'.repeat(o.rating) }}{{ '☆'.repeat(5 - o.rating) }}
              </span>
              <button
                v-if="o.status === 'completed' || o.status === 'cancelled'"
                class="btn btn-ghost btn-sm"
                :disabled="actingId === o.id"
                @click="removeOrder(o)"
              >
                删除
              </button>
            </template>
            <router-link :to="`/order/${o.id}`" class="btn btn-ghost btn-sm">订单详情</router-link>
          </div>
        </article>
      </div>
    </div>

    <PayCouponModal
      :show="!!payTarget"
      :gross="payGross"
      :order-id="payTarget?.id"
      @close="payTarget = null"
      @confirm="onPayConfirm"
    />

    <AppModal
      :show="!!reviewing"
      title="评价订单"
      width="560px"
      @close="reviewing = null"
    >
      <p class="muted text-sm mb-3">为「{{ reviewing?.productName }}」打分（确认收货后可评价）</p>

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
          >
            ★
          </button>
          <span class="muted text-sm">{{ reviewForm[row.key] }} 星</span>
        </div>
      </div>

      <div class="form-group mt-3">
        <label>评价内容（选填）</label>
        <textarea
          v-model="reviewForm.content"
          class="form-input"
          rows="3"
          maxlength="500"
          placeholder="分享你的购物体验…"
        ></textarea>
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
  </main>
</template>

<style scoped>
.tabs {
  display: inline-flex;
  gap: 8px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 5px;
  margin: 12px 0 20px;
}
.tab {
  padding: 9px 18px;
  border-radius: 999px;
  font-weight: 600;
  font-size: 13.5px;
  color: var(--ink-soft);
}
.tab.active {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
}
.status-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0 0 18px;
}
.status-chip-btn {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--ink-soft);
  font-size: 13px;
  cursor: pointer;
}
.status-chip-btn.on {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
  font-weight: 600;
}
.order-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.order-card {
  padding: 18px;
}
.reviewed-tag {
  color: var(--accent-deep);
  font-size: 12px;
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
.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12.5px;
  color: var(--muted);
}
.order-body {
  display: flex;
  gap: 14px;
  align-items: center;
}
.thumb {
  width: 88px;
  height: 88px;
  border-radius: 12px;
  object-fit: cover;
}
.info {
  flex: 1;
}
.name {
  font-weight: 650;
  margin-bottom: 4px;
}
.side {
  text-align: right;
}
.total {
  margin-top: 8px;
  font-size: 13px;
}
.total strong {
  color: var(--accent-deep);
  font-size: 16px;
}
.order-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 14px;
  flex-wrap: wrap;
  align-items: center;
}
.track {
  width: 180px;
}
@media (max-width: 700px) {
  .order-body {
    flex-wrap: wrap;
  }
  .side {
    width: 100%;
    text-align: left;
  }
  .order-card {
    padding: 14px;
  }
  .thumb {
    width: 72px;
    height: 72px;
  }
  .order-actions {
    justify-content: stretch;
  }
  .order-actions .btn {
    flex: 1;
  }
  .track {
    width: 100%;
    order: -1;
    margin-bottom: 6px;
  }
  .tabs {
    width: 100%;
  }
  .tab {
    flex: 1;
    text-align: center;
  }
}
</style>
