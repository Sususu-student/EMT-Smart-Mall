<script setup lang="ts">
/**
 * 我的售后：汇总当前用户提交的全部售后申请。
 * 此前售后入口只藏在订单详情里，用户想查进度必须逐单翻找，这里补一个统一列表。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { afterSaleApi } from '@/api'
import type { AfterSale } from '@/types'
import { dateTime, money } from '@/utils/format'

const router = useRouter()
const loading = ref(true)
const list = ref<AfterSale[]>([])
const filter = ref('ALL')

const FILTERS = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '处理中' },
  { value: 'APPROVED', label: '已同意' },
  { value: 'REJECTED', label: '已拒绝' }
]

const filtered = computed(() =>
  filter.value === 'ALL' ? list.value : list.value.filter((i) => i.status === filter.value)
)

const counts = computed(() => ({
  ALL: list.value.length,
  PENDING: list.value.filter((i) => i.status === 'PENDING').length,
  APPROVED: list.value.filter((i) => i.status === 'APPROVED').length,
  REJECTED: list.value.filter((i) => i.status === 'REJECTED').length
}))

async function load() {
  loading.value = true
  try {
    list.value = await afterSaleApi.mine()
  } catch {
    ElMessage.error('售后记录加载失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

const statusTone: Record<string, string> = {
  PENDING: 'pending',
  APPROVED: 'done',
  REJECTED: 'rejected'
}
</script>

<template>
  <div v-loading="loading" class="aftersale-page">
    <div class="page-intro">
      <p>这里汇总你提交的全部售后申请，可随时查看处理进度。</p>
      <el-button round size="small" @click="load">刷新</el-button>
    </div>

    <div class="filter-row">
      <button
        v-for="f in FILTERS"
        :key="f.value"
        type="button"
        class="chip"
        :class="{ active: filter === f.value }"
        @click="filter = f.value"
      >
        {{ f.label }}
        <span v-if="counts[f.value as keyof typeof counts]" class="badge">{{ counts[f.value as keyof typeof counts] }}</span>
      </button>
    </div>

    <div v-if="filtered.length" class="after-list">
      <article v-for="item in filtered" :key="item.id" class="after-card">
        <header class="ac-head">
          <div class="ac-title">
            <strong>{{ item.typeLabel }}</strong>
            <span class="order-no" @click="router.push(`/order/${item.orderId}`)">
              订单 {{ item.orderNo }}
            </span>
          </div>
          <span class="status-tag" :class="statusTone[item.status] || 'pending'">
            {{ item.statusLabel }}
          </span>
        </header>

        <div class="ac-body">
          <div class="ac-row">
            <span>申请原因</span>
            <p>{{ item.reason }}</p>
          </div>
          <div v-if="item.adminNote" class="ac-row note">
            <span>处理说明</span>
            <p>{{ item.adminNote }}</p>
          </div>
        </div>

        <footer class="ac-foot">
          <span>申请时间 {{ dateTime(item.createdAt) }}</span>
          <span v-if="item.handledAt">处理时间 {{ dateTime(item.handledAt) }}</span>
          <span v-if="item.orderAmount" class="amount">订单金额 {{ money(item.orderAmount) }}</span>
        </footer>
      </article>
    </div>

    <div v-else-if="!loading" class="empty">
      <strong>{{ filter === 'ALL' ? '还没有售后申请' : '该状态下暂无记录' }}</strong>
      <p>如果收到的商品有问题，可以在订单详情页提交售后申请。</p>
      <el-button round type="primary" style="margin-top: 16px" @click="router.push('/orders')">
        去我的订单
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.aftersale-page {
  min-height: 360px;
}

.page-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.page-intro p {
  font-size: 13px;
  color: var(--muted);
}

.filter-row {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink-soft);
  font-size: 12px;
  padding: 7px 14px;
  border-radius: 99px;
  cursor: pointer;
  transition: all 0.2s;
}

.chip:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.chip.active {
  background: var(--ink);
  border-color: var(--ink);
  color: var(--on-ink);
}

.badge {
  font-size: 10px;
  background: var(--brand);
  color: #fff;
  border-radius: 99px;
  padding: 1px 6px;
  min-width: 16px;
  text-align: center;
}

.chip.active .badge {
  background: var(--brand);
}

.after-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.after-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: var(--shadow-card);
  transition: border-color 0.2s, transform 0.2s;
}

.after-card:hover {
  border-color: var(--brand-soft, var(--line));
  transform: translateY(-1px);
}

.ac-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--line);
}

.ac-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.ac-title strong {
  font-size: 14px;
  color: var(--ink);
}

.order-no {
  font-size: 11px;
  color: var(--muted-light);
  cursor: pointer;
  transition: color 0.2s;
}

.order-no:hover {
  color: var(--brand);
}

.status-tag {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 99px;
  flex-shrink: 0;
}

.status-tag.pending {
  background: rgba(201, 162, 39, 0.14);
  color: #9a7b1f;
}

.status-tag.done {
  background: rgba(47, 122, 92, 0.14);
  color: var(--green);
}

.status-tag.rejected {
  background: rgba(192, 80, 77, 0.12);
  color: #c0504d;
}

.ac-body {
  padding: 12px 0 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ac-row {
  display: flex;
  gap: 12px;
  font-size: 12.5px;
}

.ac-row > span {
  color: var(--muted-light);
  flex-shrink: 0;
  width: 56px;
}

.ac-row p {
  color: var(--ink-soft);
  line-height: 1.7;
  flex: 1;
}

.ac-row.note p {
  color: var(--green);
}

.ac-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px dashed var(--line);
  font-size: 11px;
  color: var(--muted-light);
}

.amount {
  margin-left: auto;
  color: var(--ink-soft);
  font-family: var(--font-digit);
}

.empty {
  text-align: center;
  padding: 70px 20px;
  background: var(--surface);
  border: 1px dashed var(--line);
  border-radius: 14px;
}

.empty strong {
  display: block;
  font-size: 15px;
  color: var(--ink);
  margin-bottom: 8px;
}

.empty p {
  font-size: 12.5px;
  color: var(--muted-light);
}

@media (max-width: 700px) {
  .ac-foot {
    flex-direction: column;
    gap: 6px;
  }

  .amount {
    margin-left: 0;
  }
}
</style>