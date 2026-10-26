<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'
import type { User } from '@/types'
import { dateTime } from '@/utils/format'

const loading = ref(true)
const users = ref<User[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')

onMounted(load)

async function load() {
  loading.value = true
  try {
    const result = await adminApi.users({ page: page.value, size: 10, keyword: keyword.value || undefined })
    users.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function toggleStatus(user: User) {
  const next = (user.status ?? 1) === 1 ? 0 : 1
  await ElMessageBox.confirm(next === 1 ? `启用用户「${user.username}」？` : `禁用用户「${user.username}」？`, '账号状态', {
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  await adminApi.updateUserStatus(user.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  await load()
}

function changePage(value: number) {
  page.value = value
  load()
}

function roleLabel(role: string) {
  if (role === 'ADMIN') return '管理员'
  if (role === 'SELLER') return '卖家'
  return '买家'
}

function roleTag(role: string) {
  if (role === 'ADMIN') return 'danger'
  if (role === 'SELLER') return 'warning'
  return 'info'
}

/** 管理员重置密码：弹出新生成的临时密码，仅显示一次 */
async function resetPassword(user: User) {
  await ElMessageBox.confirm(
    `确定重置用户「${user.username}」的密码吗？系统会生成一个新的临时密码。`,
    '重置密码',
    { confirmButtonText: '确认重置', cancelButtonText: '取消', type: 'warning' }
  )
  const temp = await adminApi.resetUserPassword(user.id)
  await ElMessageBox.alert(
    `用户「${user.username}」的新临时密码是：\n\n${temp}\n\n请复制并转告用户，关闭后无法再次查看。`,
    '重置成功',
    { confirmButtonText: '我已记录', type: 'success' }
  )
}
</script>

<template>
  <div class="admin-page">
    <section class="panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索用户名、昵称或邮箱" clearable style="width: 260px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="right">
          <el-button round @click="search">查询</el-button>
          <span class="total">共 {{ total }} 位用户</span>
        </div>
      </div>

      <el-table v-loading="loading" :data="users" style="width: 100%">
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="avatar">{{ (row.nickname || row.username || '智').slice(0, 1) }}</span>
              <div>
                <strong>{{ row.nickname || row.username }}</strong>
                <span>@{{ row.username }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)" size="small" round>
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="150">
          <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(row.status ?? 1) === 1 ? 'success' : 'danger'" size="small" round>
              {{ (row.status ?? 1) === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="resetPassword(row)">重置密码</el-button>
            <el-button link type="primary" :disabled="row.role === 'ADMIN'" @click="toggleStatus(row)">
              {{ (row.status ?? 1) === 1 ? '禁用' : '启用' }}
            </el-button>
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

.right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.total {
  font-size: 10px;
  color: var(--muted);
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 9px;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-deep);
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
}

.user-cell strong {
  display: block;
  font-size: 11px;
}

.user-cell span {
  display: block;
  font-size: 9px;
  color: var(--muted);
}
</style>
