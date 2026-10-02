<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { cartApi } from '@/api'
import { formatPrice, firstImage } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const store = useUserStore()
const items = ref([])
const loading = ref(true)

const total = computed(() =>
  items.value
    .filter((i) => i.available)
    .reduce((sum, i) => sum + Number(i.price || 0), 0)
)

function imgOf(item) {
  if (!item.image) return '/user-avatar/default.jpg'
  return String(item.image).split(',')[0]
}

async function load() {
  loading.value = true
  const d = await cartApi.list()
  if (d.success) {
    items.value = d.items || []
    store.refreshCartCount()
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

function goCheckout() {
  const available = items.value.filter((i) => i.available)
  if (!available.length) {
    showToast('没有可结算的商品', 'warning')
    return
  }
  sessionStorage.removeItem('checkoutProductId')
  router.push('/checkout')
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container">
      <div class="page-head">
        <div>
          <h1 class="h1">购物车</h1>
          <p class="muted">共 {{ items.length }} 件商品</p>
        </div>
        <button v-if="items.length" class="btn btn-ghost" @click="clearCart">
          <i class="fas fa-trash"></i> 清空购物车
        </button>
      </div>

      <div v-if="loading" class="loading"><div class="spinner"></div>加载中…</div>
      <EmptyState
        v-else-if="!items.length"
        icon="fa-shopping-bag"
        title="购物车空空如也"
        desc="去逛逛，把心仪好物加进来吧"
      >
        <router-link to="/" class="btn btn-primary">去逛逛</router-link>
      </EmptyState>

      <template v-else>
        <div class="cart-list">
          <article
            v-for="item in items"
            :key="item.id"
            class="cart-item card"
            :class="{ unavailable: !item.available }"
          >
            <img
              :src="imgOf(item)"
              class="thumb"
              alt=""
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
            <div class="info">
              <router-link :to="`/product/${item.productId}`" class="name">
                {{ item.productName }}
              </router-link>
              <div v-if="!item.available" class="badge badge-danger mt-2">商品已下架</div>
            </div>
            <div class="price" :class="{ muted: !item.available }">
              ¥{{ formatPrice(item.price) }}
            </div>
            <button class="btn btn-ghost btn-sm" @click="removeItem(item)">
              <i class="fas fa-times"></i>
            </button>
          </article>
        </div>

        <div class="summary card card-pad">
          <div class="row">
            <span class="muted">合计</span>
            <span class="price-lg">¥{{ formatPrice(total) }}</span>
          </div>
          <button class="btn btn-primary btn-lg btn-block" @click="goCheckout">去结算</button>
        </div>
      </template>
    </div>
  </main>
</template>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: end;
  margin-bottom: 18px;
}
.cart-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 18px;
}
.cart-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px;
}
.cart-item.unavailable {
  opacity: 0.72;
}
.thumb {
  width: 88px;
  height: 88px;
  border-radius: 12px;
  object-fit: cover;
  background: var(--bg-soft);
}
.info {
  flex: 1;
}
.name {
  font-weight: 600;
}
.price {
  font-weight: 700;
  color: var(--accent-deep);
  min-width: 100px;
  text-align: right;
}
.summary {
  max-width: 420px;
  margin-left: auto;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
