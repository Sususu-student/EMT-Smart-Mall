<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import ProductImage from '@/components/ProductImage.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', confirm: '', nickname: '', email: '' })

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
    redirect(user.role === 'ADMIN' && !route.query.redirect ? '/admin' : undefined)
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
      email: registerForm.email || undefined
    })
    await cart.refresh()
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

function useDemo(role: 'user' | 'admin') {
  mode.value = 'login'
  loginForm.username = role === 'admin' ? 'admin' : 'demo'
  loginForm.password = role === 'admin' ? 'Admin@123' : 'Demo@123'
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
        <div class="visual-card">
          <span class="mini-label">AGENT 推荐理由</span>
          <p>“IPX5 防水与 24h 续航覆盖跑步需求，4.2g 佩戴负担小。”</p>
          <span class="verified">已校验价格 / 库存 / 参数</span>
        </div>
        <div class="visual-product">
          <ProductImage src="/img/headphone.svg" alt="智能导购精选" ratio="4 / 5" />
        </div>
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
          <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit">
            登录
          </el-button>
        </form>

        <form v-else class="form" @submit.prevent="submitRegister">
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
            <button @click="useDemo('admin')">管理员 admin / Admin@123</button>
          </div>
        </div>

        <p class="terms">登录即表示同意本演示项目的使用说明。演示环境不产生真实交易。</p>
      </div>
    </div>
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
  background: #191815;
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
  background: #ff9c72;
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
  width: 175px;
  border-radius: 14px;
  overflow: hidden;
  transform: rotate(3deg);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
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
  background: #555147;
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
  background: #fff;
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
