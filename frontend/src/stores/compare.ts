import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'

const KEY = 'sg_compare'
const MAX = 4

function readIds(): string[] {
  try {
    const raw = localStorage.getItem(KEY)
    const parsed = raw ? (JSON.parse(raw) as string[]) : []
    return Array.isArray(parsed) ? parsed.slice(0, MAX) : []
  } catch {
    return []
  }
}

export const useCompareStore = defineStore('compare', () => {
  const ids = ref<string[]>(readIds())
  const count = computed(() => ids.value.length)

  watch(
    ids,
    (value) => localStorage.setItem(KEY, JSON.stringify(value)),
    { deep: true }
  )

  function has(id: string) {
    return ids.value.includes(id)
  }

  function add(id: string) {
    if (ids.value.includes(id)) return false
    if (ids.value.length >= MAX) return false
    ids.value.push(id)
    return true
  }

  function remove(id: string) {
    ids.value = ids.value.filter((item) => item !== id)
  }

  function toggle(id: string) {
    if (has(id)) {
      remove(id)
      return false
    }
    return add(id)
  }

  function clear() {
    ids.value = []
  }

  return { ids, count, has, add, remove, toggle, clear, max: MAX }
})
