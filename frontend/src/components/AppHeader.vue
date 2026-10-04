<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useWindowScroll } from '@vueuse/core'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const search = ref('')
const menuOpen = ref(false)
const { y } = useWindowScroll()
const scrolled = computed(() => y.value > 12)
const userMenuRef = ref(null)
let closeTimer = 0

function doSearch() {
  const q = search.value.trim()
  if (!q) return
  router.push({ path: '/search', query: { q } })
}

function openMenu() {
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = 0
  }
  menuOpen.value = true
}

/** 稍长的关闭延迟：从头像滑到下拉项时不会被误关 */
function scheduleClose(delay = 420) {
  if (closeTimer) clearTimeout(closeTimer)
  closeTimer = setTimeout(() => {
    // 若指针仍在菜单热区内则不关
    if (userMenuRef.value && userMenuRef.value.matches(':hover')) return
    menuOpen.value = false
    closeTimer = 0
  }, delay)
}

function closeMenuNow() {
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = 0
  }
  menuOpen.value = false
}

function toggleMenu() {
  if (menuOpen.value) closeMenuNow()
  else openMenu()
}

function onDocClick(e) {
  if (!menuOpen.value) return
  const root = userMenuRef.value
  if (root && !root.contains(e.target)) closeMenuNow()
}

function onKeydown(e) {
  if (e.key === 'Escape' && menuOpen.value) closeMenuNow()
}

async function handleLogout() {
  closeMenuNow()
  await store.logout()
}

onMounted(() => {
  document.addEventListener('click', onDocClick, true)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick, true)
  document.removeEventListener('keydown', onKeydown)
  if (closeTimer) clearTimeout(closeTimer)
})

// 移动端展开搜索
const mobileSearchOpen = ref(false)
</script>

