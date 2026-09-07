<template>
  <div class="editor">
    <!-- 顶栏 -->
    <div class="topbar">
      <el-button text @click="goBack">← 返回工作台</el-button>
      <span class="project-name">{{ projectName }}</span>
      <el-select v-model="currentPageId" placeholder="选择页面" @change="switchPage">
        <el-option v-for="p in pages" :key="p.id" :label="p.title" :value="p.id" />
      </el-select>
      <el-button type="primary" @click="handleExport">导出</el-button>
    </div>
    <!-- 三栏布局 -->
    <div class="editor-body">
      <div class="panel left-panel">
        <el-tabs>
          <el-tab-pane label="组件库">
            <div class="placeholder-text">组件库（待开发）</div>
          </el-tab-pane>
          <el-tab-pane label="AI 生成">
            <div class="ai-panel">
              <el-select
                v-model="selectedTemplate"
                placeholder="选择提示词模板"
                style="width: 100%; margin-bottom: 8px;"
                @change="applyTemplate"
              >
                <el-option label="个人简历网站" value="resume" />
                <el-option label="博客网站" value="blog" />
                <el-option label="企业官网" value="company" />
              </el-select>
              <el-input
                v-model="aiPrompt"
                type="textarea"
                :rows="4"
                placeholder="描述你的网站需求，例如：&#10;生成一个博客网站，有文章列表和详情页，包含一个关于页面"
                :disabled="generating"
              />
              <el-button
                type="primary"
                :loading="generating"
                :disabled="!aiPrompt.trim()"
                style="width: 100%; margin-top: 8px;"
                @click="handleGenerate"
              >
                {{ generating ? '生成中...' : '生成网站' }}
              </el-button>
              <div v-if="progressLogs.length > 0" class="progress-log">
                <div
                  v-for="(log, idx) in progressLogs"
                  :key="idx"
                  :class="['log-line', { 'log-error': log.startsWith('[错误]'), 'log-complete': log.startsWith('[完成]') }]"
                >
                  {{ log }}
                </div>
              </div>
              <div v-if="downloadUrl" style="margin-top: 8px;">
                <el-button type="success" @click="handleDownload">
                  下载生成的项目
                </el-button>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="本地运行">
            <div class="run-panel">
              <div class="run-status-row">
                <span>运行状态：</span>
                <el-tag :type="runStatus.running ? 'success' : 'info'" size="small">
                  {{ runStatus.running ? '运行中' : '未运行' }}
                </el-tag>
              </div>
              <div v-if="runStatus.running" class="run-url">
                http://localhost:{{ runStatus.port }}
              </div>
              <div v-if="!currentTaskId" class="run-tip">当前页面暂无生成结果，请先在「AI 生成」中生成网站</div>
              <div class="run-actions">
                <el-button
                  type="primary"
                  size="small"
                  :loading="runStarting"
                  :disabled="!currentTaskId || runStatus.running"
                  @click="handleStartRun"
                >
                  {{ runStarting ? '启动中...' : '启动本机运行' }}
                </el-button>
                <el-button
                  type="danger"
                  size="small"
                  :disabled="!runStatus.running"
                  @click="handleStopRun"
                >
                  停止
                </el-button>
                <el-button
                  v-if="runStatus.running"
                  type="success"
                  size="small"
                  @click="openRunSite"
                >
                  打开网站
                </el-button>
              </div>
              <div class="run-tip">首次启动需安装依赖，可能需要几分钟；离开本页面将自动停止运行</div>
              <div v-if="runLogs.length > 0" class="progress-log">
                <div v-for="(log, idx) in runLogs" :key="idx" class="log-line">{{ log }}</div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
      <div class="canvas">
        <div v-if="!previewUrl" class="placeholder-text">画布（生成网站后将在此预览）</div>
        <iframe
          v-else
          :src="previewUrl"
          class="preview-frame"
          sandbox="allow-scripts allow-same-origin"
          title="网站预览"
        />
      </div>
      <div class="panel right-panel">属性面板（待开发）</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listPages, submitGenerate, getLatestByPage, startRun, stopRun, getRunStatus } from '../api'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId
const projectName = ref('编辑器')
const currentPageId = ref(route.params.pageId)
const pages = ref([])

