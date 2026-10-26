<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { adminApi, agentApi } from '@/api'
import type { Dashboard, LlmStatus } from '@/types'
import { dateTime, money, orderStatusType } from '@/utils/format'

const router = useRouter()
const data = ref<Dashboard | null>(null)
const status = ref<LlmStatus | null>(null)
const loading = ref(true)
const trendEl = ref<HTMLDivElement>()
const statusEl = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
let statusChart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

onMounted(async () => {
  try {
    const [dashboard, llm] = await Promise.all([adminApi.stats(), agentApi.status().catch(() => null)])
    data.value = dashboard
    status.value = llm
    await nextTick()
    renderCharts()
    if (trendEl.value && statusEl.value) {
      observer = new ResizeObserver(() => {
        trendChart?.resize()
        statusChart?.resize()
      })
      observer.observe(trendEl.value)
      observer.observe(statusEl.value)
    }
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
  trendChart?.dispose()
  statusChart?.dispose()
})

function renderCharts() {
  if (trendEl.value && data.value) {
    trendChart = echarts.init(trendEl.value)
    trendChart.setOption({
      grid: { left: 12, right: 18, top: 24, bottom: 8, containLabel: true },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#1b1a17',
        borderWidth: 0,
        textStyle: { color: '#fff', fontSize: 11 }
      },
      xAxis: {
        type: 'category',
        data: data.value.trend.map((point) => point.date),
        axisLine: { lineStyle: { color: '#e8e2d6' } },
        axisTick: { show: false },
        axisLabel: { color: '#8b8171', fontSize: 10 }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f0ece3' } },
        axisLabel: { color: '#a49d90', fontSize: 9 }
      },
      series: [
        {
          name: '销售额',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          data: data.value.trend.map((point) => point.amount),
          lineStyle: { color: '#f05a28', width: 3 },
          itemStyle: { color: '#f05a28' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(240,90,40,0.22)' },
              { offset: 1, color: 'rgba(240,90,40,0.01)' }
            ])
          }
        }
      ]
    })
  }

  if (statusEl.value && data.value) {
    statusChart = echarts.init(statusEl.value)
    statusChart.setOption({
      tooltip: { trigger: 'item' },
      legend: {
        bottom: 0,
        icon: 'circle',
        itemWidth: 7,
        itemHeight: 7,
        textStyle: { color: '#6f6a60', fontSize: 10 }
      },
      series: [
        {
          type: 'pie',
          radius: ['52%', '74%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 3 },
          label: { show: false },
          data: data.value.orderStatus.map((item, index) => ({
            name: item.label,
            value: item.count,
            itemStyle: {
              color: ['#f05a28', '#1b1a17', '#b8893d', '#2f7a5c', '#b9b1a3', '#d8d0c0'][index % 6]
            }
          }))
        }
      ]
    })
  }
}
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <div class="stat-grid">
      <article class="stat-card dark">
        <div class="stat-top"><span>今日销售额</span><el-icon><TrendCharts /></el-icon></div>
        <strong>{{ money(data?.todaySales ?? 0) }}</strong>
        <small>累计 {{ money(data?.totalSales ?? 0) }}</small>
      </article>
      <article class="stat-card">
        <div class="stat-top"><span>今日订单</span><el-icon><Tickets /></el-icon></div>
        <strong>{{ data?.todayOrderCount ?? 0 }}</strong>
        <small>全部订单 {{ data?.orderCount ?? 0 }} 笔</small>
      </article>
      <article class="stat-card">
        <div class="stat-top"><span>在售商品</span><el-icon><Goods /></el-icon></div>
        <strong>{{ data?.productCount ?? 0 }}</strong>
        <small>商品库共 {{ data?.productCount ?? 0 }} 件</small>
      </article>
      <article class="stat-card">
        <div class="stat-top"><span>注册用户</span><el-icon><User /></el-icon></div>
        <strong>{{ data?.userCount ?? 0 }}</strong>
        <small>累计 Agent 执行 {{ data?.agentLogCount ?? 0 }} 步</small>
      </article>
    </div>

    <div class="chart-grid">
      <section class="panel trend-panel">
        <div class="panel-head">
          <div><h3>近 7 日销售趋势</h3><span>已支付 / 已发货 / 已完成订单</span></div>
          <span class="pill">销售额</span>
        </div>
        <div ref="trendEl" class="chart"></div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div><h3>订单状态分布</h3><span>全部订单</span></div>
        </div>
        <div ref="statusEl" class="chart"></div>
      </section>
    </div>

    <div class="bottom-grid">
      <section class="panel">
        <div class="panel-head">
          <div><h3>热销商品</h3><span>按累计销量排序</span></div>
          <button class="text-btn" @click="router.push('/admin/products')">管理商品</button>
        </div>
        <div class="top-products">
          <div v-for="(product, index) in data?.topProducts || []" :key="product.id" class="top-product">
            <span class="rank">{{ index + 1 }}</span>
            <img :src="product.image" :alt="product.name" />
            <div class="top-copy">
              <strong>{{ product.name }}</strong>
              <span>{{ product.categoryName }} · {{ money(product.price) }}</span>
            </div>
            <div class="sales-bar">
              <span :style="{ width: `${Math.min(100, (product.sales / (data?.topProducts[0]?.sales || 1)) * 100)}%` }"></span>
            </div>
            <b>{{ product.sales }}</b>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div><h3>最近订单</h3><span>最新 5 笔</span></div>
          <button class="text-btn" @click="router.push('/admin/orders')">订单管理</button>
        </div>
        <div class="recent-orders">
          <button v-for="order in data?.recentOrders || []" :key="order.id" class="recent-order" @click="router.push('/admin/orders')">
            <div>
              <strong>{{ order.orderNo }}</strong>
              <span>{{ order.receiver }} · {{ dateTime(order.createdAt) }}</span>
            </div>
            <el-tag :type="orderStatusType(order.status)" size="small" round>{{ order.statusLabel }}</el-tag>
            <b>{{ money(order.totalAmount) }}</b>
          </button>
        </div>
      </section>
    </div>

    <section class="panel agent-status">
      <div class="agent-copy">
        <div class="agent-icon">AI</div>
        <div>
          <h3>Agent 运行状态</h3>
          <p>
            当前模式：<strong>{{ status?.available ? 'DeepSeek 在线' : '规则引擎模式' }}</strong>
            <span v-if="status?.configured"> · 模型 {{ status.model }}</span>
          </p>
        </div>
      </div>
      <div class="agent-metrics">
        <div><span>调用次数</span><strong>{{ status?.callCount ?? 0 }}</strong></div>
        <div><span>失败次数</span><strong>{{ status?.failCount ?? 0 }}</strong></div>
        <div><span>执行步数</span><strong>{{ data?.agentLogCount ?? 0 }}</strong></div>
      </div>
      <el-button round @click="router.push('/admin/agent-logs')">查看执行日志</el-button>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.stat-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 15px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-card.dark {
  background: var(--ink);
  color: var(--on-ink);
  border-color: var(--ink);
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 10px;
  color: var(--muted);
}

