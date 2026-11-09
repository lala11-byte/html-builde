﻿﻿﻿<template>
  <div class="workspace">
    <header class="ws-header">
      <div class="header-left">
        <div class="brand">
          <svg viewBox="0 0 36 36" aria-hidden="true">
            <defs>
              <linearGradient id="wc-logo-ws" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="#4f8cff" />
                <stop offset="1" stop-color="#1d4ed8" />
              </linearGradient>
            </defs>
            <path d="M5 17.5 L30.5 4.5 L22.5 30 L16.8 19.6 Z" fill="url(#wc-logo-ws)" />
            <path d="M16.8 19.6 L30.5 4.5 L22.5 30 Z" fill="#9cc3ff" />
          </svg>
          <span class="brand-name">WebCraft</span>
        </div>
        <nav class="header-nav">
          <router-link to="/workspace" class="nav-link nav-link--active">我的项目</router-link>
          <router-link to="/community" class="nav-link">作品广场</router-link>
          <router-link v-if="isAdmin" to="/admin" class="nav-link">后台管理</router-link>
        </nav>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="showCreateDialog = true" class="create-btn">
          <span class="btn-icon">+</span> 新建项目
        </el-button>
        <el-dropdown trigger="click">
          <button class="user-chip" type="button">
            <span class="user-chip-avatar">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <defs>
                  <linearGradient id="chip-user-ws" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stop-color="#2563eb" />
                    <stop offset="1" stop-color="#0284c7" />
                  </linearGradient>
                </defs>
                <circle cx="12" cy="8.2" r="3.9" fill="url(#chip-user-ws)" />
                <path d="M4.3 20.2c0-4 3.5-6.6 7.7-6.6s7.7 2.6 7.7 6.6z" fill="url(#chip-user-ws)" />
              </svg>
            </span>
            <span class="user-chip-name">{{ displayName || '未登录' }}</span>
            <svg class="user-chip-caret" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M6.5 9.5l5.5 5.5 5.5-5.5" />
            </svg>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openProfile">个人中心</el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">
                <span style="color: #ef4444;">退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="ws-main">
      <div class="main-header">
        <h2>我的项目</h2>
        <span class="project-count">{{ total }} 个项目</span>
      </div>

      <div v-if="deletedCount > 0" class="deleted-banner">
        <span class="deleted-banner-dot">!</span>
        <span>有 {{ deletedCount }} 个作品已被平台删除，相关内容无法继续编辑。如有疑问请联系管理员。</span>
      </div>

      <div v-if="loading" class="loading-area">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <div v-else-if="projects.length === 0" class="empty-area">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
            <polyline points="13 2 13 9 20 9"></polyline>
          </svg>
        </div>
        <h3>还没有项目</h3>
        <p>点击上方「新建项目」开始创建你的第一个网站</p>
      </div>

      <div v-else class="project-grid">
        <div
          v-for="project in projects"
          :key="project.id"
          class="project-card"
          :class="{ 'project-card--deleted': project.deleted }"
          @click="openEditor(project)"
        >
          <div class="card-thumbnail">
            <div v-if="project.screenshotUrl && !brokenThumbs[project.id]" class="thumb-img">
              <img :src="project.screenshotUrl" :alt="project.name" @error="brokenThumbs[project.id] = true" />
            </div>
            <div v-else class="thumb-placeholder">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
                <line x1="8" y1="21" x2="16" y2="21"></line>
                <line x1="12" y1="17" x2="12" y2="21"></line>
              </svg>
            </div>
            <div class="card-overlay">
              <span>{{ project.deleted ? '已被平台删除' : '进入编辑' }}</span>
            </div>
          </div>
          <div class="card-info">
            <h3 class="card-title">
              {{ project.name }}
              <span v-if="project.deleted" class="deleted-tag">已删除</span>
            </h3>
            <div class="card-meta">
              <span class="meta-item">{{ project.pageCount }} 页</span>
              <span class="meta-item">{{ formatDate(project.updatedAt) }}</span>
            </div>
          </div>
          <div class="card-actions" @click.stop>
            <template v-if="!project.deleted">
              <el-button text size="small" @click="handleRename(project)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                </svg>
                重命名
              </el-button>
              <el-popconfirm title="确认删除此项目？" @confirm="handleDelete(project.id)">
                <template #reference>
                  <el-button text size="small" type="danger">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
                      <polyline points="3 6 5 6 21 6"></polyline>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                    </svg>
                    删除
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
            <span v-else class="deleted-hint">该作品已被平台删除，无法继续编辑</span>
          </div>
        </div>
      </div>

      <div v-if="total > 10" class="pagination-area">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="10"
          :total="total"
          layout="prev, pager, next"
          @current-change="fetchProjects"
        />
      </div>
    </main>

    <!-- 新建项目对话框 -->
    <el-dialog v-model="showCreateDialog" width="480px" :close-on-click-modal="false" class="create-dialog" align-center>
      <template #header>
        <div class="create-dialog-head">
          <div class="create-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
          </div>
          <div class="create-titles">
            <h3>创建新项目</h3>
            <p>给你的网站起个名字，马上开始搭建</p>
          </div>
        </div>
      </template>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top" class="create-form">
        <el-form-item label="项目名称" prop="name">
          <el-input
            v-model="createForm.name"
            maxlength="50"
            show-word-limit
            placeholder="例如：我的作品集 / 产品官网"
            size="large"
            @keyup.enter="handleCreate"
          />
        </el-form-item>
      </el-form>
      <div class="create-tips">
        <div class="tip">
          <span class="tip-dot tip-dot--blue"></span>
          <span>支持多页面与可视化编辑</span>
        </div>
        <div class="tip">
          <span class="tip-dot tip-dot--purple"></span>
          <span>AI 一键生成页面代码</span>
        </div>
        <div class="tip">
          <span class="tip-dot tip-dot--green"></span>
          <span>创建后可随时重命名</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="showCreateDialog = false" size="large" class="dialog-cancel">取消</el-button>
        <el-button type="primary" @click="handleCreate" size="large" class="dialog-confirm">创建项目</el-button>
      </template>
    </el-dialog>

    <!-- 重命名对话框 -->
    <el-dialog v-model="showRenameDialog" title="重命名项目" width="440px" :close-on-click-modal="false" class="modern-dialog">
      <el-form ref="renameFormRef" :model="renameForm" :rules="createRules" label-position="top">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="renameForm.name" maxlength="50" placeholder="输入新名称" size="large" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRenameDialog = false" size="large">取消</el-button>
        <el-button type="primary" @click="handleRenameConfirm" size="large">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 个人中心对话框 -->
    <el-dialog v-model="showProfileDialog" title="个人中心" width="440px" :close-on-click-modal="false" class="modern-dialog">
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-position="top">
        <el-form-item label="用户名">
          <el-input :model-value="username" disabled size="large" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="profileForm.nickname" maxlength="20" placeholder="输入昵称（2-20位）" size="large" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showProfileDialog = false" size="large">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="handleProfileSave" size="large">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listProjects, createProject, updateProject, deleteProject, getProfile, updateNickname } from '../api'