// AI 生成
const aiPrompt = ref('')
const selectedTemplate = ref('')
const generating = ref(false)
const progressLogs = ref([])
const downloadUrl = ref('')
const previewUrl = ref('')
let eventSource = null

// 本地运行
const currentTaskId = ref('')
const runStatus = ref({ running: false, port: null })
const runStarting = ref(false)
const runLogs = ref([])

// 网关地址：后端返回的是相对路径，iframe/window.open 需要绝对地址，
// 否则会解析到前端源（localhost:5173），加载成 HTML Builder 自己的页面
const GATEWAY_BASE = 'http://localhost:8080'
const toAbsolute = (url) => (url && !url.startsWith('http') ? GATEWAY_BASE + url : url)

const promptTemplates = {
  resume: `生成一个个人简历网站，具体需求如下：

【页面规划】
1. 首页（index.html）：个人简介、技能标签、项目经历卡片展示、联系方式
2. 项目详情页（project.html）：项目名称、技术栈、项目描述、项目截图占位、项目链接
3. 联系页面（contact.html）：联系表单（姓名、邮箱、消息）、社交媒体链接

【设计风格】
- 现代简约风格，深色主题（#1a1a2e 主色调，#e94560 强调色）
- 顶部导航栏（首页、项目、联系），固定定位
- 首页 Hero 区域：头像占位 + 姓名 + 一句话简介 + 打字机效果
- 技能区：Flexbox 标签布局，hover 放大动画
- 项目卡片：Grid 布局，hover 阴影上浮
- 响应式：移动端导航栏收起为汉堡菜单

【数据库设计】
- projects 表：id, title, tech_stack, description, link, sort_order
- messages 表：id, name, email, message, created_at

【功能】
- 首页加载时通过 Fetch API 获取项目列表展示
- 联系页表单提交到后端 API 保存到 messages 表
- 底部版权信息栏`,

  blog: `生成一个博客网站，包含文章列表页、文章详情页和关于页面。文章列表页展示文章卡片（标题、摘要、日期、标签），点击进入详情页阅读全文。关于页面展示博主介绍。数据库需包含 articles 表（id, title, summary, content, tags, created_at）和 tags 表（id, name）。后端提供文章 CRUD API。设计风格：清新简洁，白色背景，阅读友好。`,

  company: `生成一个企业官网，包含首页（公司介绍、核心业务、团队展示、客户案例）、产品列表页、产品详情页。数据库需包含 products 表（id, name, description, features, image_url, category）和 cases 表（id, client_name, project_desc, result, date）。后端提供产品和案例的 CRUD API。设计风格：商务专业，蓝色主色调，大图轮播。`
}

const applyTemplate = (key) => {
  if (key && promptTemplates[key]) {
    aiPrompt.value = promptTemplates[key]
  }
}

const goBack = () => router.push('/workspace')

const switchPage = (pageId) => {
  router.push(`/editor/${projectId}/${pageId}`)
}

const handleExport = () => {
  ElMessage.info('导出功能待开发')
}

// AI 生成
const handleGenerate = async () => {
  if (!aiPrompt.value.trim()) {
    ElMessage.warning('请输入网站需求描述')
    return
  }

  generating.value = true
  progressLogs.value = []
  downloadUrl.value = ''
  previewUrl.value = ''

  try {
    const result = await submitGenerate(aiPrompt.value, route.params.pageId)
    const taskId = result.id
    currentTaskId.value = taskId

    progressLogs.value.push('[提交] 任务已提交，开始生成...')

    // 连接 SSE 进度（SSE 接口已加入网关白名单，无需 token）
    eventSource = new EventSource(`http://localhost:8080/api/v1/generator/progress/${taskId}`)

    eventSource.addEventListener('progress', (event) => {
      progressLogs.value.push(event.data)
    })

    eventSource.addEventListener('complete', (event) => {
      const data = JSON.parse(event.data)
      downloadUrl.value = toAbsolute(data.downloadUrl)
      previewUrl.value = toAbsolute(data.previewUrl)
      generating.value = false
      progressLogs.value.push('[完成] 网站生成完成！')
      eventSource.close()
    })

    eventSource.addEventListener('error', (event) => {
      progressLogs.value.push('[错误] ' + (event.data || '生成过程出错'))
      generating.value = false
      eventSource.close()
    })

    eventSource.onerror = () => {
      if (!generating.value) return
      progressLogs.value.push('[错误] 连接中断，请刷新页面查看进度')
      generating.value = false
      eventSource.close()
    }

  } catch (e) {
    generating.value = false
    progressLogs.value.push('[错误] ' + e.message)
  }
}

