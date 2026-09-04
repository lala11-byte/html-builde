import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
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

// 路由守卫：未登录跳转登录页
router.beforeEach((to) => {
  const token = localStorage.getItem('accessToken')
  if (!token && to.name !== 'Login' && to.name !== 'Register') {
    return { name: 'Login' }
  }
})

export default router