<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { priceWatchApi, productApi } from '@/api'
import type { Product, Review } from '@/types'
import { attributeEntries, dateOnly, money } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useCompareStore } from '@/stores/compare'
import ProductImage from '@/components/ProductImage.vue'
import ProductCard from '@/components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const compare = useCompareStore()

const product = ref<Product | null>(null)
const related = ref<Product[]>([])
const reviews = ref<Review[]>([])
const reviewTotal = ref(0)
const reviewPage = ref(1)
const loading = ref(true)
const quantity = ref(1)
const adding = ref(false)
const favored = ref(false)
const activeTab = ref('params')
const reviewDialog = ref(false)
const reviewForm = ref({ rating: 5, content: '' })
const submittingReview = ref(false)

const attrs = computed(() => attributeEntries(product.value))
const inCompare = computed(() => (product.value ? compare.has(product.value.id) : false))
const categoryTrail = computed(() => product.value?.categoryName || '商品')

onMounted(load)
watch(() => route.params.id, load)

async function load() {
  const id = route.params.id as string
  loading.value = true
  try {
    const data = await productApi.detail(id)
    product.value = data
    favored.value = Boolean(data.favorited)
    quantity.value = 1
    const [rel, rev] = await Promise.all([
      productApi.related(id).catch(() => []),
      productApi.reviews(id, { page: 1, size: 6 }).catch(() => ({ records: [], total: 0, page: 1, size: 6, pages: 0 }))
    ])
    related.value = rel
    reviews.value = rev.records
    reviewTotal.value = rev.total
    reviewPage.value = 1
    // 已登录时查询是否已开启降价提醒
    if (auth.isLoggedIn) {
      priceWatchApi.status(id).then((r) => { watching.value = r.watching }).catch(() => {})
    }
  } finally {
    loading.value = false
  }
}

async function loadReviews(page: number) {
  if (!product.value) return
  const result = await productApi.reviews(product.value.id, { page, size: 6 })
  reviews.value = result.records
  reviewTotal.value = result.total
  reviewPage.value = page
}

async function addToCart() {
  if (!product.value) return
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  adding.value = true
  try {
    await cart.add(product.value.id, quantity.value)
    ElMessage.success('已加入购物车')
  } finally {
    adding.value = false
  }
}

function buyNow() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  router.push({ name: 'checkout', query: { productId: product.value?.id, quantity: String(quantity.value) } })
}

async function toggleFavorite() {
  if (!product.value) return
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  const { userApi } = await import('@/api')
  favored.value = await userApi.toggleFavorite(product.value.id)
  ElMessage.success(favored.value ? '已收藏' : '已取消收藏')
}

/** 降价提醒：开启后系统每天扫描价格，降价会发站内信 */
const watching = ref(false)

async function togglePriceWatch() {
  if (!product.value) return
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const res = await priceWatchApi.toggle(product.value.id)
    watching.value = res.watching
    ElMessage.success(res.watching ? '已开启降价提醒，降价后会通知你' : '已关闭降价提醒')
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  }
}

function toggleCompare() {
  if (!product.value) return
  if (inCompare.value) {
    compare.remove(product.value.id)
    ElMessage.info('已移出对比清单')
    return
  }
  if (!compare.add(product.value.id)) {
    ElMessage.warning(`最多同时对比 ${compare.max} 件商品`)
    return
  }
  ElMessage.success('已加入对比清单')
}

function askAgent() {
  if (!product.value) return
  router.push({ name: 'chat', query: { q: `帮我看看「${product.value.name}」怎么样，值不值得买` } })
}

function openReview() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  reviewDialog.value = true
}

