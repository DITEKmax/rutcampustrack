import { ProfileRequestError } from '../../mobile-core/src/features/profile/profile-types'
import type { StudentSession } from '../../mobile-core/src/api/types'
import type { ProfileSnapshot } from '../../mobile-core/src/features/profile/profile-types'

/**
 * Auth and BFF each expose the authority tuple. A student feature is mounted
 * only when both responses describe the same server session and role.
 */
export function assertAuthBffCoherence(profile: ProfileSnapshot, candidate: StudentSession): void {
  if (profile.activeRole !== 'STUDENT'
    || candidate.activeRole !== 'STUDENT'
    || candidate.user.id !== profile.userId
    || candidate.sessionId !== profile.sessionId
    || candidate.sessionVersion !== profile.sessionVersion
    || candidate.rolesVersion !== profile.rolesVersion
    || candidate.readOnly !== profile.readOnly) {
    throw new ProfileRequestError(
      'SESSION_STATE_STALE',
      'Auth и учебная сессия не совпадают. Повтори вход.',
      409,
      {
        auth: {
          sessionId: profile.sessionId,
          sessionVersion: profile.sessionVersion,
          rolesVersion: profile.rolesVersion,
          userId: profile.userId,
          activeRole: profile.activeRole,
          readOnly: profile.readOnly,
        },
        bff: {
          sessionId: candidate.sessionId,
          sessionVersion: candidate.sessionVersion,
          rolesVersion: candidate.rolesVersion,
          userId: candidate.user.id,
          activeRole: candidate.activeRole,
          readOnly: candidate.readOnly,
        },
      },
    )
  }
}
