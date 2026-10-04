<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi, uploadApi } from '@/api'
import { useCategories } from '@/composables/categories'
import AppSelect from '@/components/AppSelect.vue'
import LoadingBlock from '@/components/LoadingBlock.vue'
import EmptyState from '@/components/EmptyState.vue'
import { showToast } from '@/composables/ui'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

/** 编辑模式：/upload?edit=<productId> */
const editId = computed(() => {
  const raw = route.query.edit
  const n = Number(raw)
  return Number.isFinite(n) && n > 0 ? n : null
})
const isEdit = computed(() => !!editId.value)
const pageLoading = ref(false)
const loadError = ref('')

const form = reactive({
  name: '',
  category: '',
  secondHandPrice: '',
  originalPrice: '',
  quantity: 1,
  condition: '',
  description: ''
})
const imageUrls = ref([])
const previews = ref([])
const uploading = ref(false)
const submitting = ref(false)

const CONDITIONS = ['全新', '几乎全新', '轻微使用', '明显使用', '重度使用']
const { categories } = useCategories()
const categoryOptions = computed(() =>
  categories.value.filter((x) => x !== '全部').map((c) => ({ label: c, value: c }))
)
const conditionOptions = CONDITIONS.map((c) => ({ label: c, value: c }))
const MAX_SIZE = 10 * 1024 * 1024

const errors = reactive({ name: '', category: '', secondHandPrice: '', originalPrice: '', condition: '' })

function validate() {
  errors.name = form.name.trim() ? '' : '请输入商品名称'
  errors.category = form.category ? '' : '请选择分类'
  errors.secondHandPrice =
    form.secondHandPrice === '' || form.secondHandPrice === null || form.secondHandPrice === undefined
      ? '请输入售价'
      : Number(form.secondHandPrice) <= 0
        ? '售价必须大于 0'
        : ''
  // 原价（划线价）选填：不填默认为售价；填写则必须 ≥ 售价
  errors.originalPrice =
    form.originalPrice === '' || form.originalPrice === null || form.originalPrice === undefined
      ? ''
      : !Number.isFinite(Number(form.originalPrice))
        ? '请输入有效金额'
        : Number(form.originalPrice) < Number(form.secondHandPrice)
          ? '原价不能低于售价'
          : ''
  errors.condition = form.condition ? '' : '请选择成色'
  return !Object.values(errors).some(Boolean)
}

function readFileAsDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('read-failed'))
    reader.readAsDataURL(file)
  })
}

async function onFilesSelected(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length || uploading.value) return

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
  try {
    const bases = []
    const localPreviews = []
    try {
      for (const f of valid) {
        const dataUrl = await readFileAsDataURL(f)
        localPreviews.push(dataUrl)
        bases.push(dataUrl)
      }
    } catch {
      showToast('图片读取失败，请重试', 'error')
      return
    }
    previews.value = [...previews.value, ...localPreviews]

    const d = await uploadApi.images(bases)
    if (d.success && d.imageUrls?.length) {
      imageUrls.value = [...imageUrls.value, ...d.imageUrls]
      showToast(`已上传 ${d.imageUrls.length} 张图片`)
    } else {
      previews.value = previews.value.slice(0, previews.value.length - localPreviews.length)
      showToast(d.message || '图片上传失败', 'error')
    }
  } finally {
    uploading.value = false // 无论成败都复位，避免卡死
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

async function loadForEdit() {
  if (!editId.value) return
  pageLoading.value = true
  loadError.value = ''
  const d = await productApi.detail(editId.value)
  pageLoading.value = false
  if (!d.success || !d.product) {
    loadError.value = d.message || '商品不存在或已被删除'
    return
  }
  const p = d.product
  if (store.user && p.sellerUsername && p.sellerUsername !== store.user.username) {
    loadError.value = '只能编辑自己的商品'
    return
  }
  form.name = p.name || ''
  form.category = p.category || ''
  form.secondHandPrice = p.secondHandPrice ?? ''
  form.originalPrice = p.originalPrice ?? ''
  form.quantity = p.quantity ?? 1
  form.condition = p.itemCondition || p.condition || ''
  form.description = p.description || ''
  const imgs = p.imageUrls?.length
    ? p.imageUrls
    : String(p.imageUrl || '').split(',').filter(Boolean)
  imageUrls.value = [...imgs]
  previews.value = [...imgs]
}

onMounted(loadForEdit)

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
  // 原价（划线价）选填：不填默认为售价
  const original =
    form.originalPrice === '' || form.originalPrice === null || form.originalPrice === undefined
      ? price
      : Number(form.originalPrice)
  const payload = {
    name: form.name.trim(),
    category: form.category,
    description: form.description.trim(),
    originalPrice: original,
    secondHandPrice: price,
    condition: form.condition,
    imageUrl: imageUrls.value,
    quantity: Number(form.quantity) || 1
  }
  const d = isEdit.value
    ? await productApi.update(editId.value, payload)
    : await productApi.create(payload)
  submitting.value = false
  if (d.success) {
    // 开启发布审核时，新商品先进待审队列，前台暂不可见
    if (!isEdit.value && d.status === 'pending') {
      showToast('已提交，等待管理员审核通过后上架')
      setTimeout(() => router.push('/user'), 600)
      return
    }
    if (isEdit.value && d.status === 'pending') {
      showToast('修改已提交，重新排队审核')
      setTimeout(() => router.push('/user'), 600)
      return
    }
    showToast(isEdit.value ? '商品已更新' : '商品发布成功')
    const targetId = isEdit.value ? editId.value : d.productId
    setTimeout(() => router.push(`/product/${targetId}`), 400)
  } else {
    showToast(d.message || (isEdit.value ? '保存失败' : '发布失败'), 'error')
  }
}
</script>

<template>
  <main class="page">
    <div class="container narrow">
      <template v-if="pageLoading">
        <LoadingBlock text="加载商品信息…" />
      </template>
      <EmptyState
        v-else-if="loadError"
        icon="fa-box-open"
        :title="loadError"
        desc="返回我的商品重新选择"
      >
        <button type="button" class="btn btn-primary" @click="router.push('/user')">我的商品</button>
      </EmptyState>
      <template v-else>
      <h1 class="h1">{{ isEdit ? '编辑商品' : '发布商品' }}</h1>
      <p class="muted mb-4">{{ isEdit ? '修改后立即对买家生效' : '把闲置好物分享给需要的人' }}</p>

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
            <AppSelect
              id="up-cat"
              v-model="form.category"
              :options="categoryOptions"
              placeholder="请选择分类"
              @change="errors.category = ''"
            />
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
          <div class="form-group">
            <label for="up-origin">原价（划线价，选填）</label>
            <input
              id="up-origin"
              v-model="form.originalPrice"
              class="form-input"
              :class="{ invalid: errors.originalPrice }"
              type="number"
              min="0"
              step="0.01"
              placeholder="不填默认与售价相同"
              @input="errors.originalPrice = ''"
            />
            <p v-if="errors.originalPrice" class="field-error">{{ errors.originalPrice }}</p>
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
            <AppSelect
              id="up-cond"
              v-model="form.condition"
              :options="conditionOptions"
              placeholder="请选择成色"
              @change="errors.condition = ''"
            />
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
          {{ submitting ? (isEdit ? '保存中…' : '发布中…') : isEdit ? '保存修改' : '发布商品' }}
        </button>
      </div>
      </template>
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

