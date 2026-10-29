<script setup lang="ts">
import '@/views/seller/seller-shared.css'

/**
 * 卖家 · 店铺订单：查看买家订单并处理发货。
 * 与商品管理、数据分析拆成独立路由。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sellerApi } from '@/api'
import type { SellerOrderDetail, SellerOrderRow, SellerOverview } from '@/types'
import { dateTime, money } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const overview = ref<SellerOverview | null>(null)

const orders = ref<SellerOrderRow[]>([])
const ordersLoading = ref(false)
const orderFilter = ref('')
const orderPage = ref(1)
const orderPageSize = ref(10)
const orderTotal = ref(0)

const shipDialog = ref(false)
const shipSaving = ref(false)
const shipTarget = ref<SellerOrderRow | null>(null)
const shipForm = reactive({ carrier: '顺丰速运', trackingNo: '' })

const detailDialog = ref(false)
const orderDetail = ref<SellerOrderDetail | null>(null)

const pendingCount = computed(() => overview.value?.pendingShipOrders ?? 0)

onMounted(async () => {
  if (!auth.isLoggedIn) {
    router.replace({ name: 'login', query: { redirect: '/seller/orders' } })
    return
  }
  if (!auth.isSeller && !auth.isAdmin) {
    ElMessage.warning('当前账号不是卖家，无法进入卖家中心')
    router.replace('/')
    return
  }
  loading.value = true
  try {
    const [ov] = await Promise.all([sellerApi.overview(), loadOrders(1)])
    overview.value = ov
  } finally {
    loading.value = false
  }
})

async function loadOrders(targetPage = orderPage.value) {
  ordersLoading.value = true
  try {
    const result = await sellerApi.orders({
      shipStatus: orderFilter.value || undefined,
      page: targetPage,
      size: orderPageSize.value
    })
    orders.value = result.records
    orderTotal.value = result.total
    orderPage.value = targetPage
  } catch {
    orders.value = []
    orderTotal.value = 0
  } finally {
    ordersLoading.value = false
  }
}

function openShip(row: SellerOrderRow) {
  shipTarget.value = row
  shipForm.carrier = '顺丰速运'
  shipForm.trackingNo = ''
  shipDialog.value = true
}

async function confirmShip() {
  if (!shipTarget.value) return
  shipSaving.value = true
  try {
    const result = await sellerApi.ship(shipTarget.value.orderId, {
      carrier: shipForm.carrier,
      trackingNo: shipForm.trackingNo || undefined
    })
    ElMessage.success(`已发货，运单号 ${result.trackingNo}`)
    shipDialog.value = false
    await loadOrders()
    overview.value = await sellerApi.overview()
  } finally {
    shipSaving.value = false
  }
}

async function openOrderDetail(row: SellerOrderRow) {
  orderDetail.value = await sellerApi.orderDetail(row.orderId)
  detailDialog.value = true
}
</script>

<template>
  <div v-loading="loading" class="seller-page">
    <div class="container">
      <header class="seller-head">
        <p class="page-desc">查看买家在你店铺下的订单，及时处理发货。</p>
      </header>

      <!-- 订单相关统计 -->
      <section class="stat-grid">
        <article class="stat-card alert">
          <span>待发货订单</span>
          <strong>{{ pendingCount }}</strong>
          <em v-if="overview?.pendingShipItems">共 {{ overview.pendingShipItems }} 件商品</em>
          <em v-else>暂无待处理</em>
        </article>
      </section>

      <div class="order-toolbar">
        <el-radio-group v-model="orderFilter" size="default" @change="loadOrders(1)">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="PENDING">待发货</el-radio-button>
          <el-radio-button value="SHIPPED">已发货</el-radio-button>
        </el-radio-group>
        <span class="order-total">共 {{ orderTotal }} 单</span>
      </div>

      <el-table v-loading="ordersLoading" :data="orders" style="width: 100%">
        <el-table-column label="订单号" min-width="170">
          <template #default="{ row }">
            <span class="order-no">{{ row.orderNo || row.orderId }}</span>
            <span class="order-time">{{ dateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收货人" min-width="150">
          <template #default="{ row }">
            <strong class="receiver">{{ row.receiver }}</strong>
            <span class="order-time">{{ row.phone }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收货地址" min-width="200">
          <template #default="{ row }">
            <span class="addr">{{ row.addressText }}</span>
          </template>
        </el-table-column>
        <el-table-column label="我的商品" width="110">
          <template #default="{ row }">{{ row.myItemCount }} 件</template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">{{ money(row.myAmount) }}</template>
        </el-table-column>
        <el-table-column label="发货状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.shipStatus === 'SHIPPED' ? 'success' : 'warning'" size="small" round>
              {{ row.shipStatusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.shipStatus !== 'SHIPPED'" link type="primary" @click="openShip(row)">
              发货
            </el-button>
            <el-button link @click="openOrderDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="orderTotal > orderPageSize"
        background
        layout="total, prev, pager, next"
        :total="orderTotal"
        :page-size="orderPageSize"
        :current-page="orderPage"
        style="margin-top: 18px; justify-content: flex-end"
        @current-change="loadOrders"
      />

      <div v-if="!ordersLoading && !orders.length" class="empty-state small">
        <strong>{{ orderFilter === 'PENDING' ? '没有待发货的订单' : '还没有订单' }}</strong>
        <p>买家下单并支付后会显示在这里。</p>
      </div>
    </div>

    <!-- 发货 -->
    <el-dialog v-model="shipDialog" title="订单发货" width="460px" :append-to-body="true">
      <el-form label-position="top">
        <el-form-item label="承运商">
          <el-select v-model="shipForm.carrier" style="width: 100%">
            <el-option label="顺丰速运" value="顺丰速运" />
            <el-option label="中通快递" value="中通快递" />
            <el-option label="圆通速递" value="圆通速递" />
            <el-option label="京东物流" value="京东物流" />
          </el-select>
        </el-form-item>
        <el-form-item label="运单号（选填，不填自动生成）">
          <el-input v-model="shipForm.trackingNo" placeholder="例如 SF1234567890" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="shipDialog = false">取消</el-button>
        <el-button type="primary" round :loading="shipSaving" @click="confirmShip">确认发货</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情 -->
    <el-dialog v-model="detailDialog" title="订单详情" width="620px" :append-to-body="true">
      <div v-if="orderDetail" class="detail-body">
        <div class="detail-row"><span>订单号</span><strong>{{ orderDetail.orderNo }}</strong></div>
        <div class="detail-row"><span>收货人</span><strong>{{ orderDetail.receiver }} {{ orderDetail.phone }}</strong></div>
        <div class="detail-row"><span>收货地址</span><strong>{{ orderDetail.addressText }}</strong></div>
        <div class="detail-row"><span>下单时间</span><strong>{{ dateTime(orderDetail.createdAt) }}</strong></div>
        <div class="detail-row"><span>订单状态</span><strong>{{ orderDetail.orderStatusLabel || orderDetail.orderStatus }}</strong></div>
        <div v-if="orderDetail.carrier" class="detail-row">
          <span>物流</span><strong>{{ orderDetail.carrier }} {{ orderDetail.trackingNo }}</strong>
        </div>
        <div class="detail-items">
          <div v-for="item in orderDetail.items" :key="item.productId" class="detail-item">
            <span>{{ item.productName }}</span>
            <span>×{{ item.quantity }}</span>
            <strong>{{ money(item.price) }}</strong>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-desc {
  font-size: 13px;
  color: var(--muted);
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-row {
  display: flex;
  gap: 14px;
  font-size: 13px;
}

.detail-row span {
  color: var(--muted);
  width: 68px;
  flex-shrink: 0;
}

.detail-row strong {
  color: var(--ink);
  font-weight: 500;
}

.detail-items {
  margin-top: 8px;
  border-top: 1px dashed var(--line);
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12.5px;
  color: var(--ink-soft);
}

.detail-item span:first-child {
  flex: 1;
}
</style>