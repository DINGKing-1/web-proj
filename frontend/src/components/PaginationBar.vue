<script setup>
const props = defineProps({
  page: { type: Number, default: 0 },
  totalPages: { type: Number, default: 0 }
})
const emit = defineEmits(['change'])

function go(p) {
  if (p < 0 || p >= props.totalPages || p === props.page) return
  emit('change', p)
}

function pageList() {
  const total = props.totalPages
  const current = props.page
  const items = []
  if (total <= 7) {
    for (let i = 0; i < total; i++) items.push(i)
    return items
  }
  items.push(0)
  const start = Math.max(1, current - 1)
  const end = Math.min(total - 2, current + 1)
  if (start > 1) items.push(-1)
  for (let i = start; i <= end; i++) items.push(i)
  if (end < total - 2) items.push(-2)
  items.push(total - 1)
  return items
}
</script>

<template>
  <div v-if="totalPages > 1" class="pager">
    <button class="pg-btn" :disabled="page <= 0" @click="go(page - 1)">
      <i class="fas fa-chevron-left"></i>
    </button>
    <template v-for="(p, i) in pageList()" :key="i">
      <span v-if="p < 0" class="pg-ellipsis">…</span>
      <button v-else class="pg-btn" :class="{ active: p === page }" @click="go(p)">
        {{ p + 1 }}
      </button>
    </template>
    <button class="pg-btn" :disabled="page >= totalPages - 1" @click="go(page + 1)">
      <i class="fas fa-chevron-right"></i>
    </button>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin-top: 28px;
  flex-wrap: wrap;
}
.pg-btn {
  min-width: 38px;
  height: 38px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid var(--border-strong);
  background: var(--surface);
  color: var(--ink-soft);
  font-weight: 600;
  font-size: 13px;
}
.pg-btn:hover:not(:disabled):not(.active) {
  border-color: var(--accent);
  color: var(--accent-deep);
}
.pg-btn.active {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  border-color: transparent;
  color: #fff;
  box-shadow: 0 8px 18px rgba(212, 136, 60, 0.28);
}
.pg-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.pg-ellipsis {
  color: var(--muted);
  padding: 0 2px;
}
</style>
