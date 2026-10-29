/**
 * 智能导购 Agent 评测脚本
 *
 * 用法：
 *   node evaluation/run-eval.mjs
 *   node evaluation/run-eval.mjs --limit=10
 *   node evaluation/run-eval.mjs --only=S01,S02,D08
 *   node evaluation/run-eval.mjs --strict
 *
 * 环境变量：
 *   EVAL_BASE      默认 http://127.0.0.1:8080
 *   EVAL_USERNAME  默认 demo
 *   EVAL_PASSWORD  默认 Demo@123
 *
 * 结果输出到 evaluation/reports/，包含 JSON、Markdown 报告和人工相关性评分 CSV。
 * 无外部依赖，Node 18+ 可直接运行。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const BASE = process.env.EVAL_BASE || 'http://127.0.0.1:8080'
const USERNAME = process.env.EVAL_USERNAME || 'demo'
const PASSWORD = process.env.EVAL_PASSWORD || 'Demo@123'
const args = process.argv.slice(2)
const strict = args.includes('--strict')
const only = (args.find((arg) => arg.startsWith('--only=')) || '').split('=')[1]
const limit = Number((args.find((arg) => arg.startsWith('--limit=')) || '').split('=')[1]) || 0

const dataset = JSON.parse(fs.readFileSync(path.join(ROOT, 'evaluation/cases.json'), 'utf8'))
let cases = dataset.cases
if (only) {
  const ids = new Set(only.split(',').map((item) => item.trim()))
  cases = cases.filter((item) => ids.has(item.id))
}
if (limit > 0) {
  cases = cases.slice(0, limit)
}

const reportDir = path.join(ROOT, 'evaluation/reports')
fs.mkdirSync(reportDir, { recursive: true })
const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)

async function request(url, options = {}) {
  const started = Date.now()
  const response = await fetch(`${BASE}${url}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    signal: AbortSignal.timeout(120000)
  })
  const text = await response.text()
  let body
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = { raw: text }
  }
  return { status: response.status, body, latencyMs: Date.now() - started }
}

async function login() {
  const result = await request('/api/user/login', {
    method: 'POST',
    body: JSON.stringify({ username: USERNAME, password: PASSWORD })
  })
  if (result.status !== 200 || result.body?.code !== 0) {
    throw new Error(`登录失败 HTTP ${result.status}: ${result.body?.message || 'unknown'}`)
  }
  return result.body.data.token
}

function check(list, name, ok, expected, actual) {
  list.push({
    name,
    ok: Boolean(ok),
    expected,
    actual
  })
}

function normalize(value) {
  if (value === null || value === undefined) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function evaluateCase(testCase, final) {
  const checks = []
  const expect = testCase.expect || {}
  const slots = final?.slots || {}

  if (expect.intent) {
    check(checks, 'intent', final?.intent === expect.intent, expect.intent, final?.intent)
  }
  if (expect.minProducts !== undefined) {
    const count = final?.products?.length || 0
    check(checks, 'minProducts', count >= expect.minProducts, `>=${expect.minProducts}`, count)
  }
  if (expect.compare !== undefined) {
    check(checks, 'compare', Boolean(final?.compare) === expect.compare, expect.compare, Boolean(final?.compare))
  }
  if (expect.compareProducts !== undefined) {
    const count = final?.compare?.products?.length || 0
    check(checks, 'compareProducts', count === expect.compareProducts, expect.compareProducts, count)
  }
  if (expect.ordersMin !== undefined) {
    const count = final?.orders?.length || 0
    check(checks, 'ordersMin', count >= expect.ordersMin, `>=${expect.ordersMin}`, count)
  }
  // 场景搭配：校验是否产出方案、件数是否达标、是否控制在预算内
  if (expect.scenePlan !== undefined) {
    check(checks, 'scenePlan', Boolean(final?.scenePlan) === expect.scenePlan, expect.scenePlan, Boolean(final?.scenePlan))
  }
  if (expect.sceneItemsMin !== undefined) {
    const count = final?.scenePlan?.items?.length || 0
    check(checks, 'sceneItemsMin', count >= expect.sceneItemsMin, `>=${expect.sceneItemsMin}`, count)
  }
  if (expect.sceneWithinBudget !== undefined) {
    const ok = final?.scenePlan ? Boolean(final.scenePlan.withinBudget) : false
    check(checks, 'sceneWithinBudget', ok === expect.sceneWithinBudget, expect.sceneWithinBudget, ok)
  }
  if (expect.sceneReasonMinLength !== undefined) {
    const len = (final?.scenePlan?.reason || '').length
    check(checks, 'sceneReasonMinLength', len >= expect.sceneReasonMinLength,
      `>=${expect.sceneReasonMinLength}`, len)
  }
  if (expect.clarifyOptionsMin !== undefined) {
    const count = final?.clarifyOptions?.length || 0
    check(checks, 'clarifyOptionsMin', count >= expect.clarifyOptionsMin, `>=${expect.clarifyOptionsMin}`, count)
  }
  if (expect.replyContains) {
    for (const text of expect.replyContains) {
      check(checks, `reply:${text}`, (final?.reply || '').includes(text), `包含「${text}」`, final?.reply || '')
    }
  }
  if (expect.slots) {
    for (const [key, expected] of Object.entries(expect.slots)) {
      const actual = slots[key]
      if (key === 'features') {
        const actualFeatures = Array.isArray(actual) ? actual : []
        for (const feature of expected) {
          check(checks, `slots.features:${feature}`, actualFeatures.includes(feature), feature, actualFeatures)
        }
      } else if (typeof expected === 'number') {
        check(checks, `slots.${key}`, Number(actual) === expected, expected, actual)
      } else {
        check(checks, `slots.${key}`, normalize(actual) === normalize(expected), expected, actual)
      }
    }
  }
  return checks
}

function csvEscape(value) {
  const text = String(value ?? '')
  return `"${text.replaceAll('"', '""')}"`
}

async function main() {
  const token = await login()
  const auth = { Authorization: `Bearer ${token}` }
  const statusResult = await request('/api/agent/status')
  const llmStatus = statusResult.body?.data || {}
  const mode = llmStatus.available ? 'llm' : 'rule-engine'

  console.log(`评测目标: ${BASE}`)
  console.log(`当前模式: ${mode}${llmStatus.model ? ` · ${llmStatus.model}` : ''}`)
  console.log(`用例数量: ${cases.length}`)
  if (!llmStatus.available) {
    console.log('提示: 未配置 DEEPSEEK_API_KEY，本次是规则引擎基线。配置 Key 并重启后可跑同一套题做对比。')
  }

  const startedAt = new Date()
  const results = []
  const manualRows = []
  const totals = {
    cases: 0,
    passed: 0,
    checks: 0,
    checkPassed: 0,
    intentChecks: 0,
    intentPassed: 0,
    slotChecks: 0,
    slotPassed: 0,
    productChecks: 0,
    productPassed: 0,
    replyChecks: 0,
    replyPassed: 0,
    degradedCases: 0,
    latencies: []
  }

  for (const testCase of cases) {
    let conversationId
    let final = null
    let error = null
    const turns = []
    const caseStarted = Date.now()

    for (const message of testCase.turns) {
      try {
        const body = { message }
        if (conversationId) body.conversationId = conversationId
        const response = await request('/api/agent/chat', {
          method: 'POST',
          headers: auth,
          body: JSON.stringify(body)
        })
        if (response.status !== 200 || response.body?.code !== 0) {
          error = `HTTP ${response.status}: ${response.body?.message || 'unknown'}`
          break
        }
        final = response.body.data
        conversationId = final.conversationId
        turns.push({
          message,
          latencyMs: response.latencyMs,
          intent: final.intent,
          reply: final.reply,
          degraded: final.degraded,
          products: (final.products || []).map((item) => ({
            id: item.product?.id,
            name: item.product?.name,
            price: item.product?.price,
            reason: item.reason,
            fallback: item.reasonFallback
          })),
          slots: final.slots,
          compare: final.compare
            ? {
                recommendedId: final.compare.recommendedId,
                recommendedName: final.compare.recommendedName,
                scores: final.compare.scores
              }
            : null,
          orders: (final.orders || []).map((order) => ({
            orderNo: order.orderNo,
            status: order.status,
            amount: order.totalAmount
          }))
        })
      } catch (exception) {
        error = exception.message
        break
      }
    }

    const checks = error ? [{ name: 'request', ok: false, expected: 'HTTP 200', actual: error }] : evaluateCase(testCase, final)
    const passed = checks.every((item) => item.ok)
    totals.cases++
    if (passed) totals.passed++
    if (final?.degraded) totals.degradedCases++

    for (const item of checks) {
      totals.checks++
      if (item.ok) totals.checkPassed++
      if (item.name === 'intent') {
        totals.intentChecks++
        if (item.ok) totals.intentPassed++
      }
      if (item.name.startsWith('slots.')) {
        totals.slotChecks++
        if (item.ok) totals.slotPassed++
      }
      if (item.name === 'minProducts' || item.name === 'compareProducts' || item.name === 'ordersMin') {
        totals.productChecks++
        if (item.ok) totals.productPassed++
      }
      if (item.name.startsWith('reply:')) {
        totals.replyChecks++
        if (item.ok) totals.replyPassed++
      }
    }

    const top = (final?.products || []).slice(0, 5).map((item) => {
      return `${item.product?.name || ''}（¥${item.product?.price ?? ''}）${item.reason || ''}`
    })
    manualRows.push([
      testCase.id,
      testCase.group,
      testCase.turns.join(' | '),
      top.join(' || '),
      '',
      passed ? '' : checks.filter((item) => !item.ok).map((item) => item.name).join(';')
    ])

    const latencyMs = Date.now() - caseStarted
    totals.latencies.push(latencyMs)
    results.push({
      id: testCase.id,
      group: testCase.group,
      turns: testCase.turns,
      passed,
      checks,
      error,
      latencyMs,
      conversationId,
      final: final
        ? {
            intent: final.intent,
            reply: final.reply,
            degraded: final.degraded,
            slots: final.slots,
            products: (final.products || []).map((item) => ({
              id: item.product?.id,
              name: item.product?.name,
              price: item.product?.price,
              reason: item.reason,
              fallback: item.reasonFallback
            })),
            compare: final.compare,
            orders: final.orders
          }
        : null,
      turnDetails: turns
    })

    console.log(`${passed ? 'PASS' : 'FAIL'} ${testCase.id.padEnd(4)} ${testCase.group.padEnd(10)} ${latencyMs}ms ${passed ? '' : `← ${checks.filter((item) => !item.ok).map((item) => item.name).join(', ')}`}`)
  }

  const apiResults = []
  for (const item of dataset.apiChecks || []) {
    try {
      if (item.type === 'productTotal') {
        const response = await request('/api/product/list?page=1&size=1')
        const total = response.body?.data?.total || 0
        apiResults.push({ ...item, ok: response.status === 200 && total >= 130, actual: total })
      } else if (item.type === 'compareSameCategory' || item.type === 'compareCrossCategory') {
        const response = await request('/api/agent/compare', {
          method: 'POST',
          body: JSON.stringify({ productIds: item.ids })
        })
        const expected = item.expectStatus || 200
        apiResults.push({
          ...item,
          ok: response.status === expected,
          actual: `HTTP ${response.status} ${response.body?.message || ''}`.trim(),
          detail: response.body?.data
            ? {
                products: response.body.data.products?.map((product) => product.name),
                scores: response.body.data.scores?.map((score) => score.totalScore)
              }
            : undefined
        })
      } else if (item.type === 'ratingConsistency') {
        const product = await request(`/api/product/${item.productId}`)
        const reviews = await request(`/api/product/${item.productId}/reviews?page=1&size=50`)
        const list = reviews.body?.data?.records || []
        const average = list.length ? list.reduce((sum, review) => sum + review.rating, 0) / list.length : 0
        const shown = Number(product.body?.data?.rating || 0)
        apiResults.push({
          ...item,
          ok: Boolean(list.length) && Math.abs(average - shown) < 0.05,
          actual: `展示 ${shown} / 评价 ${list.length} 条均分 ${average.toFixed(2)}`
        })
      } else if (item.type === 'llmStatus') {
        apiResults.push({ ...item, ok: statusResult.status === 200, actual: `${mode}${llmStatus.model ? ` / ${llmStatus.model}` : ''}` })
      }
    } catch (exception) {
      apiResults.push({ ...item, ok: false, actual: exception.message })
    }
  }

  const latencies = totals.latencies.slice().sort((a, b) => a - b)
  const avgLatency = latencies.length ? Math.round(latencies.reduce((sum, item) => sum + item, 0) / latencies.length) : 0
  const p95Latency = latencies.length ? latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * 0.95))] : 0
  const summary = {
    startedAt: startedAt.toISOString(),
    finishedAt: new Date().toISOString(),
    base: BASE,
    mode,
    model: llmStatus.model,
    total: totals.cases,
    passed: totals.passed,
    failed: totals.cases - totals.passed,
    passRate: totals.cases ? Number(((totals.passed / totals.cases) * 100).toFixed(1)) : 0,
    checks: { total: totals.checks, passed: totals.checkPassed },
    intentAccuracy: totals.intentChecks
      ? Number(((totals.intentPassed / totals.intentChecks) * 100).toFixed(1))
      : null,
    slotAccuracy: totals.slotChecks
      ? Number(((totals.slotPassed / totals.slotChecks) * 100).toFixed(1))
      : null,
    productCompliance: totals.productChecks
      ? Number(((totals.productPassed / totals.productChecks) * 100).toFixed(1))
      : null,
    replyCompliance: totals.replyChecks
      ? Number(((totals.replyPassed / totals.replyChecks) * 100).toFixed(1))
      : null,
    degradedCases: totals.degradedCases,
    avgLatencyMs: avgLatency,
    p95LatencyMs: p95Latency,
    apiChecksPassed: apiResults.filter((item) => item.ok).length,
    apiChecksTotal: apiResults.length
  }

  const groups = {}
  for (const result of results) {
    const group = (groups[result.group] ||= { total: 0, passed: 0 })
    group.total++
    if (result.passed) group.passed++
  }

  const report = {
    summary,
    groups,
    cases: results,
    apiChecks: apiResults
  }
  const jsonPath = path.join(reportDir, `report-${stamp}.json`)
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2), 'utf8')

  const lines = []
  lines.push('# 智能导购 Agent 评测报告')
  lines.push('')
  lines.push(`- 时间：${summary.startedAt}`)
  lines.push(`- 服务：${summary.base}`)
  lines.push(`- 模式：${summary.mode}${summary.model ? `（${summary.model}）` : ''}`)
  lines.push(`- 用例：${summary.total} 条，通过 ${summary.passed} 条，失败 ${summary.failed} 条`)
  lines.push(`- 通过率：${summary.passRate}%`)
  lines.push(`- 意图准确率：${summary.intentAccuracy ?? '—'}%`)
  lines.push(`- 槽位字段准确率：${summary.slotAccuracy ?? '—'}%`)
  lines.push(`- 商品结果符合率：${summary.productCompliance ?? '—'}%`)
  lines.push(`- 回复条件符合率：${summary.replyCompliance ?? '—'}%`)
  lines.push(`- 平均/ P95 耗时：${summary.avgLatencyMs}ms / ${summary.p95LatencyMs}ms`)
  lines.push(`- 接口检查：${summary.apiChecksPassed}/${summary.apiChecksTotal}`)
  lines.push('')
  if (mode !== 'llm') {
    lines.push('> 本次未启用 DeepSeek，结果为规则引擎基线；配置 `DEEPSEEK_API_KEY` 并重启后，可用同一套用例复测 LLM 模式。')
    lines.push('')
  }
  lines.push('## 分组结果')
  lines.push('')
  lines.push('| 分组 | 通过/总数 | 通过率 |')
  lines.push('| --- | --- | --- |')
  for (const [name, value] of Object.entries(groups)) {
    lines.push(`| ${name} | ${value.passed}/${value.total} | ${((value.passed / value.total) * 100).toFixed(1)}% |`)
  }
  lines.push('')
  lines.push('## 接口检查')
  lines.push('')
  for (const item of apiResults) {
    lines.push(`- ${item.ok ? '✅' : '❌'} ${item.id} ${item.label}：${item.actual || ''}`)
  }
  lines.push('')
  const failures = results.filter((result) => !result.passed)
  lines.push(`## 未通过用例（${failures.length}）`)
  lines.push('')
  if (!failures.length) {
    lines.push('无。')
  } else {
    for (const failure of failures) {
      lines.push(`### ${failure.id} · ${failure.group}`)
      lines.push('')
      lines.push(`- 对话：${failure.turns.join(' → ')}`)
      lines.push(`- 错误：${failure.error || '无'}`)
      for (const item of failure.checks.filter((entry) => !entry.ok)) {
        lines.push(`- ${item.name}：期望 ${normalize(item.expected)}，实际 ${normalize(item.actual).slice(0, 400)}`)
      }
      lines.push('')
    }
  }
  lines.push('## 说明')
  lines.push('')
  lines.push('- 自动检查覆盖意图、槽位、商品数量、对比结果、订单数量、回复关键词。')
  lines.push('- 推荐相关性需要人工判断，见同目录 `manual-review-*.csv`，请按 1-5 分填写 `relevance_1_5`。')
  lines.push('- 规则引擎基线可用于回归；LLM 模式的真实准确率需配置 Key 后重新运行。')

  const mdPath = path.join(reportDir, `report-${stamp}.md`)
  fs.writeFileSync(mdPath, lines.join('\n'), 'utf8')

  const csvPath = path.join(reportDir, `manual-review-${stamp}.csv`)
  const csv = [
    ['case_id', 'group', 'turns', 'top_products', 'relevance_1_5', 'failed_checks'].map(csvEscape).join(','),
    ...manualRows.map((row) => row.map(csvEscape).join(','))
  ].join('\n')
  fs.writeFileSync(csvPath, `\ufeff${csv}`, 'utf8')

  console.log('')
  console.log(`总通过率: ${summary.passRate}% (${summary.passed}/${summary.total})`)
  console.log(`意图准确率: ${summary.intentAccuracy ?? '—'}% · 槽位准确率: ${summary.slotAccuracy ?? '—'}%`)
  console.log(`接口检查: ${summary.apiChecksPassed}/${summary.apiChecksTotal}`)
  console.log(`报告: ${jsonPath}`)
  console.log(`Markdown: ${mdPath}`)
  console.log(`人工评分表: ${csvPath}`)

  if (strict && summary.failed > 0) {
    process.exitCode = 1
  }
}

main().catch((error) => {
  console.error('评测失败：', error.message)
  process.exitCode = 1
})
