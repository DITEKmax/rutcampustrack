import { createServer } from 'file:///C:/Users/maksd/.codex/worktrees/d650/rutcampustrack/frontends/node_modules/vite/dist/node/index.js'
import config from './vite.config.mjs'

const server = await createServer({ ...config, configFile: false })
console.log(JSON.stringify({ pid: process.pid, host: '127.0.0.1', port: 18110, strictPort: true }))
await server.listen()

const shutdown = async () => {
  await server.close()
  process.exit(0)
}

process.once('SIGINT', shutdown)
process.once('SIGTERM', shutdown)
