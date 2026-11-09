﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="editor">
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="topbar-left">
        <button class="back-btn" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="18" height="18">
            <polyline points="15 18 9 12 15 6"></polyline>
          </svg>
          返回工作台
        </button>
        <div class="brand-divider"></div>
        <div class="project-info">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
            <polyline points="16 18 22 12 16 6"></polyline>
            <polyline points="8 6 2 12 8 18"></polyline>
          </svg>
          <span class="project-name">{{ projectName }}</span>
        </div>
      </div>
      <div class="topbar-right">
        <el-select
          v-model="currentPageId"
          placeholder="选择页面"
          @change="switchPage"
          class="page-select"
          size="default"
        >
          <el-option v-for="p in pages" :key="p.id" :label="p.title" :value="p.id" />
        </el-select>
        <el-button class="export-btn" @click="handleExport">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
            <polyline points="7 10 12 15 17 10"></polyline>
            <line x1="12" y1="15" x2="12" y2="3"></line>
          </svg>
          导出
        </el-button>
      </div>
    </header>

    <!-- 三栏布局 -->
    <div class="editor-body">
      <!-- 左侧面板 -->
      <aside class="panel left-panel">
        <el-tabs class="custom-tabs" v-model="activeLeftTab">
          <el-tab-pane label="AI 生成" name="generate">
            <div class="ai-panel">
              <el-select
                v-model="selectedTemplate"
                placeholder="选择提示词模板"
                class="template-select"
                @change="applyTemplate"
              >
                <el-option label="个人简历网站" value="resume" />
                <el-option label="博客网站" value="blog" />
                <el-option label="企业官网" value="company" />
                <el-option label="教师班级管理 / 成绩管理" value="school" />
                <el-option label="自定义内容（保留当前提示词）" value="custom" />
              </el-select>
              <div class="prompt-input-wrap">
                <el-input
                  v-model="aiPrompt"
                  type="textarea"
                  :rows="5"
                  placeholder="描述你的网站需求，例如：一个带文章列表和详情页的博客网站"
                  :disabled="generating"
                  class="prompt-textarea"
                />
                <div class="prompt-char-count">{{ aiPrompt.length }} 字</div>
              </div>
              <div class="custom-prompt-wrap">
                <div class="custom-prompt-label">自定义提示词（可选）</div>
                <el-input
                  v-model="customPrompt"
                  type="textarea"
                  :rows="3"
                  placeholder="例如：所有代码使用 TypeScript，风格参考 Apple 官网"
                  :disabled="generating"
                  class="custom-prompt-textarea"
                />
              </div>
              <el-button
                type="primary"
                :loading="generating"
                :disabled="!aiPrompt.trim()"
                class="generate-btn"
                @click="handleGenerate"
              >
                <svg v-if="!generating" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="18" height="18">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
                </svg>
                {{ generating ? '生成中...' : '生成网站' }}
              </el-button>

              <div v-if="progressLogs.length > 0" class="progress-log">
                <div class="progress-log-header">
                  <span class="log-header-title">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
                      <polyline points="4 17 10 11 4 5"></polyline>
                      <line x1="12" y1="19" x2="20" y2="19"></line>
                    </svg>
                    生成进度
                  </span>
                  <span v-if="generating" class="log-live"><span class="live-dot"></span>输出中</span>
                  <span v-else class="log-badge">共 {{ progressLogs.length }} 条</span>
                </div>
                <div v-if="generating" class="log-scanbar"><i></i></div>
                <div ref="progressLogEl" class="progress-log-body">
                  <div v-for="(log, idx) in progressLogItems" :key="idx" class="log-line" :class="'log-' + log.type">
                    <span v-if="log.ts" class="log-time">{{ formatLogTime(log.ts) }}</span>
                    <span class="log-tag">{{ log.tag }}</span>
                    <span class="log-text">{{ log.body }}</span>
                  </div>
                </div>
              </div>

              <div v-if="downloadUrl" class="download-section">
                <el-button class="download-btn" @click="handleDownload">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                    <polyline points="7 10 12 15 17 10"></polyline>
                    <line x1="12" y1="15" x2="12" y2="3"></line>
                  </svg>
                  下载生成的项目
                </el-button>
              </div>

              <!-- 追加需求迭代优化 -->
              <div v-if="showIterateInput && !generating" class="iterate-section">
                <div class="section-label">继续优化</div>
                <div class="iterate-input-wrap">
                  <el-input
                    v-model="iteratingPrompt"
                    type="textarea"
                    :rows="3"
                    placeholder="输入追加需求，如：给首页加一个轮播图"
                    class="iterate-textarea"
                  />
                </div>
                <el-button
                  type="primary"
                  :disabled="!iteratingPrompt.trim()"
                  class="iterate-btn"
                  @click="handleIterate"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
                    <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
                  </svg>
                  追加需求优化
                </el-button>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="需求分析" name="requirement">
            <div class="req-doc-panel">
              <div v-if="!currentTaskId" class="empty-state">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" width="40" height="40">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="16" y1="13" x2="8" y2="13"></line>
                  <line x1="16" y1="17" x2="8" y2="17"></line>
                </svg>
                <span>请先在「AI 生成」中生成网站</span>
              </div>
              <div v-else-if="reqLoading" class="empty-state">
                <div class="loading-spinner"></div>
                <span>加载中...</span>
              </div>
              <div v-else-if="!requirementDoc && !requirementRaw" class="empty-state">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" width="40" height="40">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="12" y1="18" x2="12" y2="12"></line>
                  <line x1="9" y1="15" x2="15" y2="15"></line>
                </svg>
                <span>需求文档生成中...</span>
                <span class="empty-hint">AI 正在分析你的需求，完成后自动显示</span>
              </div>
              <div v-else-if="!requirementDoc && requirementRaw" class="req-doc-body">
                <div class="req-header">
                  <h2>需求分析文档</h2>
                  <el-tag type="warning" size="small">JSON 解析失败，显示原始内容</el-tag>
                </div>
                <pre class="req-raw">{{ requirementRaw }}</pre>
              </div>
              <div v-else class="req-doc-body">
                <div class="req-header">
                  <h4>{{ requirementDoc.projectName }}</h4>
                  <p class="req-summary">{{ requirementDoc.originalRequirement }}</p>
                </div>

                <div v-if="requirementDoc.stakeholders?.length" class="req-card">
                  <h5><span class="card-icon">👥</span> 干系人角色</h5>
                  <div v-for="(s, i) in requirementDoc.stakeholders" :key="i" class="req-item">
                    <strong>{{ s.role }}</strong>
                    <p class="req-detail">{{ s.description }}</p>
                    <p class="req-detail"><em>诉求：</em>{{ s.mainNeeds }}</p>
                  </div>
                </div>

                <div v-if="requirementDoc.functionalRequirements?.length" class="req-card">
                  <h5><span class="card-icon">⚡</span> 功能需求清单</h5>
                  <div v-for="(f, i) in requirementDoc.functionalRequirements" :key="i" class="req-item">
                    <div class="req-item-header">
                      <el-tag :type="f.priority === '高' ? 'danger' : f.priority === '中' ? 'warning' : 'info'" size="small">{{ f.priority }}</el-tag>
                      <strong>{{ f.id }} {{ f.name }}</strong>
                    </div>
                    <p class="req-detail">{{ f.description }}</p>
                  </div>
                </div>

                <div v-if="requirementDoc.businessRules?.length" class="req-card">
                  <h5><span class="card-icon">📋</span> 业务规则</h5>
                  <div v-for="(b, i) in requirementDoc.businessRules" :key="i" class="req-item">
                    <strong>{{ b.id }}</strong>
                    <p class="req-detail">{{ b.content }}</p>
                  </div>
                </div>

                <div v-if="requirementDoc.dataEntities?.length" class="req-card">
                  <h5><span class="card-icon">🗄️</span> 数据实体</h5>
                  <div v-for="(d, i) in requirementDoc.dataEntities" :key="i" class="req-item">
                    <strong>{{ d.entity }}</strong>
                    <div v-for="(f, j) in d.fields" :key="j" class="req-detail field-detail">
                      <code>{{ f.name }}</code> <span class="field-type">{{ f.type }}</span> — {{ f.description }}
                    </div>
                  </div>
                </div>

                <div v-if="requirementDoc.useCases?.length" class="req-card">
                  <h5><span class="card-icon">📝</span> 核心用例</h5>
                  <div v-for="(u, i) in requirementDoc.useCases" :key="i" class="req-item">
                    <strong>{{ u.name }}</strong>
                    <span class="req-detail">（参与者：{{ u.actor }}）</span>
                    <p class="req-detail"><em>前置：</em>{{ u.precondition }}</p>
                    <p class="req-detail"><em>后置：</em>{{ u.postcondition }}</p>
                    <p class="req-detail"><em>主流程：</em></p>
                    <ol class="req-flow-list">
                      <li v-for="(step, si) in u.mainFlow" :key="si">{{ step }}</li>
                    </ol>
                    <div v-if="u.extensionFlow?.length">
                      <p class="req-detail"><em>扩展流程：</em></p>
                      <div v-for="(ef, ei) in u.extensionFlow" :key="ei" class="req-detail">
                        {{ ef.condition }} → {{ ef.action }}
                      </div>
                    </div>
                  </div>
                </div>

                <div v-if="requirementDoc.uiOutline?.length" class="req-card">
                  <h5><span class="card-icon">🎨</span> 界面原型</h5>
                  <div v-for="(ui, i) in requirementDoc.uiOutline" :key="i" class="req-item">
                    <strong>{{ ui.page }}</strong> <span class="req-detail">（{{ ui.role }}）</span>
                    <div v-for="(el, j) in ui.elements" :key="j" class="req-detail">• {{ el }}</div>
                  </div>
                </div>

                <div v-if="requirementDoc.acceptanceCriteria?.length" class="req-card">
                  <h5><span class="card-icon">✅</span> 验收标准</h5>
                  <div v-for="(a, i) in requirementDoc.acceptanceCriteria" :key="i" class="req-item">
                    <strong>{{ a.id }}</strong>
                    <p class="req-detail">{{ a.description }}</p>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="组件库" name="components">
            <div class="component-panel">
              <el-input
                v-model="componentKeyword"
                placeholder="搜索组件名称或说明"
                clearable
                class="component-search"
              />
              <div class="component-cats">
                <button
                  v-for="cat in componentCategories"
                  :key="cat"
                  class="cat-chip"
                  :class="{ active: componentCategory === cat }"
                  @click="componentCategory = cat"
                >{{ cat }}</button>
              </div>
              <div class="component-hint" :class="{ ready: !!selectedElement }">
                <template v-if="selectedElement">已选中 &lt;{{ selectedElement.tag }}&gt;：美学配方只改它的外观；带 MCP 标记的素材来自 shadcn 真实组件，可按需求生成或替换区块</template>
                <template v-else>先在预览中点击目标元素，再把鼠标移到素材上预览</template>
              </div>
              <div v-if="componentLoading" class="placeholder-text">
                <div class="loading-spinner"></div>
                <span>加载素材中...</span>
              </div>
              <div v-else class="component-list">
                <el-popover
                  v-for="c in filteredComponents"
                  :key="c.id"
                  placement="right-start"
                  :width="304"
                  trigger="hover"
                  :show-after="160"
                  :hide-after="80"
                  popper-class="material-pop"
                  @show="loadComponentDetail(c)"
                >
                  <template #reference>
                    <div class="component-card">
                      <div class="component-card-head">
                        <span class="component-name">
                          {{ c.name }}
                          <i v-if="c.source === 'shadcn'" class="mcp-badge">MCP</i>
                        </span>
                        <el-tag size="small" :type="c.kind === 'style' ? 'success' : 'info'" effect="plain">
                          {{ c.kind === 'style' ? (c.scope === 'target' ? '配方' : '动效') : slotLabel(c.slot) }}
                        </el-tag>
                      </div>
                      <p class="component-desc">{{ c.description }}</p>
                    </div>
                  </template>
                  <div class="material-pop-body">
                    <div class="material-stage" :class="{ scaled: c.kind !== 'style' }">
                      <div v-if="isMaterialLoading(c)" class="material-loading">
                        <div class="loading-spinner"></div>
                        <span>正在加载 MCP 组件…</span>
                      </div>
                      <iframe v-else-if="materialOf(c).html" :srcdoc="previewDoc(materialOf(c))" sandbox="allow-scripts" scrolling="no" tabindex="-1"></iframe>
                      <div v-else class="material-loading"><span>暂无预览，重新悬停可重试</span></div>
                    </div>
                    <div class="material-pop-title">
                      <span>{{ c.name }}</span>
                      <em>{{ c.source === 'shadcn' ? 'MCP · shadcn 真实组件' : (c.kind === 'style' ? '只改选中元素外观' : '按需求生成新区块') }}</em>
                    </div>
                    <el-input
                      v-model="materialInstruction[c.id]"
                      type="textarea"
                      :rows="2"
                      size="small"
                      :placeholder="c.kind === 'style' ? '可选：例如更轻一点、适配深色背景' : '描述你想要的区块；替换时作为补充要求，留空则按素材版式自动融合原内容'"
                    />
                    <div class="material-pop-actions">
                      <el-button
                        size="small"
                        type="primary"
                        :disabled="patchLoading || (c.kind === 'style' && !selectedElement)"
                        @click="applyMaterial(c)"
                      >
                        {{ materialLabel(c) }}
                      </el-button>
                      <el-button v-if="c.kind !== 'style'" size="small" :disabled="!selectedElement || patchLoading" @click="applyBlock(c, 'blockReplace')">替换所在区块</el-button>
                    </div>
                    <div v-if="!selectedElement && c.kind === 'style'" class="material-tip">先在页面中选中一个元素</div>
                    <div v-else-if="!selectedElement" class="material-tip">无需选中元素，按需求生成后自动插入当前页面</div>
                    <div v-else-if="c.kind !== 'style'" class="material-tip">替换会由 AI 融合素材版式与原区块内容，输入框可写补充要求，留空也可</div>
                  </div>
                </el-popover>
                <div v-if="filteredComponents.length === 0" class="placeholder-text">
                  <span>没有匹配的素材</span>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="历史" name="history">
            <div class="history-panel">
              <div class="history-toolbar">
                <el-select
                  v-model="activeHistoryPage"
                  size="small"
                  class="history-page-select"
                  placeholder="选择页面"
                  @change="loadHistory"
                >
                  <el-option v-for="p in historyPages" :key="p" :label="p" :value="p" />
                </el-select>
                <el-select v-model="activeBranch" size="small" class="history-branch-select" @change="loadHistory">
                  <el-option v-for="b in historyBranches" :key="b" :label="b" :value="b" />
                </el-select>
                <el-button size="small" :loading="historyLoading" @click="loadHistory">刷新</el-button>
              </div>
              <p class="history-tip">版本按页面隔离展示，每次页面变动都会自动生成快照，可随时回溯或创建分支。</p>
              <div v-if="historyLoading" class="placeholder-text">
                <div class="loading-spinner"></div>
                <span>加载历史中...</span>
              </div>
              <div v-else-if="filteredHistory.length === 0" class="placeholder-text">
                <span>当前分支暂无快照</span>
              </div>
              <div v-else class="history-list">
                <div
                  v-for="s in filteredHistory"
                  :key="s.id"
                  class="history-item"
                  @click="openSnapshot(s)"
                >
                  <div class="history-item-head">
                    <span class="history-msg">{{ s.message || '版本 #' + s.id }}</span>
                    <span class="history-time">{{ s.createdAt }}</span>
                  </div>
                  <div class="history-meta">
                    <code class="history-page">{{ s.page }}</code>
                    <span class="history-branch">{{ s.branch }}</span>
                    <span class="history-id">#{{ s.id }}</span>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="本地运行" name="run">
            <div class="run-panel">
              <div class="run-status-card" :class="{ active: runStatus.running }">
                <div class="status-indicator">
                  <span class="status-dot" :class="{ active: runStatus.running }"></span>
                  <span>{{ runStatus.running ? '运行中' : '未运行' }}</span>
                </div>
                <div v-if="runStatus.running" class="run-url">
                  <a :href="'http://localhost:' + runStatus.port" target="_blank">
                    http://localhost:{{ runStatus.port }}
                  </a>
                </div>
                <div v-if="runStatus.running && portalEntries.length > 0" class="run-portals">
                  <span class="run-portals-label">端入口</span>
                  <a
                    v-for="portal in portalEntries"
                    :key="portal.key"
                    :href="portal.url"
                    target="_blank"
                    class="run-portal-link"
                  >{{ portal.name }}</a>
                </div>
              </div>

              <div v-if="!currentTaskId" class="empty-state small">
                <span>请先在「AI 生成」中生成网站</span>
              </div>

              <div class="run-actions">
                <el-button
                  type="primary"
                  :loading="runStarting"
                  :disabled="!currentTaskId || runStatus.running"
                  class="run-btn"
                  @click="handleStartRun"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
                    <polygon points="5 3 19 12 5 21 5 3"></polygon>
                  </svg>
                  {{ runStarting ? '启动中...' : '启动运行' }}
                </el-button>
                <el-button
                  type="danger"
                  :disabled="!runStatus.running"
                  class="run-btn"
                  @click="handleStopRun"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
                    <rect x="6" y="4" width="4" height="16"></rect>
                    <rect x="14" y="4" width="4" height="16"></rect>
                  </svg>
                  停止
                </el-button>
                <el-button
                  v-if="runStatus.running"
                  class="run-btn open-btn"
                  @click="openRunSite"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
                    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path>
                    <polyline points="15 3 21 3 21 9"></polyline>
                    <line x1="10" y1="14" x2="21" y2="3"></line>
                  </svg>
                  打开网站
                </el-button>
              </div>

              <p class="run-tip">首次启动需安装依赖，可能需要几分钟</p>

              <div v-if="runLogs.length > 0" class="progress-log">
                <div class="progress-log-header">
                  <span class="log-header-title">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
                      <polyline points="4 17 10 11 4 5"></polyline>
                      <line x1="12" y1="19" x2="20" y2="19"></line>
                    </svg>
                    运行日志
                  </span>
                  <span v-if="runStatus.running" class="log-live"><span class="live-dot"></span>运行中</span>
                  <span v-else class="log-badge">已结束</span>
                </div>
                <div ref="runLogEl" class="progress-log-body">
                  <div v-for="(log, idx) in runLogItems" :key="idx" class="log-line" :class="'log-' + log.type">
                    <span class="log-tag">{{ log.tag }}</span>
                    <span class="log-text">{{ log.body }}</span>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </aside>

      <!-- 中央画布 -->
      <main class="canvas">
        <div v-if="previewUrl && portalEntries.length > 0" class="portal-tabs">
          <button
            class="portal-tab"
            :class="{ active: !activePortalKey }"
            @click="selectPortal('')"
          >站点首页</button>
          <button
            v-for="portal in portalEntries"
            :key="portal.key"
            class="portal-tab"
            :class="{ active: activePortalKey === portal.key }"
            @click="selectPortal(portal.key)"
          >{{ portal.name }}</button>
        </div>
        <div v-if="!previewUrl" class="empty-canvas">
          <div class="empty-canvas-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" width="64" height="64">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
              <line x1="8" y1="21" x2="16" y2="21"></line>
              <line x1="12" y1="17" x2="12" y2="21"></line>
            </svg>
          </div>
          <h3>开始创建你的网站</h3>
          <p>在左侧面板输入需求描述，点击「生成网站」按钮，AI 将为你自动生成完整的网站</p>
        </div>
        <iframe
          v-else
          :src="frameUrl"
          class="preview-frame"
          sandbox="allow-scripts allow-same-origin"
          title="网站预览"
        />
      </main>

      <!-- 右侧面板 -->
      <aside class="panel right-panel">
        <div class="prop-panel">
          <div class="prop-header">
            <span>属性面板</span>
            <button v-if="selectedElement" class="prop-clear" @click="clearSelection">取消选中</button>
          </div>
          <div v-if="!selectedElement" class="placeholder-text">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" width="36" height="36">
              <circle cx="11" cy="11" r="8"></circle>
              <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            <span>点击预览中的任意元素</span>
            <span class="sub-text">选中后可让 AI 修改，或用组件库片段替换</span>
          </div>
          <div v-else class="prop-body">
            <div class="prop-row">
              <span class="prop-label">标签</span>
              <code class="prop-value">&lt;{{ selectedElement.tag }}&gt;</code>
            </div>
            <div class="prop-row">
              <span class="prop-label">页面</span>
              <span class="prop-value">{{ selectedElement.page || '/' }}</span>
            </div>
            <div v-if="selectedElement.text" class="prop-row">
              <span class="prop-label">文案</span>
              <span class="prop-value">{{ selectedElement.text }}</span>
            </div>
            <div class="prop-selector" :title="selectedElement.selector">{{ selectedElement.selector }}</div>

            <div class="section-label">AI 修改此元素</div>
            <el-input
              v-model="editInstruction"
              type="textarea"
              :rows="3"
              placeholder="例如：把标题改成渐变流光文字，字号更大"
              class="patch-textarea"
            />
            <el-button
              type="primary"
              class="patch-btn"
              :loading="patchLoading"
              :disabled="!editInstruction.trim()"
              @click="applyAiEdit"
            >应用修改</el-button>
            <p class="prop-tip">也可以到左侧「组件库」应用样式或区块；选中元素后可用浮层上的「移动」拖动位置、「撤回」还原。</p>
          </div>
        </div>
      </aside>
    </div>

    <!-- 历史版本预览 -->
    <el-dialog v-model="snapshotDialog" title="版本内容" width="720px" class="snapshot-dialog">
      <div v-if="snapshotDetail" class="snapshot-detail">
        <div class="snapshot-detail-meta">
          <span>#{{ snapshotDetail.id }}</span>
          <span>{{ snapshotDetail.branch }}</span>
          <span>{{ snapshotDetail.page }}</span>
          <span>{{ snapshotDetail.createdAt }}</span>
        </div>
        <p class="snapshot-detail-msg">{{ snapshotDetail.message }}</p>
        <pre class="snapshot-code">{{ snapshotDetail.content }}</pre>
      </div>
      <template #footer>
        <el-button @click="snapshotDialog = false">关闭</el-button>
        <el-button @click="branchFromSnapshot">从此版本建分支</el-button>
        <el-button type="primary" :loading="snapshotActionLoading" @click="restoreSnapshotVersion">恢复此版本</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPages, submitGenerate, iterateGenerate, getLatestByPage, startRun, stopRun, getRunStatus, listPortals, getRequirement, getTaskStatus, updateProjectScreenshot, getComponents, getComponent, patchElement, prepareElementEdit, getHistory, getSnapshot, restoreSnapshot, createSnapshotBranch } from '../api'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => route.params.projectId)
