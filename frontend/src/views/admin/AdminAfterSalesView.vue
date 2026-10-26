<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api'
import type { AfterSale } from '@/types'
import { dateTime, money } from '@/utils/format'

const loading = ref(true)
const list = ref<AfterSale[]>([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const dialog = ref(false)
const saving = ref(false)
const current = ref<AfterSale | null>(null)
const form = reactive({ status: 'APPROVED', adminNote: '' })

onMounted(load)

async function load() {
  loading.value = true
  try {
    const result = await adminApi.afterSales({ page: page.value, size: 10, status: status.value || undefined })
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

function openHandle(item: AfterSale) {
  current.value = item
  form.status = 'APPROVED'
  form.adminNote = ''
  dialog.value = true
}

async function submit() {
  if (!current.value) return
  saving.value = true
  try {
    await adminApi.handleAfterSale(current.value.id, { ...form })
    ElMessage.success('售后申请已处理')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function statusType(value: string) {
  if (value === 'APPROVED') return 'success'
  if (value === 'REJECTED') return 'danger'
  return 'warning'
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
        <div class="filters">
          <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="search">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已同意" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
          <el-button round @click="search">刷新</el-button>
        </div>
        <span class="total">共 {{ total }} 条售后申请</span>
      </div>

      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column label="订单" min-width="180">
          <template #default="{ row }">
            <strong class="order-no">{{ row.orderNo }}</strong>
            <div class="sub">{{ dateTime(row.createdAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="用户" width="110">
          <template #default="{ row }">{{ row.username }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ row.typeLabel }}</template>
        </el-table-column>
        <el-table-column label="原因" min-width="220">
          <template #default="{ row }">
            <span class="reason">{{ row.reason }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">{{ money(row.orderAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" round>{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理备注" min-width="150">
          <template #default="{ row }">{{ row.adminNote || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" @click="openHandle(row)">处理</el-button>
            <span v-else class="muted">已处理</span>
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

    <el-dialog v-model="dialog" title="处理售后申请" width="440px">
      <div v-if="current" class="handle-info">
        <p><span>订单</span>{{ current.orderNo }}</p>
        <p><span>类型</span>{{ current.typeLabel }}</p>
        <p><span>原因</span>{{ current.reason }}</p>
      </div>
      <el-form label-position="top">
        <el-form-item label="处理结果">
          <el-radio-group v-model="form.status">
            <el-radio value="APPROVED">同意</el-radio>
            <el-radio value="REJECTED">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="form.adminNote" type="textarea" :rows="3" maxlength="300" placeholder="向用户说明处理结果" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确认处理</el-button>
      </template>
    </el-dialog>
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
}

.total {
  font-size: 10px;
  color: var(--muted);
}

.order-no {
  font-size: 10px;
}

.sub {
  font-size: 9px;
  color: var(--muted);
  margin-top: 2px;
}

.reason {
  font-size: 10px;
  color: var(--muted);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.muted {
  font-size: 9px;
  color: var(--muted-light);
}

.handle-info {
  background: var(--surface-sunken);
  border-radius: 9px;
  padding: 12px;
  margin-bottom: 14px;
}

.handle-info p {
  display: flex;
  gap: 12px;
  font-size: 10px;
  color: var(--ink-soft);
  line-height: 1.8;
}

.handle-info span {
  width: 34px;
  color: var(--muted);
  flex-shrink: 0;
}
</style>