const router = useRouter()
const loading = ref(false)
const projects = ref([])
const brokenThumbs = reactive({})
const total = ref(0)
const currentPage = ref(1)
const showCreateDialog = ref(false)
const showRenameDialog = ref(false)
const createForm = ref({ name: '' })
const renameForm = ref({ name: '' })
const renameId = ref(null)
const createFormRef = ref()
const renameFormRef = ref()
const createRules = { name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }] }
const username = ref(localStorage.getItem('username') || '')
const nickname = ref(localStorage.getItem('nickname') || '')
const displayName = computed(() => nickname.value || username.value)
const isAdmin = computed(() => localStorage.getItem('role') === 'admin')
const deletedCount = computed(() => projects.value.filter(p => p.deleted).length)
const showProfileDialog = ref(false)
const profileForm = ref({ nickname: '' })
const profileFormRef = ref()
const profileSaving = ref(false)
const profileRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度2-20字符', trigger: 'blur' }
  ]
}

const GATEWAY_BASE = 'http://localhost:8080'

const fetchProfile = async () => {
  try {
    const data = await getProfile()
    nickname.value = data?.nickname || ''
    localStorage.setItem('nickname', nickname.value)
    if (data?.role) localStorage.setItem('role', data.role)
    if (data?.status) localStorage.setItem('status', data.status)
    if (data?.status === 'banned') {
      ElMessageBox.alert(
        '你的账号已被平台封禁，无法继续使用。' + (data.banReason ? `原因：${data.banReason}` : ''),
        '账号已被封禁',
        { confirmButtonText: '退出登录', showClose: false, callback: handleLogout }
      ).catch(() => handleLogout())
    }
  } catch (e) {
    // 资料接口失败时回退到用户名展示，不打断工作台
  }
}

