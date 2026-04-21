import { useCallback, useEffect, useMemo, useReducer } from 'react'
import { Link } from 'react-router'
import { ArrowLeft } from '@phosphor-icons/react'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  useGroupSubjects,
  useJournal,
  useResolveThreshold,
} from '@/features/headman/shared/headmanApi'
import type { JournalCell, Subject } from '@/features/headman/shared/types'
import { PullToRefresh } from '@/shared/components/PullToRefresh'
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

interface SubjectStats {
  subjectId: number
  subjectName: string
  groupPercent: number
  threshold: number
  studentRows: Array<{ studentId: number; studentName: string; attendancePercent: number }>
  isRedZone: boolean
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

// ── Stats collection via reducer ─────────────────────────────────────────────
// Each SubjectStatsCollector instance owns ONE pair of hooks (useJournal +
// useResolveThreshold) — hook order is stable per component instance, which
// fixes the Rules-of-Hooks violation from the previous loop-based impl.

type StatsAction =
  | { type: 'set'; subjectId: number; stats: SubjectStats }
  | { type: 'prune'; keep: Set<number> }

function statsReducer(
  state: Record<number, SubjectStats>,
  action: StatsAction,
): Record<number, SubjectStats> {
  switch (action.type) {
    case 'set': {
      const prev = state[action.subjectId]
      if (
        prev &&
        prev.groupPercent === action.stats.groupPercent &&
        prev.threshold === action.stats.threshold &&
        prev.isRedZone === action.stats.isRedZone &&
        prev.studentRows.length === action.stats.studentRows.length &&
        prev.subjectName === action.stats.subjectName
      ) {
        return state
      }
      return { ...state, [action.subjectId]: action.stats }
    }
    case 'prune': {
      const next: Record<number, SubjectStats> = {}
      let changed = false
      for (const [k, v] of Object.entries(state)) {
        if (action.keep.has(Number(k))) {
          next[Number(k)] = v
        } else {
          changed = true
        }
      }
      return changed ? next : state
    }
    default:
      return state
  }
}

interface CollectorProps {
  subject: Subject
  groupId: number
  dateFrom: string
  dateTo: string
  onStats: (stats: SubjectStats) => void
}

/**
 * Headless data-loader: calls both per-subject hooks (stable order, one
 * instance per subject) and reports computed stats upward via onStats. Does
 * not render any UI — the parent renders sorted cards.
 */
function SubjectStatsCollector({
  subject,
  groupId,
  dateFrom,
  dateTo,
  onStats,
}: CollectorProps) {
  const journal = useJournal(groupId, subject.id, dateFrom, dateTo)
  const thr = useResolveThreshold(groupId, subject.id)

  const cells = journal.data ?? []
  const threshold = thr.data?.minPercentage ?? DEFAULT_THRESHOLD
  const { studentRows, groupPercent } = useMemo(
    () => computeStudentStats(cells),
    [cells],
  )

  useEffect(() => {
    onStats({
      subjectId: subject.id,
      subjectName: subject.name,
      groupPercent,
      threshold,
      studentRows,
      isRedZone: groupPercent < threshold,
    })
  }, [onStats, subject.id, subject.name, groupPercent, threshold, studentRows])

  return null
}

export function StatsPage() {
  const { user } = useAuth()
  const groupId = user?.groupId
  const subjectsQuery = useGroupSubjects()
  const semesterStart = useMemo(computeSemesterStart, [])
  const semesterEnd = useMemo(todayIso, [])

  const [statsBySubject, dispatch] = useReducer(statsReducer, {})

  const subjects = subjectsQuery.data ?? []

  // Prune entries for subjects that no longer exist (after delete).
  useEffect(() => {
    const keep = new Set(subjects.map((s) => s.id))
    dispatch({ type: 'prune', keep })
  }, [subjects])

  const handleStats = useMemo(
    () => (stats: SubjectStats) => dispatch({ type: 'set', subjectId: stats.subjectId, stats }),
    [],
  )

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

  const sorted = sortSubjectsBySeverity(
    subjects
      .map((s) => statsBySubject[s.id])
      .filter((x): x is SubjectStats => Boolean(x)),
  )

  const queryClient = useQueryClient()
  const handleRefresh = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['groupSubjects'] }),
      queryClient.invalidateQueries({ queryKey: ['journal'] }),
      queryClient.invalidateQueries({ queryKey: ['threshold'] }),
    ])
  }, [queryClient])

  return (
    <PullToRefresh onRefresh={handleRefresh}>
    <div className="p-6">
      <Link to="/group" aria-label="Назад" className="inline-flex items-center gap-2 mb-4">
        <ArrowLeft size={20} /> Назад
      </Link>
      <h1 className="text-lg font-semibold mb-4">Статистика</h1>

      {/* Headless collectors — one per subject, stable hook order. */}
      {subjects.map((s) => (
        <SubjectStatsCollector
          key={s.id}
          subject={s}
          groupId={groupId}
          dateFrom={semesterStart}
          dateTo={semesterEnd}
          onStats={handleStats}
        />
      ))}

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
    </div>
    </PullToRefresh>
  )
}
