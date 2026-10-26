<script setup lang="ts">
/**
 * 白天 / 黑夜模式切换开关。
 * 太阳与月亮图标叠加，靠 transform 与 opacity 过渡切换，避免图标闪现。
 */
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'

const theme = useThemeStore()
const isDark = computed(() => theme.isDark)

function onToggle() {
  theme.toggle()
}
</script>

<template>
  <button
    class="theme-toggle"
    :class="{ dark: isDark }"
    type="button"
    role="switch"
    :aria-checked="isDark"
    :aria-label="isDark ? '切换到白天模式' : '切换到黑夜模式'"
    :title="isDark ? '切换到白天模式' : '切换到黑夜模式'"
    @click="onToggle"
  >
    <span class="theme-track">
      <span class="theme-thumb">
        <el-icon class="icon-sun"><Sunny /></el-icon>
        <el-icon class="icon-moon"><Moon /></el-icon>
      </span>
    </span>
  </button>
</template>

<style scoped>
.theme-toggle {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.theme-track {
  position: relative;
  display: block;
  width: 46px;
  height: 26px;
  border-radius: 99px;
  background: var(--line-strong);
  border: 1px solid var(--line-strong);
  transition: background-color 0.3s ease, border-color 0.3s ease;
}

.theme-toggle:hover .theme-track {
  border-color: var(--brand);
}

.theme-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--surface);
  box-shadow: 0 2px 6px rgba(38, 33, 25, 0.18);
  display: grid;
  place-items: center;
  transition: transform 0.32s cubic-bezier(0.22, 0.61, 0.36, 1), background-color 0.3s ease;
}

/* 图标叠放，靠透明度与旋转切换 */
.theme-thumb .el-icon {
  position: absolute;
  font-size: 12px;
  transition: opacity 0.26s ease, transform 0.32s ease;
}

.icon-sun {
  color: #e08a2e;
  opacity: 1;
  transform: rotate(0deg) scale(1);
}

.icon-moon {
  color: #b9c4d8;
  opacity: 0;
  transform: rotate(-90deg) scale(0.6);
}

/* 暗色状态：滑块右移，换成月亮 */
.theme-toggle.dark .theme-track {
  background: #2f2f38;
  border-color: #464653;
}

.theme-toggle.dark .theme-thumb {
  transform: translateX(20px);
  background: #1c1c22;
  box-shadow: 0 0 10px rgba(255, 107, 53, 0.35);
}

.theme-toggle.dark .icon-sun {
  opacity: 0;
  transform: rotate(90deg) scale(0.6);
}

.theme-toggle.dark .icon-moon {
  opacity: 1;
  transform: rotate(0deg) scale(1);
}

@media (prefers-reduced-motion: reduce) {
  .theme-track,
  .theme-thumb,
  .theme-thumb .el-icon {
    transition-duration: 0.001ms !important;
  }
}
</style>