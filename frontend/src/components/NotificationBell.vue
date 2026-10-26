<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { notificationApi } from '@/api'
import type { NotificationItem } from '@/types'
import { dateTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const visible = ref(false)
const loading = ref(false)
const unread = ref(0)
const items = ref<NotificationItem[]>([])
let timer: number | undefined

onMounted(() => {
  if (!auth.isLoggedIn) return
  refreshCount()
  timer = window.setInterval(refreshCount, 30000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})

async function refreshCount() {
  if (!auth.isLoggedIn) return
  try {
    unread.value = await notificationApi.unreadCount()
  } catch {
    unread.value = 0
  }
}

async function open() {
  visible.value = true
  loading.value = true
  try {
    const result = await notificationApi.list({ page: 1, size: 20 })
    items.value = result.records
    unread.value = items.value.filter((item) => item.readStatus === 0).length
  } finally {
    loading.value = false
  }
}

async function readAll() {
  await notificationApi.markAllRead()
  items.value = items.value.map((item) => ({ ...item, readStatus: 1 }))
  unread.value = 0
  ElMessage.success('已全部标记为已读')
}

async function openItem(item: NotificationItem) {
  if (item.readStatus === 0) {
    await notificationApi.markRead(item.id)
    item.readStatus = 1
    unread.value = Math.max(0, unread.value - 1)
  }
  if (item.link) {
    visible.value = false
    router.push(item.link)
  }
}
</script>

<template>
  <div v-if="auth.isLoggedIn" class="notification-bell">
    <button class="bell-btn" aria-label="消息通知" @click="open">
      <el-badge :value="unread" :hidden="!unread" :max="99">
        <el-icon><Bell /></el-icon>
      </el-badge>
    </button>

    <el-drawer v-model="visible" title="消息通知" direction="rtl" size="390px" :append-to-body="true">
      <div class="notice-head">
        <span>{{ unread ? `${unread} 条未读` : '全部已读' }}</span>
        <button v-if="unread" class="read-all" @click="readAll">全部已读</button>
      </div>
      <div v-loading="loading" class="notice-list">
        <button
          v-for="item in items"
          :key="item.id"
          class="notice-item"
          :class="{ unread: item.readStatus === 0 }"
          @click="openItem(item)"
        >
          <span class="notice-dot"></span>
          <div class="notice-copy">
            <div class="notice-title">
              <strong>{{ item.title }}</strong>
              <span>{{ dateTime(item.createdAt) }}</span>
            </div>
            <p>{{ item.content }}</p>
          </div>
        </button>
        <div v-if="!loading && !items.length" class="empty">
          <strong>暂无消息</strong>
          <p>订单发货、支付成功和售后处理结果会出现在这里。</p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.bell-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid transparent;
  background: transparent;
  color: var(--ink);
  display: grid;
  place-items: center;
  cursor: pointer;
  font-size: 18px;
  transition: background 0.2s, border-color 0.2s;
}

.bell-btn:hover {
  background: var(--surface);
  border-color: var(--line);
}

.notice-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 10px;
  color: var(--muted);
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.read-all {
  border: none;
  background: transparent;
  color: var(--brand);
  font-size: 10px;
  cursor: pointer;
}

.notice-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-height: 160px;
  padding-top: 12px;
}

.notice-item {
  display: flex;
  gap: 9px;
  width: 100%;
  text-align: left;
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 10px;
  padding: 11px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.notice-item:hover {
  border-color: var(--line-strong);
}

.notice-item.unread {
  background: var(--brand-tint-strong);
  border-color: #f3d6c7;
}

.notice-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--line-strong);
  margin-top: 5px;
  flex-shrink: 0;
}

.notice-item.unread .notice-dot {
  background: var(--brand);
}

.notice-copy {
  min-width: 0;
}

.notice-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.notice-title strong {
  font-size: 11px;
}

.notice-title span {
  font-size: 8px;
  color: var(--muted-light);
  white-space: nowrap;
}

.notice-copy p {
  font-size: 10px;
  line-height: 1.65;
  color: var(--muted);
  margin-top: 4px;
}
</style>
