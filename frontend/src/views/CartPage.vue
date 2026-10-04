<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { cartApi } from '@/api'
import { imgOf, toCents, formatCents, formatPrice, onImgError } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'

const router = useRouter()
const store = useUserStore()
const items = ref([])
const loading = ref(true)
/** 数量修改进行中的行（防连点） */
const busyId = ref(null)
/** 勾选的购物车项 id 集合 */
const selectedIds = ref(new Set())

const availableItems = computed(() => items.value.filter((i) => i.available))
const unavailableItems = computed(() => items.value.filter((i) => !i.available))
const selectedItems = computed(() =>
  availableItems.value.filter((i) => selectedIds.value.has(i.id))
)
const allSelected = computed(
  () => availableItems.value.length > 0 && selectedItems.value.length === availableItems.value.length
)
// 金额用“分”整数运算，避免浮点误差；仅统计勾选项
const totalCents = computed(() =>
  selectedItems.value.reduce(
    (sum, i) => sum + toCents(i.price || 0) * Math.max(1, Number(i.quantity || 1)),
    0
  )
)
const total = computed(() => formatCents(totalCents.value))

function toggleSelect(id) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

function toggleSelectAll() {
  selectedIds.value = allSelected.value
    ? new Set()
    : new Set(availableItems.value.map((i) => i.id))
}

async function load() {
  loading.value = true
  const d = await cartApi.list()
  if (d.success) {
    items.value = d.items || []
    // 保留用户已勾选项；首次进入或全部失效时默认全选可售项
    const availableIds = (d.items || []).filter((i) => i.available).map((i) => i.id)
    const prev = selectedIds.value
    if (prev && prev.size > 0) {
      selectedIds.value = new Set(availableIds.filter((id) => prev.has(id)))
    } else {
      selectedIds.value = new Set(availableIds)
    }
    store.refreshCartCount()
  } else {
    showToast(d.message || '购物车加载失败', 'error')
  }
  loading.value = false
}

async function removeItem(item) {
  const ok = await confirmDialog({
    title: '移除商品',
    message: `确定将「${item.productName}」移出购物车吗？`,
    okText: '移除',
    type: 'warning'
  })
  if (!ok) return
  const d = await cartApi.remove(item.id)
  if (d.success) {
    showToast('已移除')
    load()
  } else {
    showToast(d.message || '移除失败', 'error')
  }
}

async function clearCart() {
  if (!items.value.length) return
  const ok = await confirmDialog({
    title: '清空购物车',
    message: '确定清空全部商品吗？',
    okText: '清空',
    type: 'warning'
  })
  if (!ok) return
  const d = await cartApi.clear()
  if (d.success) {
    showToast('购物车已清空')
    load()
  } else {
    showToast(d.message || '清空失败', 'error')
  }
}

async function changeQty(item, delta) {
  if (busyId.value === item.id) return // 仅锁当前行，防连点
  const stock = Number(item.stock || 1)
  const next = Number(item.quantity || 1) + delta
  if (next < 1 || next > stock) return
  busyId.value = item.id
  const d = await cartApi.updateQuantity(item.id, next)
  if (busyId.value === item.id) busyId.value = null
  if (d.success) {
    item.quantity = next
  } else {
    showToast(d.message || '修改失败', 'error')
    load()
  }
}

function goCheckout() {
  if (!selectedItems.value.length) {
    showToast('请先勾选要结算的商品', 'warning')
    return
  }
  sessionStorage.removeItem('checkoutProductId')
  sessionStorage.setItem(
    'checkoutCartIds',
    JSON.stringify(selectedItems.value.map((i) => i.id))
  )
  router.push('/checkout')
}

onMounted(load)
</script>

