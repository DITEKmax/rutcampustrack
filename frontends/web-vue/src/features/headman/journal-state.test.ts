import { describe, expect, it } from 'vitest'
import type { HeadmanJournalRosterEntry } from '../../../../mobile-core/src/features/headman-journal/headman-journal-client'
import type {
  HeadmanLessonAction,
  HeadmanLessonActionsPage,
} from '../../../../mobile-core/src/features/headman-journal/headman-journal-actions'
import { initialExcuseType, markReadbackMatches, LessonActionsReader } from './journal-state'
const entry: HeadmanJournalRosterEntry = {
  userId: 4,
  displayName: 'Студент',
  status: 'EXCUSED',
  symbol: 'у',
  markRevision: '2',
  approvedTicket: null,
  source: 'HEADMAN_MANUAL',
  excuseReason: null,
  excuseType: 'OTHER',
  comment: 'Ранее подтверждённая причина',
  attachmentId: null,
  attachmentName: null,
  attachmentContentType: null,
  attachmentSize: null,
  editable: true,
}
const action = (id: string): HeadmanLessonAction => ({
  id,
  occurredAt: '2026-10-24T09:00:00Z',
  action: 'MARK',
  source: null,
  outcome: 'APPLIED',
  errorCode: null,
  actor: null,
  student: null,
  before: null,
  after: null,
  geo: null,
  ticket: null,
  homework: null,
})
describe('headman journal recovery', () => {
  it('preserves OTHER when opening an existing excused mark and refuses an unknown reason', () => {
    expect(initialExcuseType(entry)).toBe('OTHER')
    expect(initialExcuseType({ ...entry, excuseType: 'FUTURE_REASON' })).toBe('')
  })
  it('does not confirm a lost edit from its unchanged EXCUSED status', () => {
    const command = {
      status: 'EXCUSED' as const,
      excuseType: 'OTHER' as const,
      comment: 'Новый комментарий',
    }
    expect(markReadbackMatches(entry, command)).toBe(false)
    expect(
      markReadbackMatches({ ...entry, comment: command.comment, markRevision: '3' }, command),
    ).toBe(true)
    expect(
      markReadbackMatches(
        { ...entry, comment: command.comment },
        { ...command, file: { name: 'Справка.pdf' } as File },
      ),
    ).toBe(false)
  })
  it('cannot append a delayed page from lesson A into the already opened lesson B', async () => {
    let finishA!: (value: HeadmanLessonActionsPage) => void
    const delayed = new Promise<HeadmanLessonActionsPage>((resolve) => {
      finishA = resolve
    })
    const reader = new LessonActionsReader({
      getLessonActions: (id) =>
        id === 1 ? delayed : Promise.resolve({ items: [action('B-first')], nextCursor: null }),
    })
    const oldMore = reader.read(1, 'A-next', [action('A-first')])
    const opened = await reader.read(2, null)
    finishA({ items: [action('A-second')], nextCursor: null })
    expect(await oldMore).toBeNull()
    expect(opened?.lessonId).toBe(2)
    expect(opened?.items.map((item) => item.id)).toEqual(['B-first'])
  })
})
