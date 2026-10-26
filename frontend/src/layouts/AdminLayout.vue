<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import NotificationBell from '@/components/NotificationBell.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const drawer = ref(false)

const navItems = [
  { name: 'admin-dashboard', path: '/admin', label: '仪表盘', icon: 'DataBoard' },
  { name: 'admin-products', path: '/admin/products', label: '商品管理', icon: 'Goods' },
  { name: 'admin-categories', path: '/admin/categories', label: '分类管理', icon: 'Collection' },
  { name: 'admin-orders', path: '/admin/orders', label: '订单管理', icon: 'Tickets' },
  { name: 'admin-after-sales', path: '/admin/after-sales', label: '售后管理', icon: 'Service' },
  { name: 'admin-reviews', path: '/admin/reviews', label: '评价管理', icon: 'ChatLineSquare' },
  { name: 'admin-users', path: '/admin/users', label: '用户管理', icon: 'User' },
  { name: 'admin-conversations', path: '/admin/conversations', label: '会话审查', icon: 'ChatDotRound' },
  { name: 'admin-agent-config', path: '/admin/agent-config', label: 'Agent 配置', icon: 'Setting' },
  { name: 'admin-logs', path: '/admin/agent-logs', label: 'Agent 日志', icon: 'DataLine' }
]

const title = computed(() => {
  const item = navItems.find((nav) => nav.name === route.name)
  return item?.label || '后台管理'
})

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })

function go(path: string) {
  router.push(path)
  drawer.value = false
}

function logout() {
  auth.logout()
  router.push('/')
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <router-link to="/admin" class="admin-logo">
        <span class="logo-mark">智</span>
        <div>
          <strong>智选后台</strong>
          <small>ADMIN CONSOLE</small>
        </div>
      </router-link>

      <nav class="admin-nav">
        <span class="nav-label">运营</span>
        <router-link
          v-for="item in navItems"
          :key="item.name"
          :to="item.path"
          :class="{ active: route.name === item.name }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-foot">
        <button class="store-link" @click="router.push('/')">
          <el-icon><Shop /></el-icon>
          返回商城
        </button>
        <div class="admin-user">
          <span class="avatar">{{ (auth.user?.nickname || auth.user?.username || '管').slice(0, 1) }}</span>
          <div>
            <strong>{{ auth.user?.nickname || auth.user?.username }}</strong>
            <span>管理员</span>
          </div>
          <button class="logout" aria-label="退出登录" @click="logout"><el-icon><SwitchButton /></el-icon></button>
        </div>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div class="top-left">
          <button class="menu-btn" aria-label="菜单" @click="drawer = true">
            <el-icon><Menu /></el-icon>
          </button>
          <div>
            <h1>{{ title }}</h1>
            <span>{{ today }}</span>
          </div>
        </div>
        <div class="top-right">
          <span class="agent-pill"><span class="dot"></span> Agent 运行中</span>
          <ThemeToggle />
          <NotificationBell />
          <button class="top-icon" aria-label="前台" @click="router.push('/')"><el-icon><Monitor /></el-icon></button>
          <button class="top-avatar" @click="logout">{{ (auth.user?.nickname || auth.user?.username || '管').slice(0, 1) }}</button>
        </div>
      </header>

      <main class="admin-content">
        <router-view />
      </main>
    </div>

    <el-drawer v-model="drawer" direction="ltr" size="76%" :with-header="false">
      <div class="drawer-admin">
        <div class="admin-logo">
          <span class="logo-mark">智</span>
          <div><strong>智选后台</strong><small>ADMIN CONSOLE</small></div>
        </div>
        <nav class="admin-nav">
          <router-link
            v-for="item in navItems"
            :key="item.name"
            :to="item.path"
            :class="{ active: route.name === item.name }"
            @click="drawer = false"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>
        <button class="store-link" @click="drawer = false; router.push('/')">返回商城</button>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
  display: flex;
  background: var(--surface-sunken);
}

.admin-sidebar {
  width: 236px;
  background: var(--panel-dark-bg);
  color: #eee7db;
  display: flex;
  flex-direction: column;
  padding: 24px 16px 18px;
  position: sticky;
  top: 0;
  height: 100vh;
  flex-shrink: 0;
}

.admin-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px 26px;
}

.logo-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--brand);
  display: grid;
  place-items: center;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 18px;
  color: #fff;
}

.admin-logo strong {
  display: block;
  font-size: 14px;
  letter-spacing: 0.04em;
}

.admin-logo small {
  display: block;
  font-size: 7px;
  letter-spacing: 0.18em;
  color: #6f695e;
  margin-top: 3px;
}

.admin-nav {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.nav-label {
  font-size: 8px;
  letter-spacing: 0.18em;
  color: #5f5a50;
  padding: 8px 12px;
}

.admin-nav a {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 9px;
  font-size: 12px;
  color: #9d9588;
  transition: all 0.2s;
}

.admin-nav a:hover {
  background: var(--panel-dark-sunken);
  color: #fff;
}

.admin-nav a.active {
  background: var(--brand);
  color: #fff;
}

.admin-nav a .el-icon {
  font-size: 15px;
}

.sidebar-foot {
  margin-top: auto;
  border-top: 1px solid #2a2722;
  padding-top: 12px;
}

.store-link {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  border: none;
  background: transparent;
  color: #8f887d;
  font-size: 10px;
  padding: 9px 10px;
  cursor: pointer;
}

.store-link:hover {
  color: #ffb38f;
}

.admin-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  margin-top: 6px;
  background: var(--panel-dark-sunken);
  border-radius: 9px;
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.admin-user strong {
  display: block;
  font-size: 10px;
}

.admin-user span:not(.avatar) {
  display: block;
  font-size: 8px;
  color: #7d766a;
}

.logout {
  margin-left: auto;
  border: none;
  background: transparent;
  color: #7d766a;
  cursor: pointer;
}

.logout:hover {
  color: #ff9c72;
}

.admin-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.admin-topbar {
  height: 74px;
  padding: 0 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--surface-glass);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 10;
}

.top-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.menu-btn {
  display: none;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: var(--surface);
  cursor: pointer;
  place-items: center;
}

.top-left h1 {
  font-size: 18px;
  font-weight: 600;
}

.top-left span {
  font-size: 9px;
  color: var(--muted);
}

.top-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 9px;
  color: var(--muted);
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 99px;
  padding: 6px 10px;
}

.agent-pill .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #38a169;
}

.top-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  border: 1px solid var(--line);
  background: var(--surface);
  cursor: pointer;
  color: var(--muted);
}

.top-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: none;
  background: var(--ink);
  color: var(--on-ink);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.admin-content {
  padding: 24px 30px 40px;
  max-width: 1500px;
  width: 100%;
}

.drawer-admin {
  padding: 6px;
}

.drawer-admin .admin-logo {
  padding-left: 4px;
}

.drawer-admin .admin-nav a {
  color: var(--ink-soft);
}

.drawer-admin .admin-nav a.active {
  color: #fff;
}

@media (max-width: 960px) {
  .admin-sidebar {
    display: none;
  }

  .menu-btn {
    display: grid;
  }

  .admin-topbar {
    padding: 0 18px;
  }

  .admin-content {
    padding: 18px;
  }

  .agent-pill {
    display: none;
  }
}
</style>
