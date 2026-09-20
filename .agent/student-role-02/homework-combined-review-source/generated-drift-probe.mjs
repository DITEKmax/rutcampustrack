import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { createRequire } from 'node:module'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
const root = path.resolve(process.argv[2])
const require = createRequire(path.join(root, 'frontends/package.json'))
const entry = path.join(path.dirname(require.resolve('openapi-typescript')), 'index.mjs')
const { default: openapiTS, astToString } = await import(pathToFileURL(entry).href)
const spec = await readFile(path.join(root, 'docs/openapi/mobile-bff.json'), 'utf8')
const current = await readFile(path.join(root, 'frontends/mobile-core/src/api/generated/mobile-bff.ts'), 'utf8')
const hash = createHash('sha256').update(spec).digest('hex')
const ast = await openapiTS(JSON.parse(spec), { alphabetize: true, pathParamsAsTypes: true })
const generated = ['/* eslint-disable */', '// Generated from docs/openapi/mobile-bff.json. Do not edit.', '// Contract: JS-STUDENT-01-r1; SHA-256: ' + hash, '', astToString(ast).trimEnd(), ''].join('\n')
const result = { sourceRoot: root, exactEqual: current === generated, normalizedLineEndingsEqual: current.replace(/\r\n/g, '\n') === generated, currentHasCrlf: current.includes('\r\n'), specHash: hash }
await writeFile(new URL('./generated-drift-result.json', import.meta.url), JSON.stringify(result, null, 2))
console.log(JSON.stringify(result))
if (!result.normalizedLineEndingsEqual) process.exitCode = 1