<template>
  <header class="site-header" :class="{ scrolled }">
    <div class="container header-inner">
      <router-link to="/" class="brand">
        <span class="brand-mark">优</span>
        <span class="brand-text">优品汇</span>
      </router-link>

      <form class="search-box" :class="{ open: mobileSearchOpen }" @submit.prevent="doSearch">
        <i class="fas fa-search"></i>
        <input v-model="search" type="search" placeholder="搜索心仪好物…" aria-label="搜索" />
        <button type="submit" class="search-btn">搜索</button>
      </form>

      <nav class="header-actions">
        <button class="icon-btn search-toggle" aria-label="搜索" @click="mobileSearchOpen = !mobileSearchOpen">
          <i class="fas fa-search"></i>
        </button>
        <template v-if="store.user">
          <router-link to="/cart" class="icon-btn" aria-label="购物车">
            <i class="fas fa-shopping-bag"></i>
            <span v-if="store.cartCount > 0" class="badge-dot">{{ store.cartCount > 99 ? '99+' : store.cartCount }}</span>
          </router-link>
          <router-link to="/messages" class="icon-btn" aria-label="消息">
            <i class="fas fa-comment-dots"></i>
            <span v-if="store.unread > 0" class="badge-dot">{{ store.unread > 99 ? '99+' : store.unread }}</span>
          </router-link>
          <router-link to="/notifications" class="icon-btn" aria-label="通知">
            <i class="fas fa-bell"></i>
            <span v-if="store.notifyUnread > 0" class="badge-dot">{{ store.notifyUnread > 99 ? '99+' : store.notifyUnread }}</span>
          </router-link>
          <router-link to="/upload" class="btn btn-primary btn-sm hide-mobile">
            <i class="fas fa-plus"></i> 发布
          </router-link>
          <div
            ref="userMenuRef"
            class="user-menu"
            @mouseenter="openMenu"
            @mouseleave="scheduleClose()"
          >
            <button
              class="user-trigger"
              :aria-expanded="menuOpen"
              aria-haspopup="menu"
              @click="toggleMenu"
            >
              <img
                :src="store.user.avatar || '/user-avatar/default.jpg'"
                alt=""
                class="avatar"
                @error="$event.target.src = '/user-avatar/default.jpg'"
              />
              <span class="nickname">{{ store.displayName }}</span>
              <i class="fas fa-chevron-down" :class="{ open: menuOpen }"></i>
            </button>
            <transition name="fade">
              <div v-if="menuOpen" class="dropdown" role="menu">
                <router-link to="/user" role="menuitem" @click="closeMenuNow">
                  <i class="fas fa-user"></i> 个人中心
                </router-link>
                <router-link to="/favorites" role="menuitem" @click="closeMenuNow">
                  <i class="fas fa-heart"></i> 我的收藏
                </router-link>
                <router-link to="/my-orders" role="menuitem" @click="closeMenuNow">
                  <i class="fas fa-box"></i> 我的订单
                </router-link>
                <router-link to="/seller-orders" role="menuitem" @click="closeMenuNow">
                  <i class="fas fa-store"></i> 卖家订单
                </router-link>
                <a href="#" class="danger" role="menuitem" @click.prevent="handleLogout">
                  <i class="fas fa-sign-out-alt"></i> 退出登录
                </a>
              </div>
            </transition>
          </div>
        </template>
        <template v-else>
          <router-link to="/login" class="btn btn-outline btn-sm">登录</router-link>
          <router-link to="/register" class="btn btn-primary btn-sm">注册</router-link>
        </template>
      </nav>
    </div>
  </header>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: var(--z-nav);
  height: var(--header-h);
  background: rgba(255, 248, 241, 0.78);
  backdrop-filter: blur(18px) saturate(1.2);
  border-bottom: 1px solid transparent;
  transition: all 0.2s ease;
}
.site-header.scrolled {
  background: rgba(255, 252, 248, 0.92);
  border-bottom-color: var(--border);
  box-shadow: 0 8px 28px rgba(120, 70, 25, 0.06);
}
.header-inner {
  height: 100%;
  display: flex;
  align-items: center;
  gap: 16px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  box-shadow: 0 8px 18px rgba(212, 136, 60, 0.3);
}
.brand-text {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.04em;
  background: linear-gradient(120deg, var(--ink), var(--accent-deep));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.search-box {
  flex: 1;
  max-width: 520px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  padding: 8px 8px 8px 16px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.search-box:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(212, 136, 60, 0.14);
}
.search-box i {
  color: var(--muted);
}
.search-box input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  min-width: 0;
  font-size: 15px;
  font-weight: 500;
}
.search-btn {
  padding: 9px 18px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent-deep);
  font-weight: 700;
  font-size: 14px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  position: relative;
  color: var(--ink-soft);
  transition: background 0.15s, color 0.15s;
}
.icon-btn:hover {
  background: var(--accent-soft);
  color: var(--accent-deep);
}
.badge-dot {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--danger);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
}
.user-menu {
  position: relative;
  /* 覆盖触发器到下拉之间的空隙，避免 mouseleave 误关 */
  padding-bottom: 18px;
  margin-bottom: -18px;
}
.user-menu::before {
  content: '';
  position: absolute;
  /* 向左加宽：下拉右对齐，宽度可能超过触发器，斜向滑入时不能掉出热区 */
  left: -56px;
  right: -12px;
  top: 100%;
  height: 24px;
  z-index: var(--z-dropdown);
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px 5px 5px;
  border-radius: 999px;
  background: var(--surface);
  border: 1px solid var(--border);
  transition: border-color 0.15s;
}
.user-trigger:hover {
  border-color: var(--accent);
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}
.nickname {
  max-width: 88px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}
.user-trigger i {
  font-size: 10px;
  color: var(--muted);
  transition: transform 0.18s ease;
}
.user-trigger i.open {
  transform: rotate(180deg);
}
.dropdown {
  position: absolute;
  right: 0;
  /* 与触发器衔接，避免空隙切断 hover */
  top: 100%;
  min-width: 168px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-lg);
  padding: 8px;
  z-index: var(--z-dropdown);
}
.dropdown a {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  color: var(--ink-soft);
}
.dropdown a:hover {
  background: var(--accent-soft);
  color: var(--accent-deep);
}
.dropdown a.danger {
  color: var(--danger);
}
.search-toggle {
  display: none;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
@media (max-width: 768px) {
  .search-box {
    display: none;
    position: absolute;
    left: 12px;
    right: 12px;
    top: calc(var(--header-h) + 8px);
    max-width: none;
    z-index: var(--z-sticky);
    box-shadow: var(--shadow);
  }
  .search-box.open {
    display: flex;
  }
  .search-toggle {
    display: grid;
  }
  .hide-mobile {
    display: none;
  }
  .brand-text {
    display: none;
  }
  .nickname {
    display: none;
  }
}
</style>
