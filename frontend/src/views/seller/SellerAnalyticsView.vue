<script setup lang="ts">
import '@/views/seller/seller-shared.css'

/**
 * 卖家 · 数据分析：经营看板独立成页。
 * 数据由 SellerAnalytics 组件自己拉取，这里只做页面外壳与权限校验。
 */
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import SellerAnalytics from '@/components/SellerAnalytics.vue'

const router = useRouter()
const auth = useAuthStore()

onMounted(() => {
  if (!auth.isLoggedIn) {
    router.replace({ name: 'login', query: { redirect: '/seller/analytics' } })
    return
  }
  if (!auth.isSeller && !auth.isAdmin) {
    ElMessage.warning('当前账号不是卖家，无法查看经营数据')
    router.replace('/')
  }
})
</script>

<template>
  <div class="seller-page">
    <div class="container">
      <SellerAnalytics />
    </div>
  </div>
</template>

<style scoped>
.seller-page {
  padding-bottom: 20px;
}
</style>