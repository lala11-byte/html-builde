import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'

// 测试路由守卫逻辑
describe('Router Guard', () => {
  let router

  beforeEach(() => {
    localStorage.clear()

    // 创建测试路由（与项目结构一致）
    const Dummy = defineComponent({ template: '<div>dummy</div>' })
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', name: 'Login', component: Dummy },
        { path: '/register', name: 'Register', component: Dummy },
        { path: '/workspace', name: 'Workspace', component: Dummy },
        { path: '/editor/:projectId/:pageId', name: 'Editor', component: Dummy },
        { path: '/:pathMatch(.*)*', redirect: '/login' },
      ],
    })

    // 与项目一致的路由守卫
    router.beforeEach((to) => {
      const token = localStorage.getItem('accessToken')
      if (!token && to.name !== 'Login' && to.name !== 'Register') {
        return { name: 'Login' }
      }
    })
  })

  it('无 token 访问 /workspace 应重定向到 /login', async () => {
    await router.push('/workspace')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('无 token 访问 /login 应正常进入', async () => {
    await router.push('/login')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('无 token 访问 /register 应正常进入', async () => {
    await router.push('/register')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('Register')
  })

  it('有 token 访问 /workspace 应正常进入', async () => {
    localStorage.setItem('accessToken', 'fake-token')
    await router.push('/workspace')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('Workspace')
  })

  it('未知路径应重定向到 /login', async () => {
    await router.push('/unknown-path')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('Login')
  })
})

// 测试 App.vue 是否正确渲染 router-view
describe('App.vue', () => {
  it('应包含 router-view', async () => {
    const App = (await import('../src/App.vue')).default
    const wrapper = mount(App, {
      global: {
        stubs: ['router-view'],
      },
    })
    expect(wrapper.html()).toContain('router-view-stub')
  })
})
