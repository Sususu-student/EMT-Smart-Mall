<script setup lang="ts">
/**
 * 商品卡骨架屏：加载中占位，避免白屏和布局跳动。
 * 结构与 ProductCard 对齐（图片区 + 两行文字 + 价格行），加载完成后无缝替换。
 */
withDefaults(
  defineProps<{
    /** 骨架块数量 */
    count?: number
    /** 图片区宽高比，与真实卡片保持一致 */
    ratio?: string
  }>(),
  { count: 8, ratio: '4 / 3' }
)
</script>

<template>
  <div class="skeleton-grid" aria-hidden="true">
    <div v-for="i in count" :key="i" class="skeleton-card">
      <div class="sk-media" :style="{ aspectRatio: ratio }" />
      <div class="sk-body">
        <div class="sk-line w40" />
        <div class="sk-line w85" />
        <div class="sk-line w60" />
        <div class="sk-foot">
          <div class="sk-line w30 sk-price" />
          <div class="sk-dot" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.skeleton-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.sk-media {
  width: 100%;
  background: var(--surface-sunken);
}

.sk-body {
  padding: 14px 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

/* 流光动画：从左到右扫过，比单纯闪烁更柔和 */
.sk-line,
.sk-media,
.sk-dot {
  position: relative;
  overflow: hidden;
  border-radius: 6px;
  background: var(--surface-sunken);
}

.sk-line::after,
.sk-dot::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.55),
    transparent
  );
  animation: sk-shine 1.5s ease-in-out infinite;
}

.sk-media::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.35),
    transparent
  );
  animation: sk-shine 1.6s ease-in-out infinite;
}

html.dark .sk-line::after,
html.dark .sk-dot::after,
html.dark .sk-media::after {
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.08),
    transparent
  );
}

.sk-line {
  height: 11px;
}

.sk-price {
  height: 18px;
}

.sk-dot {
  width: 32px;
  height: 32px;
  border-radius: 50%;
}

.w30 { width: 30%; }
.w40 { width: 40%; }
.w60 { width: 60%; }
.w85 { width: 85%; }

.sk-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

@keyframes sk-shine {
  100% {
    transform: translateX(100%);
  }
}

@media (max-width: 1200px) {
  .skeleton-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .skeleton-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;
  }
}

@media (max-width: 560px) {
  .skeleton-grid {
    grid-template-columns: 1fr;
  }
}

/* 尊重降低动态效果偏好 */
@media (prefers-reduced-motion: reduce) {
  .sk-line::after,
  .sk-dot::after,
  .sk-media::after {
    animation: none;
  }
}
</style>