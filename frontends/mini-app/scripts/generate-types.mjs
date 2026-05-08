// Generate src/api/generated/{service}.types.ts from backend /api-docs.
// M19 G2 — Mini App joins the same OpenAPI drift-guard flow as PWA.
//
// Usage:
//   node scripts/generate-types.mjs
//   node scripts/generate-types.mjs --offline

import { writeFile, mkdir, readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import openapiTS, { astToString } from 'openapi-typescript'

const __dirname = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(__dirname, '../../..')
const miniAppRoot = resolve(__dirname, '..')

const offline = process.argv.includes('--offline')

const services = [
  { name: 'auth', port: 9090 },
  { name: 'academic', port: 9091 },
  { name: 'schedule', port: 9092 },
  { name: 'attendance', port: 9093 },
]

const openapiDir = resolve(repoRoot, 'docs/openapi')
const genDir = resolve(miniAppRoot, 'src/api/generated')

await mkdir(openapiDir, { recursive: true })
await mkdir(genDir, { recursive: true })

for (const svc of services) {
  const jsonPath = resolve(openapiDir, `${svc.name}.json`)
  let spec

  if (offline) {
    if (!existsSync(jsonPath)) {
      console.error(`[generate-types] ${svc.name}: offline mode requires ${jsonPath}`)
      process.exit(1)
    }
    spec = JSON.parse(await readFile(jsonPath, 'utf8'))
    console.log(`[generate-types] ${svc.name}: offline (${jsonPath})`)
  } else {
    const url = `http://localhost:${svc.port}/api-docs`
    console.log(`[generate-types] ${svc.name}: fetching ${url}`)
    const res = await fetch(url)
    if (!res.ok) {
      console.error(`[generate-types] ${svc.name}: HTTP ${res.status} from ${url}`)
      process.exit(1)
    }
    spec = await res.json()
    const pretty = JSON.stringify(spec, null, 2)
    await writeFile(jsonPath, pretty + '\n')
    console.log(`[generate-types] ${svc.name}: saved ${jsonPath} (${pretty.length} B)`)
  }

  const ast = await openapiTS(spec, { immutable: false })
  const ts = astToString(ast)
  const tsPath = resolve(genDir, `${svc.name}.types.ts`)
  const header = `// AUTO-GENERATED — do not edit by hand.\n// Regenerate: npm run generate:types (requires backend running).\n// Source: docs/openapi/${svc.name}.json (committed for CI drift-guard).\n\n`
  await writeFile(tsPath, header + ts)
  console.log(`[generate-types] ${svc.name}: wrote ${tsPath} (${(header + ts).length} B)`)
}

console.log('[generate-types] done')
