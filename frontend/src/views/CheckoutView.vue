<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { orderApi, productApi, userApi } from '@/api'
import type { Address, Product } from '@/types'
import { money } from '@/utils/format'
import { useCartStore } from '@/stores/cart'
import ProductImage from '@/components/ProductImage.vue'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()

const addresses = ref<Address[]>([])
const selectedAddressId = ref('')
const buyProduct = ref<Product | null>(null)
const buyQuantity = ref(1)
const deliveryMethod = ref('STANDARD')
const remark = ref('')
const loading = ref(true)
const submitting = ref(false)
const addressDialog = ref(false)
const addressSaving = ref(false)
const addressForm = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false
})

const isBuyNow = computed(() => Boolean(route.query.productId))

const items = computed(() => {
  if (isBuyNow.value && buyProduct.value) {
    return [
      {
        productId: buyProduct.value.id,
        name: buyProduct.value.name,
        image: buyProduct.value.image,
        imageFallback: buyProduct.value.imageFallback,
        price: buyProduct.value.price,
        quantity: buyQuantity.value
      }
    ]
  }
  return cart.summary.items
    .filter((item) => item.selected === 1 && item.available)
    .map((item) => ({
      productId: item.productId,
      name: item.name,
      image: item.image,
      imageFallback: item.imageFallback,
      price: item.price,
      quantity: item.quantity
    }))
})

const goodsTotal = computed(() => items.value.reduce((sum, item) => sum + item.price * item.quantity, 0))
const deliveryFee = computed(() => (deliveryMethod.value === 'EXPRESS' ? 12 : 0))
const total = computed(() => goodsTotal.value + deliveryFee.value)
const selectedAddress = computed(() => addresses.value.find((item) => item.id === selectedAddressId.value))

onMounted(load)

async function load() {
  loading.value = true
  try {
    addresses.value = await userApi.addresses()
    const preferred = addresses.value.find((item) => item.isDefault === 1) || addresses.value[0]
    selectedAddressId.value = preferred?.id || ''
    const productId = route.query.productId as string | undefined
    if (productId) {
      buyProduct.value = await productApi.detail(productId)
      buyQuantity.value = Math.max(1, Number(route.query.quantity || 1))
    } else {
      await cart.refresh()
      if (!cart.summary.items.some((item) => item.selected === 1 && item.available)) {
        ElMessage.warning('没有可结算的商品')
        router.replace('/cart')
      }
    }
    if (!addresses.value.length) {
      addressDialog.value = true
    }
  } finally {
    loading.value = false
  }
}

