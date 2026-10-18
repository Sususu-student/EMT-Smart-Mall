<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { RecommendedProduct } from '@/types'
import { money } from '@/utils/format'
import { useCartStore } from '@/stores/cart'
import { useCompareStore } from '@/stores/compare'
import ProductImage from './ProductImage.vue'

const props = defineProps<{ item: RecommendedProduct }>()
const router = useRouter()
const cart = useCartStore()
const compare = useCompareStore()
const adding = ref(false)

const product = computed(() => props.item.product)
const inCompare = computed(() => compare.has(product.value.id))

async function addToCart() {
  adding.value = true
  try {
    await cart.add(product.value.id, 1)
    ElMessage.success('已加入购物车')
  } finally {
    adding.value = false
  }
}

function toggleCompare() {
  if (inCompare.value) {
    compare.remove(product.value.id)
    ElMessage.info('已移出对比清单')
    return
  }
  if (!compare.add(product.value.id)) {
    ElMessage.warning(`最多同时对比 ${compare.max} 件商品`)
    return
  }
  ElMessage.success('已加入对比清单')
}
</script>

<template>
  <article class="agent-product">
    <router-link :to="`/product/${product.id}`" class="thumb">
      <ProductImage :src="product.image" :alt="product.name" ratio="1 / 1" />
    </router-link>

    <div class="content">
      <div class="top">
        <div>
          <span class="category">{{ product.categoryName }} · {{ product.brand }}</span>
          <router-link :to="`/product/${product.id}`" class="name">{{ product.name }}</router-link>
        </div>
        <div class="price-block">
          <strong>{{ money(product.price) }}</strong>
          <span v-if="product.originalPrice" class="price-old">{{ money(product.originalPrice) }}</span>
        </div>
      </div>

      <p class="reason">
        <span class="reason-label">AI 推荐</span>
        {{ item.reason }}
      </p>

      <div class="bottom">
        <div class="highlights">
          <span v-for="highlight in item.highlights.slice(0, 3)" :key="highlight" class="highlight">{{ highlight }}</span>
        </div>
        <div class="actions">
          <button class="act" @click="toggleCompare">
            <el-icon><DataAnalysis /></el-icon>
            {{ inCompare ? '已选对比' : '对比' }}
          </button>
          <button class="act" :disabled="adding" @click="addToCart">
            <el-icon><ShoppingCart /></el-icon>
            加购
          </button>
          <button class="act primary" @click="router.push(`/product/${product.id}`)">查看详情</button>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.agent-product {
  display: flex;
  gap: 14px;
  padding: 12px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 14px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.agent-product:hover {
  border-color: var(--line-strong);
  box-shadow: 0 10px 28px rgba(38, 33, 25, 0.06);
}

.thumb {
  width: 96px;
  flex-shrink: 0;
  border-radius: 10px;
  overflow: hidden;
}

.content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.category {
  display: block;
  font-size: 10px;
  color: var(--muted-light);
  letter-spacing: 0.05em;
  margin-bottom: 2px;
}

.name {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.name:hover {
  color: var(--brand);
}

.price-block {
  text-align: right;
  flex-shrink: 0;
}

.price-block strong {
  font-size: 16px;
  font-weight: 700;
}

.price-block .price-old {
  display: block;
  margin-left: 0;
}

.reason {
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-soft);
  background: #fbf8f1;
  border-radius: 8px;
  padding: 8px 10px;
}

.reason-label {
  display: inline-block;
  font-size: 9px;
  font-weight: 700;
  color: var(--brand-deep);
  background: var(--brand-soft);
  border-radius: 4px;
  padding: 1px 5px;
  margin-right: 5px;
  vertical-align: 1px;
  letter-spacing: 0.04em;
}

.bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.highlights {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}

.highlight {
  font-size: 9px;
  color: var(--muted);
  border: 1px solid var(--line);
  border-radius: 99px;
  padding: 2px 7px;
  white-space: nowrap;
}

.actions {
  display: flex;
  gap: 5px;
  margin-left: auto;
}

.act {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink-soft);
  font-size: 10px;
  border-radius: 6px;
  padding: 5px 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.act:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.act.primary {
  background: var(--ink);
  border-color: var(--ink);
  color: #fff;
}

.act.primary:hover {
  background: var(--brand);
  border-color: var(--brand);
}

.act:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 560px) {
  .thumb {
    width: 74px;
  }

  .reason {
    font-size: 11px;
  }

  .highlights {
    display: none;
  }
}
</style>
