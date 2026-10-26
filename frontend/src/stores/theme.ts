import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const THEME_KEY = 'sg_theme'

export type ThemeMode = 'light' | 'dark'

function readStoredTheme(): ThemeMode | null {
  const stored = localStorage.getItem(THEME_KEY)
  return stored === 'dark' || stored === 'light' ? stored : null
}

/** 首次访问跟随系统偏好 */
function systemPrefersDark(): boolean {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

function applyToDocument(mode: ThemeMode) {
  const root = document.documentElement
  root.classList.toggle('dark', mode === 'dark')
  // 让原生控件（滚动条、日期选择器）也跟随主题
  root.style.colorScheme = mode
}

export const useThemeStore = defineStore('theme', () => {
  const stored = readStoredTheme()
  const mode = ref<ThemeMode>(stored ?? (systemPrefersDark() ? 'dark' : 'light'))

  const isDark = computed(() => mode.value === 'dark')

  /** 切换时短暂禁用位移动画，避免卡片 hover 等过渡抖动 */
  function withTransitionGuard(apply: () => void) {
    const root = document.documentElement
    root.classList.add('theme-switching')
    apply()
    window.setTimeout(() => root.classList.remove('theme-switching'), 320)
  }

  function setTheme(next: ThemeMode) {
    if (mode.value === next) return
    withTransitionGuard(() => {
      mode.value = next
      applyToDocument(next)
      localStorage.setItem(THEME_KEY, next)
    })
  }

  function toggle() {
    setTheme(mode.value === 'dark' ? 'light' : 'dark')
  }

  /** 清除用户选择，回到跟随系统 */
  function resetToSystem() {
    withTransitionGuard(() => {
      localStorage.removeItem(THEME_KEY)
      mode.value = systemPrefersDark() ? 'dark' : 'light'
      applyToDocument(mode.value)
    })
  }

  /** 应用启动时同步一次 DOM（index.html 内联脚本已提前打过，这里兜底） */
  function init() {
    const current = readStoredTheme() ?? null
    if (current && current !== mode.value) {
      mode.value = current
    }
    applyToDocument(mode.value)

    // 用户没有显式选择时，跟随系统变化
    window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener?.('change', (event) => {
      if (readStoredTheme()) return
      withTransitionGuard(() => {
        mode.value = event.matches ? 'dark' : 'light'
        applyToDocument(mode.value)
      })
    })
  }

  return { mode, isDark, setTheme, toggle, resetToSystem, init }
})