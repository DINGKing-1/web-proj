<script setup>
defineProps({
  icon: { type: String, default: 'fa-inbox' },
  title: { type: String, default: '暂无内容' },
  desc: { type: String, default: '' }
})
</script>

<template>
  <div class="empty">
    <i class="fas" :class="icon"></i>
    <h3 class="h3">{{ title }}</h3>
    <p v-if="desc" class="muted text-sm">{{ desc }}</p>
    <div class="actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.actions {
  margin-top: 18px;
}
</style>
