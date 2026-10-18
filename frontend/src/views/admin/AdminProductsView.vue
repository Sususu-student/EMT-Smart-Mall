<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, productApi } from '@/api'
import type { AdminProductPayload, Category, Product } from '@/types'
import { money } from '@/utils/format'

const loading = ref(true)
const products = ref<Product[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const status = ref<number | undefined>(undefined)
const categoryFilter = ref('')
const categories = ref<Category[]>([])
const dialog = ref(false)
const saving = ref(false)
const editingId = ref('')
const attributeRows = ref<{ key: string; value: string }[]>([])
const importDialog = ref(false)
const importText = ref('')
const importing = ref(false)

const form = reactive({
  name: '',
  subtitle: '',
  categoryId: '',
  brand: '',
  price: 0,
  originalPrice: 0,
  stock: 0,
  description: '',
  tags: '',
  image: '/img/default.svg',
  status: 1
})

const leafCategories = ref<Category[]>([])

onMounted(async () => {
  const tree = await productApi.categories()
  categories.value = tree
  leafCategories.value = tree.flatMap((root) => root.children)
  await load()
})

async function load() {
  loading.value = true
  try {
    const result = await adminApi.products({
      page: page.value,
      size: 10,
      keyword: keyword.value || undefined,
      status: status.value,
      categoryId: categoryFilter.value || undefined
    })
    products.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = ''
  Object.assign(form, {
    name: '',
    subtitle: '',
    categoryId: leafCategories.value[0]?.id || '',
    brand: '',
    price: 0,
    originalPrice: 0,
    stock: 100,
    description: '',
    tags: '',
    image: '/img/default.svg',
    status: 1
  })
  attributeRows.value = [{ key: '', value: '' }]
}

function openImport() {
  const categoryId = leafCategories.value[0]?.id || ''
  if (!importText.value) {
    importText.value = JSON.stringify(
      [
        {
          name: '示例商品 · 请替换',
          subtitle: '批量导入示例',
          categoryId,
          brand: '示例品牌',
          price: 199,
          originalPrice: 239,
          stock: 100,
          description: '通过后台批量导入的商品',
          attributes: { 参数名: '参数值' },
          tags: '示例,导入',
          image: '/img/default.svg',
          status: 1
        }
      ],
      null,
      2
    )
  }
  importDialog.value = true
}

async function doImport() {
  let items: unknown
  try {
    items = JSON.parse(importText.value)
  } catch {
    ElMessage.error('JSON 格式不正确')
    return
  }
  if (!Array.isArray(items) || !items.length) {
    ElMessage.warning('请提供非空的 JSON 数组')
    return
  }
  importing.value = true
  try {
    const result = await adminApi.importProducts(items as AdminProductPayload[])
    if (result.failed) {
      ElMessage.warning(`导入完成：成功 ${result.success} 条，失败 ${result.failed} 条`)
      await ElMessageBox.alert(result.errors.slice(0, 8).join('\n'), '部分商品导入失败', {
        confirmButtonText: '知道了'
      })
    } else {
      ElMessage.success(`成功导入 ${result.success} 件商品`)
      importDialog.value = false
    }
    await load()
  } finally {
    importing.value = false
  }
}

function openCreate() {
  resetForm()
  dialog.value = true
}

function openEdit(product: Product) {
  editingId.value = product.id
  Object.assign(form, {
    name: product.name,
    subtitle: product.subtitle || '',
    categoryId: product.categoryId,
    brand: product.brand || '',
    price: Number(product.price),
    originalPrice: Number(product.originalPrice || product.price),
    stock: product.stock,
    description: product.description || '',
    tags: product.tags.join(','),
    image: product.image,
    status: product.status ?? 1
  })
  attributeRows.value = Object.entries(product.attributes || {}).map(([key, value]) => ({ key, value: String(value) }))
  if (!attributeRows.value.length) attributeRows.value = [{ key: '', value: '' }]
  dialog.value = true
}

function addAttribute() {
  attributeRows.value.push({ key: '', value: '' })
}

function removeAttribute(index: number) {
  attributeRows.value.splice(index, 1)
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写商品名称')
    return
  }
  if (!form.categoryId) {
    ElMessage.warning('请选择品类')
    return
  }
  if (!form.price || form.price <= 0) {
    ElMessage.warning('价格必须大于 0')
    return
  }
  const attributes: Record<string, string> = {}
  for (const row of attributeRows.value) {
    if (row.key.trim() && row.value.trim()) attributes[row.key.trim()] = row.value.trim()
  }
  const payload = {
    name: form.name,
    subtitle: form.subtitle,
    categoryId: form.categoryId,
    brand: form.brand,
    price: form.price,
    originalPrice: form.originalPrice || form.price,
    stock: form.stock,
    description: form.description,
    attributes,
    tags: form.tags,
    image: form.image || '/img/default.svg',
    status: form.status
  }
  saving.value = true
  try {
    if (editingId.value) {
      await adminApi.updateProduct(editingId.value, payload)
      ElMessage.success('商品已更新')
    } else {
      await adminApi.createProduct(payload)
      ElMessage.success('商品已创建')
    }
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(product: Product) {
  const next = product.status === 1 ? 0 : 1
  await adminApi.updateProductStatus(product.id, next)
  ElMessage.success(next === 1 ? '已上架' : '已下架')
  await load()
}

async function removeProduct(product: Product) {
  await ElMessageBox.confirm(`确定下架并删除「${product.name}」吗？`, '删除商品', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await adminApi.deleteProduct(product.id)
  ElMessage.success('商品已下架')
  await load()
}

function search() {
  page.value = 1
  load()
}

function changePage(value: number) {
  page.value = value
  load()
}

function categoryName(id: string) {
  for (const root of categories.value) {
    const child = root.children.find((item) => item.id === id)
    if (child) return `${root.name} / ${child.name}`
  }
  return id
}
</script>

<template>
  <div class="admin-page">
    <section class="panel">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="keyword" placeholder="搜索商品名称或品牌" clearable style="width: 220px" @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="categoryFilter" placeholder="全部品类" clearable style="width: 160px" @change="search">
            <el-option v-for="category in leafCategories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
          <el-select v-model="status" placeholder="全部状态" clearable style="width: 120px" @change="search">
            <el-option label="已上架" :value="1" />
            <el-option label="已下架" :value="0" />
          </el-select>
          <el-button round @click="search">查询</el-button>
        </div>
        <div class="head-actions">
          <el-button round @click="openImport">批量导入</el-button>
          <el-button type="primary" round @click="openCreate">+ 新增商品</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="products" style="width: 100%">
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="product-cell">
              <img :src="row.image" :alt="row.name" />
              <div>
                <strong>{{ row.name }}</strong>
                <span>{{ row.brand }} · {{ categoryName(row.categoryId) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="110">
          <template #default="{ row }">
            <strong>{{ money(row.price) }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="sales" label="销量" width="80" />
        <el-table-column label="评分" width="90">
          <template #default="{ row }">{{ Number(row.rating).toFixed(1) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeProduct(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 10"
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="10"
        :current-page="page"
        style="margin-top: 18px; justify-content: flex-end"
        @current-change="changePage"
      />
    </section>

    <el-dialog v-model="importDialog" title="批量导入商品" width="640px">
      <p class="import-tip">
        粘贴商品 JSON 数组，字段与新增商品一致：name、categoryId、price 必填；attributes 为对象。
        导入会逐条校验，失败条目会返回原因。
      </p>
      <el-input v-model="importText" type="textarea" :rows="14" spellcheck="false" class="import-input" />
      <template #footer>
        <el-button @click="importDialog = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">开始导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" :title="editingId ? '编辑商品' : '新增商品'" width="760px" top="6vh">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="商品名称" class="span-2">
            <el-input v-model="form.name" maxlength="200" />
          </el-form-item>
          <el-form-item label="副标题" class="span-2">
            <el-input v-model="form.subtitle" maxlength="200" placeholder="例如：主动降噪 · 24h 续航 · IPX5 防水" />
          </el-form-item>
          <el-form-item label="品类">
            <el-select v-model="form.categoryId" style="width: 100%">
              <el-option v-for="category in leafCategories" :key="category.id" :label="category.name" :value="category.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="品牌">
            <el-input v-model="form.brand" />
          </el-form-item>
          <el-form-item label="售价">
            <el-input-number v-model="form.price" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="原价">
            <el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="库存">
            <el-input-number v-model="form.stock" :min="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="上架" :value="1" />
              <el-option label="下架" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="图片地址" class="span-2">
            <el-input v-model="form.image" placeholder="/img/headphone.svg" />
          </el-form-item>
          <el-form-item label="标签（逗号分隔）" class="span-2">
            <el-input v-model="form.tags" placeholder="蓝牙耳机,运动,防水" />
          </el-form-item>
          <el-form-item label="商品描述" class="span-2">
            <el-input v-model="form.description" type="textarea" :rows="3" />
          </el-form-item>
        </div>

        <div class="attr-head">
          <div>
            <strong>商品参数</strong>
            <span>用于详情展示、Agent 推荐与对比评分</span>
          </div>
          <el-button size="small" round @click="addAttribute">+ 添加参数</el-button>
        </div>
        <div class="attr-rows">
          <div v-for="(row, index) in attributeRows" :key="index" class="attr-row">
            <el-input v-model="row.key" placeholder="参数名，如 续航" />
            <el-input v-model="row.value" placeholder="参数值，如 24" />
            <button class="attr-remove" @click="removeAttribute(index)"><el-icon><Delete /></el-icon></button>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存商品</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 15px;
  padding: 20px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.import-tip {
  font-size: 11px;
  color: var(--muted);
  line-height: 1.8;
  margin-bottom: 12px;
}

.import-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.product-cell img {
  width: 42px;
  height: 42px;
  border-radius: 7px;
  object-fit: cover;
  border: 1px solid var(--line);
  flex-shrink: 0;
}

.product-cell strong {
  display: block;
  font-size: 11px;
  font-weight: 600;
}

.product-cell span {
  display: block;
  font-size: 9px;
  color: var(--muted);
  margin-top: 2px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.span-2 {
  grid-column: span 2;
}

.attr-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--line);
  padding-top: 14px;
  margin-bottom: 10px;
}

.attr-head strong {
  font-size: 12px;
}

.attr-head span {
  display: block;
  font-size: 9px;
  color: var(--muted);
  margin-top: 2px;
}

.attr-rows {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.attr-row {
  display: grid;
  grid-template-columns: 1fr 1.4fr 30px;
  gap: 7px;
  align-items: center;
}

.attr-remove {
  border: none;
  background: transparent;
  color: var(--muted-light);
  cursor: pointer;
}

.attr-remove:hover {
  color: #d64545;
}

@media (max-width: 700px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: span 1;
  }
}
</style>