const projectName = ref('编辑器')
const currentPageId = ref(route.params.pageId)
const pages = ref([])
const activeLeftTab = ref('generate')

// AI 生成
const aiPrompt = ref('')
const customPrompt = ref('')
const selectedTemplate = ref('')
const generating = ref(false)
const progressLogs = ref([])
const downloadUrl = ref('')
const previewUrl = ref('')
let eventSource = null

// 追加需求（迭代优化）
const iteratingPrompt = ref('')
const showIterateInput = ref(false)

// 本地运行
const currentTaskId = ref('')
const runStatus = ref({ running: false, port: null })
const runStarting = ref(false)
const runLogs = ref([])

// 角色端入口（教师端/学生端/管理员端等）
const portalEntries = ref([])
const activePortalKey = ref('')
// 只在进入页面后第一次拿到端列表时按 ?portal= 恢复，之后以用户点击为准
let portalQueryRestored = false

// 需求分析文档
const requirementDoc = ref(null)
const requirementRaw = ref('')
const reqLoading = ref(false)

// 组件库
const components = ref([])
const componentLoading = ref(false)
// MCP 素材详情按需拉取：列表只带元信息，悬停卡片时才请求真实组件并缓存
const componentDetails = ref({})
const componentDetailLoading = ref({})
const componentKeyword = ref('')
const componentCategory = ref('全部')
// 素材弹层里用户填写的需求，按素材 id 缓存
const materialInstruction = ref({})

