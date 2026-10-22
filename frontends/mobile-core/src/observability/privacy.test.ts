import assert from 'node:assert/strict'
import { describe, it } from 'vitest'
import { safeErrorEvent } from './privacy'

describe('error export privacy', () => {
  it('drops secret and PII fields while retaining repeat-grouping stack and release', () => {
    const marker = 'synthetic-private@example.invalid'
    const event = {
      event_id: 'a'.repeat(32),
      release: '26cbe5912f6683a81780a831609dbddbc26feba4',
      environment: 'isolated-ci',
      message: marker,
      user: { email: marker },
      request: { headers: { authorization: marker }, data: marker, url: marker },
      breadcrumbs: [{ message: marker }],
      contexts: { secret: marker },
      extra: { password: marker },
      exception: {
        values: [
          {
            type: 'TypeError',
            value: marker,
            stacktrace: {
              frames: [
                {
                  filename: 'https://example.invalid/assets/app-a1.js?token=' + marker,
                  function: 'renderReport',
                  lineno: 12,
                  colno: 4,
                  vars: { marker },
                  pre_context: [marker],
                },
              ],
            },
          },
        ],
      },
    }
    const safe = safeErrorEvent(event)
    assert.ok(safe)
    assert.ok(!JSON.stringify(safe).includes(marker))
    assert.equal(safe.release, event.release)
    assert.equal(safe.exception?.values?.[0]?.type, 'TypeError')
    assert.equal(safe.exception?.values?.[0]?.stacktrace?.frames?.[0]?.function, 'renderReport')
    assert.equal(safe.exception?.values?.[0]?.stacktrace?.frames?.[0]?.lineno, 12)
    assert.deepEqual(safeErrorEvent(event), safe)
  })
  it('drops message-only events and all untrusted URL/path contents', () => {
    assert.equal(safeErrorEvent({}), null)
    const safe = safeErrorEvent({
      exception: {
        values: [
          {
            type: 'Error',
            stacktrace: {
              frames: [
                {
                  filename: '/secret/private@example.invalid',
                  function: 'password=private@example.invalid',
                },
              ],
            },
          },
        ],
      },
    })
    assert.ok(!JSON.stringify(safe).includes('private@example.invalid'))
  })
})
