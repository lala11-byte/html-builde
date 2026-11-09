﻿﻿﻿<template>
  <div class="community">
    <header class="com-header">
      <div class="header-left">
        <div class="brand" @click="router.push('/workspace')">
          <svg viewBox="0 0 36 36" aria-hidden="true">
            <defs>
              <linearGradient id="wc-logo-com" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="#4f8cff" />
                <stop offset="1" stop-color="#1d4ed8" />
              </linearGradient>
            </defs>
            <path d="M5 17.5 L30.5 4.5 L22.5 30 L16.8 19.6 Z" fill="url(#wc-logo-com)" />
            <path d="M16.8 19.6 L30.5 4.5 L22.5 30 Z" fill="#9cc3ff" />
          </svg>
          <span class="brand-name">WebCraft</span>
        </div>
        <nav class="header-nav">
          <router-link to="/workspace" class="nav-link">我的项目</router-link>
          <router-link to="/community" class="nav-link nav-link--active">作品广场</router-link>
          <router-link v-if="isAdmin" to="/admin" class="nav-link">后台管理</router-link>
        </nav>
      </div>
      <div class="header-right">
        <el-dropdown trigger="click">
          <button class="user-chip" type="button">
            <span class="user-chip-avatar">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <defs>
                  <linearGradient id="chip-user-com" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stop-color="#2563eb" />
                    <stop offset="1" stop-color="#0284c7" />
                  </linearGradient>
                </defs>
                <circle cx="12" cy="8.2" r="3.9" fill="url(#chip-user-com)" />
                <path d="M4.3 20.2c0-4 3.5-6.6 7.7-6.6s7.7 2.6 7.7 6.6z" fill="url(#chip-user-com)" />
              </svg>
            </span>
            <span class="user-chip-name">{{ displayName || '未登录' }}</span>
            <svg class="user-chip-caret" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M6.5 9.5l5.5 5.5 5.5-5.5" />
            </svg>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/workspace')">我的项目</el-dropdown-item>
              <el-dropdown-item v-if="isAdmin" @click="router.push('/admin')">后台管理</el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">
                <span style="color: #ef4444;">退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="com-main">
      <section class="com-hero">
        <h1>作品广场</h1>
        <p>浏览其他用户用 WebCraft 生成的成品网站，给喜欢的作品点个赞</p>
      </section>

      <div class="com-toolbar">
        <el-radio-group v-model="sort" @change="handleSortChange">
          <el-radio-button label="comprehensive">综合排序</el-radio-button>
          <el-radio-button label="time">最新发布</el-radio-button>
          <el-radio-button label="like">最喜爱</el-radio-button>
        </el-radio-group>
        <div class="com-search">
          <el-input
            v-model="keyword"
            placeholder="搜索作品名称或作者"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </div>
      </div>

      <div v-if="loading" class="loading-area">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <div v-else-if="items.length === 0" class="empty-area">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="9"></circle>
            <path d="M8 14s1.5 2 4 2 4-2 4-2"></path>
            <line x1="9" y1="9" x2="9.01" y2="9"></line>
            <line x1="15" y1="9" x2="15.01" y2="9"></line>
          </svg>
        </div>
        <h3>{{ keyword ? '没有找到相关作品' : '还没有其他用户的作品' }}</h3>
        <p>{{ keyword ? '换个关键词，或切换排序方式试试' : '当其他用户生成并保存成品后，会出现在这里' }}</p>
      </div>

      <div v-else class="com-grid">
        <article
          v-for="item in items"
          :key="item.id"
          class="com-card"
          @click="openPreview(item)"
        >
          <div class="card-thumbnail">
            <img
              v-if="item.screenshotUrl && !brokenThumbs[item.id]"
              :src="fullUrl(item.screenshotUrl)"
              :alt="item.name"
              @error="brokenThumbs[item.id] = true"
            />
            <div v-else class="thumb-placeholder">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
                <line x1="8" y1="21" x2="16" y2="21"></line>
                <line x1="12" y1="17" x2="12" y2="21"></line>
              </svg>
            </div>
            <div class="card-overlay"><span>查看成品</span></div>
            <button class="like-fab" :class="{ 'is-liked': item.liked }" @click.stop="handleLike(item)">
              <svg viewBox="0 0 24 24" :fill="item.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z"></path>
              </svg>
              <span>{{ item.likeCount }}</span>
            </button>
          </div>
          <div class="card-info">
            <h3 class="card-title">{{ item.name }}</h3>
            <div class="card-meta">
              <span class="author">
                <span class="author-avatar">{{ item.authorName?.charAt(0)?.toUpperCase() || 'U' }}</span>
                {{ item.authorName }}
              </span>
              <span class="meta-dot">·</span>
              <span>{{ item.pageCount }} 页</span>
            </div>
          </div>
        </article>
      </div>

      <div v-if="total > pageSize" class="pagination-area">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="fetchList"
        />
      </div>
    </main>

    <!-- 成品预览 -->
    <el-dialog v-model="showPreview" width="900px" class="preview-dialog" :close-on-click-modal="true">
      <div v-if="activeItem" class="preview-body">
        <div class="preview-image">
          <img v-if="activeItem.screenshotUrl" :src="fullUrl(activeItem.screenshotUrl)" :alt="activeItem.name" />
        </div>
        <div class="preview-meta">
          <div>
            <h2>{{ activeItem.name }}</h2>
            <p class="preview-sub">
              {{ activeItem.authorName }} · {{ activeItem.pageCount }} 页 · {{ formatDate(activeItem.updatedAt) }}
            </p>
          </div>
          <button class="preview-like" :class="{ 'is-liked': activeItem.liked }" @click="handleLike(activeItem)">
            <svg viewBox="0 0 24 24" :fill="activeItem.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z"></path>
            </svg>
            {{ activeItem.likeCount }}
          </button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listCommunityProjects, toggleProjectLike, getProfile } from '../api'

