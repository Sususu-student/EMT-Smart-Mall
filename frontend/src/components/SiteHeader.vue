<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useCompareStore } from '@/stores/compare'
import NotificationBell from './NotificationBell.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const compare = useCompareStore()

const keyword = ref((route.query.keyword as string) || '')
const drawer = ref(false)

/** 滚动后导航变磨砂玻璃，并收起顶部公告条 */
const scrolled = ref(false)

function onScroll() {
  scrolled.value = window.scrollY > 36
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
})

function submitSearch() {
  const value = keyword.value.trim()
  router.push(value ? { name: 'search', query: { keyword: value } } : { name: 'products' })
  drawer.value = false
}

function go(path: string) {
  router.push(path)
  drawer.value = false
}

function logout() {
  auth.logout()
  cart.clear()
  router.push('/')
}

function onCommand(command: string) {
  if (command === 'logout') {
    logout()
    return
  }
  router.push(command)
}
</script>

<template>
  <div class="header-wrap" :class="{ scrolled }">
    <div class="notice">
      <div class="container notice-inner">
        <span>新用户注册即可体验对话导购 · 从挑选到下单，一句话开始</span>
        <button class="notice-link" @click="go('/chat')">立即体验 <el-icon><ArrowRight /></el-icon></button>
      </div>
    </div>

    <header class="header">
      <div class="container header-inner">
        <router-link to="/" class="brand" aria-label="智选商城首页">
          <span class="brand-mark">智</span>
          <span class="brand-text">
            <strong>智选商城</strong>
            <small>SMART GUIDE MALL</small>
          </span>
        </router-link>

        <nav class="nav">
          <router-link to="/" exact-active-class="active">首页</router-link>
          <router-link to="/products" active-class="active">全部商品</router-link>
          <router-link to="/chat" active-class="active">智能导购</router-link>
          <router-link to="/compare" active-class="active">商品对比</router-link>
        </nav>

        <div class="actions">
          <form class="search" @submit.prevent="submitSearch">
            <el-icon class="search-icon"><Search /></el-icon>
            <input v-model="keyword" type="search" placeholder="搜索耳机、跑鞋、咖啡机…" aria-label="搜索商品" />
            <button type="submit" class="search-btn">搜索</button>
          </form>

          <NotificationBell />

          <button class="icon-btn compare-btn" aria-label="商品对比" @click="go('/compare')">
            <el-icon><DataAnalysis /></el-icon>
            <span v-if="compare.count" class="dot">{{ compare.count }}</span>
          </button>

          <button class="icon-btn" aria-label="购物车" @click="go('/cart')">
            <el-badge :value="cart.count" :hidden="!cart.count" :max="99">
              <el-icon><ShoppingCart /></el-icon>
            </el-badge>
          </button>

          <template v-if="auth.isLoggedIn">
            <el-dropdown trigger="click" @command="onCommand">
              <button class="user-trigger">
                <span class="avatar">{{ (auth.user?.nickname || auth.user?.username || '智').slice(0, 1) }}</span>
                <span class="user-name">{{ auth.user?.nickname || auth.user?.username }}</span>
                <el-icon><ArrowDown /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="/orders">我的订单</el-dropdown-item>
                  <el-dropdown-item v-if="auth.isSeller || auth.isAdmin" command="/seller">卖家中心</el-dropdown-item>
                  <el-dropdown-item v-if="auth.isAdmin" command="/admin">后台管理</el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <router-link v-else to="/login" class="login-btn">登录</router-link>

          <button class="menu-btn" aria-label="打开菜单" @click="drawer = true">
            <el-icon><Menu /></el-icon>
          </button>
        </div>
      </div>
    </header>

    <el-drawer v-model="drawer" direction="rtl" size="78%" :with-header="false">
      <div class="mobile-menu">
        <div class="mobile-brand">
          <span class="brand-mark">智</span>
          <strong>智选商城</strong>
        </div>
        <form class="mobile-search" @submit.prevent="submitSearch">
          <el-input v-model="keyword" size="large" placeholder="搜索商品" @keyup.enter="submitSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </form>
        <nav class="mobile-nav">
          <button @click="go('/')">首页</button>
          <button @click="go('/products')">全部商品</button>
          <button @click="go('/chat')">智能导购</button>
          <button @click="go('/compare')">商品对比</button>
          <button @click="go('/cart')">购物车</button>
          <button v-if="auth.isLoggedIn" @click="go('/orders')">我的订单</button>
          <button v-if="auth.isSeller || auth.isAdmin" @click="go('/seller')">卖家中心</button>
          <button v-if="auth.isLoggedIn" @click="go('/profile')">个人中心</button>
          <button v-if="auth.isLoggedIn" @click="logout(); drawer = false">退出登录</button>
          <button v-else @click="go('/login')">登录 / 注册</button>
        </nav>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.header-wrap {
  position: sticky;
  top: 0;
  z-index: 30;
}

.notice {
  background: var(--ink);
  color: #f4efe6;
  font-size: 12px;
  overflow: hidden;
  max-height: 40px;
  opacity: 1;
  transition: max-height 0.34s cubic-bezier(0.22, 0.61, 0.36, 1), opacity 0.24s ease;
}

