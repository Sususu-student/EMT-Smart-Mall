<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { productApi } from '@/api'
import type { Category, Product } from '@/types'
import ProductCard from '@/components/ProductCard.vue'
import ProductImage from '@/components/ProductImage.vue'
import CategoryIcon from '@/components/CategoryIcon.vue'
import { money } from '@/utils/format'

const router = useRouter()
const featured = ref<Product[]>([])
const latest = ref<Product[]>([])
const categories = ref<Category[]>([])
const loading = ref(true)
const prompt = ref('')

const heroProduct = computed(() => featured.value[0])
const heroReason = computed(() => {
  const product = heroProduct.value
  if (!product) return '「真实参数、真实价格、可解释推荐。」'
  const attrs = product.attributes || {}
  const waterproof = attrs['防水等级'] || attrs['防水'] || ''
  const battery = attrs['续航'] || attrs['电池'] || ''
  const weight = attrs['重量'] || ''
  const parts = [
    waterproof ? `${waterproof} 防水` : '',
    battery ? `${battery}h 续航` : '',
    weight ? `${weight}g 机身` : ''
  ].filter(Boolean)
  return `「${parts.join('、')}，${product.categoryName || '这款商品'}的参数都能对上真实需求。」`
})
const promptChips = [
  '跑步用蓝牙耳机，300 以内',
  '500 元左右的咖啡机',
  '通勤双肩包，要防水',
  '给敏感肌挑一套护肤品'
]

onMounted(async () => {
  try {
    const [hot, fresh, cats] = await Promise.all([
      productApi.list({ page: 1, size: 8, sort: 'sales' }),
      productApi.list({ page: 1, size: 4, sort: 'new' }),
      productApi.categories()
    ])
    featured.value = hot.records
    latest.value = fresh.records
    categories.value = cats
  } finally {
    loading.value = false
  }
})

function ask(text?: string) {
  const message = (text ?? prompt.value).trim()
  router.push({ name: 'chat', query: message ? { q: message } : {} })
}
</script>

