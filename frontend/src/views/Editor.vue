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
        </el-tabs>
      </div>
      <div class="canvas">
        <div v-if="previewUrl" class="preview-container">
          <div class="preview-toolbar">
            <span>预览模式</span>
            <el-button size="small" @click="refreshPreview">刷新</el-button>
            <el-button size="small" @click="openInNewTab">新窗口打开</el-button>
          </div>
          <iframe :src="previewUrl" class="preview-iframe" frameborder="0"></iframe>
        </div>
        <div v-else class="placeholder-text">
          生成网站后，预览将显示在这里
        </div>
      </div>
      <div class="panel right-panel">属性面板（待开发）</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listPages, submitGenerate } from '../api'

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
const currentTaskId = ref('')
let eventSource = null

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
    const result = await submitGenerate(aiPrompt.value)
    const taskId = result.id

    progressLogs.value.push('[提交] 任务已提交，开始生成...')

    // 连接 SSE 进度（SSE 接口已加入网关白名单，无需 token）
    eventSource = new EventSource(`http://localhost:8080/api/v1/generator/progress/${taskId}`)

    eventSource.addEventListener('progress', (event) => {
      progressLogs.value.push(event.data)
    })

    eventSource.addEventListener('complete', (event) => {
      const data = JSON.parse(event.data)
      downloadUrl.value = data.downloadUrl
      currentTaskId.value = taskId
      previewUrl.value = `http://localhost:8080/api/v1/generator/preview/${taskId}/index.html`
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

const refreshPreview = () => {
  if (previewUrl.value) {
    const url = previewUrl.value
    previewUrl.value = ''
    setTimeout(() => { previewUrl.value = url }, 0)
  }
}

const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

onMounted(async () => {
  try {
    const data = await listPages(projectId)
    pages.value = data.records || []
  } catch {
    // 忽略
  }
})

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
  }
})
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
  display: flex;
  flex-direction: column;
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
.preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: white;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.preview-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  font-size: 14px;
  color: #606266;
}
.preview-iframe {
  flex: 1;
  width: 100%;
  border: none;
}
</style>