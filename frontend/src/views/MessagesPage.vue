<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWindowSize, useEventListener, useIntervalFn } from '@vueuse/core'
import { chatApi, productApi, uploadApi } from '@/api'
import { formatTime, formatPrice, imgOf, onImgError } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import { validateChatMedia } from '@/composables/chatMedia'
import { useUserStore } from '@/stores/user'
import ChatMessage from '@/components/ChatMessage.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const { width } = useWindowSize()
const isNarrow = computed(() => width.value < 800)

const rooms = ref([])
const activeRoom = ref(null)
const pendingSeller = ref(null)
const messages = ref([])
const draft = ref('')
const goods = ref(null)
const msgListEl = ref(null)
const mediaInput = ref(null)
const lastId = ref(0)
const showListOnMobile = ref(true)
const sending = ref(false)
const uploadingMedia = ref(false)
/** 竞态防护：切换会话后过期响应直接丢弃 */
let roomSeq = 0

let es = null
let sseRetryTimer = null
let sseRetries = 0
const { pause: pausePoll, resume: resumePoll } = useIntervalFn(() => {
  if (!es) loadMsgs()
}, 4000)

const me = computed(() => store.user)

/** 同一天只在首条消息上方显示日期 */
function dateLabelOf(m, prev) {
  const day = String(m.createdAt || '').slice(0, 10)
  const prevDay = prev ? String(prev.createdAt || '').slice(0, 10) : ''
  return day && day !== prevDay ? day.replace(/-/g, '/') : ''
}

async function loadRooms() {
  const d = await chatApi.rooms()
  if (d.success) rooms.value = d.rooms || []
  else showToast(d.message || '会话加载失败', 'error')
}

const deletingRoom = ref(false)
/** 删除会话（软删，双方均不再显示） */
async function removeRoom(room) {
  if (deletingRoom.value) return
  const ok = await confirmDialog({
    title: '删除会话',
    message: `确定删除与「${room.otherNickname}」的会话吗？删除后聊天记录将不再显示。`,
    okText: '删除',
    type: 'warning'
  })
  if (!ok) return
  deletingRoom.value = true
  const d = await chatApi.deleteRoom(room.roomId)
  deletingRoom.value = false
  if (d.success) {
    showToast('会话已删除')
    activeRoom.value = null
    messages.value = []
    await loadRooms()
    showListOnMobile.value = true
  } else {
    showToast(d.message || '删除失败', 'error')
  }
}

async function openRoom(room) {
  roomSeq += 1
  pendingSeller.value = null
  activeRoom.value = room
  showListOnMobile.value = false
  messages.value = []
  lastId.value = 0
  await loadMsgs()
  await loadGoods(room.productId)
  connectSse()
}

function openPendingSeller() {
  roomSeq += 1
  activeRoom.value = null
  messages.value = []
  goods.value = null
  showListOnMobile.value = false
  closeSse()
}

async function loadGoods(productId) {
  goods.value = null
  if (!productId) return
  const d = await productApi.detail(productId)
  if (d.success && d.product) goods.value = d.product
}

/** 是否贴近消息底部（用户上翻历史时不强制滚动） */
function isNearBottom(threshold = 40) {
  const el = msgListEl.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
}

async function loadMsgs(forceScroll = false) {
  const room = activeRoom.value
  if (!room) return
  const seq = roomSeq
  const d = await chatApi.messages(room.roomId, lastId.value || undefined)
  if (seq !== roomSeq) return // 会话已切换，丢弃过期响应
  if (!d.success) return
  const list = d.messages || []
  const stick = forceScroll || isNearBottom()
  if (!lastId.value) {
    messages.value = list
    if (list.length) lastId.value = list[list.length - 1].id
    if (stick) await scrollBottom()
  } else if (list.length) {
    const known = new Set(messages.value.map((m) => m.id))
    const fresh = list.filter((m) => !known.has(m.id))
    if (fresh.length) {
      messages.value = [...messages.value, ...fresh]
      lastId.value = fresh[fresh.length - 1].id
      if (stick) await scrollBottom()
    }
  }
  await loadRooms()
  store.refreshUnread()
}