const fetchProjects = async () => {
  loading.value = true
  try {
    const data = await listProjects(currentPage.value, 10)
    projects.value = (data.records || []).map(p => ({
      ...p,
      screenshotUrl: p.screenshotUrl ? (p.screenshotUrl.startsWith('http') ? p.screenshotUrl : GATEWAY_BASE + p.screenshotUrl) : null
    }))
    total.value = data.total
  } finally {
    loading.value = false
  }
}

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

const handleCreate = async () => {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  await createProject(createForm.value)
  ElMessage.success('创建成功')
  showCreateDialog.value = false
  createForm.value = { name: '' }
  currentPage.value = 1
  await fetchProjects()
}

const handleRename = (row) => {
  renameId.value = row.id
  renameForm.value = { name: row.name }
  showRenameDialog.value = true
}

const handleRenameConfirm = async () => {
  const valid = await renameFormRef.value.validate().catch(() => false)
  if (!valid) return
  await updateProject(renameId.value, renameForm.value)
  ElMessage.success('重命名成功')
  showRenameDialog.value = false
  await fetchProjects()
}

const handleDelete = async (id) => {
  await deleteProject(id)
  ElMessage.success('删除成功')
  await fetchProjects()
}

const openEditor = (row) => {
  if (row.deleted) {
    ElMessage.warning('该作品已被平台删除，无法继续编辑')
    return
  }
  router.push(`/editor/${row.id}/0`)
}

const openProfile = async () => {
  profileForm.value = { nickname: nickname.value || username.value }
  showProfileDialog.value = true
  try {
    const data = await getProfile()
    nickname.value = data?.nickname || ''
    localStorage.setItem('nickname', nickname.value)
    profileForm.value = { nickname: nickname.value }
  } catch (e) {
    // 拉取失败时保留本地缓存值，仍可手动修改
  }
}

