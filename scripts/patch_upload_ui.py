from pathlib import Path

p = Path("frontend/src/views/UploadPage.vue")
t = p.read_text(encoding="utf-8")
nl = "\r\n" if "\r\n" in t else "\n"

old_form = r'''      <el-card shadow="never" class="block">
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
'''

new_form = r'''      <section class="card card-pad block">
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
'''

def to_nl(s: str) -> str:
    return s.replace("\n", nl) if nl == "\r\n" else s

old_n, new_n = to_nl(old_form), to_nl(new_form)
if old_n not in t:
    raise SystemExit("form template not found")
t = t.replace(old_n, new_n, 1)

t = t.replace(
    '<el-tag size="small" type="info" effect="plain">支持多图 · 可拖拽排序 · 单张 ≤10MB</el-tag>',
    '<span class="badge badge-muted">支持多图 · 可拖拽排序 · 单张 ≤10MB</span>'
)

# convert image card
t = t.replace('<el-card shadow="never" class="block">', '<section class="card card-pad block">')
t = t.replace('</el-card>', '</section>')
t = t.replace(
    '''        <template #header>
          <div class="card-head">
            <span class="h3">商品图片</span>''',
    '''        <div class="card-head">
            <span class="h3">商品图片</span>'''
)
t = t.replace(
    '''          </div>
        </template>''',
    '''          </div>'''
)

styles = to_nl('''
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
''')
if 'field-error' not in t.split('<style')[-1]:
    t = t.replace('.footer-actions', styles + '.footer-actions', 1)

p.write_text(t, encoding="utf-8")
print('el leftovers:', any(x in t for x in ['el-form', 'el-input', 'el-button', 'el-card', 'el-select']))
print('has field-error css:', 'field-error' in t)