<template>
  <div class="home">
    <section class="hero">
      <div class="container hero-grid">
        <div class="hero-copy">
          <span class="eyebrow">AI SHOPPING CONCIERGE</span>
          <h1 class="display">把挑选，<br />交给懂你的<span class="accent">导购</span>。</h1>
          <p class="lede">
            不用在一百件商品里反复比较。告诉智选你的预算、场景和偏好，
            它会追问、检索、解释推荐理由，还能把候选商品放进同一张对比表。
          </p>

          <form class="ask-box" @submit.prevent="ask()">
            <el-icon class="ask-icon"><ChatDotRound /></el-icon>
            <input
              v-model="prompt"
              type="text"
              placeholder="例如：想买跑步用的蓝牙耳机，预算 300 以内"
              aria-label="描述购物需求"
            />
            <button type="submit" class="ask-btn">
              开始对话
              <el-icon><ArrowRight /></el-icon>
            </button>
          </form>

          <div class="chips">
            <button v-for="chip in promptChips" :key="chip" class="chip" @click="ask(chip)">{{ chip }}</button>
          </div>

          <div class="hero-stats">
            <div><strong>130+</strong><span>真实参数商品</span></div>
            <div><strong>13</strong><span>细分品类</span></div>
            <div><strong>2</strong><span>协同 Agent</span></div>
            <div><strong>0</strong><span>虚构字段</span></div>
          </div>
        </div>

        <div class="hero-visual">
          <div class="visual-glow"></div>
          <div class="main-card">
            <div class="card-label">EDITOR'S PICK · 本周精选</div>
            <ProductImage :src="heroProduct?.image || '/img/headphone.svg'" :fallback-src="heroProduct?.imageFallback" :alt="heroProduct?.name || '精选商品'" ratio="4 / 5" />
            <div class="main-card-info">
              <div>
                <strong>{{ heroProduct?.name || '声屿 Air 3 运动版' }}</strong>
                <span>{{ heroProduct?.subtitle || '主动降噪 · 24h 续航 · IPX5 防水' }}</span>
              </div>
              <b>{{ money(heroProduct?.price ?? 279) }}</b>
            </div>
          </div>

          <div class="float-card reason-card">
            <span class="float-label">AGENT 推荐理由</span>
            <p>{{ heroReason }}</p>
            <span class="float-foot">已校验：价格 / 库存 / 参数</span>
          </div>

          <div class="float-card score-card">
            <div class="score-ring">92<span>.4</span></div>
            <div>
              <strong>对比综合分</strong>
              <span>防水 · 续航 · 价格 三维加权</span>
            </div>
          </div>

          <div class="vertical-note">SMART GUIDE MALL · EST. 2026</div>
        </div>
      </div>
    </section>

    <section class="brand-strip">
      <div class="container brand-strip-inner">
        <span>声屿</span><i></i><span>澎湃</span><i></i><span>步履</span><i></i><span>凝光</span><i></i><span>岩咖</span><i></i><span>栖居</span>
      </div>
    </section>

    <section class="section category-section">
      <div class="container">
        <div class="section-head">
          <div>
            <span class="eyebrow">SHOP BY CATEGORY</span>
            <h2>从品类开始逛</h2>
            <p>每个品类都有结构化参数，Agent 才能做真正的逐项对比。</p>
          </div>
          <router-link to="/products" class="link-more">查看全部商品 <el-icon><ArrowRight /></el-icon></router-link>
        </div>

        <div v-loading="loading" class="category-grid">
          <router-link
            v-for="category in categories"
            :key="category.id"
            :to="{ name: 'products', query: { categoryId: category.id } }"
            class="category-card"
          >
            <span class="cat-icon"><CategoryIcon :category-id="category.id" :name="category.name" :size="22" /></span>
            <div>
              <strong>{{ category.name }}</strong>
              <span>{{ category.children?.map((c) => c.name).join(' · ') || category.description }}</span>
            </div>
            <el-icon class="cat-arrow"><ArrowRight /></el-icon>
          </router-link>
        </div>
      </div>
    </section>

    <section class="section feature-section">
      <div class="container">
        <div class="feature-grid">
          <div class="feature-copy">
            <span class="eyebrow">WHY SMART GUIDE</span>
            <h2 class="display">不是搜索框，<br />是会追问的导购。</h2>
            <p>
              传统电商要求你先想好关键词。智选会先理解场景：跑步还是通勤？预算多少？
              更在意续航还是防水？信息不完整时，它会主动澄清，而不是丢给你一屏结果。
            </p>
            <button class="dark-btn" @click="ask('想买跑步耳机，预算300以内')">
              试着描述一个需求
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
          <div class="feature-list">
            <article class="feature-item">
              <span class="feature-no">01</span>
              <div>
                <h3>多轮澄清，理解模糊需求</h3>
                <p>槽位缺失时主动追问预算、场景和功能偏好，并把上下文带进下一轮。</p>
              </div>
            </article>
            <article class="feature-item">
              <span class="feature-no">02</span>
              <div>
                <h3>推荐理由只引用真实字段</h3>
                <p>价格、库存、参数全部来自数据库；输出前逐项校验，不一致就拦截重写。</p>
              </div>
            </article>
            <article class="feature-item">
              <span class="feature-no">03</span>
              <div>
                <h3>执行过程实时可见</h3>
                <p>意图识别、检索、推荐、校验每一步都记录，并通过 WebSocket 实时推送。</p>
              </div>
            </article>
            <article class="feature-item">
              <span class="feature-no">04</span>
              <div>
                <h3>对比决策可调权重</h3>
                <p>结构化对比矩阵 + 加权评分，价格、续航、防水谁更重要由你决定。</p>
              </div>
            </article>
          </div>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="container">
        <div class="section-head">
          <div>
            <span class="eyebrow">MOST WANTED</span>
            <h2>本周热卖</h2>
            <p>销量与口碑共同排序，点击卡片查看真实参数。</p>
          </div>
          <router-link :to="{ name: 'products', query: { sort: 'sales' } }" class="link-more">
            完整榜单 <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
        <div v-loading="loading" class="product-grid">
          <ProductCard v-for="product in featured" :key="product.id" :product="product" />
        </div>
      </div>
    </section>

    <section class="section-tight">
      <div class="container">
        <div class="compare-banner">
          <div class="banner-copy">
            <span class="eyebrow">COMPARE AGENT</span>
            <h2 class="display">两件都不错？<br />让数据替你决定。</h2>
            <p>把候选商品放进对比清单，Agent 会构建多维度矩阵、按权重评分，并给出可解释的决策建议。</p>
            <div class="banner-actions">
              <router-link to="/compare" class="light-btn">打开对比中心 <el-icon><ArrowRight /></el-icon></router-link>
              <button class="ghost-light" @click="ask('帮我对比前两个')">用对话直接对比</button>
            </div>
          </div>
          <div class="banner-visual">
            <div class="mini-matrix">
              <div class="matrix-head"><span></span><b>A</b><b>B</b></div>
              <div class="matrix-row"><span>价格</span><b class="best">¥279</b><b>¥299</b></div>
              <div class="matrix-row"><span>续航</span><b>24h</b><b class="best">30h</b></div>
              <div class="matrix-row"><span>防水</span><b class="best">IPX5</b><b>IPX4</b></div>
              <div class="matrix-row"><span>综合</span><b class="best">92.4</b><b>88.1</b></div>
              <div class="matrix-foot">建议选 A · 防水与价格更均衡</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="container">
        <div class="section-head">
          <div>
            <span class="eyebrow">JUST IN</span>
            <h2>新品上架</h2>
            <p>刚进入商品库的新面孔，参数同样完整。</p>
          </div>
          <router-link :to="{ name: 'products', query: { sort: 'new' } }" class="link-more">
            更多新品 <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
        <div class="product-grid">
          <ProductCard v-for="product in latest" :key="product.id" :product="product" />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  overflow: hidden;
}

