<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { agentApi } from '@/api'
import type {
  AgentStep,
  ChatResponse,
  CompareResult,
  Conversation,
  ExecutionLog,
  LlmStatus,
  Order,
  RecommendedProduct,
  Turn
} from '@/types'
import { dateTime, intentLabel, money, orderStatusType } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import { useCompareStore } from '@/stores/compare'
import ChatProductCard from '@/components/ChatProductCard.vue'
import AgentTimeline from '@/components/AgentTimeline.vue'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  products?: RecommendedProduct[]
  clarifyOptions?: string[]
  orders?: Order[]
  compare?: CompareResult
  intent?: string
  steps?: AgentStep[]
  degraded?: boolean
  appliedPreferences?: string[]
  taskId?: string
  time: string
}

interface WsEvent {
  type: string
  taskId: string
  conversationId: string
  step?: string
  stepName?: string
  status?: string
  durationMs?: number
  summary?: string
  timestamp?: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const compareStore = useCompareStore()

const conversations = ref<Conversation[]>([])
const conversationId = ref('')
const messages = ref<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const loadingHistory = ref(false)
const liveSteps = ref<AgentStep[]>([])
const currentTaskId = ref('')
const agentStatus = ref<LlmStatus | null>(null)
const scrollEl = ref<HTMLDivElement>()
const sessionDrawer = ref(false)
const insightDrawer = ref(false)
const insightOpen = ref(true)
let socket: WebSocket | null = null
let reconnectTimer: number | undefined
let destroyed = false

const suggestions = [
  { icon: '🎧', title: '跑步耳机', text: '想买跑步用的蓝牙耳机，预算 300 以内，要防水' },
  { icon: '☕', title: '家用咖啡机', text: '推荐一台 1000 元左右的半自动咖啡机' },
  { icon: '🎒', title: '通勤背包', text: '找一款通勤双肩包，能装 16 寸电脑，最好防水' },
  { icon: '🧴', title: '敏感肌护肤', text: '敏感肌适合的保湿护肤品，预算 300 左右' }
]

const quickAsks = ['帮我对比前两个', '查询我的订单', '预算不限，再看看', '把第一款加入购物车']
const latestSteps = computed(() => {
  if (liveSteps.value.length) return liveSteps.value
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const message = messages.value[i]
    if (message.role === 'assistant' && message.steps?.length) return message.steps
  }
  return []
})
const currentConversation = computed(() => conversations.value.find((item) => item.id === conversationId.value))
const modeLabel = computed(() => (agentStatus.value?.available ? 'DeepSeek 在线' : '规则引擎模式'))

onMounted(async () => {
  agentStatus.value = await agentApi.status().catch(() => null)
  await loadConversations()
  const q = route.query.q as string | undefined
  if (q) {
    conversationId.value = ''
    messages.value = []
    await router.replace({ name: 'chat' })
    await send(q)
  }
})

onBeforeUnmount(() => {
  destroyed = true
  if (reconnectTimer) window.clearTimeout(reconnectTimer)
  socket?.close()
})

watch(conversationId, (id) => {
  if (id) connectSocket()
  nextTick(scrollToBottom)
})

async function loadConversations() {
  try {
    conversations.value = await agentApi.conversations()
    if (!conversationId.value && conversations.value.length) {
      await selectConversation(conversations.value[0].id)
    }
  } catch {
    conversations.value = []
  }
}

async function selectConversation(id: string) {
  if (id === conversationId.value) {
    sessionDrawer.value = false
    return
  }
  conversationId.value = id
  loadingHistory.value = true
  liveSteps.value = []
  try {
    const turns = await agentApi.turns(id)
    messages.value = turns.map(toMessage)
    sessionDrawer.value = false
    await nextTick(scrollToBottom)
  } finally {
    loadingHistory.value = false
  }
}

function toMessage(turn: Turn): ChatMessage {
  return {
    id: `turn-${turn.id}`,
    role: turn.role,
    content: turn.content,
    products: turn.products || [],
    intent: turn.intent || undefined,
    taskId: turn.taskId || undefined,
    time: turn.createdAt ? dateTime(turn.createdAt).slice(11, 16) : ''
  }
}

function newConversation() {
  conversationId.value = ''
  messages.value = []
  liveSteps.value = []
  currentTaskId.value = ''
  sessionDrawer.value = false
  input.value = ''
  nextTick(() => scrollToBottom())
}

