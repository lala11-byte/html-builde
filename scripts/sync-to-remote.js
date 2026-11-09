/**
 * 代码同步脚本 - 将本地项目增量同步到远程 GPU 服务器
 *
 * 用法：
 *   node scripts/sync-to-remote.js                          # 同步到默认服务器(gpu-5090)
 *   node scripts/sync-to-remote.js --server gpu-v100        # 同步到指定服务器
 *   node scripts/sync-to-remote.js --dir backend            # 只同步 backend 目录
 *   node scripts/sync-to-remote.js --watch                  # 监听模式，文件变更自动同步
 *   node scripts/sync-to-remote.js --all                    # 同步到所有服务器
 */
const fs = require('fs')
const path = require('path')
const { Client } = require('ssh2')
const { ROOT, REMOTE_ROOT, getServerConfig, SERVERS } = require('./remote-config')

// 不同步的目录/文件
const IGNORE_PATTERNS = [
  /(^|[\\/])\.git([\\/]|$)/,
  /(^|[\\/])node_modules([\\/]|$)/,
  /(^|[\\/])target([\\/]|$)/,
  /(^|[\\/])dist([\\/]|$)/,
  /(^|[\\/])logs([\\/]|$)/,
  /(^|[\\/])\.idea([\\/]|$)/,
  /(^|[\\/])\.vscode([\\/]|$)/,
  /(^|[\\/])\.trae([\\/]|$)/,
  /\.log$/,
  /package-lock\.json$/,
]

function shouldIgnore(relPath) {
  const normalized = relPath.replace(/\\/g, '/')
  return IGNORE_PATTERNS.some((p) => p.test(normalized))
}

function collectFiles(dir) {
  const files = []
  const base = path.resolve(ROOT, dir)
  if (!fs.existsSync(base)) return files

  function walk(current) {
    const entries = fs.readdirSync(current, { withFileTypes: true })
    for (const e of entries) {
      const full = path.join(current, e.name)
      const rel = path.relative(ROOT, full)
      if (shouldIgnore(rel)) continue
      if (e.isDirectory()) {
        walk(full)
      } else {
        files.push({ localPath: full, remotePath: path.posix.join(REMOTE_ROOT, rel.replace(/\\/g, '/')) })
      }
    }
  }
  walk(base)
  return files
}

function mkdirAndPut(sftp, localPath, remotePath) {
  return new Promise((resolve, reject) => {
    const remoteDir = path.posix.dirname(remotePath)
    const parts = remoteDir.split('/').filter(Boolean)
    let current = ''

    function mkdirStep(i) {
      if (i >= parts.length) {
        sftp.fastPut(localPath, remotePath, (err) => {
          if (err) reject(err)
          else resolve()
        })
        return
      }
      current += '/' + parts[i]
      sftp.mkdir(current, () => { mkdirStep(i + 1) })
    }
    mkdirStep(0)
  })
}

async function uploadFiles(conn, files) {
  return new Promise((resolve, reject) => {
    conn.sftp((err, sftp) => {
      if (err) return reject(err)
      let done = 0
      const total = files.length
      const BATCH_SIZE = 3

      async function processBatch(index) {
        if (index >= files.length) {
          console.log()
          resolve()
          return
        }
        const batch = files.slice(index, index + BATCH_SIZE)
        await Promise.all(
          batch.map(async (f) => {
            try {
              await mkdirAndPut(sftp, f.localPath, f.remotePath)
              done++
              process.stdout.write(`\r  同步进度: ${done}/${total} (${Math.round((done / total) * 100)}%)`)
            } catch (e) {
              process.stdout.write(`\n  上传失败: ${f.remotePath} - ${e.message}\n`)
            }
          })
        )
        processBatch(index + BATCH_SIZE)
      }
      processBatch(0)
    })
  })
}

async function syncToServer(serverConfig, files, targetDir) {
  const label = `${serverConfig.name} (${serverConfig.description})`
  console.log(`\n[sync] 目标: ${label}`)

  const conn = new Client()
  return new Promise((resolve, reject) => {
    conn.on('ready', async () => {
      try {
        console.log(`[sync] 已连接，开始上传...`)
        const start = Date.now()
        await uploadFiles(conn, files)
        const elapsed = ((Date.now() - start) / 1000).toFixed(1)
        console.log(`[sync] ${serverConfig.name} 同步完成！${files.length} 个文件，耗时 ${elapsed}s`)
        conn.end()
        resolve()
      } catch (e) {
        console.error(`[sync] ${serverConfig.name} 同步失败: ${e.message}`)
        conn.end()
        resolve()
      }
    })
    conn.on('error', (err) => {
      console.error(`[sync] ${serverConfig.name} 连接失败: ${err.message}`)
      resolve()
    })
    conn.connect(serverConfig)
  })
}