// 页面版本历史
const activeBranch = ref('main')
const historyBranches = ref(['main'])
// 历史按页面隔离：不同页面的初始版本不再混在一个列表里
const activeHistoryPage = ref('')
const historyPages = ref([])
const historyList = ref([])
const historyLoading = ref(false)
const snapshotDialog = ref(false)
const snapshotDetail = ref(null)
const snapshotActionLoading = ref(false)

// 元素级编辑
const selectedElement = ref(null)
const editInstruction = ref('')
const patchLoading = ref(false)
const frameOverride = ref('')

const GATEWAY_BASE = 'http://localhost:8080'
const toAbsolute = (url) => (url && !url.startsWith('http') ? GATEWAY_BASE + url : url)

// ===== 进度日志：拆分、分类、时间戳与自动滚动 =====
const progressLogEl = ref(null)
const runLogEl = ref(null)

const LOG_ERROR_RE = /失败|错误|异常|超时|中断|无法|不存在/
const LOG_SUCCESS_RE = /完成|成功|通过|已保存|已同步/

// 解析后端「[标签] 正文」格式的进度消息
const parseLogLine = (text) => {
  const raw = String(text)
  const m = raw.match(/^\s*\[([^\]]{1,14})\]\s*([\s\S]*)$/)
  const tag = m ? m[1] : '日志'
  const body = m ? m[2] : raw.trim()
  let type = 'stage'
  if (LOG_ERROR_RE.test(raw)) type = 'error'
  else if (/警告/.test(raw)) type = 'warn'
  else if (LOG_SUCCESS_RE.test(raw)) type = 'success'
  else if (/^(提交|迭代|恢复)/.test(tag)) type = 'action'
  return { tag, body, type }
}

// 推送生成进度：SSE 批量消息可能包含多行，逐行拆分并记录到达时间
const pushProgressLog = (text) => {
  const ts = Date.now()
  for (const line of String(text).split(/\r?\n/)) {
    if (line.trim()) progressLogs.value.push({ text: line.trim(), ts })
  }
}

const decorateLogs = (list) => list.map((it) => ({ ...it, ...parseLogLine(it.text) }))
const progressLogItems = computed(() => decorateLogs(progressLogs.value))
const runLogItems = computed(() =>
  decorateLogs(runLogs.value.map((t) => ({ text: String(t), ts: 0 })))
)

const formatLogTime = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const scrollLogToBottom = async (elRef) => {
  await nextTick()
  if (elRef.value) elRef.value.scrollTop = elRef.value.scrollHeight
}
watch(() => progressLogs.value.length, () => scrollLogToBottom(progressLogEl))
watch(() => runLogs.value.length, () => scrollLogToBottom(runLogEl))

// 每次进入编辑器给预览地址带一个时间戳，避免浏览器缓存展示恢复前的旧页面
const previewNonce = Date.now()
const withNonce = (url) => (url ? url + (url.includes('?') ? '&' : '?') + '_hb=' + previewNonce : url)

// 画布预览地址：选中某个端时切换到该端入口页
const baseFrameUrl = computed(() => {
  if (!previewUrl.value) return ''
  if (!activePortalKey.value) return withNonce(previewUrl.value)
  const entry = portalEntries.value.find(p => p.key === activePortalKey.value)
  return withNonce(entry ? toAbsolute(entry.url) : previewUrl.value)
})
// patch 后保留当前页面并强制刷新，避免回到站点根路径
const frameUrl = computed(() => frameOverride.value || baseFrameUrl.value)

watch([activePortalKey, previewUrl], () => {
  frameOverride.value = ''
})

/**
 * 多端切换：每个端都有自己的 URL（/<key>/），切换时把 ?portal=<key> 同步到地址栏，
 * 便于直接复制链接或在新标签页打开，而不是把多个端塞进同一个页面用按钮承载。
 */
const syncPortalQuery = (key) => {
  const query = { ...route.query }
  if (key) {
    query.portal = key
  } else {
    delete query.portal
  }
  router.replace({ path: route.path, query }).catch(() => {})
}

const selectPortal = (key) => {
  activePortalKey.value = key || ''
  syncPortalQuery(activePortalKey.value)
}

const applyPortalList = (list) => {
  portalEntries.value = Array.isArray(list) ? list : []
  if (activePortalKey.value && !portalEntries.value.some(p => p.key === activePortalKey.value)) {
    activePortalKey.value = ''
  }
  // 首次拿到端列表时，按地址栏里的 ?portal= 恢复对应端，链接可分享
  if (!portalQueryRestored && portalEntries.value.length > 0) {
    const wanted = typeof route.query.portal === 'string' ? route.query.portal : ''
    if (wanted && portalEntries.value.some(p => p.key === wanted)) {
      activePortalKey.value = wanted
    }
    portalQueryRestored = true
  }
}