async function submitReview() {
  if (!product.value) return
  if (reviewForm.value.content.trim().length < 5) {
    ElMessage.warning('评价至少写 5 个字')
    return
  }
  submittingReview.value = true
  try {
    await productApi.addReview(product.value.id, reviewForm.value)
    ElMessage.success('评价已发布')
    reviewDialog.value = false
    reviewForm.value = { rating: 5, content: '' }
    await loadReviews(1)
    product.value = await productApi.detail(product.value.id)
  } finally {
    submittingReview.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="detail-page">
    <div v-if="product" class="container">
      <el-breadcrumb separator="/" class="crumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ name: 'products' }">全部商品</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ name: 'products', query: { categoryId: product.categoryId } }">
          {{ product.categoryName }}
        </el-breadcrumb-item>
        <el-breadcrumb-item>{{ product.name }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="detail-grid">
        <div class="gallery">
          <div class="main-image">
            <ProductImage :src="product.image" :fallback-src="product.imageFallback" :alt="product.name" ratio="1 / 1" />
            <span class="image-badge">{{ product.categoryName }}</span>
          </div>
          <div class="gallery-strip">
            <div class="strip-item active">
              <ProductImage :src="product.image" :fallback-src="product.imageFallback" :alt="product.name" ratio="1 / 1" />
            </div>
            <div class="strip-note">
              <el-icon><Camera /></el-icon>
              <span>商品参数图 · 由商品库提供</span>
            </div>
          </div>
        </div>

        <div class="info">
          <div class="meta-row">
            <span class="pill brand">{{ product.brand }}</span>
            <span class="pill">{{ product.categoryName }}</span>
            <span v-if="product.sales > 500" class="pill dark">热卖</span>
          </div>
          <h1>{{ product.name }}</h1>
          <p class="subtitle">{{ product.subtitle }}</p>

          <div class="rating-row">
            <el-rate :model-value="Number(product.rating)" disabled />
            <strong>{{ Number(product.rating).toFixed(1) }}</strong>
            <button class="text-link" @click="activeTab = 'reviews'">{{ product.reviewCount }} 条评价</button>
            <span class="divider"></span>
            <span>已售 {{ product.sales }}</span>
          </div>

          <div class="price-panel">
            <div class="price-line">
              <span class="label">价格</span>
              <strong class="big-price">{{ money(product.price) }}</strong>
              <span v-if="product.originalPrice" class="price-old">{{ money(product.originalPrice) }}</span>
            </div>
            <div class="price-meta">
              <span>库存 {{ product.stock }} 件</span>
              <span>·</span>
              <span>下单后 30 分钟内支付</span>
            </div>
          </div>

          <div class="attr-preview">
            <div v-for="attr in attrs.slice(0, 4)" :key="attr.key" class="attr-cell">
              <span>{{ attr.key }}</span>
              <strong>{{ attr.value }}{{ attr.unit || '' }}</strong>
            </div>
          </div>

          <div class="quantity-row">
            <span class="label">数量</span>
            <el-input-number v-model="quantity" :min="1" :max="Math.max(1, product.stock)" size="large" />
            <span class="stock-tip">单次最多 {{ Math.max(1, product.stock) }} 件</span>
          </div>

          <div class="actions">
            <el-button type="primary" size="large" :loading="adding" :disabled="product.stock <= 0" @click="addToCart">
              <el-icon><ShoppingCart /></el-icon>
              加入购物车
            </el-button>
            <el-button size="large" class="buy-btn" :disabled="product.stock <= 0" @click="buyNow">立即购买</el-button>
          </div>

          <div class="secondary-actions">
            <button :class="{ active: favored }" @click="toggleFavorite">
              <el-icon><StarFilled v-if="favored" /><Star v-else /></el-icon>
              {{ favored ? '已收藏' : '收藏' }}
            </button>
            <button :class="{ active: inCompare }" @click="toggleCompare">
              <el-icon><DataAnalysis /></el-icon>
              {{ inCompare ? '已在对比清单' : '加入对比' }}
            </button>
            <button :class="{ active: watching }" @click="togglePriceWatch">
              <el-icon><BellFilled v-if="watching" /><Bell v-else /></el-icon>
              {{ watching ? '已开启降价提醒' : '降价提醒' }}
            </button>
            <button @click="askAgent">
              <el-icon><ChatDotRound /></el-icon>
              问 Agent
            </button>
          </div>

          <div class="agent-note">
            <div class="agent-avatar">AI</div>
            <div>
              <strong>想听更具体的建议？</strong>
              <p>把使用场景告诉导购 Agent，它会结合参数解释这件商品适不适合你。</p>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>

          <div class="promise-row">
            <span><el-icon><CircleCheck /></el-icon> 参数来自商品库</span>
            <span><el-icon><CircleCheck /></el-icon> 价格库存实时校验</span>
            <span><el-icon><CircleCheck /></el-icon> 演示环境不产生真实交易</span>
          </div>
        </div>
      </div>

      <div class="detail-tabs">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="商品参数" name="params">
            <div class="tab-content params">
              <div class="param-table">
                <div v-for="attr in attrs" :key="attr.key" class="param-row">
                  <span>{{ attr.key }}</span>
                  <strong>{{ attr.value }}{{ attr.unit || '' }}</strong>
                </div>
                <div class="param-row">
                  <span>品牌</span>
                  <strong>{{ product.brand }}</strong>
                </div>
                <div class="param-row">
                  <span>品类</span>
                  <strong>{{ product.categoryName }}</strong>
                </div>
              </div>
              <div class="description">
                <h3>商品说明</h3>
                <p>{{ product.description }}</p>
                <div class="tags">
                  <span v-for="tag in product.tags" :key="tag" class="pill">{{ tag }}</span>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`用户评价 (${reviewTotal})`" name="reviews">
            <div class="tab-content reviews">
              <div class="reviews-head">
                <div>
                  <strong class="review-score">{{ Number(product.rating).toFixed(1) }}</strong>
                  <el-rate :model-value="Number(product.rating)" disabled />
                  <span class="muted">{{ reviewTotal }} 条真实评价</span>
                </div>
                <el-button round @click="openReview">写评价</el-button>
              </div>
              <div v-if="reviews.length" class="review-list">
                <article v-for="review in reviews" :key="review.id" class="review-item">
                  <div class="review-avatar">{{ (review.username || '智').slice(0, 1) }}</div>
                  <div>
                    <div class="review-meta">
                      <strong>{{ review.username }}</strong>
                      <el-rate :model-value="review.rating" disabled size="small" />
                      <span>{{ dateOnly(review.createdAt) }}</span>
                    </div>
                    <p>{{ review.content }}</p>
                  </div>
                </article>
              </div>
              <div v-else class="empty">
                <strong>还没有评价</strong>
                <p>成为第一个分享体验的人。</p>
              </div>
              <el-pagination
                v-if="reviewTotal > 6"
                background
                layout="prev, pager, next"
                :total="reviewTotal"
                :page-size="6"
                :current-page="reviewPage"
                style="margin-top: 20px; justify-content: flex-start"
                @current-change="loadReviews"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="推荐与对比" name="agent">
            <div class="tab-content agent-tab">
              <div class="agent-block">
                <span class="eyebrow">EXPLAINABLE RECOMMENDATION</span>
                <h3>这件商品适合谁？</h3>
                <p>
                  导购 Agent 会读取上方全部参数，结合你的预算和使用场景生成推荐理由。
                  如果参数不足以支撑结论，它会明确说明，而不是编造卖点。
                </p>
                <el-button type="primary" round @click="askAgent">让 Agent 分析这件商品</el-button>
              </div>
              <div class="agent-compare">
                <h4>同类商品快速对比</h4>
                <p>把本品与同类商品加入对比清单，Agent 会构建结构化矩阵并加权评分。</p>
                <div class="compare-actions">
                  <el-button round @click="toggleCompare">{{ inCompare ? '移出对比清单' : '加入对比清单' }}</el-button>
                  <el-button round @click="router.push('/compare')">前往对比中心</el-button>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <section v-if="related.length" class="related">
        <div class="section-head">
          <div>
            <span class="eyebrow">YOU MAY ALSO LIKE</span>
            <h2>同类推荐</h2>
          </div>
          <router-link :to="{ name: 'products', query: { categoryId: product.categoryId } }" class="link-more">
            更多{{ product.categoryName }} <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
        <div class="related-grid">
          <ProductCard v-for="item in related" :key="item.id" :product="item" compact />
        </div>
      </section>
    </div>

    <el-dialog v-model="reviewDialog" title="发表评价" width="440px">
      <div class="review-form">
        <label>评分</label>
        <el-rate v-model="reviewForm.rating" />
        <label>评价内容</label>
        <el-input v-model="reviewForm.content" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="说说你的使用体验…" />
      </div>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button type="primary" :loading="submittingReview" @click="submitReview">发布评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-page {
  padding: 26px 0 40px;
  min-height: 60vh;
}

.crumb {
  padding: 6px 0 22px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1.02fr 0.98fr;
  gap: 46px;
  align-items: start;
}

.gallery {
  position: sticky;
  top: 96px;
}

.main-image {
  position: relative;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: var(--surface);
}

.image-badge {
  position: absolute;
  left: 16px;
  top: 16px;
  background: var(--surface-glass);
  backdrop-filter: blur(6px);
  border-radius: 99px;
  padding: 6px 12px;
  font-size: 11px;
  letter-spacing: 0.04em;
}

.gallery-strip {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.strip-item {
  width: 68px;
  border-radius: 9px;
  overflow: hidden;
  border: 1px solid var(--ink);
}

.strip-note {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  color: var(--muted);
}

.info h1 {
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: clamp(26px, 3vw, 36px);
  font-weight: 600;
  line-height: 1.35;
  margin: 14px 0 10px;
}

.meta-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.subtitle {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.rating-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  font-size: 12px;
  color: var(--muted);
}

.rating-row :deep(.el-rate) {
  height: 18px;
}

.rating-row strong {
  color: var(--ink);
  font-size: 14px;
}

.text-link {
  border: none;
  background: transparent;
  color: var(--ink);
  font-size: 12px;
  border-bottom: 1px solid var(--line-strong);
  cursor: pointer;
  padding: 0;
}

.divider {
  width: 1px;
  height: 11px;
  background: var(--line-strong);
}

.price-panel {
  margin: 22px 0;
  background: var(--surface-sunken);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 16px 18px;
}

.price-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.price-line .label {
  font-size: 11px;
  color: var(--muted);
  letter-spacing: 0.08em;
}

.big-price {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.price-meta {
  display: flex;
  gap: 8px;
  font-size: 10px;
  color: var(--muted);
  margin-top: 5px;
}

.attr-preview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  background: var(--line);
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 22px;
}

.attr-cell {
  background: var(--surface);
  padding: 12px;
}

.attr-cell span {
  display: block;
  font-size: 9px;
  color: var(--muted);
  letter-spacing: 0.06em;
  margin-bottom: 4px;
}

.attr-cell strong {
  font-size: 12px;
  font-weight: 600;
}

.quantity-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
}

.quantity-row .label {
  font-size: 12px;
  color: var(--muted);
}

.stock-tip {
  font-size: 10px;
  color: var(--muted-light);
}

.actions {
  display: flex;
  gap: 10px;
}

.actions .el-button {
  flex: 1;
  height: 48px;
  border-radius: 12px;
}

.buy-btn {
  background: var(--brand-soft);
  border-color: #ffd9c7;
  color: var(--brand-deep);
}

.buy-btn:hover {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
}

.secondary-actions {
  display: flex;
  gap: 18px;
  margin-top: 16px;
}

.secondary-actions button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  transition: color 0.2s;
}

.secondary-actions button:hover,
.secondary-actions button.active {
  color: var(--brand);
}

.agent-note {
  margin-top: 22px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--ink);
  color: #f4efe6;
  border-radius: 14px;
  padding: 14px 16px;
  cursor: pointer;
  transition: transform 0.2s;
}

