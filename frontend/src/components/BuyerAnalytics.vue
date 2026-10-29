<script setup lang="ts">
/**
 * 买家消费画像：品类构成环形图 + 偏好雷达图 + AI 关键词云。
 * 三块都做了空数据兜底，没有记录时显示引导文案而不是空白图表。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { InfoFilled } from '@element-plus/icons-vue'
import { analyticsApi } from '@/api'
import type { BuyerAnalytics } from '@/types'

const loading = ref(true)
const data = ref<BuyerAnalytics | null>(null)

const shareEl = ref<HTMLDivElement | null>(null)
const radarEl = ref<HTMLDivElement | null>(null)
let shareChart: echarts.ECharts | null = null
let radarChart: echarts.ECharts | null = null

const hasSpend = computed(() => (data.value?.categoryShares?.length ?? 0) > 0)
const hasPreference = computed(() =>
  (data.value?.preferences ?? []).some((p) => p.score > 0)
)
const hasKeywords = computed(() => (data.value?.keywords?.length ?? 0) > 0)

const money = (v: number | string) =>
  '¥' + Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 0 })

function renderShare() {
  if (!shareEl.value || !data.value) return
  shareChart = shareChart || echarts.init(shareEl.value)
  const items = data.value.categoryShares
  shareChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => `${p.name}<br/>${money(p.value)} · ${p.data.count} 项`
    },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'center',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { fontSize: 11, color: '#8a8378' }
    },
    series: [
      {
        type: 'pie',
        radius: ['52%', '74%'],
        center: ['34%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderWidth: 2, borderColor: '#fff' },
        label: { show: false },
        emphasis: { label: { show: false }, scaleSize: 6 },
        data: items.map((i) => ({ name: i.name, value: Number(i.amount), count: i.count }))
      }
    ]
  }, true)
}

function renderRadar() {
  if (!radarEl.value || !data.value) return
  radarChart = radarChart || echarts.init(radarEl.value)
  const prefs = data.value.preferences
  radarChart.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      indicator: prefs.map((p) => ({ name: p.name, max: 100 })),
      radius: '66%',
      splitNumber: 4,
      axisName: { fontSize: 11, color: '#8a8378' },
      splitLine: { lineStyle: { color: 'rgba(140,130,115,.18)' } },
      splitArea: { areaStyle: { color: ['rgba(240,90,40,.02)', 'rgba(240,90,40,.05)'] } },
      axisLine: { lineStyle: { color: 'rgba(140,130,115,.18)' } }
    },
    series: [
      {
        type: 'radar',
        symbolSize: 5,
        lineStyle: { width: 2, color: '#f05a28' },
        areaStyle: { color: 'rgba(240,90,40,.16)' },
        itemStyle: { color: '#f05a28' },
        data: [{ value: prefs.map((p) => p.score), name: '偏好度' }]
      }
    ]
  }, true)
}

async function load() {
  loading.value = true
  try {
    data.value = await analyticsApi.buyer()
  } catch {
    data.value = null
  } finally {
    loading.value = false
    await nextTick()
    renderShare()
    renderRadar()
  }
}

function onResize() {
  shareChart?.resize()
  radarChart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  shareChart?.dispose()
  radarChart?.dispose()
})

// 关键词字号：按出现频次映射到 12-26px
const keywordStyle = (weight: number) => {
  const size = Math.min(26, 12 + weight * 2.2)
  return { fontSize: `${size}px` }
}

const typeColor: Record<string, string> = {
  category: 'var(--brand)',
  feature: 'var(--ink-soft)',
  scene: 'var(--green)',
  price: 'var(--gold)'
}
</script>

<template>
  <div v-loading="loading" class="buyer-analytics">
    <!-- 数据来源说明 -->
    <div class="source-note">
      <el-icon><InfoFilled /></el-icon>
      <span>画像基于你的订单与浏览足迹计算，演示环境包含历史模拟数据</span>
    </div>

    <!-- 概览数字 -->
    <div class="overview">
      <div class="ov-item">
        <span>累计消费</span>
        <strong>{{ money(data?.totalSpent ?? 0) }}</strong>
      </div>
      <div class="ov-item">
        <span>订单数</span>
        <strong>{{ data?.orderCount ?? 0 }}</strong>
      </div>
      <div class="ov-item">
        <span>购买件数</span>
        <strong>{{ data?.itemCount ?? 0 }}</strong>
      </div>
    </div>

    <div class="chart-grid">
      <!-- 品类构成 -->
      <section class="chart-card">
        <header>
          <h4>消费构成</h4>
          <span>钱都花在哪了</span>
        </header>
        <div v-if="hasSpend" ref="shareEl" class="chart-box" />
        <div v-else class="chart-empty">
          <p>还没有消费记录</p>
          <span>下单后这里会显示各品类占比</span>
        </div>
      </section>

      <!-- 偏好雷达 -->
      <section class="chart-card">
        <header>
          <h4>兴趣偏好</h4>
          <span>浏览 + 加购 + 购买加权</span>
        </header>
        <div v-if="hasPreference" ref="radarEl" class="chart-box" />
        <div v-else class="chart-empty">
          <p>暂无足够行为数据</p>
          <span>多逛逛、加加购物车，画像会更准</span>
        </div>
      </section>

      <!-- AI 关键词 -->
      <section class="chart-card wide">
        <header>
          <h4>AI 对话偏好词</h4>
          <span>从你和导购的对话里提取</span>
        </header>
        <div v-if="hasKeywords" class="word-cloud">
          <span
            v-for="k in data!.keywords"
            :key="k.word"
            class="word"
            :style="{ ...keywordStyle(k.weight), color: typeColor[k.type] || 'var(--ink-soft)' }"
          >
            {{ k.word }}
          </span>
        </div>
        <div v-else class="chart-empty">
          <p>还没有对话记录</p>
          <span>去「智能导购」聊几句，这里会出现你的偏好词</span>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.buyer-analytics {
  min-height: 320px;
}

.source-note {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--muted-light);
  padding: 8px 12px;
  margin-bottom: 14px;
  background: var(--surface-sunken);
  border-radius: 8px;
}

.overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}

.ov-item {
  background: var(--surface-sunken);
  border-radius: 12px;
  padding: 14px 16px;
}

.ov-item span {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
}

.ov-item strong {
  font-family: var(--font-digit);
  font-size: 22px;
  color: var(--ink);
}

.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.chart-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 16px 18px 10px;
  box-shadow: var(--shadow-card);
}

.chart-card.wide {
  grid-column: 1 / -1;
}

.chart-card header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 6px;
}

.chart-card h4 {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
}

.chart-card header span {
  font-size: 10px;
  color: var(--muted-light);
}

.chart-box {
  height: 240px;
}

.chart-empty {
  height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--muted-light);
}

.chart-empty p {
  font-size: 13px;
  color: var(--muted);
}

.chart-empty span {
  font-size: 11px;
}

.word-cloud {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 18px;
  padding: 20px 6px 24px;
  min-height: 150px;
}

.word {
  font-family: var(--font-serif);
  font-weight: 600;
  line-height: 1;
  transition: transform 0.2s ease;
}

.word:hover {
  transform: scale(1.12);
}

@media (max-width: 900px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .overview {
    grid-template-columns: 1fr;
  }
}
</style>