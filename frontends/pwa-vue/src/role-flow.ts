import type { ProfileRole } from '../../mobile-core/src/features/profile/profile-types'

export interface PwaStudentRoleSelectionRequest {
  readonly role: 'STUDENT'
  readonly expectedSessionVersion: string
}

export interface PwaHeadmanRoleSelectionRequest {
  readonly role: 'HEADMAN'
  readonly expectedSessionVersion: string
}

export type PwaRoleSelectionRequest = PwaStudentRoleSelectionRequest | PwaHeadmanRoleSelectionRequest

export function createPwaRoleSelection(
  role: ProfileRole,
  expectedSessionVersion: string,
): PwaRoleSelectionRequest | null {
  if (role !== 'STUDENT' && role !== 'HEADMAN') return null
  return { role, expectedSessionVersion }
}

/** The PWA owns only the STUDENT surface; other selectable grants stay inert. */
export function createPwaStudentRoleSelection(
  role: ProfileRole,
  expectedSessionVersion: string,
): PwaStudentRoleSelectionRequest | null {
  const request = createPwaRoleSelection(role, expectedSessionVersion)
  return request?.role === 'STUDENT' ? request : null
}

export function createPwaHeadmanRoleSelection(
  role: ProfileRole,
  expectedSessionVersion: string,
): PwaHeadmanRoleSelectionRequest | null {
  const request = createPwaRoleSelection(role, expectedSessionVersion)
  return request?.role === 'HEADMAN' ? request : null
}
