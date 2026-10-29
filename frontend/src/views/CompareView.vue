<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { agentApi, productApi } from '@/api'
import type { CompareResult, Product } from '@/types'
import { money } from '@/utils/format'
import { useCompareStore } from '@/stores/compare'
import CompareRadar from '@/components/CompareRadar.vue'
import ProductImage from '@/components/ProductImage.vue'

const route = useRoute()
const router = useRouter()
const compareStore = useCompareStore()

const selected = ref<Product[]>([])
const result = ref<CompareResult | null>(null)
const computing = ref(false)
const pickerOpen = ref(false)
const pickerKeyword = ref('')
const pickerResults = ref<Product[]>([])
const pickerLoading = ref(false)
const weightDraft = reactive<Record<string, number>>({})
const hasCustomWeights = ref(false)

/**
 * 页内友好提示：替代会遮挡顶部的全局 toast。
 * 用于「跨品类无法对比」这类需要解释原因、可能反复出现的场景。
 */
interface CompareNotice {
  type: 'category' | 'error'
  title: string
  text: string
}
const notice = ref<CompareNotice | null>(null)

const products = computed(() => (result.value?.products?.length ? result.value.products : selected.value))
const canCompare = computed(() => selected.value.length >= 2)

onMounted(async () => {
  const idsParam = route.query.ids as string | undefined
  if (idsParam) {
    const ids = idsParam.split(',').filter(Boolean)
    compareStore.clear()
    ids.forEach((id) => compareStore.add(id))
  }
  await loadSelected()
})

watch(
  () => compareStore.ids,
  async () => {
    await loadSelected()
  },
  { deep: true }
)

watch(
  () => [selected.value.length, route.query.ids],
  () => {
    if (canCompare.value) {
      if (!result.value) runCompare()
    } else {
      result.value = null
    }
  }
)

async function loadSelected() {
  if (!compareStore.ids.length) {
    selected.value = []
    result.value = null
    return
  }
  try {
    selected.value = await productApi.batch(compareStore.ids)
    if (selected.value.length < 2) {
      result.value = null
    } else if (result.value && result.value.products.length !== selected.value.length) {
      await runCompare()
    }
  } catch {
    selected.value = []
  }
}

async function runCompare(custom = false) {
  if (!canCompare.value) return
  computing.value = true
  notice.value = null
  try {
    const weights: Record<string, number> = {}
    if (custom) {
      for (const dim of result.value?.dimensions || []) {
        weights[dim.key] = weightDraft[dim.key] ?? dim.weight
      }
    }
    result.value = await agentApi.compare({
      productIds: selected.value.map((product) => product.id),
      weights: custom ? weights : undefined
    })
    for (const dim of result.value.dimensions) {
      weightDraft[dim.key] = dim.weight
    }
    hasCustomWeights.value = custom
    if (custom) ElMessage.success('已按新权重重新计算')
  } catch (e: any) {
    // 后端仍可能因为数据变化拒绝对比，这里用页内提示说明原因，
    // 并清空结果，避免界面一直停在「正在分析」
    const raw: string = e?.response?.data?.message || e?.message || ''
    result.value = null
    if (raw.includes('品类')) {
      // 跨品类是最常见的情况，换成有指向性的说法，并告诉用户下一步怎么做
      notice.value = {
        type: 'category',
        title: '这几件商品没法直接比',
        text: '它们的品类不同，参数维度对不上。换成同品类的商品，对比结果才有参考意义。'
      }
    } else {
      notice.value = {
        type: 'error',
        title: '对比没能完成',
        text: raw || '可能是网络波动，稍后再试一次。'
      }
    }
  } finally {
    computing.value = false
  }
}

function removeProduct(id: string) {
  compareStore.remove(id)
  // 移出商品后约束可能已解除，清掉提示避免残留误导
  notice.value = null
  ElMessage.info('已移出对比清单')
}

async function openPicker() {
  pickerOpen.value = true
  if (!pickerResults.value.length) await searchPicker()
}

async function searchPicker() {
  pickerLoading.value = true
  try {
    const params = {
      page: 1,
      size: 8,
      keyword: pickerKeyword.value || undefined,
      categoryId: selected.value[0]?.categoryId || undefined,
      sort: 'sales'
    }
    const data = pickerKeyword.value.trim()
      ? await productApi.search(params)
      : await productApi.list(params)
    pickerResults.value = data.records.filter((product) => !compareStore.has(product.id))
  } finally {
    pickerLoading.value = false
  }
}

