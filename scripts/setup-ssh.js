/**
 * SSH 密钥上传脚本 - 将本地公钥添加到远程服务器 authorized_keys
 *
 * 用法：node scripts/setup-ssh.js
 */
const { Client } = require('ssh2')
const fs = require('fs')
const path = require('path')
const os = require('os')

const HOST = '223.109.239.36'
const PORT = 28332
const USER = 'root'
const PASSWORD = 'rah7John'

const pubKeyPath = path.join(os.homedir(), '.ssh', 'id_rsa.pub')

if (!fs.existsSync(pubKeyPath)) {
  console.error('未找到 SSH 公钥，请先运行 ssh-keygen -t rsa -b 4096')
  process.exit(1)
}

const pubKey = fs.readFileSync(pubKeyPath, 'utf8').trim()
console.log(`公钥: ${pubKey.slice(0, 60)}...`)

const conn = new Client()

conn.on('ready', () => {
  console.log(`已连接到 ${USER}@${HOST}:${PORT}`)

  // 确保 .ssh 目录存在，权限正确
  const cmds = [
    'mkdir -p ~/.ssh',
    'chmod 700 ~/.ssh',
    `echo '${pubKey}' >> ~/.ssh/authorized_keys`,
    'chmod 600 ~/.ssh/authorized_keys',
    // 去重
    "sort -u ~/.ssh/authorized_keys -o ~/.ssh/authorized_keys",
    'echo "密钥添加成功"',
  ]

  conn.exec(cmds.join(' && '), (err, stream) => {
    if (err) throw err
    stream.on('data', (data) => process.stdout.write(data.toString()))
    stream.stderr.on('data', (data) => process.stderr.write(data.toString()))
    stream.on('close', () => {
      console.log('\n完成！现在可以免密登录了，测试一下：')
      console.log(`  ssh ${USER}@${HOST} -p ${PORT}`)
      conn.end()
    })
  })
})

conn.on('error', (err) => {
  console.error('连接失败:', err.message)
  process.exit(1)
})

conn.connect({
  host: HOST,
  port: PORT,
  username: USER,
  password: PASSWORD,
  readyTimeout: 10000,
})