/* 滚动后公告条收起，导航直接贴顶 */
.header-wrap.scrolled .notice {
  max-height: 0;
  opacity: 0;
}

.notice-inner {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.notice-link {
  background: transparent;
  border: none;
  color: #ffb38f;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 0;
}

.header {
  position: relative;
  z-index: 20;
  background: rgba(244, 239, 228, 0.82);
  backdrop-filter: blur(14px) saturate(1.35);
  -webkit-backdrop-filter: blur(14px) saturate(1.35);
  border-bottom: 1px solid rgba(228, 221, 205, 0.7);
  transition: background 0.3s ease, box-shadow 0.3s ease, border-color 0.3s ease;
}

/* 顶部与公告条之间加一条柔和过渡，避免硬切 */
.header::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: -1px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 179, 143, 0.55), transparent);
}

.header-wrap.scrolled .header {
  background: rgba(244, 239, 228, 0.68);
  backdrop-filter: blur(20px) saturate(1.5);
  -webkit-backdrop-filter: blur(20px) saturate(1.5);
  border-bottom-color: rgba(228, 221, 205, 0.55);
  box-shadow: 0 10px 30px rgba(38, 33, 25, 0.06);
}

.header-inner {
  height: 76px;
  display: flex;
  align-items: center;
  gap: 30px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  flex-shrink: 0;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--ink);
  color: #fff;
  display: grid;
  place-items: center;
  font-family: var(--font-serif);
  font-weight: 700;
  font-size: 21px;
  position: relative;
}

.brand-mark::after {
  content: '';
  position: absolute;
  right: -3px;
  bottom: -3px;
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: var(--brand);
  border: 2px solid var(--bg);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-text strong {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.brand-text small {
  font-size: 8px;
  letter-spacing: 0.22em;
  color: var(--muted);
  margin-top: 3px;
}

.nav {
  display: flex;
  gap: 26px;
  font-size: 14px;
  color: var(--ink-soft);
}

.nav a {
  position: relative;
  padding: 6px 0;
  transition: color 0.2s;
}

.nav a::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--brand);
  transform: scaleX(0);
  transition: transform 0.22s ease;
  transform-origin: left;
}

.nav a:hover,
.nav a.active {
  color: var(--ink);
}

.nav a:hover::after,
.nav a.active::after {
  transform: scaleX(1);
}

.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

.search {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 99px;
  padding: 4px 5px 4px 14px;
  width: 278px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search:focus-within {
  border-color: var(--ink);
  box-shadow: 0 6px 20px rgba(27, 26, 23, 0.08);
}

.search-icon {
  color: var(--muted);
  font-size: 15px;
}

.search input {
  border: none;
  outline: none;
  flex: 1;
  min-width: 0;
  font-size: 13px;
  background: transparent;
  color: var(--ink);
}

.search-btn {
  border: none;
  background: var(--ink);
  color: #fff;
  border-radius: 99px;
  padding: 7px 14px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.search-btn:hover {
  background: var(--brand);
}

.icon-btn {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid transparent;
  background: transparent;
  display: grid;
  place-items: center;
  cursor: pointer;
  color: var(--ink);
  font-size: 19px;
  transition: background 0.2s, border-color 0.2s;
}

.icon-btn:hover {
  background: #fff;
  border-color: var(--line);
}

.dot {
  position: absolute;
  top: 0;
  right: -1px;
  min-width: 17px;
  height: 17px;
  border-radius: 99px;
  background: var(--brand);
  color: #fff;
  font-size: 10px;
  display: grid;
  place-items: center;
  padding: 0 4px;
  border: 2px solid var(--bg);
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 99px;
  padding: 4px 10px 4px 4px;
  cursor: pointer;
  font-size: 12px;
  color: var(--ink);
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-deep);
  display: grid;
  place-items: center;
  font-weight: 700;
}

.user-name {
  max-width: 76px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.login-btn {
  padding: 9px 18px;
  border-radius: 99px;
  background: var(--ink);
  color: #fff;
  font-size: 13px;
  transition: background 0.2s;
}

.login-btn:hover {
  background: var(--brand);
}

.menu-btn {
  display: none;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid var(--line);
  background: #fff;
  cursor: pointer;
  place-items: center;
}

.mobile-menu {
  padding: 10px 4px;
}

.mobile-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 22px;
}

.mobile-search {
  margin-bottom: 18px;
}

.mobile-nav {
  display: flex;
  flex-direction: column;
}

.mobile-nav button {
  text-align: left;
  border: none;
  border-bottom: 1px solid var(--line);
  background: transparent;
  padding: 16px 4px;
  font-size: 16px;
  color: var(--ink);
  cursor: pointer;
}

@media (max-width: 1120px) {
  .search {
    width: 210px;
  }

  .nav {
    gap: 18px;
  }
}

@media (max-width: 960px) {
  .nav,
  .search,
  .user-name {
    display: none;
  }

  .menu-btn {
    display: grid;
  }

  .header-inner {
    height: 68px;
    gap: 16px;
  }

  .compare-btn {
    display: none;
  }
}

@media (max-width: 620px) {
  .notice-inner span {
    max-width: 230px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .user-trigger {
    padding: 3px;
  }
}
</style>
