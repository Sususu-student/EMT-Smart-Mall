<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '@/api'
import type { Category, Product } from '@/types'
import ProductCard from '@/components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const categories = ref<Category[]>([])
const products = ref<Product[]>([])
const total = ref(0)
const loading = ref(true)
const mobileFilter = ref(false)

const sort = computed(() => (route.query.sort as string) || 'default')
const activeCategory = computed(() => (route.query.categoryId as string) || '')
const keyword = computed(() => (route.query.keyword as string) || '')
const page = computed(() => Number(route.query.page || 1))

const priceOptions = [
  { label: '200 元以内', min: 0, max: 200 },
  { label: '200 - 500 元', min: 200, max: 500 },
  { label: '500 - 1000 元', min: 500, max: 1000 },
  { label: '1000 - 3000 元', min: 1000, max: 3000 },
  { label: '3000 元以上', min: 3000, max: undefined as number | undefined }
]

const activePriceLabel = computed(() => {
  const min = Number(route.query.minPrice || 0)
  const max = route.query.maxPrice ? Number(route.query.maxPrice) : undefined
  const found = priceOptions.find((option) => Number(option.min) === min && option.max === max)
  return found?.label || ''
})

const activeCategoryName = computed(() => {
  for (const root of categories.value) {
    if (root.id === activeCategory.value) return root.name
    const child = root.children.find((item) => item.id === activeCategory.value)
    if (child) return child.name
  }
  return '全部商品'
})

const hasFilters = computed(
  () => Boolean(activeCategory.value || keyword.value || route.query.minPrice || route.query.maxPrice)
)

onMounted(async () => {
  try {
    categories.value = await productApi.categories()
  } catch {
    categories.value = []
  }
})

async function load() {
  loading.value = true
  try {
    const result = await productApi.list({
      page: page.value,
      size: 12,
      categoryId: activeCategory.value || undefined,
      keyword: keyword.value || undefined,
      minPrice: route.query.minPrice || undefined,
      maxPrice: route.query.maxPrice || undefined,
      sort: sort.value
    })
    products.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query,
  () => {
    load()
    mobileFilter.value = false
  },
  { immediate: true, deep: true }
)

function updateQuery(patch: Record<string, string | undefined>) {
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries({ ...route.query, ...patch })) {
    if (value !== undefined && value !== null && value !== '') {
      query[key] = String(value)
    }
  }
  if (!Object.prototype.hasOwnProperty.call(patch, 'page')) {
    delete query.page
  }
  router.push({ name: 'products', query })
}

function selectCategory(id: string) {
  updateQuery({ categoryId: id || undefined })
}

function selectPrice(option: (typeof priceOptions)[number] | null) {
  if (!option) {
    updateQuery({ minPrice: undefined, maxPrice: undefined })
    return
  }
  updateQuery({
    minPrice: String(option.min),
    maxPrice: option.max === undefined ? undefined : String(option.max)
  })
}

function clearFilters() {
  router.push({ name: 'products', query: keyword.value ? { keyword: keyword.value } : {} })
}

function removeKeyword() {
  updateQuery({ keyword: undefined })
}

function changePage(value: number) {
  router.push({ name: 'products', query: { ...route.query, page: String(value) } })
}

function changeSort(value: string) {
  updateQuery({ sort: value === 'default' ? undefined : value })
}
</script>

