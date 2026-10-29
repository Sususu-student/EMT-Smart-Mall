<script setup lang="ts">
/**
 * 卖家经营看板：KPI 卡片 + 销售趋势双轴图 + 商品四象限散点 + 评分分布 + 履约漏斗。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { InfoFilled } from '@element-plus/icons-vue'
import { analyticsApi } from '@/api'
import type { SellerAnalytics } from '@/types'

const loading = ref(true)
const days = ref(30)
const data = ref<SellerAnalytics | null>(null)

const trendEl = ref<HTMLDivElement | null>(null)
const scatterEl = ref<HTMLDivElement | null>(null)
let trendChart: echarts.ECharts | null = null
let scatterChart: echarts.ECharts | null = null

const kpi = computed(() => data.value?.kpi)
const money = (v: number) => '¥' + Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
const hasTrend = computed(() => (data.value?.trend ?? []).some((t) => Number(t.sales) > 0))
const hasProducts = computed(() => (data.value?.products?.length ?? 0) > 0)
const hasRatings = computed(() => (data.value?.ratings ?? []).some((r) => r.count > 0))
const hasFunnel = computed(() => (data.value?.funnel?.[0]?.value ?? 0) > 0)

/** 评分分布用纯 DOM 画横向条，比图表更轻也更直白 */
const ratingTotal = computed(() => (data.value?.ratings ?? []).reduce((s, r) => s + r.count, 0))
const ratingPct = (count: number) => (ratingTotal.value === 0 ? 0 : Math.round((count * 100) / ratingTotal.value))

function renderTrend() {
  if (!trendEl.value || !data.value) return
  trendChart = trendChart || echarts.init(trendEl.value)
  const t = data.value.trend
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (ps: any[]) => {
        const d = ps[0]?.axisValue ?? ''
        const sales = ps.find((p) => p.seriesName === '销售额')?.data ?? 0
        const avg = ps.find((p) => p.seriesName === '客单价')?.data ?? 0
        return `${d}<br/>销售额 ${money(sales)}<br/>客单价 ${money(avg)}`
      }
    },
    legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 8, textStyle: { fontSize: 11, color: '#8a8378' } },
    grid: { left: 8, right: 8, top: 34, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      data: t.map((p) => p.date.slice(5)),
      axisLine: { lineStyle: { color: 'rgba(140,130,115,.25)' } },
      axisLabel: { fontSize: 10, color: '#9a9388' },
      axisTick: { show: false }
    },
    yAxis: [
      {
        type: 'value',
        name: '销售额',
        nameTextStyle: { fontSize: 10, color: '#9a9388' },
        axisLabel: { fontSize: 10, color: '#9a9388' },
        splitLine: { lineStyle: { color: 'rgba(140,130,115,.12)' } }
      },
      {
        type: 'value',
        name: '客单价',
        nameTextStyle: { fontSize: 10, color: '#9a9388' },
        axisLabel: { fontSize: 10, color: '#9a9388' },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '销售额',
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#f0774a' },
            { offset: 1, color: '#fbb59b' }
          ])
        },
        data: t.map((p) => Number(p.sales))
      },
      {
        name: '客单价',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 5,
        lineStyle: { width: 2, color: '#2f7a5c' },
        itemStyle: { color: '#2f7a5c' },
        data: t.map((p) => Number(p.avgOrder))
      }
    ]
  }, true)
}

function renderScatter() {
  if (!scatterEl.value || !data.value) return
  scatterChart = scatterChart || echarts.init(scatterEl.value)
  const pts = data.value.products
  const maxViews = Math.max(...pts.map((p) => p.views), 1)
  const maxSales = Math.max(...pts.map((p) => p.sales), 1)

  scatterChart.setOption({
    tooltip: {
      formatter: (o: any) => {
        const p = pts[o.dataIndex]
        return `${p.name}<br/>浏览 ${p.views} · 销量 ${p.sales}<br/>库存 ${p.stock} · 评分 ${p.rating}`
      }
    },
    grid: { left: 10, right: 20, top: 24, bottom: 10, containLabel: true },
    xAxis: {
      name: '浏览量',
      nameLocation: 'end',
      nameTextStyle: { fontSize: 10, color: '#9a9388' },
      axisLabel: { fontSize: 10, color: '#9a9388' },
      splitLine: { lineStyle: { color: 'rgba(140,130,115,.12)' } }
    },
    yAxis: {
      name: '销量',
      nameTextStyle: { fontSize: 10, color: '#9a9388' },
      axisLabel: { fontSize: 10, color: '#9a9388' },
      splitLine: { lineStyle: { color: 'rgba(140,130,115,.12)' } }
    },
    series: [
      {
        type: 'scatter',
        symbolSize: (val: number[]) => 12 + Math.min(24, (pts.find((p) => p.views === val[0])?.stock ?? 0) / 20),
        itemStyle: {
          color: (o: any) => {
            const r = Number(pts[o.dataIndex]?.rating ?? 0)
            if (r >= 4.6) return '#2f7a5c'
            if (r >= 4.2) return '#f05a28'
            return '#c9a227'
          },
          opacity: 0.78
        },
        data: pts.map((p) => [p.views, p.sales]),
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { type: 'dashed', color: 'rgba(140,130,115,.3)' },
          // 关闭中线自带的数值标签，否则会与坐标轴刻度混在一起造成误读
          label: { show: false },
          data: [{ xAxis: maxViews / 2 }, { yAxis: maxSales / 2 }]
        }
      }
    ]
  }, true)
}