async function doSync(targetDir, servers) {
  console.log(`[sync] 收集文件...`)
  const files = collectFiles(targetDir)
  console.log(`[sync] 共 ${files.length} 个文件待同步`)

  if (files.length === 0) {
    console.log('[sync] 没有需要同步的文件')
    return
  }

  for (const serverConfig of servers) {
    await syncToServer(serverConfig, files, targetDir)
  }
}

async function watchAndSync(targetDir, serverConfig) {
  const chokidar = require('chokidar')
  const watchPath = path.resolve(ROOT, targetDir)

  const ignored = [
    '**/.git/**', '**/node_modules/**', '**/target/**', '**/dist/**',
    '**/logs/**', '**/.idea/**', '**/.vscode/**', '**/.trae/**',
    '**/*.log', '**/package-lock.json',
  ]

  const watcher = chokidar.watch(watchPath, {
    ignored, persistent: true, ignoreInitial: true,
    awaitWriteFinish: { stabilityThreshold: 300, pollInterval: 100 },
  })

  let timer = null
  let pending = new Set()

  const flush = async () => {
    if (pending.size === 0) return
    const files = Array.from(pending).filter((f) => fs.existsSync(f))
    pending.clear()
    if (files.length === 0) return
    console.log(`\n[sync] 检测到 ${files.length} 个文件变更 -> ${serverConfig.name}`)

    const conn = new Client()
    await new Promise((resolve) => {
      conn.on('ready', () => {
        conn.sftp((err, sftp) => {
          if (err) { conn.end(); resolve(); return }
          let done = 0
          files.forEach((localPath) => {
            const rel = path.relative(ROOT, localPath)
            const remotePath = path.posix.join(REMOTE_ROOT, rel.replace(/\\/g, '/'))
            mkdirAndPut(sftp, localPath, remotePath).then(() => {
              console.log(`  -> ${rel}`)
              done++
              if (done >= files.length) {
                const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
                console.log(`[${time}] 同步完成`)
                conn.end(); resolve()
              }
            }).catch((e) => {
              console.error(`  失败: ${rel} - ${e.message}`)
              done++
              if (done >= files.length) { conn.end(); resolve() }
            })
          })
        })
      })
      conn.on('error', () => resolve())
      conn.connect(serverConfig)
    })
  }

  watcher.on('all', (event, filePath) => {
    pending.add(filePath)
    clearTimeout(timer)
    timer = setTimeout(flush, 2000)
  })

  watcher.on('ready', () => {
    const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    console.log(`[${time}] 监听模式已启动 -> ${serverConfig.name}`)
    console.log(`[sync] 监听路径: ${watchPath}`)
    console.log(`[sync] 远程路径: ${REMOTE_ROOT}`)
  })

  watcher.on('error', (err) => {
    console.error(`[sync] 监听错误: ${err.message}`)
  })
}

async function main() {
  const args = process.argv.slice(2)
  const watchMode = args.includes('--watch')
  const syncAll = args.includes('--all')
  const serverArgIdx = args.findIndex((a) => a === '--server' || a.startsWith('--server='))
  let serverName = null
  if (serverArgIdx !== -1) {
    if (args[serverArgIdx].startsWith('--server=')) {
      serverName = args[serverArgIdx].split('=')[1]
    } else {
      serverName = args[serverArgIdx + 1]
    }
  }
  const dirArg = args.find((a) => a.startsWith('--dir='))
  const targetDir = dirArg ? dirArg.split('=')[1] : '.'

  // 列出可用服务器
  if (args.includes('--list')) {
    console.log('可用服务器:')
    for (const [name, cfg] of Object.entries(SERVERS)) {
      console.log(`  ${name} - ${cfg.description}`)
    }
    return
  }

  if (watchMode) {
    const serverConfig = getServerConfig(serverName)
    console.log(`[sync] 目标: ${serverConfig.name} (${serverConfig.description})`)
    await watchAndSync(targetDir, serverConfig)
    return
  }

  const servers = syncAll
    ? Object.values(SERVERS).map((cfg) => ({ ...cfg, name: Object.keys(SERVERS).find((k) => SERVERS[k] === cfg) }))
    : [getServerConfig(serverName)]

  console.log(`[sync] 同步目录: ${targetDir === '.' ? '整个项目' : targetDir}`)
  console.log(`[sync] 远程路径: ${REMOTE_ROOT}`)
  await doSync(targetDir, servers)
}

main().catch(() => process.exit(1))