<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi } from '@/api'
import type { ExecutionLog } from '@/types'
import { dateTime } from '@/utils/format'

const loading = ref(true)
const logs = ref<ExecutionLog[]>([])
const total = ref(0)
const page = ref(1)
const taskId = ref('')
const agentId = ref('')
const step = ref('')
const detail = ref<ExecutionLog | null>(null)
const drawer = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const result = await adminApi.agentLogs({
      page: page.value,
      size: 20,
      taskId: taskId.value || undefined,
      agentId: agentId.value || undefined,
      step: step.value || undefined
    })
    logs.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function openDetail(log: ExecutionLog) {
  detail.value = log
  drawer.value = true
}

function pretty(value?: string) {
  if (!value) return '—'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function changePage(value: number) {
  page.value = value
  load()
}

function statusType(status: string) {
  if (status === 'failed') return 'danger'
  if (status === 'skipped') return 'info'
  return 'success'
}
</script>

<template>
  <div class="admin-page">
    <section class="panel">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="taskId" placeholder="按 Task ID 精确查询" clearable style="width: 220px" @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="agentId" placeholder="全部 Agent" clearable style="width: 150px" @change="search">
            <el-option label="导购 Agent" value="guide-agent" />
            <el-option label="对比 Agent" value="compare-agent" />
          </el-select>
          <el-input v-model="step" placeholder="步骤，如 search_products" clearable style="width: 190px" @keyup.enter="search" />
          <el-button round @click="search">查询</el-button>
          <el-button round @click="load">刷新</el-button>
        </div>
        <span class="total">共 {{ total }} 条执行记录</span>
      </div>

      <el-table v-loading="loading" :data="logs" style="width: 100%">
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="Agent" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" round>{{ row.agentId === 'compare-agent' ? '对比 Agent' : '导购 Agent' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="步骤" min-width="180">
          <template #default="{ row }">
            <strong class="step-name">{{ row.stepName }}</strong>
            <div class="step-code">{{ row.step }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" round>{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ row.durationMs }} ms</template>
        </el-table-column>
        <el-table-column label="输出摘要" min-width="260">
          <template #default="{ row }">
            <span class="summary">{{ row.output || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Task ID" width="130">
          <template #default="{ row }">
            <span class="task-id">{{ row.taskId?.slice(0, 10) }}…</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 20"
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="20"
        :current-page="page"
        style="margin-top: 18px; justify-content: flex-end"
        @current-change="changePage"
      />
    </section>

    <el-drawer v-model="drawer" title="执行日志详情" size="520px">
      <div v-if="detail" class="log-detail">
        <div class="detail-row">
          <span>Task ID</span>
          <strong>{{ detail.taskId }}</strong>
        </div>
        <div class="detail-row">
          <span>Agent</span>
          <strong>{{ detail.agentId }}</strong>
        </div>
        <div class="detail-row">
          <span>步骤</span>
          <strong>{{ detail.stepName }}（{{ detail.step }}）</strong>
        </div>
        <div class="detail-row">
          <span>状态 / 耗时</span>
          <strong>{{ detail.status }} · {{ detail.durationMs }} ms</strong>
        </div>
        <div class="detail-row">
          <span>会话</span>
          <strong>{{ detail.conversationId || '—' }}</strong>
        </div>
        <div class="detail-row">
          <span>时间</span>
          <strong>{{ dateTime(detail.createdAt) }}</strong>
        </div>
        <h4>输入</h4>
        <pre>{{ pretty(detail.input) }}</pre>
        <h4>输出</h4>
        <pre>{{ pretty(detail.output) }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.panel {
  background: var(--surface);
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

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.total {
  font-size: 10px;
  color: var(--muted);
}

.step-name {
  display: block;
  font-size: 10.5px;
}

.step-code {
  font-size: 8.5px;
  color: var(--muted);
  margin-top: 2px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.summary {
  display: block;
  font-size: 9.5px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 360px;
}

.task-id {
  font-size: 9px;
  color: var(--muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.log-detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--line);
  font-size: 10px;
}

.detail-row span {
  color: var(--muted);
  flex-shrink: 0;
}

.detail-row strong {
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}

.log-detail h4 {
  font-size: 10px;
  color: var(--muted);
  margin: 16px 0 7px;
  letter-spacing: 0.08em;
}

.log-detail pre {
  background: var(--panel-dark-bg);
  color: #d8d0c3;
  border-radius: 9px;
  padding: 12px;
  font-size: 9.5px;
  line-height: 1.7;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