.hero {
  position: relative;
  padding: 76px 0 64px;
  background: var(--hero-bg);
}

.hero-grid {
  display: grid;
  grid-template-columns: 1.02fr 0.98fr;
  gap: 60px;
  align-items: center;
}

.hero-copy h1 {
  font-family: var(--font-digit), var(--font-serif);
  font-weight: 700;
  font-size: clamp(44px, 5.4vw, 68px);
  margin: 18px 0 22px;
}

.accent {
  color: var(--brand);
  position: relative;
  white-space: nowrap;
}

.accent::after {
  content: '';
  position: absolute;
  left: 2px;
  right: 2px;
  bottom: 2px;
  height: 9px;
  background: var(--glow-accent);
  z-index: -1;
}

.lede {
  max-width: 520px;
  font-size: 15px;
  line-height: 1.9;
  color: var(--muted);
}

.ask-box {
  margin-top: 30px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: 16px;
  padding: 8px 8px 8px 16px;
  max-width: 570px;
  box-shadow: 0 16px 40px rgba(47, 40, 27, 0.08);
}

.ask-icon {
  color: var(--brand);
  font-size: 18px;
  flex-shrink: 0;
}

.ask-box input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  font-size: 13px;
  background: transparent;
  color: var(--ink);
  padding: 10px 0;
}

.ask-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: var(--ink);
  color: var(--on-ink);
  border-radius: 11px;
  padding: 13px 18px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.2s;
}

.ask-btn:hover {
  background: var(--brand);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
  max-width: 570px;
}

.chip {
  border: 1px solid var(--line);
  background: var(--chip-bg);
  color: var(--ink-soft);
  border-radius: 99px;
  padding: 7px 12px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.chip:hover {
  border-color: var(--brand);
  color: var(--brand-deep);
  background: var(--surface);
}

.hero-stats {
  display: flex;
  gap: 34px;
  margin-top: 42px;
  padding-top: 24px;
  border-top: 1px solid var(--line);
}

.hero-stats div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.hero-stats strong {
  font-family: var(--font-digit);
  font-variant-numeric: tabular-nums;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.hero-stats span {
  font-size: 10px;
  color: var(--muted);
  letter-spacing: 0.05em;
}

.hero-visual {
  position: relative;
  min-height: 560px;
  display: grid;
  place-items: center;
}

.visual-glow {
  position: absolute;
  width: 440px;
  height: 440px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--glow-accent), transparent 65%);
  filter: blur(10px);
}

.main-card {
  position: relative;
  width: 360px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 20px;
  padding: 14px;
  box-shadow: var(--shadow-card-hover);
  transform: rotate(-2.5deg);
  z-index: 2;
}

.card-label {
  font-size: 8px;
  letter-spacing: 0.2em;
  color: var(--muted);
  padding: 4px 2px 12px;
}

