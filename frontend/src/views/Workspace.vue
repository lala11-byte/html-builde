<template>
  <div class="workspace">
    <el-container>
      <el-header class="header">
        <h2>HTML 生成器</h2>
        <el-button type="danger" @click="handleLogout">退出</el-button>
      </el-header>
      <el-main>
        <div class="toolbar">
          <el-button type="primary" @click="showCreateDialog = true">新建项目</el-button>
        </div>
        <el-table :data="projects" v-loading="loading" style="width: 100%">
          <el-table-column prop="name" label="项目名称" />
          <el-table-column prop="pageCount" label="页面数" width="100" />
          <el-table-column prop="updatedAt" label="更新时间" width="180">
            <template #default="{ row }">
              {{ new Date(row.updatedAt).toLocaleString() }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" @click="openEditor(row)">编辑</el-button>
              <el-button size="small" @click="handleRename(row)">重命名</el-button>
              <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)">
                <template #reference>
                  <el-button size="small" type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="total > 0"
          v-model:current-page="currentPage"
          :page-size="10"
          :total="total"
          layout="prev, pager, next"
          @current-change="fetchProjects"
          style="margin-top: 16px; justify-content: center;"
        />
      </el-main>
    </el-container>

    <!-- 新建项目对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建项目" width="400px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="createForm.name" maxlength="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重命名对话框 -->
    <el-dialog v-model="showRenameDialog" title="重命名项目" width="400px">
      <el-form ref="renameFormRef" :model="renameForm" :rules="createRules">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="renameForm.name" maxlength="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRenameDialog = false">取消</el-button>
        <el-button type="primary" @click="handleRenameConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProjects, createProject, updateProject, deleteProject } from '../api'

const router = useRouter()
const loading = ref(false)
const projects = ref([])
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

const fetchProjects = async () => {
  loading.value = true
  try {
    const data = await listProjects(currentPage.value, 10)
    projects.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
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
  router.push(`/editor/${row.id}/0`)
}

const handleLogout = () => {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  router.push('/login')
}

onMounted(fetchProjects)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #dcdfe6;
}
.toolbar {
  margin-bottom: 16px;
}
</style>