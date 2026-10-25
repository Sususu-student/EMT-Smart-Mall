<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    src?: string
    alt?: string
    ratio?: string
    /** 远程图加载失败时回退的本地图片，保证断网/被墙时不破图 */
    fallbackSrc?: string
  }>(),
  { src: '/img/default.svg', alt: '商品图片', ratio: '1 / 1', fallbackSrc: '' }
)

/** 0=主图 1=本地兜底 2=纯色占位 */
const stage = ref(0)

watch(
  () => props.src,
  () => {
    stage.value = 0
  }
)

const currentSrc = ref('')

watch(
  () => [props.src, props.fallbackSrc, stage.value] as const,
  () => {
    currentSrc.value = stage.value === 0 ? props.src || '' : stage.value === 1 ? props.fallbackSrc || '' : ''
  },
  { immediate: true }
)

function onError() {
  // 先退回本地缓存图，再退回纯色占位
  if (stage.value === 0 && props.fallbackSrc) {
    stage.value = 1
    return
  }
  stage.value = 2
}
</script>

<template>
  <div class="product-image" :style="{ aspectRatio: ratio }">
    <img
      v-if="stage < 2 && currentSrc"
      :src="currentSrc"
      :alt="alt"
      loading="lazy"
      @error="onError"
    />
    <div v-else class="fallback">
      <span>智选</span>
    </div>
  </div>
</template>

<style scoped>
.product-image {
  width: 100%;
  overflow: hidden;
  background: #f1ede4;
  display: grid;
  place-items: center;
}

.product-image img {
  width: 100%;
  height: 100%;
  /* 真实照片按容器比例裁切，保证不变形 */
  object-fit: cover;
  object-position: center;
  transition: transform 0.5s cubic-bezier(0.2, 0.7, 0.2, 1);
}

.fallback {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #f3ede2, #e6ddcd);
  color: #b4a993;
  font-family: var(--font-serif);
  letter-spacing: 0.2em;
  font-size: 14px;
}
</style>
