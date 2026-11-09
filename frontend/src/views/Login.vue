﻿﻿﻿<template>
  <div class="auth-page">
    <div class="auth-bg"></div>
    <div class="auth-card">
      <div class="auth-head">
        <div class="auth-logo">
          <svg viewBox="0 0 36 36" aria-hidden="true">
            <defs>
              <linearGradient id="wc-logo-login" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="#4f8cff" />
                <stop offset="1" stop-color="#1d4ed8" />
              </linearGradient>
            </defs>
            <path d="M5 17.5 L30.5 4.5 L22.5 30 L16.8 19.6 Z" fill="url(#wc-logo-login)" />
            <path d="M16.8 19.6 L30.5 4.5 L22.5 30 Z" fill="#9cc3ff" />
          </svg>
        </div>
        <StrokeText
          class="auth-title"
          text="WebCraft"
          stroke-color="#7aa7f5"
          fill-color="#1e1b4b"
          :stroke-width="1.2"
          :draw-duration="1.5"
          :fill-delay="0.15"
          :stagger="0.05"
          :font-size="46"
          :font-weight="800"
          :letter-spacing="-1.5"
          fill-mode="wipe"
          wipe-from="left"
        />
        <TextType
          as="p"
          class="auth-subtitle"
          :text="[
            'AI 驱动的网站生成平台',
            '一句话描述，生成你的网站',
            '从灵感到上线，只要几分钟'
          ]"
          :typing-speed="70"
          :initial-delay="600"
          :pause-duration="1800"
          :deleting-speed="32"
          type-effect="fade"
          cursor-style="bar"
          cursor-color="#0284c7"
        />
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" @click="handleLogin" :loading="loading" class="auth-btn">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
      <div class="auth-footer">
        <span>还没有账号？</span>
        <router-link to="/register" class="link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { login } from '../api'
import TextType from '../components/TextType.vue'
import StrokeText from '../components/StrokeText.vue'
import '../styles/auth.css'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = await login(form)
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('username', form.username)
    localStorage.setItem('role', data.role || 'user')
    localStorage.setItem('status', data.status || 'active')
    router.push(data.role === 'admin' ? '/admin' : '/workspace')
  } catch {
    // 错误已在拦截器处理
  } finally {
    loading.value = false
  }
}
</script>