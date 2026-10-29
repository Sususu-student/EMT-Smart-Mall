<script setup lang="ts">
/**
 * 场景搭配方案卡片：在聊天流里展示 AI 组合出的一整套商品。
 * 与单件推荐卡的区别是一次给出完整方案，并用一句话说明「为什么这样搭」。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { ElMessage } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import type { ScenePlan } from '@/types'

const props = defineProps<{ plan: ScenePlan }>()
const router = useRouter()
const cart = useCartStore()

const itemCount = computed(() => props.plan.items?.length ?? 0)

const money = (v: number | string) =>
  '¥' + Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })

async function addAll() {
  if (!props.plan.items?.length) return
  let ok = 0
  for (const item of props.plan.items) {
    try {
      await cart.add(item.productId, 1)
      ok++
    } catch {
      // 单件失败不阻断其余商品
    }
  }
  if (ok === 0) {
    ElMessage.error('加购失败，请稍后重试')
  } else if (ok < props.plan.items.length) {
    ElMessage.warning(`已加入 ${ok} 件，${props.plan.items.length - ok} 件加购失败`)
  } else {
    ElMessage.success('整套方案已加入购物车')
  }
}

function goProduct(id: string) {
  router.push(`/product/${id}`)
}
</script>

<template>
  <div class="scene-plan">
    <header class="sp-head">
      <div class="sp-title">
        <span class="sp-badge">{{ plan.sceneName }}</span>
        <strong>为你搭配了 {{ itemCount }} 件</strong>
      </div>
      <div class="sp-total">
        <span>合计</span>
        <strong>{{ money(plan.total) }}</strong>
        <small v-if="plan.withinBudget">预算 {{ money(plan.budget) }} · 未超支</small>
        <small v-else class="over">超出预算 {{ money(Number(plan.total) - Number(plan.budget)) }}</small>
      </div>
    </header>

    <div class="sp-items">
      <article
        v-for="item in plan.items"
        :key="item.productId"
        class="sp-item"
        @click="goProduct(item.productId)"
      >
        <div class="sp-thumb">
          <ProductImage
            :src="item.image || item.imageFallback || ''"
            :fallback-src="item.imageFallback || ''"
            :alt="item.name"
          />
          <span class="sp-role">{{ item.role }}</span>
        </div>
        <p class="sp-name">{{ item.name }}</p>
        <span class="sp-price">{{ money(item.price) }}</span>
      </article>
    </div>

    <div v-if="plan.reason" class="sp-reason">
      <el-icon><Sunny /></el-icon>
      <p>{{ plan.reason }}</p>
    </div>

    <footer class="sp-foot">
      <el-button type="primary" round @click="addAll">
        <el-icon><ShoppingCart /></el-icon>
        一键加购整套
      </el-button>
      <el-button round @click="router.push('/products')">看看其他商品</el-button>
    </footer>
  </div>
</template>

<style scoped>
.scene-plan {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 18px 20px;
  box-shadow: var(--shadow-card);
  margin-top: 10px;
}

.sp-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--line);
}

.sp-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.sp-badge {
  background: var(--brand);
  color: #fff;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 99px;
}

.sp-title strong {
  font-size: 13.5px;
  color: var(--ink);
}

.sp-total {
  text-align: right;
  flex-shrink: 0;
}

.sp-total > span {
  font-size: 10px;
  color: var(--muted-light);
  margin-right: 6px;
}

.sp-total strong {
  font-family: var(--font-digit);
  font-size: 20px;
  color: var(--brand);
}

.sp-total small {
  display: block;
  font-size: 10px;
  color: var(--muted-light);
  margin-top: 2px;
}

.sp-total small.over {
  color: #c0504d;
}

.sp-items {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 12px;
  padding: 16px 0;
}

.sp-item {
  cursor: pointer;
  transition: transform 0.2s ease;
}

.sp-item:hover {
  transform: translateY(-2px);
}

.sp-thumb {
  position: relative;
  aspect-ratio: 1;
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface-sunken);
  margin-bottom: 7px;
}

.sp-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sp-role {
  position: absolute;
  left: 6px;
  bottom: 6px;
  background: rgba(20, 16, 12, 0.72);
  color: #fff;
  font-size: 9px;
  padding: 2px 7px;
  border-radius: 99px;
  backdrop-filter: blur(2px);
}

.sp-name {
  font-size: 11.5px;
  color: var(--ink-soft);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 3px;
}

.sp-price {
  font-size: 12px;
  font-family: var(--font-digit);
  color: var(--ink);
  font-weight: 600;
}

.sp-reason {
  display: flex;
  gap: 9px;
  padding: 12px 14px;
  background: var(--surface-sunken);
  border-radius: 10px;
  margin-bottom: 14px;
}

.sp-reason .el-icon {
  color: var(--brand);
  flex-shrink: 0;
  margin-top: 2px;
}

.sp-reason p {
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--ink-soft);
}

.sp-foot {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 600px) {
  .sp-head {
    flex-direction: column;
  }

  .sp-total {
    text-align: left;
  }
}
</style>