<template>
  <div class="products-page">
    <div class="page-head">
      <div class="container">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>全部商品</el-breadcrumb-item>
          <el-breadcrumb-item v-if="activeCategoryName !== '全部商品'">{{ activeCategoryName }}</el-breadcrumb-item>
        </el-breadcrumb>
        <h1>{{ keyword ? `“${keyword}” 的搜索结果` : activeCategoryName }}</h1>
        <p class="desc">
          {{ keyword ? `共找到 ${total} 件相关商品，` : '所有商品参数均由数据库提供，' }}
          支持按品类、价格和功能偏好筛选，Agent 推荐时会引用这些真实字段。
        </p>
      </div>
    </div>

    <div class="container products-layout">
      <aside class="filters" :class="{ open: mobileFilter }">
        <div class="filter-head">
          <strong>筛选</strong>
          <button v-if="hasFilters" class="clear" @click="clearFilters">清空</button>
        </div>

        <div class="filter-group">
          <h4>品类</h4>
          <button class="cat-all" :class="{ active: !activeCategory }" @click="selectCategory('')">全部商品</button>
          <div v-for="root in categories" :key="root.id" class="cat-root">
            <button class="cat-root-name" :class="{ active: activeCategory === root.id }" @click="selectCategory(root.id)">
              <span>{{ root.icon }}</span>{{ root.name }}
            </button>
            <div class="cat-children">
              <button
                v-for="child in root.children"
                :key="child.id"
                :class="{ active: activeCategory === child.id }"
                @click="selectCategory(child.id)"
              >
                {{ child.name }}
              </button>
            </div>
          </div>
        </div>

        <div class="filter-group">
          <h4>价格区间</h4>
          <button
            v-for="option in priceOptions"
            :key="option.label"
            class="price-option"
            :class="{ active: activePriceLabel === option.label }"
            @click="selectPrice(option)"
          >
            {{ option.label }}
          </button>
        </div>

        <div class="filter-tip">
          <el-icon><MagicStick /></el-icon>
          <p>不知道怎么选？<router-link to="/chat">让 Agent 帮你挑 →</router-link></p>
        </div>
      </aside>

      <section class="results">
        <div class="toolbar">
          <div class="active-filters">
            <button v-if="keyword" class="filter-chip" @click="removeKeyword">
              {{ keyword }} <el-icon><Close /></el-icon>
            </button>
            <button v-if="activeCategory" class="filter-chip" @click="selectCategory('')">
              {{ activeCategoryName }} <el-icon><Close /></el-icon>
            </button>
            <button v-if="activePriceLabel" class="filter-chip" @click="selectPrice(null)">
              {{ activePriceLabel }} <el-icon><Close /></el-icon>
            </button>
            <span v-if="!hasFilters" class="result-count">共 {{ total }} 件商品</span>
          </div>
          <div class="toolbar-right">
            <button class="mobile-filter-btn" @click="mobileFilter = !mobileFilter">
              <el-icon><Filter /></el-icon> 筛选
            </button>
            <el-select :model-value="sort" size="default" style="width: 132px" @change="changeSort">
              <el-option label="综合排序" value="default" />
              <el-option label="销量优先" value="sales" />
              <el-option label="评分优先" value="rating" />
              <el-option label="价格从低到高" value="price_asc" />
              <el-option label="价格从高到低" value="price_desc" />
              <el-option label="最新上架" value="new" />
            </el-select>
          </div>
        </div>

        <div v-loading="loading" class="product-grid">
          <ProductCard v-for="product in products" :key="product.id" :product="product" />
        </div>

        <div v-if="!loading && !products.length" class="empty">
          <strong>没有找到匹配的商品</strong>
          <p>试着放宽价格区间，或换一个关键词。</p>
          <el-button style="margin-top: 18px" round @click="clearFilters">清空筛选</el-button>
        </div>

        <el-pagination
          v-if="total > 12"
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="12"
          :current-page="page"
          style="margin-top: 36px"
          @current-change="changePage"
        />
      </section>
    </div>
  </div>
</template>

<style scoped>
.products-page {
  padding-bottom: 40px;
}

.products-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 36px;
  padding-top: 32px;
  align-items: start;
}

.filters {
  position: sticky;
  top: 96px;
  border-right: 1px solid var(--line);
  padding-right: 24px;
}

.filter-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.filter-head strong {
  font-size: 15px;
}

.clear {
  border: none;
  background: transparent;
  color: var(--brand);
  font-size: 11px;
  cursor: pointer;
}

.filter-group {
  padding: 20px 0;
  border-bottom: 1px solid var(--line);
}

.filter-group h4 {
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--muted);
  text-transform: uppercase;
  margin-bottom: 12px;
}

.cat-all,
.cat-root-name,
.price-option {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  font-size: 13px;
  color: var(--ink-soft);
  padding: 7px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.18s, color 0.18s;
}

.cat-all:hover,
.cat-root-name:hover,
.price-option:hover {
  background: #fff;
  color: var(--ink);
}

.cat-all.active,
.cat-root-name.active {
  background: var(--ink);
  color: #fff;
}

.cat-root {
  margin-top: 6px;
}

.cat-root-name {
  font-weight: 600;
  font-size: 12.5px;
}

.cat-root-name span {
  font-size: 15px;
}

.cat-children {
  display: flex;
  flex-direction: column;
  padding: 4px 0 4px 26px;
}

.cat-children button {
  border: none;
  background: transparent;
  text-align: left;
  font-size: 12px;
  color: var(--muted);
  padding: 5px 0;
  cursor: pointer;
}

.cat-children button:hover,
.cat-children button.active {
  color: var(--brand);
}

.price-option.active {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 600;
}

.filter-tip {
  margin-top: 20px;
  background: #fff;
  border: 1px dashed var(--line-strong);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  gap: 9px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--muted);
}

.filter-tip .el-icon {
  color: var(--brand);
  font-size: 15px;
  flex-shrink: 0;
}

.filter-tip a {
  color: var(--brand-deep);
  font-weight: 600;
}

.results {
  min-width: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 20px;
  min-height: 36px;
}

.active-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--line-strong);
  background: #fff;
  border-radius: 99px;
  padding: 5px 10px;
  font-size: 11px;
  cursor: pointer;
  color: var(--ink-soft);
}

.filter-chip:hover {
  border-color: var(--brand);
  color: var(--brand-deep);
}

.result-count {
  font-size: 12px;
  color: var(--muted);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.mobile-filter-btn {
  display: none;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 9px;
  padding: 9px 12px;
  font-size: 12px;
  cursor: pointer;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  min-height: 320px;
}

@media (max-width: 1080px) {
  .products-layout {
    grid-template-columns: 190px 1fr;
    gap: 24px;
  }

  .product-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 760px) {
  .products-layout {
    grid-template-columns: 1fr;
  }

  .filters {
    display: none;
    position: static;
    border-right: none;
    padding-right: 0;
  }

  .filters.open {
    display: block;
    background: #fff;
    border: 1px solid var(--line);
    border-radius: 14px;
    padding: 18px;
    max-height: 60vh;
    overflow: auto;
  }

  .mobile-filter-btn {
    display: inline-flex;
  }
}

@media (max-width: 480px) {
  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
