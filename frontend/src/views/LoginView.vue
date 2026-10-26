<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, productApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import type { Product } from '@/types'
import { money } from '@/utils/format'
import ProductImage from '@/components/ProductImage.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()

const mode = ref<'login' | 'register'>('login')
const forgotDialog = ref(false)
const forgotSaving = ref(false)
const forgotForm = reactive({ username: '', email: '', newPassword: '', confirm: '' })
const loading = ref(false)
const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', confirm: '', nickname: '', email: '', role: 'USER' })

/* ===== 左侧展示区轮播：每 3 秒切换一个真实商品 + AGENT 推荐理由 ===== */
const showcase = ref<Product[]>([])
const showcaseIndex = ref(0)
const showcaseReady = ref(false)
let showcaseTimer: number | undefined

const current = computed<Product | null>(() => showcase.value[showcaseIndex.value] || null)

/** 用商品自身参数拼一句“像 Agent 说的”推荐理由，避免硬编码假数据 */
const currentReason = computed(() => {
  const p = current.value
  if (!p) return ''
  const attrs = p.attributes || {}
  const parts: string[] = [`${money(p.price)} 的价位`]
  for (const key of Object.keys(attrs).slice(0, 2)) {
    const value = attrs[key]
    if (value !== null && value !== undefined && String(value).trim()) {
      parts.push(`${key} ${value}`)
    }
  }
  if (p.rating) parts.push(`${Number(p.rating).toFixed(1)} 分口碑`)
  return `“${parts.join('，')}，在同价位里参数比较均衡。”`
})

function nextShowcase() {
  if (showcase.value.length < 2) return
  showcaseIndex.value = (showcaseIndex.value + 1) % showcase.value.length
}

function goShowcase(index: number) {
  showcaseIndex.value = index
  restartShowcaseTimer()
}

function restartShowcaseTimer() {
  if (showcaseTimer) window.clearInterval(showcaseTimer)
  showcaseTimer = window.setInterval(nextShowcase, 3000)
}

onMounted(async () => {
  try {
    const result = await productApi.list({ page: 1, size: 8, sort: 'sales' })
    showcase.value = result.records || []
  } catch {
    showcase.value = []
  } finally {
    showcaseReady.value = true
    restartShowcaseTimer()
  }
})

onBeforeUnmount(() => {
  if (showcaseTimer) window.clearInterval(showcaseTimer)
})

