<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { supportApi, chatApi, uploadApi } from '@/api'
import { formatTime, isAuthFailure, isTransient } from '@/api/client'
import { showToast, confirmDialog } from '@/composables/ui'
import { validateChatMedia } from '@/composables/chatMedia'
import { useUserStore } from '@/stores/user'
import AdminShell from '@/components/AdminShell.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import ChatMessage from '@/components/ChatMessage.vue'

const router = useRouter()
const store = useUserStore()
const rooms = ref([])
const active = ref(null)
const messages = ref([])
const draft = ref('')
const replies = ref([])
const loading = ref(true)
const showReplyMgr = ref(false)
const editingReplyId = ref(null)
const replyForm = ref({ category: '交易', title: '', content: '' })
const listEl = ref(null)
const mediaInput = ref(null)
const sending = ref(false)
const uploadingMedia = ref(false)
/** 已收到的最后一条消息 id，用于增量拉取 */
const lastId = ref(0)
/** 竞态防护：切换会话后丢弃过期响应 */
let roomSeq = 0
let timer = 0

/** 同一天只在首条消息上方显示日期 */
function dateLabelOf(m, prev) {
  const day = String(m.createdAt || '').slice(0, 10)
  const prevDay = prev ? String(prev.createdAt || '').slice(0, 10) : ''
  return day && day !== prevDay ? day.replace(/-/g, '/') : ''
}

/** 是否贴近底部：管理员上翻历史时不强制滚动 */
function isNearBottom(threshold = 40) {
  const el = listEl.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
}

async function loadRooms() {
  const d = await supportApi.rooms()
  if (d.success) {
    rooms.value = d.rooms || []
  } else if (isAuthFailure(d)) {
    store.setUser(null)
    router.replace('/admin')
  } else if (!isTransient(d)) {
    showToast(d.message || '会话加载失败', 'error')
  }
}

async function loadReplies() {
  const d = await supportApi.replies()
  if (d.success) replies.value = d.items || []
}

async function openRoom(room) {
  roomSeq += 1
  active.value = room
  messages.value = []
  lastId.value = 0
  await loadMsgs(true)
}

async function loadMsgs(reset = false) {
  const room = active.value
  if (!room) return
  const seq = roomSeq
  const stick = reset || isNearBottom()
  // 增量拉取：非 reset 时只取 lastId 之后的消息
  const d = await supportApi.messages(room.roomId, reset ? undefined : lastId.value || undefined)
  if (seq !== roomSeq) return // 会话已切换，丢弃过期响应
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

function isMe(m) {
  return m.senderUsername === 'admin' || m.senderUsername === store.user?.username
}

function avatarOf(m) {
  if (isMe(m)) return store.user?.avatar || '/user-avatar/default.jpg'
  return active.value?.avatar || '/user-avatar/default.jpg'
}

async function send(text) {
  const content = (text != null ? text : draft.value).trim()
  if (!content || !active.value || sending.value) return
  sending.value = true
  try {
    const d = await supportApi.send({ roomId: active.value.roomId, content, type: 'text' })
    if (d.success) {
      if (text == null) draft.value = ''
      await loadMsgs()
      await nextTick()
      scrollBottom() // 自己发的回复总是滚到底
      loadRooms()
    } else {
      showToast(d.message || '发送失败', 'error')
    }
  } finally {
    sending.value = false
  }
}

/** 发送图片 / 视频 */
async function onPickMedia(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || uploadingMedia.value || !active.value) return
  const err = validateChatMedia(file)
  if (err) {
    showToast(err, 'warning')
    return
  }
  uploadingMedia.value = true
  try {
    const media = await uploadApi.chatMedia(file)
    const d = await supportApi.send({
      roomId: active.value.roomId,
      content: media.url,
      type: media.type
    })
    if (d.success) {
      await loadMsgs()
      await nextTick()
      scrollBottom()
      loadRooms()
    } else {
      showToast(d.message || '发送失败', 'error')
    }
  } catch (e2) {
    showToast(e2.message || '发送失败，请重试', 'error')
  } finally {
    uploadingMedia.value = false
  }
}

/** 话术填入输入框，便于修改后再发送（避免一键误发） */
function useReply(r) {
  draft.value = r.content
}

function startEditReply(r) {
  editingReplyId.value = r.id
  replyForm.value = { category: r.category || '交易', title: r.title || '', content: r.content || '' }
}

