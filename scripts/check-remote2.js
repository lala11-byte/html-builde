const { Client } = require('ssh2')
const fs = require('fs')
const path = require('path')
const os = require('os')

const CONFIG = {
  host: '223.109.239.32',
  port: 13936,
  username: 'root',
  password: 'ohTh3tox',
}

function exec(conn, cmd) {
  return new Promise((resolve) => {
    conn.exec(cmd, (err, stream) => {
      if (err) return resolve('ERROR: ' + err.message)
      let out = ''
      stream.on('data', (d) => out += d.toString())
      stream.stderr.on('data', (d) => out += d.toString())
      stream.on('close', () => resolve(out))
    })
  })
}

async function main() {
  const conn = new Client()
  return new Promise((resolve, reject) => {
    conn.on('ready', async () => {
      try {
        console.log('=== 远程服务器环境 ===\n')
        console.log('系统:', (await exec(conn, 'uname -a')).trim())
        console.log('\nGPU:')
        console.log(await exec(conn, 'nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv 2>&1'))
        console.log('Python:', (await exec(conn, 'python3 --version 2>&1')).trim())
        console.log('\n内存:')
        console.log(await exec(conn, 'free -h | head -2'))
        console.log('磁盘:')
        console.log(await exec(conn, 'df -h / | tail -1'))
        console.log('\nDocker:', (await exec(conn, 'docker --version 2>&1 || echo "未安装"')).trim())
        conn.end()
        resolve()
      } catch (e) {
        console.error(e)
        conn.end()
        reject(e)
      }
    })
    conn.on('error', (e) => { console.error('连接失败:', e.message); reject(e) })
    conn.connect({ ...CONFIG, readyTimeout: 15000 })
  })
}

main().catch(() => process.exit(1))