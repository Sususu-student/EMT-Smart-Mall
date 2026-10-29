<script setup lang="ts">
/**
 * 用户中心布局：个人中心 / 我的订单 / 卖家中心共用一套左侧导航。
 *
 * 设计取舍：
 *   - 前台（首页、商品、详情、AI 导购）保持顶部导航与全宽视觉，
 *     不破坏商品的图片冲击力；
 *   - 后台（本布局）改用左侧导航，密集的功能项更容易扫视，
 *     也更符合"工作台"的心理预期。
 *   - 侧栏按角色渲染：普通用户看不到卖家菜单。
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import NotificationBell from '@/components/NotificationBell.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const drawer = ref(false)

interface NavItem {
  key: string
  label: string
  icon: string
  /** 目标路由 */
  path: string
  /** 路由上的 tab 参数，与 path 一起构成高亮判定 */
  tab?: string
  /** 仅这些角色可见，不填则所有登录用户可见 */
  roles?: string[]
}

const groups = computed(() => {
  const role = auth.user?.role || 'USER'
  // 卖家与管理员都能看到卖家工作台菜单
  const canSell = auth.isSeller || auth.isAdmin

  const account: NavItem[] = [
    { key: 'overview', label: '账户概览', icon: 'User', path: '/profile', tab: 'overview' },
    { key: 'orders', label: '我的订单', icon: 'Tickets', path: '/orders' },
    { key: 'after-sales', label: '我的售后', icon: 'Service', path: '/after-sales' },
    { key: 'address', label: '收货地址', icon: 'Location', path: '/profile', tab: 'address' },
    { key: 'favorites', label: '我的收藏', icon: 'Star', path: '/profile', tab: 'favorites' }
  ]

  const preference: NavItem[] = [
    { key: 'preferences', label: '导购偏好', icon: 'MagicStick', path: '/profile', tab: 'preferences' },
    { key: 'analytics', label: '消费画像', icon: 'PieChart', path: '/profile', tab: 'analytics' },
    { key: 'history', label: '浏览足迹', icon: 'Clock', path: '/profile', tab: 'history' }
  ]

  const seller: NavItem[] = [
    { key: 'seller-products', label: '商品管理', icon: 'Goods', path: '/seller', tab: 'products' },
    { key: 'seller-orders', label: '店铺订单', icon: 'Box', path: '/seller', tab: 'orders' },
    { key: 'customer-service', label: '客服消息', icon: 'ChatLineRound', path: '/messages' },
    { key: 'seller-analytics', label: '数据分析', icon: 'TrendCharts', path: '/seller', tab: 'analytics' }
  ]

  const result = [
    { label: '账户', items: account },
    { label: '偏好', items: preference }
  ]
  if (canSell) {
    result.push({ label: '卖家工作台', items: seller })
  }
  if (role === 'ADMIN') {
    result.push({
      label: '平台',
      items: [{ key: 'admin', label: '后台管理', icon: 'Setting', path: '/admin' }]
    })
  }
  return result
})

/** 当前高亮项：路由 path + tab 同时匹配 */
const activeKey = computed(() => {
  const path = route.path
  const tab = (route.query.tab as string) || ''
  if (path.startsWith('/seller')) {
    return 'seller-' + (tab || 'products')
  }
  if (path.startsWith('/orders')) {
    return 'orders'
  }
  if (path.startsWith('/after-sales')) {
    return 'after-sales'
  }
  if (path.startsWith('/messages')) {
    return 'customer-service'
  }
  if (path.startsWith('/admin')) {
    return 'admin'
  }
  if (path.startsWith('/profile')) {
    return tab || 'overview'
  }
  return ''
})

const pageTitle = computed(() => {
  for (const g of groups.value) {
    const hit = g.items.find((i) => i.key === activeKey.value)
    if (hit) return hit.label
  }
  return '个人中心'
})

const brandSub = computed(() =>
  auth.isSeller ? 'SELLER WORKSPACE' : 'MY ACCOUNT'
)

function go(item: NavItem) {
  if (item.tab) {
    router.push({ path: item.path, query: { tab: item.tab } })
  } else {
    router.push(item.path)
  }
  drawer.value = false
}

function logout() {
  auth.logout()
  router.push('/')
}

// 切换路由时自动收起移动端抽屉
watch(() => route.fullPath, () => {
  drawer.value = false
})
</script>

<template>
  <div class="user-shell">
    <!-- 侧栏：桌面端常驻，移动端抽屉 -->
    <aside class="user-sidebar" :class="{ open: drawer }">
      <router-link to="/" class="sidebar-logo">
        <span class="logo-mark">智</span>
        <div>
          <strong>智选商城</strong>
          <small>{{ brandSub }}</small>
        </div>
      </router-link>

      <nav class="sidebar-nav">
        <template v-for="group in groups" :key="group.label">
          <span class="nav-label">{{ group.label }}</span>
          <button
            v-for="item in group.items"
            :key="item.key"
            type="button"
            class="nav-item"
            :class="{ active: activeKey === item.key }"
            @click="go(item)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </button>
        </template>
      </nav>

      <div class="sidebar-foot">
        <button class="store-link" type="button" @click="router.push('/')">
          <el-icon><Shop /></el-icon>
          返回商城首页
        </button>
        <div class="user-chip">
          <span class="avatar">{{ (auth.user?.nickname || auth.user?.username || '用').slice(0, 1) }}</span>
          <div class="user-meta">
            <strong>{{ auth.user?.nickname || auth.user?.username }}</strong>
            <span>{{ auth.user?.role === 'SELLER' ? '卖家' : auth.user?.role === 'ADMIN' ? '管理员' : '买家' }}</span>
          </div>
          <button class="logout" type="button" aria-label="退出登录" @click="logout">
            <el-icon><SwitchButton /></el-icon>
          </button>
        </div>
      </div>
    </aside>

    <!-- 移动端遮罩 -->
    <div v-if="drawer" class="drawer-mask" @click="drawer = false" />

    <div class="user-main">
      <header class="user-topbar">
        <div class="top-left">
          <button class="menu-btn" type="button" aria-label="菜单" @click="drawer = true">
            <el-icon><Menu /></el-icon>
          </button>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="top-right">
          <ThemeToggle />
          <NotificationBell />
          <button class="cart-btn" type="button" aria-label="购物车" @click="router.push('/cart')">
            <el-icon><ShoppingCart /></el-icon>
          </button>
        </div>
      </header>

      <main class="user-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.user-shell {
  display: flex;
  min-height: 100vh;
  background: var(--surface-sunken);
}

