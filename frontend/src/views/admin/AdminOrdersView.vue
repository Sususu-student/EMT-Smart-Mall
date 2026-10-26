<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'
import type { Order } from '@/types'
import { dateTime, money, orderStatusType } from '@/utils/format'

const loading = ref(true)
const orders = ref<Order[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const status = ref('')
const detailVisible = ref(false)
const current = ref<Order | null>(null)
const shipDialog = ref(false)
const shipTarget = ref<Order | null>(null)
const shipping = ref(false)
const shipForm = reactive({ carrier: '顺丰速运', trackingNo: '' })

const statusOptions = [
  { label: '待支付', value: 'PENDING_PAYMENT' },
  { label: '已支付', value: 'PAID' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已关闭', value: 'CLOSED' }
]

onMounted(load)

async function load() {
  loading.value = true
  try {
    const result = await adminApi.orders({
      page: page.value,
      size: 10,
      keyword: keyword.value || undefined,
      status: status.value || undefined
    })
    orders.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function openDetail(order: Order) {
  current.value = order
  detailVisible.value = true
}

function openShip(order: Order) {
  shipTarget.value = order
  shipForm.carrier = '顺丰速运'
  shipForm.trackingNo = ''
  detailVisible.value = false
  shipDialog.value = true
}

async function confirmShip() {
  if (!shipTarget.value) return
  shipping.value = true
  try {
    await adminApi.ship(shipTarget.value.id, { ...shipForm })
    ElMessage.success('已标记为已发货')
    shipDialog.value = false
    await load()
  } finally {
    shipping.value = false
  }
}

function changePage(value: number) {
  page.value = value
  load()
}
</script>

<template>
  <div class="admin-page">
    <section class="panel">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="keyword" placeholder="搜索订单号或收货人" clearable style="width: 240px" @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="search">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button round @click="search">查询</el-button>
        </div>
        <span class="total">共 {{ total }} 笔订单</span>
      </div>

      <el-table v-loading="loading" :data="orders" style="width: 100%">
        <el-table-column label="订单号" min-width="190">
          <template #default="{ row }">
            <button class="order-link" @click="openDetail(row)">{{ row.orderNo }}</button>
            <div class="sub">{{ dateTime(row.createdAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="220">
          <template #default="{ row }">
            <div class="items-cell">
              <img v-for="item in row.items.slice(0, 3)" :key="item.id" :src="item.productImage" :alt="item.productName" />
              <span>{{ row.items[0]?.productName }} 等 {{ row.totalQuantity }} 件</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="收货人" width="110">
          <template #default="{ row }">
            <div>{{ row.receiver }}</div>
            <div class="sub">{{ row.phone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderStatusType(row.status)" size="small" round>{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PAID'" link type="success" @click="openShip(row)">发货</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 10"
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="10"
        :current-page="page"
        style="margin-top: 18px; justify-content: flex-end"
        @current-change="changePage"
      />
    </section>

    <el-drawer v-model="detailVisible" title="订单详情" size="480px">
      <div v-if="current" class="order-detail">
        <div class="detail-head">
          <div>
            <h3>{{ current.orderNo }}</h3>
            <span>{{ dateTime(current.createdAt) }}</span>
          </div>
          <el-tag :type="orderStatusType(current.status)" round>{{ current.statusLabel }}</el-tag>
        </div>
        <div class="detail-section">
          <h4>商品</h4>
          <div v-for="item in current.items" :key="item.id" class="detail-item">
            <img :src="item.productImage" :alt="item.productName" />
            <div>
              <strong>{{ item.productName }}</strong>
              <span>{{ money(item.productPrice) }} × {{ item.quantity }}</span>
            </div>
            <b>{{ money(item.productPrice * item.quantity) }}</b>
          </div>
        </div>
        <div class="detail-section">
          <h4>收货信息</h4>
          <p>{{ current.receiver }} · {{ current.phone }}</p>
          <p>{{ current.addressText }}</p>
          <p v-if="current.remark">备注：{{ current.remark }}</p>
        </div>
        <div class="detail-section">
          <h4>金额</h4>
          <div class="amount-line"><span>商品合计</span><b>{{ money(current.totalAmount) }}</b></div>
          <div class="amount-line total"><span>实付</span><b>{{ money(current.totalAmount) }}</b></div>
        </div>
        <el-button v-if="current.status === 'PAID'" type="primary" round style="width: 100%" @click="openShip(current)">
          标记为已发货
        </el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="shipDialog" title="订单发货" width="420px">
      <p class="ship-tip">填写承运商与运单号，用户可在订单详情中查看物流信息。</p>
      <el-form label-position="top">
        <el-form-item label="承运商">
          <el-select v-model="shipForm.carrier" style="width: 100%">
            <el-option label="顺丰速运" value="顺丰速运" />
            <el-option label="京东物流" value="京东物流" />
            <el-option label="中通快递" value="中通快递" />
            <el-option label="圆通速递" value="圆通速递" />
          </el-select>
        </el-form-item>
        <el-form-item label="运单号">
          <el-input v-model="shipForm.trackingNo" placeholder="不填则自动生成演示运单号" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialog = false">取消</el-button>
        <el-button type="primary" :loading="shipping" @click="confirmShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ship-tip {
  font-size: 10px;
  color: var(--muted);
  line-height: 1.7;
  margin-bottom: 14px;
}

.panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 15px;
  padding: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.total {
  font-size: 10px;
  color: var(--muted);
}

.order-link {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 10.5px;
  font-weight: 600;
  color: var(--ink);
  cursor: pointer;
}

.order-link:hover {
  color: var(--brand);
}

.sub {
  font-size: 9px;
  color: var(--muted);
  margin-top: 2px;
}

.items-cell {
  display: flex;
  align-items: center;
  gap: 5px;
}

.items-cell img {
  width: 28px;
  height: 28px;
  border-radius: 5px;
  object-fit: cover;
  border: 1px solid var(--line);
}

.items-cell span {
  font-size: 9px;
  color: var(--muted);
  margin-left: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.detail-head h3 {
  font-size: 13px;
}

.detail-head span {
  display: block;
  font-size: 9px;
  color: var(--muted);
  margin-top: 4px;
}

.detail-section {
  padding: 16px 0;
  border-bottom: 1px solid var(--line);
}

.detail-section h4 {
  font-size: 11px;
  margin-bottom: 10px;
}

.detail-section p {
  font-size: 10px;
  color: var(--muted);
  line-height: 1.8;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 9px;
}

.detail-item img {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid var(--line);
}

.detail-item div {
  flex: 1;
}

.detail-item strong {
  display: block;
  font-size: 10px;
}

.detail-item span {
  font-size: 9px;
  color: var(--muted);
}

.detail-item b {
  font-size: 11px;
}

.amount-line {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: var(--muted);
  padding: 4px 0;
}

.amount-line b {
  color: var(--ink);
}

.amount-line.total {
  border-top: 1px dashed var(--line);
  margin-top: 4px;
  padding-top: 10px;
}

.amount-line.total b {
  font-size: 16px;
  color: var(--brand-deep);
}
</style>
