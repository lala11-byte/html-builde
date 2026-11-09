/**
 * 远程执行脚本 - 在远程 GPU 服务器上执行命令
 *
 * 用法：
 *   node scripts/remote-exec.js "nvidia-smi"                    # 默认服务器(gpu-5090)
 *   node scripts/remote-exec.js --server gpu-v100 "nvidia-smi"  # 指定服务器
 *   node scripts/remote-exec.js --cmd "python3 train.py"        # 执行并实时输出
 *   node scripts/remote-exec.js --interactive                   # 交互式 shell
 *   node scripts/remote-exec.js --list                          # 列出可用服务器
 */
const { Client } = require('ssh2')
const readline = require('readline')
const { getServerConfig, SERVERS } = require('./remote-config')

function execCommand(conn, cmd) {
  return new Promise((resolve, reject) => {
    conn.exec(cmd, { pty: true }, (err, stream) => {
      if (err) return reject(err)
      stream.on('data', (data) => process.stdout.write(data.toString()))
      stream.stderr.on('data', (data) => process.stderr.write(data.toString()))
      stream.on('close', (code) => resolve(code))
    })
  })
}

function execShell(conn) {
  return new Promise((resolve, reject) => {
    conn.shell({ pty: true }, (err, stream) => {
      if (err) return reject(err)

      stream.on('data', (data) => process.stdout.write(data.toString()))
      stream.stderr.on('data', (data) => process.stderr.write(data.toString()))

      const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        terminal: true,
      })

      rl.on('line', (line) => {
        if (line === 'exit' || line === 'quit') {
          stream.end('exit\n')
          rl.close()
          conn.end()
          resolve()
        } else {
          stream.write(line + '\n')
        }
      })

      stream.on('close', () => {
        rl.close()
        conn.end()
        resolve()
      })
    })
  })
}

async function main() {
  const args = process.argv.slice(2)

  // 列出可用服务器
  if (args.includes('--list')) {
    console.log('可用服务器:')
    for (const [name, cfg] of Object.entries(SERVERS)) {
      console.log(`  ${name} - ${cfg.description}`)
    }
    return
  }

  const interactive = args.includes('--interactive') || args.includes('-i')
  const serverArgIdx = args.findIndex((a) => a === '--server' || a.startsWith('--server='))
  let serverName = null
  if (serverArgIdx !== -1) {
    if (args[serverArgIdx].startsWith('--server=')) {
      serverName = args[serverArgIdx].split('=')[1]
    } else {
      serverName = args[serverArgIdx + 1]
    }
  }
  const cmdIndex = args.indexOf('--cmd')
  let cmd = cmdIndex !== -1 ? args.slice(cmdIndex + 1).join(' ') : ''

  // 如果没有 --cmd，取第一个非选项参数作为命令
  if (!cmd) {
    const nonFlags = args.filter((a, i) => {
      if (a.startsWith('--')) {
        // 跳过 --server xxx 的值参数
        if (a === '--server' || a.startsWith('--server=')) {
          if (!a.startsWith('--server=')) return false // 跳过 --server
          return false // 跳过 --server=xxx
        }
        return false
      }
      // 跳过 --server 后面的值
      if (i > 0 && (args[i - 1] === '--server')) return false
      return true
    })
    if (nonFlags.length > 0) {
      cmd = nonFlags.join(' ')
    }
  }

  const serverConfig = getServerConfig(serverName)

  if (interactive || args.length === 0) {
    console.log(`进入交互式 shell (${serverConfig.name})，输入 exit 退出`)
    console.log('连接中...')
  } else if (!cmd) {
    console.log('用法:')
    console.log('  node scripts/remote-exec.js "命令"')
    console.log('  node scripts/remote-exec.js --server gpu-v100 "命令"')
    console.log('  node scripts/remote-exec.js --interactive')
    console.log('  node scripts/remote-exec.js --list')
    process.exit(0)
  }

  const conn = new Client()

  return new Promise((resolve, reject) => {
    conn.on('ready', async () => {
      try {
        if (interactive || args.length === 0) {
          await execShell(conn)
        } else {
          console.log(`[remote:${serverConfig.name}] 执行: ${cmd}`)
          console.log('---')
          await execCommand(conn, cmd)
          console.log('---')
          console.log(`[remote:${serverConfig.name}] 命令执行完毕`)
        }
        resolve()
      } catch (e) {
        console.error(`[remote] 执行失败: ${e.message}`)
        conn.end()
        reject(e)
      }
    })

    conn.on('error', (err) => {
      console.error(`[remote] 连接失败: ${err.message}`)
      reject(err)
    })

    conn.connect(serverConfig)
  })
}

main().catch(() => process.exit(1))