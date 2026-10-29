<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { afterSaleApi, csApi, orderApi, paymentApi } from '@/api'
import type { AfterSale, CsConversation, Order } from '@/types'
import { dateTime, money, orderStatusType } from '@/utils/format'
import { resolveAssetUrl } from '@/api/http'
import CustomerServicePanel from '@/components/CustomerServicePanel.vue'

const route = useRoute()
const router = useRouter()
const order = ref<Order | null>(null)
const loading = ref(true)
const afterSales = ref<AfterSale[]>([])
const afterSaleDialog = ref(false)
const submittingAfterSale = ref(false)
const afterSaleForm = reactive({ type: 'REFUND', reason: '' })

const payDialog = ref(false)
const paying = ref(false)
const payChannel = ref('ALIPAY')

const canApplyAfterSale = computed(() => {
  if (!order.value || afterSales.value.length) return false
  return ['PAID', 'SHIPPED', 'COMPLETED'].includes(order.value.status)
})

const hasLogistics = computed(() => Boolean(order.value?.trackingNo || order.value?.carrier))
const deliveryLabel = computed(() => (order.value?.deliveryMethod === 'EXPRESS' ? '加急配送' : '标准配送'))
const paymentLabel = computed(() => {
  const map: Record<string, string> = { ALIPAY: '支付宝', WECHAT: '微信支付', CARD: '银行卡', MOCK: '模拟支付' }
  return map[order.value?.paymentChannel || ''] || order.value?.paymentChannel || '—'
})

const steps = computed(() => {
  if (!order.value) return []
  return [
    { label: '提交订单', time: order.value.createdAt, done: true },
    { label: '完成支付', time: order.value.paidAt, done: Boolean(order.value.paidAt) },
    { label: '商家发货', time: order.value.shippedAt, done: Boolean(order.value.shippedAt) },
    { label: '确认收货', time: order.value.finishedAt, done: Boolean(order.value.finishedAt) }
  ]
})

const statusText = computed(() => {
  const status = order.value?.status
  if (status === 'PENDING_PAYMENT') return '订单已提交，请在 30 分钟内完成支付'
  if (status === 'PAID') return '支付成功，商家正在备货'
  if (status === 'SHIPPED') return '包裹已发出，注意查收'
  if (status === 'COMPLETED') return '订单已完成，感谢你的购买'
  if (status === 'CANCELLED') return '订单已取消，库存已释放'
  if (status === 'CLOSED') return '订单已关闭'
  return ''
})

// ---------- 联系卖家 ----------
const csConversation = ref<CsConversation | null>(null)
/** 订单里含卖家商品时才允许联系卖家（平台自营订单没有卖家可找） */
const canContactSeller = computed(() => {
  const items: any[] = (order.value as any)?.items || []
  return items.some((i) => Boolean(i.sellerId))
})

async function openCustomerService() {
  if (!order.value) return
  try {
    csConversation.value = await csApi.openByOrder(order.value.id)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '无法打开客服会话')
  }
}

// ---------- 待支付倒计时 ----------
// 后端下单时会写入 expiredAt，并由定时任务在超时后关闭订单并回滚库存。
// 前端只负责展示剩余时间，避免用户误以为可以无限期挂着不付。
const now = ref(Date.now())
let ticker: ReturnType<typeof setInterval> | null = null

