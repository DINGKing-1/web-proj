<script setup>
import { computed } from 'vue'
import { ORDER_FLOW, ORDER_STATUS } from '@/api/client'

const props = defineProps({
  status: { type: String, required: true }
})

const currentIndex = computed(() => {
  const i = ORDER_FLOW.indexOf(props.status)
  return i >= 0 ? i : 0
})
const cancelled = computed(() => props.status === 'cancelled')
</script>

<template>
  <div class="timeline">
    <div v-if="cancelled" class="cancelled-line">
      <i class="fas fa-ban"></i>
      订单已取消
    </div>
    <template v-else>
      <div
        v-for="(step, i) in ORDER_FLOW"
        :key="step"
        class="step"
        :class="{ done: i <= currentIndex, current: i === currentIndex }"
      >
        <div class="dot">
          <i v-if="i <= currentIndex" class="fas fa-check"></i>
          <span v-else>{{ i + 1 }}</span>
        </div>
        <div class="label">{{ ORDER_STATUS[step].label }}</div>
        <div v-if="i < ORDER_FLOW.length - 1" class="line" :class="{ on: i < currentIndex }" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.timeline {
  display: flex;
  align-items: flex-start;
  gap: 0;
  padding: 18px 8px 8px;
}
.step {
  position: relative;
  flex: 1;
  text-align: center;
}
.dot {
  width: 32px;
  height: 32px;
  margin: 0 auto 8px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #f2ebe3;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
  border: 2px solid transparent;
  position: relative;
  z-index: 1;
}
.step.done .dot {
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  color: #fff;
}
.step.current .dot {
  box-shadow: 0 0 0 4px rgba(212, 136, 60, 0.22);
}
.label {
  font-size: 12.5px;
  color: var(--muted);
  font-weight: 500;
}
.step.done .label {
  color: var(--accent-deep);
  font-weight: 600;
}
.line {
  position: absolute;
  top: 15px;
  left: calc(50% + 22px);
  width: calc(100% - 44px);
  height: 2px;
  background: var(--border-strong);
}
.line.on {
  background: linear-gradient(90deg, var(--accent), var(--accent-deep));
}
.cancelled-line {
  width: 100%;
  text-align: center;
  color: var(--muted);
  font-weight: 600;
  padding: 12px;
}
</style>
