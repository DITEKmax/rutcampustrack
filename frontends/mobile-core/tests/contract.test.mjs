import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
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