function addProduct(product: Product) {
  if (compareStore.has(product.id)) return
  // 品类校验前置到选择阶段：跨品类对比没有可比性，与其等提交后再报错，
  // 不如在这里就拦下来并说明原因，体验更顺
  if (selected.value.length && product.categoryId !== selected.value[0].categoryId) {
    const current = selected.value[0].categoryName || '当前品类'
    notice.value = {
      type: 'category',
      title: '只能对比同一品类',
      text: `已选的是「${current}」，这件属于「${product.categoryName || '其他品类'}」。换个同品类的试试？`
    }
    return
  }
  if (!compareStore.add(product.id)) {
    ElMessage.warning(`最多同时对比 ${compareStore.max} 件商品`)
    return
  }
  pickerResults.value = pickerResults.value.filter((item) => item.id !== product.id)
  notice.value = null
  ElMessage.success('已加入对比')
}

function goProduct(id: string) {
  router.push(`/product/${id}`)
}

function scoreOf(productId: string) {
  return result.value?.scores.find((score) => score.productId === productId)
}

function isBest(dimensionKey: string, productId: string) {
  const dimension = result.value?.dimensions.find((item) => item.key === dimensionKey)
  return dimension?.bestProductId === productId
}

function dimensionDisplay(key: string, productId: string) {
  const dimension = result.value?.dimensions.find((item) => item.key === key)
  const value = dimension?.displayValues[productId]
  if (value === undefined || value === null) return '—'
  return `${value}${dimension?.unit || ''}`
}
</script>

