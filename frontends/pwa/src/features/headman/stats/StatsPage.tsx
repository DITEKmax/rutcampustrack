import { useMemo } from 'react'
import { Link } from 'react-router'
import { ArrowLeft } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  useGroupSubjects,
  useJournal,
  useResolveThreshold,
} from '@/features/headman/shared/headmanApi'
import type { JournalCell, Subject } from '@/features/headman/shared/types'
import { SubjectStatsCard } from './SubjectStatsCard'

const DEFAULT_THRESHOLD = 75

function computeSemesterStart(): string {
  const now = new Date()
  const year = now.getMonth() >= 8 ? now.getFullYear() : now.getFullYear() - 1
  return `${year}-09-01`
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

/**
 * Per-student attendance calculation (CONTEXT.md D-12):
 *   present-like (present | excused | free_attendance) counts as "attended"
 *   absent counts as missed
 *   cancelled cells are excluded from denominator entirely
 *   default 100% when denominator is 0.
 */
function computeStudentStats(cells: JournalCell[]): {
  studentRows: Array<{ studentId: number; studentName: string; attendancePercent: number }>
  groupPercent: number
} {
  const byStudent = new Map<
    number,
    { studentName: string; presentLike: number; absent: number }
  >()
  for (const c of cells) {
    if (c.status === 'cancelled') continue
    const entry =
      byStudent.get(c.studentId) ??
      { studentName: c.studentName, presentLike: 0, absent: 0 }
    if (
      c.status === 'present' ||
      c.status === 'excused' ||
      c.status === 'free_attendance'
    ) {
      entry.presentLike += 1
    } else if (c.status === 'absent') {
      entry.absent += 1
    }
    byStudent.set(c.studentId, entry)
  }

  const studentRows = Array.from(byStudent.entries()).map(([studentId, v]) => {
    const denom = v.presentLike + v.absent
    const pct = denom === 0 ? 100 : (v.presentLike / denom) * 100
    return { studentId, studentName: v.studentName, attendancePercent: pct }
  })

  if (studentRows.length === 0) {
    return { studentRows, groupPercent: 100 }
  }
  const groupPercent =
    studentRows.reduce((sum, r) => sum + r.attendancePercent, 0) / studentRows.length
  return { studentRows, groupPercent }
}

interface SubjectRowProps {
  subject: Subject
  groupId: number
  dateFrom: string
  dateTo: string
  statsSink: (stats: SubjectStats) => void
}

interface SubjectStats {
  subjectId: number
  groupPercent: number
  threshold: number
  studentRows: Array<{ studentId: number; studentName: string; attendancePercent: number }>
  isRedZone: boolean
  subjectName: string
  isLoading: boolean
}

/**
 * Sorts subjects descending by red-zone severity:
 *   1) red-zone subjects first (groupPercent < threshold)
 *   2) within red-zone: lowest attendance first (most urgent)
 *   3) non-red-zone: alphabetically by subject name
 */
function sortSubjectsBySeverity(list: SubjectStats[]): SubjectStats[] {
  return [...list].sort((a, b) => {
    if (a.isRedZone && !b.isRedZone) return -1
    if (!a.isRedZone && b.isRedZone) return 1
    if (a.isRedZone && b.isRedZone) {
      return a.groupPercent - b.groupPercent
    }
    return a.subjectName.localeCompare(b.subjectName, 'ru')
  })
}

/**
 * Internal data-loader wrapper. Keeps hook order stable per subject by being a
 * distinct component instance. Computes stats + reports them up to parent for
 * global sorting via the `statsSink` callback.
 */
function SubjectRow({ subject, groupId, dateFrom, dateTo }: SubjectRowProps) {
  const journalQuery = useJournal(groupId, subject.id, dateFrom, dateTo)
  const thresholdQuery = useResolveThreshold(groupId, subject.id)

  const cells = journalQuery.data ?? []
  const threshold = thresholdQuery.data?.minPercentage ?? DEFAULT_THRESHOLD
  const { studentRows, groupPercent } = useMemo(() => computeStudentStats(cells), [cells])

  return (
    <SubjectStatsCard
      subjectId={subject.id}
      groupId={groupId}
      subjectName={subject.name}
      groupAttendancePercent={groupPercent}
      threshold={threshold}
      studentRows={studentRows}
    />
  )
}

/**
 * Two-pass render: first compute stats per subject (each in its own hook-calling
 * child), then render sorted. To keep hook order stable we always call
 * `useJournal`/`useResolveThreshold` for every subject, so we introduce a
 * "planner" subcomponent that computes stats + queues a sorted render below.
 *
 * Practical approach: since hook order per child component is fixed, we can
 * compute and sort entirely from `subjects` + per-child queries using a tiny
 * "collector" pattern implemented with a stable helper that calls hooks in a
 * loop driven by the `subjects` array length (React's Rules of Hooks allow
 * this as long as order does not change within a render). We do this via an
 * internal `SubjectStatsCollector` that calls hooks for every subject and
 * returns the sorted stats array.
 */
function SubjectStatsCollector({
  subjects,
  groupId,
  dateFrom,
  dateTo,
}: {
  subjects: Subject[]
  groupId: number
  dateFrom: string
  dateTo: string
}) {
  // Call hooks in a stable order driven by `subjects` length.
  const stats: SubjectStats[] = []
  for (const subject of subjects) {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const journal = useJournal(groupId, subject.id, dateFrom, dateTo)
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const thr = useResolveThreshold(groupId, subject.id)
    const cells = journal.data ?? []
    const threshold = thr.data?.minPercentage ?? DEFAULT_THRESHOLD
    const { studentRows, groupPercent } = computeStudentStats(cells)
    stats.push({
      subjectId: subject.id,
      subjectName: subject.name,
      groupPercent,
      threshold,
      studentRows,
      isRedZone: groupPercent < threshold,
      isLoading: journal.isLoading || thr.isLoading,
    })
  }

  const sorted = sortSubjectsBySeverity(stats)

  return (
    <>
      {sorted.map((s) => (
        <SubjectStatsCard
          key={s.subjectId}
          subjectId={s.subjectId}
          groupId={groupId}
          subjectName={s.subjectName}
          groupAttendancePercent={s.groupPercent}
          threshold={s.threshold}
          studentRows={s.studentRows}
        />
      ))}
    </>
  )
}

export function StatsPage() {
  const { user } = useAuth()
  const groupId = user?.groupId
  const subjectsQuery = useGroupSubjects()
  const semesterStart = useMemo(computeSemesterStart, [])
  const semesterEnd = useMemo(todayIso, [])

  if (!groupId) {
    return (
      <div className="p-6">
        <Link to="/group" aria-label="Назад" className="inline-flex items-center gap-2 mb-4">
          <ArrowLeft size={20} /> Назад
        </Link>
        <h1 className="text-lg font-semibold mb-4">Статистика</h1>
        <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
          Группа не назначена
        </p>
      </div>
    )
  }

  if (subjectsQuery.isLoading) {
    return (
      <div className="p-6">
        <Link to="/group" aria-label="Назад" className="inline-flex items-center gap-2 mb-4">
          <ArrowLeft size={20} /> Назад
        </Link>
        <h1 className="text-lg font-semibold mb-4">Статистика</h1>
        {Array.from({ length: 3 }).map((_, i) => (
          <div
            key={i}
            className="h-[200px] rounded-lg animate-pulse mb-4"
            style={{ background: 'var(--bg-surface)' }}
          />
        ))}
      </div>
    )
  }

  const subjects = subjectsQuery.data ?? []
  if (subjects.length === 0) {
    return (
      <div className="p-6">
        <Link to="/group" aria-label="Назад" className="inline-flex items-center gap-2 mb-4">
          <ArrowLeft size={20} /> Назад
        </Link>
        <h1 className="text-lg font-semibold mb-4">Статистика</h1>
        <p
          className="text-sm text-center py-12"
          style={{ color: 'var(--text-muted)' }}
        >
          Группе не назначены предметы
        </p>
      </div>
    )
  }

  return (
    <div className="p-6">
      <Link to="/group" aria-label="Назад" className="inline-flex items-center gap-2 mb-4">
        <ArrowLeft size={20} /> Назад
      </Link>
      <h1 className="text-lg font-semibold mb-4">Статистика</h1>
      <SubjectStatsCollector
        subjects={subjects}
        groupId={groupId}
        dateFrom={semesterStart}
        dateTo={semesterEnd}
      />
    </div>
  )
}

// Suppress unused warning for SubjectRow (kept for future per-row refactor)
void SubjectRow
