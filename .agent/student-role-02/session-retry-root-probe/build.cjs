const path = require('node:path')
const { createRequire } = require('node:module')
const frontend = path.resolve(__dirname, '../../worktrees/student-role-02/homework-ui/frontends')
createRequire(path.join(frontend, 'package.json'))('esbuild').buildSync({
  entryPoints: [path.join(__dirname, 'probe.ts')], outfile: path.join(__dirname, 'probe.cjs'),
  bundle: true, platform: 'node', format: 'cjs', nodePaths: [path.join(frontend, 'node_modules')],
})
