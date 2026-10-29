<script setup lang="ts">
import '@/views/seller/seller-shared.css'

/**
 * 卖家 · 商品管理：发布、编辑、上下架与库存维护。
 * 与订单、数据分析拆成独立路由，各自只加载自己的数据。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { productApi, sellerApi, uploadApi } from '@/api'
import type { Category, Product, SellerOverview } from '@/types'
import { money } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import ProductImage from '@/components/ProductImage.vue'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const overview = ref<SellerOverview | null>(null)
const products = ref<Product[]>([])
const categories = ref<Category[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')

const dialog = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const uploading = ref(false)

const form = reactive({
  name: '',
  subtitle: '',
  categoryId: '',
  brand: '',
  price: 0,
  originalPrice: undefined as number | undefined,
  stock: 0,
  description: '',
  image: '',
  attributesText: '',
  tags: ''
})

const flatCategories = ref<{ id: string; name: string }[]>([])

onMounted(async () => {
  if (!auth.isLoggedIn) {
    router.replace({ name: 'login', query: { redirect: '/seller' } })
    return
  }
  if (!auth.isSeller && !auth.isAdmin) {
    ElMessage.warning('当前账号不是卖家，无法进入卖家中心')
    router.replace('/')
    return
  }
  await loadAll()
})

async function loadAll() {
  loading.value = true
  try {
    const [ov, list, cats] = await Promise.all([
      sellerApi.overview(),
      sellerApi.products({ page: page.value, size: 10, keyword: keyword.value || undefined }),
      productApi.list({ page: 1, size: 1 }).then(() => productApi.categories())
    ])
    overview.value = ov
    products.value = list.records
    total.value = list.total
    categories.value = cats
    flatCategories.value = cats.flatMap((root) =>
      (root.children || []).length
        ? (root.children || []).map((child) => ({ id: child.id, name: `${root.name} / ${child.name}` }))
        : [{ id: root.id, name: root.name }]
    )
  } finally {
    loading.value = false
  }
}

async function loadProducts() {
  const list = await sellerApi.products({ page: page.value, size: 10, keyword: keyword.value || undefined })
  products.value = list.records
  total.value = list.total
}

async function loadOverview() {
  overview.value = await sellerApi.overview()
}

function openPublish() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    subtitle: '',
    categoryId: flatCategories.value[0]?.id || '',
    brand: '',
    price: 0,
    originalPrice: undefined,
    stock: 0,
    description: '',
    image: '',
    attributesText: '',
    tags: ''
  })
  dialog.value = true
}

function openEdit(product: Product) {
  editingId.value = product.id
  Object.assign(form, {
    name: product.name,
    subtitle: product.subtitle || '',
    categoryId: product.categoryId,
    brand: product.brand || '',
    price: product.price,
    originalPrice: product.originalPrice ?? undefined,
    stock: product.stock,
    description: product.description || '',
    image: product.image || '',
    attributesText: Object.entries(product.attributes || {})
      .map(([k, v]) => `${k}=${v}`)
      .join('\n'),
    tags: (product.tags || []).join(',')
  })
  dialog.value = true
}

function onImagePicked(options: UploadRequestOptions) {
  const file = options.file as File
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }
  if (!/^image\/(jpeg|png|webp|gif)$/.test(file.type)) {
    ElMessage.warning('只支持 jpg / png / webp / gif 格式')
    return
  }
  uploading.value = true
  uploadApi
    .image(file)
    .then((result) => {
      form.image = result.url
      ElMessage.success('图片已上传')
    })
    .catch(() => {
      ElMessage.error('图片上传失败，请重试')
    })
    .finally(() => {
      uploading.value = false
    })
}

function clearImage() {
  form.image = ''
}

function parseAttributes(): Record<string, string> {
  const result: Record<string, string> = {}
  for (const line of form.attributesText.split('\n')) {
    const text = line.trim()
    if (!text) continue
    const index = text.indexOf('=')
    if (index <= 0) continue
    const key = text.slice(0, index).trim()
    const value = text.slice(index + 1).trim()
    if (key) result[key] = value
  }
  return result
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写商品名称')
    return
  }
  if (!form.categoryId) {
    ElMessage.warning('请选择商品品类')
    return
  }
  if (!form.price || form.price <= 0) {
    ElMessage.warning('请填写正确的价格')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      subtitle: form.subtitle.trim() || undefined,
      categoryId: form.categoryId,
      brand: form.brand.trim() || undefined,
      price: Number(form.price),
      originalPrice: form.originalPrice ? Number(form.originalPrice) : undefined,
      stock: Number(form.stock) || 0,
      description: form.description.trim() || undefined,
      image: form.image.trim() || undefined,
      attributes: parseAttributes(),
      tags: form.tags.trim() || undefined,
      status: 1
    }
    if (editingId.value) {
      await sellerApi.update(editingId.value, payload)
      ElMessage.success('商品已更新')
    } else {
      await sellerApi.publish(payload)
      ElMessage.success('商品已上架')
    }
    dialog.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(product: Product) {
  const next = product.status === 1 ? 0 : 1
  await sellerApi.toggleStatus(product.id, next)
  ElMessage.success(next === 1 ? '已上架' : '已下架')
  await loadProducts()
}

async function removeProduct(product: Product) {
  await ElMessageBox.confirm(`确定删除「${product.name}」吗？`, '删除商品', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await sellerApi.remove(product.id)
  ElMessage.success('已删除')
  await loadAll()
}
</script>

<template>
  <div v-loading="loading" class="seller-page">
    <div class="container">
      <header class="seller-head">
        <p class="page-desc">维护你的商品信息、库存与价格，点击右上角可发布新商品。</p>
        <el-button type="primary" round size="large" @click="openPublish">
          <el-icon><Plus /></el-icon> 发布商品
        </el-button>
      </header>

      <!-- 商品相关统计 -->
      <section class="stat-grid">
        <article class="stat-card">
          <span>我的商品</span>
          <strong>{{ overview?.productCount ?? 0 }}</strong>
        </article>
        <article class="stat-card">
          <span>在售中</span>
          <strong>{{ overview?.onSaleCount ?? 0 }}</strong>
        </article>
        <article class="stat-card">
          <span>累计售出</span>
          <strong>{{ overview?.soldCount ?? 0 }}</strong>
        </article>
        <article class="stat-card dark">
          <span>库存总量</span>
          <strong>{{ overview?.stockTotal ?? 0 }}</strong>
        </article>
      </section>

      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索我的商品"
          clearable
          style="max-width: 260px"
          @keyup.enter="page = 1; loadProducts()"
          @clear="page = 1; loadProducts()"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button round @click="page = 1; loadProducts()">搜索</el-button>
      </div>

      <div v-if="!loading && !products.length" class="empty-state">
        <el-icon><Goods /></el-icon>
        <strong>还没有发布商品</strong>
        <p>点击右上角「发布商品」，上架你的第一件商品。</p>
      </div>

      <div v-else class="product-list">
        <article v-for="item in products" :key="item.id" class="product-row">
          <div class="thumb">
            <ProductImage :src="item.image" :fallback-src="item.imageFallback" :alt="item.name" ratio="1 / 1" />
          </div>
          <div class="info">
            <strong>{{ item.name }}</strong>
            <span class="sub">{{ item.subtitle || item.brand || '—' }}</span>
            <div class="tags">
              <span class="tag">{{ item.categoryName }}</span>
              <span class="tag" :class="item.status === 1 ? 'ok' : 'off'">
                {{ item.status === 1 ? '在售' : '已下架' }}
              </span>
            </div>
          </div>
          <div class="metric">
            <span>价格</span>
            <strong>{{ money(item.price) }}</strong>
          </div>
          <div class="metric">
            <span>库存</span>
            <strong>{{ item.stock }}</strong>
          </div>
          <div class="metric">
            <span>已售</span>
            <strong>{{ item.sales }}</strong>
          </div>
          <div class="row-actions">
            <el-button size="small" round @click="openEdit(item)">编辑</el-button>
            <el-button size="small" round @click="toggleStatus(item)">
              {{ item.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button size="small" round type="danger" plain @click="removeProduct(item)">删除</el-button>
          </div>
        </article>
      </div>

      <el-pagination
        v-if="total > 10"
        v-model:current-page="page"
        :page-size="10"
        :total="total"
        layout="prev, pager, next"
        class="pager"
        @current-change="loadProducts"
      />
    </div>

    <el-dialog v-model="dialog" :title="editingId ? '编辑商品' : '发布商品'" width="620px" :append-to-body="true">
      <el-form label-position="top" class="publish-form">
        <el-form-item label="商品名称" required>
          <el-input v-model="form.name" placeholder="例如：星野 降噪蓝牙耳机 Pro" maxlength="60" />
        </el-form-item>
        <el-form-item label="一句话卖点">
          <el-input v-model="form.subtitle" placeholder="例如：主动降噪 · 36h 续航 · IPX5 防水" maxlength="80" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="品类" required>
            <el-select v-model="form.categoryId" placeholder="选择品类" style="width: 100%">
              <el-option v-for="c in flatCategories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="品牌">
            <el-input v-model="form.brand" placeholder="例如：星野" maxlength="30" />
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="售价" required>
            <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" controls-position="right" />
          </el-form-item>
          <el-form-item label="原价">
            <el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width: 100%" controls-position="right" />
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="库存" required>
            <el-input-number v-model="form.stock" :min="0" style="width: 100%" controls-position="right" />
          </el-form-item>
          <el-form-item label="标签（逗号分隔）">
            <el-input v-model="form.tags" placeholder="降噪,长续航" />
          </el-form-item>
        </div>
        <el-form-item label="商品描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="介绍商品的主要特点" />
        </el-form-item>
        <el-form-item label="商品主图">
          <div class="upload-area">
            <div v-if="form.image" class="upload-preview">
              <img :src="form.image" alt="主图预览" />
              <div class="upload-ops">
                <span class="uploaded">已上传</span>
                <el-button size="small" round @click="clearImage">移除</el-button>
              </div>
            </div>
            <el-upload
              v-else
              class="upload-box"
              :show-file-list="false"
              :http-request="onImagePicked"
              accept="image/jpeg,image/png,image/webp,image/gif"
            >
              <div v-loading="uploading" class="upload-placeholder">
                <el-icon><Plus /></el-icon>
                <span>点击上传图片</span>
                <small>支持 jpg / png / webp，不超过 5MB</small>
              </div>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="规格属性（每行一个，格式 键=值）">
          <el-input v-model="form.attributesText" type="textarea" :rows="3" placeholder="续航=36h&#10;防水=IPX5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="dialog = false">取消</el-button>
        <el-button type="primary" round :loading="saving" @click="submit">
          {{ editingId ? '保存修改' : '发布商品' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 商品页专属样式，其余通用样式见下方共享块 */
.page-desc {
  font-size: 13px;
  color: var(--muted);
}

.upload-area {
  width: 100%;
}

.upload-box {
  width: 140px;
}

.upload-placeholder {
  width: 140px;
  height: 140px;
  border: 1px dashed var(--line);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--muted-light);
  cursor: pointer;
  transition: border-color 0.2s;
}

.upload-placeholder:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.upload-placeholder span {
  font-size: 12px;
}

.upload-placeholder small {
  font-size: 9px;
  text-align: center;
  line-height: 1.4;
  padding: 0 8px;
}

.upload-preview {
  display: flex;
  align-items: center;
  gap: 14px;
}

.upload-preview img {
  width: 140px;
  height: 140px;
  object-fit: cover;
  border-radius: 12px;
  border: 1px solid var(--line);
}

.upload-ops {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.uploaded {
  font-size: 12px;
  color: var(--green);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.publish-form :deep(.el-form-item__label) {
  font-size: 12px;
  color: var(--muted);
}
</style>