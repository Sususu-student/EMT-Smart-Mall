<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'
import type { Category } from '@/types'

const loading = ref(true)
const list = ref<Category[]>([])
const dialog = ref(false)
const saving = ref(false)
const editingId = ref('')
const form = reactive({
  name: '',
  parentId: '',
  icon: '',
  description: '',
  sortOrder: 10,
  status: 1
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = await adminApi.categories()
  } finally {
    loading.value = false
  }
}

const roots = () => list.value.filter((item) => !item.parentId)

function parentName(id?: string) {
  if (!id) return '顶级分类'
  return list.value.find((item) => item.id === id)?.name || id
}

function openCreate() {
  editingId.value = ''
  Object.assign(form, { name: '', parentId: '', icon: '', description: '', sortOrder: 10, status: 1 })
  dialog.value = true
}

function openEdit(item: Category) {
  editingId.value = item.id
  Object.assign(form, {
    name: item.name,
    parentId: item.parentId || '',
    icon: item.icon || '',
    description: item.description || '',
    sortOrder: item.sortOrder ?? 10,
    status: 1
  })
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写分类名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await adminApi.updateCategory(editingId.value, { ...form })
      ElMessage.success('分类已更新')
    } else {
      await adminApi.createCategory({ ...form })
      ElMessage.success('分类已创建')
    }
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(item: Category) {
  await ElMessageBox.confirm(`确定删除分类「${item.name}」吗？`, '删除分类', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await adminApi.deleteCategory(item.id)
  ElMessage.success('分类已删除')
  await load()
}
</script>

<template>
  <div class="admin-page">
    <section class="panel">
      <div class="toolbar">
        <div>
          <h3 class="title">商品分类</h3>
          <p class="desc">分类是 Agent 检索与商品参数维度定义的基础，含子分类时不能直接删除。</p>
        </div>
        <el-button type="primary" round @click="openCreate">+ 新增分类</el-button>
      </div>

      <el-table v-loading="loading" :data="list" style="width: 100%" row-key="id">
        <el-table-column label="分类" min-width="220">
          <template #default="{ row }">
            <div class="cat-cell">
              <span class="cat-icon">{{ row.icon || '📦' }}</span>
              <strong>{{ row.name }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="父分类" width="150">
          <template #default="{ row }">{{ parentName(row.parentId) }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.parentId ? 'info' : 'warning'" round>
              {{ row.parentId ? '子分类' : '顶级分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="220">
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialog" :title="editingId ? '编辑分类' : '新增分类'" width="480px">
      <el-form label-position="top">
        <el-form-item label="分类名称">
          <el-input v-model="form.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="父分类">
          <el-select v-model="form.parentId" clearable placeholder="顶级分类" style="width: 100%">
            <el-option v-for="item in roots().filter((root) => root.id !== editingId)" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="图标（emoji）">
            <el-input v-model="form.icon" maxlength="20" placeholder="🎧" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.title {
  font-size: 15px;
}

.desc {
  font-size: 10px;
  color: var(--muted);
  margin-top: 5px;
}

.cat-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cat-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--bg);
  display: grid;
  place-items: center;
  font-size: 15px;
}

.cat-cell strong {
  font-size: 11px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}
</style>