<template>
  <div class="compare-page">
    <div class="page-head">
      <div class="container">
        <span class="eyebrow">COMPARE AGENT</span>
        <h1>商品对比</h1>
        <p class="desc">
          从参数出发，而不是感觉。Agent 会构建对比矩阵、按权重评分，并给出可解释的决策建议。
        </p>
      </div>
    </div>

    <div class="container compare-wrap">
      <section v-if="selected.length" class="selected-bar">
        <div class="selected-list">
          <div v-for="product in selected" :key="product.id" class="selected-item">
            <div class="selected-thumb" @click="goProduct(product.id)">
              <ProductImage :src="product.image" :fallback-src="product.imageFallback" :alt="product.name" ratio="1 / 1" />
            </div>
            <div class="selected-copy">
              <strong>{{ product.name }}</strong>
              <span>{{ money(product.price) }}</span>
            </div>
            <button class="selected-remove" aria-label="移出对比" @click="removeProduct(product.id)">
              <el-icon><Close /></el-icon>
            </button>
          </div>
          <button v-if="selected.length < 4" class="add-slot" @click="openPicker">
            <el-icon><Plus /></el-icon>
            <span>添加商品</span>
          </button>
        </div>
        <div class="selected-actions">
          <span class="count">{{ selected.length }} / {{ compareStore.max }}</span>
          <el-button v-if="selected.length" text @click="compareStore.clear()">清空</el-button>
          <el-button type="primary" round :loading="computing" :disabled="!canCompare" @click="runCompare(false)">
            重新对比
          </el-button>
        </div>
      </section>

      <!-- 页内提示：比全局 toast 更克制，且不会遮挡顶部导航 -->
      <transition name="notice-fade">
        <aside v-if="notice" class="compare-notice" :class="notice.type" role="status">
          <span class="notice-icon">
            <el-icon><InfoFilled /></el-icon>
          </span>
          <div class="notice-body">
            <strong>{{ notice.title }}</strong>
            <p>{{ notice.text }}</p>
          </div>
          <button class="notice-close" type="button" aria-label="关闭提示" @click="notice = null">
            <el-icon><Close /></el-icon>
          </button>
        </aside>
      </transition>

      <section v-if="!selected.length" class="compare-empty">
        <div class="empty-visual">
          <div class="empty-card a">A</div>
          <div class="empty-card b">B</div>
          <div class="empty-vs">VS</div>
        </div>
        <div class="empty-copy">
          <span class="eyebrow">START COMPARING</span>
          <h2 class="display">把犹豫，拆成参数。</h2>
          <p>
            选择 2-4 件同品类商品，Agent 会读取真实参数生成对比矩阵。
            你还可以调整价格、续航、防水等维度的权重，看看结论会不会改变。
          </p>
          <div class="empty-actions">
            <el-button type="primary" size="large" round @click="openPicker">选择商品</el-button>
            <el-button size="large" round @click="router.push('/products')">去商品列表</el-button>
          </div>
        </div>
      </section>

      <section v-else-if="selected.length === 1" class="one-more">
        <div class="one-copy">
          <h2>还需要一件商品</h2>
          <p>再选一件同类商品，才能生成结构化对比与加权评分。</p>
          <el-button type="primary" round @click="openPicker">继续添加</el-button>
        </div>
        <div class="one-product">
          <ProductImage :src="selected[0].image" :fallback-src="selected[0].imageFallback" :alt="selected[0].name" ratio="4 / 3" />
          <strong>{{ selected[0].name }}</strong>
        </div>
      </section>

      <template v-else>
        <section v-loading="computing" class="compare-hero">
          <div class="recommend-card">
            <span class="eyebrow">AGENT RECOMMENDATION</span>
            <div class="recommend-title">
              <h2>{{ result?.recommendedName || (computing ? '正在分析…' : '暂无法对比') }}</h2>
              <span v-if="result?.scores.length" class="recommend-score">
                {{ result.scores[0].totalScore }}<small>分</small>
              </span>
            </div>
            <p class="advice">{{ result?.advice }}</p>
            <div class="highlights">
              <span v-for="highlight in result?.highlights || []" :key="highlight" class="pill brand">{{ highlight }}</span>
              <span v-if="result?.degraded" class="pill">规则引擎生成</span>
            </div>
            <div class="recommend-actions">
              <el-button v-if="result" type="primary" round @click="goProduct(result.recommendedId)">查看推荐商品</el-button>
              <span class="disclaimer">建议基于商品库真实参数与加权评分</span>
            </div>
          </div>
          <CompareRadar :result="result" />
        </section>

        <section class="score-board">
          <div class="board-head">
            <h3>加权得分</h3>
            <span class="muted">总分 100 · 权重可在下方调整</span>
          </div>
          <div class="score-list">
            <div v-for="score in result?.scores || []" :key="score.productId" class="score-row">
              <span class="rank">{{ score.rank }}</span>
              <strong>{{ result?.products.find((p) => p.id === score.productId)?.name }}</strong>
              <div class="bar">
                <span :style="{ width: `${score.totalScore}%` }" :class="{ winner: score.rank === 1 }"></span>
              </div>
              <b>{{ score.totalScore }}</b>
            </div>
          </div>
        </section>

        <section class="matrix-section">
          <div class="matrix-head">
            <div>
              <h3>对比矩阵</h3>
              <p>橙色为该维度最优。参数为空时显示“—”，不参与评分。</p>
            </div>
            <span class="pill">{{ result?.dimensions.length || 0 }} 个维度</span>
          </div>
          <div class="matrix-scroll">
            <table class="matrix">
              <thead>
                <tr>
                  <th class="dim-col">维度</th>
                  <th v-for="product in result?.products || []" :key="product.id">
                    <button class="matrix-product" @click="goProduct(product.id)">
                      <ProductImage :src="product.image" :fallback-src="product.imageFallback" :alt="product.name" ratio="1 / 1" />
                      <strong>{{ product.name }}</strong>
                      <span>{{ money(product.price) }}</span>
                    </button>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="dimension in result?.dimensions || []" :key="dimension.key">
                  <td class="dim-col">
                    <strong>{{ dimension.key }}</strong>
                    <span v-if="dimension.unit">{{ dimension.unit }}</span>
                  </td>
                  <td
                    v-for="product in result?.products || []"
                    :key="product.id"
                    :class="{ best: isBest(dimension.key, product.id) }"
                  >
                    {{ dimensionDisplay(dimension.key, product.id) }}
                    <el-icon v-if="isBest(dimension.key, product.id)" class="best-icon"><Select /></el-icon>
                  </td>
                </tr>
                <tr class="score-row-table">
                  <td class="dim-col"><strong>综合得分</strong></td>
                  <td v-for="product in result?.products || []" :key="product.id">
                    <b>{{ scoreOf(product.id)?.totalScore }}</b>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="weights-section">
          <div class="weights-copy">
            <h3>调整你关心的权重</h3>
            <p>权重越高，该维度对综合得分的影响越大。修改后点击“按新权重重算”。</p>
            <div class="weights-actions">
              <el-button type="primary" round :loading="computing" @click="runCompare(true)">按新权重重算</el-button>
              <span v-if="hasCustomWeights" class="pill brand">已应用自定义权重</span>
            </div>
          </div>
          <div class="weight-list">
            <div v-for="dimension in result?.dimensions || []" :key="dimension.key" class="weight-row">
              <div class="weight-label">
                <strong>{{ dimension.key }}</strong>
                <span>{{ dimension.higherBetter ? '越高越好' : '越低越好' }}</span>
              </div>
              <el-slider v-model="weightDraft[dimension.key]" :min="0.1" :max="3" :step="0.1" />
              <b>{{ Number(weightDraft[dimension.key] ?? 1).toFixed(1) }}</b>
            </div>
          </div>
        </section>
      </template>
    </div>

    <el-drawer v-model="pickerOpen" title="选择对比商品" direction="rtl" size="520px">
      <div class="picker-search">
        <el-input v-model="pickerKeyword" placeholder="搜索商品名称或品类" clearable @keyup.enter="searchPicker">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button round @click="searchPicker">搜索</el-button>
      </div>
      <div v-loading="pickerLoading" class="picker-list">
        <div v-for="product in pickerResults" :key="product.id" class="picker-item">
          <div class="picker-thumb">
            <ProductImage :src="product.image" :fallback-src="product.imageFallback" :alt="product.name" ratio="1 / 1" />
          </div>
          <div class="picker-copy">
            <strong>{{ product.name }}</strong>
            <span>{{ product.categoryName }} · {{ money(product.price) }}</span>
          </div>
          <el-button size="small" round @click="addProduct(product)">加入</el-button>
        </div>
        <div v-if="!pickerLoading && !pickerResults.length" class="empty">
          <strong>没有更多可选商品</strong>
          <p>试试其他关键词，或先去商品列表收藏。</p>
        </div>
      </div>
      <div class="picker-tip">提示：为保证维度可比，选择首件商品后，候选列表只展示同品类商品。</div>
    </el-drawer>
  </div>