const countdown = computed(() => {
  const o = order.value
  if (!o || o.status !== 'PENDING_PAYMENT' || !o.expiredAt) return ''
  const left = new Date(o.expiredAt).getTime() - now.value
  if (left <= 0) return ''
  const total = Math.floor(left / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const sec = total % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(sec)}` : `${pad(m)}:${pad(sec)}`
})

/** 每秒刷新一次倒计时显示 */
onMounted(() => {
  ticker = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (ticker) clearInterval(ticker)
})

/** 倒计时归零后刷新订单，让状态同步为已关闭 */
watch(countdown, (val, old) => {
  if (old && !val && order.value?.status === 'PENDING_PAYMENT') {
    load()
  }
})

onMounted(load)
watch(() => route.params.id, load)

async function load() {
  loading.value = true
  try {
    order.value = await orderApi.detail(route.params.id as string)
    try {
      afterSales.value = await afterSaleApi.byOrder(order.value.id)
    } catch {
      afterSales.value = []
    }
  } finally {
    loading.value = false
  }
  if (route.query.pay === '1' && order.value?.status === 'PENDING_PAYMENT') {
    openPayment()
    router.replace({ path: route.path, query: {} })
  }
}

function openPayment() {
  payChannel.value = 'ALIPAY'
  payDialog.value = true
}

async function submitPayment() {
  if (!order.value) return
  paying.value = true
  try {
    const payment = await paymentApi.create(order.value.id, payChannel.value)
    await paymentApi.callback(payment.paymentNo, true)
    order.value = await orderApi.detail(order.value.id)
    payDialog.value = false
    ElMessage.success('支付成功，订单已进入备货')
  } finally {
    paying.value = false
  }
}

async function cancel() {
  if (!order.value) return
  await ElMessageBox.confirm('取消后库存会释放，确定取消该订单吗？', '取消订单', {
    confirmButtonText: '确定取消',
    cancelButtonText: '再想想',
    type: 'warning'
  })
  order.value = await orderApi.cancel(order.value.id)
  ElMessage.success('订单已取消')
}

async function confirmReceipt() {
  if (!order.value) return
  await ElMessageBox.confirm('确认已经收到商品？', '确认收货', {
    confirmButtonText: '确认收货',
    cancelButtonText: '取消'
  })
  order.value = await orderApi.confirm(order.value.id)
  ElMessage.success('订单已完成')
}

function openAfterSale() {
  afterSaleForm.type = 'REFUND'
  afterSaleForm.reason = ''
  afterSaleDialog.value = true
}

async function submitAfterSale() {
  if (!order.value) return
  if (afterSaleForm.reason.trim().length < 5) {
    ElMessage.warning('请至少填写 5 个字的售后原因')
    return
  }
  submittingAfterSale.value = true
  try {
    await afterSaleApi.create({
      orderId: order.value.id,
      type: afterSaleForm.type,
      reason: afterSaleForm.reason
    })
    ElMessage.success('售后申请已提交，等待管理员处理')
    afterSaleDialog.value = false
    afterSales.value = await afterSaleApi.byOrder(order.value.id)
  } finally {
    submittingAfterSale.value = false
  }
}

async function copyOrderNo() {
  if (!order.value) return
  try {
    await navigator.clipboard.writeText(order.value.orderNo)
    ElMessage.success('订单号已复制')
  } catch {
    ElMessage.info(order.value.orderNo)
  }
}
</script>

<template>
  <div v-loading="loading" class="order-detail-page">
    <div v-if="order" class="container">
      <div class="back-row">
        <button class="back" @click="router.push('/orders')"><el-icon><ArrowLeft /></el-icon> 返回订单列表</button>
      </div>

      <section class="status-banner">
        <div class="status-icon">
          <el-icon><CircleCheckFilled v-if="order.status === 'COMPLETED'" /><Van v-else-if="order.status === 'SHIPPED'" /><Clock v-else-if="order.status === 'PENDING_PAYMENT'" /><Wallet v-else /></el-icon>
        </div>
        <div class="status-copy">
          <div class="status-line">
            <h1>{{ order.statusLabel }}</h1>
            <el-tag :type="orderStatusType(order.status)" effect="light" round>{{ order.status }}</el-tag>
          </div>
          <p>{{ statusText }}</p>
          <p v-if="order.status === 'PENDING_PAYMENT' && countdown" class="countdown">
            剩余支付时间 <strong>{{ countdown }}</strong>
          </p>
        </div>
        <div class="status-actions">
          <template v-if="order.status === 'PENDING_PAYMENT'">
            <el-button round @click="cancel">取消订单</el-button>
            <el-button type="primary" round @click="openPayment">立即支付</el-button>
          </template>
          <el-button v-else-if="order.status === 'SHIPPED'" type="primary" round @click="confirmReceipt">确认收货</el-button>
          <el-button v-if="canApplyAfterSale" round @click="openAfterSale">申请售后</el-button>
          <el-button v-if="canContactSeller" round @click="openCustomerService">联系卖家</el-button>
        </div>
      </section>

      <!-- 客服会话 -->
      <CustomerServicePanel
        v-if="csConversation"
        :conversation="csConversation"
        class="cs-block"
        @close="csConversation = null"
      />

      <div class="order-detail-grid">
        <div class="main-col">
          <section class="panel">
            <div class="panel-head">
              <h3>物流进度</h3>
              <span class="muted">演示数据</span>
            </div>
            <div class="timeline">
              <div v-for="(step, index) in steps" :key="step.label" class="step" :class="{ done: step.done }">
                <div class="step-rail">
                  <span class="dot"><el-icon v-if="step.done"><Check /></el-icon></span>
                  <span v-if="index < steps.length - 1" class="line"></span>
                </div>
                <div class="step-copy">
                  <strong>{{ step.label }}</strong>
                  <span>{{ step.time ? dateTime(step.time) : '待完成' }}</span>
                </div>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-head">
              <h3>商品清单</h3>
              <span class="muted">{{ order.totalQuantity }} 件</span>
            </div>
            <div class="items">
              <article v-for="item in order.items" :key="item.id" class="item">
                <router-link :to="`/product/${item.productId}`" class="thumb">
                  <img :src="resolveAssetUrl(item.productImage)" :alt="item.productName" />
                </router-link>
                <div class="item-info">
                  <router-link :to="`/product/${item.productId}`" class="item-name">{{ item.productName }}</router-link>
                  <span>{{ money(item.productPrice) }} × {{ item.quantity }}</span>
                </div>
                <strong>{{ money(item.productPrice * item.quantity) }}</strong>
              </article>
            </div>
            <div class="amount-row">
              <span>商品合计</span>
              <strong>{{ money(order.totalAmount - (order.deliveryFee || 0)) }}</strong>
            </div>
            <div class="amount-row">
              <span>配送费（{{ deliveryLabel }}）</span>
              <strong>{{ order.deliveryFee ? money(order.deliveryFee) : '免运费' }}</strong>
            </div>
            <div class="amount-row total">
              <span>实付金额</span>
              <b>{{ money(order.totalAmount) }}</b>
            </div>
          </section>
        </div>

        <aside class="side-col">
          <section class="panel">
            <div class="panel-head"><h3>收货信息</h3></div>
            <div class="info-line"><span>收货人</span><strong>{{ order.receiver }}</strong></div>
            <div class="info-line"><span>联系电话</span><strong>{{ order.phone }}</strong></div>
            <div class="info-line"><span>地址</span><strong class="address">{{ order.addressText }}</strong></div>
            <div class="info-line"><span>备注</span><strong>{{ order.remark || '无' }}</strong></div>
          </section>

          <section class="panel">
            <div class="panel-head"><h3>订单信息</h3></div>
            <div class="info-line">
              <span>订单号</span>
              <strong class="order-no" @click="copyOrderNo">{{ order.orderNo }} <el-icon><CopyDocument /></el-icon></strong>
            </div>
            <div class="info-line"><span>下单时间</span><strong>{{ dateTime(order.createdAt) }}</strong></div>
            <div class="info-line"><span>配送方式</span><strong>{{ deliveryLabel }}</strong></div>
            <div class="info-line"><span>支付时间</span><strong>{{ dateTime(order.paidAt) }}</strong></div>
            <div class="info-line"><span>发货时间</span><strong>{{ dateTime(order.shippedAt) }}</strong></div>
            <div class="info-line"><span>完成时间</span><strong>{{ dateTime(order.finishedAt) }}</strong></div>
          </section>

          <section v-if="order.paymentNo" class="panel">
            <div class="panel-head"><h3>支付信息</h3></div>
            <div class="info-line"><span>支付单号</span><strong class="order-no">{{ order.paymentNo }}</strong></div>
            <div class="info-line"><span>支付方式</span><strong>{{ paymentLabel }}</strong></div>
            <div class="info-line"><span>支付时间</span><strong>{{ dateTime(order.paidAt) }}</strong></div>
          </section>

          <section v-if="hasLogistics" class="panel">
            <div class="panel-head"><h3>物流信息</h3></div>
            <div class="info-line"><span>承运商</span><strong>{{ order.carrier || '—' }}</strong></div>
            <div class="info-line"><span>运单号</span><strong class="order-no">{{ order.trackingNo || '—' }}</strong></div>
          </section>

          <section v-if="afterSales.length" class="panel">
            <div class="panel-head"><h3>售后申请</h3></div>
            <div v-for="item in afterSales" :key="item.id" class="after-sale-item">
              <div class="info-line">
                <span>{{ item.typeLabel }}</span>
                <el-tag :type="item.status === 'APPROVED' ? 'success' : item.status === 'REJECTED' ? 'danger' : 'warning'" size="small" round>
                  {{ item.statusLabel }}
                </el-tag>
              </div>
              <p class="after-sale-reason">{{ item.reason }}</p>
              <p v-if="item.adminNote" class="after-sale-note">处理备注：{{ item.adminNote }}</p>
              <span class="after-sale-time">{{ dateTime(item.createdAt) }}</span>
            </div>
          </section>

          <button class="agent-help" @click="router.push({ name: 'chat', query: { q: `帮我查一下订单 ${order.orderNo} 到哪了` } })">
            <el-icon><ChatDotRound /></el-icon>
            让 Agent 查询这笔订单
          </button>
        </aside>
      </div>
    </div>

    <el-dialog v-model="payDialog" title="模拟支付" width="420px">
      <div class="pay-amount">
        <span>应付金额</span>
        <strong>{{ money(order?.totalAmount) }}</strong>
      </div>
      <p class="after-sale-tip">这是模拟支付：不会产生真实扣款，提交后由系统模拟支付网关回调。</p>
      <el-radio-group v-model="payChannel" class="pay-channels">
        <el-radio-button value="ALIPAY">支付宝</el-radio-button>
        <el-radio-button value="WECHAT">微信支付</el-radio-button>
        <el-radio-button value="CARD">银行卡</el-radio-button>
      </el-radio-group>
      <template #footer>
        <el-button @click="payDialog = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="submitPayment">确认支付</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="afterSaleDialog" title="申请售后" width="460px">
      <p class="after-sale-tip">演示环境支持退款、退货退款、换货申请，提交后由管理员审核，不产生真实退款。</p>
      <el-form label-position="top">
        <el-form-item label="售后类型">
          <el-select v-model="afterSaleForm.type" style="width: 100%">
            <el-option label="仅退款" value="REFUND" />
            <el-option label="退货退款" value="RETURN" />
            <el-option label="换货" value="EXCHANGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="售后原因">
          <el-input v-model="afterSaleForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请描述遇到的问题，例如：商品与描述不符" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="afterSaleDialog = false">取消</el-button>
        <el-button type="primary" :loading="submittingAfterSale" @click="submitAfterSale">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cs-block {
  margin-top: 16px;
  max-width: 560px;
}

.countdown {
  font-size: 12.5px;
  color: var(--brand);
  margin-top: 6px;
}

.countdown strong {
  font-family: var(--font-digit);
  font-size: 15px;
  letter-spacing: 0.04em;
}

.order-detail-page {
  padding: 24px 0 50px;
  min-height: 60vh;
}

.back-row {
  margin-bottom: 16px;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.back:hover {
  color: var(--ink);
}

.status-banner {
  display: flex;
  align-items: center;
  gap: 18px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 24px 26px;
  margin-bottom: 18px;
}

.status-icon {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-deep);
  display: grid;
  place-items: center;
  font-size: 25px;
  flex-shrink: 0;
}

.status-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-line h1 {
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 25px;
  font-weight: 600;
}

.status-copy p {
  font-size: 12px;
  color: var(--muted);
  margin-top: 5px;
}

.status-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.order-detail-grid {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 18px;
  align-items: start;
}

.main-col,
.side-col {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 20px 22px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-head h3 {
  font-size: 14px;
}

.panel-head .muted {
  font-size: 10px;
}

.timeline {
  display: flex;
  gap: 0;
}

.step {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.step-rail {
  display: flex;
  align-items: center;
  width: 100%;
  position: relative;
  height: 22px;
}

.step-rail .dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1px solid var(--line-strong);
  background: var(--surface);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 11px;
  z-index: 1;
  flex-shrink: 0;
}

.step.done .dot {
  background: var(--green);
  border-color: var(--green);
}

.step-rail .line {
  position: absolute;
  top: 11px;
  left: 22px;
  right: 0;
  height: 1px;
  background: var(--line);
}

.step.done .line {
  background: var(--success-tint);
}

.step:last-child .line {
  display: none;
}

.step-copy {
  display: block;
  margin-top: 8px;
  padding-right: 8px;
}

.step-copy strong {
  display: block;
  font-size: 11px;
}

.step-copy span {
  display: block;
  font-size: 9px;
  color: var(--muted);
  margin-top: 3px;
}

.items {
  display: flex;
  flex-direction: column;
}

.item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}

.thumb {
  width: 66px;
  height: 66px;
  border-radius: 9px;
  overflow: hidden;
  border: 1px solid var(--line);
  flex-shrink: 0;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-name {
  font-size: 12.5px;
  font-weight: 600;
}

.item-name:hover {
  color: var(--brand);
}

.item-info span {
  font-size: 10px;
  color: var(--muted);
}

.item strong {
  font-size: 13px;
}

.amount-row {
  display: flex;
  justify-content: flex-end;
  gap: 30px;
  padding: 10px 0 0;
  font-size: 11px;
  color: var(--muted);
}

.amount-row strong {
  min-width: 80px;
  text-align: right;
  color: var(--ink);
  font-weight: 500;
}

.amount-row.total {
  border-top: 1px solid var(--line);
  margin-top: 8px;
  padding-top: 14px;
  align-items: baseline;
}

.amount-row.total b {
  font-size: 22px;
  color: var(--brand-deep);
}

.info-line {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--line);
  font-size: 11px;
}

.info-line:last-child {
  border-bottom: none;
}

.info-line span {
  color: var(--muted);
  flex-shrink: 0;
}

.info-line strong {
  font-weight: 500;
  text-align: right;
  line-height: 1.6;
}

.info-line .address {
  max-width: 210px;
}

.order-no {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--brand-deep);
}

.pay-amount {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  background: var(--surface-sunken);
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 12px;
  font-size: 11px;
  color: var(--muted);
}

.pay-amount strong {
  font-size: 28px;
  color: var(--ink);
}

.pay-channels {
  width: 100%;
  display: flex;
}

.pay-channels :deep(.el-radio-button) {
  flex: 1;
}

.after-sale-item {
  border-bottom: 1px dashed var(--line);
  padding: 8px 0;
}

.after-sale-item:last-child {
  border-bottom: none;
}

.after-sale-reason {
  font-size: 11px;
  color: var(--ink-soft);
  line-height: 1.7;
  margin: 7px 0 4px;
}

.after-sale-note {
  font-size: 10px;
  color: var(--muted);
  line-height: 1.6;
}

.after-sale-time {
  font-size: 9px;
  color: var(--muted-light);
}

.after-sale-tip {
  font-size: 10px;
  line-height: 1.8;
  color: var(--muted);
  margin-bottom: 14px;
}

.agent-help {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px dashed var(--line-strong);
  background: transparent;
  border-radius: 12px;
  padding: 13px;
  font-size: 11px;
  color: var(--muted);
  cursor: pointer;
}

.agent-help:hover {
  border-color: var(--brand);
  color: var(--brand);
}

@media (max-width: 900px) {
  .order-detail-grid {
    grid-template-columns: 1fr;
  }

  .status-banner {
    flex-wrap: wrap;
  }

  .status-actions {
    margin-left: 0;
    width: 100%;
  }

  .timeline {
    flex-direction: column;
    gap: 14px;
  }

  .step {
    flex-direction: row;
    align-items: flex-start;
    gap: 10px;
  }

  .step-rail {
    width: 22px;
    flex: none;
    height: auto;
  }

  .step-rail .line {
    display: none;
  }

  .step-copy {
    margin-top: 0;
  }
}
</style>