async function removeConversation(conversation: Conversation) {
  await ElMessageBox.confirm(`确定删除会话「${conversation.title}」吗？`, '删除会话', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await agentApi.deleteConversation(conversation.id)
  ElMessage.success('会话已删除')
  if (conversation.id === conversationId.value) {
    newConversation()
  }
  await loadConversations()
}

async function send(text?: string) {
  const message = (text ?? input.value).trim()
  if (!message || sending.value) return
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: '/chat' } })
    return
  }
  input.value = ''
  sending.value = true
  liveSteps.value = []
  currentTaskId.value = ''
  messages.value.push({
    id: `user-${Date.now()}`,
    role: 'user',
    content: message,
    time: nowTime()
  })
  await nextTick(scrollToBottom)

  try {
    const response = await agentApi.chat({ message, conversationId: conversationId.value || undefined })
    applyResponse(response)
  } catch {
    messages.value.push({
      id: `error-${Date.now()}`,
      role: 'assistant',
      content: '抱歉，刚才没有连上导购服务。请确认后端已启动后重试。',
      time: nowTime()
    })
  } finally {
    sending.value = false
    await nextTick(scrollToBottom)
  }
}

function applyResponse(response: ChatResponse) {
  conversationId.value = response.conversationId
  currentTaskId.value = response.taskId
  liveSteps.value = response.steps || []
  messages.value.push({
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: response.reply,
    products: response.products || [],
    clarifyOptions: response.clarifyOptions || [],
    orders: response.orders || [],
    compare: response.compare,
    intent: response.intent,
    steps: response.steps || [],
    degraded: response.degraded,
    appliedPreferences: response.appliedPreferences || [],
    taskId: response.taskId,
    time: nowTime()
  })
  loadConversations()
}

function nowTime() {
  const date = new Date()
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function scrollToBottom() {
  if (scrollEl.value) {
    scrollEl.value.scrollTop = scrollEl.value.scrollHeight
  }
}

function connectSocket() {
  if (!conversationId.value || destroyed) return
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    if (socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ action: 'subscribe', conversationId: conversationId.value }))
    }
    return
  }
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const token = encodeURIComponent(auth.token)
  const url = `${protocol}://${window.location.host}/ws/agent?token=${token}&conversationId=${conversationId.value}`
  socket = new WebSocket(url)
  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data) as WsEvent
      if (data.type !== 'step') return
      if (currentTaskId.value && data.taskId && data.taskId !== currentTaskId.value) return
      const step: AgentStep = {
        step: data.step || '',
        stepName: data.stepName || '',
        status: data.status || 'success',
        durationMs: data.durationMs || 0,
        summary: data.summary || '',
        createdAt: data.timestamp ? dateTime(data.timestamp).slice(11, 19) : nowTime()
      }
      liveSteps.value = [...liveSteps.value, step]
      if (insightOpen.value) {
        nextTick(() => {
          const panel = document.querySelector('.insight .timeline-card') as HTMLElement | null
          panel?.scrollTo({ top: 9999, behavior: 'smooth' })
        })
      }
    } catch {
      // ignore malformed frames
    }
  }
  socket.onclose = () => {
    socket = null
    if (!destroyed && conversationId.value) {
      reconnectTimer = window.setTimeout(connectSocket, 2500)
    }
  }
}

async function showTrace(message: ChatMessage) {
  if (message.taskId) {
    try {
      const logs = await agentApi.logs({ taskId: message.taskId })
      liveSteps.value = logs.map((log: ExecutionLog) => ({
        step: log.step,
        stepName: log.stepName,
        status: log.status,
        durationMs: log.durationMs,
        summary: log.output || '',
        createdAt: log.createdAt ? dateTime(log.createdAt).slice(11, 19) : ''
      }))
    } catch {
      // fall back to message steps
    }
  }
  insightOpen.value = true
  insightDrawer.value = true
}

function openCompare(result: CompareResult) {
  compareStore.clear()
  result.products.forEach((product) => compareStore.add(product.id))
  router.push('/compare')
}

