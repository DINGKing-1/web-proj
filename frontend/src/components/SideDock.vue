<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
/** 侧边栏常开，不随悬停收起 */
const expanded = ref(true)

const items = computed(() => [
  { key: 'home', icon: 'fa-house', label: '首页', to: '/' },
  { key: 'support', icon: 'fa-headset', label: '客服', to: '/support' },
  { key: 'messages', icon: 'fa-comment-dots', label: '消息', to: '/messages', badge: store.unread },
  { key: 'notify', icon: 'fa-bell', label: '通知', to: '/notifications', badge: store.notifyUnread },
  { key: 'publish', icon: 'fa-plus', label: '发布', to: '/upload', accent: true },
  { key: 'orders', icon: 'fa-box', label: '订单', to: '/my-orders', badge: (store.user?.buyerOrderCount || 0) + (store.user?.sellerOrderCount || 0) },
  { key: 'favorites', icon: 'fa-heart', label: '收藏', to: '/favorites' },
  { key: 'cart', icon: 'fa-shopping-bag', label: '购物车', to: '/cart', badge: store.cartCount },
  { key: 'user', icon: 'fa-user', label: '我的', to: '/user' }
])

function go(item) {
  if (!store.user && ['messages', 'orders', 'favorites', 'cart', 'user', 'publish', 'notify', 'support'].includes(item.key)) {
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
  if (item.key === 'support') return path.startsWith('/support')
  if (item.key === 'publish') return path.startsWith('/upload')
  return path === item.to || path.startsWith(item.to + '/')
}
</script>

<template>
  <aside class="side-dock liquid-glass expanded" aria-label="快捷导航">
    <div class="dock-glow" aria-hidden="true"></div>
    <div class="dock-items">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="dock-item"
        :class="{ active: isActive(item), accent: item.accent }"
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
/*
  液态玻璃（低透明度）：
  - 基底更实（α≈0.88–0.94），内容不被背景干扰
  - 多层高光：顶部镜面、侧面焦散、边缘折射描边
  - 缓慢流光动画，增强“液体”质感
*/
.side-dock {
  position: fixed;
  right: 18px;
  top: 50%;
  transform: translateY(-50%);
  z-index: var(--z-sticky);
  width: 78px;
  padding: 10px 8px;
  border-radius: 22px;
  color: var(--ink-soft);
  isolation: isolate;

  /* 低透明度：厚玻璃而不是薄纱 */
  background:
    linear-gradient(
      155deg,
      rgba(255, 253, 248, 0.94) 0%,
      rgba(255, 246, 235, 0.88) 42%,
      rgba(250, 240, 228, 0.92) 100%
    );
  border: 1px solid rgba(255, 255, 255, 0.78);
  box-shadow:
    0 14px 40px rgba(120, 70, 25, 0.16),
    0 4px 12px rgba(120, 70, 25, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.95),
    inset 0 -1.5px 0 rgba(255, 255, 255, 0.35),
    inset 1px 0 0 rgba(255, 255, 255, 0.45),
    inset -1px 0 0 rgba(255, 236, 214, 0.35);
  backdrop-filter: blur(28px) saturate(1.6) brightness(1.02);
  -webkit-backdrop-filter: blur(28px) saturate(1.6) brightness(1.02);
  overflow: hidden;
}

.side-dock.expanded {
  width: 78px;
  padding: 10px 8px;
  box-shadow:
    0 22px 56px rgba(120, 70, 25, 0.2),
    0 6px 18px rgba(120, 70, 25, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 1),
    inset 0 -1.5px 0 rgba(255, 255, 255, 0.4),
    inset 1px 0 0 rgba(255, 255, 255, 0.55),
    inset -1px 0 0 rgba(255, 236, 214, 0.4);
}

/* 液态层：镜面高光 + 边缘折射 + 暖色焦散 */
.dock-glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: inherit;
  z-index: 0;
  background:
    /* 顶部镜面 */
    linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.72) 0%,
      rgba(255, 255, 255, 0.12) 28%,
      transparent 48%
    ),
    /* 左侧折射亮边 */
    linear-gradient(
      90deg,
      rgba(255, 255, 255, 0.55) 0%,
      transparent 18%
    ),
    /* 暖色焦散 */
    radial-gradient(90% 55% at 85% 15%, rgba(255, 214, 160, 0.28), transparent 58%),
    radial-gradient(70% 40% at 15% 85%, rgba(255, 255, 255, 0.35), transparent 55%);
}

/* 缓慢流光，液体流动感 */
.dock-glow::after {
  content: '';
  position: absolute;
  inset: -20% -40%;
  background: linear-gradient(
    115deg,
    transparent 30%,
    rgba(255, 255, 255, 0.45) 48%,
    rgba(255, 230, 190, 0.22) 55%,
    transparent 72%
  );
  transform: translateX(-30%) rotate(8deg);
  animation: dockSheen 5.5s ease-in-out infinite;
  mix-blend-mode: soft-light;
}

@keyframes dockSheen {
  0%,
  100% {
    transform: translateX(-35%) rotate(8deg);
    opacity: 0.55;
  }
  50% {
    transform: translateX(25%) rotate(8deg);
    opacity: 1;
  }
}

.dock-items {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}


.dock-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  padding: 6px 2px;
  border-radius: 12px;
  color: var(--ink-soft);
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.dock-item:hover {
  background: rgba(255, 255, 255, 0.72);
  color: var(--accent-deep);
  transform: translateY(-1px);
  box-shadow:
    0 4px 12px rgba(120, 70, 25, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.dock-item.active {
  background:
    linear-gradient(160deg, rgba(212, 136, 60, 0.28), rgba(212, 136, 60, 0.12)),
    rgba(255, 255, 255, 0.55);
  color: var(--accent-deep);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.85),
    0 2px 8px rgba(212, 136, 60, 0.12);
}

/* 中间发布入口：暖金强调 */
.dock-item.accent {
  background: linear-gradient(160deg, var(--accent), var(--accent-deep));
  color: #fff;
  box-shadow:
    0 6px 16px rgba(212, 136, 60, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.35);
}
.dock-item.accent:hover {
  background: linear-gradient(160deg, var(--accent-deep), var(--accent));
  color: #fff;
  transform: translateY(-1px);
  box-shadow:
    0 8px 20px rgba(212, 136, 60, 0.42),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}

.icon-wrap {
  position: relative;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  font-size: 14px;
}

.dock-label {
  font-size: 11.5px;
  font-weight: 650;
  letter-spacing: 0.02em;
  line-height: 1.2;
  color: inherit;
  max-width: 64px;
  opacity: 1;
  overflow: hidden;
  white-space: nowrap;
}

.side-dock.expanded .dock-label {
  max-width: 64px;
  opacity: 1;
}

.dock-badge {
  position: absolute;
  top: -2px;
  right: -7px;
  min-width: 15px;
  height: 15px;
  padding: 0 3px;
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
  width: 22px;
  height: 1px;
  margin: 2px 0;
  background: linear-gradient(90deg, transparent, rgba(120, 70, 25, 0.18), transparent);
}

/* 移动端收成更窄，避免遮挡内容 */
@media (max-width: 768px) {
  .side-dock {
    right: 8px;
    width: 68px;
    padding: 8px 6px;
    border-radius: 18px;
  }
  .side-dock.expanded {
    width: 68px;
    padding: 8px 6px;
  }
  .dock-item {
    padding: 5px 2px;
  }
  .icon-wrap {
    width: 20px;
    height: 20px;
    font-size: 12px;
  }
  .dock-label {
    font-size: 11px;
    max-width: 56px;
  }
}
</style>