function resetReplyForm() {
  editingReplyId.value = null
  replyForm.value = { category: '交易', title: '', content: '' }
}

async function saveReply() {
  if (!replyForm.value.title.trim() || !replyForm.value.content.trim()) {
    showToast('请填写标题和内容', 'warning')
    return
  }
  const payload = {
    category: replyForm.value.category,
    title: replyForm.value.title.trim(),
    content: replyForm.value.content.trim()
  }
  const d = editingReplyId.value
    ? await supportApi.updateReply(editingReplyId.value, payload)
    : await supportApi.addReply(payload)
  if (d.success) {
    showToast(editingReplyId.value ? '话术已更新' : '话术已添加')
    resetReplyForm()
    loadReplies()
  } else {
    showToast(d.message || '保存失败', 'error')
  }
}

async function removeReply(r) {
  const ok = await confirmDialog({ title: '删除话术', message: `删除「${r.title}」？`, okText: '删除', type: 'warning' })
  if (!ok) return
  const d = await supportApi.deleteReply(r.id)
  if (d.success) {
    showToast('已删除')
    if (editingReplyId.value === r.id) resetReplyForm()
    loadReplies()
  }
}

onMounted(async () => {
  await Promise.all([loadRooms(), loadReplies()])
  loading.value = false
  timer = setInterval(() => {
    // 标签隐藏时暂停轮询
    if (document.visibilityState !== 'visible') return
    if (active.value) loadMsgs()
    loadRooms()
  }, 5000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <AdminShell active="support">
    <template #title>客服工作台</template>

    <LoadingBlock v-if="loading" />
    <div v-else class="support-admin">
      <aside class="room-panel card">
        <div class="panel-title">
          咨询会话
          <button type="button" class="btn btn-outline btn-sm" @click="showReplyMgr = true">话术库</button>
        </div>
        <div v-if="!rooms.length" class="muted pad">暂无咨询</div>
        <div v-else class="room-list">
          <button
            v-for="r in rooms"
            :key="r.roomId"
            type="button"
            class="room-item"
            :class="{ on: active?.roomId === r.roomId }"
            @click="openRoom(r)"
          >
            <img :src="r.avatar || '/user-avatar/default.jpg'" alt="" @error="$event.target.src = '/user-avatar/default.jpg'" />
            <div class="r-body">
              <div class="r-top">
                <strong class="truncate">{{ r.nickname || r.username }}</strong>
                <span v-if="r.unread" class="badge-dot">{{ r.unread > 9 ? '9+' : r.unread }}</span>
              </div>
              <div class="r-sub">
                <span class="muted text-xs truncate">{{ r.lastMessage || '发起咨询' }}</span>
                <span class="muted text-xs">{{ formatTime(r.lastMessageTime) }}</span>
              </div>
            </div>
          </button>
        </div>
      </aside>

      <section class="chat-panel card">
        <div v-if="!active" class="empty-chat muted">选择左侧会话开始接待</div>
        <template v-else>
          <header class="chat-head">
            <strong>{{ active.nickname || active.username }}</strong>
            <span class="muted text-xs">@{{ active.username }}</span>
          </header>
          <div ref="listEl" class="msg-list">
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
            <div v-if="!messages.length" class="muted text-sm empty-msg">暂无消息</div>
          </div>
          <div class="quick-row">
            <button
              v-for="r in replies"
              :key="r.id"
              type="button"
              class="quick-chip"
              :title="r.content"
              @click="useReply(r)"
            >
              {{ r.title }}
            </button>
          </div>
          <form class="composer" @submit.prevent="send()">
            <button
              type="button"
              class="icon-btn"
              title="发送图片或视频"
              :disabled="uploadingMedia"
              @click="mediaInput?.click()"
            >
              <i class="fas" :class="uploadingMedia ? 'fa-spinner fa-spin' : 'fa-image'"></i>
            </button>
            <input ref="mediaInput" type="file" accept="image/*,video/*" hidden @change="onPickMedia" />
            <input v-model="draft" class="composer-input" placeholder="输入回复，或点击上方话术…" maxlength="500" :disabled="sending" />
            <button type="submit" class="btn btn-primary" :disabled="!draft.trim() || sending">
              {{ sending ? '发送中…' : '发送' }}
            </button>
          </form>
        </template>
      </section>
    </div>

    <!-- 话术管理 -->
    <div v-if="showReplyMgr" class="modal-mask" @click.self="showReplyMgr = false">
      <div class="modal-box">
        <header class="modal-head">
          <h2 class="h3">快捷话术库</h2>
          <button class="icon-x" @click="showReplyMgr = false">×</button>
        </header>
        <div class="modal-body">
          <ul class="reply-list">
            <li v-for="r in replies" :key="r.id">
              <div>
                <strong>{{ r.title }}</strong>
                <span class="chip">{{ r.category }}</span>
                <div class="muted text-xs">{{ r.content }}</div>
              </div>
              <div class="reply-actions">
                <button class="btn btn-ghost btn-sm" @click="startEditReply(r)">编辑</button>
                <button class="btn btn-ghost btn-sm" @click="removeReply(r)">删除</button>
              </div>
            </li>
          </ul>
          <div class="add-box">
            <h3 class="h3">{{ editingReplyId ? '编辑话术' : '新增话术' }}</h3>
            <div class="row">
              <select v-model="replyForm.category" class="form-input">
                <option>交易</option>
                <option>售后</option>
                <option>账号</option>
                <option>其他</option>
              </select>
              <input v-model="replyForm.title" class="form-input" placeholder="标题（如：如何退款）" />
            </div>
            <textarea v-model="replyForm.content" class="form-input" rows="3" placeholder="回复内容"></textarea>
            <div class="row-actions">
              <button class="btn btn-primary" @click="saveReply">{{ editingReplyId ? '保存修改' : '保存话术' }}</button>
              <button v-if="editingReplyId" class="btn btn-outline" @click="resetReplyForm">取消编辑</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AdminShell>
</template>

<style scoped>
.support-admin {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  /* 两栏等高，内部各自滚动 */
  height: calc(100vh - 140px);
  min-height: 520px;
}
.room-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  font-weight: 600;
  flex-shrink: 0;
}
.pad {
  padding: 16px;
}
.room-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.room-item {
  width: 100%;
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  text-align: left;
  border-bottom: 1px solid var(--border);
  border-left: 3px solid transparent;
}
.room-item:hover {
  background: var(--surface-warm);
}
.room-item.on {
  background: var(--accent-soft);
  border-left-color: var(--accent);
}
.room-item img {
  width: 40px;
  height: 40px;
  border-radius: 10px;
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
}
.r-sub {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 2px;
}
.badge-dot {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--danger);
  color: #fff;
  font-size: 11px;
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
}
.chat-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.empty-chat {
  flex: 1;
  display: grid;
  place-items: center;
}
.chat-head {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.msg-list {
  flex: 1;
  /* 关键修复：缺 min-height:0 时 flex 子项不收缩，消息列表无法滚动 */
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 16px;
  background: var(--surface-warm);
}
.empty-msg {
  text-align: center;
  margin: 24px 0;
}
.quick-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--border);
  max-height: 96px;
  overflow-y: auto;
  flex-shrink: 0;
}
.quick-chip {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--border-strong);
  background: var(--surface-warm);
  font-size: 12px;
}
.quick-chip:hover {
  border-color: var(--accent);
  color: var(--accent-deep);
}
.composer {
  display: flex;
  gap: 10px;
  padding: 12px;
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}
.icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  border: 1px solid var(--border-strong);
  color: var(--ink-soft);
  flex-shrink: 0;
}
.composer-input {
  flex: 1;
  height: 40px;
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  padding: 0 12px;
  outline: none;
}
.composer-input:focus {
  border-color: var(--accent);
}
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(44, 34, 27, 0.45);
  z-index: 500;
  display: grid;
  place-items: center;
  padding: 20px;
}
.modal-box {
  width: min(100%, 640px);
  max-height: 85vh;
  overflow: auto;
  background: var(--surface);
  border-radius: 16px;
}
.modal-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px;
  border-bottom: 1px solid var(--border);
}
.icon-x {
  font-size: 24px;
  color: var(--muted);
  background: none;
  border: none;
}
.modal-body {
  padding: 16px 18px;
}
.reply-list {
  list-style: none;
  margin: 0 0 18px;
  padding: 0;
}
.reply-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.reply-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.chip {
  margin-left: 8px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent-deep);
}
.add-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.row {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 10px;
}
.row-actions {
  display: flex;
  gap: 10px;
}
.truncate {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 900px) {
  .support-admin {
    grid-template-columns: 1fr;
    height: auto;
  }
  .room-panel {
    max-height: 280px;
  }
  .chat-panel {
    min-height: 480px;
  }
}
</style>