.stat-card.dark .stat-top {
  color: #b7afa2;
}

.stat-top .el-icon {
  font-size: 16px;
  color: var(--brand);
}

.stat-card strong {
  font-family: var(--font-digit);
  font-variant-numeric: tabular-nums;
  font-size: 28px;
  letter-spacing: -0.03em;
  font-weight: 700;
}

.stat-card small {
  font-size: 9px;
  color: var(--muted);
}

.stat-card.dark small {
  color: #8f887d;
}

.chart-grid {
  display: grid;
  grid-template-columns: 1.6fr 0.8fr;
  gap: 14px;
}

.bottom-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 14px;
}

.panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 15px;
  padding: 20px;
  min-width: 0;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-head h3 {
  font-size: 14px;
}

.panel-head span {
  font-size: 9px;
  color: var(--muted);
}

.text-btn {
  border: none;
  background: transparent;
  color: var(--brand);
  font-size: 10px;
  cursor: pointer;
}

.chart {
  height: 250px;
  width: 100%;
}

.top-products {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.top-product {
  display: grid;
  grid-template-columns: 18px 34px minmax(120px, 1.4fr) 1fr 40px;
  align-items: center;
  gap: 9px;
  font-size: 10px;
}

.rank {
  color: var(--muted-light);
  font-size: 9px;
}

.top-product img {
  width: 34px;
  height: 34px;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid var(--line);
}

.top-copy {
  min-width: 0;
}

.top-copy strong {
  display: block;
  font-size: 10.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-copy span {
  font-size: 8.5px;
  color: var(--muted);
}

.sales-bar {
  height: 5px;
  background: var(--bg);
  border-radius: 99px;
  overflow: hidden;
}

.sales-bar span {
  display: block;
  height: 100%;
  background: var(--brand);
  border-radius: 99px;
}

.top-product b {
  text-align: right;
  font-size: 10px;
}

.recent-orders {
  display: flex;
  flex-direction: column;
}

.recent-order {
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  border-bottom: 1px solid var(--line);
  background: transparent;
  padding: 10px 0;
  text-align: left;
  cursor: pointer;
}

.recent-order:last-child {
  border-bottom: none;
}

.recent-order div {
  flex: 1;
  min-width: 0;
}

.recent-order strong {
  display: block;
  font-size: 10px;
  letter-spacing: 0.02em;
}

.recent-order span {
  display: block;
  font-size: 8px;
  color: var(--muted);
  margin-top: 2px;
}

.recent-order b {
  font-size: 11px;
  white-space: nowrap;
}

.agent-status {
  display: flex;
  align-items: center;
  gap: 24px;
}

.agent-copy {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.agent-icon {
  width: 40px;
  height: 40px;
  border-radius: 11px;
  background: var(--brand-soft);
  color: var(--brand-deep);
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
}

.agent-copy h3 {
  font-size: 13px;
}

.agent-copy p {
  font-size: 10px;
  color: var(--muted);
  margin-top: 3px;
}

.agent-copy p strong {
  color: var(--ink);
}

.agent-metrics {
  display: flex;
  gap: 26px;
}

.agent-metrics div {
  text-align: right;
}

.agent-metrics span {
  display: block;
  font-size: 9px;
  color: var(--muted);
}

.agent-metrics strong {
  font-size: 17px;
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }

  .agent-status {
    flex-direction: column;
    align-items: flex-start;
  }

  .agent-metrics {
    width: 100%;
    justify-content: space-between;
  }

  .top-product {
    grid-template-columns: 16px 30px minmax(90px, 1fr) 34px;
  }

  .sales-bar {
    display: none;
  }
}
</style>
