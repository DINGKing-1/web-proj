<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { productApi, uploadApi } from '@/api'
import { CATEGORIES } from '@/api/client'
import { showToast } from '@/composables/ui'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()

const formRef = ref()
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

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  secondHandPrice: [
    {
      validator: (_r, v, cb) => {
        if (v === '' || v === null || v === undefined) cb(new Error('请输入售价'))
        else if (Number(v) <= 0) cb(new Error('售价必须大于 0'))
        else cb()
      },
      trigger: 'blur'
    }
  ],
  condition: [{ required: true, message: '请选择成色', trigger: 'change' }]
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
  const formEl = formRef.value
  if (!formEl) return
  await formEl.validate(async (valid) => {
    if (!valid) return
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
  })
}
</script>

<template>
  <main class="page">
    <div class="container narrow">
      <h1 class="h1">发布商品</h1>
      <p class="muted mb-4">把闲置好物分享给需要的人</p>

      <el-card shadow="never" class="block">
        <template #header>
          <div class="card-head">
            <span class="h3">商品图片</span>
            <el-tag size="small" type="info" effect="plain">支持多图 · 可拖拽排序 · 单张 ≤10MB</el-tag>
          </div>
        </template>
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
      </el-card>

      <el-card shadow="never" class="block">
        <template #header><span class="h3">基本信息</span></template>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="商品名称" prop="name">
            <el-input v-model="form.name" maxlength="255" show-word-limit placeholder="例如：iPhone 14 Pro 256G" />
          </el-form-item>

          <el-row :gutter="14">
            <el-col :span="12">
              <el-form-item label="分类" prop="category">
                <el-select v-model="form.category" placeholder="请选择分类" class="w-full">
                  <el-option
                    v-for="c in CATEGORIES.filter((x) => x !== '全部')"
                    :key="c"
                    :label="c"
                    :value="c"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="售价（元）" prop="secondHandPrice">
                <el-input-number
                  v-model="form.secondHandPrice"
                  :min="0"
                  :precision="2"
                  :step="1"
                  controls-position="right"
                  class="w-full"
                  placeholder="0.00"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="14">
            <el-col :span="12">
              <el-form-item label="库存">
                <el-input-number v-model="form.quantity" :min="1" :max="999" controls-position="right" class="w-full" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="成色" prop="condition">
                <el-select v-model="form.condition" placeholder="请选择成色" class="w-full">
                  <el-option v-for="c in CONDITIONS" :key="c" :label="c" :value="c" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="商品描述">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
              maxlength="2000"
              show-word-limit
              placeholder="介绍成色、使用情况、配件等…"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <div class="footer-actions">
        <el-button size="large" @click="router.back()">取消</el-button>
        <el-button type="primary" size="large" :loading="submitting" @click="submit">
          发布商品
        </el-button>
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
.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>

