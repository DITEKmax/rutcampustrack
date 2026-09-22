import { describe, expect, it, vi } from 'vitest'
import type { ProfileRole } from '../../mobile-core/src/features/profile/profile-types'
import { createPwaRoleSelection, createPwaStudentRoleSelection } from './role-flow'

describe('PWA student role flow', () => {
  it('does not create Auth PUT input for non-student grants', () => {
    const authPut = vi.fn()
    const roles: ProfileRole[] = ['TEACHER', 'HEADMAN', 'ADMIN']

    for (const role of roles) {
      const request = createPwaStudentRoleSelection(role, '7')
      expect(request).toBeNull()
      if (request) authPut(request)
    }

    expect(authPut).not.toHaveBeenCalled()
  })

  it('creates the STUDENT Auth PUT input with the current session version', () => {
    const authPut = vi.fn()
    const request = createPwaStudentRoleSelection('STUDENT', '7')
    if (request) authPut(request)

    expect(authPut).toHaveBeenCalledOnce()
    expect(authPut).toHaveBeenCalledWith({ role: 'STUDENT', expectedSessionVersion: '7' })
  })

  it('admits the ADMIN Auth PUT input with the current session version', () => {
    expect(createPwaRoleSelection('ADMIN', '7')).toEqual({
      role: 'ADMIN',
      expectedSessionVersion: '7',
    })
  })
})
