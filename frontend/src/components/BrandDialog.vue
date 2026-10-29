<script setup lang="ts">
/**
 * 品牌档案弹层：展示品牌故事、定位与在售商品。
 * 从首页品牌条点击进入，不跳转页面，保留首页上下文。
 */
import { computed, ref, watch } from 'vue'
import { brandApi, productApi } from '@/api'
import type { Brand, Product } from '@/types'
import ProductCard from './ProductCard.vue'
import ProductCardSkeleton from './ProductCardSkeleton.vue'

const props = defineProps<{ modelValue: boolean; brand: Brand | null }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const products = ref<Product[]>([])
const loading = ref(false)
const total = ref(0)

watch(
  () => [props.modelValue, props.brand?.name] as const,
  async ([open, name]) => {
    if (!open || !name) return
    loading.value = true
    products.value = []
    try {
      const result = await productApi.list({ brand: name, page: 1, size: 8 })
      products.value = result.records
      total.value = result.total
    } catch {
      products.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)

const highlights = computed(() =>
  (props.brand?.highlight || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean)
)

function viewAll() {
  if (!props.brand) return
  visible.value = false
  // 交给父组件处理跳转，这里只关闭弹层
  window.location.assign(`/products?brand=${encodeURIComponent(props.brand.name)}`)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="880px"
    :show-close="true"
    class="brand-dialog"
    align-center
  >
    <template #header>
      <div v-if="brand" class="brand-head">
        <div class="brand-mark" :style="{ background: brand.accent || 'var(--brand)' }">
          {{ brand.logoText || brand.name.slice(0, 1) }}
        </div>
        <div class="brand-title">
          <strong>{{ brand.name }}</strong>
          <span>{{ brand.slogan }}</span>
        </div>
      </div>
    </template>

    <div v-if="brand" class="brand-body">
      <div class="brand-meta">
        <div v-if="brand.positioning">
          <span>定位</span><strong>{{ brand.positioning }}</strong>
        </div>
        <div v-if="brand.origin">
          <span>产地</span><strong>{{ brand.origin }}</strong>
        </div>
        <div v-if="brand.founded">
          <span>创立</span><strong>{{ brand.founded }}</strong>
        </div>
        <div>
          <span>在售</span><strong>{{ brand.productCount }} 件</strong>
        </div>
      </div>

      <p class="brand-intro">{{ brand.intro }}</p>

      <div v-if="highlights.length" class="brand-tags">
        <el-tag v-for="tag in highlights" :key="tag" size="small" round effect="plain">{{ tag }}</el-tag>
      </div>

      <div v-if="brand.story" class="brand-story">
        <h4>品牌故事</h4>
        <p>{{ brand.story }}</p>
      </div>

      <div class="brand-products">
        <div class="bp-head">
          <h4>该品牌商品</h4>
          <button v-if="total > products.length" class="bp-more" type="button" @click="viewAll">
            查看全部 {{ total }} 件
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>

        <ProductCardSkeleton v-if="loading" :count="4" ratio="4 / 3" />
        <div v-else-if="products.length" class="bp-grid">
          <ProductCard v-for="p in products" :key="p.id" :product="p" />
        </div>
        <div v-else class="bp-empty">该品牌暂无在售商品</div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.brand-head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 700;
  flex-shrink: 0;
}

.brand-title strong {
  display: block;
  font-family: var(--font-serif);
  font-size: 19px;
  color: var(--ink);
  line-height: 1.3;
}

.brand-title span {
  font-size: 12px;
  color: var(--muted);
}

.brand-body {
  padding-top: 4px;
}

.brand-meta {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  background: var(--surface-sunken);
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 18px;
}

.brand-meta div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.brand-meta span {
  font-size: 10px;
  color: var(--muted);
  letter-spacing: 0.08em;
}

.brand-meta strong {
  font-size: 13px;
  color: var(--ink);
  font-weight: 600;
}

.brand-intro {
  font-size: 13px;
  line-height: 1.9;
  color: var(--ink-soft);
  margin-bottom: 14px;
}

.brand-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 20px;
}

.brand-story {
  border-left: 3px solid var(--brand);
  padding: 2px 0 2px 14px;
  margin-bottom: 24px;
}

.brand-story h4,
.brand-products h4 {
  font-size: 12px;
  font-weight: 700;
  color: var(--muted);
  letter-spacing: 0.1em;
  margin-bottom: 8px;
}

.brand-story p {
  font-size: 13px;
  line-height: 1.95;
  color: var(--ink-soft);
}

.bp-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.bp-more {
  border: none;
  background: none;
  color: var(--brand);
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.bp-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.bp-empty {
  text-align: center;
  padding: 30px 0;
  font-size: 13px;
  color: var(--muted);
}

@media (max-width: 900px) {
  .brand-meta {
    grid-template-columns: repeat(2, 1fr);
  }

  .bp-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>