const router = useRouter()
const GATEWAY_BASE = 'http://localhost:8080'

const loading = ref(false)
const items = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 12
const brokenThumbs = reactive({})
const showPreview = ref(false)
const activeItem = ref(null)

const username = ref(localStorage.getItem('username') || '')
const nickname = ref(localStorage.getItem('nickname') || '')
const displayName = computed(() => nickname.value || username.value)
const isAdmin = computed(() => localStorage.getItem('role') === 'admin')

const sort = ref('comprehensive')
const keyword = ref('')

const fullUrl = (url) => (!url ? '' : url.startsWith('http') ? url : GATEWAY_BASE + url)

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + ' 天前'
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const fetchProfile = async () => {
  try {
    const data = await getProfile()
    nickname.value = data?.nickname || ''
    localStorage.setItem('nickname', nickname.value)
  } catch (e) {
    // 资料接口失败时回退到用户名展示
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const data = await listCommunityProjects(currentPage.value, pageSize, sort.value, keyword.value.trim())
    items.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleSortChange = () => {
  currentPage.value = 1
  fetchList()
}

const handleSearch = () => {
  currentPage.value = 1
  fetchList()
}

const handleLogout = () => {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('username')
  localStorage.removeItem('nickname')
  localStorage.removeItem('role')
  localStorage.removeItem('status')
  router.push('/login')
}

const openPreview = (item) => {
  activeItem.value = item
  showPreview.value = true
}

const handleLike = async (item) => {
  try {
    const data = await toggleProjectLike(item.id)
    item.liked = data.liked
    item.likeCount = data.likeCount
  } catch (e) {
    ElMessage.error('操作失败，请稍后重试')
  }
}

onMounted(() => {
  fetchList()
  fetchProfile()
})
</script>

<style scoped>
.community {
  position: relative;
  min-height: 100vh;
  /* 手绘创作插画背景：高度铺满一屏完整展示，宽度不足处用 #FEFDFD 补白 */
  background: #fefdfd url('/craft-bg.jpg') center top / auto 100% no-repeat fixed;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
/* 极浅白色蒙版：保证卡片与标题在插画上依然清晰 */
.community::before {
  content: '';
  position: fixed;
  inset: 0;
  background: linear-gradient(180deg, rgba(254, 253, 253, 0.66) 0%, rgba(254, 253, 253, 0.32) 42%, rgba(254, 253, 253, 0.55) 100%);
  pointer-events: none;
  z-index: 0;
}
.com-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  height: 64px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.58), rgba(255, 255, 255, 0.3));
  -webkit-backdrop-filter: var(--glass-blur-heavy);
  backdrop-filter: var(--glass-blur-heavy);
  border-bottom: 1px solid rgba(255, 255, 255, 0.72);
  box-shadow: 0 10px 30px rgba(30, 58, 138, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.8);
  position: sticky;
  top: 0;
  z-index: 100;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 28px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #1e293b;
  cursor: pointer;
}
.brand svg {
  width: 28px;
  height: 28px;
  filter: drop-shadow(0 4px 10px rgba(37, 99, 235, 0.28));
}
.brand-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.header-nav {
  display: flex;
  align-items: center;
  gap: 6px;
}
.nav-link {
  padding: 7px 14px;
  border-radius: 9px;
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
  text-decoration: none;
  transition: all 0.2s;
}
.nav-link:hover {
  color: #1e40af;
  background: rgba(255, 255, 255, 0.55);
}
.nav-link--active {
  color: #1e40af;
  background: rgba(255, 255, 255, 0.75);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7), 0 4px 12px rgba(37, 99, 235, 0.14);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.com-main {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 36px 24px 56px;
}
.com-hero {
  margin-bottom: 28px;
}
.com-hero h1 {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.6px;
  color: #1e293b;
}
.com-hero p {
  margin: 0;
  font-size: 14px;
  /* 背景为手绘插画，副标题加深以保证对比度 */
  color: #475569;
}
.com-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
  flex-wrap: wrap;
}
.com-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 320px;
  max-width: 100%;
}
.com-search .el-input {
  flex: 1;
}
.loading-area,
.empty-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: #94a3b8;
}
.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #e2e8f0;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.empty-icon {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.55);
  -webkit-backdrop-filter: var(--glass-blur);
  backdrop-filter: var(--glass-blur);
  border: 1px solid rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  box-shadow: var(--glass-shadow), var(--glass-highlight);
  color: #cbd5e1;
}
.empty-icon svg {
  width: 36px;
  height: 36px;
}
.empty-area h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #64748b;
}
.empty-area p {
  margin: 0;
  font-size: 14px;
}
.com-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}
.com-card {
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.76), rgba(255, 255, 255, 0.52));
  -webkit-backdrop-filter: var(--glass-blur-heavy);
  backdrop-filter: var(--glass-blur-heavy);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 16px 40px rgba(30, 58, 138, 0.16), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s ease;
}
.com-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--glass-shadow-hover), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  border-color: rgba(255, 255, 255, 0.95);
}
.card-thumbnail {
  position: relative;
  width: 100%;
  height: 190px;
  background: rgba(241, 245, 249, 0.5);
  overflow: hidden;
}
.card-thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s ease;
}
.com-card:hover .card-thumbnail img {
  transform: scale(1.05);
}
.thumb-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
}
.thumb-placeholder svg {
  width: 48px;
  height: 48px;
}
.card-overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 36, 74, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.25s ease;
}
.card-overlay span {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 8px 20px;
  border: 2px solid rgba(255, 255, 255, 0.6);
  border-radius: 8px;
}
.com-card:hover .card-overlay {
  opacity: 1;
}
.like-fab {
  position: absolute;
  top: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 11px;
  border-radius: 15px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.82);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.like-fab svg {
  width: 15px;
  height: 15px;
}
.like-fab:hover {
  transform: scale(1.06);
  color: #e11d48;
}
.like-fab.is-liked {
  color: #e11d48;
  border-color: rgba(244, 114, 182, 0.6);
  background: rgba(255, 241, 245, 0.92);
}
.card-info {
  padding: 14px 16px 16px;
}
.card-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #94a3b8;
}
.author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-weight: 500;
}
.author-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: linear-gradient(135deg, #7dd3fc, #38bdf8);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.meta-dot {
  color: #cbd5e1;
}
.pagination-area {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
.preview-dialog :deep(.el-dialog__body) {
  padding: 0 0 8px;
}
.preview-body {
  display: flex;
  flex-direction: column;
}
.preview-image {
  width: 100%;
  max-height: 560px;
  overflow: hidden;
  background: #f1f5f9;
  border-radius: 10px;
}
.preview-image img {
  width: 100%;
  height: 100%;
  max-height: 560px;
  object-fit: contain;
  display: block;
}
.preview-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px 8px;
}
.preview-meta h2 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}
.preview-sub {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
}
.preview-like {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.8);
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.preview-like svg {
  width: 18px;
  height: 18px;
}
.preview-like:hover {
  color: #e11d48;
}
.preview-like.is-liked {
  color: #e11d48;
  background: rgba(255, 241, 245, 0.95);
  border-color: rgba(244, 114, 182, 0.6);
}
</style>