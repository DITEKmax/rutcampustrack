export interface JournalMutationScope {
  readonly api: object
  readonly groupId: number | null
  readonly lessonId: number
  readonly generation: number
}

/** A confirmation and an awaited ACK belong to exactly one account/group/lesson context. */
export function journalScopeCurrent(captured: JournalMutationScope, current: JournalMutationScope | null): boolean {
  return current !== null && captured.api === current.api && captured.groupId === current.groupId
    && captured.lessonId === current.lessonId && captured.generation === current.generation
}
