<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCartStore } from '@/stores/cart'
import { useAuthStore } from '@/stores/auth'
import { money } from '@/utils/format'
import ProductImage from '@/components/ProductImage.vue'

const router = useRouter()
const cart = useCartStore()
const auth = useAuthStore()

const items = computed(() => cart.summary.items)
const allSelected = computed(() => items.value.length > 0 && items.value.every((item) => item.selected === 1))
const selectedItems = computed(() => items.value.filter((item) => item.selected === 1 && item.available))

onMounted(() => {
  if (auth.isLoggedIn) cart.refresh()
})

async function toggleAll(value: boolean) {
  await cart.select(null, value)
}

async function toggleItem(id: number, value: boolean) {
  await cart.select([id], value)
}

async function updateQuantity(id: number, value: number | undefined) {
  if (!value) return
  await cart.update(id, value)
}

async function removeItem(id: number, name: string) {
  await ElMessageBox.confirm(`确定从购物车移除「${name}」吗？`, '移除商品', {
    confirmButtonText: '移除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await cart.remove(id)
  ElMessage.success('已移除')
}

function checkout() {
  if (!selectedItems.value.length) {
    ElMessage.warning('请先选择要结算的商品')
    return
  }
  router.push('/checkout')
}
</script>

<template>
  <div class="cart-page">
    <div class="page-head">
      <div class="container">
        <span class="eyebrow">YOUR BAG</span>
        <h1>购物车</h1>
        <p class="desc">确认数量与规格后结算。库存与价格会在下单时再次校验。</p>
      </div>
    </div>

    <div v-if="!auth.isLoggedIn" class="container">
      <div class="empty">
        <strong>登录后查看购物车</strong>
        <p>登录即可同步购物车、收藏和订单。</p>
        <el-button type="primary" round style="margin-top: 18px" @click="router.push('/login')">去登录</el-button>
      </div>
    </div>

    <div v-else-if="!items.length" class="container">
      <div class="empty">
        <strong>购物车还是空的</strong>
        <p>去逛逛，或者让 Agent 根据你的需求推荐几件。</p>
        <div class="empty-actions">
          <el-button type="primary" round @click="router.push('/products')">去逛商品</el-button>
          <el-button round @click="router.push('/chat')">让 Agent 推荐</el-button>
        </div>
      </div>
    </div>

    <div v-else class="container cart-layout">
      <div class="cart-list">
        <div class="cart-head">
          <el-checkbox :model-value="allSelected" @change="toggleAll(Boolean($event))">全选</el-checkbox>
          <span class="muted">共 {{ items.length }} 种商品</span>
        </div>

        <article v-for="item in items" :key="item.id" class="cart-item" :class="{ disabled: !item.available }">
          <el-checkbox
            :model-value="item.selected === 1"
            :disabled="!item.available"
            @change="toggleItem(item.id, Boolean($event))"
          />
          <router-link :to="`/product/${item.productId}`" class="thumb">
            <ProductImage :src="item.image" :fallback-src="item.imageFallback" :alt="item.name" ratio="1 / 1" />
          </router-link>
          <div class="item-info">
            <router-link :to="`/product/${item.productId}`" class="item-name">{{ item.name }}</router-link>
            <span v-if="!item.available" class="unavailable">商品已下架或库存不足</span>
            <span v-else class="stock">库存 {{ item.stock }} 件</span>
          </div>
          <div class="unit-price">
            <strong>{{ money(item.price) }}</strong>
            <span v-if="item.originalPrice" class="price-old">{{ money(item.originalPrice) }}</span>
          </div>
          <el-input-number
            :model-value="item.quantity"
            :min="1"
            :max="Math.max(1, item.stock)"
            size="small"
            :disabled="!item.available"
            @change="updateQuantity(item.id, $event as number | undefined)"
          />
          <div class="subtotal">{{ money(item.subtotal) }}</div>
          <button class="remove" aria-label="移除商品" @click="removeItem(item.id, item.name)">
            <el-icon><Delete /></el-icon>
          </button>
        </article>
      </div>

      <aside class="summary">
        <h3>订单摘要</h3>
        <div class="summary-row">
          <span>已选商品</span>
          <strong>{{ cart.summary.selectedCount }} 件</strong>
        </div>
        <div class="summary-row">
          <span>商品合计</span>
          <strong>{{ money(cart.summary.totalAmount) }}</strong>
        </div>
        <div v-if="cart.summary.savedAmount > 0" class="summary-row save">
          <span>已优惠</span>
          <strong>-{{ money(cart.summary.savedAmount) }}</strong>
        </div>
        <div class="summary-row">
          <span>运费</span>
          <strong>免运费</strong>
        </div>
        <div class="summary-total">
          <span>应付总额</span>
          <b>{{ money(cart.summary.totalAmount) }}</b>
        </div>
        <el-button type="primary" size="large" class="checkout-btn" :disabled="!selectedItems.length" @click="checkout">
          去结算（{{ cart.summary.selectedCount }}）
        </el-button>
        <button class="continue" @click="router.push('/products')">继续购物</button>
        <div class="summary-tip">
          <el-icon><Lock /></el-icon>
          <span>演示环境，不产生真实扣款</span>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.cart-page {
  padding-bottom: 50px;
}

.empty-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 18px;
}

.cart-layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 32px;
  padding-top: 30px;
  align-items: start;
}