.main-card :deep(.product-image) {
  border-radius: 12px;
}

.main-card-info {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  padding: 14px 4px 4px;
}

.main-card-info strong {
  display: block;
  font-size: 14px;
  line-height: 1.4;
}

.main-card-info span {
  display: block;
  font-size: 10px;
  color: var(--muted);
  margin-top: 4px;
}

.main-card-info b {
  font-size: 22px;
  font-weight: 700;
  white-space: nowrap;
}

.float-card {
  position: absolute;
  background: var(--surface-glass);
  backdrop-filter: blur(10px);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 14px;
  box-shadow: var(--shadow-float);
  z-index: 3;
}

.reason-card {
  width: 250px;
  top: 40px;
  left: -24px;
}

.float-label {
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: var(--brand);
}

.reason-card p {
  font-size: 11px;
  line-height: 1.75;
  color: var(--ink-soft);
  margin: 8px 0 10px;
}

.float-foot {
  font-size: 9px;
  color: var(--green);
}

.score-card {
  bottom: 46px;
  right: -22px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.score-ring {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  border: 2px solid var(--brand);
  display: grid;
  place-items: center;
  font-size: 18px;
  font-weight: 700;
  color: var(--brand-deep);
}

.score-ring span {
  font-size: 10px;
}

.score-card strong {
  display: block;
  font-size: 12px;
}

.score-card span {
  display: block;
  font-size: 9px;
  color: var(--muted);
  margin-top: 4px;
}

.vertical-note {
  position: absolute;
  right: -34px;
  top: 50%;
  writing-mode: vertical-rl;
  font-size: 8px;
  letter-spacing: 0.28em;
  color: var(--muted-light);
}

.brand-strip {
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: var(--section-alt-bg);
}

.brand-strip-inner {
  height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  color: var(--panel-dark-muted);
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 15px;
  letter-spacing: 0.12em;
}

.brand-strip-inner i {
  width: 3px;
  height: 3px;
  background: var(--panel-dark-line);
  border-radius: 50%;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  min-height: 120px;
}

.category-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 18px 16px;
  box-shadow: var(--shadow-card);
  transition: transform 0.26s cubic-bezier(0.22, 0.61, 0.36, 1), box-shadow 0.26s ease, border-color 0.26s ease;
}

.category-card:hover {
  border-color: var(--line-strong);
  transform: translateY(-3px);
  box-shadow: var(--shadow-float);
}

.category-card:hover .cat-icon,
.category-card:hover .cat-arrow {
  color: var(--brand);
}

.cat-icon {
  color: var(--ink-soft);
  width: 46px;
  height: 46px;
  background: var(--bg);
  border-radius: 12px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  transition: color 0.24s ease, background 0.24s ease;
}

.category-card:hover .cat-icon {
  background: var(--brand-soft);
}

.cat-arrow {
  margin-left: auto;
  color: var(--muted-light);
  font-size: 13px;
  transition: color 0.24s ease, transform 0.24s ease;
}

.category-card:hover .cat-arrow {
  transform: translateX(3px);
}

.category-card strong {
  display: block;
  font-size: 14px;
  margin-bottom: 3px;
}

.category-card div span {
  font-size: 10px;
  color: var(--muted);
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 150px;
}

.feature-section {
  background: var(--section-alt-bg);
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.feature-grid {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  gap: 70px;
  align-items: start;
}

.feature-copy h2 {
  font-size: clamp(34px, 4vw, 50px);
  margin: 16px 0 20px;
}

.feature-copy p {
  color: var(--muted);
  font-size: 14px;
  line-height: 1.9;
  max-width: 400px;
}

.dark-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 26px;
  border: none;
  background: var(--ink);
  color: var(--on-ink);
  border-radius: 99px;
  padding: 14px 22px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.dark-btn:hover {
  background: var(--brand);
}

.feature-item {
  display: flex;
  gap: 22px;
  padding: 22px 0;
  border-bottom: 1px solid var(--line);
}

.feature-item:first-child {
  padding-top: 0;
}

.feature-item:last-child {
  border-bottom: none;
}

.feature-no {
  font-size: 11px;
  color: var(--brand);
  font-weight: 700;
  padding-top: 4px;
  letter-spacing: 0.08em;
}

.feature-item h3 {
  font-size: 16px;
  margin-bottom: 7px;
}

.feature-item p {
  font-size: 12px;
  line-height: 1.8;
  color: var(--muted);
  max-width: 440px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}

.compare-banner {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 40px;
  align-items: center;
  background: var(--panel-dark-bg);
  color: var(--panel-dark-ink);
  border-radius: 24px;
  padding: 54px;
  position: relative;
  overflow: hidden;
}

.compare-banner::before {
  content: '';
  position: absolute;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(240, 90, 40, 0.25), transparent 65%);
  right: -80px;
  top: -120px;
}

