<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'
import type { AgentPrompt, AgentTool, LlmStatus } from '@/types'

const loading = ref(true)
const tab = ref('model')
const status = ref<LlmStatus | null>(null)
const prompts = ref<AgentPrompt[]>([])
const tools = ref<AgentTool[]>([])
const modelSaving = ref(false)
const promptDialog = ref(false)
const promptSaving = ref(false)
const editingPrompt = ref<AgentPrompt | null>(null)
const promptContent = ref('')
const modelForm = reactive({ model: 'deepseek-flash', temperature: 0.3 })

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [llm, promptList, toolList] = await Promise.all([
      adminApi.agentModel(),
      adminApi.agentPrompts(),
      adminApi.agentTools()
    ])
    status.value = llm
    prompts.value = promptList
    tools.value = toolList
    modelForm.model = llm.model || 'deepseek-flash'
    modelForm.temperature = llm.temperature ?? 0.3
  } finally {
    loading.value = false
  }
}

async function saveModel() {
  modelSaving.value = true
  try {
    status.value = await adminApi.updateAgentModel({ ...modelForm })
    ElMessage.success(status.value.configured ? '模型配置已生效' : '配置已保存；未配置 API Key 时暂不建立连接')
  } finally {
    modelSaving.value = false
  }
}

function openPrompt(item: AgentPrompt) {
  editingPrompt.value = item
  promptContent.value = item.content
  promptDialog.value = true
}

async function savePrompt() {
  if (!editingPrompt.value) return
  if (!promptContent.value.trim()) {
    ElMessage.warning('Prompt 内容不能为空')
    return
  }
  promptSaving.value = true
  try {
    await adminApi.updateAgentPrompt(editingPrompt.value.key, promptContent.value)
    ElMessage.success('Prompt 已保存，下一次 LLM 调用生效')
    promptDialog.value = false
    prompts.value = await adminApi.agentPrompts()
  } finally {
    promptSaving.value = false
  }
}