function connectSse() {
  closeSse()
  clearSseRetry()
  if (!activeRoom.value || typeof EventSource === 'undefined') {
    resumePoll()
    return
  }
  try {
    const url = `/api/chat/stream/${activeRoom.value.roomId}?sinceId=${lastId.value || 0}`
    es = new EventSource(url, { withCredentials: true })
    es.addEventListener('messages', async (e) => {
      sseRetries = 0
      try {
        const list = JSON.parse(e.data)
        if (!Array.isArray(list) || !list.length) return
        const known = new Set(messages.value.map((m) => m.id))
        const fresh = list.filter((m) => !known.has(m.id))
        if (fresh.length) {
          messages.value = [...messages.value, ...fresh]
          lastId.value = fresh[fresh.length - 1].id
          if (isNearBottom()) await scrollBottom()
          await loadRooms()
          store.refreshUnread()
        }
      } catch {
        /* ignore */
      }
    })
    es.onerror = () => {
      // 瞬时断网退避重连，避免永久退回轮询
      closeSse()
      const roomAtFail = activeRoom.value?.roomId
      sseRetries += 1
      if (sseRetries > 6) {
        resumePoll()
        return
      }
      resumePoll()
      sseRetryTimer = setTimeout(() => {
        if (activeRoom.value?.roomId === roomAtFail) {
          connectSse()
        }
      }, Math.min(30000, 1000 * 2 ** sseRetries))
    }
    pausePoll()
  } catch {
    resumePoll()
  }
}

function clearSseRetry() {
  if (sseRetryTimer) {
    clearTimeout(sseRetryTimer)
    sseRetryTimer = null
  }
  sseRetries = 0
}

function closeSse() {
  if (es) {
    es.close()
    es = null
  }
}

async function scrollBottom() {
  await nextTick()
  const el = msgListEl.value
  if (el) el.scrollTop = el.scrollHeight
}

/** 新会话：从商品页/店铺页带着卖家信息进来时先建房 */
async function ensureRoom() {
  if (activeRoom.value) return true
  if (!pendingSeller.value) return false
  const created = await chatApi.createRoom(
    pendingSeller.value.sellerUsername,
    pendingSeller.value.productId
  )
  if (!created.success || !created.roomId) {
    showToast(created.message || '创建会话失败', 'error')
    return false
  }
  await loadRooms()
  const room = rooms.value.find((r) => String(r.roomId) === String(created.roomId))
  activeRoom.value =
    room ||
    {
      roomId: created.roomId,
      otherNickname: pendingSeller.value.sellerNickname,
      otherAvatar: pendingSeller.value.sellerAvatar,
      productId: pendingSeller.value.productId
    }
  pendingSeller.value = null
  await loadGoods(activeRoom.value.productId)
  connectSse()
  return true
}

async function sendText() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  sending.value = true
  try {
    if (!(await ensureRoom())) return
    const d = await chatApi.send({
      roomId: activeRoom.value.roomId,
      content,
      type: 'text',
      productId: activeRoom.value.productId
    })
    if (d.success) {
      draft.value = ''
      // 自己发消息后总是滚动到底部
      await loadMsgs(true)
    } else {
      showToast(d.message || '发送失败', 'error')
    }
  } finally {
    sending.value = false
  }
}

async function onPickMedia(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || uploadingMedia.value) return
  const err = validateChatMedia(file)
  if (err) {
    showToast(err, 'warning')
    return
  }
  uploadingMedia.value = true
  try {
    if (!(await ensureRoom())) return
    const media = await uploadApi.chatMedia(file)
    const d = await chatApi.send({
      roomId: activeRoom.value.roomId,
      content: media.url,
      type: media.type,
      productId: activeRoom.value.productId
    })
    if (d.success) await loadMsgs(true)
    else showToast(d.message || '发送失败', 'error')
  } catch (e2) {
    showToast(e2.message || '发送失败，请重试', 'error')
  } finally {
    uploadingMedia.value = false
  }
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendText()
  }
}

function isMine(msg) {
  return me.value && msg.senderUsername === me.value.username
}

function avatarOf(msg) {
  if (isMine(msg)) return me.value?.avatar || '/user-avatar/default.jpg'
  return (
    activeRoom.value?.otherAvatar ||
    pendingSeller.value?.sellerAvatar ||
    '/user-avatar/default.jpg'
  )
}

function backToList() {
  showListOnMobile.value = true
}

useEventListener(document, 'visibilitychange', () => {
  if (document.visibilityState !== 'visible') {
    closeSse()
    pausePoll()
  } else if (activeRoom.value) {
    connectSse()
  } else {
    resumePoll()
  }
})