async function submitLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const user = await auth.login(loginForm.username, loginForm.password)
    await cart.refresh()
    ElMessage.success(`欢迎回来，${user.nickname || user.username}`)
    const fallback = user.role === 'ADMIN' ? '/admin' : user.role === 'SELLER' ? '/seller' : undefined
    redirect(!route.query.redirect ? fallback : undefined)
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  if (!registerForm.username || !registerForm.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  if (registerForm.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  if (registerForm.password !== registerForm.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await auth.register({
      username: registerForm.username,
      password: registerForm.password,
      nickname: registerForm.nickname || undefined,
      email: registerForm.email || undefined,
      role: registerForm.role
    })
    await cart.refresh()
    if (registerForm.role === 'SELLER') {
      ElMessage.success('卖家账号已创建，去发布你的第一件商品吧')
      router.replace('/seller')
      return
    }
    ElMessage.success('注册成功，开始你的第一次智能导购吧')
    redirect()
  } finally {
    loading.value = false
  }
}

function redirect(fallback?: string) {
  const target = (route.query.redirect as string) || fallback || '/'
  router.replace(target)
}

function openForgot() {
  forgotForm.username = loginForm.username || ''
  forgotForm.email = ''
  forgotForm.newPassword = ''
  forgotForm.confirm = ''
  forgotDialog.value = true
}

async function submitForgot() {
  if (!forgotForm.username.trim() || !forgotForm.email.trim()) {
    ElMessage.warning('请填写用户名和注册邮箱')
    return
  }
  if (forgotForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (forgotForm.newPassword !== forgotForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  forgotSaving.value = true
  try {
    await authApi.resetPassword({
      username: forgotForm.username.trim(),
      email: forgotForm.email.trim(),
      newPassword: forgotForm.newPassword
    })
    forgotDialog.value = false
    loginForm.username = forgotForm.username.trim()
    loginForm.password = ''
    ElMessage.success('密码已重置，请用新密码登录')
  } finally {
    forgotSaving.value = false
  }
}

function useDemo(role: 'user' | 'admin' | 'seller') {
  mode.value = 'login'
  const accounts = {
    user: { username: 'demo', password: 'Demo@123' },
    seller: { username: 'seller', password: 'Seller@123' },
    admin: { username: 'admin', password: 'Admin@123' }
  }
  loginForm.username = accounts[role].username
  loginForm.password = accounts[role].password
  ElMessage.success('已填入演示账号，点击登录即可')
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-visual">
      <router-link to="/" class="auth-brand">
        <span class="brand-mark">智</span>
        <span>
          <strong>智选商城</strong>
          <small>SMART GUIDE MALL</small>
        </span>
      </router-link>

      <div class="visual-copy">
        <span class="eyebrow">CONVERSATIONAL COMMERCE</span>
        <h1 class="display">把选择困难，<br />留给 Agent。</h1>
        <p>描述预算、场景和偏好，它会追问、检索、解释，并把候选商品放进同一张对比表。</p>
      </div>

      <div class="visual-cards">
        <Transition name="showcase" mode="out-in">
          <div v-if="current" :key="current.id" class="visual-card">
            <span class="mini-label">AGENT 推荐理由</span>
            <p>{{ currentReason }}</p>
            <span class="verified">已校验价格 / 库存 / 参数</span>
          </div>
          <div v-else key="placeholder" class="visual-card">
            <span class="mini-label">AGENT 推荐理由</span>
            <p>“IPX5 防水与 24h 续航覆盖跑步需求，4.2g 佩戴负担小。”</p>
            <span class="verified">已校验价格 / 库存 / 参数</span>
          </div>
        </Transition>

        <Transition name="showcase" mode="out-in">
          <div v-if="current" :key="current.id" class="visual-product">
            <ProductImage :src="current.image" :fallback-src="current.imageFallback" :alt="current.name" ratio="4 / 5" />
            <div class="product-caption">
              <strong>{{ current.name }}</strong>
              <span>{{ money(current.price) }}</span>
            </div>
          </div>
          <div v-else key="placeholder" class="visual-product">
            <ProductImage src="/img/headphone.svg" alt="智能导购精选" ratio="4 / 5" />
          </div>
        </Transition>
      </div>

      <div v-if="showcase.length > 1" class="showcase-dots">
        <button
          v-for="(item, index) in showcase"
          :key="item.id"
          class="showcase-dot"
          :class="{ active: index === showcaseIndex }"
          :aria-label="`查看第 ${index + 1} 个推荐商品`"
          @click="goShowcase(index)"
        ></button>
      </div>

      <div class="visual-foot">
        <span>130+ 商品</span><i></i><span>13 个品类</span><i></i><span>2 个 Agent</span>
      </div>
    </div>

    <div class="auth-form-wrap">
      <div class="auth-form">
        <router-link to="/" class="back-home"><el-icon><ArrowLeft /></el-icon> 返回商城</router-link>

        <div class="form-head">
          <h2>{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h2>
          <p>{{ mode === 'login' ? '登录后同步购物车、订单与导购偏好。' : '注册即可体验对话导购与商品对比。' }}</p>
        </div>

        <div class="mode-tabs">
          <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
          <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
        </div>

        <form v-if="mode === 'login'" class="form" @submit.prevent="submitLogin">
          <label>
            <span>用户名</span>
            <el-input v-model="loginForm.username" size="large" placeholder="请输入用户名" autocomplete="username">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </label>
          <label>
            <span>密码</span>
            <el-input
              v-model="loginForm.password"
              type="password"
              size="large"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </label>
          <div class="form-assist">
            <button type="button" class="forgot-link" @click="openForgot">忘记密码？</button>
          </div>
          <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit">
            登录
          </el-button>
        </form>

        <form v-else class="form" @submit.prevent="submitRegister">
          <div class="role-picker">
            <span class="role-label">注册为</span>
            <div class="role-options">
              <label class="role-option" :class="{ active: registerForm.role === 'USER' }">
                <input v-model="registerForm.role" type="radio" value="USER" name="role" />
                <el-icon><ShoppingBag /></el-icon>
                <div>
                  <strong>买家</strong>
                  <span>浏览商品 · 对话导购 · 下单购买</span>
                </div>
                <el-icon v-if="registerForm.role === 'USER'" class="role-check"><CircleCheckFilled /></el-icon>
              </label>
              <label class="role-option" :class="{ active: registerForm.role === 'SELLER' }">
                <input v-model="registerForm.role" type="radio" value="SELLER" name="role" />
                <el-icon><Shop /></el-icon>
                <div>
                  <strong>卖家</strong>
                  <span>发布商品 · 管理库存 · 查看订单</span>
                </div>
                <el-icon v-if="registerForm.role === 'SELLER'" class="role-check"><CircleCheckFilled /></el-icon>
              </label>
            </div>
          </div>

          <label>
            <span>用户名</span>
            <el-input v-model="registerForm.username" size="large" placeholder="3-20 位字母、数字或下划线">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </label>
          <div class="form-row">
            <label>
              <span>密码</span>
              <el-input v-model="registerForm.password" type="password" show-password size="large" placeholder="至少 6 位" />
            </label>
            <label>
              <span>确认密码</span>
              <el-input v-model="registerForm.confirm" type="password" show-password size="large" placeholder="再次输入" />
            </label>
          </div>
          <div class="form-row">
            <label>
              <span>昵称（选填）</span>
              <el-input v-model="registerForm.nickname" size="large" placeholder="怎么称呼你" />
            </label>
            <label>
              <span>邮箱（选填）</span>
              <el-input v-model="registerForm.email" size="large" placeholder="you@example.com" />
            </label>
          </div>
          <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit">
            注册并开始逛
          </el-button>
        </form>

        <div class="demo-box">
          <span class="demo-title">演示账号</span>
          <div class="demo-actions">
            <button @click="useDemo('user')">用户 demo / Demo@123</button>
            <button @click="useDemo('seller')">卖家 seller / Seller@123</button>
            <button @click="useDemo('admin')">管理员 admin / Admin@123</button>
          </div>
        </div>

        <p class="terms">登录即表示同意本演示项目的使用说明。演示环境不产生真实交易。</p>
      </div>
    </div>

    <el-dialog v-model="forgotDialog" title="重置密码" width="440px">
      <p class="forgot-tip">
        输入你的用户名和注册时填写的邮箱，校验通过后即可设置新密码。
      </p>
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="forgotForm.username" placeholder="你的用户名" />
        </el-form-item>
        <el-form-item label="注册邮箱">
          <el-input v-model="forgotForm.email" placeholder="注册时填写的邮箱" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="forgotForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="forgotForm.confirm" type="password" show-password placeholder="再次输入" />
        </el-form-item>
      </el-form>
      <div class="forgot-note">
        演示环境不发送邮件，仅校验邮箱是否匹配。生产环境应改为邮件验证码或一次性链接。
      </div>
      <template #footer>
        <el-button @click="forgotDialog = false">取消</el-button>
        <el-button type="primary" :loading="forgotSaving" @click="submitForgot">重置密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  background: var(--bg);
}

.auth-visual {
  position: relative;
  background: var(--panel-dark-bg);
  color: #f4efe6;
  padding: 34px 48px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.auth-visual::before {
  content: '';
  position: absolute;
  width: 560px;
  height: 560px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(240, 90, 40, 0.22), transparent 65%);
  left: -100px;
  bottom: -140px;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  position: relative;
  z-index: 1;
}

.auth-brand .brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--brand);
  display: grid;
  place-items: center;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 20px;
}

.auth-brand strong {
  display: block;
  font-size: 17px;
  letter-spacing: 0.05em;
}

.auth-brand small {
  display: block;
  font-size: 8px;
  letter-spacing: 0.22em;
  color: #8d8578;
  margin-top: 2px;
}

.visual-copy {
  position: relative;
  z-index: 1;
  margin-top: 64px;
  max-width: 500px;
}

.visual-copy .eyebrow {
  color: #ff9c72;
}

.visual-copy .eyebrow::before {
  background: var(--panel-dark-accent);
}

.visual-copy h1 {
  font-size: clamp(40px, 5vw, 64px);
  color: #fff;
  margin: 18px 0 20px;
}

.visual-copy p {
  color: #b7afa2;
  font-size: 13.5px;
  line-height: 1.9;
  max-width: 400px;
}

.visual-cards {
  position: relative;
  z-index: 1;
  margin-top: 40px;
  display: flex;
  align-items: flex-end;
  gap: 22px;
}

.visual-card {
  flex: 1;
  max-width: 250px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid #35322b;
  border-radius: 14px;
  padding: 16px;
  backdrop-filter: blur(8px);
}

.mini-label {
  font-size: 8px;
  letter-spacing: 0.16em;
  color: #ff9c72;
  font-weight: 700;
}

.visual-card p {
  font-size: 11px;
  line-height: 1.8;
  color: #d8d0c3;
  margin: 10px 0 12px;
}

.verified {
  font-size: 8.5px;
  color: #6fc39d;
}

.visual-product {
  position: relative;
  width: 175px;
  border-radius: 14px;
  overflow: hidden;
  transform: rotate(3deg);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
}

.product-caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 22px 12px 11px;
  background: linear-gradient(180deg, rgba(20, 19, 17, 0) 0%, rgba(20, 19, 17, 0.82) 100%);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.product-caption strong {
  font-size: 11px;
  color: #fff;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.product-caption span {
  font-family: var(--font-digit);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  font-weight: 700;
  color: #ff9c72;
}

/* 轮播切换：淡入 + 轻微上浮 */
.showcase-enter-active,
.showcase-leave-active {
  transition: opacity 0.42s ease, transform 0.42s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.showcase-enter-from {
  opacity: 0;
  transform: translateY(14px);
}

.showcase-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.showcase-dots {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 18px;
}

.showcase-dot {
  width: 16px;
  height: 3px;
  border: none;
  border-radius: 99px;
  background: #3d3a33;
  cursor: pointer;
  padding: 0;
  transition: background 0.24s ease, width 0.24s ease;
}

.showcase-dot:hover {
  background: var(--muted);
}

.showcase-dot.active {
  width: 26px;
  background: var(--brand);
}

.visual-foot {
  margin-top: auto;
  padding-top: 34px;
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 10px;
  color: #7d766a;
  position: relative;
  z-index: 1;
}

.visual-foot i {
  width: 3px;
  height: 3px;
  background: var(--muted);
  border-radius: 50%;
}

.auth-form-wrap {
  display: grid;
  place-items: center;
  padding: 40px 24px;
}

.auth-form {
  width: min(430px, 100%);
}

.back-home {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 34px;
}

.back-home:hover {
  color: var(--ink);
}

.form-head h2 {
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 32px;
  font-weight: 600;
}

.form-head p {
  font-size: 12px;
  color: var(--muted);
  margin-top: 8px;
}

.mode-tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--line);
  margin: 26px 0 22px;
}

.mode-tabs button {
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--muted);
  padding: 0 0 11px;
  cursor: pointer;
  position: relative;
}

