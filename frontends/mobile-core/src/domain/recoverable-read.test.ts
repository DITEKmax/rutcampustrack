import { describe, expect, it } from 'vitest'
import { StudentApiError } from '../api/student-client'
import { isRecoverableReadFailure } from './recoverable-read'

describe('isRecoverableReadFailure', () => {
  it('allows a transport failure and expired session to recover a local schedule snapshot', () => {
    expect(isRecoverableReadFailure(new TypeError('Failed to fetch'))).toBe(true)
    expect(isRecoverableReadFailure(new StudentApiError(new Response(null, { status: 401 }), null))).toBe(true)
  })

  it('keeps a scope denial out of the cached offline projection', () => {
    expect(isRecoverableReadFailure(new StudentApiError(new Response(null, { status: 403 }), null))).toBe(false)
  })
})
