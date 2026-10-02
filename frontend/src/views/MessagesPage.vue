<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWindowSize, useEventListener, useIntervalFn, refDebounced } from '@vueuse/core'
import { chatApi, productApi, uploadApi } from '@/api'
import { formatTime, formatPrice, firstImage } from '@/api/client'
import { showToast } from '@/composables/ui'
import { useUserStore } from '@/stores/user'

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
const imgInput = ref(null)
const lastId = ref(0)
const mobileListOpen = ref(true)
const showListOnMobile = ref(true)

let es = null
const { pause: pausePoll, resume: resumePoll } = useIntervalFn(() => {
  if (!es) loadMsgs()
}, 4000)

const me = computed(() => store.user)

async function loadRooms() {
  const d = await chatApi.rooms()
  if (d.success) rooms.value = d.rooms || []
}

async function openRoom(room) {
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

async function loadMsgs() {
  if (!activeRoom.value) return
  const d = await chatApi.messages(activeRoom.value.roomId, lastId.value || undefined)
  if (!d.success) return
  const list = d.messages || []
  if (!lastId.value) {
    messages.value = list
    if (list.length) lastId.value = list[list.length - 1].id
    await scrollBottom()
  } else if (list.length) {
    const known = new Set(messages.value.map((m) => m.id))
    const fresh = list.filter((m) => !known.has(m.id))
    if (fresh.length) {
      messages.value = [...messages.value, ...fresh]
      lastId.value = fresh[fresh.length - 1].id
      await scrollBottom()
    }
  }
  await loadRooms()
  store.refreshUnread()
}

function connectSse() {
  closeSse()
  if (!activeRoom.value || typeof EventSource === 'undefined') {
    resumePoll()
    return
  }
  try {
    const url = `/api/chat/stream/${activeRoom.value.roomId}?sinceId=${lastId.value || 0}`
    es = new EventSource(url, { withCredentials: true })
    es.addEventListener('messages', async (e) => {
      try {
        const list = JSON.parse(e.data)
        if (!Array.isArray(list) || !list.length) return
        const known = new Set(messages.value.map((m) => m.id))
        const fresh = list.filter((m) => !known.has(m.id))
        if (fresh.length) {
          messages.value = [...messages.value, ...fresh]
          lastId.value = fresh[fresh.length - 1].id
          await scrollBottom()
          await loadRooms()
          store.refreshUnread()
        }
      } catch {
        /* ignore */
      }
    })
    es.onerror = () => {
      // SSE 断开则退回轮询
      closeSse()
      resumePoll()
    }
    pausePoll()
  } catch {
    resumePoll()
  }
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

async function sendText() {
  const content = draft.value.trim()
  if (!content) return

  if (!activeRoom.value && pendingSeller.value) {
    const created = await chatApi.createRoom(
      pendingSeller.value.sellerUsername,
      pendingSeller.value.productId
    )
    if (!created.success || !created.roomId) {
      showToast(created.message || '创建会话失败', 'error')
      return
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
  }

  if (!activeRoom.value) return

  const d = await chatApi.send({
    roomId: activeRoom.value.roomId,
    content,
    type: 'text',
    productId: activeRoom.value.productId
  })
  if (d.success) {
    draft.value = ''
    await loadMsgs()
  } else {
    showToast(d.message || '发送失败', 'error')
  }
}

async function onPickImage(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || !activeRoom.value) return
  if (file.size > 10 * 1024 * 1024) {
    showToast('图片不能超过 10MB', 'warning')
    return
  }
  const dataUrl = await new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
  const up = await uploadApi.images(dataUrl)
  if (!up.success) {
    showToast(up.message || '上传失败', 'error')
    return
  }
  const d = await chatApi.send({
    roomId: activeRoom.value.roomId,
    content: up.imageUrl || up.imageUrls?.[0],
    type: 'image',
    productId: activeRoom.value.productId
  })
  if (d.success) loadMsgs()
  else showToast(d.message || '发送失败', 'error')
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

function openImage(url) {
  if (url) window.open(url, '_blank')
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
  } else if (q.sellerUsername && q.productId) {
    pendingSeller.value = {
      sellerUsername: q.sellerUsername,
      sellerNickname: q.sellerNickname || q.sellerUsername,
      sellerAvatar: q.sellerAvatar || '/user-avatar/default.jpg',
      productId: Number(q.productId)
    }
    openPendingSeller()
    await loadGoods(Number(q.productId))
  }
  // 窄屏默认看列表
  if (isNarrow.value) showListOnMobile.value = true
})

onBeforeUnmount(() => {
  closeSse()
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
          </header>

          <div v-if="goods" class="goods-bar">
            <img
              :src="firstImage(goods) || '/user-avatar/default.jpg'"
              class="g-img"
              alt=""
              @error="$event.target.src = '/user-avatar/default.jpg'"
            />
            <div class="g-info">
              <router-link :to="`/product/${goods.id}`" class="g-name">{{ goods.name }}</router-link>
              <div class="price">¥{{ formatPrice(goods.secondHandPrice) }}</div>
            </div>
          </div>

          <div ref="msgListEl" class="msg-list">
            <div
              v-for="m in messages"
              :key="m.id"
              class="msg"
              :class="{ mine: isMine(m) }"
            >
              <template v-if="m.messageType === 'image'">
                <img
                  :src="m.content"
                  class="msg-img"
                  alt="图片消息"
                  @click="openImage(m.content)"
                />
              </template>
              <div v-else class="bubble">{{ m.content }}</div>
            </div>
            <div v-if="!messages.length && !pendingSeller" class="muted text-sm empty-msg">
              暂无消息，打个招呼吧
            </div>
          </div>

          <footer class="composer">
            <button class="icon-btn" type="button" title="发送图片" @click="imgInput?.click()">
              <i class="fas fa-image"></i>
            </button>
            <input ref="imgInput" type="file" accept="image/*" hidden @change="onPickImage" />
            <textarea
              v-model="draft"
              class="form-textarea input"
              rows="1"
              placeholder="输入消息，Enter 发送"
              @keydown="onKeydown"
            ></textarea>
            <button type="button" class="btn btn-primary" @click="sendText">发送</button>
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
  min-height: 560px;
  height: calc(100vh - 170px);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 22px;
  overflow: hidden;
  box-shadow: var(--shadow);
}
.chat-shell.narrow {
  grid-template-columns: 1fr;
  height: calc(100vh - 130px);
  width: min(100% - 12px, 1000px);
}
.panel-left {
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
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
}
.panel-right {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
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
}
.goods-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 14px;
  background: var(--bg-soft);
  border-bottom: 1px solid var(--border);
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
  overflow: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background:
    radial-gradient(600px 300px at 10% 0%, rgba(255, 232, 212, 0.55), transparent 60%),
    var(--surface);
}
.msg {
  display: flex;
}
.msg.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: min(260px, 78vw);
  padding: 10px 14px;
  border-radius: 16px;
  background: var(--bg-soft);
  color: var(--ink);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.55;
}
.msg.mine .bubble {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
  border-bottom-right-radius: 6px;
}
.msg-img {
  max-width: min(220px, 60vw);
  border-radius: 12px;
  cursor: pointer;
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