async function load() {
  loading.value = true
  try {
    data.value = await analyticsApi.seller(days.value)
  } catch {
    data.value = null
  } finally {
    loading.value = false
    await nextTick()
    renderTrend()
    renderScatter()
  }
}

function switchDays(d: number) {
  if (days.value === d) return
  days.value = d
  load()
}

function onResize() {
  trendChart?.resize()
  scatterChart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  scatterChart?.dispose()
})

const keywordStyle = (weight: number) => {
  const size = Math.min(20, 11 + weight * 1.6)
  return { fontSize: `${size}px` }
}
</script>

<template>
  <div v-loading="loading" class="seller-analytics">
    <!-- 数据来源说明：本项目交易数据由脚本模拟生成，用于展示分析能力 -->
    <div class="source-note">
      <el-icon><InfoFilled /></el-icon>
      <span>统计基于演示数据（近 90 天模拟订单），指标口径与真实经营一致</span>
    </div>

    <!-- KPI -->
    <div class="kpi-grid">
      <article class="kpi-card primary">
        <span>累计销售额</span>
        <strong>{{ money(kpi?.totalSales ?? 0) }}</strong>
      </article>
      <article class="kpi-card">
        <span>订单数</span>
        <strong>{{ kpi?.orderCount ?? 0 }}</strong>
      </article>
      <article class="kpi-card" :class="{ warn: (kpi?.pendingShipCount ?? 0) > 0 }">
        <span>待发货</span>
        <strong>{{ kpi?.pendingShipCount ?? 0 }}</strong>
      </article>
      <article class="kpi-card">
        <span>商品浏览量</span>
        <strong>{{ kpi?.viewCount ?? 0 }}</strong>
      </article>
      <article class="kpi-card">
        <span>平均评分</span>
        <strong>{{ kpi?.avgRating ?? 0 }}</strong>
      </article>
    </div>

    <!-- 销售趋势 -->
    <section class="panel-card">
      <header class="panel-head">
        <div>
          <h4>销售趋势与客单价</h4>
          <p>左轴销售额，右轴客单价</p>
        </div>
        <div class="range-switch">
          <button :class="{ active: days === 7 }" @click="switchDays(7)">近 7 天</button>
          <button :class="{ active: days === 30 }" @click="switchDays(30)">近 30 天</button>
          <button :class="{ active: days === 90 }" @click="switchDays(90)">近 90 天</button>
        </div>
      </header>
      <div v-if="hasTrend" ref="trendEl" class="chart-box tall" />
      <div v-else class="chart-empty">
        <p>所选周期内还没有成交</p>
        <span>换个时间范围看看</span>
      </div>
    </section>

    <div class="two-col">
      <!-- 商品散点 -->
      <section class="panel-card">
        <header class="panel-head">
          <div>
            <h4>商品表现</h4>
            <p>横轴浏览 · 纵轴销量 · 颜色为评分</p>
          </div>
        </header>
        <div v-if="hasProducts" ref="scatterEl" class="chart-box" />
        <div v-else class="chart-empty">
          <p>还没有商品数据</p>
        </div>
        <div class="legend-note">
          <span><i style="background: #2f7a5c" />评分 ≥ 4.6</span>
          <span><i style="background: #f05a28" />4.2 - 4.6</span>
          <span><i style="background: #c9a227" />&lt; 4.2</span>
        </div>
        <div class="quadrant-note">
          <span><b>右上</b>明星品：曝光与销量双高，重点保库存</span>
          <span><b>右下</b>转化偏低：浏览量高但卖不动，检查详情页与价格</span>
          <span><b>左上</b>潜力品：卖得动但曝光少，值得加大推广</span>
        </div>
      </section>

      <!-- 评分分布 -->
      <section class="panel-card">
        <header class="panel-head">
          <div>
            <h4>评分分布</h4>
            <p>共 {{ ratingTotal }} 条评价</p>
          </div>
        </header>
        <div v-if="hasRatings" class="rating-list">
          <div v-for="r in data!.ratings" :key="r.star" class="rating-row">
            <span class="star">{{ r.star }} 星</span>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: ratingPct(r.count) + '%' }" />
            </div>
            <span class="pct">{{ ratingPct(r.count) }}%</span>
          </div>
        </div>
        <div v-else class="chart-empty small">
          <p>还没有买家评价</p>
        </div>

        <div v-if="data?.reviewKeywords?.length" class="review-words">
          <span
            v-for="k in data!.reviewKeywords"
            :key="k.word"
            class="rw"
            :class="k.type"
            :style="keywordStyle(k.weight)"
          >{{ k.word }}</span>
        </div>
      </section>
    </div>

    <!-- 漏斗 -->
    <section class="panel-card">
      <header class="panel-head">
        <div>
          <h4>转化漏斗</h4>
          <p>浏览 → 下单 → 支付</p>
        </div>
      </header>
      <div v-if="hasFunnel" class="funnel">
        <div v-for="(f, i) in data!.funnel" :key="f.name" class="funnel-step">
          <div class="fn-bar" :style="{ width: Math.max(28, 100 - i * 18) + '%' }">
            <span class="fn-name">{{ f.name }}</span>
            <strong>{{ f.value }}</strong>
          </div>
          <span v-if="i > 0" class="fn-rate">{{ f.rate }}%</span>
        </div>
      </div>
      <div v-else class="chart-empty small">
        <p>还没有足够的转化数据</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.seller-analytics {
  min-height: 400px;
}