/* ---------------- 侧栏 ---------------- */
.user-sidebar {
  width: 236px;
  flex-shrink: 0;
  background: var(--panel-dark-bg);
  color: #eee7db;
  display: flex;
  flex-direction: column;
  padding: 24px 14px 18px;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px 24px;
}

.logo-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--brand);
  display: grid;
  place-items: center;
  font-family: var(--font-serif);
  font-size: 18px;
  color: #fff;
  flex-shrink: 0;
}

.sidebar-logo strong {
  display: block;
  font-size: 14px;
  letter-spacing: 0.04em;
  color: #fff;
}

.sidebar-logo small {
  display: block;
  font-size: 7px;
  letter-spacing: 0.18em;
  color: #6f695e;
  margin-top: 3px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-label {
  font-size: 8px;
  letter-spacing: 0.18em;
  color: #5f5a50;
  padding: 14px 12px 6px;
}

.nav-label:first-child {
  padding-top: 6px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 9px;
  border: none;
  background: none;
  font-size: 12.5px;
  color: #9d9588;
  cursor: pointer;
  text-align: left;
  width: 100%;
  transition: background 0.2s, color 0.2s;
}

.nav-item:hover {
  background: var(--panel-dark-sunken);
  color: #fff;
}

.nav-item.active {
  background: var(--brand);
  color: #fff;
}

.nav-item .el-icon {
  font-size: 15px;
}

.sidebar-foot {
  margin-top: auto;
  padding-top: 20px;
}

.store-link {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 12px;
  border-radius: 9px;
  border: 1px solid var(--panel-dark-line);
  background: none;
  color: #9d9588;
  font-size: 11.5px;
  cursor: pointer;
  margin-bottom: 12px;
  transition: all 0.2s;
}

.store-link:hover {
  color: #fff;
  border-color: #5f5a50;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px;
  border-radius: 10px;
  background: var(--panel-dark-sunken);
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 13px;
  flex-shrink: 0;
}

.user-meta {
  flex: 1;
  min-width: 0;
}

.user-meta strong {
  display: block;
  font-size: 12px;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta span {
  font-size: 9px;
  color: #6f695e;
}

.logout {
  border: none;
  background: none;
  color: #6f695e;
  cursor: pointer;
  padding: 4px;
  transition: color 0.2s;
}

.logout:hover {
  color: var(--brand);
}

/* ---------------- 主区域 ---------------- */
.user-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.user-topbar {
  height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 20;
}

.top-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.top-left h1 {
  font-family: var(--font-serif);
  font-size: 18px;
  color: var(--ink);
}

.top-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cart-btn,
.menu-btn {
  border: none;
  background: none;
  color: var(--ink-soft);
  cursor: pointer;
  padding: 6px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  transition: background 0.2s;
}

.cart-btn:hover,
.menu-btn:hover {
  background: var(--surface-sunken);
}

.menu-btn {
  display: none;
}

.user-content {
  flex: 1;
  min-width: 0;          /* 关键：允许 flex 子项收缩，否则宽表格会撑破布局 */
  padding: 26px 28px 40px;
}

/*
 * 前台 .container 用的是 min(1240px, 100vw - 48px)，
 * 在本布局里可用宽度是「视口 - 侧栏」，直接用 100vw 会溢出 236px，
 * 导致表格右侧被裁掉。这里改为 100% 自适应父容器。
 */
.user-content :deep(.container) {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
}

/*
 * 顶栏已经展示当前分区标题，内页重复的大标题可以省掉，
 * 但保留右侧的操作按钮（例如「发布商品」），所以只隐藏文字部分。
 */
.user-content :deep(.page-head) {
  display: none;
}

.user-content :deep(.seller-head) {
  padding: 0 0 16px;
  border-bottom: none;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.user-content :deep(.seller-head > div) {
  display: none;
}

/* ---------------- 移动端 ---------------- */
.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(20, 16, 12, 0.5);
  z-index: 90;
}

@media (max-width: 900px) {
  .menu-btn {
    display: grid;
  }

  .user-sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 100;
    transform: translateX(-100%);
    transition: transform 0.28s cubic-bezier(0.22, 0.61, 0.36, 1);
  }

  .user-sidebar.open {
    transform: translateX(0);
  }

  .user-topbar {
    padding: 0 16px;
  }

  .user-content {
    padding: 16px 14px 32px;
  }
}
</style>