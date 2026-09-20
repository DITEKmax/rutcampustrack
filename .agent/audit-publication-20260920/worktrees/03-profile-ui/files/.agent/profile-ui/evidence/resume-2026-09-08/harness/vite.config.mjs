import { defineConfig } from 'file:///C:/Users/maksd/.codex/worktrees/d650/rutcampustrack/frontends/node_modules/vite/dist/node/index.js'
import vue from 'file:///C:/Users/maksd/.codex/worktrees/d650/rutcampustrack/frontends/node_modules/@vitejs/plugin-vue/dist/index.mjs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const harnessRoot = fileURLToPath(new URL('.', import.meta.url))
const productRoot = 'C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/.agent/worktrees/profile-ui'
const dependenciesRoot = 'C:/Users/maksd/.codex/worktrees/d650/rutcampustrack/frontends/node_modules'

export default defineConfig({
  root: harnessRoot,
  plugins: [vue()],
  resolve: {
    alias: {
      vue: path.join(dependenciesRoot, 'vue'),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 18110,
    strictPort: true,
    fs: {
      allow: [harnessRoot, productRoot, dependenciesRoot],
    },
  },
})
