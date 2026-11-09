/**
 * 检查远程服务器环境（GPU、CUDA、磁盘等）
 *
 * 用法：node scripts/check-remote.js
 */
const { Client } = require('ssh2')
const fs = require('fs')
const path = require('path')
const os = require('os')

const CONFIG = {
  host: '223.109.239.36',
  port: 28332,
  username: 'root',
  privateKey: fs.readFileSync(path.join(os.homedir(), '.ssh', 'id_rsa')),
}

function exec(conn, cmd) {
  return new Promise((resolve, reject) => {
    conn.exec(cmd, (err, stream) => {
      if (err) return reject(err)
      let out = ''
      let errOut = ''
      stream.on('data', (d) => out += d.toString())
      stream.stderr.on('data', (d) => errOut += d.toString())
      stream.on('close', () => resolve(out || errOut))
    })
  })
}

async function main() {
  const conn = new Client()
  return new Promise((resolve, reject) => {
    conn.on('ready', async () => {
      try {
        console.log('=== 远程服务器环境检查 ===\n')

        console.log('--- 系统信息 ---')
        console.log(await exec(conn, 'uname -a'))
        console.log(await exec(conn, 'cat /etc/os-release | head -4'))

        console.log('--- GPU 信息 ---')
        const nvidia = await exec(conn, 'nvidia-smi --query-gpu=name,memory.total,driver_version,cuda_version --format=csv 2>&1')
        console.log(nvidia || '无 NVIDIA GPU 或未安装驱动')

        console.log('--- CUDA 版本 ---')
        console.log(await exec(conn, 'cat /usr/local/cuda/version.txt 2>/dev/null || nvcc --version 2>/dev/null | tail -1 || echo "未找到 CUDA"'))

        console.log('--- Python 环境 ---')
        console.log(await exec(conn, 'python3 --version 2>&1; which python3 2>&1'))

        console.log('--- Conda 环境 ---')
        console.log(await exec(conn, 'conda --version 2>&1 || echo "未安装 conda"'))

        console.log('--- 磁盘空间 ---')
        console.log(await exec(conn, 'df -h / | tail -1'))

        console.log('--- 内存 ---')
        console.log(await exec(conn, 'free -h | head -3'))

        console.log('=== 检查完成 ===')
        conn.end()
        resolve()
      } catch (e) {
        console.error('检查失败:', e.message)
        conn.end()
        reject(e)
      }
    })

    conn.on('error', (err) => {
      console.error('连接失败:', err.message)
      reject(err)
    })

    conn.connect({ ...CONFIG, readyTimeout: 10000 })
  })
}

main().catch(() => process.exit(1))