<template>
  <main class="page cart-page">
    <div class="container">
      <div class="page-head">
        <div>
          <h1 class="h1">购物车</h1>
          <p class="muted">
            共 {{ items.length }} 件
            <template v-if="unavailableItems.length"> · {{ unavailableItems.length }} 件不可售</template>
          </p>
        </div>
        <button v-if="items.length" class="btn btn-ghost btn-sm" @click="clearCart">
          <i class="fas fa-trash"></i> 清空购物车
        </button>
      </div>

      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="!items.length"
        icon="fa-shopping-bag"
        title="购物车空空如也"
        desc="去逛逛，把心仪好物加进来吧"
      >
        <router-link to="/" class="btn btn-primary">去逛逛</router-link>
      </EmptyState>

      <template v-else>
        <!-- 表头 -->
        <div class="cart-head card">
          <label class="c-check">
            <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" />
            全选
          </label>
          <span class="c-goods">商品信息</span>
          <span class="c-price">单价</span>
          <span class="c-op">操作</span>
        </div>

        <!-- 可结算 -->
        <section class="cart-panel card">
          <div class="panel-tag">
            <i class="fas fa-store"></i> 可结算商品
            <span class="tag-count">{{ availableItems.length }}</span>
          </div>
          <article
            v-for="item in availableItems"
            :key="item.id"
            class="cart-row"
          >
            <label class="c-check">
              <input
                type="checkbox"
                :checked="selectedIds.has(item.id)"
                @change="toggleSelect(item.id)"
              />
            </label>
            <router-link :to="`/product/${item.productId}`" class="c-thumb">
              <img
                :src="imgOf(item)"
                alt=""
                @error="onImgError"
              />
            </router-link>
            <div class="c-goods">
              <router-link :to="`/product/${item.productId}`" class="c-name">
                {{ item.productName }}
              </router-link>
              <div class="c-meta">
                <span v-if="item.category" class="chip">{{ item.category }}</span>
                <span class="chip ok"><i class="fas fa-check"></i> 有货</span>
              </div>
              <div class="qty-row">
                <span class="muted text-sm">数量</span>
                <div class="stepper">
                  <button type="button" class="step-btn" :disabled="item.quantity <= 1 || busyId === item.id" @click="changeQty(item, -1)">−</button>
                  <span class="step-num">{{ item.quantity || 1 }}</span>
                  <button type="button" class="step-btn" :disabled="item.quantity >= (item.stock || 1) || busyId === item.id" @click="changeQty(item, 1)">+</button>
                </div>
              </div>
            </div>
            <div class="c-price">
              <div class="p-main">¥{{ formatPrice(item.price) }}</div>
            </div>
            <div class="c-op">
              <button class="icon-del" title="移除" @click="removeItem(item)">
                <i class="fas fa-times"></i>
              </button>
            </div>
          </article>
          <div v-if="!availableItems.length" class="panel-empty muted">没有可结算的商品</div>
        </section>

        <!-- 不可售 -->
        <section v-if="unavailableItems.length" class="cart-panel card dim">
          <div class="panel-tag">
            <i class="fas fa-ban"></i> 已失效
            <span class="tag-count">{{ unavailableItems.length }}</span>
          </div>
          <article
            v-for="item in unavailableItems"
            :key="item.id"
            class="cart-row off"
          >
            <div class="c-thumb">
              <img
                :src="imgOf(item)"
                alt=""
                @error="onImgError"
              />
            </div>
            <div class="c-goods">
              <div class="c-name">{{ item.productName }}</div>
              <div class="c-meta">
                <span class="chip warn"><i class="fas fa-exclamation-circle"></i> 商品已下架</span>
              </div>
            </div>
            <div class="c-price muted">¥{{ formatPrice(item.price) }}</div>
            <div class="c-op">
              <button class="icon-del" title="移除" @click="removeItem(item)">
                <i class="fas fa-times"></i>
              </button>
            </div>
          </article>
        </section>

        <!-- 底部结算条 -->
        <div class="checkout-bar card">
          <div class="bar-info">
            <span class="muted">已选 <strong class="accent">{{ selectedItems.length }}</strong> 件</span>
            <div class="bar-total">
              <span class="muted">合计</span>
              <span class="price-lg">¥{{ total }}</span>
            </div>
          </div>
          <button
            class="btn btn-primary btn-lg checkout-btn"
            :disabled="!selectedItems.length"
            @click="goCheckout"
          >
            去结算
            <i class="fas fa-arrow-right"></i>
          </button>
        </div>
      </template>
    </div>
  </main>