function useClarify(option: string) {
  send(option)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

function goOrder(order: Order) {
  router.push(`/order/${order.id}`)
}
</script>

<template>
  <div class="chat-page">
    <div class="chat-shell">
      <aside class="sessions">
        <div class="sessions-head">
          <div>
            <span class="eyebrow">CONVERSATIONS</span>
            <h2>导购会话</h2>
          </div>
          <button class="new-chat" aria-label="新建会话" @click="newConversation">
            <el-icon><Plus /></el-icon>
          </button>
        </div>

        <button class="new-chat-wide" @click="newConversation">
          <el-icon><EditPen /></el-icon>
          开始新对话
        </button>

        <div class="session-list">
          <div
            v-for="conversation in conversations"
            :key="conversation.id"
            class="session"
            :class="{ active: conversation.id === conversationId }"
            @click="selectConversation(conversation.id)"
          >
            <el-icon class="session-icon"><ChatLineRound /></el-icon>
            <div class="session-copy">
              <strong>{{ conversation.title }}</strong>
              <span>{{ conversation.turnCount }} 轮 · {{ dateTime(conversation.updatedAt).slice(5, 16) }}</span>
            </div>
            <button class="session-delete" aria-label="删除会话" @click.stop="removeConversation(conversation)">
              <el-icon><Delete /></el-icon>
            </button>
          </div>
          <div v-if="!conversations.length" class="session-empty">还没有会话，开始你的第一次导购吧。</div>
        </div>

        <div class="sessions-foot">
          <div class="agent-mode">
            <span class="mode-dot" :class="{ live: agentStatus?.available }"></span>
            <div>
              <strong>{{ modeLabel }}</strong>
              <span>{{ agentStatus?.available ? agentStatus.model : '无需 API Key，功能完整' }}</span>
            </div>
          </div>
          <button class="foot-link" @click="router.push('/products')">
            <el-icon><Goods /></el-icon> 去逛商品
          </button>
        </div>
      </aside>

      <section class="chat-main">
        <header class="chat-head">
          <div class="head-left">
            <button class="mobile-only icon-ghost" aria-label="会话列表" @click="sessionDrawer = true">
              <el-icon><Menu /></el-icon>
            </button>
            <div class="agent-avatar">AI</div>
            <div>
              <h1>智选导购 Agent</h1>
              <div class="agent-status">
                <span class="mode-dot" :class="{ live: agentStatus?.available }"></span>
                {{ modeLabel }}
                <span class="sep">·</span>
                <span>意图识别 / 商品检索 / 对比决策</span>
              </div>
            </div>
          </div>
          <div class="head-actions">
            <button class="icon-ghost" aria-label="执行轨迹" @click="insightOpen = true; insightDrawer = true">
              <el-icon><DataLine /></el-icon>
            </button>
            <button class="icon-ghost" aria-label="新建会话" @click="newConversation">
              <el-icon><Plus /></el-icon>
            </button>
          </div>
        </header>

        <div ref="scrollEl" v-loading="loadingHistory" class="messages">
          <div v-if="!messages.length" class="welcome">
            <div class="welcome-glow"></div>
            <span class="eyebrow">SMART GUIDE · AI CONCIERGE</span>
            <h2 class="display">今天想买点什么？</h2>
            <p>
              直接描述需求就好。我会先理解场景和预算，再检索商品、解释推荐理由；
              信息不够时会主动问你。
            </p>
            <div class="suggestion-grid">
              <button v-for="item in suggestions" :key="item.title" class="suggestion" @click="send(item.text)">
                <span class="suggestion-icon">{{ item.icon }}</span>
                <div>
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.text }}</span>
                </div>
                <el-icon class="suggestion-arrow"><ArrowRight /></el-icon>
              </button>
            </div>
            <div class="welcome-caps">
              <span><el-icon><CircleCheck /></el-icon> 多轮澄清</span>
              <span><el-icon><CircleCheck /></el-icon> 真实字段推荐</span>
              <span><el-icon><CircleCheck /></el-icon> 执行过程可见</span>
            </div>
          </div>

          <template v-else>
            <div v-for="message in messages" :key="message.id" class="message" :class="message.role">
              <div v-if="message.role === 'assistant'" class="msg-avatar">AI</div>
              <div class="msg-body">
                <div class="bubble" :class="{ 'assistant-bubble': message.role === 'assistant' }">
                  <p class="msg-text">{{ message.content }}</p>

                  <div v-if="message.products?.length" class="msg-products">
                    <ChatProductCard v-for="item in message.products" :key="item.product.id" :item="item" />
                  </div>

                  <div v-if="message.compare" class="chat-compare">
                    <div class="cc-head">
                      <span><el-icon><DataAnalysis /></el-icon> 对比决策结果</span>
                      <el-tag size="small" round effect="light">{{ message.compare.products.length }} 款商品</el-tag>
                    </div>
                    <div class="cc-advice">{{ message.compare.advice }}</div>
                    <div class="cc-scores">
                      <div v-for="score in message.compare.scores" :key="score.productId" class="cc-score" :class="{ winner: score.productId === message.compare.recommendedId }">
                        <span>{{ message.compare.products.find((p) => p.id === score.productId)?.name }}</span>
                        <b>{{ score.totalScore }}</b>
                      </div>
                    </div>
                    <el-button size="small" round class="cc-btn" @click="openCompare(message.compare)">
                      查看完整对比与雷达图
                    </el-button>
                  </div>

                  <div v-if="message.orders?.length" class="chat-orders">
                    <button v-for="order in message.orders" :key="order.id" class="chat-order" @click="goOrder(order)">
                      <div>
                        <strong>{{ order.orderNo }}</strong>
                        <span>{{ order.items.map((item) => item.productName).join('、') }}</span>
                      </div>
                      <div class="order-right">
                        <el-tag size="small" :type="orderStatusType(order.status)" round>{{ order.statusLabel }}</el-tag>
                        <b>{{ money(order.totalAmount) }}</b>
                      </div>
                    </button>
                  </div>

                  <div v-if="message.clarifyOptions?.length" class="clarify">
                    <span class="clarify-label">你可以这样说</span>
                    <button v-for="option in message.clarifyOptions" :key="option" class="clarify-chip" @click="useClarify(option)">
                      {{ option }}
                    </button>
                  </div>
                </div>

                <div class="msg-meta">
                  <span>{{ message.time }}</span>
                  <template v-if="message.role === 'assistant'">
                    <span class="meta-sep">·</span>
                    <span>{{ intentLabel(message.intent) }}</span>
                    <span v-if="message.degraded" class="degraded-tag">规则引擎</span>
                    <span v-for="pref in message.appliedPreferences || []" :key="pref" class="degraded-tag pref-tag">
                      偏好 {{ pref }}
                    </span>
                    <button v-if="message.taskId" class="trace-link" @click="showTrace(message)">
                      执行轨迹 <el-icon><ArrowRight /></el-icon>
                    </button>
                  </template>
                </div>
              </div>
            </div>

            <div v-if="sending" class="message assistant">
              <div class="msg-avatar">AI</div>
              <div class="msg-body">
                <div class="bubble assistant-bubble typing">
                  <span></span><span></span><span></span>
                  <em>正在理解需求、检索商品…</em>
                </div>
              </div>
            </div>
          </template>
        </div>

        <div class="composer">
          <div class="composer-box">
            <textarea
              v-model="input"
              rows="1"
              placeholder="描述你的购物需求，例如：想买跑步耳机，预算 300 以内，要防水"
              :disabled="sending"
              @keydown="onKeydown"
            ></textarea>
            <button class="send-btn" :disabled="sending || !input.trim()" aria-label="发送" @click="send()">
              <el-icon><Promotion /></el-icon>
            </button>
          </div>
          <div class="composer-hints">
            <div class="hint-chips">
              <button v-for="ask in quickAsks" :key="ask" @click="send(ask)">{{ ask }}</button>
            </div>
            <span class="send-hint">Enter 发送 · Shift + Enter 换行</span>
          </div>
        </div>
      </section>

      <aside class="insight">
        <div class="insight-head">
          <div>
            <span class="eyebrow">LIVE TRACE</span>
            <h2>执行轨迹</h2>
          </div>
          <span v-if="liveSteps.length" class="step-count">{{ liveSteps.length }} 步</span>
        </div>
        <AgentTimeline :steps="latestSteps" title="Agent 执行过程" />
        <div class="insight-note">
          <el-icon><InfoFilled /></el-icon>
          <p>每一步都会写入执行日志，可通过 WebSocket 实时推送。点击消息下方的“执行轨迹”可回看历史。</p>
        </div>
      </aside>
    </div>

    <el-drawer v-model="sessionDrawer" title="导购会话" direction="ltr" size="82%">
      <button class="new-chat-wide" @click="newConversation">
        <el-icon><EditPen /></el-icon> 开始新对话
      </button>
      <div class="session-list drawer-list">
        <div
          v-for="conversation in conversations"
          :key="conversation.id"
          class="session"
          :class="{ active: conversation.id === conversationId }"
          @click="selectConversation(conversation.id)"
        >
          <el-icon class="session-icon"><ChatLineRound /></el-icon>
          <div class="session-copy">
            <strong>{{ conversation.title }}</strong>
            <span>{{ conversation.turnCount }} 轮</span>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="insightDrawer" title="Agent 执行轨迹" direction="rtl" size="88%">
      <AgentTimeline :steps="latestSteps" title="Agent 执行过程" />
    </el-drawer>
  </div>
