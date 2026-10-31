// Canonical shared Academic/Schedule contracts. Offline by default; no backend calls.
// Use --service=academic to regenerate only the changed Academic contract.
import { readFile, writeFile } from 'node:fs/promises'
import openapiTS, { astToString } from 'openapi-typescript'
const checkOnly = process.argv.includes('--check')
const services = process.argv.includes('--service=academic')
  ? ['academic']
  : ['academic', 'schedule']
for (const service of services) {
  const specUrl = new URL(`../../../docs/openapi/${service}.json`, import.meta.url)
  const outputUrl = new URL(`../src/api/generated/${service}.types.ts`, import.meta.url)
  const spec = JSON.parse(await readFile(specUrl, 'utf8'))
  const ast = await openapiTS(spec, { immutable: false })
  // Preserve the original accepted snapshot header; the workspace script below is canonical.
  const header = `// AUTO-GENERATED — do not edit by hand.\n// Regenerate: npm run generate:types (requires backend running).\n// Source: docs/openapi/${service}.json (committed for CI drift-guard).\n\n`
  const generated = header + astToString(ast)
  if (checkOnly) {
    const current = await readFile(outputUrl, 'utf8')
    if (current.replace(/\r\n/g, '\n') !== generated.replace(/\r\n/g, '\n'))
      throw new Error(
        `Canonical ${service} types drifted; run npm run generate:academic-schedule-types in frontends`,
      )
    console.log(`Canonical ${service} snapshot matches docs/openapi/${service}.json`)
  } else {
    await writeFile(outputUrl, generated, 'utf8')
    console.log(`Generated canonical ${service} snapshot`)
  }
}
