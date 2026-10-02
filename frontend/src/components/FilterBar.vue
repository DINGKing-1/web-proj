<script setup>
/** 商品筛选栏：排序 / 价格 / 成色，样式对齐设计系统 form-* + btn */
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
  { label: '价格从低到高', value: 'price-asc' },
  { label: '价格从高到低', value: 'price-desc' }
]
const CONDITIONS = ['全新', '几乎全新', '轻微使用', '明显使用', '重度使用']
const CATEGORIES = ['手机', '笔记本电脑', '耳机', '相机', '手机壳', '空调', '冰箱', '半身裙', '其他']

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
        <label for="f-sort">排序</label>
        <select
          id="f-sort"
          class="form-select"
          :value="sortBy"
          @change="emit('update:sortBy', $event.target.value)"
        >
          <option v-for="s in SORTS" :key="s.value" :value="s.value">{{ s.label }}</option>
        </select>
      </div>

      <div v-if="showCategory" class="field">
        <label for="f-cat">分类</label>
        <select
          id="f-cat"
          class="form-select"
          :value="category"
          @change="emit('update:category', $event.target.value)"
        >
          <option value="">全部分类</option>
          <option v-for="c in CATEGORIES" :key="c" :value="c">{{ c }}</option>
        </select>
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
        <label for="f-cond">成色</label>
        <select
          id="f-cond"
          class="form-select"
          :value="condition"
          @change="emit('update:condition', $event.target.value)"
        >
          <option value="">不限</option>
          <option v-for="c in CONDITIONS" :key="c" :value="c">{{ c }}</option>
        </select>
      </div>

      <div class="field actions">
        <label class="ghost-label">&nbsp;</label>
        <div class="btn-row">
          <button type="button" class="btn btn-primary" @click="onApply">筛选</button>
          <button type="button" class="btn btn-outline" @click="onReset">重置</button>
        </div>
      </div>
    </div>
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