</template>

<style scoped>
.chat-page {
  height: calc(100vh - 112px);
  min-height: 620px;
  padding: 14px 18px;
  background:
    radial-gradient(circle at 8% 0%, rgba(240, 90, 40, 0.05), transparent 30%),
    var(--bg);
}

.chat-shell {
  height: 100%;
  max-width: 1560px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 252px minmax(0, 1fr) 316px;
  gap: 14px;
}

.sessions,
.insight {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.sessions {
  padding: 18px 14px;
}

.sessions-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 0 4px 14px;
}

.sessions-head .eyebrow {
  font-size: 8px;
  letter-spacing: 0.2em;
}

.sessions-head h2 {
  font-size: 17px;
  margin-top: 6px;
}

.new-chat {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px solid var(--line);
  background: #fff;
  cursor: pointer;
  display: grid;
  place-items: center;
  color: var(--ink);
}

.new-chat:hover {
  background: var(--ink);
  color: #fff;
}

.new-chat-wide {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  border: 1px dashed var(--line-strong);
  background: transparent;
  border-radius: 10px;
  padding: 10px;
  font-size: 11px;
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.2s;
}

.new-chat-wide:hover {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-soft);
}

.session-list {
  margin-top: 12px;
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-height: 0;
}

.session {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 8px;
  border-radius: 9px;
  cursor: pointer;
  transition: background 0.18s;
}

