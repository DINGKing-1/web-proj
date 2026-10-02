<script setup>
defineProps({
  text: { type: String, default: '加载中…' }
})
</script>

<template>
  <div class="loading" role="status" aria-live="polite">
    <div class="spinner"></div>
    {{ text }}
  </div>
</template>
