import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'Landing',
      component: () => import('../views/Landing.vue')
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/Login.vue')
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('../views/Register.vue')
    },
    {
      path: '/workspace',
      name: 'Workspace',
      component: () => import('../views/Workspace.vue')
    },
    {
      path: '/community',
      name: 'Community',
      component: () => import('../views/Community.vue')
    },
    {
      path: '/admin',
      name: 'Admin',
      component: () => import('../views/Admin.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/editor/:projectId/:pageId',
      name: 'Editor',
      component: () => import('../views/Editor.vue')
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/login'
    }
  ]
})

// 路由守卫：未登录跳转登录页；后台管理需要管理员角色
router.beforeEach((to) => {
  const token = localStorage.getItem('accessToken')
  if (!token && to.name !== 'Landing' && to.name !== 'Login' && to.name !== 'Register') {
    return { name: 'Login' }
  }
  if (to.meta.requiresAdmin && localStorage.getItem('role') !== 'admin') {
    return { name: 'Workspace' }
  }
})

export default router