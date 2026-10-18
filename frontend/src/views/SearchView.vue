<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '@/api'
import type { Product } from '@/types'
import ProductCard from '@/components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const submitted = ref('')
const products = ref<Product[]>([])
const total = ref(0)
const loading = ref(false)
const sort = ref('default')
const page = ref(1)
const trending = ref<Product[]>([])

const hotWords = ['蓝牙耳机', '跑步鞋', '咖啡机', '双肩包', '扫地机器人', '护肤品']
const showEmptyTip = computed(() => !loading.value && submitted.value && !products.value.length)

onMounted(async () => {
  try {
    const result = await productApi.list({ page: 1, size: 4, sort: 'sales' })
    trending.value = result.records
  } catch {
    trending.value = []
  }
})

async function search() {
  const value = keyword.value.trim()
  if (!value) return
  submitted.value = value
  page.value = 1
  await router.replace({ name: 'search', query: { keyword: value } })
  await load()
}

async function load() {
  const value = (route.query.keyword as string) || ''
  keyword.value = value
  submitted.value = value
  if (!value) {
    products.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await productApi.search({
      keyword: value,
      page: page.value,
      size: 12,
      sort: sort.value
    })
    products.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query.keyword,
  () => load(),
  { immediate: true }
)

function quickSearch(word: string) {
  keyword.value = word
  search()
}

function changeSort(value: string) {
  sort.value = value
  load()
}

function changePage(value: number) {
  page.value = value
  load()
  window.scrollTo({ top: 260, behavior: 'smooth' })
}
</script>

<template>
  <div class="search-page">
    <div class="search-hero">
      <div class="container search-hero-inner">
        <span class="eyebrow">SEARCH THE CATALOG</span>
        <h1 class="display">{{ submitted ? '搜索结果' : '找点什么？' }}</h1>
        <form class="search-form" @submit.prevent="search">
          <el-icon><Search /></el-icon>
          <input v-model="keyword" type="search" placeholder="输入商品名称、品牌或品类" aria-label="搜索商品" />
          <button type="submit">搜索</button>
        </form>
        <div class="hot-words">
          <span>热门搜索</span>
          <button v-for="word in hotWords" :key="word" @click="quickSearch(word)">{{ word }}</button>
        </div>
      </div>
    </div>

    <div class="container results-wrap">
      <template v-if="submitted">
        <div class="results-head">
          <div>
            <h2>“{{ submitted }}”</h2>
            <p>共 {{ total }} 件商品，按当前排序展示</p>
          </div>
          <el-select v-model="sort" style="width: 140px" @change="changeSort">
            <el-option label="综合排序" value="default" />
            <el-option label="销量优先" value="sales" />
            <el-option label="评分优先" value="rating" />
            <el-option label="价格从低到高" value="price_asc" />
            <el-option label="价格从高到低" value="price_desc" />
          </el-select>
        </div>

        <div v-loading="loading" class="product-grid">
          <ProductCard v-for="product in products" :key="product.id" :product="product" />
        </div>

        <div v-if="showEmptyTip" class="empty">
          <strong>没有找到“{{ submitted }}”</strong>
          <p>试试更宽泛的关键词，或直接让 Agent 理解你的需求。</p>
          <el-button type="primary" round style="margin-top: 18px" @click="router.push('/chat')">去对话导购</el-button>
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
      </template>

      <template v-else>
        <div class="section-head">
          <div>
            <span class="eyebrow">TRENDING NOW</span>
            <h2>最近大家都在买</h2>
          </div>
          <router-link to="/products" class="link-more">全部商品 <el-icon><ArrowRight /></el-icon></router-link>
        </div>
        <div class="product-grid four">
          <ProductCard v-for="product in trending" :key="product.id" :product="product" />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.search-page {
  padding-bottom: 40px;
}

.search-hero {
  background:
    radial-gradient(circle at 80% 10%, rgba(240, 90, 40, 0.1), transparent 40%),
    #f2ede3;
  border-bottom: 1px solid var(--line);
  padding: 54px 0 44px;
}

.search-hero-inner {
  max-width: 760px;
  text-align: center;
}

.search-hero h1 {
  font-size: clamp(34px, 4vw, 48px);
  margin: 12px 0 24px;
}

.search-form {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border: 1px solid var(--line-strong);
  border-radius: 16px;
  padding: 8px 8px 8px 18px;
  box-shadow: 0 18px 48px rgba(47, 40, 27, 0.09);
}

.search-form .el-icon {
  color: var(--brand);
  font-size: 19px;
}

.search-form input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  padding: 10px 0;
  background: transparent;
}

.search-form button {
  border: none;
  background: var(--ink);
  color: #fff;
  border-radius: 11px;
  padding: 13px 26px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.search-form button:hover {
  background: var(--brand);
}

.hot-words {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 16px;
  font-size: 11px;
  color: var(--muted);
}

.hot-words button {
  border: none;
  background: transparent;
  color: var(--ink-soft);
  font-size: 11px;
  cursor: pointer;
  border-bottom: 1px solid var(--line-strong);
  padding: 1px 0;
}

.hot-words button:hover {
  color: var(--brand);
  border-color: var(--brand);
}

.results-wrap {
  padding-top: 36px;
}

.results-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.results-head h2 {
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 28px;
  font-weight: 600;
}

.results-head p {
  font-size: 12px;
  color: var(--muted);
  margin-top: 5px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  min-height: 260px;
}

.product-grid.four {
  min-height: 120px;
}

@media (max-width: 1080px) {
  .product-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 780px) {
  .product-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .product-grid {
    grid-template-columns: 1fr;
  }

  .search-form button {
    padding: 12px 16px;
  }
}
</style>
