<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { agentApi } from '@/api'
import type { LlmStatus } from '@/types'

const status = ref<LlmStatus | null>(null)

onMounted(async () => {
  try {
    status.value = await agentApi.status()
  } catch {
    status.value = null
  }
})
</script>

<template>
  <footer class="footer">
    <div class="container footer-grid">
      <div class="footer-brand">
        <div class="footer-logo">
          <span class="brand-mark">智</span>
          <strong>智选商城</strong>
        </div>
        <p>以大语言模型为核心的智能导购电商平台。对话即搜索，推荐有依据，对比可解释。</p>
        <div class="status">
          <span class="status-dot" :class="{ live: status?.available }"></span>
          <span>{{ status?.available ? 'DeepSeek 在线' : '规则引擎模式' }}</span>
          <span class="status-divider">·</span>
          <span>{{ status?.available ? status.model : '无 API Key 也可完整运行' }}</span>
        </div>
      </div>

      <div class="footer-col">
        <h4>逛一逛</h4>
        <router-link to="/products">全部商品</router-link>
        <router-link to="/products?sort=sales">热销榜单</router-link>
        <router-link to="/products?sort=new">新品上架</router-link>
        <router-link to="/compare">对比中心</router-link>
      </div>

      <div class="footer-col">
        <h4>智能服务</h4>
        <router-link to="/chat">对话导购</router-link>
        <router-link to="/orders">订单查询</router-link>
        <router-link to="/profile">偏好管理</router-link>
        <router-link to="/cart">购物车</router-link>
      </div>

      <div class="footer-col">
        <h4>关于项目</h4>
        <span>Spring Boot 3 + Vue 3</span>
        <span>LangChain4j + DeepSeek</span>
        <span>Agent 执行过程可视化</span>
        <span>字段校验与幻觉拦截</span>
      </div>
    </div>

    <div class="container footer-bottom">
      <span>© 2026 智选商城 · 本科毕业设计演示项目</span>
      <span>演示数据，不产生真实交易</span>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  margin-top: 80px;
  background: #171613;
  color: #e9e2d6;
  padding: 60px 0 26px;
}

.footer-grid {
  display: grid;
  grid-template-columns: 1.6fr 0.7fr 0.7fr 0.9fr;
  gap: 44px;
}

.footer-brand p {
  color: #a39b8e;
  font-size: 13px;
  line-height: 1.9;
  margin: 16px 0 18px;
  max-width: 340px;
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-logo .brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: var(--brand);
  color: #fff;
  display: grid;
  place-items: center;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 20px;
}

.footer-logo strong {
  font-size: 18px;
  letter-spacing: 0.05em;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #8f887d;
  border: 1px solid #35322c;
  border-radius: 99px;
  padding: 7px 12px;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--muted);
}

.status-dot.live {
  background: var(--green);
  box-shadow: 0 0 0 3px rgba(95, 211, 155, 0.15);
}

.status-divider {
  color: #575147;
}

.footer-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 13px;
}

.footer-col h4 {
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  letter-spacing: 0.08em;
}

.footer-col a,
.footer-col span {
  color: #a39b8e;
  transition: color 0.2s;
}

.footer-col a:hover {
  color: #ffb38f;
}

.footer-bottom {
  margin-top: 48px;
  padding-top: 20px;
  border-top: 1px solid #2a2722;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 11px;
  color: #777064;
}

@media (max-width: 900px) {
  .footer-grid {
    grid-template-columns: 1fr 1fr;
    gap: 34px;
  }
}

@media (max-width: 560px) {
  .footer-grid {
    grid-template-columns: 1fr;
  }

  .footer-bottom {
    flex-direction: column;
  }
}
</style>
