<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { productApi, uploadApi } from '@/api'
import { CATEGORIES } from '@/api/client'
import { showToast } from '@/composables/ui'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()

const form = reactive({
  name: '',
  category: '',
  secondHandPrice: '',
  quantity: 1,
  condition: '',
  description: ''
})
const imageUrls = ref([])
const previews = ref([])
const uploading = ref(false)
const submitting = ref(false)

const CONDITIONS = ['全新', '几乎全新', '轻微使用', '明显使用', '重度使用']
const MAX_SIZE = 10 * 1024 * 1024

const errors = reactive({ name: '', category: '', secondHandPrice: '', condition: '' })

function validate() {
  errors.name = form.name.trim() ? '' : '请输入商品名称'
  errors.category = form.category ? '' : '请选择分类'
  errors.secondHandPrice =
    form.secondHandPrice === '' || form.secondHandPrice === null || form.secondHandPrice === undefined
      ? '请输入售价'
      : Number(form.secondHandPrice) <= 0
        ? '售价必须大于 0'
        : ''
  errors.condition = form.condition ? '' : '请选择成色'
  return !Object.values(errors).some(Boolean)
}

function readFileAsDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

async function onFilesSelected(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return

  const valid = []
  for (const f of files) {
    if (!f.type.startsWith('image/')) {
      showToast(`「${f.name}」不是图片`, 'warning')
      continue
    }
    if (f.size > MAX_SIZE) {
      showToast(`「${f.name}」超过 10MB`, 'warning')
      continue
    }
    valid.push(f)
  }
  if (!valid.length) return

  uploading.value = true
  const bases = []
  const localPreviews = []
  for (const f of valid) {
    const dataUrl = await readFileAsDataURL(f)
    localPreviews.push(dataUrl)
    bases.push(dataUrl)
  }
  previews.value = [...previews.value, ...localPreviews]

  const d = await uploadApi.images(bases)
  uploading.value = false
  if (d.success && d.imageUrls?.length) {
    imageUrls.value = [...imageUrls.value, ...d.imageUrls]
    showToast(`已上传 ${d.imageUrls.length} 张图片`)
  } else {
    previews.value = previews.value.slice(0, previews.value.length - localPreviews.length)
    showToast(d.message || '图片上传失败', 'error')
  }
}

function removeImage(index) {
  imageUrls.value.splice(index, 1)
  previews.value.splice(index, 1)
}

function onDragStart(e, index) {
  e.dataTransfer.setData('text/plain', String(index))
}

function onDrop(e, index) {
  const from = Number(e.dataTransfer.getData('text/plain'))
  if (Number.isNaN(from) || from === index) return
  const imgs = [...imageUrls.value]
  const [moved] = imgs.splice(from, 1)
  imgs.splice(index, 0, moved)
  imageUrls.value = imgs
  const prev = [...previews.value]
  const [pm] = prev.splice(from, 1)
  prev.splice(index, 0, pm)
  previews.value = prev
}

async function submit() {
  if (!store.user) {
    showToast('请先登录', 'warning')
    router.push('/login')
    return
  }
  if (!validate()) {
    showToast('请完善必填信息', 'warning')
    return
  }
  if (!imageUrls.value.length) {
    showToast('请至少上传一张图片', 'warning')
    return
  }
  submitting.value = true
  const price = Number(form.secondHandPrice)
  const d = await productApi.create({
    name: form.name.trim(),
    category: form.category,
    description: form.description.trim(),
    originalPrice: price,
    secondHandPrice: price,
    condition: form.condition,
    imageUrl: imageUrls.value,
    quantity: Number(form.quantity) || 1
  })
  submitting.value = false
  if (d.success) {
    showToast('商品发布成功')
    setTimeout(() => router.push(`/product/${d.productId}`), 400)
  } else {
    showToast(d.message || '发布失败', 'error')
  }
}
</script>

