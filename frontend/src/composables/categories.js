import { ref } from 'vue'
import { CATEGORIES } from '@/api/client'
import { categoryApi } from '@/api'

/**
 * 分类列表：启动后从后端拉取（后台可维护），失败时退回 client.js 的静态常量。
 * 模块级共享，多个页面同时用只发一次请求。
 */
const categories = ref([...CATEGORIES])
const loaded = ref(false)
let pending = null

function ensureLoaded() {
  if (loaded.value || pending) return pending
  pending = categoryApi
    .list()
    .then((d) => {
      if (d.success && Array.isArray(d.names) && d.names.length) {
        categories.value = ['全部', ...d.names]
      }
      loaded.value = true
    })
    .catch(() => {
      // 拉取失败保持静态兜底，不影响页面可用
      loaded.value = true
    })
    .finally(() => {
      pending = null
    })
  return pending
}

export function useCategories() {
  ensureLoaded()
  return { categories }
}
