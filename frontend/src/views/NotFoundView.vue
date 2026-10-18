<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="code">404</div>
    <h1 class="display">这一页走丢了</h1>
    <p>你访问的页面不存在，或者商品已经下架。</p>
    <div class="actions">
      <el-button type="primary" round @click="router.push('/')">回到首页</el-button>
      <el-button round @click="router.push('/products')">去逛商品</el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 70vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 20px;
}

.code {
  font-size: clamp(90px, 18vw, 180px);
  font-weight: 800;
  letter-spacing: -0.06em;
  color: #e8e0d2;
  line-height: 1;
}

h1 {
  font-size: 34px;
  margin: 10px 0;
}

p {
  font-size: 13px;
  color: var(--muted);
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}
</style>
