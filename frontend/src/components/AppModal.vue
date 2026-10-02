<script setup>
/** 统一弹窗：对齐设计系统 modal-overlay / modal-box */
defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: String, default: '560px' }
})
const emit = defineEmits(['close'])
</script>

<template>
  <Teleport to="body">
    <div v-if="show" class="modal-overlay" role="dialog" aria-modal="true" @click.self="emit('close')">
      <div class="modal-box app-modal" :style="{ width: `min(100%, ${width})` }">
        <header class="app-modal-head">
          <h2 class="h2">{{ title }}</h2>
          <button type="button" class="app-modal-close" aria-label="关闭" @click="emit('close')">
            <i class="fas fa-xmark"></i>
          </button>
        </header>
        <div class="app-modal-body">
          <slot />
        </div>
        <footer v-if="$slots.actions" class="modal-actions">
          <slot name="actions" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.app-modal {
  max-height: min(88vh, 860px);
  overflow: auto;
  padding: 24px 26px 22px;
}
.app-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}
.app-modal-head .h2 {
  margin: 0;
}
.app-modal-close {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--muted);
  display: grid;
  place-items: center;
}
.app-modal-close:hover {
  background: var(--accent-soft);
  color: var(--accent-deep);
}
.app-modal-body {
  color: var(--ink);
}
</style>
