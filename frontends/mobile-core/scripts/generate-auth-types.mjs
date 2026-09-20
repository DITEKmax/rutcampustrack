import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import openapiTS, { astToString } from 'openapi-typescript'

const specUrl = new URL('../../../docs/openapi/auth.json', import.meta.url)
const outputUrl = new URL('../src/api/generated/auth.ts', import.meta.url)
const checkOnly = process.argv.includes('--check')

const specText = await readFile(specUrl, 'utf8')
const specHash = createHash('sha256').update(specText).digest('hex')
const ast = await openapiTS(JSON.parse(specText), {
  alphabetize: true,
  pathParamsAsTypes: true,
})
const generated = [
  '/* eslint-disable */',
  '// Generated from docs/openapi/auth.json. Do not edit.',
  '// Contract: Auth session/role; SHA-256: ' + specHash,
  '',
  astToString(ast).trimEnd(),
  '',
].join('\n')

if (checkOnly) {
  let current
  try {
    current = await readFile(outputUrl, 'utf8')
  } catch {
    throw new Error('Generated file is missing: ' + fileURLToPath(outputUrl))
  }
  if (current !== generated) {
    throw new Error('Generated Auth types drifted; run npm run generate:auth-types')
  }
  console.log('Auth OpenAPI types match (' + specHash + ')')
} else {
  await mkdir(new URL('../src/api/generated/', import.meta.url), { recursive: true })
  await writeFile(outputUrl, generated, 'utf8')
  console.log('Generated ' + fileURLToPath(outputUrl) + ' (' + specHash + ')')
}
