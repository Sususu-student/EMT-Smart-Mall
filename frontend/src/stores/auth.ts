import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, userApi } from '@/api'
import type { User } from '@/types'
import { TOKEN_KEY } from '@/api/http'

function readUser(): User | null {
  try {
    const raw = localStorage.getItem('sg_user')
    return raw ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<User | null>(readUser())
  const isLoggedIn = computed(() => Boolean(token.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  function persist(nextToken: string, nextUser: User) {
    token.value = nextToken
    user.value = nextUser
    localStorage.setItem(TOKEN_KEY, nextToken)
    localStorage.setItem('sg_user', JSON.stringify(nextUser))
  }

  async function login(username: string, password: string) {
    const data = await authApi.login({ username, password })
    persist(data.token, data.user)
    return data.user
  }

  async function register(payload: { username: string; password: string; nickname?: string; email?: string }) {
    const data = await authApi.register(payload)
    persist(data.token, data.user)
    return data.user
  }

  async function refreshProfile() {
    if (!token.value) return
    try {
      const profile = await userApi.profile()
      user.value = profile.user
      localStorage.setItem('sg_user', JSON.stringify(profile.user))
    } catch {
      logout()
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem('sg_user')
  }

  return { token, user, isLoggedIn, isAdmin, login, register, refreshProfile, logout }
})
