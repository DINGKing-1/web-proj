<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { chatApi, supportApi, uploadApi } from '@/api'
import { showToast } from '@/composables/ui'
import { validateChatMedia } from '@/composables/chatMedia'
import { useUserStore } from '@/stores/user'
import LoadingBlock from '@/components/LoadingBlock.vue'
import ChatMessage from '@/components/ChatMessage.vue'

const router = useRouter()
const store = useUserStore()
const roomId = ref(null)
const agentName = ref('平台客服')
const agentAvatar = ref('')
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const uploadingMedia = ref(false)
const loading = ref(true)
const faq = ref([])
const listEl = ref(null)
const mediaInput = ref(null)
const sessionError = ref(false)
/** 已收到的最后一条消息 id，用于增量拉取 */
const lastId = ref(0)
/** 竞态防护：快速进出/切会话时丢弃过期响应 */
let roomSeq = 0
let timer = 0

/** 同一天只在首条消息上方显示日期 */
function dateLabelOf(m, prev) {
  const day = String(m.createdAt || '').slice(0, 10)
  const prevDay = prev ? String(prev.createdAt || '').slice(0, 10) : ''
  return day && day !== prevDay ? day.replace(/-/g, '/') : ''
}

/** 是否贴近底部：用户上翻历史时不强制滚动 */
function isNearBottom(threshold = 40) {
  const el = listEl.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
}

async function openSession() {
  if (!store.user) {
    router.push('/login?redirect=/support')
    return
  }
  sessionError.value = false
  const d = await supportApi.openSession()
  if (d.success) {
    roomId.value = d.roomId
    agentName.value = d.agentName || '平台客服'
    agentAvatar.value = d.agentAvatar || ''
    await loadMsgs(true)
  } else {
    sessionError.value = true
    showToast(d.message || '进入客服失败', 'error')
  }
  const f = await supportApi.faq()
  if (f.success) faq.value = f.items || []
  loading.value = false
}

async function loadMsgs(reset = false) {
  if (!roomId.value) return
  const roomAtCall = roomId.value
  const seq = ++roomSeq
  const stick = reset || isNearBottom()
  // 增量拉取：非 reset 时只取 lastId 之后的消息
  const d = await chatApi.messages(roomAtCall, reset ? undefined : lastId.value || undefined)
  if (seq !== roomSeq || roomId.value !== roomAtCall) return
  if (!d.success) return
  const incoming = d.messages || []
  if (reset || !lastId.value) {
    messages.value = incoming
  } else {
    const known = new Set(messages.value.map((m) => m.id))
    const fresh = incoming.filter((m) => m.id > lastId.value && !known.has(m.id))
    if (fresh.length) messages.value = [...messages.value, ...fresh]
  }
  if (messages.value.length) lastId.value = messages.value[messages.value.length - 1].id
  if (stick) {
    await nextTick()
    scrollBottom()
  }
}

function scrollBottom() {
  const el = listEl.value
  if (el) el.scrollTop = el.scrollHeight
}

async function send(text) {
  const content = (text != null ? text : draft.value).trim()
  if (!content || !roomId.value) {
    if (!roomId.value) {
      sessionError.value = true
      showToast('会话未就绪，请点击重试', 'warning')
    }
    return
  }
  sending.value = true
  const d = await chatApi.send({ roomId: roomId.value, content, type: 'text' })
  sending.value = false
  if (d.success) {
    if (text == null) draft.value = ''
    await loadMsgs(false) // 增量拉取新消息
    await nextTick()
    scrollBottom() // 自己发的消息总是滚到底
  } else {
    showToast(d.message || '发送失败', 'error')
  }
}

/** 发送图片 / 视频消息 */
async function onPickMedia(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || uploadingMedia.value) return
  if (!roomId.value) {
    sessionError.value = true
    showToast('会话未就绪，请点击重试', 'warning')
    return
  }
  const err = validateChatMedia(file)
  if (err) {
    showToast(err, 'warning')
    return
  }
  uploadingMedia.value = true
  try {
    const media = await uploadApi.chatMedia(file)
    const d = await chatApi.send({ roomId: roomId.value, content: media.url, type: media.type })
    if (d.success) {
      await loadMsgs(false)
      await nextTick()
      scrollBottom()
    } else {
      showToast(d.message || '发送失败', 'error')
    }
  } catch (e2) {
    showToast(e2.message || '发送失败，请重试', 'error')
  } finally {
    uploadingMedia.value = false
  }
}

/** 点常见问题：后台会把预设答案以客服身份自动回复 */
async function askFaq(item) {
  if (!roomId.value || sending.value) return
  sending.value = true
  const d = await supportApi.ask({ roomId: roomId.value, faqId: item.id, title: item.title })
  sending.value = false
  if (d.success) {
    await loadMsgs(false)
    await nextTick()
    scrollBottom()
  } else {
    showToast(d.message || '发送失败', 'error')
  }
}

function isMe(m) {
  return m.senderUsername && store.user && m.senderUsername === store.user.username
}

