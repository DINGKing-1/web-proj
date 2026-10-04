<script setup>
import { ref, watch, computed } from 'vue'
import { couponApi } from '@/api'
import { formatPrice } from '@/api/client'
import AppModal from '@/components/AppModal.vue'

/**
 * 支付时选优惠券。
 * 只列出「未使用 + 在有效期 + 满足门槛 + 抵扣不超过本单金额」的券。
 */
const props = defineProps({
  show: { type: Boolean, default: false },
  /** 本单原价总额（元） */
  gross: { type: Number, default: 0 },
  orderId: { type: [Number, String], default: null }
})
const emit = defineEmits(['close', 'confirm'])

const options = ref([])
const selected = ref(0) // 0 = 不使用
const loading = ref(false)

watch(
  () => props.show,
  async (v) => {
    if (!v) return
    selected.value = 0
    loading.value = true
    try {
      const d = await couponApi.mine()
      options.value = d.success ? filterUsable(d.coupons || []) : []
    } catch {
      options.value = []
    }
    loading.value = false
  }
)

function filterUsable(rows) {
  const now = Date.now()
  const gross = Number(props.gross) || 0
  return rows
    .filter((c) => c.status === 'unused')
    .filter((c) => {
      const start = c.startAt ? new Date(c.startAt).getTime() : 0
      const end = c.endAt ? new Date(c.endAt).getTime() : Number.MAX_SAFE_INTEGER
      return now >= start && now <= end
    })
    .filter((c) => gross + 1e-9 >= Number(c.minAmount || 0))
    .filter((c) => Number(c.discountAmount || 0) > 0 && Number(c.discountAmount || 0) <= gross + 1e-9)
}

const chosen = computed(() => options.value.find((c) => c.userCouponId === selected.value) || null)
const discount = computed(() => (chosen.value ? Number(chosen.value.discountAmount) : 0))
const payable = computed(() => Math.max(0, Number(props.gross || 0) - discount.value))

function confirmPay() {
  emit('confirm', selected.value || null, discount.value || 0)
}
</script>

<template>
  <AppModal :show="show" title="选择优惠券" width="460px" @close="emit('close')">
    <p class="muted text-sm mb-3">本单原价 ¥{{ formatPrice(gross) }}</p>

    <p v-if="loading" class="muted text-sm">加载中…</p>

    <div v-else class="coupon-list">
      <label class="coupon-none">
        <input v-model="selected" type="radio" :value="0" />
        不使用优惠券
      </label>

      <label
        v-for="c in options"
        :key="c.userCouponId"
        class="coupon-item"
        :class="{ on: selected === c.userCouponId }"
      >
        <input v-model="selected" type="radio" :value="c.userCouponId" />
        <div class="coupon-body">
          <div class="coupon-name">{{ c.name }}</div>
          <div class="coupon-meta">
            满 {{ formatPrice(c.minAmount) }} 减 {{ formatPrice(c.discountAmount) }}
            · {{ (c.endAt || '').slice(0, 10) }} 到期
          </div>
        </div>
        <div class="coupon-value">-¥{{ formatPrice(c.discountAmount) }}</div>
      </label>

      <p v-if="!options.length" class="muted text-sm">暂无可用优惠券</p>
    </div>

    <div class="summary" v-if="options.length">
      <span>优惠 <strong class="off">-¥{{ formatPrice(discount) }}</strong></span>
      <span>应付 <strong class="pay">¥{{ formatPrice(payable) }}</strong></span>
    </div>

    <div class="actions">
      <button type="button" class="btn btn-outline" @click="emit('close')">取消</button>
      <button type="button" class="btn btn-primary" @click="confirmPay">确认支付</button>
    </div>
  </AppModal>
</template>

<style scoped>
.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 320px;
  overflow-y: auto;
}
.coupon-none {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  padding: 8px 2px;
}
.coupon-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.coupon-item:hover {
  border-color: var(--accent);
}
.coupon-item.on {
  border-color: var(--accent);
  background: var(--accent-soft, #faf3ea);
}
.coupon-body {
  flex: 1;
  min-width: 0;
}
.coupon-name {
  font-size: 14px;
  font-weight: 600;
}
.coupon-meta {
  font-size: 12px;
  color: var(--muted);
  margin-top: 2px;
}
.coupon-value {
  color: var(--danger);
  font-weight: 700;
  font-size: 15px;
}
.summary {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--border);
  font-size: 13.5px;
}
.off {
  color: var(--danger);
}
.pay {
  color: var(--accent-deep);
  font-size: 17px;
}
.actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