const handleProfileSave = async () => {
  const valid = await profileFormRef.value.validate().catch(() => false)
  if (!valid) return
  profileSaving.value = true
  try {
    const data = await updateNickname(profileForm.value.nickname)
    nickname.value = data?.nickname || profileForm.value.nickname
    localStorage.setItem('nickname', nickname.value)
    ElMessage.success('昵称已更新')
    showProfileDialog.value = false
  } finally {
    profileSaving.value = false
  }
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

onMounted(() => {
  fetchProjects()
  fetchProfile()
})
</script>

<style scoped>
.workspace {
  position: relative;
  min-height: 100vh;
  /* 手绘创作插画背景：高度铺满一屏完整展示，宽度不足处用 #FEFDFD 补白 */
  background: #fefdfd url('/craft-bg.jpg') center top / auto 100% no-repeat fixed;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
/* 极浅白色蒙版：保证卡片与标题在插画上依然清晰 */
.workspace::before {
  content: '';
  position: fixed;
  inset: 0;
  background: linear-gradient(180deg, rgba(254, 253, 253, 0.66) 0%, rgba(254, 253, 253, 0.32) 42%, rgba(254, 253, 253, 0.55) 100%);
  pointer-events: none;
  z-index: 0;
}
.ws-header {
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
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
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
.create-btn {
  height: 38px;
  border-radius: 10px;
  padding: 0 20px;
  font-weight: 600;
  background: linear-gradient(135deg, #2563eb, #0284c7);
  border: none;
  transition: all 0.3s;
}
.create-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.4);
}
.btn-icon {
  font-size: 18px;
  font-weight: 300;
  margin-right: 4px;
}
.ws-main {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px;
}
.main-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 28px;
}
.main-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
}
.project-count {
  font-size: 14px;
  color: #94a3b8;
}
.deleted-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: -8px 0 24px;
  padding: 11px 16px;
  border-radius: 11px;
  font-size: 13.5px;
  color: #9a3b0c;
  background: rgba(255, 247, 237, 0.92);
  border: 1px solid rgba(253, 186, 116, 0.7);
  box-shadow: 0 6px 18px rgba(234, 88, 12, 0.08);
}
.deleted-banner-dot {
  flex: 0 0 20px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f97316;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.loading-area, .empty-area {
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
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}
.project-card {
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
.project-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--glass-shadow-hover), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  border-color: rgba(255, 255, 255, 0.95);
}
.card-thumbnail {
  position: relative;
  width: 100%;
  height: 180px;
  background: rgba(241, 245, 249, 0.5);
  overflow: hidden;
}
.thumb-img {
  width: 100%;
  height: 100%;
}
.thumb-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s ease;
}
.project-card:hover .thumb-img img {
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
  background: rgba(15, 36, 74, 0.5);
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
  border: 2px solid rgba(255,255,255,0.6);
  border-radius: 8px;
}
.project-card:hover .card-overlay {
  opacity: 1;
}
.project-card--deleted {
  cursor: not-allowed;
  border-color: rgba(251, 146, 60, 0.55);
}
.project-card--deleted .thumb-img img,
.project-card--deleted .thumb-placeholder {
  filter: grayscale(0.85);
  opacity: 0.75;
}
.project-card--deleted .card-overlay {
  opacity: 1;
  background: linear-gradient(180deg, rgba(120, 53, 15, 0.28), rgba(120, 53, 15, 0.55));
}
.project-card--deleted .card-overlay span {
  border-color: rgba(255, 255, 255, 0.7);
  background: rgba(120, 53, 15, 0.35);
}
.project-card--deleted:hover {
  transform: none;
  box-shadow: 0 16px 40px rgba(30, 58, 138, 0.16), inset 0 1px 0 rgba(255, 255, 255, 0.9);
}
.deleted-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  color: #b45309;
  background: rgba(255, 237, 213, 0.95);
  border: 1px solid rgba(253, 186, 116, 0.8);
  vertical-align: middle;
}
.deleted-hint {
  font-size: 12px;
  color: #b45309;
  padding: 4px 2px;
}
.card-info {
  padding: 14px 16px 10px;
}
.card-title {
  margin: 0 0 6px;
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
  gap: 12px;
  font-size: 12px;
  color: #64748b;
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.card-actions {
  display: flex;
  gap: 4px;
  padding: 0 12px 12px;
}
.card-actions :deep(.el-button) {
  font-size: 12px;
}
.pagination-area {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
.modern-dialog :deep(.el-dialog__header) {
  padding: 24px 24px 0;
}
.modern-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}
.modern-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}
.modern-dialog :deep(.el-dialog__footer) {
  padding: 0 24px 24px;
}
.create-dialog :deep(.el-dialog) {
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 24px 60px rgba(30, 58, 138, 0.18);
}
.create-dialog :deep(.el-dialog__header) {
  padding: 0;
  margin: 0;
}
.create-dialog :deep(.el-dialog__body) {
  padding: 0 26px 4px;
}
.create-dialog :deep(.el-dialog__footer) {
  padding: 16px 26px 24px;
}
.create-dialog-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 24px 26px 18px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.14), rgba(2, 132, 199, 0.1) 55%, rgba(255, 255, 255, 0));
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 18px 18px 0 0;
}
.create-icon {
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  background: linear-gradient(135deg, #2563eb, #0284c7);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.35);
}
.create-icon svg {
  width: 22px;
  height: 22px;
}
.create-titles h3 {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: -0.2px;
}
.create-titles p {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}
.create-form {
  padding-top: 18px;
}
.create-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #334155;
  padding-bottom: 6px;
}
.create-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 0 0 1px rgba(203, 213, 225, 0.9) inset;
  transition: box-shadow 0.2s;
}
.create-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #2563eb inset, 0 0 0 4px rgba(37, 99, 235, 0.12);
}
.create-tips {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 4px 0 6px;
  padding: 14px 16px;
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.9);
  border: 1px solid rgba(226, 232, 240, 0.9);
}
.tip {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  color: #475569;
}
.tip-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: 0 0 7px;
}
.tip-dot--blue {
  background: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}
.tip-dot--purple {
  background: #0ea5e9;
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.15);
}
.tip-dot--green {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
}
.dialog-cancel {
  border-radius: 10px;
  padding: 0 22px;
}
.dialog-confirm {
  border-radius: 10px;
  padding: 0 22px;
  background: linear-gradient(135deg, #2563eb, #0284c7);
  border: none;
  font-weight: 600;
}
.dialog-confirm:hover {
  background: linear-gradient(135deg, #1d4ed8, #0369a1);
}
</style>