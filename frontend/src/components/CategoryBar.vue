<script setup>
import { CATEGORIES } from '@/api/client'
import { useRoute } from 'vue-router'

const route = useRoute()
function toPath(cat) {
  return cat === '全部' ? '/' : `/category/${encodeURIComponent(cat)}`
}
function isActive(cat) {
  if (cat === '全部') return route.path === '/'
  return route.path.startsWith('/category/') && route.params.name === cat
}
</script>

<template>
  <nav class="category-bar" aria-label="商品分类">
    <div class="container category-scroll">
      <router-link
        v-for="cat in CATEGORIES"
        :key="cat"
        :to="toPath(cat)"
        class="cat-item"
        :class="{ active: isActive(cat) }"
      >
        {{ cat }}
      </router-link>
    </div>
  </nav>
</template>

<style scoped>
.category-bar {
  position: sticky;
  top: var(--header-h);
  z-index: var(--z-sticky);
  background: rgba(255, 250, 245, 0.88);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border);
}
.category-scroll {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding: 12px 0;
  scrollbar-width: none;
}
.category-scroll::-webkit-scrollbar {
  display: none;
}
.cat-item {
  flex-shrink: 0;
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--ink-soft);
  transition: all 0.15s ease;
}
.cat-item:hover {
  background: var(--accent-soft);
  color: var(--accent-deep);
}
.cat-item.active {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
  box-shadow: 0 6px 16px rgba(212, 136, 60, 0.25);
}
</style>
