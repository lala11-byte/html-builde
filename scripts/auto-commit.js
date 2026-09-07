/**
 * 文件监听自动提交脚本（auto-commit watcher）
 *
 * 用法：node scripts/auto-commit.js
 * 效果：监听工作区文件变更，文件保存后静默 3 秒自动 git add + commit + push
 * 退出：Ctrl+C
 */
const fs = require('fs')
const path = require('path')
const { execSync } = require('child_process')

const ROOT = path.resolve(__dirname, '..')
const DEBOUNCE_MS = 3000
const RETRY_MS = 2000

// 排除构建产物、依赖、git 内部目录和日志
const EXCLUDE = /(^|[\\/])(\.git|node_modules|target|dist|logs|\.idea|\.vscode)([\\/]|$)|\.log$/

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

    // 取变更文件名（最多 5 个）生成提交信息
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
      // 远程有新提交时先 rebase 再推
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

// 防抖监听：变更后静默 DEBOUNCE_MS 才触发提交
fs.watch(ROOT, { recursive: true }, (_event, filename) => {
  if (!filename) return
  if (EXCLUDE.test(filename)) return
  clearTimeout(timer)
  timer = setTimeout(commitAndPush, DEBOUNCE_MS)
})

console.log(`[auto-commit] 正在监听 ${ROOT}`)
console.log(`[auto-commit] 文件保存后 ${DEBOUNCE_MS / 1000} 秒自动 commit + push，Ctrl+C 退出`)
