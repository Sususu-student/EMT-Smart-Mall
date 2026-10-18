import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  },
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/StoreLayout.vue'),
      children: [
        { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
        { path: 'products', name: 'products', component: () => import('@/views/ProductsView.vue') },
        { path: 'search', name: 'search', component: () => import('@/views/SearchView.vue') },
        { path: 'product/:id', name: 'product-detail', component: () => import('@/views/ProductDetailView.vue') },
        { path: 'cart', name: 'cart', component: () => import('@/views/CartView.vue') },
        {
          path: 'checkout',
          name: 'checkout',
          component: () => import('@/views/CheckoutView.vue'),
          meta: { requiresAuth: true }
        },
        { path: 'orders', name: 'orders', component: () => import('@/views/OrdersView.vue'), meta: { requiresAuth: true } },
        {
          path: 'order/:id',
          name: 'order-detail',
          component: () => import('@/views/OrderDetailView.vue'),
          meta: { requiresAuth: true }
        },
        { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { requiresAuth: true } },
        { path: 'chat', name: 'chat', component: () => import('@/views/ChatView.vue'), meta: { requiresAuth: true } },
        { path: 'compare', name: 'compare', component: () => import('@/views/CompareView.vue') }
      ]
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue')
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', name: 'admin-dashboard', component: () => import('@/views/admin/DashboardView.vue') },
        { path: 'products', name: 'admin-products', component: () => import('@/views/admin/AdminProductsView.vue') },
        { path: 'orders', name: 'admin-orders', component: () => import('@/views/admin/AdminOrdersView.vue') },
        { path: 'users', name: 'admin-users', component: () => import('@/views/admin/AdminUsersView.vue') },
        { path: 'agent-logs', name: 'admin-logs', component: () => import('@/views/admin/AdminLogsView.vue') }
      ]
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }
  return true
})

export default router