const refreshPortals = async () => {
  if (!currentTaskId.value) {
    applyPortalList([])
    return
  }
  try {
    applyPortalList(await listPortals(currentTaskId.value))
  } catch {
    applyPortalList([])
  }
}

// ===== 组件库 =====
const componentCategories = computed(() => {
  const cats = [...new Set(components.value.map((c) => c.category).filter(Boolean))]
  return ['全部', ...cats]
})

const filteredComponents = computed(() => {
  const kw = componentKeyword.value.trim().toLowerCase()
  return components.value.filter((c) => {
    const catOk = componentCategory.value === '全部' || c.category === componentCategory.value
    const kwOk = !kw
      || (c.name || '').toLowerCase().includes(kw)
      || (c.description || '').toLowerCase().includes(kw)
    return catOk && kwOk
  })
})

const loadComponents = async () => {
  if (componentLoading.value) return
  componentLoading.value = true
  try {
    components.value = await getComponents()
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    componentLoading.value = false
  }
}

// 素材详情：内置配方直接用列表数据，MCP 素材需要先按需拉取真实组件
const materialOf = (c) => componentDetails.value[c.id] || c
const isMaterialLoading = (c) => c.source === 'shadcn' && !materialOf(c).html
  && !!componentDetailLoading.value[c.id]

const loadComponentDetail = async (c) => {
  if (!c || c.source !== 'shadcn') return
  if (componentDetails.value[c.id] || componentDetailLoading.value[c.id]) return
  componentDetailLoading.value = { ...componentDetailLoading.value, [c.id]: true }
  try {
    const detail = await getComponent(c.id)
    componentDetails.value = { ...componentDetails.value, [c.id]: detail }
  } catch {
    // 失败提示由拦截器处理，下次悬停可重试
  } finally {
    componentDetailLoading.value = { ...componentDetailLoading.value, [c.id]: false }
  }
}

watch(activeLeftTab, (tab) => {
  if (tab === 'components' && components.value.length === 0) loadComponents()
  if (tab === 'history') loadHistory()
})

// ===== 版本历史 =====
const filteredHistory = computed(() =>
  historyList.value.filter(
    (s) =>
      (s.branch || 'main') === activeBranch.value &&
      (!activeHistoryPage.value || s.page === activeHistoryPage.value)
  )
)

// 历史默认展示当前编辑中的页面：优先选中元素所在页，其次当前端入口页，最后站点首页
const pickHistoryPage = () => {
  const pages = historyPages.value
  if (!pages.length) return ''
  const candidates = []
  if (selectedElement.value?.page) {
    const p = normalizePage(selectedElement.value.page)
    candidates.push(p)
    if (p === '/') candidates.push('/index.html')
    if (p.endsWith('/')) candidates.push(p + 'index.html')
  }
  if (activePortalKey.value) candidates.push(`/${activePortalKey.value}/index.html`)
  candidates.push('/index.html')
  return candidates.find((c) => pages.includes(c)) || pages[0]
}

const loadHistory = async () => {
  const taskId = Number(currentTaskId.value)
  if (!taskId) {
    historyList.value = []
    historyPages.value = []
    return
  }
  historyLoading.value = true
  try {
    // 首次进入先拿页面列表，锁定到当前页后再拉该页的版本
    if (!historyPages.value.length) {
      const meta = await getHistory(taskId)
      historyPages.value = meta?.pages?.length ? meta.pages : []
    }
    if (!activeHistoryPage.value || !historyPages.value.includes(activeHistoryPage.value)) {
      activeHistoryPage.value = pickHistoryPage()
    }
    const res = await getHistory(taskId, activeHistoryPage.value || undefined)
    historyList.value = res?.snapshots || []
    if (res?.pages?.length) historyPages.value = res.pages
    historyBranches.value = res?.branches?.length ? res.branches : ['main']
    if (!historyBranches.value.includes(activeBranch.value)) {
      activeBranch.value = 'main'
    }
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    historyLoading.value = false
  }
}

const refreshHistoryIfOpen = () => {
  if (activeLeftTab.value === 'history') loadHistory()
}

const openSnapshot = async (snapshot) => {
  snapshotDetail.value = snapshot
  snapshotDialog.value = true
  try {
    snapshotDetail.value = await getSnapshot(snapshot.id)
  } catch {
    // 错误提示已由请求拦截器统一处理
  }
}

