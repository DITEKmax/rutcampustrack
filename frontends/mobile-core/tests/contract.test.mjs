import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'
import { fileURLToPath } from 'node:url'
import {
  loadContract,
  validateAllFixtures,
  validateFixture,
} from '../scripts/validate-fixtures.mjs'

test('all committed examples conform to the exported OpenAPI contract', async () => {
  assert.deepEqual(await validateAllFixtures(), [])
})

test('generated BFF and Auth hashes identify canonical LF OpenAPI source', async () => {
  for (const service of ['mobile-bff', 'auth']) {
    const specText = await readFile(
      new URL(`../../../docs/openapi/${service}.json`, import.meta.url),
      'utf8',
    )
    const canonicalHash = createHash('sha256').update(specText.replace(/\r\n/g, '\n')).digest('hex')
    const generated = await readFile(
      new URL(`../src/api/generated/${service}.ts`, import.meta.url),
      'utf8',
    )
    assert.match(generated, new RegExp(`SHA-256: ${canonicalHash}(?:\\r?\\n|$)`))
  }
})

test('coordinates cannot be omitted or replaced by an unavailable sentinel', async () => {
  const fixture = JSON.parse(
    await readFile(new URL('../fixtures/checkin-coordinates.json', import.meta.url), 'utf8'),
  )
  delete fixture.geo.latitude
  assert.match(await validateFixture('StudentCheckinCommand', fixture), /latitude: required/)

  fixture.geo.latitude = null
  assert.match(await validateFixture('StudentCheckinCommand', fixture), /null is not allowed/)
})

test('geo discriminator rejects a payload from the other input shape', async () => {
  const fixture = JSON.parse(
    await readFile(new URL('../fixtures/checkin-unavailable.json', import.meta.url), 'utf8'),
  )
  fixture.geo.kind = 'COORDINATES'
  assert.match(await validateFixture('StudentCheckinCommand', fixture), /latitude: required/)
})

test('homework timestamps stay required nullable in OpenAPI and generated TypeScript', async () => {
  const { root } = await loadContract()
  const generatedPath = fileURLToPath(new URL('../src/api/generated/mobile-bff.ts', import.meta.url))
  const program = ts.createProgram([generatedPath], { strictNullChecks: true, noEmit: true, skipLibCheck: true })
  const checker = program.getTypeChecker()
  const source = program.getSourceFile(generatedPath)
  const declaration = source.statements.find(node => ts.isInterfaceDeclaration(node) && node.name.text === 'components')
  const components = checker.getTypeAtLocation(declaration)
  const schemasSymbol = components.getProperty('schemas')
  const schemas = checker.getTypeOfSymbolAtLocation(schemasSymbol, declaration)
  for (const schemaName of ['StudentHomeworkItem', 'StudentHomeworkCompletion']) {
    const schema = root.components.schemas[schemaName]
    assert.ok(schema)
    const fixture = schemaName === 'StudentHomeworkCompletion'
      ? { id: '142', completed: true }
      : JSON.parse(await readFile(new URL('../fixtures/homework-feed.json', import.meta.url), 'utf8')).items[0]
    for (const completedAt of ['2026-09-07T09:30:00Z', null])
      assert.equal(await validateFixture(schemaName, { ...fixture, completedAt }), null)
    const missing = { ...fixture }
    delete missing.completedAt
    assert.match(await validateFixture(schemaName, missing), /completedAt: required/)
    const symbol = schemas.getProperty(schemaName)
    const model = checker.getTypeOfSymbolAtLocation(symbol, declaration)
    const property = model.getProperty('completedAt')
    assert.ok(property)
    assert.equal(property.flags & ts.SymbolFlags.Optional, 0)
    const type = checker.getTypeOfSymbolAtLocation(property, declaration)
    assert.ok(type.isUnion())
    assert.deepEqual(type.types.map(member => member.flags).sort((a, b) => a - b), [ts.TypeFlags.String, ts.TypeFlags.Null].sort((a, b) => a - b))
    assert.equal(type.types.some(member => member.flags & ts.TypeFlags.Undefined), false)
  }
})