.agent-note:hover {
  transform: translateY(-2px);
}

.agent-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--brand);
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.agent-note strong {
  display: block;
  font-size: 12px;
}

.agent-note p {
  font-size: 10px;
  color: #b7afa2;
  margin-top: 3px;
  line-height: 1.6;
}

.agent-note .arrow {
  margin-left: auto;
  color: #ffb38f;
}

.promise-row {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed var(--line);
  font-size: 10px;
  color: var(--muted);
}

.promise-row span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.promise-row .el-icon {
  color: var(--green);
}

.detail-tabs {
  margin-top: 56px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 10px 26px 26px;
}

.tab-content {
  padding: 18px 0 6px;
}

.params {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
}

.param-table {
  border-top: 1px solid var(--line);
}

.param-row {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}

.param-row span {
  color: var(--muted);
}

.param-row strong {
  font-weight: 500;
  text-align: right;
}

.description h3 {
  font-size: 16px;
  margin-bottom: 10px;
}

.description p {
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--muted);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 16px;
}

.reviews-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
}

.reviews-head > div {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-score {
  font-size: 34px;
  font-weight: 700;
}

.review-list {
  display: flex;
  flex-direction: column;
}

.review-item {
  display: flex;
  gap: 12px;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
}

.review-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--bg-deep);
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.review-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  color: var(--muted);
}

