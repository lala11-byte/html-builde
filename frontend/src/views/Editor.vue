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
      <div class="canvas">画布（待开发）</div>
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
const generating = ref(false)
const progressLogs = ref([])
const downloadUrl = ref('')
let eventSource = null

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
  padding: 24px;
  background: #f0f2f5;
  overflow: auto;
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
</style>