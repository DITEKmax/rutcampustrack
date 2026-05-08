import { useCallback, useEffect, useMemo, useReducer, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, ChartBar, WarningCircle } from '@phosphor-icons/react'
import { motion } from 'motion/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  useGroupSubjects,
  useJournal,
  useResolveThreshold,
} from '@/features/headman/shared/headmanApi'
import type {
  JournalCell,
  Subject,
  SubjectType,
} from '@/features/headman/shared/types'
import { SubjectStatsCard } from './SubjectStatsCard'

const DEFAULT_THRESHOLD = 75

interface StudentStatsRow {
  studentId: number
  studentName: string
  attendancePercent: number
}

interface SubjectStats {
  subjectId: number
  subjectName: string
  subjectType?: SubjectType
  groupPercent: number
  threshold: number
  studentRows: StudentStatsRow[]
  isRedZone: boolean
}

type StatsAction =
  | { type: 'set'; subjectId: number; stats: SubjectStats }
  | { type: 'prune'; keep: Set<number> }

function computeSemesterStart(): string {
  const now = new Date()
  const year = now.getMonth() >= 8 ? now.getFullYear() : now.getFullYear() - 1
  return `${year}-09-01`
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

function computeStudentStats(cells: JournalCell[]): {
  studentRows: StudentStatsRow[]
  groupPercent: number
} {
  const byStudent = new Map<number, { studentName: string; presentLike: number; absent: number }>()
  for (const cell of cells) {
    if (cell.status === 'cancelled') continue
    const entry =
      byStudent.get(cell.studentId) ?? {
        studentName: cell.studentName,
        presentLike: 0,
        absent: 0,
      }
    if (
      cell.status === 'present' ||
      cell.status === 'excused' ||
      cell.status === 'free_attendance'
    ) {
      entry.presentLike += 1
    } else if (cell.status === 'absent') {
      entry.absent += 1
    }
    byStudent.set(cell.studentId, entry)
  }

  const studentRows = Array.from(byStudent.entries()).map(([studentId, value]) => {
    const denominator = value.presentLike + value.absent
    const attendancePercent = denominator === 0 ? 100 : (value.presentLike / denominator) * 100
    return {
      studentId,
      studentName: value.studentName,
      attendancePercent,
    }
  })

  if (studentRows.length === 0) {
    return { studentRows, groupPercent: 100 }
  }

  return {
    studentRows,
    groupPercent:
      studentRows.reduce((sum, row) => sum + row.attendancePercent, 0) /
      studentRows.length,
  }
}

function statsReducer(
  state: Record<number, SubjectStats>,
  action: StatsAction,
): Record<number, SubjectStats> {
  switch (action.type) {
    case 'set': {
      const previous = state[action.subjectId]
      if (
        previous &&
        previous.groupPercent === action.stats.groupPercent &&
        previous.threshold === action.stats.threshold &&
        previous.isRedZone === action.stats.isRedZone &&
        previous.studentRows.length === action.stats.studentRows.length &&
        previous.subjectName === action.stats.subjectName &&
        previous.subjectType === action.stats.subjectType
      ) {
        return state
      }
      return { ...state, [action.subjectId]: action.stats }
    }
    case 'prune': {
      const next: Record<number, SubjectStats> = {}
      let changed = false
      for (const [key, value] of Object.entries(state)) {
        if (action.keep.has(Number(key))) next[Number(key)] = value
        else changed = true
      }
      return changed ? next : state
    }
    default:
      return state
  }
}

function sortSubjectsBySeverity(list: SubjectStats[]): SubjectStats[] {
  return [...list].sort((left, right) => {
    if (left.isRedZone && !right.isRedZone) return -1
    if (!left.isRedZone && right.isRedZone) return 1
    if (left.isRedZone && right.isRedZone) return left.groupPercent - right.groupPercent
    return left.subjectName.localeCompare(right.subjectName, 'ru')
  })
}

function SubjectStatsCollector({
  subject,
  groupId,
  dateFrom,
  dateTo,
  onStats,
}: {
  subject: Subject
  groupId: number
  dateFrom: string
  dateTo: string
  onStats: (stats: SubjectStats) => void
}) {
  const journal = useJournal(groupId, subject.id, dateFrom, dateTo)
  const thresholdQuery = useResolveThreshold(groupId, subject.id)
  const threshold = thresholdQuery.data?.minPercentage ?? DEFAULT_THRESHOLD
  const { studentRows, groupPercent } = useMemo(
    () => computeStudentStats(journal.data ?? []),
    [journal.data],
  )

  useEffect(() => {
    onStats({
      subjectId: subject.id,
      subjectName: subject.name,
      subjectType: subject.type,
      groupPercent,
      threshold,
      studentRows,
      isRedZone: groupPercent < threshold,
    })
  }, [groupPercent, onStats, studentRows, subject.id, subject.name, subject.type, threshold])

  return null
}

export function HeadmanStatsPage() {
  const { user } = useAuth()
  const groupId = user?.groupId
  const isHeadman = user?.role === 'STUDENT' && user.isHeadman
  const subjectsQuery = useGroupSubjects(isHeadman)
  const subjects = subjectsQuery.data ?? []
  const semesterStart = useMemo(computeSemesterStart, [])
  const semesterEnd = useMemo(todayIso, [])
  const [statsBySubject, dispatch] = useReducer(statsReducer, {})

  useEffect(() => {
    dispatch({ type: 'prune', keep: new Set(subjects.map((subject) => subject.id)) })
  }, [subjects])

  const handleStats = useCallback((stats: SubjectStats) => {
    dispatch({ type: 'set', subjectId: stats.subjectId, stats })
  }, [])

  const sortedStats = sortSubjectsBySeverity(
    subjects
      .map((subject) => statsBySubject[subject.id])
      .filter((stats): stats is SubjectStats => Boolean(stats)),
  )

  if (!isHeadman) {
    return (
      <PageShell>
        <BackLink />
        <InlineState text="Раздел доступен только старосте." />
      </PageShell>
    )
  }

  if (!groupId) {
    return (
      <PageShell>
        <BackLink />
        <InlineState text="Группа не назначена." />
      </PageShell>
    )
  }

  return (
    <PageShell>
      <BackLink />

      <header className="mb-5 flex items-start gap-3">
        <div
          className="grid size-11 shrink-0 place-items-center rounded-lg border"
          style={{
            background: 'color-mix(in oklab, var(--accent-secondary) 13%, transparent)',
            borderColor: 'color-mix(in oklab, var(--accent-secondary) 35%, transparent)',
            color: 'var(--accent-secondary)',
          }}
        >
          <ChartBar size={24} weight="duotone" aria-hidden="true" />
        </div>
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase" style={{ color: 'var(--text-muted)' }}>
            Староста
          </p>
          <h1
            className="text-2xl font-bold leading-tight text-balance"
            style={{ fontFamily: 'var(--font-heading)' }}
          >
            Статистика
          </h1>
        </div>
      </header>

      {subjects.map((subject) => (
        <SubjectStatsCollector
          key={subject.id}
          subject={subject}
          groupId={groupId}
          dateFrom={semesterStart}
          dateTo={semesterEnd}
          onStats={handleStats}
        />
      ))}

      {subjectsQuery.isLoading && <SkeletonList />}

      {!subjectsQuery.isLoading && subjectsQuery.isError && (
        <InlineError text="Не удалось загрузить предметы группы. Обновите страницу." />
      )}

      {!subjectsQuery.isLoading && !subjectsQuery.isError && subjects.length === 0 && (
        <EmptyState text="Группе не назначены предметы." />
      )}

      {!subjectsQuery.isLoading &&
        !subjectsQuery.isError &&
        subjects.length > 0 &&
        sortedStats.length < subjects.length && <SkeletonList />}

      {!subjectsQuery.isLoading && !subjectsQuery.isError && sortedStats.length > 0 && (
        <div className="flex flex-col gap-3">
          {sortedStats.map((stats, index) => (
            <motion.div
              key={stats.subjectId}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.16, delay: index * 0.02, ease: 'easeOut' }}
            >
              <SubjectStatsCard
                subjectId={stats.subjectId}
                subjectName={stats.subjectName}
                subjectType={stats.subjectType}
                groupAttendancePercent={stats.groupPercent}
                threshold={stats.threshold}
                studentRows={stats.studentRows}
              />
            </motion.div>
          ))}
        </div>
      )}
    </PageShell>
  )
}

function PageShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-full px-3 py-4 pb-[calc(72px+env(safe-area-inset-bottom))]">
      {children}
    </div>
  )
}

function BackLink() {
  return (
    <Link
      to="/group"
      className="mb-4 inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
        color: 'var(--text-secondary)',
      }}
    >
      <ArrowLeft size={14} weight="bold" aria-hidden="true" />
      Назад
    </Link>
  )
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="flex min-h-[28vh] flex-col items-center justify-center gap-3 text-center">
      <div
        className="grid size-14 place-items-center rounded-full border"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
          color: 'var(--accent-secondary)',
        }}
      >
        <ChartBar size={28} weight="duotone" aria-hidden="true" />
      </div>
      <p className="max-w-[30ch] text-sm" style={{ color: 'var(--text-secondary)' }}>
        {text}
      </p>
    </div>
  )
}

function InlineState({ text }: { text: string }) {
  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-center">
      <WarningCircle size={28} weight="duotone" style={{ color: 'var(--accent-warning)' }} />
      <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
        {text}
      </p>
    </div>
  )
}

function InlineError({ text }: { text: string }) {
  return (
    <div
      role="alert"
      className="mb-3 flex items-start gap-2 rounded-lg border px-4 py-3 text-sm"
      style={{
        background: 'color-mix(in oklab, var(--accent-danger) 9%, transparent)',
        borderColor: 'color-mix(in oklab, var(--accent-danger) 35%, transparent)',
        color: 'var(--accent-danger)',
      }}
    >
      <WarningCircle size={18} weight="fill" className="mt-0.5 shrink-0" aria-hidden="true" />
      <span>{text}</span>
    </div>
  )
}

function SkeletonList() {
  return (
    <div className="flex flex-col gap-3" aria-label="Загрузка">
      {Array.from({ length: 3 }).map((_, index) => (
        <motion.div
          key={index}
          initial={{ opacity: 0.6 }}
          animate={{ opacity: 1 }}
          transition={{ repeat: Infinity, repeatType: 'reverse', duration: 0.9 }}
          className="h-[160px] rounded-lg"
          style={{ background: 'var(--bg-secondary)' }}
        />
      ))}
    </div>
  )
}
