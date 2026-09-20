import { describe, expect, it } from 'vitest'
import type { StudentSession } from '../../mobile-core/src/api/types'
import type { ProfileSnapshot } from '../../mobile-core/src/features/profile/profile-types'
import { assertAuthBffCoherence } from './session-coherence'

const profile: ProfileSnapshot = {
  sessionId: '00000000-0000-4000-8000-000000000001',
  userId: '42',
  displayName: 'Анна Смирнова',
  sessionVersion: '3',
  rolesVersion: '4',
  activeRole: 'STUDENT',
  roles: [{ grantId: '17', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false }],
  readOnly: false,
  passwordPolicy: {
    minCodePoints: 12,
    maxUtf8Bytes: 72,
    requiresDecimalDigit: true,
    specialCategories: ['P', 'S'],
    normalization: 'NONE',
  },
}

const candidate: StudentSession = {
  user: { id: '42', displayName: 'Анна Смирнова' },
  activeRole: 'STUDENT',
  sessionId: profile.sessionId,
  sessionVersion: profile.sessionVersion,
  rolesVersion: profile.rolesVersion,
  readOnly: false,
  group: null,
  semester: null,
  capabilities: ['TODAY'],
  serverNow: '2026-09-11T08:00:00Z',
  _links: {},
}

describe('Auth/BFF session coherence', () => {
  it('accepts an identical authority tuple', () => {
    expect(() => assertAuthBffCoherence(profile, candidate)).not.toThrow()
  })

  it.each([
    ['session id', { sessionId: '00000000-0000-4000-8000-000000000002' }],
    ['session version', { sessionVersion: '5' }],
    ['roles version', { rolesVersion: '6' }],
    ['user', { user: { id: '43', displayName: 'Анна Смирнова' } }],
    ['readonly', { readOnly: true }],
  ])('rejects a mismatched %s before feature mounting', (_label, change) => {
    const next = { ...candidate, ...change } as StudentSession
    try {
      assertAuthBffCoherence(profile, next)
      throw new Error('expected a coherence error')
    } catch (error) {
      expect(error).toMatchObject({
        name: 'ProfileRequestError',
        code: 'SESSION_STATE_STALE',
        status: 409,
      })
    }
  })
})
