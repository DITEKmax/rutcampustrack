import type {
  HeadmanJournalExcuseType,
  HeadmanJournalMarkCommand,
  HeadmanJournalRosterEntry,
} from '../../../../mobile-core/src/features/headman-journal/headman-journal-client'
import type {
  HeadmanLessonAction,
  HeadmanLessonActionsPage,
} from '../../../../mobile-core/src/features/headman-journal/headman-journal-actions'

const excuseTypes: readonly HeadmanJournalExcuseType[] = [
  'ILLNESS',
  'MEDICAL_EXAMINATION',
  'COMPETITION_PARTICIPATION',
  'FAMILY_CIRCUMSTANCES',
  'SUMMONS',
  'UNIVERSITY_ORDER',
  'EXEMPTION',
  'OTHER',
]
export function initialExcuseType(
  entry: Pick<HeadmanJournalRosterEntry, 'status' | 'excuseType'>,
): HeadmanJournalExcuseType | '' {
  if (entry.status !== 'EXCUSED') return 'ILLNESS'
  return excuseTypes.find((type) => type === entry.excuseType) ?? ''
}

/** A matching status alone cannot confirm an edited reason, comment or newly uploaded file. */
export function markReadbackMatches(
  entry: HeadmanJournalRosterEntry | undefined,
  command: HeadmanJournalMarkCommand | null,
): boolean {
  if (!entry || entry.status !== (command?.status ?? 'EMPTY')) return false
  if (command?.status !== 'EXCUSED') return true
  if (command.file) return false // The report exposes no digest proving the selected file bytes.
  return (
    entry.excuseType === command.excuseType && (entry.comment || null) === (command.comment || null)
  )
}

export class LessonActionsReader {
  private revision = 0
  constructor(
    private readonly api: {
      getLessonActions(id: number, cursor: string | null): Promise<HeadmanLessonActionsPage>
    },
  ) {}
  async read(id: number, cursor: string | null, previous: readonly HeadmanLessonAction[] = []) {
    const revision = ++this.revision
    const page = await this.api.getLessonActions(id, cursor)
    if (revision !== this.revision) return null
    return { lessonId: id, items: [...previous, ...page.items], cursor: page.nextCursor }
  }
}
