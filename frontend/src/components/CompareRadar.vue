<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { CompareResult } from '@/types'

const props = defineProps<{ result: CompareResult | null }>()
const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

const palette = ['#f05a28', '#1b1a17', '#2f7a5c', '#b8893d']

function render() {
  if (!chartEl.value || !props.result || !props.result.dimensions.length) return
  if (!chart) {
    chart = echarts.init(chartEl.value)
  }
  const dimensions = props.result.dimensions
  const indicators = dimensions.map((dimension) => ({
    name: dimension.unit ? `${dimension.key}(${dimension.unit})` : dimension.key,
    max: 100
  }))
  const series = props.result.products.map((product) => ({
    name: product.name.length > 12 ? `${product.name.slice(0, 12)}…` : product.name,
    value: dimensions.map((dimension) => Math.round((dimension.normalizedValues[product.id] || 0) * 100))
  }))

  chart.setOption(
    {
      color: palette,
      tooltip: { trigger: 'item' },
      legend: {
        bottom: 0,
        icon: 'circle',
        itemWidth: 7,
        itemHeight: 7,
        textStyle: { color: '#6f6a60', fontSize: 11 }
      },
      radar: {
        center: ['50%', '46%'],
        radius: '62%',
        indicator: indicators,
        splitNumber: 4,
        axisName: { color: '#6f6a60', fontSize: 10 },
        splitLine: { lineStyle: { color: '#e8e2d6' } },
        splitArea: { areaStyle: { color: ['#fff', '#fbf9f4'] } },
        axisLine: { lineStyle: { color: '#e8e2d6' } }
      },
      series: [
        {
          type: 'radar',
          symbol: 'circle',
          symbolSize: 4,
          lineStyle: { width: 2 },
          areaStyle: { opacity: 0.08 },
          data: series
        }
      ]
    },
    true
  )
}

onMounted(() => {
  nextTick(render)
  if (chartEl.value) {
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(chartEl.value)
  }
})

watch(
  () => props.result,
  () => nextTick(render),
  { deep: true }
)

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="radar-wrap">
    <div v-if="!result" class="radar-empty">选择商品后生成雷达图</div>
    <div v-else ref="chartEl" class="radar-chart"></div>
  </div>
</template>

<style scoped>
.radar-wrap {
  width: 100%;
  height: 360px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 16px;
  overflow: hidden;
}

.radar-chart {
  width: 100%;
  height: 100%;
}

.radar-empty {
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--muted);
  font-size: 13px;
}
</style>
