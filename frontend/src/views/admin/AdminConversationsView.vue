<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi } from '@/api'
import type { AdminConversation, Turn } from '@/types'
import { dateTime, intentLabel } from '@/utils/format'

const loading = ref(true)
const list = ref<AdminConversation[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const drawer = ref(false)
const current = ref<AdminConversation | null>(null)
const turns = ref<Turn[]>([])
const turnsLoading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const result = await adminApi.conversations({
      page: page.value,
      size: 10,
      keyword: keyword.value || undefined
    })
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function openTurns(item: AdminConversation) {
  current.value = item
  drawer.value = true
  turnsLoading.value = true
  try {
    turns.value = await adminApi.conversationTurns(item.id)
  } finally {
    turnsLoading.value = false
  }
}

function changePage(value: number) {
  page.value = value
  load()
}
</script>

<template>
  <div class="admin-page">
    <section class="panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索会话标题" clearable style="width: 240px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="right">
          <el-button round @click="search">查询</el-button>
          <span class="total">共 {{ total }} 个会话</span>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column label="会话" min-width="260">
          <template #default="{ row }">
            <strong class="title">{{ row.title }}</strong>
            <div class="sub">{{ row.id }}</div>
          </template>
        </el-table-column>
        <el-table-column label="用户" width="140">
          <template #default="{ row }">
            <div>{{ row.nickname || row.username }}</div>
            <div class="sub">@{{ row.username }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="turnCount" label="轮次" width="80" />
        <el-table-column label="最近更新" width="150">
          <template #default="{ row }">{{ dateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTurns(row)">查看对话</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 10"
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="10"
        :current-page="page"
        style="margin-top: 18px; justify-content: flex-end"
        @current-change="changePage"
      />
    </section>

    <el-drawer v-model="drawer" title="对话审查" size="560px">
      <div v-if="current" class="conversation-head">
        <h3>{{ current.title }}</h3>
        <span>{{ current.nickname || current.username }} · {{ current.turnCount }} 轮 · {{ dateTime(current.updatedAt) }}</span>
      </div>
      <div v-loading="turnsLoading" class="turns">
        <div v-for="turn in turns" :key="turn.id" class="turn" :class="turn.role">
          <div class="turn-meta">
            <strong>{{ turn.role === 'user' ? '用户' : 'Agent' }}</strong>
            <span v-if="turn.intent">{{ intentLabel(turn.intent) }}</span>
            <span>{{ dateTime(turn.createdAt) }}</span>
          </div>
          <p>{{ turn.content }}</p>
          <div v-if="turn.products?.length" class="turn-products">
            <span v-for="item in turn.products" :key="item.product.id">{{ item.product.name }}</span>
          </div>
        </div>
        <div v-if="!turnsLoading && !turns.length" class="empty">暂无对话内容</div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 15px;
  padding: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.total {
  font-size: 10px;
  color: var(--muted);
}

.title {
  display: block;
  font-size: 10.5px;
}

.sub {
  font-size: 8.5px;
  color: var(--muted);
  margin-top: 2px;
}

.conversation-head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 14px;
}

.conversation-head h3 {
  font-size: 15px;
}

.conversation-head span {
  display: block;
  font-size: 9px;
  color: var(--muted);
  margin-top: 4px;
}

.turns {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 200px;
}

.turn {
  background: #fbf8f1;
  border-radius: 10px;
  padding: 11px 12px;
}

.turn.user {
  background: #1b1a17;
  color: #fff;
}

.turn-meta {
  display: flex;
  gap: 8px;
  font-size: 8.5px;
  color: var(--muted);
  margin-bottom: 5px;
}

.turn.user .turn-meta {
  color: #9d9588;
}

.turn-meta strong {
  color: var(--ink);
  font-size: 9px;
}

.turn.user .turn-meta strong {
  color: #fff;
}

.turn p {
  font-size: 11px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.turn-products {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
  margin-top: 7px;
}

.turn-products span {
  font-size: 8.5px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 99px;
  padding: 2px 7px;
  color: var(--muted);
}
</style>