</template>

<style scoped>
.cart-page {
  padding-bottom: 110px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: end;
  margin-bottom: 18px;
}

/* 表头 */
.cart-head {
  display: grid;
  grid-template-columns: 40px 1fr 120px 72px;
  gap: 12px;
  align-items: center;
  padding: 12px 20px;
  color: var(--muted);
  font-size: 13px;
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
  border-bottom: none;
}
.c-check {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--muted);
}
.c-check input {
  width: 16px;
  height: 16px;
  accent-color: var(--accent);
  cursor: pointer;
}

.cart-panel {
  border-top-left-radius: 0;
  border-top-right-radius: 0;
  margin-bottom: 16px;
  overflow: hidden;
}
.cart-panel.dim {
  opacity: 0.85;
}
.panel-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  font-size: 13px;
  font-weight: 600;
  background: var(--surface-warm);
  border-bottom: 1px solid var(--border);
  color: var(--ink-soft);
}
.tag-count {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent-deep);
  font-size: 12px;
  display: inline-grid;
  place-items: center;
}

.cart-row {
  display: grid;
  grid-template-columns: 40px 1fr 120px 72px;
  gap: 12px;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}
.cart-row:last-child {
  border-bottom: none;
}
.cart-row.off .c-name {
  color: var(--muted);
}

.c-thumb {
  display: flex;
  gap: 14px;
  min-width: 0;
}
.c-thumb img,
.cart-row .c-thumb img {
  width: 88px;
  height: 88px;
  border-radius: 12px;
  object-fit: cover;
  background: var(--bg-soft);
  flex-shrink: 0;
}

/* 商品信息与缩略图同排：把缩略图放进 goods 列 */
.cart-row {
  grid-template-columns: minmax(0, 1fr) 120px 72px;
}
.cart-row .c-thumb {
  grid-column: 1;
}

.c-goods {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
/* 重排：缩略图 + 文案横向 */
.cart-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.cart-row .c-goods {
  flex: 1;
}
.cart-row .c-price {
  width: 110px;
  text-align: right;
  flex-shrink: 0;
}
.cart-row .c-op {
  width: 48px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

.c-name {
  font-weight: 600;
  font-size: 15px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.c-name:hover {
  color: var(--accent-deep);
}
.c-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.chip {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--surface-warm);
  color: var(--muted);
  border: 1px solid var(--border);
}
.chip.ok {
  color: var(--success);
  background: var(--success-soft);
  border-color: transparent;
}
.chip.warn {
  color: var(--danger);
  background: var(--danger-soft);
  border-color: transparent;
}
.qty-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  overflow: hidden;
}
.step-btn {
  width: 28px;
  height: 28px;
  color: var(--ink-soft);
  background: var(--surface-warm);
}
.step-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.step-num {
  min-width: 32px;
  text-align: center;
  font-weight: 600;
  font-size: 13px;
}
.p-main {
  font-size: 16px;
  font-weight: 700;
  color: var(--accent-deep);
}
.icon-del {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: var(--muted);
}
.icon-del:hover {
  color: var(--danger);
  background: var(--danger-soft);
}
.panel-empty {
  padding: 24px;
  text-align: center;
}

/* 底部结算条 */
.checkout-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 22px;
  width: min(100% - 40px, var(--max));
  z-index: var(--z-sticky);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  box-shadow: var(--shadow-lg);
}
.bar-info {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}
.bar-total {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.accent {
  color: var(--accent-deep);
}
.checkout-btn {
  min-width: 160px;
}

@media (max-width: 640px) {
  .cart-head {
    display: none;
  }
  .cart-row {
    flex-wrap: wrap;
  }
  .cart-row .c-price {
    width: auto;
    margin-left: 102px;
  }
  .checkout-bar {
    bottom: 12px;
    padding: 12px 14px;
  }
  .checkout-btn {
    min-width: 120px;
  }
}
</style>