.session:hover {
  background: var(--bg);
}

.session.active {
  background: var(--ink);
  color: #fff;
}

.session-icon {
  color: var(--muted-light);
  font-size: 14px;
  flex-shrink: 0;
}

.session.active .session-icon {
  color: #ffb38f;
}

.session-copy {
  min-width: 0;
  flex: 1;
}

.session-copy strong {
  display: block;
  font-size: 11.5px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-copy span {
  display: block;
  font-size: 9px;
  color: var(--muted-light);
  margin-top: 2px;
}

.session.active .session-copy span {
  color: #9d9588;
}

.session-delete {
  border: none;
  background: transparent;
  color: var(--muted-light);
  cursor: pointer;
  opacity: 0;
  padding: 3px;
}

.session:hover .session-delete {
  opacity: 1;
}

.session-delete:hover {
  color: #d64545;
}

.session-empty {
  font-size: 10px;
  color: var(--muted);
  text-align: center;
  padding: 24px 10px;
  line-height: 1.8;
}

.sessions-foot {
  border-top: 1px solid var(--line);
  padding-top: 12px;
  margin-top: 10px;
}

.agent-mode {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 8px;
  background: #fbf8f1;
  border-radius: 9px;
}

.mode-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #b9b1a3;
  margin-top: 4px;
  flex-shrink: 0;
}

.mode-dot.live {
  background: #38a169;
  box-shadow: 0 0 0 3px rgba(56, 161, 105, 0.13);
}

.agent-mode strong {
  display: block;
  font-size: 10px;
}

.agent-mode span {
  display: block;
  font-size: 8.5px;
  color: var(--muted);
  margin-top: 2px;
}

.foot-link {
  display: flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 10px;
  padding: 10px 6px 0;
  cursor: pointer;
}

.foot-link:hover {
  color: var(--ink);
}

.chat-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 16px;
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.9);
  flex-shrink: 0;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 11px;
}

.agent-avatar {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: var(--ink);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  position: relative;
  flex-shrink: 0;
}

.agent-avatar::after {
  content: '';
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #38a169;
  border: 2px solid #fff;
}

