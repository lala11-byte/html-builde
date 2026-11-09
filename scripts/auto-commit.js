/**
 * 文件监听自动提交脚本（auto-commit watcher）
 *
 * 用法：node scripts/auto-commit.js
 * 效果：监听工作区文件变更，文件保存后静默 3 秒自动 git add + commit + push
 * 退出：Ctrl+C
 */
const path = require('path')
const { execSync } = require('child_process')
const chokidar = require('chokidar')

const ROOT = path.resolve(__dirname, '..')
const DEBOUNCE_MS = 3000
const RETRY_MS = 2000

// 排除目录和文件
const IGNORED = [
  '**/.git/**',
  '**/node_modules/**',
  '**/target/**',
  '**/dist/**',
  '**/logs/**',
  '**/.idea/**',
  '**/.vscode/**',
  '**/*.log',
]

let timer = null
let busy = false

const time = () => new Date().toLocaleTimeString('zh-CN', { hour12: false })

function run(cmd) {
  return execSync(cmd, { cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
}

function hasChanges() {
  return run('git status --porcelain').trim().length > 0
}

function commitAndPush() {
  if (busy) {
    timer = setTimeout(commitAndPush, RETRY_MS)
    return
  }
  busy = true
  try {
    if (!hasChanges()) return

    const files = run('git status --porcelain')
      .split('\n')
      .filter(Boolean)
      .map((l) => l.slice(3).trim().replace(/"/g, ''))
      .slice(0, 5)
      .join(', ')
    const msg = `自动提交：更新 ${files || '项目文件'}`

    run('git add -A')
    run(`git commit -m "${msg}"`)

    try {
      run('git push')
    } catch {
      run('git pull --rebase')
      run('git push')
    }

    console.log(`[${time()}] 已提交并推送: ${msg}`)
  } catch (e) {
    console.error(`[${time()}] 提交失败: ${(e.message || '').split('\n')[0]}`)
  } finally {
    busy = false
  }
}

// 使用 chokidar 替代 fs.watch，解决 Windows 下事件丢失问题
const watcher = chokidar.watch(ROOT, {
  ignored: IGNORED,
  persistent: true,
  ignoreInitial: true,
  awaitWriteFinish: {
    stabilityThreshold: 500,
    pollInterval: 100,
  },
})

watcher.on('all', (_event, filePath) => {
  clearTimeout(timer)
  timer = setTimeout(commitAndPush, DEBOUNCE_MS)
})

watcher.on('ready', () => {
  console.log(`[auto-commit] 正在监听 ${ROOT}`)
  console.log(`[auto-commit] 文件保存后 ${DEBOUNCE_MS / 1000} 秒自动 commit + push，Ctrl+C 退出`)
})

watcher.on('error', (err) => {
  console.error(`[${time()}] 监听错误: ${err.message}`)
})