const restoreSnapshotVersion = async () => {
  if (!snapshotDetail.value || !currentTaskId.value) return
  try {
    await ElMessageBox.confirm(
      `将页面 ${snapshotDetail.value.page} 恢复为版本 #${snapshotDetail.value.id}？恢复后会生成一条新版本记录。`,
      '恢复版本',
      { type: 'warning', confirmButtonText: '恢复', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  snapshotActionLoading.value = true
  try {
    await restoreSnapshot(Number(currentTaskId.value), snapshotDetail.value.id)
    ElMessage.success('已恢复该版本')
    snapshotDialog.value = false
    await loadHistory()
    reloadPreviewAfterPatch(snapshotDetail.value.page)
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    snapshotActionLoading.value = false
  }
}

const branchFromSnapshot = async () => {
  if (!snapshotDetail.value || !currentTaskId.value) return
  let name = ''
  try {
    const res = await ElMessageBox.prompt('输入新分支名称，例如 experiment', '从此版本创建分支', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '分支名称不能为空'
    })
    name = res.value
  } catch {
    return
  }
  snapshotActionLoading.value = true
  try {
    await createSnapshotBranch(Number(currentTaskId.value), snapshotDetail.value.id, name)
    ElMessage.success('分支已创建')
    snapshotDialog.value = false
    await loadHistory()
    activeBranch.value = name.trim().replace(/\s+/g, '-')
    loadHistory()
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    snapshotActionLoading.value = false
  }
}

// ===== 元素级编辑（iframe 内 bridge 通过 postMessage 回传选中元素）=====
// 把 iframe 内回传的地址归一化为站点内路径（去掉网关预览前缀 / 域名 / 查询参数）
const normalizePage = (page) => {
  let p = page || '/'
  try {
    p = new URL(p, window.location.origin).pathname
  } catch {
    // 非标准路径时保持原样
  }
  p = p.replace(/^\/?(?:api\/v1\/)?generator\/preview\/\d+/, '')
  if (!p.startsWith('/')) p = '/' + p
  return p
}

const handleFrameMessage = (event) => {
  const frame = document.querySelector('.preview-frame')
  if (!frame || event.source !== frame.contentWindow) return
  const data = event.data
  if (!data || typeof data !== 'object') return
  if (data.type === 'hb:select') {
    selectedElement.value = {
      selector: data.selector,
      tag: data.tag,
      text: data.text,
      outerHTML: data.outerHTML,
      page: normalizePage(data.page)
    }
    editInstruction.value = ''
    return
  }
  // 拖动移动：bridge 内已改好内联样式，这里只负责持久化（不刷新 iframe）
  if (data.type === 'hb:position') {
    persistPosition(data, false)
    return
  }
  if (data.type === 'hb:position-revert') {
    persistPosition(data, true)
  }
}

const persistPosition = async (data, revert) => {
  if (!currentTaskId.value || !data.selector) return
  try {
    await patchElement({
      taskId: Number(currentTaskId.value),
      page: normalizePage(data.page),
      selector: data.selector,
      tag: data.tag,
      mode: 'position',
      style: data.style || '',
      branch: activeBranch.value || 'main'
    })
    ElMessage.success(revert ? '已撤回移动' : '位置已保存')
    refreshHistoryIfOpen()
  } catch {
    // 错误提示已由请求拦截器统一处理
  }
}

const clearSelection = () => {
  selectedElement.value = null
  editInstruction.value = ''
  const frame = document.querySelector('.preview-frame')
  if (frame && frame.contentWindow) {
    frame.contentWindow.postMessage({ type: 'hb:clear' }, '*')
  }
}

// patch 后按当前页面路径重新加载 iframe（URL 加时间戳绕过缓存）
const reloadPreviewAfterPatch = (page) => {
  const base = baseFrameUrl.value
  if (!base) return
  let target = base
  const p = normalizePage(page)
  if (p !== '/' && !activePortalKey.value) {
    target = base.endsWith('/') ? base.slice(0, -1) + p : base + p
  }
  frameOverride.value = target + (target.includes('?') ? '&' : '?') + '_hb=' + Date.now()
}

const doPatch = async (payload) => {
  if (!currentTaskId.value) {
    ElMessage.warning('请先生成网站')
    return
  }
  if (!selectedElement.value) {
    ElMessage.warning('请先在预览中点击目标元素')
    return
  }
  if (!selectedElement.value.selector) {
    ElMessage.warning('未能识别选中元素，请刷新预览后重试')
    return
  }
  patchLoading.value = true
  const page = normalizePage(selectedElement.value.page)
  try {
    const res = await patchElement({
      taskId: Number(currentTaskId.value),
      page,
      selector: selectedElement.value.selector,
      tag: selectedElement.value.tag,
      outerHTML: selectedElement.value.outerHTML,
      branch: activeBranch.value || 'main',
      ...payload
    })
    ElMessage.success(res.summary || '已应用修改')
    selectedElement.value = null
    editInstruction.value = ''
    reloadPreviewAfterPatch(page)
    refreshHistoryIfOpen()
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    patchLoading.value = false
  }
}

const applyAiEdit = () => {
  if (!editInstruction.value.trim()) {
    ElMessage.warning('请填写修改要求')
    return
  }
  return doPatch({ mode: 'ai', instruction: editInstruction.value })
}

// 素材悬停预览：在隔离的 iframe 里渲染素材自身（仅允许脚本以运行动效，无同源权限）
const previewDoc = (c) => {
  const base = '*{box-sizing:border-box}html,body{margin:0}body{font:14px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif;color:#0f172a;background:#f8fafc}'
  // 动效类素材依赖脚本运行，预览里要一并执行（iframe 已放开 allow-scripts）
  const script = c.js && String(c.js).trim() ? `<script>${String(c.js)}<\/script>` : ''
  if (c.kind === 'style') {
    // 美学配方里的 & 代表目标元素，预览时绑定到示例节点
    const css = String(c.css || '').replace(/&/g, '.hb-prev')
    return `<!doctype html><html><head><meta charset="utf-8"><style>${base}body{height:100%;display:flex;align-items:center;justify-content:center;padding:18px;background:linear-gradient(180deg,#f8fafc,#eef2f7)}${css}</style></head><body>${c.html || ''}${script}</body></html>`
  }
  return `<!doctype html><html><head><meta charset="utf-8"><style>${base}${c.css || ''}</style></head><body>${c.html || ''}${script}</body></html>`
}

const materialLabel = (c) => {
  if (c.kind !== 'style') return '按需求生成'
  return (materialInstruction.value[c.id] || '').trim() ? '按需求优化' : '套用配方'
}

// 当前编辑页：优先选中元素所在页，其次当前端入口页，多端站点则默认第一个端首页，最后站点首页
const currentEditPage = computed(() => {
  if (selectedElement.value?.page) return normalizePage(selectedElement.value.page)
  const key = activePortalKey.value || portalEntries.value[0]?.key
  return key ? `/${key}/index.html` : '/index.html'
})

// 未选中元素时按需求生成区块：位置由素材用途（顶部/内容段/页脚）决定，直接插入当前页面
const generateBlock = async (component, instruction) => {
  if (!currentTaskId.value) {
    ElMessage.warning('请先生成网站')
    return
  }
  const page = currentEditPage.value
  patchLoading.value = true
  try {
    const res = await patchElement({
      taskId: Number(currentTaskId.value),
      page,
      mode: 'blockGenerate',
      componentId: component.id,
      instruction,
      branch: activeBranch.value || 'main'
    })
    // 多端站点未指定端时，插入的是第一个端首页，预览同步切到该端
    if (!selectedElement.value && !activePortalKey.value && portalEntries.value.length) {
      selectPortal(portalEntries.value[0].key)
    }
    ElMessage.success(res.summary || '已添加区块')
    reloadPreviewAfterPatch(page)
    refreshHistoryIfOpen()
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    patchLoading.value = false
  }
}

// 统一入口：配方只改外观；区块素材按需求生成，选中了元素时插在其所在区块之后，没选中则直接加入页面
const applyMaterial = (c) => {
  const instruction = (materialInstruction.value[c.id] || '').trim()
  if (c.kind !== 'style') {
    if (!instruction) {
      ElMessage.warning('先描述一下你想要的区块内容')
      return
    }
    if (!selectedElement.value) {
      return generateBlock(c, instruction)
    }
    return doPatch({ mode: 'blockGenerate', componentId: c.id, instruction })
  }
  if (instruction) {
    return doPatch({ mode: 'ai', instruction })
  }
  return doPatch({ mode: 'style', componentId: c.id })
}

// 区块用途文案：top 置顶、bottom 置底、flow 内容段
const slotLabel = (slot) => {
  if (slot === 'top') return '顶部'
  if (slot === 'bottom') return '页脚'
  if (slot === 'flow') return '内容段'
  return '区块'
}

const applyBlock = (component, mode) => {
  if (mode === 'blockReplace') {
    const instruction = (materialInstruction.value[component.id] || '').trim()
    return ElMessageBox.confirm(
      `AI 会把「${component.name}」的版式与当前选中的页面结构段内容融合重写，保留原有标题、文案、链接与功能。输入框中的描述会作为补充要求，留空则按素材版式自动融合。`,
      '替换所在区块',
      { type: 'warning', confirmButtonText: '替换', cancelButtonText: '取消' }
    )
      .then(() => doPatch({ mode, componentId: component.id, instruction }))
      .catch(() => {})
  }
  return doPatch({ mode, componentId: component.id })
}

const STORAGE_KEY = (projectId, pageId) => `editor_prompt_${projectId}_${pageId}`

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

  company: `生成一个企业官网，包含首页（公司介绍、核心业务、团队展示、客户案例）、产品列表页、产品详情页。数据库需包含 products 表（id, name, description, features, image_url, category）和 cases 表（id, client_name, project_desc, result, date）。后端提供产品和案例的 CRUD API。设计风格：商务专业，蓝色主色调，大图轮播。`,

  school: `生成一个教师班级管理与成绩管理系统，分为教师端、学生端、管理员端三个端，具体需求如下：

【页面规划】
教师端（teacher）
1. 登录（login.html）：账号 + 密码 + 图形验证码，登录成功进入本端首页
2. 首页（index.html）：工作台概览——所带班级数、学生总数、待录入成绩数、最近操作记录
3. 班级管理（class.html）：班级列表（班级名称、年级、人数、班主任），支持新增、编辑、删除班级
4. 学生管理（student.html）：学生列表（学号、姓名、性别、所属班级），支持按班级筛选与增删改
5. 成绩录入（score-entry.html）：先选班级与考试，再在表格中逐行录入学生成绩，支持批量保存
6. 成绩统计（score-stats.html）：按班级与考试展示平均分、最高分、最低分、及格率，并含分数段分布图

学生端（student）
1. 登录（login.html）：账号 + 密码 + 图形验证码
2. 首页（index.html）：个人成绩概览——本学期考试数、平均分、班级排名、最新通知
3. 成绩查询（score.html）：按学号与考试筛选查看本人成绩明细，并显示本人成绩与班级平均分的对比

管理员端（admin）
1. 登录（login.html）：账号 + 密码 + 图形验证码
2. 首页（index.html）：全局看板——班级总数、学生总数、教师总数、成绩记录数
3. 账号管理（accounts.html）：账号列表，支持新建账号、绑定角色（教师/学生/管理员）、重置密码、启用、禁用
4. 系统设置（settings.html）：考试类型、科目、及格线等基础配置

【设计风格】
- 教育类后台管理风格，浅色底：页面背景 #f5f7fa、卡片 #ffffff、主色 #2f6fed、成功 #17b26a、危险 #f04438
- 左侧固定侧边栏导航 + 顶部面包屑，当前菜单高亮；三个端共用同一套样式类，仅菜单项与数据范围不同
- 以表格为主要信息载体：表头浅灰、隔行变色、hover 行高亮，数值列右对齐并使用等宽字体
- 关键指标用 KPI 卡片组：大字号数值 + 小字说明 + 趋势标识
- 新增与编辑统一用模态框表单，必填项标红星，校验失败在字段下方红字提示
- 成绩数值用色块标签区分：≥90 绿、60–89 蓝、<60 红
- 响应式：窗口宽度小于 900px 时侧边栏收起为图标模式，表格改为横向滚动

【数据库设计】
- classes 表：id, name, grade, head_teacher, student_count, created_at
- students 表：id, student_no, name, gender, class_id, created_at
- teachers 表：id, name, account, subject, created_at
- accounts 表：id, account, password, role, status, created_at（role 取 teacher/student/admin，status 1 启用 0 禁用）
- exams 表：id, name, subject, exam_date, class_id, full_score
- scores 表：id, student_id, exam_id, score, created_at

【功能】
- 各端入口页未登录时跳转本端登录页；登录调用 POST /api/auth/login，token 存 localStorage 并在请求头 X-Token 中携带
- 登录页通过 GET /api/auth/captcha 获取图形验证码，验证码错误提示并可点击刷新
- 账号管理页调用 /api/accounts 完成新建账号、绑定角色、重置密码、启用禁用
- 数据隔离：教师端只展示本人所带班级的学生与成绩，学生端只能查看本人成绩，管理员端可见全部数据
- 教师端成绩录入页通过 Fetch API 批量提交成绩到后端保存，保存后留在本页可继续录入
- 班级、学生、成绩列表页通过 Fetch API 拉取数据，支持按班级与考试筛选
- 成绩统计页按班级与考试聚合计算平均分、最高分、最低分、及格率及分数段人数
- 学生端登录后按学号查询本人全部成绩，并计算与班级平均分的差值
- 管理员端统计接口汇总班级、学生、教师与成绩记录数量`
}

const applyTemplate = (key) => {
  if (key === 'custom') return
  if (key && promptTemplates[key]) {
    aiPrompt.value = promptTemplates[key]
  }
}

// 根据当前提示词内容回填模板选中项，避免「未选模板却已有文本」
const syncTemplateWithPrompt = () => {
  const text = aiPrompt.value.trim()
  if (!text) {
    selectedTemplate.value = ''
    return
  }
  const matched = Object.keys(promptTemplates).find((k) => promptTemplates[k].trim() === text)
  selectedTemplate.value = matched || 'custom'
}

const goBack = () => router.push('/workspace')

const switchPage = (pageId) => {
  router.push(`/editor/${projectId.value}/${pageId}`)
}

const handleExport = () => {
  ElMessage.info('导出功能待开发')
}

const handleGenerate = async () => {
  if (!aiPrompt.value.trim()) {
    ElMessage.warning('请输入网站需求描述')
    return
  }
  if (generating.value) {
    ElMessage.info('网站正在生成中，请勿重复提交')
    return
  }

  generating.value = true
  showIterateInput.value = false
  progressLogs.value = []
  downloadUrl.value = ''
  previewUrl.value = ''

  // 保存 prompt 到 localStorage
  localStorage.setItem(STORAGE_KEY(projectId.value, route.params.pageId), aiPrompt.value)

  try {
    const result = await submitGenerate(aiPrompt.value, projectId.value, route.params.pageId, customPrompt.value || undefined)
    const taskId = result.id
    currentTaskId.value = taskId
    // 保存当前 taskId 到 localStorage
    sessionStorage.setItem(`editor_taskId_${projectId.value}_${route.params.pageId}`, taskId)

    pushProgressLog('[提交] 任务已提交，开始生成...')

    connectSSE(taskId, (data) => {
      downloadUrl.value = toAbsolute(data.downloadUrl)
      previewUrl.value = toAbsolute(data.previewUrl)
      generating.value = false
      showIterateInput.value = true
      pushProgressLog('[完成] 网站生成完成！')
      loadRequirementDoc(currentTaskId.value)
      if (data.screenshotUrl) {
        updateProjectScreenshot(projectId.value, data.screenshotUrl).catch(() => {})
      }
      // 自动切换到运行 tab 并获取运行状态
      activeLeftTab.value = 'run'
      fetchRunStatus()
    })
  } catch (e) {
    generating.value = false
    pushProgressLog('[错误] ' + e.message)
  }
}

const handleIterate = async () => {
  if (!iteratingPrompt.value.trim()) {
    ElMessage.warning('请输入追加需求')
    return
  }
  if (!currentTaskId.value) {
    ElMessage.warning('请先生成网站')
    return
  }

  generating.value = true
  showIterateInput.value = false
  progressLogs.value = []
  previewUrl.value = ''

  try {
    const result = await iterateGenerate(currentTaskId.value, iteratingPrompt.value, customPrompt.value || undefined)
    const newTaskId = result.id
    currentTaskId.value = newTaskId
    sessionStorage.setItem(`editor_taskId_${projectId.value}_${route.params.pageId}`, newTaskId)
    iteratingPrompt.value = ''

    pushProgressLog('[迭代] 追加需求已提交，开始优化...')

    connectSSE(newTaskId, (data) => {
      downloadUrl.value = toAbsolute(data.downloadUrl)
      previewUrl.value = toAbsolute(data.previewUrl)
      generating.value = false
      showIterateInput.value = true
      pushProgressLog('[迭代-完成] 迭代优化完成！')
      loadRequirementDoc(currentTaskId.value)
      if (data.screenshotUrl) {
        updateProjectScreenshot(projectId.value, data.screenshotUrl).catch(() => {})
      }
      // 自动切换到运行 tab 并获取运行状态
      activeLeftTab.value = 'run'
      fetchRunStatus()
    })
  } catch (e) {
    generating.value = false
    showIterateInput.value = true
    pushProgressLog('[迭代-错误] ' + e.message)
  }
}

const connectSSE = (taskId, onComplete) => {
  if (eventSource) {
    eventSource.close()
  }
  eventSource = new EventSource(`http://localhost:8080/api/v1/generator/progress/${taskId}`)

  // 校验当前 taskId 是否匹配，防止切换项目后旧 SSE 事件污染进度日志
  const isCurrentTask = () => currentTaskId.value === taskId

  eventSource.addEventListener('progress', (event) => {
    if (!isCurrentTask()) return
    pushProgressLog(event.data)
    // 检测需求文档已保存，立即加载
    if (event.data.includes('文档已保存')) {
      loadRequirementDoc(taskId)
    }
  })

  eventSource.addEventListener('complete', (event) => {
    if (!isCurrentTask()) return
    const data = JSON.parse(event.data)
    onComplete(data)
    eventSource.close()
  })

  eventSource.addEventListener('error', (event) => {
    if (!isCurrentTask()) return
    pushProgressLog('[错误] ' + (event.data || '生成过程出错'))
    generating.value = false
    showIterateInput.value = true
    eventSource.close()
  })

  eventSource.onerror = () => {
    if (!generating.value) return
    if (!isCurrentTask()) return
    pushProgressLog('[错误] 连接中断，请刷新页面查看进度')
    generating.value = false
    showIterateInput.value = true
    eventSource.close()
  }
}

const handleDownload = () => {
  if (downloadUrl.value) {
    window.open(downloadUrl.value, '_blank')
  }
}

const loadPageData = async () => {
  // 关闭旧页面的 SSE 连接，防止跨页面进度串扰
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  // 重置所有页面相关状态
  generating.value = false
  showIterateInput.value = false
  progressLogs.value = []
  downloadUrl.value = ''
  previewUrl.value = ''
  requirementDoc.value = null
  requirementRaw.value = ''
  runStatus.value = { running: false, port: null }
  runLogs.value = []
  portalEntries.value = []
  activePortalKey.value = ''
  portalQueryRestored = false
  // 切换任务后历史归属的页面需要重新识别
  historyPages.value = []
  activeHistoryPage.value = ''
  historyList.value = []

  try {
    const data = await listPages(projectId.value)
    pages.value = data.records || []
  } catch {
    // 忽略
  }

  // 恢复 prompt 输入
  const savedPrompt = localStorage.getItem(STORAGE_KEY(projectId.value, route.params.pageId))
  if (savedPrompt) {
    aiPrompt.value = savedPrompt
    syncTemplateWithPrompt()
  }

  try {
    const latest = await getLatestByPage(projectId.value, route.params.pageId)
    if (latest && latest.id) {
      currentTaskId.value = latest.id

      if (latest.status === 'COMPLETED') {
        // 已完成：为旧任务补注入元素选取脚本（幂等）
        await prepareElementEdit(latest.id).catch(() => {})
        // 已完成：恢复预览
        previewUrl.value = toAbsolute(latest.previewUrl)
        downloadUrl.value = toAbsolute(latest.downloadUrl)
        showIterateInput.value = true
        loadRequirementDoc(latest.id)
        refreshPortals()
      } else if (latest.status === 'FAILED') {
        // 失败：显示错误信息，不重连
        if (latest.progress) pushProgressLog(latest.progress)
        pushProgressLog('[错误] 生成失败，请重试')
        showIterateInput.value = false
      } else if (latest.status === 'RUNNING' || latest.status === 'PENDING') {
        // 正在生成中：重连 SSE
        generating.value = true
        pushProgressLog('[恢复] 检测到正在进行的生成任务，正在重连...')
        if (latest.status === 'RUNNING') {
          pushProgressLog('[恢复] 任务曾中断，将从已有产物继续生成，AI 将保持代码格式一致性')
        }
        // 尝试加载已有需求文档（可能已生成）
        loadRequirementDoc(latest.id)
        connectSSE(latest.id, (data) => {
          downloadUrl.value = toAbsolute(data.downloadUrl)
          previewUrl.value = toAbsolute(data.previewUrl)
          generating.value = false
          showIterateInput.value = true
          pushProgressLog('[完成] 网站生成完成！')
          loadRequirementDoc(currentTaskId.value)
          if (data.screenshotUrl) {
            updateProjectScreenshot(projectId.value, data.screenshotUrl).catch(() => {})
          }
          // 自动切换到运行 tab 并获取运行状态
          activeLeftTab.value = 'run'
          fetchRunStatus()
        })
      }

      // 恢复 prompt（从后端返回的任务中获取）
      if (latest.prompt) {
        aiPrompt.value = latest.prompt
        syncTemplateWithPrompt()
      }

      // 恢复运行状态
      try {
        const status = await getRunStatus(latest.id)
        runStatus.value = status
        if (status.logs) runLogs.value = status.logs
      } catch { /* 忽略 */ }

      return
    }
  } catch {
    // 忽略
  }

  // 无任务记录
  currentTaskId.value = ''
}

watch(() => route.params.pageId, () => {
  if (route.name === 'Editor') {
    loadPageData()
  }
})

watch(() => route.params.projectId, () => {
  if (route.name === 'Editor') {
    loadPageData()
  }
})

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
  loadComponents()
  window.addEventListener('beforeunload', stopRunOnLeave)
  window.addEventListener('message', handleFrameMessage)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', stopRunOnLeave)
  window.removeEventListener('message', handleFrameMessage)
  // 关闭 SSE 连接（生成任务在后端独立线程中继续运行，不受影响）
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  // 不在 SPA 导航时停止预览服务器，仅浏览器关闭/刷新时通过 beforeunload 停止
})