</template>

<style scoped>
/*
 * 页内提示条
 * 用于跨品类等「需要解释原因」的约束提示，替代会遮挡顶部的全局 toast。
 * 克制的左侧色条 + 浅底，与页面的编辑风格保持一致。
 */
.compare-notice {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--line);
  border-left: 3px solid var(--gold);
  background: var(--surface);
  box-shadow: var(--shadow-card);
}

.compare-notice.error {
  border-left-color: var(--brand);
}

.notice-icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: rgba(201, 162, 39, 0.14);
  color: var(--gold);
  flex-shrink: 0;
  margin-top: 1px;
}

.compare-notice.error .notice-icon {
  background: var(--brand-tint);
  color: var(--brand);
}

.notice-body {
  flex: 1;
  min-width: 0;
}

.notice-body strong {
  display: block;
  font-size: 13px;
  color: var(--ink);
  margin-bottom: 3px;
}

.notice-body p {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-soft);
}

.notice-close {
  border: none;
  background: none;
  color: var(--muted-light);
  cursor: pointer;
  padding: 2px;
  border-radius: 6px;
  transition: color 0.2s, background 0.2s;
  flex-shrink: 0;
}

.notice-close:hover {
  color: var(--ink);
  background: var(--surface-sunken);
}

.notice-fade-enter-active,
.notice-fade-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.notice-fade-enter-from,
.notice-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
.compare-page {
  padding-bottom: 50px;
}

.compare-wrap {
  padding-top: 28px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.selected-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 14px 16px;
}

.selected-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.selected-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--surface-sunken);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 5px 8px 5px 5px;
  max-width: 260px;
}

.selected-thumb {
  width: 38px;
  height: 38px;
  border-radius: 7px;
  overflow: hidden;
  flex-shrink: 0;
  cursor: pointer;
}

.selected-copy {
  min-width: 0;
}

