<script setup>
import { watch, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useWindowSize, useEventListener, useIntervalFn } from '@vueuse/core'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import AppHeader from '@/components/AppHeader.vue'
import CategoryBar from '@/components/CategoryBar.vue'
import AppFooter from '@/components/AppFooter.vue'
import SideDock from '@/components/SideDock.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const store = useUserStore()
const isBare = () => route.meta.bare === true

const { width } = useWindowSize()
const isMobile = computed(() => width.value < 768)

function refreshAll() {
  store.refreshSession()
  store.refreshCartCount()
  store.refreshUnread()
}

onMounted(() => refreshAll())

const { pause, resume } = useIntervalFn(() => {
  if (document.visibilityState === 'visible') refreshAll()
}, 5000)

useEventListener(document, 'visibilitychange', () => {
  if (document.visibilityState === 'visible') {
    refreshAll()
    resume()
  } else {
    pause()
  }
})

watch(
  () => route.fullPath,
  () => {
    store.refreshCartCount()
    store.refreshUnread()
  }
)

onBeforeUnmount(() => pause())
</script>

<template>
  <el-config-provider :locale="zhCn">
    <div class="app-shell" :class="{ 'is-mobile': isMobile }">
      <template v-if="!isBare()">
        <AppHeader />
        <CategoryBar />
      </template>
      <router-view />
      <template v-if="!isBare()">
        <AppFooter />
        <SideDock />
      </template>
    </div>
  </el-config-provider>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.app-shell > :deep(.page) {
  flex: 1;
}
</style>
