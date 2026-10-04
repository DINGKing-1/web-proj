<script setup>
import { computed, nextTick } from 'vue'
import AppSelect from '@/components/AppSelect.vue'
import { useCategories } from '@/composables/categories'

/** 商品筛选栏：排序 / 价格 / 成色，样式对齐设计系统 */
const props = defineProps({
  sortBy: { type: String, default: 'newest' },
  minPrice: { type: String, default: '' },
  maxPrice: { type: String, default: '' },
  condition: { type: String, default: '' },
  showCategory: { type: Boolean, default: false },
  category: { type: String, default: '' }
})
const emit = defineEmits(['update:sortBy', 'update:minPrice', 'update:maxPrice', 'update:condition', 'update:category', 'apply', 'reset'])

const SORTS = [
  { label: '最新发布', value: 'newest' },
  { label: '销量优先', value: 'sales' },
  { label: '价格从低到高', value: 'price-asc' },
  { label: '价格从高到低', value: 'price-desc' }
]
const CONDITIONS = [
  { label: '不限', value: '' },
  { label: '全新', value: '全新' },
  { label: '几乎全新', value: '几乎全新' },
  { label: '轻微使用', value: '轻微使用' },
  { label: '明显使用', value: '明显使用' },
  { label: '重度使用', value: '重度使用' }
]
// 与导航共用同一份分类（后台可维护），避免两处硬编码失同步
const { categories } = useCategories()
const CATEGORY_OPTIONS = computed(() => [
  { label: '全部分类', value: '' },
  ...categories.value.filter((c) => c !== '全部').map((c) => ({ label: c, value: c }))
])

async function onSortChange(v) {
  emit('update:sortBy', v)
  await nextTick()
  emit('apply')
}
async function onConditionChange(v) {
  emit('update:condition', v)
  await nextTick()
  emit('apply')
}
async function onCategoryChange(v) {
  emit('update:category', v)
  await nextTick()
  emit('apply')
}
function onApply() {
  emit('apply')
}
function onReset() {
  emit('update:sortBy', 'newest')
  emit('update:minPrice', '')
  emit('update:maxPrice', '')
  emit('update:condition', '')
  if (props.showCategory) emit('update:category', '')
  emit('reset')
}
</script>

<template>
  <section class="filter-bar card card-pad" aria-label="商品筛选">
    <div class="filter-grid">
      <div class="field">
        <label id="f-sort-label">排序</label>
        <AppSelect
          :model-value="sortBy"
          :options="SORTS"
          placeholder="排序方式"
          aria-labelledby="f-sort-label"
          @update:model-value="onSortChange"
        />
      </div>

      <div v-if="showCategory" class="field">
        <label id="f-cat-label">分类</label>
        <AppSelect
          :model-value="category"
          :options="CATEGORY_OPTIONS"
          placeholder="全部分类"
          aria-labelledby="f-cat-label"
          @update:model-value="onCategoryChange"
        />
      </div>

      <div class="field">
        <label for="f-min">最低价</label>
        <input
          id="f-min"
          class="form-input"
          type="number"
          min="0"
          step="0.01"
          placeholder="不限"
          :value="minPrice"
          @input="emit('update:minPrice', $event.target.value)"
          @keyup.enter="onApply"
        />
      </div>

      <div class="field">
        <label for="f-max">最高价</label>
        <input
          id="f-max"
          class="form-input"
          type="number"
          min="0"
          step="0.01"
          placeholder="不限"
          :value="maxPrice"
          @input="emit('update:maxPrice', $event.target.value)"
          @keyup.enter="onApply"
        />
      </div>

      <div class="field">
        <label id="f-cond-label">成色</label>
        <AppSelect
          :model-value="condition"
          :options="CONDITIONS"
          placeholder="不限"
          aria-labelledby="f-cond-label"
          @update:model-value="onConditionChange"
        />
      </div>

      <div class="field actions">
        <label class="ghost-label">&nbsp;</label>
        <div class="btn-row">
          <button type="button" class="btn btn-primary" @click="onApply">筛选</button>
          <button type="button" class="btn btn-outline" @click="onReset">重置</button>
        </div>
      </div>
    </div>
    <p class="hint">改排序 / 成色会立即生效，价格区间可点「筛选」或按回车</p>
  </section>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 22px;
}
.filter-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px 12px;
  align-items: end;
}
.field label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 6px;
}
.ghost-label {
  visibility: hidden;
}
.btn-row {
  display: flex;
  gap: 8px;
}
.btn-row .btn {
  flex: 1;
  white-space: nowrap;
}
.hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--muted);
}
@media (max-width: 1024px) {
  .filter-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .actions {
    grid-column: 1 / -1;
  }
}
</style>