.selected-copy strong {
  display: block;
  font-size: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-copy span {
  font-size: 9px;
  color: var(--muted);
}

.selected-remove {
  border: none;
  background: transparent;
  color: var(--muted-light);
  cursor: pointer;
  padding: 3px;
}

.selected-remove:hover {
  color: #d64545;
}

.add-slot {
  display: flex;
  align-items: center;
  gap: 5px;
  border: 1px dashed var(--line-strong);
  background: transparent;
  border-radius: 10px;
  padding: 12px 16px;
  font-size: 10px;
  color: var(--muted);
  cursor: pointer;
}

.add-slot:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.selected-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.count {
  font-size: 10px;
  color: var(--muted);
  background: var(--bg);
  border-radius: 99px;
  padding: 5px 9px;
}

.compare-empty {
  display: grid;
  grid-template-columns: 0.85fr 1.15fr;
  gap: 50px;
  align-items: center;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 20px;
  padding: 60px;
  min-height: 420px;
}

.empty-visual {
  position: relative;
  height: 270px;
}

.empty-card {
  position: absolute;
  width: 180px;
  height: 230px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-size: 44px;
  font-weight: 700;
  border: 1px solid var(--line);
}

.empty-card.a {
  left: 20px;
  top: 20px;
  background: var(--surface-sunken);
  color: var(--muted-light);
  transform: rotate(-7deg);
}

.empty-card.b {
  right: 30px;
  top: 0;
  background: var(--ink);
  color: var(--on-ink);
  transform: rotate(5deg);
}

.empty-vs {
  position: absolute;
  left: 50%;
  top: 105px;
  transform: translateX(-50%);
  background: var(--brand);
  color: #fff;
  border-radius: 50%;
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 10px 26px rgba(240, 90, 40, 0.3);
  z-index: 2;
}

.empty-copy h2 {
  font-size: clamp(30px, 3.6vw, 44px);
  margin: 14px 0 16px;
}

.empty-copy p {
  font-size: 13px;
  line-height: 1.9;
  color: var(--muted);
  max-width: 440px;
}

.empty-actions {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}

.one-more {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 30px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 40px;
}

.one-copy h2 {
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 28px;
  margin-bottom: 10px;
}

.one-copy p {
  color: var(--muted);
  font-size: 12px;
  margin-bottom: 18px;
}

.one-product {
  width: 190px;
  text-align: center;
}

.one-product :deep(.product-image) {
  border-radius: 12px;
  overflow: hidden;
}

.one-product strong {
  display: block;
  font-size: 10px;
  margin-top: 8px;
}

.compare-hero {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 18px;
  align-items: stretch;
}

.recommend-card {
  background: var(--panel-dark-bg);
  color: #f4efe6;
  border-radius: 18px;
  padding: 30px;
  display: flex;
  flex-direction: column;
}

.recommend-card .eyebrow {
  color: #ff9c72;
}

.recommend-card .eyebrow::before {
  background: var(--panel-dark-accent);
}

.recommend-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 14px 0 12px;
}

.recommend-title h2 {
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 26px;
  font-weight: 600;
  color: #fff;
}

.recommend-score {
  font-size: 34px;
  font-weight: 700;
  color: #ff9c72;
  white-space: nowrap;
}

.recommend-score small {
  font-size: 11px;
  font-weight: 500;
  margin-left: 2px;
}

.advice {
  font-size: 12.5px;
  line-height: 1.9;
  color: #b7afa2;
  flex: 1;
}

.highlights {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin: 16px 0;
}

.highlights .pill {
  background: var(--panel-dark-raised);
  border-color: #3a362f;
  color: #d8d0c3;
}

.highlights .pill.brand {
  background: rgba(240, 90, 40, 0.16);
  border-color: rgba(240, 90, 40, 0.3);
  color: #ffb38f;
}

.recommend-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.disclaimer {
  font-size: 9px;
  color: #7d766a;
}

.score-board {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 22px;
}

.board-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
}

.board-head h3 {
  font-size: 15px;
}

.board-head .muted {
  font-size: 10px;
}

.score-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.score-row {
  display: grid;
  grid-template-columns: 22px minmax(140px, 240px) 1fr 50px;
  align-items: center;
  gap: 12px;
}

.rank {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--bg);
  display: grid;
  place-items: center;
  font-size: 10px;
  color: var(--muted);
}

.score-row:first-child .rank {
  background: var(--ink);
  color: var(--on-ink);
}

