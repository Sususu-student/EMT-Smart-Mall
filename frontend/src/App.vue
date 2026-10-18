<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const auth = useAuthStore()
const cart = useCartStore()

onMounted(() => {
  if (auth.isLoggedIn) {
    auth.refreshProfile()
    cart.refresh()
  }
})
</script>

<template>
  <router-view />
</template>