.mode-tabs button.active {
  color: var(--ink);
  font-weight: 600;
}

.mode-tabs button.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--ink);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 忘记密码入口 */
.form-assist {
  display: flex;
  justify-content: flex-end;
  margin-top: -4px;
}

.forgot-link {
  border: none;
  background: transparent;
  color: var(--brand);
  font-size: 11px;
  cursor: pointer;
  padding: 0;
  transition: opacity 0.2s ease;
}

.forgot-link:hover {
  opacity: 0.75;
  text-decoration: underline;
}

.forgot-tip {
  font-size: 11px;
  line-height: 1.7;
  color: var(--muted);
  margin-bottom: 14px;
}

.forgot-note {
  margin-top: 4px;
  padding: 10px 12px;
  background: var(--surface-sunken);
  border-radius: 9px;
  font-size: 10px;
  line-height: 1.7;
  color: var(--muted);
}

/* 注册角色选择：买家 / 卖家 */
.role-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.role-label {
  font-size: 11px;
  color: var(--muted);
  letter-spacing: 0.04em;
}

.role-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
}

.role-option {
  position: relative;
  display: flex !important;
  flex-direction: row !important;
  align-items: center;
  gap: 9px;
  border: 1px solid var(--line);
  border-radius: 11px;
  padding: 11px 12px;
  cursor: pointer;
  background: var(--surface);
  transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.role-option:hover {
  border-color: var(--line-strong);
  transform: translateY(-1px);
}

.role-option.active {
  border-color: var(--brand);
  background: var(--brand-tint-strong);
}

.role-option input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.role-option > .el-icon:first-of-type {
  font-size: 18px;
  color: var(--muted);
  flex-shrink: 0;
}

.role-option.active > .el-icon:first-of-type {
  color: var(--brand);
}

.role-option div {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.role-option strong {
  font-size: 12px;
  color: var(--ink);
}

.role-option span {
  font-size: 9px;
  color: var(--muted-light);
  line-height: 1.5;
}

.role-option .role-check {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 13px;
  color: var(--brand);
}

@media (max-width: 480px) {
  .role-options {
    grid-template-columns: 1fr;
  }
}

.form label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  flex: 1;
}

.form label > span {
  font-size: 11px;
  color: var(--muted);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.submit {
  width: 100%;
  height: 46px;
  border-radius: 11px;
  margin-top: 6px;
}

.demo-box {
  margin-top: 26px;
  padding: 14px;
  border: 1px dashed var(--line-strong);
  border-radius: 11px;
}

.demo-title {
  display: block;
  font-size: 9px;
  letter-spacing: 0.12em;
  color: var(--muted);
  margin-bottom: 9px;
}

.demo-actions {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
}

.demo-actions button {
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 6px;
  padding: 5px 9px;
  font-size: 9px;
  color: var(--ink-soft);
  cursor: pointer;
}

.demo-actions button:hover {
  border-color: var(--brand);
  color: var(--brand-deep);
}

.terms {
  font-size: 9px;
  color: var(--muted-light);
  line-height: 1.7;
  margin-top: 20px;
}

@media (max-width: 900px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .auth-visual {
    display: none;
  }

  .auth-form-wrap {
    min-height: 100vh;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
