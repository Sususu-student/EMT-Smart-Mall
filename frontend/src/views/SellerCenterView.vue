<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { productApi, sellerApi, uploadApi } from '@/api'
import type { Category, Product, SellerOrderRow, SellerOverview } from '@/types'
import { dateTime, money } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import ProductImage from '@/components/ProductImage.vue'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const tab = ref('products')
const overview = ref<SellerOverview | null>(null)
const products = ref<Product[]>([])
const categories = ref<Category[]>([])
const orders = ref<SellerOrderRow[]>([])
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

async function loadOrders() {
  orders.value = await sellerApi.orders(10)
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

function switchTab(name: string) {
  tab.value = name
  if (name === 'orders' && !orders.value.length) loadOrders()
}
</script>

<template>
  <div class="seller-page">
    <div class="container">
      <header class="seller-head">
        <div>
          <span class="eyebrow">SELLER CENTER</span>
          <h1 class="display">卖家中心</h1>
          <p>发布商品、维护库存与价格，查看自己商品产生的订单。</p>
        </div>
        <el-button type="primary" round size="large" @click="openPublish">
          <el-icon><Plus /></el-icon> 发布商品
        </el-button>
      </header>

      <section v-loading="loading" class="stat-grid">
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

      <el-tabs :model-value="tab" @tab-change="switchTab">
        <el-tab-pane label="商品管理" name="products">
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
        </el-tab-pane>

        <el-tab-pane label="我的订单" name="orders">
          <el-table :data="orders" style="width: 100%">
            <el-table-column prop="productName" label="商品" min-width="200" />
            <el-table-column label="数量" width="90">
              <template #default="{ row }">{{ row.quantity }}</template>
            </el-table-column>
            <el-table-column label="金额" width="120">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="订单号" min-width="180">
              <template #default="{ row }">
                <span class="order-no">{{ row.orderId }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!orders.length" class="empty-state small">
            <strong>还没有订单</strong>
            <p>买家下单后会显示在这里。</p>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="dialog" :title="editingId ? '编辑商品' : '发布商品'" width="620px">
      <el-form label-position="top" class="publish-form">
        <el-form-item label="商品名称" required>
          <el-input v-model="form.name" placeholder="例如：星野 降噪蓝牙耳机 Pro" maxlength="60" />
        </el-form-item>
        <el-form-item label="一句话卖点">
          <el-input v-model="form.subtitle" placeholder="例如：主动降噪 · 36h 续航 · IPX5 防水" maxlength="80" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="品类" required>
            <el-select v-model="form.categoryId" filterable style="width: 100%">
              <el-option v-for="c in flatCategories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="品牌">
            <el-input v-model="form.brand" placeholder="选填" />
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="售价（元）" required>
            <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="10" style="width: 100%" />
          </el-form-item>
          <el-form-item label="原价（元）">
            <el-input-number v-model="form.originalPrice" :min="0" :precision="2" :step="10" style="width: 100%" />
          </el-form-item>
          <el-form-item label="库存">
            <el-input-number v-model="form.stock" :min="0" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="商品参数（每行一条，格式 键=值）">
          <el-input
            v-model="form.attributesText"
            type="textarea"
            :rows="4"
            placeholder="续航=36h&#10;防水等级=IPX5&#10;重量=5.2g"
          />
        </el-form-item>
        <el-form-item label="商品描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
        <el-form-item label="商品主图">
          <div class="upload-block">
            <div v-if="form.image" class="upload-preview">
              <img :src="form.image" alt="主图预览" />
              <div class="upload-preview-actions">
                <span class="upload-ok">已上传</span>
                <el-button size="small" round @click="clearImage">移除</el-button>
              </div>
            </div>
            <el-upload
              v-else
              class="upload-box"
              drag
              :show-file-list="false"
              :http-request="onImagePicked"
              accept="image/jpeg,image/png,image/webp,image/gif"
            >
              <div v-loading="uploading" class="upload-inner">
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <p>把图片拖到这里，或<em>点击选择</em></p>
                <span>支持 jpg / png / webp / gif，最大 5MB</span>
              </div>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="标签（逗号分隔）">
          <el-input v-model="form.tags" placeholder="例如：降噪,长续航" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">
          {{ editingId ? '保存修改' : '确认上架' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.seller-page {
  padding: 40px 0 70px;
  min-height: 70vh;
}

.seller-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 26px;
}

.seller-head h1 {
  font-family: var(--font-digit), var(--font-serif);
  font-weight: 700;
  font-size: clamp(30px, 4vw, 44px);
  margin: 10px 0 8px;
}

.seller-head p {
  color: var(--muted);
  font-size: 13px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 26px;
  min-height: 96px;
}

.stat-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 18px;
  box-shadow: var(--shadow-card);
}

.stat-card span {
  display: block;
  font-size: 10px;
  color: var(--muted);
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}

.stat-card strong {
  font-family: var(--font-digit);
  font-variant-numeric: tabular-nums;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.stat-card.dark {
  background: var(--ink);
  border-color: var(--ink);
}

.stat-card.dark span {
  color: #a49d90;
}

.stat-card.dark strong {
  color: #fff;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.product-row {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 13px;
  padding: 13px 16px;
  box-shadow: var(--shadow-card);
  transition: border-color 0.22s ease, transform 0.22s ease;
}

.product-row:hover {
  border-color: var(--line-strong);
  transform: translateY(-2px);
}

.thumb {
  width: 62px;
  flex-shrink: 0;
  border-radius: 9px;
  overflow: hidden;
}

.info {
  flex: 1;
  min-width: 0;
}

.info strong {
  display: block;
  font-size: 13px;
  margin-bottom: 3px;
}

.info .sub {
  display: block;
  font-size: 10px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tags {
  display: flex;
  gap: 5px;
  margin-top: 6px;
}

.tag {
  font-size: 9px;
  padding: 2px 7px;
  border-radius: 99px;
  background: var(--bg);
  color: var(--muted);
}

.tag.ok {
  background: var(--success-tint);
  color: var(--green);
}

.tag.off {
  background: var(--danger-tint-strong);
  color: #b9532a;
}

.metric {
  width: 74px;
  text-align: right;
  flex-shrink: 0;
}

.metric span {
  display: block;
  font-size: 9px;
  color: var(--muted-light);
  margin-bottom: 3px;
}

.metric strong {
  font-family: var(--font-digit);
  font-variant-numeric: tabular-nums;
  font-size: 14px;
}

.row-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.pager {
  margin-top: 18px;
}

.empty-state {
  text-align: center;
  padding: 54px 20px;
  color: var(--muted);
  background: var(--surface);
  border: 1px dashed var(--line-strong);
  border-radius: 14px;
}

.empty-state.small {
  padding: 30px;
  border: none;
}

.empty-state .el-icon {
  font-size: 28px;
  color: var(--muted-light);
  margin-bottom: 10px;
}

.empty-state strong {
  display: block;
  color: var(--ink);
  font-size: 14px;
  margin-bottom: 6px;
}

.empty-state p {
  font-size: 11px;
}

.order-no {
  font-family: var(--font-digit);
  font-size: 11px;
  color: var(--muted);
}

.upload-block {
  width: 100%;
}

.upload-box {
  width: 100%;
}

.upload-box :deep(.el-upload),
.upload-box :deep(.el-upload-dragger) {
  width: 100%;
}

.upload-box :deep(.el-upload-dragger) {
  border-radius: 12px;
  border: 1px dashed var(--line-strong);
  background: var(--surface-sunken);
  padding: 22px 16px;
  transition: border-color 0.22s ease, background 0.22s ease;
}

.upload-box :deep(.el-upload-dragger:hover) {
  border-color: var(--brand);
  background: var(--brand-tint-strong);
}

.upload-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-height: 78px;
  justify-content: center;
}

.upload-icon {
  font-size: 26px;
  color: var(--muted-light);
}

.upload-inner p {
  font-size: 12px;
  color: var(--ink-soft);
}

.upload-inner p em {
  font-style: normal;
  color: var(--brand);
}

.upload-inner span {
  font-size: 10px;
  color: var(--muted-light);
}

.upload-preview {
  display: flex;
  align-items: center;
  gap: 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px;
  background: var(--surface-sunken);
}

.upload-preview img {
  width: 78px;
  height: 78px;
  object-fit: cover;
  border-radius: 9px;
  background: var(--bg);
}

.upload-preview-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.upload-ok {
  font-size: 11px;
  color: var(--green);
}

.publish-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 0 14px;
}

@media (max-width: 900px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .product-row {
    flex-wrap: wrap;
  }

  .seller-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>