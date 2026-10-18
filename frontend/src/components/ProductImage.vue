<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    src?: string
    alt?: string
    ratio?: string
  }>(),
  { src: '/img/default.svg', alt: '商品图片', ratio: '1 / 1' }
)

const failed = ref(false)

watch(
  () => props.src,
  () => {
    failed.value = false
  }
)
</script>

<template>
  <div class="product-image" :style="{ aspectRatio: ratio }">
    <img v-if="!failed && src" :src="src" :alt="alt" loading="lazy" @error="failed = true" />
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
  object-fit: cover;
  transition: transform 0.5s cubic-bezier(0.2, 0.7, 0.2, 1);
}

.fallback {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #f3ede2, #e6ddcd);
  color: #b4a993;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  letter-spacing: 0.2em;
  font-size: 14px;
}
</style>