function avatarOf(m) {
  if (isMe(m)) return store.user?.avatar || '/user-avatar/default.jpg'
  return agentAvatar.value || '/user-avatar/default.jpg'
}

onMounted(() => {
  openSession()
  timer = setInterval(() => {
    // 标签隐藏时暂停轮询，省流量也避免后台旧响应
    if (document.visibilityState !== 'visible') return
    if (roomId.value) loadMsgs(false) // 增量轮询，上翻历史时不强制滚动
  }, 4000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <main class="page support-page">
    <div class="container">
      <LoadingBlock v-if="loading" />
      <div v-else class="support-shell card">
        <header class="support-head">
          <img
            :src="agentAvatar || '/user-avatar/default.jpg'"
            class="agent-avatar"
            alt=""
            @error="$event.target.src = '/user-avatar/default.jpg'"
          />
          <div class="head-meta">
            <h1 class="h3">{{ agentName }}</h1>
            <div class="online">
              <span class="dot"></span> 在线 · 平均响应约 1 分钟
            </div>
          </div>
        </header>

        <div ref="listEl" class="msg-list">
          <div class="sys-tip">您好，这里是优品汇官方客服。请描述您的问题，或点击下方常见问题快速咨询。</div>
          <ChatMessage
            v-for="(m, idx) in messages"
            :key="m.id"
            :mine="isMe(m)"
            :content="m.content"
            :message-type="m.messageType || 'text'"
            :created-at="m.createdAt"
            :avatar="avatarOf(m)"
            :date-label="dateLabelOf(m, messages[idx - 1])"
          />
        </div>

        <div v-if="sessionError || !roomId" class="session-error">
          <span>{{ sessionError ? '会话开启失败' : '会话未就绪' }}</span>
          <button type="button" class="btn btn-outline btn-sm" @click="openSession">重试</button>
        </div>

        <div class="faq-row">
          <button
            v-for="f in faq"
            :key="f.id"
            type="button"
            class="faq-chip"
            :disabled="sending || !roomId"
            :title="f.content"
            @click="askFaq(f)"
          >
            {{ f.title }}
          </button>
        </div>

        <form class="composer" @submit.prevent="send()">
          <button
            type="button"
            class="icon-btn"
            title="发送图片或视频"
            :disabled="uploadingMedia || !roomId"
            @click="mediaInput?.click()"
          >
            <i class="fas" :class="uploadingMedia ? 'fa-spinner fa-spin' : 'fa-image'"></i>
          </button>
          <input ref="mediaInput" type="file" accept="image/*,video/*" hidden @change="onPickMedia" />
          <input
            v-model="draft"
            class="composer-input"
            placeholder="输入您想咨询的问题…"
            maxlength="500"
            :disabled="sending || !roomId"
          />
          <button type="submit" class="btn btn-primary" :disabled="sending || !draft.trim() || !roomId">
            发送
          </button>
        </form>
      </div>
    </div>
  </main>
</template>

<style scoped>
.support-page {
  padding: 20px 0 28px;
}
.support-shell {
  display: flex;
  flex-direction: column;
  /* 收窄到阅读宽度，宽屏不再空旷 */
  width: min(100%, 820px);
  margin-inline: auto;
  height: min(720px, calc(100vh - var(--header-h) - var(--nav-h) - 72px));
  min-height: 440px;
  overflow: hidden;
}
.support-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  background: var(--surface-warm);
  flex-shrink: 0;
}
.head-meta {
  min-width: 0;
}
.agent-avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  object-fit: cover;
  flex-shrink: 0;
}
.online {
  font-size: 12px;
  color: var(--success);
  margin-top: 2px;
}
.online .dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--success);
  margin-right: 4px;
}
.msg-list {
  flex: 1;
  /* 关键修复：缺 min-height:0 时 flex 子项不会收缩，滚动条不出现 */
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 18px;
  background: var(--surface-warm);
}
.sys-tip {
  text-align: center;
  color: var(--muted);
  font-size: 12px;
  margin-bottom: 16px;
}
.session-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--border);
  background: var(--bg-soft);
  color: var(--ink-soft);
  font-size: 13px;
  flex-shrink: 0;
}
.faq-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 10px 14px;
  border-top: 1px solid var(--border);
  background: var(--surface);
  flex-shrink: 0;
  overscroll-behavior-x: contain;
}
.faq-chip {
  flex-shrink: 0;
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid var(--border-strong);
  background: var(--surface-warm);
  font-size: 12px;
  color: var(--ink-soft);
}
.faq-chip:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent-deep);
}
.faq-chip:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.composer {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}
.icon-btn {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  border: 1px solid var(--border-strong);
  color: var(--ink-soft);
  flex-shrink: 0;
}
.composer-input {
  flex: 1;
  height: 42px;
  border: 1px solid var(--border-strong);
  border-radius: 12px;
  padding: 0 14px;
  outline: none;
}
.composer-input:focus {
  border-color: var(--accent);
}
</style>
