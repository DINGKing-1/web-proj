<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const expanded = ref(false)

const items = computed(() => [
  { key: 'home', icon: 'fa-house', label: '首页', to: '/' },
  { key: 'category', icon: 'fa-table-cells-large', label: '分类', to: '/category/全部' },
  { key: 'messages', icon: 'fa-comment-dots', label: '消息', to: '/messages', badge: store.unread },
  { key: 'orders', icon: 'fa-box', label: '订单', to: '/my-orders', badge: (store.user?.buyerOrderCount || 0) + (store.user?.sellerOrderCount || 0) },
  { key: 'favorites', icon: 'fa-heart', label: '收藏', to: '/favorites' },
  { key: 'cart', icon: 'fa-shopping-bag', label: '购物车', to: '/cart', badge: store.cartCount },
  { key: 'user', icon: 'fa-user', label: '我的', to: '/user' }
])

function go(item) {
  if (!store.user && ['messages', 'orders', 'favorites', 'cart', 'user'].includes(item.key)) {
    router.push({ path: '/login', query: { redirect: item.to } })
    return
  }
  router.push(item.to)
}

function goTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function isActive(item) {
  const path = route.path
  if (item.to === '/') return path === '/'
  if (item.key === 'category') return path.startsWith('/category')
  return path === item.to || path.startsWith(item.to + '/')
}
</script>

<template>
  <aside
    class="side-dock liquid-glass"
    :class="{ expanded }"
    aria-label="快捷导航"
    @mouseenter="expanded = true"
    @mouseleave="expanded = false"
  >
    <div class="dock-glow" aria-hidden="true"></div>
    <div class="dock-items">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="dock-item"
        :class="{ active: isActive(item) }"
        :title="item.label"
        @click="go(item)"
      >
        <span class="icon-wrap">
          <i class="fas" :class="item.icon"></i>
          <span v-if="item.badge > 0" class="dock-badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
        </span>
        <span class="dock-label">{{ item.label }}</span>
      </button>

      <div class="dock-divider" aria-hidden="true"></div>

      <button type="button" class="dock-item" title="回到顶部" @click="goTop">
        <span class="icon-wrap">
          <i class="fas fa-arrow-up"></i>
        </span>
        <span class="dock-label">顶部</span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
/* 液态玻璃：半透明暖白 + 模糊 + 内高光 + 轻微色散描边 */
.side-dock {
  position: fixed;
  right: 18px;
  top: 50%;
  transform: translateY(-50%);
  z-index: var(--z-sticky);
  width: 56px;
  padding: 10px 8px;
  border-radius: 28px;
  color: var(--ink-soft);

  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.72) 0%, rgba(255, 248, 241, 0.42) 48%, rgba(255, 255, 255, 0.58) 100%);
  border: 1px solid rgba(255, 255, 255, 0.65);
  box-shadow:
    0 10px 40px rgba(120, 70, 25, 0.14),
    0 2px 8px rgba(120, 70, 25, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.85),
    inset 0 -1px 0 rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(22px) saturate(1.45);
  -webkit-backdrop-filter: blur(22px) saturate(1.45);

  transition: width 0.28s cubic-bezier(0.22, 1, 0.36, 1), padding 0.28s ease, box-shadow 0.25s ease;
  overflow: hidden;
}

.side-dock.expanded {
  width: 92px;
  padding: 12px 10px;
  box-shadow:
    0 18px 50px rgba(120, 70, 25, 0.18),
    0 4px 14px rgba(120, 70, 25, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    inset 0 -1px 0 rgba(255, 255, 255, 0.3);
}

/* 顶部流光，强化“液态”感 */
.dock-glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: inherit;
  background:
    radial-gradient(120% 60% at 20% 0%, rgba(255, 255, 255, 0.55), transparent 55%),
    radial-gradient(80% 40% at 100% 80%, rgba(212, 136, 60, 0.12), transparent 60%);
  mix-blend-mode: soft-light;
}

.dock-items {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.dock-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 4px;
  border-radius: 16px;
  color: var(--ink-soft);
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.dock-item:hover {
  background: rgba(255, 255, 255, 0.55);
  color: var(--accent-deep);
  transform: translateY(-1px);
}

.dock-item.active {
  background: linear-gradient(160deg, rgba(212, 136, 60, 0.22), rgba(212, 136, 60, 0.08));
  color: var(--accent-deep);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65);
}

.icon-wrap {
  position: relative;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  font-size: 15px;
}

.dock-label {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.02em;
  max-width: 0;
  opacity: 0;
  overflow: hidden;
  white-space: nowrap;
  transition: max-width 0.22s ease, opacity 0.18s ease;
}

.side-dock.expanded .dock-label {
  max-width: 64px;
  opacity: 1;
}

.dock-badge {
  position: absolute;
  top: -3px;
  right: -8px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--danger);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: grid;
  place-items: center;
  box-shadow: 0 2px 6px rgba(214, 69, 69, 0.35);
}

.dock-divider {
  width: 28px;
  height: 1px;
  margin: 4px 0;
  background: linear-gradient(90deg, transparent, rgba(120, 70, 25, 0.18), transparent);
}

/* 移动端收成更窄，避免遮挡内容 */
@media (max-width: 768px) {
  .side-dock {
    right: 10px;
    width: 48px;
    padding: 8px 6px;
    border-radius: 22px;
  }
  .side-dock.expanded {
    width: 78px;
  }
  .dock-item {
    padding: 7px 2px;
  }
  .icon-wrap {
    width: 24px;
    height: 24px;
    font-size: 13px;
  }
}
</style>
