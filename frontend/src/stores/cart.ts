import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { cartApi } from '@/api'
import type { CartSummary } from '@/types'
import { useAuthStore } from './auth'

const emptySummary = (): CartSummary => ({
  items: [],
  totalQuantity: 0,
  selectedCount: 0,
  totalAmount: 0,
  savedAmount: 0
})

export const useCartStore = defineStore('cart', () => {
  const summary = ref<CartSummary>(emptySummary())
  const loading = ref(false)
  const count = computed(() => summary.value.totalQuantity || summary.value.items.length)

  async function refresh() {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      summary.value = emptySummary()
      return
    }
    loading.value = true
    try {
      summary.value = await cartApi.summary()
    } finally {
      loading.value = false
    }
  }

  async function add(productId: string, quantity = 1) {
    await cartApi.add(productId, quantity)
    await refresh()
  }

  async function update(id: number, quantity: number) {
    await cartApi.update(id, quantity)
    await refresh()
  }

  async function remove(id: number) {
    await cartApi.remove(id)
    await refresh()
  }

  async function select(ids: number[] | null, selected: boolean) {
    await cartApi.select(ids, selected)
    await refresh()
  }

  function clear() {
    summary.value = emptySummary()
  }

  return { summary, loading, count, refresh, add, update, remove, select, clear }
})
