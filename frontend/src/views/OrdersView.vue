<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '@/api'
import type { Order } from '@/types'
import { dateTime, money, orderStatusType } from '@/utils/format'

const router = useRouter()
const active = ref('ALL')
const orders = ref<Order[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)

const tabs = [
  { name: 'ALL', label: '全部订单' },
  { name: 'PENDING_PAYMENT', label: '待支付' },
  { name: 'PAID', label: '待发货' },
  { name: 'SHIPPED', label: '待收货' },
  { name: 'COMPLETED', label: '已完成' },
  { name: 'CLOSED', label: '已关闭' }
]

onMounted(load)

async function load() {
  loading.value = true
  try {
    const result = await orderApi.list({
      status: active.value === 'ALL' ? undefined : active.value,
      page: page.value,
      size: 8
    })
    orders.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function switchTab(name: string) {
  active.value = name
  page.value = 1
  await load()
}

function pay(order: Order) {
  router.push(`/order/${order.id}`)
}

async function cancel(order: Order) {
  await ElMessageBox.confirm('取消后库存会释放，确定取消该订单吗？', '取消订单', {
    confirmButtonText: '确定取消',
    cancelButtonText: '再想想',
    type: 'warning'
  })
  await orderApi.cancel(order.id)
  ElMessage.success('订单已取消')
  await load()
}

async function confirmReceipt(order: Order) {
  await ElMessageBox.confirm('确认已经收到商品？', '确认收货', {
    confirmButtonText: '确认收货',
    cancelButtonText: '取消'
  })
  await orderApi.confirm(order.id)
  ElMessage.success('订单已完成')
  await load()
}

function changePage(value: number) {
  page.value = value
  load()
}
</script>

<template>
  <div class="orders-page">
    <div class="page-head">
      <div class="container">
        <span class="eyebrow">ORDER HISTORY</span>
        <h1>我的订单</h1>
        <p class="desc">查看订单状态、模拟支付、确认收货。Agent 也可以帮你查询物流进度。</p>
      </div>
    </div>

    <div class="container orders-wrap">
      <div class="tabs">
        <button
          v-for="tab in tabs"
          :key="tab.name"
          :class="{ active: active === tab.name }"
          @click="switchTab(tab.name)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-loading="loading" class="order-list">
        <article v-for="order in orders" :key="order.id" class="order-card">
          <header class="order-head">
            <div class="order-meta">
              <span class="order-no">{{ order.orderNo }}</span>
              <span>{{ dateTime(order.createdAt) }}</span>
            </div>
            <el-tag :type="orderStatusType(order.status)" effect="light" round>{{ order.statusLabel }}</el-tag>
          </header>

          <div class="order-body" @click="router.push(`/order/${order.id}`)">
            <div class="order-thumbs">
              <div v-for="item in order.items.slice(0, 3)" :key="item.id" class="thumb">
                <img :src="item.productImage" :alt="item.productName" />
              </div>
            </div>
            <div class="order-summary">
              <strong>共 {{ order.totalQuantity }} 件商品</strong>
              <span>点击查看订单详情</span>
            </div>
            <div class="order-amount">
              <span>实付</span>
              <b>{{ money(order.totalAmount) }}</b>
            </div>
          </div>

          <footer class="order-actions">
            <button class="action-link" @click="router.push(`/order/${order.id}`)">订单详情</button>
            <template v-if="order.status === 'PENDING_PAYMENT'">
              <el-button round @click="cancel(order)">取消订单</el-button>
              <el-button type="primary" round @click="pay(order)">立即支付</el-button>
            </template>
            <el-button v-else-if="order.status === 'SHIPPED'" type="primary" round @click="confirmReceipt(order)">
              确认收货
            </el-button>
            <el-button v-else-if="order.status === 'COMPLETED'" round @click="router.push(`/product/${order.items[0]?.productId}`)">
              再次购买
            </el-button>
          </footer>
        </article>

        <div v-if="!loading && !orders.length" class="empty">
          <strong>这里还没有订单</strong>
          <p>去挑一件喜欢的商品，或让 Agent 根据需求推荐。</p>
          <div class="empty-actions">
            <el-button type="primary" round @click="router.push('/products')">去逛商品</el-button>
            <el-button round @click="router.push('/chat')">问 Agent</el-button>
          </div>
        </div>
      </div>

      <el-pagination
        v-if="total > 8"
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="8"
        :current-page="page"
        style="margin-top: 30px"
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<style scoped>
.orders-page {
  padding-bottom: 50px;
}

.orders-wrap {
  padding-top: 26px;
}

.tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 22px;
}

.tabs button {
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 99px;
  padding: 8px 16px;
  font-size: 12px;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.2s;
}

.tabs button:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.tabs button.active {
  background: var(--ink);
  border-color: var(--ink);
  color: var(--on-ink);
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 240px;
}

.order-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  overflow: hidden;
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 20px;
  background: var(--surface-sunken);
  border-bottom: 1px solid var(--line);
}

.order-meta {
  display: flex;
  gap: 16px;
  font-size: 11px;
  color: var(--muted);
  flex-wrap: wrap;
}

.order-no {
  color: var(--ink);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.order-body {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px 20px;
  cursor: pointer;
}

.order-thumbs {
  display: flex;
  gap: 8px;
}

.thumb {
  width: 62px;
  height: 62px;
  border-radius: 9px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: var(--surface-sunken);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.order-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.order-summary strong {
  font-size: 13px;
}

.order-summary span {
  font-size: 10px;
  color: var(--muted);
}

.order-amount {
  margin-left: auto;
  text-align: right;
  font-size: 10px;
  color: var(--muted);
}

.order-amount b {
  display: block;
  font-size: 18px;
  color: var(--ink);
}

.order-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--line);
}

.action-link {
  margin-right: auto;
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.action-link:hover {
  color: var(--ink);
}

.empty-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 18px;
}

@media (max-width: 620px) {
  .order-body {
    flex-wrap: wrap;
  }

  .order-amount {
    margin-left: 0;
    text-align: left;
  }

  .order-actions {
    flex-wrap: wrap;
  }

  .action-link {
    width: 100%;
    text-align: left;
    margin-bottom: 4px;
  }
}
</style>