.source-note {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--muted-light);
  padding: 8px 12px;
  margin-bottom: 12px;
  background: var(--surface-sunken);
  border-radius: 8px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.kpi-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: var(--shadow-card);
}

.kpi-card.primary {
  background: var(--ink);
  border-color: var(--ink);
}

.kpi-card.primary span {
  color: var(--muted);
}

.kpi-card.primary strong {
  color: var(--on-ink);
}

.kpi-card.warn {
  border-color: var(--brand);
  background: var(--brand-tint);
}

.kpi-card span {
  display: block;
  font-size: 10px;
  color: var(--muted);
  margin-bottom: 5px;
}

.kpi-card strong {
  font-family: var(--font-digit);
  font-size: 20px;
  color: var(--ink);
}

.panel-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 16px 18px;
  box-shadow: var(--shadow-card);
  margin-bottom: 16px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.panel-head h4 {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
}

.panel-head p {
  font-size: 10px;
  color: var(--muted-light);
  margin-top: 2px;
}

.range-switch {
  display: flex;
  gap: 4px;
  background: var(--surface-sunken);
  border-radius: 99px;
  padding: 3px;
}

.range-switch button {
  border: none;
  background: none;
  font-size: 11px;
  color: var(--muted);
  padding: 5px 12px;
  border-radius: 99px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.range-switch button.active {
  background: var(--ink);
  color: var(--on-ink);
}

.chart-box {
  height: 230px;
}

.chart-box.tall {
  height: 260px;
}

.two-col {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 16px;
}

.chart-empty {
  height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.chart-empty.small {
  height: 120px;
}

.chart-empty p {
  font-size: 13px;
  color: var(--muted);
}

.chart-empty span {
  font-size: 11px;
  color: var(--muted-light);
}

.quadrant-note {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 10px;
  color: var(--muted-light);
  padding-top: 8px;
  border-top: 1px dashed var(--line);
  margin-top: 8px;
}

.quadrant-note b {
  color: var(--muted);
  font-weight: 600;
}

.legend-note {
  display: flex;
  gap: 16px;
  font-size: 10px;
  color: var(--muted);
  padding-top: 6px;
}

.legend-note i {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

/* 评分分布 */
.rating-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 6px 0 12px;
}

.rating-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.star {
  font-size: 11px;
  color: var(--muted);
  width: 32px;
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 8px;
  background: var(--surface-sunken);
  border-radius: 99px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #f0774a, #fbb59b);
  border-radius: 99px;
  transition: width 0.6s ease;
}

.pct {
  font-size: 10px;
  color: var(--muted-light);
  width: 32px;
  text-align: right;
  flex-shrink: 0;
}

.review-words {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}

.rw {
  font-family: var(--font-serif);
  font-weight: 600;
}

.rw.good {
  color: var(--green);
}

.rw.bad {
  color: #c0504d;
}

/* 漏斗 */
.funnel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 0 6px;
}

.funnel-step {
  display: flex;
  align-items: center;
  gap: 12px;
}

.fn-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-radius: 10px;
  background: linear-gradient(90deg, rgba(240, 90, 40, 0.85), rgba(240, 90, 40, 0.45));
  color: #fff;
  transition: width 0.6s ease;
}

.fn-name {
  font-size: 12px;
}

.fn-bar strong {
  font-family: var(--font-digit);
  font-size: 15px;
}

.fn-rate {
  font-size: 11px;
  color: var(--muted);
}

.fn-rate.odd {
  color: var(--gold);
  cursor: help;
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .two-col {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>