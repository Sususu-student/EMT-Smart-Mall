<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SiteHeader from '@/components/SiteHeader.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { useCompareStore } from '@/stores/compare'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()
const auth = useAuthStore()

const showCompareBar = computed(() => compare.count > 0 && route.name !== 'compare')
const showFloatingChat = computed(() => route.name !== 'chat' && auth.isLoggedIn)

watch(
  () => route.fullPath,
  () => {
    if (route.name === 'chat') {
      document.body.classList.add('chat-route')
    } else {
      document.body.classList.remove('chat-route')
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="store-shell">
    <SiteHeader />
    <main class="store-main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    <SiteFooter />

    <transition name="fade">
      <div v-if="showCompareBar" class="compare-bar">
        <div class="container compare-inner">
          <div class="compare-copy">
            <span class="compare-count">{{ compare.count }}</span>
            <div>
              <strong>对比清单</strong>
              <small>最多可同时对比 {{ compare.max }} 件商品</small>
            </div>
          </div>
          <div class="compare-actions">
            <el-button text @click="compare.clear()">清空</el-button>
            <el-button type="primary" round @click="router.push('/compare')">
              查看对比 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </transition>

    <transition name="fade">
      <button
        v-if="showFloatingChat"
        class="floating-chat"
        :style="{ bottom: showCompareBar ? '118px' : '88px' }"
        aria-label="打开智能导购"
        @click="router.push('/chat')"
      >
        <el-icon><ChatDotRound /></el-icon>
        <span>问导购</span>
      </button>
    </transition>
  </div>
</template>

<style scoped>
.store-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.store-main {
  flex: 1;
}

.compare-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 40;
  background: rgba(27, 26, 23, 0.96);
  backdrop-filter: blur(12px);
  color: #fff;
  padding: 12px 0;
  box-shadow: 0 -10px 40px rgba(0, 0, 0, 0.16);
}

.compare-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.compare-copy {
  display: flex;
  align-items: center;
  gap: 12px;
}

.compare-count {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--brand);
  display: grid;
  place-items: center;
  font-weight: 700;
}

.compare-copy strong {
  display: block;
  font-size: 14px;
}

.compare-copy small {
  display: block;
  color: #b8b0a3;
  font-size: 11px;
  margin-top: 2px;
}

.compare-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.compare-actions :deep(.el-button.is-text) {
  color: #cfc7ba;
}

.floating-chat {
  position: fixed;
  right: 26px;
  bottom: 88px;
  z-index: 35;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: none;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  width: 48px;
  height: 48px;
  padding: 0;
  font-size: 18px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 14px 36px rgba(240, 90, 40, 0.32);
  transition: transform 0.2s, box-shadow 0.2s;
}

.floating-chat:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 44px rgba(240, 90, 40, 0.4);
}

@media (max-width: 620px) {
  .compare-copy small {
    display: none;
  }

  .floating-chat {
    right: 16px;
    bottom: 82px;
    padding: 12px;
  }

  .floating-chat span {
    display: none;
  }
}
</style>
