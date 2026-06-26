import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/HomeView.vue'),
      meta: { guest: true },
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guest: true },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { guest: true },
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/novels/:id/setup',
      name: 'NovelSetup',
      component: () => import('@/views/NovelSetupView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/novels/:id/read',
      name: 'NovelRead',
      component: () => import('@/views/ReaderView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/novels/:id/write',
      name: 'NovelWrite',
      component: () => import('@/views/NovelWriteView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/novels/:id/monitor',
      name: 'Monitor',
      component: () => import('@/views/MonitorView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('@/views/ProfileView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

// 导航守卫
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('yunmo_token')

  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.meta.guest && token) {
    // 已登录用户访问首页/登录/注册 → 跳转书房
    if (to.path === '/') {
      next('/dashboard')
    } else {
      next()
    }
  } else {
    next()
  }
})

export default router