.banner-copy {
  position: relative;
  z-index: 1;
}

.banner-copy .eyebrow {
  color: var(--panel-dark-accent);
}

.banner-copy .eyebrow::before {
  background: var(--panel-dark-accent);
}

.banner-copy h2 {
  font-size: clamp(32px, 4vw, 46px);
  margin: 16px 0 18px;
  color: var(--panel-dark-ink);
}

.banner-copy p {
  color: var(--panel-dark-muted);
  font-size: 14px;
  line-height: 1.9;
  max-width: 440px;
}

.banner-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 26px;
  flex-wrap: wrap;
}

.light-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--surface);
  color: var(--ink);
  border-radius: 99px;
  padding: 13px 20px;
  font-size: 13px;
  font-weight: 600;
  transition: background 0.2s;
}

.light-btn:hover {
  background: var(--brand);
  color: #ffffff;
}

.ghost-light {
  border: none;
  background: transparent;
  color: var(--panel-dark-muted);
  font-size: 12px;
  border-bottom: 1px solid var(--panel-dark-line);
  padding: 2px 0;
  cursor: pointer;
}

.banner-visual {
  position: relative;
  z-index: 1;
}

.mini-matrix {
  background: var(--panel-dark-sunken);
  border: 1px solid var(--panel-dark-line);
  border-radius: 16px;
  padding: 20px;
  font-size: 12px;
}

.matrix-head,
.matrix-row {
  display: grid;
  grid-template-columns: 1fr 0.8fr 0.8fr;
  padding: 10px 6px;
  border-bottom: 1px solid var(--panel-dark-line);
  text-align: right;
}

.matrix-head {
  color: var(--panel-dark-muted);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.matrix-head b:first-child,
.matrix-row b:first-child {
  text-align: right;
}

.matrix-row span {
  text-align: left;
  color: var(--panel-dark-muted);
}

.matrix-row b {
  font-weight: 600;
  color: var(--panel-dark-ink);
}

.matrix-row b.best {
  color: var(--panel-dark-accent);
}

.matrix-foot {
  padding: 14px 6px 2px;
  font-size: 10px;
  color: var(--panel-dark-muted);
}

@media (max-width: 1080px) {
  .hero-grid {
    gap: 30px;
  }

  .main-card {
    width: 310px;
  }

  .reason-card {
    left: -10px;
    width: 220px;
  }

  .score-card {
    right: -6px;
  }

  .category-grid,
  .product-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .hero {
    padding: 50px 0 40px;
  }

  .hero-grid,
  .feature-grid,
  .compare-banner {
    grid-template-columns: 1fr;
  }

  .hero-visual {
    min-height: 480px;
    margin-top: 10px;
  }

  .vertical-note {
    display: none;
  }

  .category-grid,
  .product-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .brand-strip-inner {
    font-size: 12px;
  }

  .compare-banner {
    padding: 36px 24px;
  }
}

@media (max-width: 560px) {
  .hero-copy h1 {
    font-size: 44px;
  }

  .hero-stats {
    gap: 20px;
    flex-wrap: wrap;
  }

  .ask-box {
    flex-wrap: wrap;
    padding: 10px;
  }

  .ask-btn {
    width: 100%;
    justify-content: center;
  }

  .main-card {
    width: 280px;
  }

  .reason-card {
    left: 0;
    top: 10px;
    transform: scale(0.9);
    transform-origin: left top;
  }

  .score-card {
    right: 0;
    bottom: 10px;
    transform: scale(0.9);
    transform-origin: right bottom;
  }

  .category-grid,
  .product-grid {
    grid-template-columns: 1fr;
  }

  .brand-strip-inner span:nth-of-type(n + 5) {
    display: none;
  }
}
</style>