const fetchRunStatus = async () => {
  if (!currentTaskId.value) return
  try {
    const status = await getRunStatus(currentTaskId.value)
    runStatus.value = status
    if (status.logs) runLogs.value = status.logs
    if (Array.isArray(status.portals)) {
      applyPortalList(status.portals)
    } else {
      refreshPortals()
    }
  } catch { /* 忽略 */ }
}

const handleStartRun = async () => {
  if (!currentTaskId.value) return
  runStarting.value = true
  runLogs.value = ['[运行] 正在启动...']
  try {
    const status = await startRun(currentTaskId.value)
    runStatus.value = status
    runLogs.value = status.logs || []
    if (Array.isArray(status.portals)) {
      applyPortalList(status.portals)
    } else {
      refreshPortals()
    }
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
    if (Array.isArray(status.portals)) {
      applyPortalList(status.portals)
    } else {
      refreshPortals()
    }
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

const loadRequirementDoc = async (taskId) => {
  reqLoading.value = true
  try {
    const result = await getRequirement(taskId)
    if (result) {
      if (typeof result === 'string') {
        try {
          requirementDoc.value = JSON.parse(result)
          requirementRaw.value = ''
        } catch (parseErr) {
          console.error('需求文档 JSON 解析失败，降级显示原始文本:', parseErr.message)
          requirementDoc.value = null
          requirementRaw.value = result
        }
      } else {
        requirementDoc.value = result
        requirementRaw.value = ''
      }
    } else {
      requirementDoc.value = null
      requirementRaw.value = ''
    }
  } catch {
    requirementDoc.value = null
    requirementRaw.value = ''
  } finally {
    reqLoading.value = false
  }
}
</script>

<style scoped>
.editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  /* 与工作台一致的浅色底，避免深蓝压暗整个编辑区 */
  background: linear-gradient(180deg, #fdfefe 0%, #eef4fc 58%, #e6edfa 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* ====== 顶栏（浅色玻璃，与工作台头部一致） ====== */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 56px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.5));
  -webkit-backdrop-filter: var(--glass-blur-heavy);
  backdrop-filter: var(--glass-blur-heavy);
  color: #1e293b;
  flex-shrink: 0;
  z-index: 10;
  border-bottom: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 10px 30px rgba(49, 46, 129, 0.1), inset 0 1px 0 rgba(255, 255, 255, 0.85);
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.32);
  color: #475569;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.back-btn:hover {
  background: #fff;
  border-color: rgba(37, 99, 235, 0.35);
  color: #2563eb;
}
.brand-divider {
  width: 1px;
  height: 22px;
  background: rgba(148, 163, 184, 0.35);
}
.project-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
}
.project-info svg {
  color: #2563eb;
}
.project-name {
  font-weight: 600;
  font-size: 15px;
  color: #1e293b;
  letter-spacing: -0.3px;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.page-select {
  width: 160px;
}
.page-select :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(148, 163, 184, 0.28);
  box-shadow: none;
  border-radius: 8px;
}
.page-select :deep(.el-input__inner) {
  color: #334155;
}
.page-select :deep(.el-input__suffix) {
  color: #2563eb;
}
.export-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.32);
  color: #475569;
  padding: 6px 16px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.export-btn:hover {
  background: #fff;
  border-color: rgba(37, 99, 235, 0.35);
  color: #2563eb;
}