.review-meta strong {
  color: var(--ink);
  font-size: 12px;
}

.review-meta :deep(.el-rate) {
  height: 14px;
}

.review-item p {
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--ink-soft);
  margin-top: 7px;
}

.agent-tab {
  display: grid;
  grid-template-columns: 1.3fr 0.7fr;
  gap: 30px;
}

.agent-block h3 {
  font-size: 24px;
  margin: 10px 0;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
}

.agent-block p,
.agent-compare p {
  font-size: 13px;
  line-height: 1.9;
  color: var(--muted);
  margin-bottom: 18px;
}

.agent-compare {
  background: var(--surface-sunken);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 20px;
}

.agent-compare h4 {
  font-size: 15px;
  margin-bottom: 8px;
}

.compare-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.related {
  margin-top: 60px;
}

.related-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}

.review-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-form label {
  font-size: 12px;
  color: var(--muted);
  margin-top: 8px;
}

@media (max-width: 960px) {
  .detail-grid {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .gallery {
    position: static;
  }

  .params,
  .agent-tab {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .related-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 560px) {
  .attr-preview {
    grid-template-columns: repeat(2, 1fr);
  }

  .actions {
    flex-direction: column;
  }

  .related-grid {
    grid-template-columns: 1fr;
  }
}
</style>