async function saveAddress() {
  if (!addressForm.receiver || !addressForm.phone || !addressForm.province || !addressForm.city || !addressForm.district || !addressForm.detail) {
    ElMessage.warning('请把收货信息填写完整')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(addressForm.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  addressSaving.value = true
  try {
    const created = await userApi.createAddress({ ...addressForm, isDefault: addresses.value.length === 0 || addressForm.isDefault })
    addresses.value = await userApi.addresses()
    selectedAddressId.value = created.id
    addressDialog.value = false
    Object.assign(addressForm, { receiver: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false })
    ElMessage.success('收货地址已添加')
  } finally {
    addressSaving.value = false
  }
}

async function submitOrder() {
  if (!selectedAddressId.value) {
    ElMessage.warning('请选择收货地址')
    addressDialog.value = true
    return
  }
  if (!items.value.length) {
    ElMessage.warning('没有可结算的商品')
    return
  }
  submitting.value = true
  try {
    const order = await orderApi.create({
      productId: isBuyNow.value ? buyProduct.value?.id : undefined,
      quantity: isBuyNow.value ? buyQuantity.value : undefined,
      addressId: selectedAddressId.value,
      deliveryMethod: deliveryMethod.value,
      remark: remark.value
    })
    await cart.refresh()
    ElMessage.success(`订单 ${order.orderNo} 已创建，请完成支付`)
    router.replace({ path: `/order/${order.id}`, query: { pay: '1' } })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="checkout-page">
    <div class="page-head">
      <div class="container">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/cart' }">购物车</el-breadcrumb-item>
          <el-breadcrumb-item>确认订单</el-breadcrumb-item>
        </el-breadcrumb>
        <h1>确认订单</h1>
        <p class="desc">核对收货信息与商品清单，提交后可进行模拟支付。</p>
      </div>
    </div>

    <div class="container checkout-layout">
      <div class="checkout-main">
        <section class="card">
          <div class="card-head">
            <h3>收货地址</h3>
            <button class="text-btn" @click="addressDialog = true">+ 新增地址</button>
          </div>
          <div v-if="addresses.length" class="address-grid">
            <button
              v-for="address in addresses"
              :key="address.id"
              class="address-card"
              :class="{ active: selectedAddressId === address.id }"
              @click="selectedAddressId = address.id"
            >
              <div class="address-top">
                <strong>{{ address.receiver }}</strong>
                <span>{{ address.phone }}</span>
                <span v-if="address.isDefault === 1" class="default-tag">默认</span>
              </div>
              <p>{{ address.province }}{{ address.city }}{{ address.district }}{{ address.detail }}</p>
              <span class="check-dot"><el-icon><Check /></el-icon></span>
            </button>
          </div>
          <div v-else class="no-address">
            <p>还没有收货地址，添加后才能下单。</p>
            <el-button round @click="addressDialog = true">添加地址</el-button>
          </div>
        </section>

        <section class="card">
          <div class="card-head">
            <h3>配送方式</h3>
            <span class="muted">模拟配送，不产生真实物流费用</span>
          </div>
          <div class="delivery-options">
            <button class="delivery-option" :class="{ active: deliveryMethod === 'STANDARD' }" @click="deliveryMethod = 'STANDARD'">
              <el-icon><Van /></el-icon>
              <div><strong>标准配送</strong><span>预计 3-5 天送达 · 免运费</span></div>
              <el-icon v-if="deliveryMethod === 'STANDARD'" class="check"><Check /></el-icon>
            </button>
            <button class="delivery-option" :class="{ active: deliveryMethod === 'EXPRESS' }" @click="deliveryMethod = 'EXPRESS'">
              <el-icon><Promotion /></el-icon>
              <div><strong>加急配送</strong><span>预计 1-2 天送达 · ¥12</span></div>
              <el-icon v-if="deliveryMethod === 'EXPRESS'" class="check"><Check /></el-icon>
            </button>
          </div>
        </section>

        <section class="card">
          <div class="card-head">
            <h3>商品清单</h3>
            <span class="muted">{{ items.length }} 种商品</span>
          </div>
          <div class="checkout-items">
            <article v-for="item in items" :key="item.productId" class="checkout-item">
              <div class="thumb">
                <ProductImage :src="item.image" :fallback-src="item.imageFallback" :alt="item.name" ratio="1 / 1" />
              </div>
              <div class="item-name">
                {{ item.name }}
                <span>数量 × {{ item.quantity }}</span>
              </div>
              <strong>{{ money(item.price * item.quantity) }}</strong>
            </article>
          </div>
        </section>

        <section class="card">
          <div class="card-head"><h3>订单备注</h3></div>
          <el-input v-model="remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选填，例如：工作日白天送达" />
        </section>
      </div>

      <aside class="summary">
        <h3>支付明细</h3>
        <div class="summary-row"><span>商品金额</span><strong>{{ money(goodsTotal) }}</strong></div>
        <div class="summary-row"><span>配送费</span><strong>{{ deliveryFee ? money(deliveryFee) : '免运费' }}</strong></div>
        <div class="summary-row"><span>优惠</span><strong>—</strong></div>
        <div class="summary-total"><span>应付</span><b>{{ money(total) }}</b></div>

        <div v-if="selectedAddress" class="deliver-to">
          <span>配送至</span>
          <p>{{ selectedAddress.receiver }} · {{ selectedAddress.province }}{{ selectedAddress.city }}{{ selectedAddress.district }}{{ selectedAddress.detail }}</p>
        </div>

        <el-button type="primary" size="large" class="submit-btn" :loading="submitting" @click="submitOrder">
          提交订单
        </el-button>
        <p class="pay-tip">提交后将弹出模拟支付确认 · 不产生真实扣款</p>
      </aside>
    </div>

    <el-dialog v-model="addressDialog" title="新增收货地址" width="520px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="收货人">
            <el-input v-model="addressForm.receiver" placeholder="姓名" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="addressForm.phone" placeholder="11 位手机号" maxlength="11" />
          </el-form-item>
          <el-form-item label="省份">
            <el-input v-model="addressForm.province" placeholder="如：江苏省" />
          </el-form-item>
          <el-form-item label="城市">
            <el-input v-model="addressForm.city" placeholder="如：南京市" />
          </el-form-item>
        </div>
        <el-form-item label="区县">
          <el-input v-model="addressForm.district" placeholder="如：栖霞区" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="addressForm.detail" placeholder="街道、门牌号" />
        </el-form-item>
        <el-checkbox v-model="addressForm.isDefault">设为默认地址</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="addressDialog = false">取消</el-button>
        <el-button type="primary" :loading="addressSaving" @click="saveAddress">保存地址</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.checkout-page {
  padding-bottom: 50px;
}

.checkout-layout {
  display: grid;
  grid-template-columns: 1fr 330px;
  gap: 32px;
  padding-top: 30px;
  align-items: start;
}

.checkout-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 20px 22px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.card-head h3 {
  font-size: 15px;
}

.text-btn {
  border: none;
  background: transparent;
  color: var(--brand);
  font-size: 11px;
  cursor: pointer;
}

.address-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.address-card {
  position: relative;
  text-align: left;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.address-card:hover {
  border-color: var(--line-strong);
}

.address-card.active {
  border-color: var(--brand);
  background: #fff8f3;
}

.address-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.address-top strong {
  font-size: 13px;
}

.address-top span {
  color: var(--muted);
}

.default-tag {
  background: var(--brand-soft);
  color: var(--brand-deep) !important;
  font-size: 9px;
  border-radius: 4px;
  padding: 1px 5px;
}

.address-card p {
  margin-top: 8px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--muted);
}

.check-dot {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: none;
  place-items: center;
  font-size: 10px;
}

.address-card.active .check-dot {
  display: grid;
}

.checkout-items {
  display: flex;
  flex-direction: column;
}

.checkout-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}

.checkout-item:last-child {
  border-bottom: none;
}

.thumb {
  width: 64px;
  border-radius: 9px;
  overflow: hidden;
  border: 1px solid var(--line);
}

.item-name {
  flex: 1;
  font-size: 12.5px;
  font-weight: 500;
}

.item-name span {
  display: block;
  color: var(--muted);
  font-size: 10px;
  margin-top: 3px;
}

.checkout-item strong {
  font-size: 13px;
}

.summary {
  position: sticky;
  top: 96px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 22px;
}

.summary h3 {
  font-size: 16px;
  margin-bottom: 16px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--muted);
  padding: 7px 0;
}

.summary-row strong {
  color: var(--ink);
  font-weight: 500;
}

.summary-total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 16px 0;
  margin-top: 8px;
  border-top: 1px solid var(--line);
  font-size: 12px;
}

.summary-total b {
  font-size: 27px;
  letter-spacing: -0.03em;
}

.deliver-to {
  background: #fbf8f1;
  border-radius: 10px;
  padding: 12px;
  font-size: 10px;
  color: var(--muted);
  margin-bottom: 14px;
}

.deliver-to p {
  margin-top: 4px;
  line-height: 1.6;
  color: var(--ink-soft);
}

.submit-btn {
  width: 100%;
  height: 46px;
  border-radius: 10px;
}

.pay-tip {
  text-align: center;
  font-size: 9px;
  color: var(--muted-light);
  margin-top: 10px;
}

.delivery-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.delivery-option {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 11px;
  padding: 13px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.delivery-option:hover {
  border-color: var(--line-strong);
}

.delivery-option.active {
  border-color: var(--brand);
  background: #fff8f3;
}

.delivery-option .el-icon {
  color: var(--brand);
  font-size: 18px;
}

.delivery-option div {
  flex: 1;
}

.delivery-option strong {
  display: block;
  font-size: 11px;
}

.delivery-option span {
  display: block;
  font-size: 9px;
  color: var(--muted);
  margin-top: 3px;
}

.delivery-option .check {
  color: var(--brand);
  font-size: 14px;
}

.no-address {
  padding: 20px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}

@media (max-width: 900px) {
  .checkout-layout {
    grid-template-columns: 1fr;
  }

  .summary {
    position: static;
  }

  .address-grid {
    grid-template-columns: 1fr;
  }
}
</style>