.chat-head h1 {
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 17px;
  font-weight: 600;
}

.agent-status {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 9px;
  color: var(--muted);
  margin-top: 3px;
}

.agent-status .mode-dot {
  margin-top: 0;
}

.sep {
  color: var(--line-strong);
}

.head-actions {
  display: flex;
  gap: 5px;
}

.icon-ghost {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 16px;
}

.icon-ghost:hover {
  background: var(--bg);
  color: var(--ink);
}

.mobile-only {
  display: none;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 22px 24px 8px;
  min-height: 0;
  scroll-behavior: smooth;
}

.welcome {
  max-width: 720px;
  margin: 20px auto;
  text-align: center;
  position: relative;
}

.welcome-glow {
  position: absolute;
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, rgba(240, 90, 40, 0.09), transparent 68%);
  left: 50%;
  top: -80px;
  transform: translateX(-50%);
  pointer-events: none;
}

.welcome h2 {
  font-size: clamp(30px, 3.4vw, 42px);
  margin: 14px 0 12px;
  position: relative;
}

.welcome > p {
  max-width: 520px;
  margin: 0 auto;
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--muted);
}

.suggestion-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
  margin-top: 28px;
  text-align: left;
}

.suggestion {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.suggestion:hover {
  border-color: var(--brand);
  transform: translateY(-1px);
  box-shadow: 0 8px 22px rgba(38, 33, 25, 0.06);
}

.suggestion-icon {
  font-size: 20px;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: var(--bg);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.suggestion div {
  min-width: 0;
}

.suggestion strong {
  display: block;
  font-size: 11.5px;
}

.suggestion div span {
  display: block;
  font-size: 9.5px;
  color: var(--muted);
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.suggestion-arrow {
  margin-left: auto;
  color: var(--muted-light);
  font-size: 12px;
}

.welcome-caps {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 24px;
  flex-wrap: wrap;
}

.welcome-caps span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 9.5px;
  color: var(--muted);
}

.welcome-caps .el-icon {
  color: var(--green);
}

.message {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  max-width: 860px;
}

.message.user {
  flex-direction: row-reverse;
  margin-left: auto;
}

.msg-avatar {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--ink);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 9px;
  font-weight: 700;
  flex-shrink: 0;
}

.msg-body {
  min-width: 0;
  max-width: 100%;
}

.message.user .msg-body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.bubble {
  border-radius: 14px;
  padding: 13px 16px;
  background: #f3efe7;
  font-size: 13px;
  line-height: 1.75;
}

.message.user .bubble {
  background: var(--ink);
  color: #fff;
  border-bottom-right-radius: 4px;
  max-width: 640px;
}

.assistant-bubble {
  background: #fbf9f4;
  border: 1px solid var(--line);
  border-bottom-left-radius: 4px;
}

.msg-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-products {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  min-width: 0;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 9px;
  color: var(--muted-light);
  margin-top: 6px;
  padding: 0 4px;
  flex-wrap: wrap;
}

.meta-sep {
  color: var(--line-strong);
}

.degraded-tag {
  background: #fff1e8;
  color: #b9532a;
  font-size: 8px;
  padding: 1px 5px;
  border-radius: 4px;
}

.pref-tag {
  background: #edf6f0;
  color: #2f7a5c;
}

.trace-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: none;
  background: transparent;
  color: var(--brand);
  font-size: 9px;
  cursor: pointer;
  padding: 0;
  margin-left: 2px;
}

.typing {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--muted);
  font-size: 11px;
}

.typing span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--brand);
  animation: blink 1.2s infinite;
}

.typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing span:nth-child(3) {
  animation-delay: 0.3s;
}

.typing em {
  font-style: normal;
  margin-left: 6px;
}

@keyframes blink {
  0%, 100% { opacity: 0.2; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-2px); }
}

.clarify {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}

.clarify-label {
  font-size: 9px;
  color: var(--muted-light);
}

.clarify-chip {
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 99px;
  padding: 5px 10px;
  font-size: 10px;
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.2s;
}

.clarify-chip:hover {
  border-color: var(--brand);
  color: var(--brand-deep);
  background: var(--brand-soft);
}

