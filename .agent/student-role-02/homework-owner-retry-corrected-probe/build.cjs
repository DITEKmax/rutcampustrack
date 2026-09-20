const path = require('node:path')
const { createRequire } = require('node:module')
const taskFrontend = path.resolve(__dirname, '../../worktrees/student-role-02/homework-ui/frontends')
const taskRequire = createRequire(path.join(taskFrontend, 'package.json'))
taskRequire('esbuild').buildSync({
  entryPoints: [path.join(__dirname, 'probe.ts')],
  outfile: path.join(__dirname, 'probe.cjs'),
  bundle: true, platform: 'node', format: 'cjs',
  nodePaths: [path.join(taskFrontend, 'node_modules')],
})
