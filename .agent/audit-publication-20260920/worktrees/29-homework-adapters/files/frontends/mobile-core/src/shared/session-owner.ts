import { StudentApi } from '../api/student-client'
import type { StudentSession } from '../api/types'

/** A request that outlives its authenticated owner must fail closed. */
export class StaleSessionGenerationError extends Error {
  constructor() {
    super('Authenticated session generation is no longer current')
    this.name = 'StaleSessionGenerationError'
  }
}

export interface StudentApiGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

/**
 * Captures the generation at client creation. StudentApi retries a 401 after
 * onUnauthorized; both the refresh callback and the retry token read are
 * therefore guarded before the transport can be called a second time.
 */
export function createGenerationBoundStudentApi(
  owner: StudentApiGenerationOwner,
  fetcher?: typeof fetch,
): StudentApi {
  const generation = owner.currentGeneration()
  return new StudentApi({
    accessToken: () => {
      assertCurrent(owner, generation)
      return owner.accessTokenFor(generation)
    },
    onUnauthorized: async () => {
      assertCurrent(owner, generation)
      await owner.refreshFor(generation)
      assertCurrent(owner, generation)
    },
    ...(fetcher ? { fetcher } : {}),
  })
}

/**
 * The accepted session has nullable group/semester fields. Keeping those
 * dimensions explicit lets callers disable Homework without inventing a
 * capability or a role grant.
 */
export interface StudentFeatureScope {
  userId: string | null
  activeRole: string | null
  groupId: string | null
  semesterId: string | null
  resetGeneration: number
}

export function studentFeatureScope(session: StudentSession, resetGeneration: number): StudentFeatureScope {
  return {
    userId: session.user.id,
    activeRole: session.activeRole,
    groupId: session.group?.id ?? null,
    semesterId: session.semester?.id ?? null,
    resetGeneration,
  }
}

/** In-memory owner identity used by query keys, focus and late callbacks. */
export function studentFeatureScopeIdentity(scope: StudentFeatureScope): string {
  return JSON.stringify([
    scope.userId,
    scope.activeRole,
    scope.groupId,
    scope.semesterId,
    scope.resetGeneration,
  ])
}

/**
 * Persistent read models deliberately use a stable policy marker instead of
 * the in-memory generation number, so an offline warm reload can read the
 * same authorized student/role/group/semester partition. The runtime owner
 * key still includes resetGeneration via studentFeatureScopeIdentity().
 */
export function studentOfflineScopeKey(scope: StudentFeatureScope): string {
  return JSON.stringify([
    'student-read-model-v1',
    scope.userId,
    scope.activeRole,
    scope.groupId,
    scope.semesterId,
  ])
}

function assertCurrent(owner: StudentApiGenerationOwner, generation: number): void {
  if (owner.currentGeneration() !== generation) throw new StaleSessionGenerationError()
}
