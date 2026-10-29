<script setup lang="ts">
/**
 * 客服聊天面板：买家与卖家共用，靠 myRole 区分消息左右。
 * 实时收发走 /ws/cs，断线时降级为手动刷新（历史消息仍可读取）。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { csApi } from '@/api'
import { resolveWsUrl } from '@/api/http'
import type { CsConversation, CsMessage } from '@/types'

const props = defineProps<{ conversation: CsConversation | null }>()
const emit = defineEmits<{ close: [] }>()

const messages = ref<CsMessage[]>([])
const draft = ref('')
const sending = ref(false)
const connected = ref(false)
const loading = ref(false)
const listEl = ref<HTMLDivElement | null>(null)
let ws: WebSocket | null = null
let retryTimer: ReturnType<typeof setTimeout> | null = null

const myRole = computed(() => props.conversation?.myRole || 'buyer')
const title = computed(() =>
  myRole.value === 'buyer' ? '联系卖家' : '买家咨询'
)

/** 判断消息是否由我发出 */
const isMine = (m: CsMessage) => m.senderRole === myRole.value

async function scrollToBottom() {
  await nextTick()
  if (listEl.value) {
    listEl.value.scrollTop = listEl.value.scrollHeight
  }
}

async function loadHistory() {
  if (!props.conversation) return
  loading.value = true
  try {
    messages.value = await csApi.messages(props.conversation.id)
    await csApi.markRead(props.conversation.id)
    await scrollToBottom()
  } catch {
    ElMessage.error('消息加载失败')
  } finally {
    loading.value = false
  }
}

function connect() {
  const conv = props.conversation
  if (!conv) return
  const token = localStorage.getItem('sg_token')
  if (!token) return
  try {
    ws = new WebSocket(resolveWsUrl(`/ws/cs?token=${encodeURIComponent(token)}&conversationId=${conv.id}`))
  } catch {
    return
  }
  ws.onopen = () => {
    connected.value = true
    ws?.send(JSON.stringify({ action: 'join', conversationId: conv.id }))
  }
  ws.onmessage = (ev) => {
    try {
      const data = JSON.parse(ev.data)
      if (data.type !== 'message') return
      // 去重：自己发的消息服务端也会回推
      if (messages.value.some((m) => String(m.id) === String(data.id))) return
      messages.value.push({
        id: data.id,
        conversationId: data.conversationId,
        senderId: data.senderId,
        senderRole: data.senderRole,
        content: data.content,
        createdAt: data.createdAt
      })
      scrollToBottom()
      if (data.senderRole !== myRole.value) {
        csApi.markRead(conv.id).catch(() => {})
      }
    } catch {
      // 忽略无法解析的推送
    }
  }
  ws.onclose = () => {
    connected.value = false
    // 断线后 3 秒重连，避免频繁重试
    retryTimer = setTimeout(() => {
      if (props.conversation) connect()
    }, 3000)
  }
  ws.onerror = () => {
    connected.value = false
  }
}

function disconnect() {
  if (retryTimer) {
    clearTimeout(retryTimer)
    retryTimer = null
  }
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
  connected.value = false
}

function send() {
  const text = draft.value.trim()
  if (!text) return
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    ElMessage.warning('连接已断开，正在重连，请稍后再发')
    return
  }
  sending.value = true
  try {
    ws.send(JSON.stringify({ action: 'send', conversationId: props.conversation!.id, content: text }))
    draft.value = ''
  } finally {
    sending.value = false
  }
}

/** Enter 发送，Shift+Enter 换行 */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

const fmtTime = (s?: string) => {
  if (!s) return ''
  const d = new Date(s)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

watch(() => props.conversation?.id, async (id) => {
  disconnect()
  messages.value = []
  if (id) {
    await loadHistory()
    connect()
  }
}, { immediate: true })

onMounted(() => {
  if (props.conversation) connect()
})

onBeforeUnmount(disconnect)
</script>

<template>
  <div v-if="conversation" class="cs-panel">
    <header class="cs-head">
      <div>
        <strong>{{ title }}</strong>
        <span class="cs-status" :class="{ online: connected }">
          <i />{{ connected ? '已连接' : '重连中…' }}
        </span>
      </div>
      <button class="cs-close" type="button" aria-label="关闭" @click="emit('close')">
        <el-icon><Close /></el-icon>
      </button>
    </header>

    <div ref="listEl" v-loading="loading" class="cs-body">
      <div v-if="!messages.length && !loading" class="cs-empty">
        <p>还没有消息</p>
        <span>{{ myRole === 'buyer' ? '有问题可以直接问卖家，比如发货时间、尺码建议' : '买家还没有发来消息' }}</span>
      </div>
      <div
        v-for="m in messages"
        :key="m.id"
        class="cs-msg"
        :class="{ mine: isMine(m) }"
      >
        <div class="cs-bubble">{{ m.content }}</div>
        <span class="cs-time">{{ fmtTime(m.createdAt) }}</span>
      </div>
    </div>

    <footer class="cs-foot">
      <textarea
        v-model="draft"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        rows="2"
        @keydown="onKeydown"
      />
      <button type="button" class="cs-send" :disabled="!draft.trim() || sending" @click="send">
        发送
      </button>
    </footer>
  </div>
</template>

<style scoped>
.cs-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.cs-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
  background: var(--surface-sunken);
}

.cs-head strong {
  font-size: 13px;
  color: var(--ink);
}

.cs-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: var(--muted-light);
  margin-left: 8px;
}

.cs-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--muted-light);
}

.cs-status.online i {
  background: var(--green);
}

.cs-close {
  border: none;
  background: none;
  color: var(--muted);
  cursor: pointer;
  padding: 4px;
}

.cs-close:hover {
  color: var(--brand);
}

.cs-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 240px;
  max-height: 460px;
}

.cs-empty {
  margin: auto;
  text-align: center;
  color: var(--muted-light);
}

.cs-empty p {
  font-size: 13px;
  color: var(--muted);
  margin-bottom: 4px;
}

.cs-empty span {
  font-size: 11px;
}

.cs-msg {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 78%;
}

.cs-msg.mine {
  align-self: flex-end;
  align-items: flex-end;
}

.cs-bubble {
  padding: 9px 13px;
  border-radius: 12px;
  border-top-left-radius: 4px;
  background: var(--surface-sunken);
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.65;
  word-break: break-word;
  white-space: pre-wrap;
}

.cs-msg.mine .cs-bubble {
  background: var(--brand);
  color: #fff;
  border-top-left-radius: 12px;
  border-top-right-radius: 4px;
}

.cs-time {
  font-size: 9px;
  color: var(--muted-light);
  margin-top: 3px;
}

.cs-foot {
  display: flex;
  gap: 8px;
  padding: 12px 14px;
  border-top: 1px solid var(--line);
  background: var(--surface-sunken);
}

.cs-foot textarea {
  flex: 1;
  resize: none;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px 11px;
  font-size: 13px;
  font-family: inherit;
  line-height: 1.5;
  background: var(--surface);
  color: var(--ink);
  outline: none;
}

.cs-foot textarea:focus {
  border-color: var(--brand);
}

.cs-send {
  align-self: flex-end;
  border: none;
  background: var(--ink);
  color: var(--on-ink);
  font-size: 12.5px;
  padding: 9px 18px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
}

.cs-send:hover:not(:disabled) {
  background: var(--brand);
}

.cs-send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>