onMounted(async () => {
  await loadRooms()
  const q = route.query
  if (q.roomId) {
    const room = rooms.value.find((r) => String(r.roomId) === String(q.roomId))
    if (room) await openRoom(room)
  } else if (q.sellerUsername) {
    pendingSeller.value = {
      sellerUsername: q.sellerUsername,
      sellerNickname: q.sellerNickname || q.sellerUsername,
      sellerAvatar: q.sellerAvatar || '/user-avatar/default.jpg',
      productId: q.productId ? Number(q.productId) : null
    }
    openPendingSeller()
    if (q.productId) await loadGoods(Number(q.productId))
  }
  // 窄屏默认看列表
  if (isNarrow.value) showListOnMobile.value = true
})

// 同路由 query 变化（前进/后退或站内二次进入）时重新处理
watch(
  () => route.query,
  async (q) => {
    if (!q || !route.path.startsWith('/messages')) return
    if (q.roomId) {
      const room = rooms.value.find((r) => String(r.roomId) === String(q.roomId))
      if (room) await openRoom(room)
    } else if (q.sellerUsername) {
      pendingSeller.value = {
        sellerUsername: q.sellerUsername,
        sellerNickname: q.sellerNickname || q.sellerUsername,
        sellerAvatar: q.sellerAvatar || '/user-avatar/default.jpg',
        productId: q.productId ? Number(q.productId) : null
      }
      openPendingSeller()
      if (q.productId) await loadGoods(Number(q.productId))
    }
  }
)

onBeforeUnmount(() => {
  closeSse()
  clearSseRetry()
  pausePoll()
})
</script>

<template>
  <main class="chat-page">
    <div class="chat-shell" :class="{ narrow: isNarrow }">
      <aside class="panel-left" :class="{ hidden: isNarrow && !showListOnMobile }">
        <div class="panel-title">
          <h2 class="h3">消息</h2>
          <router-link to="/" class="btn btn-ghost btn-sm">首页</router-link>
        </div>
        <div v-if="!rooms.length" class="empty-rooms">
          <i class="fas fa-comments"></i>
          <p>暂无会话</p>
          <p class="muted text-sm">在商品页联系卖家开始聊天</p>
        </div>
        <div v-else class="room-list">
          <button
            v-for="r in rooms"
            :key="r.roomId"
            class="room-item"
            :class="{ active: activeRoom && activeRoom.roomId === r.roomId }"
            @click="openRoom(r)"
          >
            <img
              :src="r.otherAvatar || '/user-avatar/default.jpg'"
              class="avatar"
              alt=""
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
            <div class="r-body">
              <div class="r-top">
                <strong class="truncate">{{ r.otherNickname }}</strong>
                <span class="text-xs muted">{{ formatTime(r.lastMessageTime) }}</span>
              </div>
              <div class="r-sub">
                <span class="truncate muted text-sm">{{ r.lastMessage || '开始对话' }}</span>
                <span v-if="r.unread > 0" class="unread">{{ r.unread > 99 ? '99+' : r.unread }}</span>
              </div>
            </div>
          </button>
        </div>
      </aside>

      <section class="panel-right" :class="{ hidden: isNarrow && showListOnMobile }">
        <div v-if="!activeRoom && !pendingSeller" class="empty-chat">
          <i class="fas fa-comment-dots"></i>
          <p>选择一个会话开始聊天</p>
        </div>

        <template v-else>
          <header class="chat-header">
            <button class="btn btn-ghost btn-sm back-list" @click="backToList">
              <i class="fas fa-arrow-left"></i>
            </button>
            <img
              :src="(activeRoom && activeRoom.otherAvatar) || pendingSeller?.sellerAvatar || '/user-avatar/default.jpg'"
              class="avatar"
              alt=""
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
            <div class="who">
              <div class="h3 truncate">
                {{ (activeRoom && activeRoom.otherNickname) || pendingSeller?.sellerNickname || '会话' }}
              </div>
              <div class="text-xs muted">
                {{ pendingSeller ? '发送消息开始对话' : '实时聊天中' }}
              </div>
            </div>
            <span v-if="!pendingSeller" class="live-dot">LIVE</span>
            <button
              v-if="!pendingSeller && activeRoom"
              type="button"
              class="btn btn-ghost btn-sm"
              title="删除会话"
              :disabled="deletingRoom"
              @click="removeRoom(activeRoom)"
            >
              <i class="fas fa-trash"></i>
            </button>
          </header>

          <div v-if="goods" class="goods-bar">
            <img
              :src="imgOf(goods)"
              class="g-img"
              alt=""
              @error="onImgError"
            />
            <div class="g-info">
              <router-link :to="`/product/${goods.id}`" class="g-name">{{ goods.name }}</router-link>
              <div class="price">¥{{ formatPrice(goods.secondHandPrice) }}</div>
            </div>
          </div>

          <div ref="msgListEl" class="msg-list">
            <ChatMessage
              v-for="(m, idx) in messages"
              :key="m.id"
              :mine="isMine(m)"
              :content="m.content"
              :message-type="m.messageType || 'text'"
              :created-at="m.createdAt"
              :avatar="avatarOf(m)"
              :date-label="dateLabelOf(m, messages[idx - 1])"
            />
            <div v-if="!messages.length && !pendingSeller" class="muted text-sm empty-msg">
              暂无消息，打个招呼吧
            </div>
          </div>

          <footer class="composer">
            <button
              class="icon-btn"
              type="button"
              title="发送图片或视频"
              :disabled="uploadingMedia"
              @click="mediaInput?.click()"
            >
              <i class="fas" :class="uploadingMedia ? 'fa-spinner fa-spin' : 'fa-image'"></i>
            </button>
            <input
              ref="mediaInput"
              type="file"
              accept="image/*,video/*"
              hidden
              @change="onPickMedia"
            />
            <textarea
              v-model="draft"
              class="form-textarea input"
              rows="1"
              placeholder="输入消息，Enter 发送"
              @keydown="onKeydown"
            ></textarea>
            <button type="button" class="btn btn-primary" :disabled="sending" @click="sendText">
              {{ sending ? '发送中…' : '发送' }}
            </button>
          </footer>
        </template>
      </section>
    </div>
  </main>
