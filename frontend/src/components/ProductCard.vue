<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { Product } from '@/types'
import { money } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useCompareStore } from '@/stores/compare'
import ProductImage from './ProductImage.vue'

const props = withDefaults(defineProps<{ product: Product; compact?: boolean }>(), { compact: false })

const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const compare = useCompareStore()
const adding = ref(false)

const favored = ref(Boolean(props.product.favorited))
const inCompare = computed(() => compare.has(props.product.id))
const discount = computed(() => {
  const original = Number(props.product.originalPrice || 0)
  if (!original || original <= props.product.price) return 0
  return Math.round((1 - props.product.price / original) * 100)
})

async function addToCart() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: `/product/${props.product.id}` } })
    return
  }
  adding.value = true
  try {
    await cart.add(props.product.id, 1)
    ElMessage.success('已加入购物车')
  } finally {
    adding.value = false
  }
}

async function toggleFavorite() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: `/product/${props.product.id}` } })
    return
  }
  const { userApi } = await import('@/api')
  const added = await userApi.toggleFavorite(props.product.id)
  favored.value = added
  ElMessage.success(added ? '已收藏' : '已取消收藏')
}

function toggleCompare() {
  if (inCompare.value) {
    compare.remove(props.product.id)
    ElMessage.info('已移出对比清单')
    return
  }
  if (!compare.add(props.product.id)) {
    ElMessage.warning(`最多同时对比 ${compare.max} 件商品`)
    return
  }
  ElMessage.success('已加入对比清单')
}
</script>

<template>
  <article class="product-card" :class="{ compact }">
    <div class="media">
      <router-link :to="`/product/${product.id}`" class="media-link">
        <ProductImage :src="product.image" :alt="product.name" ratio="4 / 5" />
      </router-link>
      <span v-if="discount" class="discount">-{{ discount }}%</span>
      <button class="fav" :class="{ active: favored }" :aria-label="favored ? '取消收藏' : '收藏'" @click.stop="toggleFavorite">
        <el-icon><StarFilled v-if="favored" /><Star v-else /></el-icon>
      </button>
      <div class="hover-actions">
        <button class="mini-btn" @click.stop="addToCart">
          <el-icon><ShoppingCart /></el-icon>
          加购
        </button>
        <button class="mini-btn" :class="{ active: inCompare }" @click.stop="toggleCompare">
          <el-icon><DataAnalysis /></el-icon>
          {{ inCompare ? '已选' : '对比' }}
        </button>
      </div>
    </div>

    <div class="body">
      <div class="meta">
        <span>{{ product.categoryName || product.brand || '智选' }}</span>
        <span>{{ product.sales }} 人已买</span>
      </div>
      <router-link :to="`/product/${product.id}`" class="name">{{ product.name }}</router-link>
      <p v-if="!compact" class="subtitle">{{ product.subtitle }}</p>
      <div class="rating">
        <el-rate :model-value="Number(product.rating)" disabled size="small" />
        <span>{{ Number(product.rating).toFixed(1) }} · {{ product.reviewCount }} 条评价</span>
      </div>
      <div class="footer">
        <div class="price">
          <span class="price-strong">{{ money(product.price) }}</span>
          <span v-if="product.originalPrice" class="price-old">{{ money(product.originalPrice) }}</span>
        </div>
        <button class="add-btn" :disabled="adding || product.stock <= 0" aria-label="加入购物车" @click.stop="addToCart">
          <el-icon><Plus /></el-icon>
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.product-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition: transform 0.28s cubic-bezier(0.22, 0.61, 0.36, 1), box-shadow 0.28s ease, border-color 0.28s ease;
  display: flex;
  flex-direction: column;
}

@media (hover: hover) {
  .product-card:hover {
    transform: translateY(-6px);
    box-shadow: var(--shadow-card-hover);
    border-color: var(--line-strong);
  }

  .product-card:hover .media :deep(img) {
    transform: scale(1.05);
  }

  .product-card:hover .hover-actions {
    transform: translateY(0);
    opacity: 1;
  }
}

.media {
  position: relative;
  overflow: hidden;
  background: #f2ede3;
}

.media-link {
  display: block;
}

.discount {
  position: absolute;
  top: 12px;
  left: 12px;
  background: var(--brand);
  color: #fff;
  font-family: var(--font-digit);
  font-variant-numeric: tabular-nums;
  font-size: 11px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 6px;
  letter-spacing: 0.03em;
}

.fav {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(4px);
  color: var(--ink-soft);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: color 0.2s, background 0.2s;
}

.fav:hover,
.fav.active {
  color: var(--brand);
  background: #fff;
}

.hover-actions {
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 10px;
  display: flex;
  gap: 6px;
  transform: translateY(10px);
  opacity: 0;
  transition: transform 0.26s ease, opacity 0.26s ease;
}

/* 悬浮操作条下方加深色渐变，避免盖住图片上的文字 */
.media::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 76px;
  background: linear-gradient(180deg, rgba(27, 26, 23, 0) 0%, rgba(27, 26, 23, 0.34) 100%);
  opacity: 0;
  transition: opacity 0.26s ease;
  pointer-events: none;
}

@media (hover: hover) {
  .product-card:hover .media::after {
    opacity: 1;
  }
}

.mini-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: none;
  background: rgba(27, 26, 23, 0.88);
  color: #fff;
  backdrop-filter: blur(6px);
  border-radius: 8px;
  padding: 8px 0;
  font-size: 11px;
  cursor: pointer;
  transition: background 0.2s;
}

.mini-btn:hover {
  background: var(--brand);
}

.mini-btn.active {
  background: var(--brand);
}

.body {
  padding: 14px 15px 15px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  flex: 1;
}

.meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-family: var(--font-digit);
  font-variant-numeric: tabular-nums;
  font-size: 10px;
  color: var(--muted-light);
  letter-spacing: 0.04em;
}

.meta > span:first-child {
  font-family: var(--font-sans);
  letter-spacing: 0.02em;
}

.name {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
  transition: color 0.2s;
}

.name:hover {
  color: var(--brand);
}

.subtitle {
  font-size: 11px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rating {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  color: var(--muted-light);
}

.rating :deep(.el-rate) {
  height: 14px;
  --el-rate-icon-size: 12px;
  --el-rate-icon-margin: 0;
}

.rating :deep(.el-rate__item) {
  margin-right: 1px;
}

.footer {
  margin-top: auto;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-top: 5px;
}

.price-strong {
  font-size: 18px;
}

.add-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--line);
  background: #fff;
  cursor: pointer;
  display: grid;
  place-items: center;
  color: var(--ink);
  transition: all 0.2s;
}

.add-btn:hover:not(:disabled) {
  background: var(--ink);
  border-color: var(--ink);
  color: #fff;
}

.add-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.compact .body {
  padding: 12px;
}

.compact .name {
  font-size: 13px;
  min-height: 38px;
}
</style>