const handleDownload = () => {
  if (downloadUrl.value) {
    window.open(downloadUrl.value, '_blank')
  }
}

// 加载页面数据（页面列表 + 已有生成结果 + 运行状态）
const loadPageData = async () => {
  try {
    const data = await listPages(projectId)
    pages.value = data.records || []
  } catch {
    // 忽略
  }

  // 加载该页面已有的生成结果
  try {
    const latest = await getLatestByPage(route.params.pageId)
    if (latest && latest.previewUrl) {
      previewUrl.value = toAbsolute(latest.previewUrl)
      downloadUrl.value = toAbsolute(latest.downloadUrl)
      currentTaskId.value = latest.id
      // 恢复运行状态（例如生成服务重启后进程仍在运行）
      const status = await getRunStatus(latest.id)
      runStatus.value = status
      if (status.logs) runLogs.value = status.logs
    } else {
      currentTaskId.value = ''
      previewUrl.value = ''
      runStatus.value = { running: false, port: null }
    }
  } catch {
    currentTaskId.value = ''
  }
}

// 切换页面时（组件被路由复用，不触发 onMounted）重新加载
watch(() => route.params.pageId, () => {
  if (route.name === 'Editor') {
    loadPageData()
  }
})

// 离开页面时停止本机运行
const stopRunOnLeave = () => {
  if (!currentTaskId.value) return
  const token = localStorage.getItem('accessToken')
  fetch(`${GATEWAY_BASE}/api/v1/generator/run/${currentTaskId.value}/stop`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    keepalive: true
  }).catch(() => {})
}

onMounted(() => {
  loadPageData()
  window.addEventListener('beforeunload', stopRunOnLeave)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', stopRunOnLeave)
  if (eventSource) {
    eventSource.close()
  }
  // SPA 路由离开（关闭浏览器走 beforeunload）
  stopRunOnLeave()
})

// 本地运行操作
const handleStartRun = async () => {
  if (!currentTaskId.value) return
  runStarting.value = true
  runLogs.value = ['[运行] 正在启动...']
  try {
    const status = await startRun(currentTaskId.value)
    runStatus.value = status
    runLogs.value = status.logs || []
    if (status.running) {
      ElMessage.success('网站已在本机启动')
    }
  } catch (e) {
    ElMessage.error(e.message || '启动失败')
  } finally {
    runStarting.value = false
  }
}

const handleStopRun = async () => {
  if (!currentTaskId.value) return
  try {
    const status = await stopRun(currentTaskId.value)
    runStatus.value = { running: false, port: status.port }
    if (status.logs) runLogs.value = status.logs
    ElMessage.success('网站已停止')
  } catch (e) {
    ElMessage.error(e.message || '停止失败')
  }
}

const openRunSite = () => {
  if (runStatus.value.port) {
    window.open(`http://localhost:${runStatus.value.port}`, '_blank')
  }
}
</script>

<style scoped>
.editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-bottom: 1px solid #dcdfe6;
  background: #fff;
}
.project-name {
  font-weight: bold;
  flex: 1;
}
.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.panel {
  width: 240px;
  border-right: 1px solid #dcdfe6;
  padding: 12px;
  background: #fafafa;
  overflow-y: auto;
}
.right-panel {
  width: 280px;
  border-right: none;
  border-left: 1px solid #dcdfe6;
}
.canvas {
  flex: 1;
  padding: 0;
  background: #f0f2f5;
  overflow: hidden;
}
.preview-frame {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}
.placeholder-text {
  color: #999;
  text-align: center;
  padding: 40px 0;
}
.ai-panel {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.run-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.run-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.run-url {
  font-family: monospace;
  font-size: 12px;
  color: #409eff;
  word-break: break-all;
}
.run-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.run-tip {
  font-size: 12px;
  color: #999;
  line-height: 1.5;
}
.progress-log {
  margin-top: 8px;
  padding: 8px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
  max-height: 300px;
  overflow-y: auto;
  line-height: 1.6;
}
.log-line {
  white-space: pre-wrap;
  word-break: break-all;
}
.log-error {
  color: #f56c6c;
}
.log-complete {
  color: #67c23a;
}
</style>