.score-row strong {
  font-size: 11px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar {
  height: 7px;
  background: var(--bg);
  border-radius: 99px;
  overflow: hidden;
}

.bar span {
  display: block;
  height: 100%;
  background: var(--line-strong);
  border-radius: 99px;
  transition: width 0.5s ease;
}

.bar span.winner {
  background: linear-gradient(90deg, #ff9c72, var(--brand));
}

.score-row b {
  text-align: right;
  font-size: 13px;
}

.matrix-section {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 22px;
}

.matrix-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.matrix-head h3 {
  font-size: 15px;
}

.matrix-head p {
  font-size: 10px;
  color: var(--muted);
  margin-top: 4px;
}

.matrix-scroll {
  overflow-x: auto;
}

.matrix {
  width: 100%;
  border-collapse: collapse;
  min-width: 640px;
}

.matrix th,
.matrix td {
  border-bottom: 1px solid var(--line);
  padding: 12px 14px;
  text-align: center;
  font-size: 12px;
  color: var(--ink-soft);
}

.matrix thead th {
  vertical-align: bottom;
  font-weight: 500;
  background: var(--surface-sunken);
  border-top: 1px solid var(--line);
}

.matrix th:first-child {
  border-left: 1px solid var(--line);
  border-top-left-radius: 10px;
}

.matrix th:last-child {
  border-right: 1px solid var(--line);
  border-top-right-radius: 10px;
}

.dim-col {
  text-align: left !important;
  width: 150px;
}

.dim-col strong {
  display: block;
  font-size: 11px;
}

.dim-col span {
  font-size: 9px;
  color: var(--muted);
}

.matrix-product {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  cursor: pointer;
  margin: 0 auto;
}

.matrix-product :deep(.product-image) {
  width: 64px;
  border-radius: 8px;
  overflow: hidden;
}

.matrix-product strong {
  font-size: 10px;
  font-weight: 500;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.matrix-product span {
  font-size: 9px;
  color: var(--muted);
}

.matrix td.best {
  color: var(--brand-deep);
  font-weight: 600;
  background: var(--brand-tint-strong);
}

.best-icon {
  font-size: 10px;
  margin-left: 3px;
}

.score-row-table td {
  border-bottom: none;
  background: var(--surface-sunken);
  font-size: 14px;
}

.score-row-table b {
  color: var(--brand-deep);
}

.weights-section {
  display: grid;
  grid-template-columns: 0.8fr 1.2fr;
  gap: 30px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 24px;
}

.weights-copy h3 {
  font-size: 16px;
  margin-bottom: 8px;
}

.weights-copy p {
  font-size: 11px;
  line-height: 1.8;
  color: var(--muted);
  max-width: 330px;
}

.weights-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.weight-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.weight-row {
  display: grid;
  grid-template-columns: 110px 1fr 36px;
  align-items: center;
  gap: 14px;
}

.weight-label strong {
  display: block;
  font-size: 11px;
}

.weight-label span {
  font-size: 9px;
  color: var(--muted);
}

.weight-row b {
  font-size: 11px;
  text-align: right;
}

.picker-search {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.picker-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 200px;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px;
}

.picker-thumb {
  width: 46px;
  height: 46px;
  border-radius: 7px;
  overflow: hidden;
  flex-shrink: 0;
}

.picker-copy {
  flex: 1;
  min-width: 0;
}

.picker-copy strong {
  display: block;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-copy span {
  font-size: 9px;
  color: var(--muted);
}

.picker-tip {
  margin-top: 16px;
  padding: 11px;
  background: var(--surface-sunken);
  border-radius: 8px;
  font-size: 9.5px;
  color: var(--muted);
  line-height: 1.7;
}

@media (max-width: 1000px) {
  .compare-empty,
  .compare-hero,
  .weights-section {
    grid-template-columns: 1fr;
  }

  .compare-empty {
    padding: 36px;
  }

  .empty-visual {
    display: none;
  }
}

@media (max-width: 700px) {
  .selected-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .selected-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .score-row {
    grid-template-columns: 20px 1fr 44px;
  }

  .score-row .bar {
    display: none;
  }

  .one-more {
    flex-direction: column;
    align-items: flex-start;
    padding: 26px;
  }

  .weight-row {
    grid-template-columns: 90px 1fr 30px;
  }
}
</style>