<template>
  <main class="page">
    <div class="container narrow">
      <h1 class="h1">发布商品</h1>
      <p class="muted mb-4">把闲置好物分享给需要的人</p>

      <section class="card card-pad block">
        <div class="card-head">
            <span class="h3">商品图片</span>
            <span class="badge badge-muted">支持多图 · 可拖拽排序 · 单张 ≤10MB</span>
          </div>
        <div class="image-grid">
          <div
            v-for="(url, i) in previews"
            :key="i"
            class="img-cell"
            draggable="true"
            @dragstart="onDragStart($event, i)"
            @dragover.prevent
            @drop="onDrop($event, i)"
          >
            <img :src="url" alt="" />
            <button class="remove-btn" type="button" @click="removeImage(i)">
              <i class="fas fa-times"></i>
            </button>
            <span v-if="i === 0" class="cover-chip">封面</span>
          </div>
          <label class="add-cell">
            <i class="fas fa-plus"></i>
            <span>添加</span>
            <input type="file" accept="image/*" multiple hidden @change="onFilesSelected" />
          </label>
        </div>
        <div v-if="uploading" class="muted text-sm mt-2">图片上传中…</div>
      </section>

      <section class="card card-pad block">
        <h2 class="h3 mb-3">基本信息</h2>

        <div class="form-group">
          <label for="up-name">商品名称</label>
          <input
            id="up-name"
            v-model="form.name"
            class="form-input"
            :class="{ invalid: errors.name }"
            maxlength="255"
            placeholder="例如：iPhone 14 Pro 256G"
            @input="errors.name = ''"
          />
          <p v-if="errors.name" class="field-error">{{ errors.name }}</p>
        </div>

        <div class="row">
          <div class="form-group">
            <label for="up-cat">分类</label>
            <select
              id="up-cat"
              v-model="form.category"
              class="form-select"
              :class="{ invalid: errors.category }"
              @change="errors.category = ''"
            >
              <option value="" disabled>请选择分类</option>
              <option v-for="c in CATEGORIES.filter((x) => x !== '全部')" :key="c" :value="c">{{ c }}</option>
            </select>
            <p v-if="errors.category" class="field-error">{{ errors.category }}</p>
          </div>
          <div class="form-group">
            <label for="up-price">售价（元）</label>
            <input
              id="up-price"
              v-model="form.secondHandPrice"
              class="form-input"
              :class="{ invalid: errors.secondHandPrice }"
              type="number"
              min="0"
              step="0.01"
              placeholder="0.00"
              @input="errors.secondHandPrice = ''"
            />
            <p v-if="errors.secondHandPrice" class="field-error">{{ errors.secondHandPrice }}</p>
          </div>
        </div>

        <div class="row">
          <div class="form-group">
            <label for="up-qty">库存</label>
            <input
              id="up-qty"
              v-model="form.quantity"
              class="form-input"
              type="number"
              min="1"
              max="999"
            />
          </div>
          <div class="form-group">
            <label for="up-cond">成色</label>
            <select
              id="up-cond"
              v-model="form.condition"
              class="form-select"
              :class="{ invalid: errors.condition }"
              @change="errors.condition = ''"
            >
              <option value="" disabled>请选择成色</option>
              <option v-for="c in CONDITIONS" :key="c" :value="c">{{ c }}</option>
            </select>
            <p v-if="errors.condition" class="field-error">{{ errors.condition }}</p>
          </div>
        </div>

        <div class="form-group">
          <label for="up-desc">商品描述</label>
          <textarea
            id="up-desc"
            v-model="form.description"
            class="form-textarea"
            rows="4"
            maxlength="2000"
            placeholder="介绍成色、使用情况、配件等…"
          ></textarea>
        </div>
      </section>

      <div class="footer-actions">
        <button type="button" class="btn btn-outline btn-lg" @click="router.back()">取消</button>
        <button type="button" class="btn btn-primary btn-lg" :disabled="submitting" @click="submit">
          {{ submitting ? '发布中…' : '发布商品' }}
        </button>
      </div>
    </div>
  </main>
</template>

<style scoped>
.narrow {
  max-width: 820px;
}
.block {
  margin-bottom: 16px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(112px, 1fr));
  gap: 12px;
}
.img-cell {
  position: relative;
  aspect-ratio: 1;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--border);
}
.img-cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.remove-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(44, 34, 27, 0.7);
  color: #fff;
  font-size: 11px;
}
.cover-chip {
  position: absolute;
  left: 6px;
  bottom: 6px;
  background: rgba(212, 136, 60, 0.92);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
}
.add-cell {
  aspect-ratio: 1;
  border: 1.5px dashed var(--border-strong);
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--muted);
  cursor: pointer;
  background: var(--surface-warm);
}
.add-cell:hover {
  border-color: var(--accent);
  color: var(--accent-deep);
}

.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.form-input.invalid,
.form-select.invalid {
  border-color: var(--danger);
  box-shadow: 0 0 0 3px rgba(214, 69, 69, 0.12);
}
.field-error {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--danger);
}
.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>

