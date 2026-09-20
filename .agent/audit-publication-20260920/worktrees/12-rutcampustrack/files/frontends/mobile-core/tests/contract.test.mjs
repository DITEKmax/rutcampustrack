import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  loadContract,
  validateAllFixtures,
  validateFixture,
} from '../scripts/validate-fixtures.mjs'

test('all committed examples conform to the exported OpenAPI contract', async () => {
  assert.deepEqual(await validateAllFixtures(), [])
})

test('coordinates cannot be omitted or replaced by an unavailable sentinel', async () => {
  const fixture = JSON.parse(
    await readFile(
      new URL('../fixtures/checkin-coordinates.json', import.meta.url),
      'utf8',
    ),
  )
  delete fixture.geo.latitude
  assert.match(
    await validateFixture('StudentCheckinCommand', fixture),
    /latitude: required/,
  )

  fixture.geo.latitude = null
  assert.match(
    await validateFixture('StudentCheckinCommand', fixture),
    /null is not allowed/,
  )
})

test('geo discriminator rejects a payload from the other input shape', async () => {
  const fixture = JSON.parse(
    await readFile(
      new URL('../fixtures/checkin-unavailable.json', import.meta.url),
      'utf8',
    ),
  )
  fixture.geo.kind = 'COORDINATES'
  assert.match(
    await validateFixture('StudentCheckinCommand', fixture),
    /latitude: required/,
  )
})

test('homework timestamps stay required nullable in OpenAPI and generated TypeScript', async () => {
  const { root } = await loadContract()
  const generated = await readFile(
    new URL('../src/api/generated/mobile-bff.ts', import.meta.url),
    'utf8',
  )

  for (const schemaName of ['StudentHomeworkItem', 'StudentHomeworkCompletion']) {
    const schema = root.components.schemas[schemaName]
    assert.ok(schema, `Missing OpenAPI schema: ${schemaName}`)
    assert.ok(schema.required.includes('completedAt'))
    assert.deepEqual(schema.properties.completedAt, {
      type: 'string',
      format: 'date-time',
      nullable: true,
    })

    const start = generated.indexOf(`${schemaName}: {`)
    const end = generated.indexOf('\n        };', start)
    assert.notEqual(start, -1, `Missing generated schema: ${schemaName}`)
    assert.notEqual(end, -1, `Unterminated generated schema: ${schemaName}`)
    assert.match(
      generated.slice(start, end),
      /completedAt: string \| null;/,
    )
  }

  assert.equal(
    await validateFixture('StudentHomeworkCompletion', {
      id: '142',
      completed: true,
      completedAt: '2026-09-07T09:30:00Z',
    }),
    null,
  )
  assert.equal(
    await validateFixture('StudentHomeworkCompletion', {
      id: '848636',
      completed: false,
      completedAt: null,
    }),
    null,
  )
})
