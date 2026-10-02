<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] }, // [{ label, value }]
  placeholder: { type: String, default: '请选择' },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const rootRef = ref(null)

const selectedLabel = computed(() => {
  const hit = props.options.find((o) => String(o.value) === String(props.modelValue))
  return hit ? hit.label : props.placeholder
})

function toggle() {
  if (props.disabled) return
  open.value = !open.value
}

function pick(opt) {
  if (opt.disabled) return
  emit('update:modelValue', opt.value)
  emit('change', opt.value)
  open.value = false
}

function onDocClick(e) {
  if (!open.value) return
  if (rootRef.value && !rootRef.value.contains(e.target)) open.value = false
}

function onKeydown(e) {
  if (e.key === 'Escape') open.value = false
}

watch(open, (v) => {
  if (!v) return
})

onMounted(() => {
  document.addEventListener('click', onDocClick, true)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick, true)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="rootRef" class="app-select" :class="{ open, disabled }">
    <button
      type="button"
      class="app-select-trigger"
      :disabled="disabled"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click="toggle"
    >
      <span class="label" :class="{ placeholder: selectedLabel === placeholder }">{{ selectedLabel }}</span>
      <i class="fas fa-chevron-down chev" :class="{ open }"></i>
    </button>
    <transition name="drop">
      <div v-if="open" class="app-select-menu" role="listbox">
        <button
          v-for="opt in options"
          :key="String(opt.value)"
          type="button"
          class="app-select-item"
          role="option"
          :class="{ active: String(opt.value) === String(modelValue), disabled: opt.disabled }"
          :aria-selected="String(opt.value) === String(modelValue)"
          @click="pick(opt)"
        >
          <span>{{ opt.label }}</span>
          <i v-if="String(opt.value) === String(modelValue)" class="fas fa-check"></i>
        </button>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.app-select {
  position: relative;
  width: 100%;
}
.app-select-trigger {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.app-select-trigger:hover:not(:disabled) {
  border-color: var(--accent);
}
.app-select.open .app-select-trigger {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(212, 136, 60, 0.15);
}
.app-select.disabled .app-select-trigger {
  opacity: 0.55;
  cursor: not-allowed;
}
.label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.label.placeholder {
  color: var(--muted);
}
.chev {
  font-size: 10px;
  color: var(--muted);
  transition: transform 0.18s ease;
  flex-shrink: 0;
}
.chev.open {
  transform: rotate(180deg);
}
.app-select-menu {
  position: absolute;
  left: 0;
  right: 0;
  top: calc(100% + 6px);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow-lg);
  padding: 6px;
  z-index: var(--z-dropdown);
  max-height: 260px;
  overflow: auto;
}
.app-select-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--ink-soft);
  text-align: left;
}
.app-select-item:hover,
.app-select-item.active {
  background: var(--accent-soft);
  color: var(--accent-deep);
}
.app-select-item.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.app-select-item i {
  font-size: 11px;
}
.drop-enter-active,
.drop-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.drop-enter-from,
.drop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
