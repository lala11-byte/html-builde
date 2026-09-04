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
    <!-- 三栏布局占位 -->
    <div class="editor-body">
      <div class="panel left-panel">组件库（待开发）</div>
      <div class="canvas">画布（待开发）</div>
      <div class="panel right-panel">属性面板（待开发）</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listPages } from '../api'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId
const projectName = ref('编辑器')
const currentPageId = ref(route.params.pageId)
const pages = ref([])

const goBack = () => router.push('/workspace')

const switchPage = (pageId) => {
  router.push(`/editor/${projectId}/${pageId}`)
}

const handleExport = () => {
  ElMessage.info('导出功能待开发')
}

onMounted(async () => {
  try {
    const data = await listPages(projectId)
    pages.value = data.records || []
  } catch {
    // 忽略
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
</style>