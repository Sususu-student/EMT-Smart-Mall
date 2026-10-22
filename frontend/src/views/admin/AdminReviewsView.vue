<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api'
import type { ReviewAdmin } from '@/types'
import { dateTime } from '@/utils/format'

const loading = ref(true)
const list = ref<ReviewAdmin[]>([])
const total = ref(0)
const page = ref(1)
const status = ref<number | undefined>(undefined)
const keyword = ref('')

onMounted(load)

async function load() {
  loading.value = true
  try {
    const result = await adminApi.reviews({
      page: page.value,
      size: 10,
      status: status.value,
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

async function toggle(row: ReviewAdmin) {
  const next = row.status === 1 ? 0 : 1
  await adminApi.updateReviewStatus(row.id, next)
  ElMessage.success(next === 1 ? '评价已恢复展示' : '评价已隐藏')
  await load()
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
          <el-input v-model="keyword" placeholder="搜索评价内容或用户" clearable style="width: 220px" @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="status" placeholder="全部状态" clearable style="width: 130px" @change="search">
            <el-option label="展示中" :value="1" />
            <el-option label="已隐藏" :value="0" />
          </el-select>
          <el-button round @click="search">查询</el-button>
        </div>
        <span class="total">共 {{ total }} 条评价</span>
      </div>

      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column label="商品" min-width="200">
          <template #default="{ row }">
            <strong class="product-name">{{ row.productName }}</strong>
            <div class="sub">{{ row.productId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="用户" width="130">
          <template #default="{ row }">{{ row.username }}</template>
        </el-table-column>
        <el-table-column label="评分" width="130">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="260">
          <template #default="{ row }">
            <span class="content">{{ row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="展示" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggle(row)" />
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

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.total {
  font-size: 10px;
  color: var(--muted);
}

.product-name {
  display: block;
  font-size: 10.5px;
}

.sub {
  font-size: 8.5px;
  color: var(--muted);
  margin-top: 2px;
}

.content {
  font-size: 10px;
  line-height: 1.6;
  color: var(--muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
