<template>
  <div class="admin">
    <header class="ad-header">
      <div class="header-left">
        <div class="brand" @click="router.push('/workspace')">
          <svg viewBox="0 0 36 36" aria-hidden="true">
            <defs>
              <linearGradient id="wc-logo-admin" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="#4f8cff" />
                <stop offset="1" stop-color="#1d4ed8" />
              </linearGradient>
            </defs>
            <path d="M5 17.5 L30.5 4.5 L22.5 30 L16.8 19.6 Z" fill="url(#wc-logo-admin)" />
            <path d="M16.8 19.6 L30.5 4.5 L22.5 30 Z" fill="#9cc3ff" />
          </svg>
          <span class="brand-name">WebCraft</span>
          <span class="admin-badge">管理后台</span>
        </div>
        <nav class="header-nav">
          <router-link to="/workspace" class="nav-link">我的项目</router-link>
          <router-link to="/community" class="nav-link">作品广场</router-link>
          <router-link to="/admin" class="nav-link nav-link--active">后台管理</router-link>
        </nav>
      </div>
      <div class="header-right">
        <button class="user-chip" type="button" @click="handleLogout">
          <span class="user-chip-name">{{ displayName || '管理员' }}</span>
          <span class="logout-text">退出</span>
        </button>
      </div>
    </header>

    <main class="ad-main">
      <section class="stat-grid">
        <div class="stat-card">
          <span class="stat-label">注册用户</span>
          <span class="stat-value">{{ formatNum(stats.userCount) }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">作品总数</span>
          <span class="stat-value">{{ formatNum(stats.projectCount) }}</span>
        </div>
        <div class="stat-card stat-card--danger">
          <span class="stat-label">已删除作品</span>
          <span class="stat-value">{{ formatNum(stats.deletedProjectCount) }}</span>
        </div>
        <div class="stat-card stat-card--ok">
          <span class="stat-label">社区已上架</span>
          <span class="stat-value">{{ formatNum(stats.publishedCount) }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">社区已下架</span>
          <span class="stat-value">{{ formatNum(stats.unpublishedCount) }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">生成任务</span>
          <span class="stat-value">{{ formatNum(stats.taskCount) }}</span>
        </div>
        <div class="stat-card stat-card--warn">
          <span class="stat-label">运行中 / 排队</span>
          <span class="stat-value">{{ formatNum(stats.runningTaskCount) }}</span>
        </div>
        <div class="stat-card stat-card--danger">
          <span class="stat-label">失败任务</span>
          <span class="stat-value">{{ formatNum(stats.failedTaskCount) }}</span>
        </div>
        <div class="stat-card stat-card--token">
          <span class="stat-label">累计 Token</span>
          <span class="stat-value">{{ formatNum(stats.totalTokens) }}</span>
        </div>
      </section>

      <el-tabs v-model="activeTab" class="ad-tabs">
        <!-- Token 用量 -->
        <el-tab-pane label="Token 用量" name="token">
          <div class="toolbar">
            <el-input
              v-model="tokenKeyword"
              placeholder="搜索用户名 / 昵称"
              clearable
              class="tool-input"
              @keyup.enter="searchTokenUsers"
              @clear="searchTokenUsers"
            />
            <el-button type="primary" @click="searchTokenUsers">搜索</el-button>
          </div>

          <el-table :data="tokenUsers" v-loading="tokenLoading" stripe class="ad-table" empty-text="暂无 Token 用量数据">
            <el-table-column label="用户" min-width="180">
              <template #default="{ row }">
                <div class="user-cell">
                  <span class="user-cell-name">{{ row.nickname || row.username || ('用户' + row.userId) }}</span>
                  <span class="user-cell-sub">#{{ row.userId }} {{ row.username }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="角色 / 状态" width="140">
              <template #default="{ row }">
                <el-tag size="small" :type="row.role === 'admin' ? 'warning' : 'info'">{{ row.role === 'admin' ? '管理员' : '用户' }}</el-tag>
                <el-tag size="small" :type="row.status === 'banned' ? 'danger' : 'success'" class="status-tag">
                  {{ row.status === 'banned' ? '已封禁' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="taskCount" label="任务数" width="90" align="right" />
            <el-table-column label="未删除作品消耗" min-width="160" align="right">
              <template #default="{ row }">
                <span class="token-main">{{ formatNum(row.totalTokens - row.deletedTokens) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已删除作品消耗" min-width="140" align="right">
              <template #default="{ row }">
                <span class="token-danger">{{ formatNum(row.deletedTokens) }}</span>
                <div class="token-sub">{{ row.deletedTaskCount }} 个任务</div>
              </template>
            </el-table-column>
            <el-table-column label="累计 Token" min-width="130" align="right">
              <template #default="{ row }">
                <span class="token-total">{{ formatNum(row.totalTokens) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openTaskDrill(row)">任务明细</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="tokenTotal > tokenPageSize" class="pagination-area">
            <el-pagination
              v-model:current-page="tokenPage"
              :page-size="tokenPageSize"
              :total="tokenTotal"
              layout="prev, pager, next"
              @current-change="fetchTokenUsers"
            />
          </div>

          <section v-if="drillUser" class="drill">
            <div class="drill-head">
              <div>
                <h3>{{ drillUser.nickname || drillUser.username || ('用户' + drillUser.userId) }} 的任务明细</h3>
                <p>共 {{ drillUser.taskCount }} 个任务，累计 {{ formatNum(drillUser.totalTokens) }} token</p>
              </div>
              <div class="drill-actions">
                <el-select v-model="taskDeleted" class="filter-select" @change="searchTasks">
                  <el-option label="全部任务" value="" />
                  <el-option label="未删除作品" value="0" />
                  <el-option label="已删除作品" value="1" />
                </el-select>
                <el-button text @click="drillUser = null">收起</el-button>
              </div>
            </div>
            <el-table :data="tokenTasks" v-loading="taskLoading" stripe size="small" class="ad-table" empty-text="暂无任务">
              <el-table-column prop="id" label="任务ID" width="90" />
              <el-table-column label="作品" min-width="170">
                <template #default="{ row }">
                  <span>{{ row.projectName || '—' }}</span>
                  <el-tag v-if="row.deleted" size="small" type="danger" class="status-tag">已删除</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="需求描述" min-width="240" show-overflow-tooltip>
                <template #default="{ row }">{{ row.prompt || '—' }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" />
              <el-table-column prop="modelCalls" label="调用" width="80" align="right" />
              <el-table-column label="Prompt" width="110" align="right">
                <template #default="{ row }">{{ formatNum(row.promptTokens) }}</template>
              </el-table-column>
              <el-table-column label="Completion" width="120" align="right">
                <template #default="{ row }">{{ formatNum(row.completionTokens) }}</template>
              </el-table-column>
              <el-table-column label="合计" width="110" align="right">
                <template #default="{ row }"><strong>{{ formatNum(row.totalTokens) }}</strong></template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="160" />
            </el-table>
            <div v-if="taskTotal > taskPageSize" class="pagination-area">
              <el-pagination
                v-model:current-page="taskPage"
                :page-size="taskPageSize"
                :total="taskTotal"
                layout="prev, pager, next"
                small
                @current-change="fetchTasks"
              />
            </div>
          </section>
        </el-tab-pane>

        <!-- 作品管理 -->
        <el-tab-pane label="作品管理" name="project">
          <div class="toolbar">
            <el-input v-model="projectKeyword" placeholder="搜索作品名称" clearable class="tool-input" @keyup.enter="searchProjects" @clear="searchProjects" />
            <el-input v-model="projectUserId" placeholder="按用户ID筛选" clearable class="tool-input tool-input--sm" @keyup.enter="searchProjects" @clear="searchProjects" />
            <el-select v-model="projectDeleted" class="filter-select" @change="searchProjects">
              <el-option label="全部状态" value="" />
              <el-option label="未删除" value="0" />
              <el-option label="已删除" value="1" />
            </el-select>
            <el-select v-model="projectCommunity" class="filter-select" @change="searchProjects">
              <el-option label="全部社区状态" value="" />
              <el-option label="已上架" value="published" />
              <el-option label="已下架" value="unpublished" />
            </el-select>
            <el-button type="primary" @click="searchProjects">查询</el-button>
          </div>

          <el-table :data="projects" v-loading="projectLoading" stripe class="ad-table" empty-text="没有符合条件的作品">
            <el-table-column label="作品" min-width="220">
              <template #default="{ row }">
                <div class="project-cell">
                  <div class="project-thumb">
                    <img v-if="thumbUrl(row.screenshotUrl)" :src="thumbUrl(row.screenshotUrl)" alt="" @error="row._broken = true" />
                    <span v-else class="thumb-fallback">{{ (row.name || '?').charAt(0) }}</span>
                  </div>
                  <div class="project-cell-info">
                    <span class="project-cell-name">{{ row.name }}</span>
                    <span class="user-cell-sub">#{{ row.id }} · {{ row.pageCount }} 页</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="作者" min-width="140">
              <template #default="{ row }">
                <div class="user-cell">
                  <span class="user-cell-name">{{ row.authorName }}</span>
                  <span class="user-cell-sub">用户 #{{ row.userId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="likeCount" label="点赞" width="80" align="right" />
            <el-table-column label="社区状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.communityStatus === 'published' ? 'success' : 'info'">
                  {{ row.communityStatus === 'published' ? '已上架' : '已下架' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="删除状态" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="row.deleted ? 'danger' : 'success'">{{ row.deleted ? '已删除' : '正常' }}</el-tag>
                <div v-if="row.deleted" class="user-cell-sub">{{ row.deletedAt }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="160" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <template v-if="row.deleted">
                  <el-button text type="primary" size="small" @click="handleRestore(row)">恢复</el-button>
                </template>
                <template v-else>
                  <el-button
                    text
                    :type="row.communityStatus === 'published' ? 'warning' : 'success'"
                    size="small"
                    @click="handleCommunity(row, row.communityStatus === 'published' ? 'unpublished' : 'published')"
                  >
                    {{ row.communityStatus === 'published' ? '下架' : '上架' }}
                  </el-button>
                  <el-popconfirm title="删除后作者工作台会看到该作品已被删除，确认删除？" confirm-button-text="删除" @confirm="handleDeleteProject(row)">
                    <template #reference>
                      <el-button text type="danger" size="small">删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="projectTotal > projectPageSize" class="pagination-area">
            <el-pagination
              v-model:current-page="projectPage"
              :page-size="projectPageSize"
              :total="projectTotal"
              layout="prev, pager, next"
              @current-change="fetchProjects"
            />
          </div>
        </el-tab-pane>

        <!-- 生成任务 -->
        <el-tab-pane label="生成任务" name="task">
          <div class="toolbar">
            <el-input v-model="adminTaskKeyword" placeholder="搜索需求 / 作品 / 用户" clearable class="tool-input" @keyup.enter="searchAdminTasks" @clear="searchAdminTasks" />
            <el-input v-model="adminTaskUserId" placeholder="按用户ID筛选" clearable class="tool-input tool-input--sm" @keyup.enter="searchAdminTasks" @clear="searchAdminTasks" />
            <el-select v-model="adminTaskStatus" class="filter-select" @change="searchAdminTasks">
              <el-option label="全部状态" value="" />
              <el-option label="排队中" value="PENDING" />
              <el-option label="运行中" value="RUNNING" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="失败" value="FAILED" />
              <el-option label="已终止" value="CANCELLED" />
            </el-select>
            <el-button type="primary" @click="searchAdminTasks">查询</el-button>
          </div>

          <el-table :data="adminTasks" v-loading="adminTaskLoading" stripe class="ad-table" empty-text="没有符合条件的任务">
            <el-table-column prop="id" label="任务ID" width="80" />
            <el-table-column label="用户" min-width="140">
              <template #default="{ row }">
                <div class="user-cell">
                  <span class="user-cell-name">{{ row.nickname || row.username || ('用户' + row.userId) }}</span>
                  <span class="user-cell-sub">#{{ row.userId }} {{ row.username || '' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="作品" min-width="140">
              <template #default="{ row }">
                <span>{{ row.projectName || '—' }}</span>
                <el-tag v-if="row.projectDeleted" size="small" type="danger" class="status-tag">已删除</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="需求描述" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.prompt || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="taskStatusType(row.status)">{{ taskStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Token" width="100" align="right">
              <template #default="{ row }">{{ formatNum(row.totalTokens) }}</template>
            </el-table-column>
            <el-table-column label="失败原因" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.errorMessage || '—' }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PENDING' || row.status === 'RUNNING'"
                  text
                  type="warning"
                  size="small"
                  @click="handleCancelTask(row)"
                >终止</el-button>
                <el-popconfirm
                  v-else-if="row.status === 'FAILED' || row.status === 'CANCELLED'"
                  title="删除该任务及其页面快照，确认删除？"
                  confirm-button-text="删除"
                  @confirm="handleDeleteTask(row)"
                >
                  <template #reference>
                    <el-button text type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
                <span v-else class="user-cell-sub">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="adminTaskTotal > adminTaskPageSize" class="pagination-area">
            <el-pagination
              v-model:current-page="adminTaskPage"
              :page-size="adminTaskPageSize"
              :total="adminTaskTotal"
              layout="prev, pager, next"
              @current-change="fetchAdminTasks"
            />
          </div>
        </el-tab-pane>

        <!-- 社区管理 -->
        <el-tab-pane label="社区管理" name="community">
          <div class="toolbar">
            <el-input v-model="communityKeyword" placeholder="搜索要上架/下架的作品" clearable class="tool-input" @keyup.enter="searchCommunity" @clear="searchCommunity" />
            <el-select v-model="communityStatus" class="filter-select" @change="searchCommunity">
              <el-option label="全部作品" value="" />
              <el-option label="已上架" value="published" />
              <el-option label="已下架" value="unpublished" />
            </el-select>
            <el-button type="primary" @click="searchCommunity">查询</el-button>
          </div>

          <el-table :data="communityItems" v-loading="communityLoading" stripe class="ad-table" empty-text="没有符合条件的作品">
            <el-table-column label="作品" min-width="220">
              <template #default="{ row }">
                <div class="project-cell">
                  <div class="project-thumb">
                    <img v-if="thumbUrl(row.screenshotUrl)" :src="thumbUrl(row.screenshotUrl)" alt="" />
                    <span v-else class="thumb-fallback">{{ (row.name || '?').charAt(0) }}</span>
                  </div>
                  <div class="project-cell-info">
                    <span class="project-cell-name">{{ row.name }}</span>
                    <span class="user-cell-sub">{{ row.authorName }} · {{ row.likeCount }} 赞</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.communityStatus === 'published' ? 'success' : 'info'">
                  {{ row.communityStatus === 'published' ? '广场展示中' : '已下架' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="publishedAt" label="上架时间" width="170" />
            <el-table-column prop="updatedAt" label="最近更新" width="170" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  :type="row.communityStatus === 'published' ? 'warning' : 'success'"
                  size="small"
                  @click="handleCommunity(row, row.communityStatus === 'published' ? 'unpublished' : 'published')"
                >
                  {{ row.communityStatus === 'published' ? '下架' : '上架' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="communityTotal > communityPageSize" class="pagination-area">
            <el-pagination
              v-model:current-page="communityPage"
              :page-size="communityPageSize"
              :total="communityTotal"
              layout="prev, pager, next"
              @current-change="fetchCommunity"
            />
          </div>
        </el-tab-pane>

        <!-- 用户管理 -->
        <el-tab-pane label="用户管理" name="user">
          <div class="toolbar">
            <el-input v-model="userKeyword" placeholder="搜索用户名 / 昵称" clearable class="tool-input" @keyup.enter="searchUsers" @clear="searchUsers" />
            <el-select v-model="userStatus" class="filter-select" @change="searchUsers">
              <el-option label="全部用户" value="" />
              <el-option label="正常" value="active" />
              <el-option label="已封禁" value="banned" />
            </el-select>
            <el-button type="primary" @click="searchUsers">搜索</el-button>
          </div>

          <el-table :data="users" v-loading="userLoading" stripe class="ad-table" empty-text="没有符合条件的用户">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="用户" min-width="160">
              <template #default="{ row }">
                <div class="user-cell">
                  <span class="user-cell-name">{{ row.nickname || row.username }}</span>
                  <span class="user-cell-sub">{{ row.username }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="角色" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.role === 'admin' ? 'warning' : 'info'">{{ row.role === 'admin' ? '管理员' : '用户' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'banned' ? 'danger' : 'success'">
                  {{ row.status === 'banned' ? '已封禁' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="封禁理由" min-width="180">
              <template #default="{ row }">
                <span v-if="row.banReason">{{ row.banReason }}</span>
                <span v-else class="user-cell-sub">—</span>
                <div v-if="row.bannedAt" class="user-cell-sub">{{ row.bannedAt }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="注册时间" width="160" />
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'banned'"
                  text
                  type="success"
                  size="small"
                  @click="handleUnban(row)"
                >解封</el-button>
                <el-button
                  v-else
                  text
                  type="danger"
                  size="small"
                  :disabled="row.role === 'admin'"
                  @click="openBanDialog(row)"
                >封禁</el-button>
                <el-button text type="primary" size="small" @click="handleToggleRole(row)">
                  {{ row.role === 'admin' ? '取消管理员' : '设为管理员' }}
                </el-button>
                <el-button text type="primary" size="small" @click="openPasswordDialog(row)">重置密码</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="userTotal > userPageSize" class="pagination-area">
            <el-pagination
              v-model:current-page="userPage"
              :page-size="userPageSize"
              :total="userTotal"
              layout="prev, pager, next"
              @current-change="fetchUsers"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </main>

    <el-dialog v-model="showBanDialog" title="封禁用户" width="440px" :close-on-click-modal="false">
      <p class="ban-tip">封禁后该用户将无法登录，已登录会话在工作台会收到提示并退出。</p>
      <el-form label-position="top">
        <el-form-item label="封禁理由">
          <el-input v-model="banReason" maxlength="100" show-word-limit placeholder="例如：发布违规内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBanDialog = false">取消</el-button>
        <el-button type="danger" :loading="banSaving" @click="handleBan">确认封禁</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPasswordDialog" title="重置密码" width="420px" :close-on-click-modal="false">
      <p class="ban-tip">重置后该用户需使用新密码重新登录，请及时告知用户。</p>
      <el-form label-position="top">
        <el-form-item label="用户">
          <span>{{ passwordUser ? (passwordUser.nickname || passwordUser.username) : '' }}</span>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password maxlength="32" placeholder="至少 6 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPasswordDialog = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="handleResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminStats,
  listAdminUsers,
  banUser,
  unbanUser,
  listAdminProjects,
  adminDeleteProject,
  restoreAdminProject,
  setAdminCommunityStatus,
  listTokenUsers,
  listTokenTasks,
  listAdminTasks,
  cancelAdminTask,
  deleteAdminTask,
  setAdminUserRole,
  resetAdminUserPassword
} from '../api'

const router = useRouter()
const GATEWAY_BASE = 'http://localhost:8080'

const username = ref(localStorage.getItem('username') || '')
const nickname = ref(localStorage.getItem('nickname') || '')
const displayName = computed(() => nickname.value || username.value)

const activeTab = ref('token')
const stats = reactive({
  userCount: 0,
  projectCount: 0,
  deletedProjectCount: 0,
  publishedCount: 0,
  unpublishedCount: 0,
  taskCount: 0,
  runningTaskCount: 0,
  completedTaskCount: 0,
  failedTaskCount: 0,
  cancelledTaskCount: 0,
  totalTokens: 0
})

const formatNum = (n) => Number(n || 0).toLocaleString('zh-CN')

// === Token 用量 ===
const tokenLoading = ref(false)
const tokenUsers = ref([])
const tokenTotal = ref(0)
const tokenPage = ref(1)
const tokenPageSize = 10
const tokenKeyword = ref('')
const drillUser = ref(null)
const taskLoading = ref(false)
const tokenTasks = ref([])
const taskTotal = ref(0)
const taskPage = ref(1)
const taskPageSize = 10
const taskDeleted = ref('')

const fetchStats = async () => {
  try {
    const data = await getAdminStats()
    Object.assign(stats, data || {})
  } catch {
    // 错误提示由 axios 拦截器统一处理
  }
}

const fetchTokenUsers = async () => {
  tokenLoading.value = true
  try {
    const data = await listTokenUsers(tokenKeyword.value, tokenPage.value, tokenPageSize)
    tokenUsers.value = data.records || []
    tokenTotal.value = data.total || 0
  } catch {
    tokenUsers.value = []
    tokenTotal.value = 0
  } finally {
    tokenLoading.value = false
  }
}

const searchTokenUsers = () => {
  tokenPage.value = 1
  fetchTokenUsers()
}

const openTaskDrill = (row) => {
  drillUser.value = row
  taskDeleted.value = ''
  taskPage.value = 1
  fetchTasks()
}

const fetchTasks = async () => {
  if (!drillUser.value) return
  taskLoading.value = true
  try {
    const data = await listTokenTasks(drillUser.value.userId, taskDeleted.value, taskPage.value, taskPageSize)
    tokenTasks.value = data.records || []
    taskTotal.value = data.total || 0
  } catch {
    tokenTasks.value = []
    taskTotal.value = 0
  } finally {
    taskLoading.value = false
  }
}

const searchTasks = () => {
  taskPage.value = 1
  fetchTasks()
}

// === 生成任务管理 ===
const adminTaskLoading = ref(false)
const adminTasks = ref([])
const adminTaskTotal = ref(0)
const adminTaskPage = ref(1)
const adminTaskPageSize = 10
const adminTaskKeyword = ref('')
const adminTaskStatus = ref('')
const adminTaskUserId = ref('')

const TASK_STATUS = {
  PENDING: { text: '排队中', type: 'info' },
  RUNNING: { text: '运行中', type: 'warning' },
  COMPLETED: { text: '已完成', type: 'success' },
  FAILED: { text: '失败', type: 'danger' },
  CANCELLED: { text: '已终止', type: 'info' }
}

const taskStatusText = (status) => TASK_STATUS[status]?.text || status || '—'
const taskStatusType = (status) => TASK_STATUS[status]?.type || 'info'

const fetchAdminTasks = async () => {
  adminTaskLoading.value = true
  try {
    const params = { page: adminTaskPage.value, size: adminTaskPageSize }
    if (adminTaskKeyword.value) params.keyword = adminTaskKeyword.value
    if (adminTaskStatus.value) params.status = adminTaskStatus.value
    if (adminTaskUserId.value) params.userId = adminTaskUserId.value
    const data = await listAdminTasks(params)
    adminTasks.value = data.records || []
    adminTaskTotal.value = data.total || 0
  } catch {
    adminTasks.value = []
    adminTaskTotal.value = 0
  } finally {
    adminTaskLoading.value = false
  }
}

const searchAdminTasks = () => {
  adminTaskPage.value = 1
  fetchAdminTasks()
}

const handleCancelTask = async (row) => {
  try {
    await cancelAdminTask(row.id)
    ElMessage.success(`任务 #${row.id} 已终止`)
    fetchAdminTasks()
    fetchStats()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  }
}

const handleDeleteTask = async (row) => {
  try {
    await deleteAdminTask(row.id)
    ElMessage.success(`任务 #${row.id} 已删除`)
    if (adminTasks.value.length === 1 && adminTaskPage.value > 1) {
      adminTaskPage.value -= 1
    }
    fetchAdminTasks()
    fetchStats()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  }
}

// === 作品管理 ===
const projectLoading = ref(false)
const projects = ref([])
const projectTotal = ref(0)
const projectPage = ref(1)
const projectPageSize = 10
const projectKeyword = ref('')
const projectUserId = ref('')
const projectDeleted = ref('')
const projectCommunity = ref('')

const fetchProjects = async () => {
  projectLoading.value = true
  try {
    const params = { page: projectPage.value, size: projectPageSize }
    if (projectKeyword.value) params.keyword = projectKeyword.value
    if (projectUserId.value) params.userId = projectUserId.value
    if (projectDeleted.value !== '') params.deleted = projectDeleted.value
    if (projectCommunity.value) params.communityStatus = projectCommunity.value
    const data = await listAdminProjects(params)
    projects.value = data.records || []
    projectTotal.value = data.total || 0
  } catch {
    projects.value = []
    projectTotal.value = 0
  } finally {
    projectLoading.value = false
  }
}

const searchProjects = () => {
  projectPage.value = 1
  fetchProjects()
}

const handleDeleteProject = async (row) => {
  try {
    await adminDeleteProject(row.id)
    ElMessage.success('作品已删除，作者工作台会看到相应提示')
    fetchProjects()
    fetchStats()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  }
}

const handleRestore = async (row) => {
  try {
    await restoreAdminProject(row.id)
    ElMessage.success('作品已恢复')
    fetchProjects()
    fetchStats()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  }
}

// === 社区管理 ===
const communityLoading = ref(false)
const communityItems = ref([])
const communityTotal = ref(0)
const communityPage = ref(1)
const communityPageSize = 10
const communityKeyword = ref('')
const communityStatus = ref('')

const fetchCommunity = async () => {
  communityLoading.value = true
  try {
    const params = {
      page: communityPage.value,
      size: communityPageSize,
      deleted: 0
    }
    if (communityKeyword.value) params.keyword = communityKeyword.value
    if (communityStatus.value) params.communityStatus = communityStatus.value
    const data = await listAdminProjects(params)
    communityItems.value = data.records || []
    communityTotal.value = data.total || 0
  } catch {
    communityItems.value = []
    communityTotal.value = 0
  } finally {
    communityLoading.value = false
  }
}

const searchCommunity = () => {
  communityPage.value = 1
  fetchCommunity()
}

const handleCommunity = async (row, status) => {
  try {
    await setAdminCommunityStatus(row.id, status)
    ElMessage.success(status === 'published' ? '已上架到作品广场' : '已从作品广场下架')
    fetchProjects()
    fetchCommunity()
    fetchStats()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  }
}

// === 用户管理 ===
const userLoading = ref(false)
const users = ref([])
const userTotal = ref(0)
const userPage = ref(1)
const userPageSize = 10
const userKeyword = ref('')
const userStatus = ref('')

const fetchUsers = async () => {
  userLoading.value = true
  try {
    const data = await listAdminUsers(userKeyword.value, userStatus.value, userPage.value, userPageSize)
    users.value = data.records || []
    userTotal.value = data.total || 0
  } catch {
    users.value = []
    userTotal.value = 0
  } finally {
    userLoading.value = false
  }
}

const searchUsers = () => {
  userPage.value = 1
  fetchUsers()
}

const showBanDialog = ref(false)
const banReason = ref('')
const banSaving = ref(false)
const banningUser = ref(null)

const openBanDialog = (row) => {
  banningUser.value = row
  banReason.value = ''
  showBanDialog.value = true
}

const handleBan = async () => {
  if (!banReason.value.trim()) {
    ElMessage.warning('请填写封禁理由')
    return
  }
  banSaving.value = true
  try {
    await banUser(banningUser.value.id, banReason.value.trim())
    ElMessage.success('用户已封禁')
    showBanDialog.value = false
    fetchUsers()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    banSaving.value = false
  }
}

const handleUnban = async (row) => {
  try {
    await unbanUser(row.id)
    ElMessage.success('用户已解封')
    fetchUsers()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  }
}

const handleToggleRole = async (row) => {
  const nextRole = row.role === 'admin' ? 'user' : 'admin'
  const label = nextRole === 'admin' ? '设为管理员' : '取消管理员'
  try {
    await ElMessageBox.confirm(
      `确认将「${row.nickname || row.username}」${label}？`,
      '角色调整',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
    await setAdminUserRole(row.id, nextRole)
    ElMessage.success(`已${label}`)
    fetchUsers()
  } catch {
    // 取消或接口报错（提示由拦截器统一处理）
  }
}

const showPasswordDialog = ref(false)
const passwordUser = ref(null)
const newPassword = ref('')
const passwordSaving = ref(false)

const openPasswordDialog = (row) => {
  passwordUser.value = row
  newPassword.value = ''
  showPasswordDialog.value = true
}

const handleResetPassword = async () => {
  if (newPassword.value.trim().length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  passwordSaving.value = true
  try {
    await resetAdminUserPassword(passwordUser.value.id, newPassword.value.trim())
    ElMessage.success('密码已重置')
    showPasswordDialog.value = false
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    passwordSaving.value = false
  }
}

const thumbUrl = (url) => {
  if (!url) return ''
  return url.startsWith('http') ? url : GATEWAY_BASE + url
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
  if (localStorage.getItem('role') !== 'admin') {
    ElMessage.error('无管理员权限')
    router.replace('/workspace')
    return
  }
  fetchStats()
  fetchTokenUsers()
  fetchProjects()
  fetchAdminTasks()
  fetchCommunity()
  fetchUsers()
})
</script>

<style scoped>
.admin {
  min-height: 100vh;
  background: #f6f8fc;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  padding-bottom: 48px;
}
.ad-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  height: 64px;
  background: #fff;
  border-bottom: 1px solid #e8edf5;
  position: sticky;
  top: 0;
  z-index: 100;
}
.header-left { display: flex; align-items: center; gap: 28px; }
.brand { display: flex; align-items: center; gap: 10px; cursor: pointer; color: #1e293b; }
.brand svg { width: 28px; height: 28px; filter: drop-shadow(0 4px 10px rgba(37, 99, 235, 0.25)); }
.brand-name { font-size: 18px; font-weight: 700; letter-spacing: -0.5px; }
.admin-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
  font-weight: 600;
}
.header-nav { display: flex; align-items: center; gap: 6px; }
.nav-link {
  padding: 7px 14px;
  border-radius: 9px;
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
  text-decoration: none;
  transition: all 0.2s;
}
.nav-link:hover { color: #1e40af; background: #f1f5fb; }
.nav-link--active { color: #1e40af; background: #e8f0ff; font-weight: 600; }
.header-right { display: flex; align-items: center; }
.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 999px;
  padding: 6px 14px;
  cursor: pointer;
  font-size: 14px;
  color: #334155;
  transition: all 0.2s;
}
.user-chip:hover { border-color: #bfd3ff; background: #eef4ff; }
.user-chip-name { font-weight: 600; }
.logout-text { color: #ef4444; font-size: 12px; }

.ad-main { max-width: 1240px; margin: 0 auto; padding: 24px; }
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
.stat-card {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 14px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  box-shadow: 0 6px 18px rgba(30, 58, 138, 0.05);
}
.stat-label { font-size: 12.5px; color: #7b8aa3; }
.stat-value { font-size: 22px; font-weight: 700; color: #1e293b; letter-spacing: -0.5px; }
.stat-card--danger .stat-value { color: #ef4444; }
.stat-card--warn .stat-value { color: #d97706; }
.stat-card--ok .stat-value { color: #0f9d6e; }
.stat-card--token .stat-value { color: #1d4ed8; }

.ad-tabs :deep(.el-tabs__header) { margin-bottom: 14px; }
.ad-tabs :deep(.el-tabs__item) { font-weight: 600; color: #475569; }
.ad-tabs :deep(.el-tabs__item.is-active) { color: #1d4ed8; }

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.tool-input { width: 220px; }
.tool-input--sm { width: 140px; }
.filter-select { width: 140px; }
.ad-table {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e8edf5;
}

.user-cell { display: flex; flex-direction: column; gap: 2px; }
.user-cell-name { font-weight: 600; color: #1e293b; }
.user-cell-sub { font-size: 12px; color: #8b9ab1; }
.status-tag { margin-left: 4px; }

.token-main { color: #1e293b; font-weight: 600; }
.token-danger { color: #e2823a; font-weight: 600; }
.token-sub { font-size: 11.5px; color: #94a3b8; }
.token-total { color: #1d4ed8; font-weight: 700; }

.pagination-area { display: flex; justify-content: center; margin-top: 16px; }

.drill {
  margin-top: 22px;
  padding: 18px 20px 22px;
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(30, 58, 138, 0.06);
}
.drill-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.drill-head h3 { margin: 0 0 4px; font-size: 16px; color: #1e293b; }
.drill-head p { margin: 0; font-size: 12.5px; color: #8b9ab1; }
.drill-actions { display: flex; align-items: center; gap: 10px; }

.project-cell { display: flex; align-items: center; gap: 10px; }
.project-thumb {
  flex: 0 0 46px;
  width: 46px;
  height: 34px;
  border-radius: 7px;
  overflow: hidden;
  background: linear-gradient(135deg, #eef2ff, #e0f2fe);
  display: flex;
  align-items: center;
  justify-content: center;
}
.project-thumb img { width: 100%; height: 100%; object-fit: cover; }
.thumb-fallback { font-size: 15px; font-weight: 700; color: #64748b; }
.project-cell-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.project-cell-name { font-weight: 600; color: #1e293b; }

.ban-tip {
  margin: 0 0 14px;
  font-size: 13px;
  color: #b45309;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  padding: 9px 12px;
  border-radius: 9px;
  line-height: 1.5;
}
</style>
