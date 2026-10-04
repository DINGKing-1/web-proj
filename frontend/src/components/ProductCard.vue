<script setup>
import { computed } from 'vue'
import { imgOf, formatPrice, onImgError } from '@/api/client'

const props = defineProps({
  product: { type: Object, required: true }
})

const image = computed(() => imgOf(props.product))
</script>

<template>
  <router-link :to="`/product/${product.id}`" class="product-card">
    <div class="thumb">
      <img
        :src="image"
        :alt="product.name"
        loading="lazy"
        @error="onImgError"
      />
      <span
        v-if="product.status && product.status !== 'active'"
        class="status-chip"
      >{{ product.status === 'sold' ? '已售出' : product.status === 'pending' ? '审核中' : product.status === 'rejected' ? '已驳回' : '已下架' }}</span>
    </div>
    <div class="body">
      <h3 class="title">{{ product.name }}</h3>
      <div class="price-row">
        <span class="price">¥{{ formatPrice(product.secondHandPrice) }}</span>
        <span v-if="product.originalPrice" class="price-origin">¥{{ formatPrice(product.originalPrice) }}</span>
      </div>
      <div class="meta">
        <span v-if="product.itemCondition" class="cond">{{ product.itemCondition }}</span>
        <span class="cond">{{ product.category }}</span>
      </div>
      <div class="sales-line">
        <i class="fas fa-fire"></i>
        <span v-if="product.salesCount > 0">已售 {{ product.salesCount }} 件</span>
        <span v-else>暂无销量</span>
        <span class="views" v-if="product.viewCount != null">
          <i class="fas fa-eye"></i> {{ product.viewCount }}
        </span>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.product-card {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s;
}
.product-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: rgba(212, 136, 60, 0.35);
}
.thumb {
  position: relative;
  aspect-ratio: 1 / 0.92;
  background: linear-gradient(180deg, #f7eee4, #efe2d2);
  overflow: hidden;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}
.product-card:hover .thumb img {
  transform: scale(1.05);
}
.status-chip {
  position: absolute;
  top: 10px;
  left: 10px;
  background: rgba(44, 34, 27, 0.72);
  color: #fff;
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 999px;
}
.body {
  padding: 14px 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.title {
  margin: 0;
  font-size: 15.5px;
  font-weight: 700;
  line-height: 1.45;
  letter-spacing: -0.01em;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.9em;
  color: var(--ink);
}
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cond {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--muted);
}
.price-row {
  display: flex;
  align-items: baseline;
}
.price-row .price {
  font-size: 18px;
  font-weight: 800;
}
.sales-line {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 550;
  color: var(--muted);
}
.sales-line i {
  font-size: 11px;
  color: var(--accent);
  opacity: 0.85;
}
.views {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
