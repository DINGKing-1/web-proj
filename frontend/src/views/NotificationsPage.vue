<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { notificationApi } from '@/api'
import { formatTime } from '@/api/client'
import { showToast } from '@/composables/ui'
import EmptyState from '@/components/EmptyState.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const router = useRouter()
const items = ref([])
const loading = ref(true)
const unread = ref(0)

const typeIcon = {
  order: 'fa-box',
  review: 'fa-star',
  system: 'fa-bell',
  chat: 'fa-comment-dots'
}

/** 根据 refType/refId 跳转到关联单据 */
function jumpTo(n) {
  if (!n) return
  const refType = n.refType
  const refId = n.refId
  if (!refType || refId == null || refId === '') return
  if (refType === 'order') {
    router.push(`/order/${refId}`)
  } else if (refType === 'product') {
    router.push(`/product/${refId}`)
  }
}

async function load() {
  loading.value = true
  const d = await notificationApi.list()
  if (d.success) {
    items.value = d.notifications || []
    unread.value = d.unread || 0
    store.notifyUnread = unread.value // 同步全局未读角标
  } else {
    showToast(d.message || '通知加载失败', 'error')
  }
  loading.value = false
}

async function readAll() {
  const d = await notificationApi.readAll()
  if (d.success) {
    showToast('已全部标为已读')
    unread.value = 0
    store.notifyUnread = 0 // 全部已读，角标归零
    load()
  } else {
    showToast(d.message || '操作失败', 'error')
  }
}

async function readOne(n) {
  if (n.readFlag) return
  const d = await notificationApi.readOne(n.id)
  if (!d.success) {
    showToast(d.message || '操作失败', 'error')
    return
  }
  n.readFlag = true
  unread.value = Math.max(0, unread.value - 1)
  store.notifyUnread = unread.value // 单条已读，角标减一
}

const hasUnread = computed(() => unread.value > 0)

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="container">
      <div class="section-head">
        <div>
          <h1 class="h1">消息通知</h1>
          <p class="muted">订单动态、评价与系统消息</p>
        </div>
        <button v-if="hasUnread" type="button" class="btn btn-outline btn-sm" @click="readAll">
          全部已读
        </button>
      </div>

      <LoadingBlock v-if="loading" />
      <EmptyState
        v-else-if="!items.length"
        icon="fa-bell"
        title="暂无通知"
        desc="有新动态时会在这里提醒你"
      />
      <ul v-else class="notice-list">
        <li
          v-for="n in items"
          :key="n.id"
          class="notice-item card"
          :class="{ unread: !n.readFlag, clickable: n.refType && n.refId != null }"
          @click="readOne(n); jumpTo(n)"
        >
          <div class="n-icon">
            <i class="fas" :class="typeIcon[n.type] || 'fa-bell'"></i>
          </div>
          <div class="n-body">
            <div class="n-top">
              <strong>{{ n.title }}</strong>
              <span v-if="!n.readFlag" class="dot"></span>
              <span class="muted text-xs">{{ formatTime(n.createdAt) }}</span>
            </div>
            <p class="n-content">{{ n.content }}</p>
            <span v-if="n.refType && n.refId != null" class="n-jump">查看详情 →</span>
          </div>
        </li>
      </ul>
    </div>
  </main>
</template>

<style scoped>
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 18px;
}
.notice-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.notice-item {
  display: flex;
  gap: 14px;
  padding: 16px 18px;
  align-items: flex-start;
}
.notice-item.unread {
  border-color: var(--accent);
  background: var(--surface-warm);
}
.notice-item.clickable {
  cursor: pointer;
}
.notice-item.clickable:hover {
  border-color: var(--accent);
}
.n-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--accent-soft);
  color: var(--accent-deep);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.n-body {
  flex: 1;
  min-width: 0;
}
.n-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.n-content {
  margin: 0;
  color: var(--ink-soft);
  line-height: 1.6;
  font-size: 14px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  flex-shrink: 0;
}
.n-jump {
  display: inline-block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--accent-deep);
}
</style>
