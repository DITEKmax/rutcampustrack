import type { StudentHomework, StudentHomeworkItem } from '../../api/types'

export interface HomeworkFocusRequest {
  readonly id: string
  readonly expectedCompleted: boolean
  readonly origin: Element | null
  readonly feedIdentity: string | null
  readonly ownerKey: string | null
}

export function homeworkFeedIdentity(feed: StudentHomework | null | undefined): string | null {
  if (!feed) return null
  return `${feed.semester.id}:${feed.from}:${feed.to}`
}

export function isHomeworkCompletionConfirmed(
  item: Pick<StudentHomeworkItem, 'completed' | 'completedAt'> | null | undefined,
  expectedCompleted: boolean,
): boolean {
  if (!item || item.completed !== expectedCompleted) return false
  if (!expectedCompleted) return item.completedAt === null
  return item.completedAt !== null && Number.isFinite(Date.parse(item.completedAt))
}

export function canRestoreHomeworkFocus(options: {
  request: HomeworkFocusRequest
  item: Pick<StudentHomeworkItem, 'completed' | 'completedAt'> | null | undefined
  pending: boolean
  activeElement: Element | null
  documentBody: Element | null
  feedIdentity: string | null
  ownerKey: string | null
}): boolean {
  const { request, item, pending, activeElement, documentBody, feedIdentity, ownerKey } = options
  if (pending || request.feedIdentity !== feedIdentity || request.ownerKey !== ownerKey) return false
  if (!isHomeworkCompletionConfirmed(item, request.expectedCompleted)) return false
  return activeElement === null || activeElement === documentBody || activeElement === request.origin
}