</template>

<style scoped>
.chat-page {
  padding: 16px 0 32px;
}
.chat-shell {
  width: min(100% - 24px, 1000px);
  margin: 0 auto;
  display: grid;
  grid-template-columns: 280px 1fr;
  /* 高度写死 + 内部 overflow，消息区才能独立滚动 */
  height: calc(100vh - var(--header-h) - var(--nav-h) - 56px);
  min-height: 480px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 22px;
  overflow: hidden;
  box-shadow: var(--shadow);
}
.chat-shell.narrow {
  grid-template-columns: 1fr;
  height: calc(100vh - var(--header-h) - var(--nav-h) - 28px);
  width: min(100% - 12px, 1000px);
}
.panel-left,
.panel-right {
  display: flex;
  flex-direction: column;
  min-width: 0;
  /* 关键：允许 flex 子项收缩到 0，消息列表才会出现滚动条 */
  min-height: 0;
}
.panel-left {
  border-right: 1px solid var(--border);
  background: var(--surface-warm);
}
.panel-left.hidden,
.panel-right.hidden {
  display: none;
}
.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}
.room-list {
  overflow: auto;
  flex: 1;
  min-height: 0;
  overscroll-behavior: contain;
}
.room-item {
  width: 100%;
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  text-align: left;
  border-bottom: 1px solid rgba(237, 226, 212, 0.7);
}
.room-item:hover,
.room-item.active {
  background: var(--accent-soft);
}
.avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.r-body {
  flex: 1;
  min-width: 0;
}
.r-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}
.r-sub {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.unread {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--danger);
  color: #fff;
  font-size: 11px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.back-list {
  display: none;
}
.who {
  min-width: 0;
  flex: 1;
}
.live-dot {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--success);
  background: var(--success-soft);
  padding: 4px 8px;
  border-radius: 999px;
  flex-shrink: 0;
}
.goods-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 14px;
  background: var(--bg-soft);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.g-img {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  object-fit: cover;
}
.g-name {
  font-weight: 600;
  font-size: 13.5px;
}
.msg-list {
  flex: 1;
  /* 关键修复：不加 min-height:0 时 flex 子项最小高度=内容高度，
     容器被撑爆且 overflow:hidden 裁切，滚动条永不出现 */
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 16px;
  background: var(--surface-warm);
}
.empty-msg {
  text-align: center;
  margin: auto;
}
.empty-rooms,
.empty-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--muted);
  gap: 6px;
  padding: 24px;
  text-align: center;
}
.empty-rooms i,
.empty-chat i {
  font-size: 36px;
  opacity: 0.45;
}
.composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px 14px;
  border-top: 1px solid var(--border);
  background: var(--surface);
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
  flex-shrink: 0;
}
.input {
  flex: 1;
  min-height: 44px;
  max-height: 100px;
  resize: none;
}
.icon-btn {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  border: 1px solid var(--border-strong);
  color: var(--ink-soft);
  flex-shrink: 0;
}
@media (max-width: 800px) {
  .back-list {
    display: inline-flex;
  }
  .chat-shell {
    border-radius: 16px;
  }
}
</style>
