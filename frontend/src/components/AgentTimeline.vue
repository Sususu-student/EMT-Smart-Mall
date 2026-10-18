<script setup lang="ts">
import { computed } from 'vue'
import type { AgentStep } from '@/types'

const props = withDefaults(defineProps<{ steps: AgentStep[]; title?: string; emptyText?: string }>(), {
  title: 'Agent 执行轨迹',
  emptyText: '发送一条消息，查看 Agent 的思考与工具调用过程'
})

const hasSteps = computed(() => props.steps && props.steps.length > 0)

function icon(status: string) {
  if (status === 'failed') return 'CircleCloseFilled'
  if (status === 'skipped') return 'RemoveFilled'
  return 'CircleCheckFilled'
}
</script>

<template>
  <div class="timeline-card">
    <div class="timeline-head">
      <div>
        <span class="eyebrow">EXECUTION TRACE</span>
        <h3>{{ title }}</h3>
      </div>
      <span class="live-dot" :class="{ active: hasSteps }"></span>
    </div>

    <div v-if="!hasSteps" class="timeline-empty">
      <el-icon><Cpu /></el-icon>
      <p>{{ emptyText }}</p>
    </div>

    <div v-else class="timeline">
      <div v-for="(step, index) in steps" :key="`${step.step}-${index}`" class="timeline-item" :class="step.status">
        <div class="timeline-rail">
          <span class="timeline-node">
            <el-icon><component :is="icon(step.status)" /></el-icon>
          </span>
          <span v-if="index < steps.length - 1" class="timeline-line"></span>
        </div>
        <div class="timeline-content">
          <div class="timeline-title">
            <strong>{{ step.stepName }}</strong>
            <span class="duration">{{ step.durationMs }} ms</span>
          </div>
          <p>{{ step.summary }}</p>
          <span class="step-code">{{ step.step }} · {{ step.createdAt }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.timeline-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 20px;
  min-height: 100%;
}

.timeline-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px dashed var(--line);
}

.timeline-head .eyebrow {
  font-size: 9px;
  letter-spacing: 0.18em;
}

.timeline-head h3 {
  font-size: 16px;
  margin-top: 7px;
  font-weight: 700;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d8d0c0;
  margin-top: 6px;
}

.live-dot.active {
  background: #38a169;
  box-shadow: 0 0 0 4px rgba(56, 161, 105, 0.12);
  animation: pulse 2.4s infinite;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(56, 161, 105, 0.12); }
  50% { box-shadow: 0 0 0 6px rgba(56, 161, 105, 0.05); }
}

.timeline-empty {
  padding: 44px 12px;
  text-align: center;
  color: var(--muted);
}

.timeline-empty .el-icon {
  font-size: 30px;
  color: var(--muted-light);
  margin-bottom: 12px;
}

.timeline-empty p {
  font-size: 12px;
  line-height: 1.8;
}

.timeline {
  padding-top: 18px;
}

.timeline-item {
  display: flex;
  gap: 12px;
}

.timeline-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 20px;
  flex-shrink: 0;
}

.timeline-node {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  color: #38a169;
  font-size: 16px;
  background: #fff;
  z-index: 1;
}

.timeline-item.failed .timeline-node {
  color: #d64545;
}

.timeline-item.skipped .timeline-node {
  color: var(--muted-light);
}

.timeline-line {
  flex: 1;
  width: 1px;
  background: var(--line);
  margin: 4px 0;
  min-height: 22px;
}

.timeline-content {
  padding-bottom: 18px;
  min-width: 0;
}

.timeline-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.timeline-title strong {
  font-size: 12px;
  font-weight: 600;
}

.duration {
  font-size: 10px;
  color: var(--muted-light);
  font-variant-numeric: tabular-nums;
}

.timeline-content p {
  font-size: 11px;
  color: var(--muted);
  margin-top: 4px;
  line-height: 1.6;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.step-code {
  display: block;
  margin-top: 5px;
  font-size: 9px;
  color: #b9b1a3;
  letter-spacing: 0.03em;
}
</style>