/* ====== 三栏布局 ====== */
.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ====== 左右面板（玻璃） ====== */
.panel {
  display: flex;
  flex-direction: column;
  /* 侧边栏用浅冷色底，与内部纯白输入框形成层次 */
  background: linear-gradient(180deg, rgba(238, 244, 253, 0.92), rgba(232, 240, 252, 0.88));
  -webkit-backdrop-filter: var(--glass-blur);
  backdrop-filter: var(--glass-blur);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
  overflow: hidden;
}
.left-panel {
  width: 320px;
  border-right: 1px solid rgba(148, 163, 184, 0.28);
  flex-shrink: 0;
}
.right-panel {
  width: 300px;
  border-left: 1px solid rgba(148, 163, 184, 0.28);
  flex-shrink: 0;
}

/* 自定义 tabs */
.left-panel :deep(.el-tabs) {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.left-panel :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.25);
  background: rgba(255, 255, 255, 0.6);
}
.left-panel :deep(.el-tabs__nav-wrap::after) {
  display: none;
}
.left-panel :deep(.el-tabs__item) {
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  padding: 0 14px;
  height: 40px;
  line-height: 40px;
}
.left-panel :deep(.el-tabs__item.is-active) {
  color: #2563eb;
}
.left-panel :deep(.el-tabs__active-bar) {
  background: #2563eb;
  height: 2px;
}
.left-panel :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
}
.left-panel :deep(.el-tab-pane) {
  height: 100%;
  overflow-y: auto;
  padding: 16px;
}

/* AI 生成面板 */
.ai-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.template-select :deep(.el-select__wrapper) {
  border-radius: 10px;
  min-height: 36px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.4);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  font-size: 13px;
}
.template-select :deep(.el-select__wrapper:hover) {
  border-color: rgba(37, 99, 235, 0.5);
}
.template-select :deep(.el-select__wrapper.is-focused) {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}
.prompt-input-wrap {
  position: relative;
}
.prompt-textarea :deep(.el-textarea__inner) {
  border-radius: 10px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.4);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  font-size: 13px;
  line-height: 1.6;
  resize: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.prompt-textarea :deep(.el-textarea__inner:focus) {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}
.prompt-char-count {
  text-align: right;
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}
.custom-prompt-wrap {
  position: relative;
}
.custom-prompt-label {
  font-size: 12px;
  font-weight: 500;
  color: #475569;
  margin-bottom: 6px;
}
.custom-prompt-textarea :deep(.el-textarea__inner) {
  border-radius: 10px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.4);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  font-size: 12px;
  line-height: 1.5;
  resize: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.custom-prompt-textarea :deep(.el-textarea__inner:focus) {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}
.generate-btn {
  width: 100%;
  height: 42px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  background: linear-gradient(135deg, #2563eb, #0284c7);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s;
}
.generate-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.4);
}
.download-section {
  padding-top: 4px;
}
.download-btn {
  width: 100%;
  height: 38px;
  border-radius: 10px;
  border: 1px solid rgba(52, 211, 153, 0.6);
  color: #059669;
  background: rgba(236, 253, 245, 0.65);
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s;
}
.download-btn:hover {
  background: rgba(209, 250, 229, 0.8);
  border-color: #10b981;
}

/* 迭代优化 */
.iterate-section {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.5);
}
.section-label {
  font-size: 12px;
  font-weight: 600;
  color: #0284c7;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.section-label::before {
  content: '';
  width: 4px;
  height: 14px;
  background: linear-gradient(135deg, #2563eb, #0284c7);
  border-radius: 2px;
}
.iterate-input-wrap {
  position: relative;
  margin-bottom: 8px;
}
.iterate-textarea :deep(.el-textarea__inner) {
  border-radius: 10px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.4);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  font-size: 13px;
  line-height: 1.6;
  resize: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.iterate-textarea :deep(.el-textarea__inner:focus) {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}
.iterate-btn {
  width: 100%;
  height: 38px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #fff;
  transition: all 0.3s;
}
.iterate-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(245, 158, 11, 0.4);
}
.iterate-btn:disabled {
  background: #e2e8f0;
  color: #94a3b8;
  transform: none;
  box-shadow: none;
}

/* 进度日志（深色终端玻璃） */
.progress-log {
  margin-top: 4px;
  background: var(--glass-bg-dark);
  -webkit-backdrop-filter: blur(16px) saturate(160%);
  backdrop-filter: blur(16px) saturate(160%);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 8px 28px rgba(15, 12, 40, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}
.progress-log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #7dd3fc;
  background: rgba(37, 99, 235, 0.14);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.log-header-title {
  display: flex;
  align-items: center;
  gap: 6px;
}
.log-live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 500;
  color: #6ee7b7;
}
.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.8);
  animation: pulse 1.4s ease-in-out infinite;
}
.log-badge {
  font-size: 10px;
  font-weight: 500;
  color: #8b90b5;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 1px 8px;
}
.log-scanbar {
  height: 2px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.05);
}
.log-scanbar i {
  display: block;
  width: 33%;
  height: 100%;
  background: linear-gradient(90deg, transparent, #60a5fa, #22d3ee, transparent);
  animation: scan 1.6s ease-in-out infinite;
}
@keyframes scan {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(400%); }
}
.progress-log-body {
  max-height: 300px;
  overflow-y: auto;
  padding: 6px 0;
}
.progress-log-body::-webkit-scrollbar {
  width: 6px;
}
.progress-log-body::-webkit-scrollbar-thumb {
  background: rgba(96, 165, 250, 0.35);
  border-radius: 3px;
}
.progress-log .log-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 3px 12px;
  font-size: 11px;
  font-family: 'SF Mono', 'Cascadia Code', 'Fira Code', monospace;
  line-height: 1.6;
  transition: background 0.15s;
}
.progress-log .log-line:hover {
  background: rgba(255, 255, 255, 0.05);
}
.log-time {
  flex-shrink: 0;
  font-size: 10px;
  color: #6b7194;
  font-variant-numeric: tabular-nums;
}
.log-tag {
  flex-shrink: 0;
  font-size: 10px;
  color: #7dd3fc;
  background: rgba(96, 165, 250, 0.18);
  border: 1px solid rgba(96, 165, 250, 0.3);
  border-radius: 4px;
  padding: 0 6px;
  line-height: 16px;
}
.log-text {
  color: #cdd6f4;
  white-space: pre-wrap;
  word-break: break-all;
}
.log-line.log-error {
  background: rgba(243, 139, 168, 0.08);
}
.log-line.log-error .log-text,
.log-line.log-error .log-tag {
  color: #f38ba8;
}
.log-line.log-error .log-tag {
  background: rgba(243, 139, 168, 0.14);
  border-color: rgba(243, 139, 168, 0.35);
}
.log-line.log-success .log-text {
  color: #a6e3a1;
}
.log-line.log-success .log-tag {
  color: #a6e3a1;
  background: rgba(166, 227, 161, 0.12);
  border-color: rgba(166, 227, 161, 0.3);
}
.log-line.log-warn .log-text {
  color: #f9e2af;
}
.log-line.log-warn .log-tag {
  color: #f9e2af;
  background: rgba(249, 226, 175, 0.12);
  border-color: rgba(249, 226, 175, 0.3);
}
.log-line.log-action .log-tag {
  color: #89b4fa;
  background: rgba(137, 180, 250, 0.14);
  border-color: rgba(137, 180, 250, 0.32);
}

