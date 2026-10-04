<script setup>
import { ref, onMounted } from 'vue'
import { favoriteApi } from '@/api'
import { formatPrice, imgOf, onImgError } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'

const favorites = ref([])
const loading = ref(true)
/** 加载是否失败：失败时不得显示“还没有收藏”误导用户 */
const loadError = ref(false)

async function load() {
  loading.value = true
  loadError.value = false
  const d = await favoriteApi.list()
  if (d.success) {
    favorites.value = d.favorites || []
  } else {
    loadError.value = true
    showToast(d.message || '收藏加载失败', 'error')
  }
  loading.value = false
}

async function removeFav(item) {
  const ok = await confirmDialog({
    title: '取消收藏',
    message: `确定取消收藏「${item.name}」吗？`,
    okText: '取消收藏',
    type: 'warning'
  })
  if (!ok) return
  const d = await favoriteApi.remove(item.favoriteId)
  if (d.success) {
    showToast('已取消收藏')
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container">
      <div class="page-head">
        <div>
          <h1 class="h1">我的收藏</h1>
          <p class="muted">共 {{ favorites.length }} 件收藏</p>
        </div>
      </div>

      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="loadError"
        icon="fa-exclamation-circle"
        title="收藏加载失败"
        desc="网络异常，请稍后重试"
      >
        <button class="btn btn-primary" @click="load">重新加载</button>
      </EmptyState>
      <EmptyState
        v-else-if="!favorites.length"
        icon="fa-heart"
        title="还没有收藏任何商品"
        desc="看到喜欢的点个收藏吧"
      >
        <router-link to="/" class="btn btn-primary">去逛逛</router-link>
      </EmptyState>

      <div v-else class="grid">
        <article v-for="item in favorites" :key="item.favoriteId" class="fav-card">
          <router-link :to="`/product/${item.id}`" class="thumb-wrap">
            <img
              :src="imgOf(item)"
              alt=""
              @error="onImgError"
            />
            <span
              v-if="item.status && item.status !== 'active'"
              class="status-chip"
            >
              {{ item.status === 'sold' ? '已售出' : item.status === 'pending' ? '审核中' : item.status === 'rejected' ? '已驳回' : '已下架' }}
            </span>
          </router-link>
          <div class="body">
            <router-link :to="`/product/${item.id}`" class="name">{{ item.name }}</router-link>
            <div class="meta">
              <span class="badge">{{ item.category }}</span>
              <span class="price">¥{{ formatPrice(item.secondHandPrice) }}</span>
            </div>
            <button class="btn btn-ghost btn-sm" @click="removeFav(item)">
              <i class="fas fa-heart-broken"></i> 取消收藏
            </button>
          </div>
        </article>
      </div>
    </div>
  </main>
</template>

<style scoped>
.page-head {
  margin-bottom: 18px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}
.fav-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}
.fav-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow);
}
.thumb-wrap {
  position: relative;
  display: block;
  aspect-ratio: 1 / 0.92;
  background: var(--bg-soft);
}
.thumb-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.status-chip {
  position: absolute;
  top: 10px;
  left: 10px;
  background: rgba(44, 34, 27, 0.72);
  color: #fff;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 999px;
}
.body {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.name {
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
@media (max-width: 1024px) {
  .grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 700px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
}
</style>