.cart-list {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 6px 20px;
}

.cart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 2px;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}

.cart-item {
  display: grid;
  grid-template-columns: 24px 84px 1fr 90px 116px 90px 30px;
  align-items: center;
  gap: 14px;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
}

.cart-item:last-child {
  border-bottom: none;
}

.cart-item.disabled {
  opacity: 0.6;
}

.thumb {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--line);
}

.item-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.item-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-name:hover {
  color: var(--brand);
}

.stock,
.unavailable {
  font-size: 10px;
  color: var(--muted);
}

.unavailable {
  color: #d64545;
}

.unit-price {
  display: flex;
  flex-direction: column;
  font-size: 12px;
}

.unit-price strong {
  font-weight: 600;
}

.unit-price .price-old {
  margin-left: 0;
  font-size: 10px;
}

.subtotal {
  text-align: right;
  font-weight: 700;
  font-size: 14px;
}

.remove {
  border: none;
  background: transparent;
  color: var(--muted-light);
  cursor: pointer;
  font-size: 15px;
  padding: 6px;
}

.remove:hover {
  color: #d64545;
}

.summary {
  position: sticky;
  top: 96px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 22px;
}

.summary h3 {
  font-size: 16px;
  margin-bottom: 18px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--muted);
  padding: 8px 0;
}

.summary-row strong {
  color: var(--ink);
  font-weight: 500;
}

.summary-row.save strong {
  color: var(--green);
}

.summary-total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 16px 0;
  margin-top: 6px;
  border-top: 1px solid var(--line);
  font-size: 12px;
}

.summary-total b {
  font-size: 26px;
  letter-spacing: -0.03em;
}

.checkout-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
}

.continue {
  width: 100%;
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
  padding: 12px 0 4px;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.summary-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin-top: 12px;
  font-size: 10px;
  color: var(--muted-light);
}

@media (max-width: 1000px) {
  .cart-layout {
    grid-template-columns: 1fr;
  }

  .summary {
    position: static;
  }
}

@media (max-width: 760px) {
  .cart-item {
    grid-template-columns: 22px 68px 1fr 30px;
    gap: 10px;
  }

  .unit-price,
  .cart-item :deep(.el-input-number) {
    display: none;
  }

  .subtotal {
    grid-column: 3;
    text-align: left;
    font-size: 13px;
  }

  .remove {
    grid-column: 4;
    grid-row: 1;
  }
}
</style>