/* 需求分析文档面板 */
.req-doc-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 16px;
  color: #94a3b8;
  font-size: 13px;
  text-align: center;
}
.empty-state svg {
  color: #cbd5e1;
}
.empty-state.small {
  padding: 16px 0;
  font-size: 12px;
}
.empty-hint {
  font-size: 11px;
  color: #cbd5e1;
}
.loading-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid #e2e8f0;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.req-doc-body {
  overflow-y: auto;
  flex: 1;
}
.req-raw {
  margin: 0;
  padding: 16px;
  background: rgba(30, 41, 59, 0.85);
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
  color: #e2e8f0;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
}
.req-header {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}
.req-header h4 {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}
.req-summary {
  margin: 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
.req-card {
  margin-bottom: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.req-card:hover {
  border-color: rgba(37, 99, 235, 0.4);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.1);
}
.req-card h5 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1e40af;
  padding-bottom: 6px;
  border-bottom: 1px solid #e2e8f0;
}
.card-icon {
  font-size: 14px;
}
.req-item {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f1f5f9;
}
.req-item:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}
.req-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.req-item strong {
  font-size: 12px;
  color: #334155;
}
.req-detail {
  margin: 2px 0;
  font-size: 11px;
  color: #64748b;
  padding-left: 4px;
  line-height: 1.5;
}
.field-detail {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 8px;
  font-size: 11px;
}
.field-detail code {
  background: #e2e8f0;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  color: #334155;
}
.field-type {
  color: #0284c7;
  font-weight: 500;
}
.req-flow-list {
  margin: 2px 0;
  padding-left: 20px;
  font-size: 11px;
  color: #475569;
}
.req-flow-list li {
  margin-bottom: 2px;
}

/* 运行面板 */
.run-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.run-status-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.run-status-card.active {
  background: rgba(236, 253, 245, 0.6);
  border-color: rgba(167, 243, 208, 0.8);
}
.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #cbd5e1;
}
.status-dot.active {
  background: #10b981;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.4);
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
.run-url {
  padding-left: 16px;
}
.run-url a {
  font-size: 12px;
  font-family: monospace;
  color: #2563eb;
  text-decoration: none;
  word-break: break-all;
}
.run-url a:hover {
  text-decoration: underline;
}
.run-portals {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 6px 0 0 16px;
}
.run-portals-label {
  font-size: 11px;
  color: #94a3b8;
}
.run-portal-link {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(238, 242, 255, 0.7);
  border: 1px solid rgba(199, 210, 254, 0.6);
  color: #1d4ed8;
  text-decoration: none;
}
.run-portal-link:hover {
  background: rgba(224, 231, 255, 0.85);
}
.run-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.run-btn {
  flex: 1;
  min-width: 80px;
  height: 36px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}
.open-btn {
  background: rgba(236, 253, 245, 0.65);
  border: 1px solid rgba(167, 243, 208, 0.7);
  color: #059669;
}
.open-btn:hover {
  background: rgba(209, 250, 229, 0.8);
}
.run-tip {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.5;
  padding: 8px;
  background: rgba(255, 251, 235, 0.7);
  border-radius: 8px;
  border: 1px solid rgba(253, 230, 138, 0.7);
}

/* 占位面板 */
.placeholder-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: #94a3b8;
  font-size: 13px;
}
.placeholder-text svg {
  color: #cbd5e1;
}
.sub-text {
  font-size: 12px;
  color: #cbd5e1;
}

/* ====== 中央画布 ====== */
.canvas {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: rgba(255, 255, 255, 0.25);
  overflow: hidden;
  position: relative;
  background-image:
    radial-gradient(circle, rgba(148, 163, 184, 0.35) 1px, transparent 1px);
  background-size: 24px 24px;
}
/* 角色端切换条（玻璃） */
.portal-tabs {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--glass-bg);
  -webkit-backdrop-filter: var(--glass-blur);
  backdrop-filter: var(--glass-blur);
  border-bottom: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--glass-highlight);
  overflow-x: auto;
}
.portal-tab {
  flex-shrink: 0;
  height: 28px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  background: rgba(248, 250, 252, 0.6);
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.portal-tab:hover {
  border-color: rgba(199, 210, 254, 0.9);
  color: #1d4ed8;
}
.portal-tab.active {
  background: rgba(238, 242, 255, 0.8);
  border-color: rgba(165, 180, 252, 0.9);
  color: #1d4ed8;
  font-weight: 600;
}
.empty-canvas {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 0;
  gap: 16px;
  color: #94a3b8;
}
.empty-canvas-icon {
  width: 100px;
  height: 100px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.55);
  -webkit-backdrop-filter: var(--glass-blur);
  backdrop-filter: var(--glass-blur);
  border: 1px solid rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--glass-shadow), var(--glass-highlight);
  color: #cbd5e1;
}
.empty-canvas h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #64748b;
}
.empty-canvas p {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
  max-width: 340px;
  text-align: center;
  line-height: 1.6;
}
.preview-frame {
  width: 100%;
  flex: 1;
  min-height: 0;
  border: none;
  background: #fff;
  box-shadow: 0 0 40px rgba(0, 0, 0, 0.08);
}

/* ====== 组件库面板 ====== */
.component-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.component-search :deep(.el-input__wrapper) {
  border-radius: 10px;
  min-height: 36px;
}
.component-cats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.cat-chip {
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(255, 255, 255, 0.75);
  color: #64748b;
  font-size: 12px;
  line-height: 1;
  padding: 6px 10px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s;
}
.cat-chip:hover {
  border-color: rgba(37, 99, 235, 0.45);
  color: #2563eb;
}
.cat-chip.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.22);
}
.component-hint {
  font-size: 12px;
  line-height: 1.5;
  color: #94a3b8;
  padding: 8px 10px;
  border: 1px dashed rgba(148, 163, 184, 0.4);
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.08);
}
.component-hint.ready {
  color: #2563eb;
  border-color: rgba(37, 99, 235, 0.45);
  background: rgba(37, 99, 235, 0.06);
}
.component-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.component-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  transition: box-shadow 0.18s, transform 0.18s, border-color 0.18s;
}
.component-card:hover {
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
  border-color: rgba(99, 102, 241, 0.4);
}
/* 素材悬停预览弹层 */
.material-pop-body {
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.material-stage {
  height: 148px;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: #f8fafc;
}
.material-stage iframe {
  width: 100%;
  height: 148px;
  border: 0;
  display: block;
  pointer-events: none;
}
.material-stage.scaled iframe {
  width: 780px;
  height: 560px;
  transform: scale(0.36);
  transform-origin: top left;
}
.material-loading {
  height: 148px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 16px;
  text-align: center;
  font-size: 11px;
  color: #64748b;
}
.mcp-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 0 4px;
  border-radius: 4px;
  font-size: 9px;
  font-style: normal;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #7c3aed;
  background: rgba(124, 58, 237, 0.1);
  vertical-align: 1px;
}
.material-pop-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.material-pop-title span {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.material-pop-title em {
  font-size: 11px;
  font-style: normal;
  color: #94a3b8;
}
.material-pop-actions {
  display: flex;
  gap: 6px;
}
.material-pop-actions :deep(.el-button) {
  margin-left: 0;
  flex: 1;
}
.material-tip {
  font-size: 11px;
  color: #f59e0b;
}
.component-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.component-name {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.component-desc {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
.component-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.component-actions :deep(.el-button) {
  margin-left: 0;
  flex: 1;
  min-width: 0;
  border-radius: 8px;
  font-size: 12px;
  padding: 5px 8px;
}

/* ====== 属性面板 ====== */
.prop-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  padding: 16px;
  overflow-y: auto;
}
.prop-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.prop-clear {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12px;
  color: #2563eb;
  cursor: pointer;
}
.prop-clear:hover {
  text-decoration: underline;
}
.prop-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.prop-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
}
.prop-label {
  flex-shrink: 0;
  width: 40px;
  color: #94a3b8;
}
.prop-value {
  color: #334155;
  word-break: break-all;
}
code.prop-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: #2563eb;
}
.prop-selector {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  color: #64748b;
  padding: 6px 8px;
  border-radius: 8px;
  background: rgba(148, 163, 184, 0.12);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.patch-textarea :deep(textarea) {
  font-size: 12px;
}
.patch-btn {
  width: 100%;
  border-radius: 10px;
}
.prop-tip {
  margin: 0;
  font-size: 11px;
  line-height: 1.6;
  color: #94a3b8;
}
/* 历史面板 */
.history-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 2px 2px 8px;
}
.history-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.history-page-select {
  flex: 1 0 100%;
}
.history-branch-select {
  flex: 1;
}
.history-tip {
  margin: 0;
  font-size: 11px;
  line-height: 1.6;
  color: #94a3b8;
}
.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 240px);
  overflow-y: auto;
}
.history-item {
  padding: 10px 12px;
  border: 1px solid #e8ecf3;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: border-color .18s, box-shadow .18s, transform .18s;
}
.history-item:hover {
  border-color: #c7d2fe;
  box-shadow: 0 6px 18px rgba(99, 102, 241, .1);
  transform: translateY(-1px);
}
.history-item-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.history-msg {
  font-size: 12.5px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-time {
  flex-shrink: 0;
  font-size: 11px;
  color: #94a3b8;
}
.history-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  flex-wrap: wrap;
}
.history-page {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10.5px;
  padding: 1px 6px;
  border-radius: 5px;
  background: #f1f5f9;
  color: #475569;
}
.history-branch {
  font-size: 10.5px;
  padding: 1px 6px;
  border-radius: 5px;
  background: rgba(99, 102, 241, .1);
  color: #4f46e5;
}
.history-id {
  font-size: 10.5px;
  color: #94a3b8;
  margin-left: auto;
}
/* 版本内容弹窗 */
.snapshot-detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.snapshot-detail-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #64748b;
}
.snapshot-detail-msg {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}
.snapshot-code {
  margin: 0;
  max-height: 52vh;
  overflow: auto;
  padding: 14px;
  border-radius: 10px;
  background: #0f172a;
  color: #cbd5e1;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>