.chat-compare {
  margin-top: 12px;
  border: 1px solid #f2d8c8;
  background: #fff9f4;
  border-radius: 11px;
  padding: 12px;
  min-width: min(520px, 100%);
}

.cc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 10px;
  font-weight: 600;
  color: var(--brand-deep);
}

.cc-head span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.cc-advice {
  font-size: 11.5px;
  line-height: 1.75;
  color: var(--ink-soft);
  margin: 9px 0;
}

.cc-scores {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 9px;
}

.cc-score {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 9.5px;
  color: var(--muted);
}

.cc-score b {
  color: var(--ink);
}

.cc-score.winner {
  border-color: var(--brand);
  color: var(--brand-deep);
}

.cc-btn {
  font-size: 10px;
}

.chat-orders {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 11px;
}

.chat-order {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 9px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
}

.chat-order:hover {
  border-color: var(--ink);
}

.chat-order strong {
  display: block;
  font-size: 10px;
}

.chat-order span {
  display: block;
  font-size: 9px;
  color: var(--muted);
  margin-top: 3px;
}

.order-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.order-right b {
  font-size: 12px;
}

.composer {
  padding: 10px 18px 14px;
  border-top: 1px solid var(--line);
  background: #fff;
  flex-shrink: 0;
}

.composer-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  border: 1px solid var(--line-strong);
  border-radius: 14px;
  padding: 8px 8px 8px 14px;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: #fff;
}

.composer-box:focus-within {
  border-color: var(--ink);
  box-shadow: 0 8px 26px rgba(38, 33, 25, 0.07);
}

.composer-box textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: 12.5px;
  line-height: 1.7;
  max-height: 110px;
  min-height: 24px;
  background: transparent;
  color: var(--ink);
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  background: var(--ink);
  color: #fff;
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 16px;
  transition: background 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: var(--brand);
}

.send-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.composer-hints {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
}

.hint-chips {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}

.hint-chips button {
  border: 1px solid var(--line);
  background: transparent;
  border-radius: 99px;
  padding: 4px 9px;
  font-size: 9px;
  color: var(--muted);
  cursor: pointer;
}

.hint-chips button:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.send-hint {
  font-size: 8.5px;
  color: var(--muted-light);
  white-space: nowrap;
}

.insight {
  padding: 18px 14px;
  gap: 12px;
  overflow-y: auto;
}

.insight-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 0 4px;
}

.insight-head .eyebrow {
  font-size: 8px;
  letter-spacing: 0.2em;
}

.insight-head h2 {
  font-size: 16px;
  margin-top: 6px;
}

.step-count {
  font-size: 9px;
  color: var(--muted);
  background: var(--bg);
  border-radius: 99px;
  padding: 4px 8px;
}

.insight :deep(.timeline-card) {
  border: none;
  padding: 0;
}

.insight-note {
  display: flex;
  gap: 7px;
  font-size: 9.5px;
  line-height: 1.7;
  color: var(--muted);
  background: #fbf8f1;
  border-radius: 9px;
  padding: 11px;
}

.insight-note .el-icon {
  color: var(--brand);
  flex-shrink: 0;
  margin-top: 1px;
}

.drawer-list {
  margin-top: 14px;
}

@media (max-width: 1240px) {
  .chat-shell {
    grid-template-columns: 232px minmax(0, 1fr);
  }

  .insight {
    display: none;
  }

  .head-actions .icon-ghost:first-child {
    display: grid;
  }
}

@media (min-width: 1241px) {
  .head-actions .icon-ghost:first-child {
    display: none;
  }
}

@media (max-width: 860px) {
  .chat-page {
    height: calc(100vh - 104px);
    padding: 8px;
  }

  .chat-shell {
    grid-template-columns: 1fr;
  }

  .sessions {
    display: none;
  }

  .mobile-only {
    display: grid;
  }

  .messages {
    padding: 16px 12px 6px;
  }

  .suggestion-grid {
    grid-template-columns: 1fr;
  }

  .welcome {
    margin: 6px auto;
  }

  .welcome > p {
    font-size: 11.5px;
  }

  .chat-compare {
    min-width: 0;
  }

  .composer {
    padding: 8px 10px 10px;
  }

  .send-hint {
    display: none;
  }

  .msg-body {
    max-width: calc(100% - 40px);
  }

  .agent-status .sep,
  .agent-status span:last-child {
    display: none;
  }
}
</style>
