<script setup>
import EmptyState from '@/components/EmptyState.vue'
</script>

<template>
  <main class="page">
    <div class="container">
      <EmptyState icon="fa-compass" title="页面不存在" desc="你访问的页面可能已失效或被移除">
        <router-link to="/" class="btn btn-primary">返回首页</router-link>
      </EmptyState>
    </div>
  </main>
</template>
