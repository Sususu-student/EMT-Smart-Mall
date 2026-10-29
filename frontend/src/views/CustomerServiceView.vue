<script setup lang="ts">
/**
 * 客服消息中心：卖家在这里查看买家咨询并回复。
 * 左侧会话列表，右侧聊天面板，采用经典双栏工作台布局。
 */
import { computed, onMounted, ref } from 'vue'
import { csApi } from '@/api'
import type { CsConversation } from '@/types'
import CustomerServicePanel from '@/components/CustomerServicePanel.vue'

const loading = ref(true)
const conversations = ref<CsConversation[]>([])
const active = ref<CsConversation | null>(null)

const totalUnread = computed(() => conversations.value.reduce((s, c) => s + (c.unread || 0), 0))

async function load() {
  loading.value = true
  try {
    conversations.value = await csApi.conversations()
    if (!active.value && conversations.value.length) {
      active.value = conversations.value[0]
    }
  } catch {
    conversations.value = []
  } finally {
    loading.value = false
  }
}

/** 切换会话时把该会话标记为已读，并同步本地未读数 */
async function select(c: CsConversation) {
  active.value = c
  if (c.unread > 0) {
    try {
      await csApi.markRead(c.id)
      c.unread = 0
    } catch {
      // 忽略标记失败
    }
  }
}

const fmtTime = (s?: string) => {
  if (!s) return ''
  const d = new Date(s)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  return sameDay
    ? `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    : `${d.getMonth() + 1}/${d.getDate()}`
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="cs-center">
    <div class="cs-layout">
      <!-- 会话列表 -->
      <aside class="cs-list">
        <header class="cs-list-head">
          <h3>买家咨询</h3>
          <span v-if="totalUnread" class="cs-badge">{{ totalUnread }}</span>
        </header>

        <div v-if="conversations.length" class="cs-items">
          <button
            v-for="c in conversations"
            :key="c.id"
            type="button"
            class="cs-item"
            :class="{ active: active?.id === c.id }"
            @click="select(c)"
          >
            <div class="cs-item-main">
              <span class="cs-item-title">订单 {{ c.orderId?.slice(0, 8) }}…</span>
              <span class="cs-item-time">{{ fmtTime(c.lastAt) }}</span>
            </div>
            <p class="cs-item-msg">{{ c.lastMessage || '还没有消息' }}</p>
            <span v-if="c.unread" class="cs-item-unread">{{ c.unread }}</span>
          </button>
        </div>

        <div v-else class="cs-list-empty">
          <p>暂无买家咨询</p>
          <span>买家从订单详情点「联系卖家」后会出现在这里</span>
        </div>
      </aside>

      <!-- 聊天区 -->
      <section class="cs-chat">
        <CustomerServicePanel v-if="active" :conversation="active" @close="active = null" />
        <div v-else class="cs-chat-empty">
          <p>选择左侧会话开始回复</p>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.cs-center {
  min-height: 420px;
}

.cs-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  align-items: start;
}

.cs-list {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.cs-list-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-sunken);
}

.cs-list-head h3 {
  font-size: 13px;
  color: var(--ink);
}

.cs-badge {
  background: var(--brand);
  color: #fff;
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 99px;
}

.cs-items {
  max-height: 520px;
  overflow-y: auto;
}

.cs-item {
  position: relative;
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: none;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
  transition: background 0.2s;
}

.cs-item:hover {
  background: var(--surface-sunken);
}

.cs-item.active {
  background: var(--brand-tint);
  border-left: 3px solid var(--brand);
}

.cs-item-main {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.cs-item-title {
  font-size: 12px;
  color: var(--ink);
  font-weight: 600;
}

.cs-item-time {
  font-size: 9px;
  color: var(--muted-light);
}

.cs-item-msg {
  font-size: 11px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-item-unread {
  position: absolute;
  right: 16px;
  bottom: 12px;
  background: var(--brand);
  color: #fff;
  font-size: 9px;
  min-width: 16px;
  height: 16px;
  border-radius: 99px;
  display: grid;
  place-items: center;
  padding: 0 4px;
}

.cs-list-empty {
  padding: 40px 20px;
  text-align: center;
}

.cs-list-empty p {
  font-size: 12.5px;
  color: var(--muted);
  margin-bottom: 4px;
}

.cs-list-empty span {
  font-size: 10.5px;
  color: var(--muted-light);
  line-height: 1.6;
}

.cs-chat {
  min-height: 380px;
}

.cs-chat-empty {
  height: 380px;
  display: grid;
  place-items: center;
  border: 1px dashed var(--line);
  border-radius: 14px;
  color: var(--muted-light);
  font-size: 12.5px;
}

@media (max-width: 900px) {
  .cs-layout {
    grid-template-columns: 1fr;
  }
}
</style>