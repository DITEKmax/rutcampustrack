import type { SemesterSnapshot, SnapshotCleanupResult } from '../../../mobile-core/src/offline/semester-snapshot'

export function createDemoSnapshots() {
  let snapshot: SemesterSnapshot | null = null
  const result = (
    operation: SnapshotCleanupResult['operation'],
    replacementOwnerId: string | null = null,
  ): SnapshotCleanupResult => ({
    operation, ownerId: snapshot?.ownerId ?? null, replacementOwnerId,
    idbDelete: { status: 'skipped' }, pointerCleanup: { status: 'skipped' },
    safeOffline: true, retryRequired: false,
  })
  return {
    async read(ownerId: string, scopeKey?: string) {
      return snapshot?.ownerId === ownerId && (!scopeKey || snapshot.scopeKey === scopeKey) ? snapshot : null
    },
    async readCurrent() { return snapshot },
    async write(value: SemesterSnapshot) { snapshot = value },
    async switchToDetailed(ownerId: string) {
      const outcome = result('switch', ownerId)
      if (snapshot?.ownerId !== ownerId) snapshot = null
      return outcome
    },
    async clearDetailed() {
      const outcome = result('clear')
      snapshot = null
      return outcome
    },
    async clearMismatchedCurrent(scopeKey: string) {
      if (!snapshot || snapshot.scopeKey === scopeKey) return null
      const outcome = result('clear')
      snapshot = null
      return outcome
    },
    invalidatePendingWrites() {},
    hasUnresolvedCleanup: () => false,
    async retryCleanup(outcome: SnapshotCleanupResult) { return outcome },
  }
}