async function resetPrompt(item: AgentPrompt) {
  await ElMessageBox.confirm(`确定将「${item.name}」恢复为默认模板吗？`, '恢复默认 Prompt', {
    confirmButtonText: '恢复默认',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await adminApi.resetAgentPrompt(item.key)
  ElMessage.success('已恢复默认模板')
  prompts.value = await adminApi.agentPrompts()
}
</script>

<template>
  <div v-loading="loading" class="agent-config">
    <section class="panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">AGENT CONFIGURATION</span>
          <h3>Agent 运行配置</h3>
          <p>管理模型参数、Prompt 模板与工具清单。API Key 仍通过环境变量注入，不在后台保存。</p>
        </div>
        <el-tag :type="status?.available ? 'success' : 'warning'" round>
          {{ status?.available ? 'LLM 在线' : '规则引擎模式' }}
        </el-tag>
      </div>

      <el-tabs v-model="tab">
        <el-tab-pane label="模型配置" name="model">
          <div class="model-grid">
            <div class="model-form">
              <el-form label-position="top">
                <el-form-item label="模型">
                  <el-select v-model="modelForm.model" allow-create filterable style="width: 100%">
                    <el-option label="deepseek-flash（推荐）" value="deepseek-flash" />
                    <el-option label="deepseek-v4-pro（更强/更贵）" value="deepseek-v4-pro" />
                  </el-select>
                </el-form-item>
                <el-form-item label="温度（0-2）">
                  <div class="temp-row">
                    <el-slider v-model="modelForm.temperature" :min="0" :max="2" :step="0.1" />
                    <b>{{ Number(modelForm.temperature).toFixed(1) }}</b>
                  </div>
                </el-form-item>
                <el-button type="primary" round :loading="modelSaving" @click="saveModel">保存模型配置</el-button>
              </el-form>
              <div class="model-note">
                <el-icon><InfoFilled /></el-icon>
                <p>
                  未配置 <code>DEEPSEEK_API_KEY</code> 时，保存只更新期望配置，不会建立真实连接；
                  设置环境变量并重启后生效。API Key 不落库，避免密钥泄露。
                </p>
              </div>
            </div>

            <div class="status-card">
              <h4>运行状态</h4>
              <div class="status-row"><span>模式</span><strong>{{ status?.mode || '—' }}</strong></div>
              <div class="status-row"><span>模型</span><strong>{{ status?.model || '—' }}</strong></div>
              <div class="status-row"><span>温度</span><strong>{{ status?.temperature ?? '—' }}</strong></div>
              <div class="status-row"><span>调用 / 失败</span><strong>{{ status?.callCount ?? 0 }} / {{ status?.failCount ?? 0 }}</strong></div>
              <div class="status-row"><span>最近错误</span><strong class="error">{{ status?.lastError || '无' }}</strong></div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`Prompt 模板 (${prompts.length})`" name="prompts">
          <div class="prompt-list">
            <article v-for="item in prompts" :key="item.key" class="prompt-card">
              <div class="prompt-head">
                <div>
                  <h4>{{ item.name }}</h4>
                  <code>{{ item.key }}</code>
                </div>
                <el-tag v-if="item.customized" size="small" type="warning" round>已自定义</el-tag>
                <el-tag v-else size="small" type="info" round>默认</el-tag>
              </div>
              <p class="prompt-desc">{{ item.description }}</p>
              <pre>{{ item.content.length > 260 ? item.content.slice(0, 260) + '…' : item.content }}</pre>
              <div class="prompt-actions">
                <el-button size="small" round @click="openPrompt(item)">编辑</el-button>
                <el-button v-if="item.customized" size="small" round @click="resetPrompt(item)">恢复默认</el-button>
              </div>
            </article>
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`工具清单 (${tools.length})`" name="tools">
          <el-table :data="tools" style="width: 100%">
            <el-table-column prop="name" label="工具" width="180" />
            <el-table-column prop="description" label="说明" min-width="260" />
            <el-table-column label="参数" min-width="220">
              <template #default="{ row }">
                <span class="param">{{ row.parameters.join(', ') }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="usedBy" label="调用方" width="120" />
            <el-table-column prop="type" label="类型" width="100" />
          </el-table>
          <div class="tool-note">
            工具是后端 Spring Bean，运行期注册需要改代码；后台只提供只读清单，避免误配置造成越权。
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="promptDialog" :title="`编辑 Prompt · ${editingPrompt?.name || ''}`" width="700px">
      <p class="dialog-desc">{{ editingPrompt?.description }}</p>
      <el-input v-model="promptContent" type="textarea" :rows="14" maxlength="6000" show-word-limit class="prompt-editor" />
      <template #footer>
        <el-button @click="promptDialog = false">取消</el-button>
        <el-button type="primary" :loading="promptSaving" @click="savePrompt">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 15px;
  padding: 22px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 8px;
}

.panel-head .eyebrow {
  font-size: 8px;
  letter-spacing: 0.2em;
}

.panel-head h3 {
  font-size: 18px;
  margin-top: 6px;
}

.panel-head p {
  font-size: 10px;
  color: var(--muted);
  line-height: 1.7;
  margin-top: 5px;
  max-width: 560px;
}

.model-grid {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 30px;
  padding-top: 10px;
}

.temp-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.temp-row b {
  width: 28px;
  text-align: right;
  font-size: 12px;
}

.model-note {
  display: flex;
  gap: 8px;
  margin-top: 18px;
  padding: 12px;
  background: #fbf8f1;
  border-radius: 9px;
  font-size: 9.5px;
  line-height: 1.8;
  color: var(--muted);
}

.model-note .el-icon {
  color: var(--brand);
  flex-shrink: 0;
}

.model-note code {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 3px;
  padding: 0 3px;
}

.status-card {
  background: #191815;
  color: #eee7db;
  border-radius: 13px;
  padding: 20px;
}

.status-card h4 {
  font-size: 12px;
  color: #ff9c72;
  margin-bottom: 12px;
}

.status-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 7px 0;
  border-bottom: 1px solid #2d2b26;
  font-size: 10px;
  color: #9d9588;
}

.status-row strong {
  color: #eee7db;
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}

.status-row .error {
  color: #ff9c72;
}

.prompt-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 8px;
}

.prompt-card {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 16px;
  background: #fdfcf9;
}

.prompt-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.prompt-head h4 {
  font-size: 12px;
}

.prompt-head code {
  font-size: 8.5px;
  color: var(--muted);
}

.prompt-desc {
  font-size: 9.5px;
  color: var(--muted);
  margin: 5px 0 9px;
}

.prompt-card pre {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px;
  font-size: 9px;
  line-height: 1.7;
  color: var(--ink-soft);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 150px;
  overflow: auto;
}

.prompt-actions {
  display: flex;
  gap: 7px;
  margin-top: 10px;
}

.param {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 9px;
  color: var(--muted);
}

.tool-note {
  margin-top: 14px;
  font-size: 9px;
  line-height: 1.7;
  color: var(--muted);
  background: #fbf8f1;
  border-radius: 8px;
  padding: 10px;
}

.dialog-desc {
  font-size: 10px;
  color: var(--muted);
  margin-bottom: 12px;
}

.prompt-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .model-grid {
    grid-template-columns: 1fr;
  }
}
</style>
