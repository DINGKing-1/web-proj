<script setup>
import { formatTime } from '@/api/client'

const props = defineProps({
  mine: { type: Boolean, default: false },
  content: { type: String, default: '' },
  messageType: { type: String, default: 'text' },
  createdAt: { type: [String, Date], default: '' },
  avatar: { type: String, default: '' },
  showAvatar: { type: Boolean, default: true },
  /** 同一天仅首条显示日期分割线 */
  dateLabel: { type: String, default: '' }
})

function openMedia(url) {
  if (url) window.open(url, '_blank')
}
</script>

<template>
  <div>
    <div v-if="dateLabel" class="cm-date">{{ dateLabel }}</div>
    <div class="cm-row" :class="{ mine: props.mine }">
      <img
        v-if="showAvatar"
        class="cm-avatar"
        :src="avatar || '/user-avatar/default.jpg'"
        alt=""
        @error="$event.target.src = '/user-avatar/default.jpg'"
      />
      <div class="cm-body">
        <div class="cm-bubble" :class="{ media: messageType === 'image' || messageType === 'video' }">
          <img
            v-if="messageType === 'image'"
            class="cm-img"
            :src="content"
            alt="图片消息"
            @click="openMedia(content)"
          />
          <video
            v-else-if="messageType === 'video'"
            class="cm-video"
            :src="content"
            controls
            preload="metadata"
          ></video>
          <p v-else class="cm-text">{{ content }}</p>
        </div>
        <span class="cm-time">{{ formatTime(createdAt) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cm-date {
  text-align: center;
  margin: 14px 0 10px;
  font-size: 11px;
  color: var(--muted);
  position: relative;
}
.cm-date::before,
.cm-date::after {
  content: '';
  display: inline-block;
  width: min(80px, 18%);
  height: 1px;
  background: var(--border);
  vertical-align: middle;
  margin: 0 10px;
}
.cm-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
}
.cm-row.mine {
  flex-direction: row-reverse;
}
.cm-avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  object-fit: cover;
  flex-shrink: 0;
}
.cm-body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: min(72%, 460px);
  min-width: 0;
}
.cm-row.mine .cm-body {
  align-items: flex-end;
}
.cm-bubble {
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: 14px;
  padding: 10px 12px;
  overflow: hidden;
}
.cm-row.mine .cm-bubble {
  /* 实心底色，保证白字对比度 ≥ 4.5:1 */
  background: #a85f1f;
  border-color: transparent;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.cm-row:not(.mine) .cm-bubble {
  border-bottom-left-radius: 4px;
}
.cm-bubble.media {
  padding: 4px;
  line-height: 0;
  background: var(--surface);
}
.cm-row.mine .cm-bubble.media {
  background: #a85f1f;
}
.cm-text {
  margin: 0;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14.5px;
}
.cm-img {
  max-width: min(220px, 58vw);
  max-height: 280px;
  border-radius: 10px;
  cursor: pointer;
  object-fit: cover;
}
.cm-video {
  width: min(260px, 62vw);
  max-height: 320px;
  border-radius: 10px;
  background: #000;
}
.cm-time {
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
}
</style>
