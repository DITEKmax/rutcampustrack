import { useMemo } from 'react'
import { Percent } from '@phosphor-icons/react'
import { useSubjectJournals } from '@/features/headman/shared/headmanApi'
import type {
  AttendanceStatus,
  GroupMember,
  JournalCell,
  Subject,
} from '@/features/headman/shared/types'

interface StudentAttendanceStatsBlockProps {
  groupId: number
  members: GroupMember[]
  subjects: Subject[]
}

interface StudentAttendanceStatsRow {
  studentId: number
  fullName: string
  surname: string
  presentPercent: number
  excusePercent: number
  absentPercent: number
}

interface MutableStudentCounters {
  studentId: number
  fullName: string
  surname: string
  present: number
  excuse: number
  absent: number
}

const COUNTED_STATUSES: AttendanceStatus[] = [
  'present',
  'absent',
  'excused',
  'free_attendance',
]

function computeSemesterStart(): string {
  const now = new Date()
  const year = now.getMonth() >= 8 ? now.getFullYear() : now.getFullYear() - 1
  return `${year}-09-01`
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

function surnameOf(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] || fullName
}

function percentage(value: number, total: number): number {
  return total === 0 ? 0 : Math.round((value / total) * 100)
}

export function computeStudentAttendanceBreakdown(
  members: GroupMember[],
  cells: JournalCell[],
): StudentAttendanceStatsRow[] {
  const byStudent = new Map<number, MutableStudentCounters>()

  for (const member of members) {
    byStudent.set(member.id, {
      studentId: member.id,
      fullName: member.fullName,
      surname: surnameOf(member.fullName),
      present: 0,
      excuse: 0,
      absent: 0,
    })
  }

  for (const cell of cells) {
    if (!COUNTED_STATUSES.includes(cell.status)) continue
    const entry =
      byStudent.get(cell.studentId) ??
      {
        studentId: cell.studentId,
        fullName: cell.studentName,
        surname: surnameOf(cell.studentName),
        present: 0,
        excuse: 0,
        absent: 0,
      }

    if (cell.status === 'present') {
      entry.present += 1
    } else if (cell.status === 'excused' || cell.status === 'free_attendance') {
      entry.excuse += 1
    } else if (cell.status === 'absent') {
      entry.absent += 1
    }

    byStudent.set(cell.studentId, entry)
  }

  return Array.from(byStudent.values())
    .sort((a, b) => a.surname.localeCompare(b.surname, 'ru'))
    .map((entry) => {
      const total = entry.present + entry.excuse + entry.absent
      return {
        studentId: entry.studentId,
        fullName: entry.fullName,
        surname: entry.surname,
        presentPercent: percentage(entry.present, total),
        excusePercent: percentage(entry.excuse, total),
        absentPercent: percentage(entry.absent, total),
      }
    })
}

function StatValue({
  value,
  tone,
}: {
  value: number
  tone: 'present' | 'excuse' | 'absent'
}) {
  const colorByTone = {
    present: 'var(--accent-primary)',
    excuse: 'var(--status-free-attendance)',
    absent: 'var(--accent-danger)',
  }

  return (
    <span
      className="rounded px-1.5 py-1 text-center text-xs font-semibold tabular-nums"
      style={{
        color: colorByTone[tone],
        background: 'var(--bg-surface)',
      }}
    >
      {value}%
    </span>
  )
}

export function StudentAttendanceStatsBlock({
  groupId,
  members,
  subjects,
}: StudentAttendanceStatsBlockProps) {
  const dateFrom = useMemo(computeSemesterStart, [])
  const dateTo = useMemo(todayIso, [])
  const journalQueries = useSubjectJournals(groupId, subjects, dateFrom, dateTo)

  const allCells = journalQueries.flatMap((query) => query.data ?? [])
  const isLoading = subjects.length > 0 && journalQueries.some((query) => query.isLoading)
  const isError = journalQueries.some((query) => query.isError)
  const rows = computeStudentAttendanceBreakdown(members, allCells)

  return (
    <section
      className="mt-8 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-secondary)] p-4"
      aria-labelledby="student-attendance-stats-title"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2
            id="student-attendance-stats-title"
            className="text-base font-semibold"
            style={{ fontFamily: 'var(--font-heading)' }}
          >
            Статистика по студентам
          </h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            За текущий семестр
          </p>
        </div>
        <div
          className="flex size-9 shrink-0 items-center justify-center rounded-lg"
          style={{
            background: 'var(--bg-surface)',
            color: 'var(--accent-primary)',
          }}
          aria-hidden="true"
        >
          <Percent size={20} weight="bold" />
        </div>
      </div>

      {subjects.length === 0 && (
        <p className="mt-4 text-sm" style={{ color: 'var(--text-muted)' }}>
          Предметы не назначены
        </p>
      )}

      {subjects.length > 0 && isLoading && (
        <div className="mt-4 flex flex-col gap-2">
          {Array.from({ length: Math.max(2, Math.min(4, members.length || 3)) }).map((_, index) => (
            <div
              key={index}
              className="h-10 rounded animate-pulse"
              style={{ background: 'var(--bg-surface)' }}
            />
          ))}
        </div>
      )}

      {subjects.length > 0 && !isLoading && rows.length === 0 && (
        <p className="mt-4 text-sm" style={{ color: 'var(--text-muted)' }}>
          В группе пока нет студентов
        </p>
      )}

      {subjects.length > 0 && !isLoading && rows.length > 0 && (
        <div className="mt-4">
          <div
            className="grid grid-cols-[minmax(0,1fr)_3.5rem_3.5rem_3.5rem] gap-2 px-2 text-xs font-semibold"
            style={{ color: 'var(--text-muted)' }}
          >
            <span>Фамилия</span>
            <span className="text-center">+</span>
            <span className="text-center">у+сп</span>
            <span className="text-center">н</span>
          </div>
          <div className="mt-2 flex flex-col gap-1.5">
            {rows.map((row) => (
              <div
                key={row.studentId}
                className="grid grid-cols-[minmax(0,1fr)_3.5rem_3.5rem_3.5rem] items-center gap-2 rounded px-2 py-2"
                style={{ background: 'var(--bg-surface)' }}
                aria-label={`${row.surname}: посещение ${row.presentPercent}%, уважительная причина ${row.excusePercent}%, пропуски ${row.absentPercent}%`}
                title={row.fullName}
              >
                <span className="truncate text-sm font-medium">{row.surname}</span>
                <StatValue value={row.presentPercent} tone="present" />
                <StatValue value={row.excusePercent} tone="excuse" />
                <StatValue value={row.absentPercent} tone="absent" />
              </div>
            ))}
          </div>
        </div>
      )}

      {isError && (
        <p className="mt-3 text-sm" style={{ color: 'var(--accent-danger)' }}>
          Часть данных не загрузилась
        </p>
      )}
    </section>
  )
}
