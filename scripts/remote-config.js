/**
 * 远程服务器配置
 */
const fs = require('fs')
const path = require('path')
const os = require('os')

const ROOT = path.resolve(__dirname, '..')
const REMOTE_ROOT = '/root/html-builder'
const PRIVATE_KEY = fs.readFileSync(path.join(os.homedir(), '.ssh', 'id_rsa'))

const SERVERS = {
  'gpu-5090': {
    host: '223.109.239.36',
    port: 28332,
    username: 'root',
    privateKey: PRIVATE_KEY,
    readyTimeout: 10000,
    description: 'RTX 5090 32GB / CUDA 13.2 / Ubuntu 24.04',
  },
  'gpu-v100': {
    host: '223.109.239.32',
    port: 13936,
    username: 'root',
    privateKey: PRIVATE_KEY,
    readyTimeout: 10000,
    description: 'Tesla V100 32GB / Ubuntu 22.04',
  },
}

const DEFAULT_SERVER = 'gpu-5090'

function getServerConfig(name) {
  const key = name || DEFAULT_SERVER
  if (!SERVERS[key]) {
    console.error(`未知服务器: ${key}，可选: ${Object.keys(SERVERS).join(', ')}`)
    process.exit(1)
  }
  return { ...SERVERS[key], name: key }
}

module.exports = { ROOT, REMOTE_ROOT, SERVERS, DEFAULT_SERVER, getServerConfig }