/**
 * SSH 密钥上传脚本 - 将本地公钥添加到远程服务器 authorized_keys
 *
 * 用法：node scripts/setup-ssh2.js
 */
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

const pubKeyPath = path.join(os.homedir(), '.ssh', 'id_rsa.pub')
const pubKey = fs.readFileSync(pubKeyPath, 'utf8').trim()
console.log(`公钥: ${pubKey.slice(0, 60)}...`)

const conn = new Client()

conn.on('ready', () => {
  console.log(`已连接到 ${CONFIG.username}@${CONFIG.host}:${CONFIG.port}`)

  const cmds = [
    'mkdir -p ~/.ssh',
    'chmod 700 ~/.ssh',
    `echo '${pubKey}' >> ~/.ssh/authorized_keys`,
    'chmod 600 ~/.ssh/authorized_keys',
    "sort -u ~/.ssh/authorized_keys -o ~/.ssh/authorized_keys",
    'echo "OK: 密钥添加成功"',
  ]

  conn.exec(cmds.join(' && '), (err, stream) => {
    if (err) throw err
    stream.on('data', (data) => process.stdout.write(data.toString()))
    stream.stderr.on('data', (data) => process.stderr.write(data.toString()))
    stream.on('close', () => {
      console.log('\n完成！')
      conn.end()
    })
  })
})

conn.on('error', (err) => {
  console.error('连接失败:', err.message)
  process.exit(1)
})

conn.connect({ ...CONFIG, readyTimeout: 10000 })