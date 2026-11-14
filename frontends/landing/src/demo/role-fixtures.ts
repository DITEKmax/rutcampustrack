import { createAdminActivityFixture } from '../../../mobile-core/src/dev/admin-dashboard-fixtures'
import { AdminDashboardClient } from '../../../mobile-core/src/features/admin-dashboard/admin-dashboard-client'
import type { HeadmanJournalRosterEntry } from '../../../mobile-core/src/features/headman-journal/headman-journal-client'
import type { TeacherDayLesson } from '../../../mobile-core/src/features/teacher/teacher-client'
import type { TeacherRosterRow } from '../../../mobile-core/src/features/teacher/TeacherRoster.vue'
import { demoDate, lessonsForDate, schedule, selectedDemoLesson, statusFor } from './fixtures'
import {
  group, isEditableMemberId, lessonId, markedAt, rowKey, student, type DemoSnapshot,
} from './model'
import type { AdminActivityMetrics, AdminDashboardActivity } from '../../../mobile-core/src/features/admin-dashboard/admin-dashboard-activity'

const members = [
  { id: 1, name: student, status: 'EMPTY' },
  { id: 2, name: 'Артём Волков', status: 'PRESENT' },
  { id: 3, name: 'Вера Лебедева', status: 'ABSENT' },
  { id: 4, name: 'Кирилл Орлов', status: 'EXCUSED' },
  { id: 5, name: 'Мария Крылова', status: 'PRESENT' },
  { id: 6, name: 'Никита Соколов', status: 'EMPTY' },
] as const

export function teacherLessons(snapshot: DemoSnapshot): TeacherDayLesson[] {
  return lessonsForDate(snapshot).map((item) => ({
    id: Number(item.id),
    assignmentId: item.lessonNumber,
    groupId: 411,
    groupName: group,
    subjectId: item.lessonNumber,
    subjectName: item.subject.name,
    semesterId: 1,
    lessonType: item.subject.type,
    date: item.date,
    lessonNumber: item.lessonNumber,
    startsAt: item.startsAt,
    endsAt: item.endsAt,
    room: item.room.current,
    status: item.status,
    oneOff: false,
    moved: false,
    cancelled: item.status === 'CANCELLED',
    cancellationReason: item.status === 'CANCELLED' ? 'Пара отменена' : null,
  }))
}

export function groupRoster(snapshot: DemoSnapshot, lesson = selectedDemoLesson(snapshot)): HeadmanJournalRosterEntry[] {
  if (!lesson || lesson.status === 'CANCELLED') return []
  return members.map(({ id, name, status: seededStatus }) => {
    const active = lesson.id === lessonId
    const editable = isEditableMemberId(id) && (lesson.status === 'ACTIVE' || lesson.status === 'CLOSED')
    const manual = isEditableMemberId(id) ? snapshot.manualMarks[rowKey(lesson.id, id)] : undefined
    const studentMark = active && id === 1 && snapshot.markState !== 'ready'
    const own = statusFor(lesson, studentMark)
    const historicalStudent = own === 'PRESENT' || own === 'ABSENT' || own === 'EXCUSED' ? own : 'EMPTY'
    const baseline = active ? seededStatus : lesson.status === 'CLOSED'
      ? id === 1 ? historicalStudent : 'PRESENT'
      : 'EMPTY'
    const status = studentMark ? 'PRESENT' : (manual?.value ?? baseline)
    return {
      userId: id,
      displayName: name,
      status,
      symbol:
        status === 'PRESENT' ? '+' : status === 'ABSENT' ? 'н' : status === 'EXCUSED' ? 'у' : null,
      markRevision: status === 'EMPTY' ? null : String(manual?.revision ?? 1),
      approvedTicket: null,
      source: studentMark
        ? 'student_geo'
        : status === 'EMPTY'
          ? null
          : status === 'EXCUSED'
            ? 'ticket'
            : 'headman',
      timestamp: studentMark ? markedAt : null,
      excuseReason: status === 'EXCUSED' ? 'Медицинское обследование' : null,
      excuseType: status === 'EXCUSED' ? 'MEDICAL_EXAMINATION' : null,
      comment: null,
      attachmentId: null,
      attachmentName: null,
      attachmentContentType: null,
      attachmentSize: null,
      editable,
    }
  })
}

export function teacherRoster(snapshot: DemoSnapshot): TeacherRosterRow[] {
  const lesson = selectedDemoLesson(snapshot)
  return groupRoster(snapshot, lesson).map((entry) => {
    const pending = lesson?.id === lessonId && entry.userId === 1 && snapshot.scenario === 'pending'
    return {
      studentId: entry.userId,
      displayName: entry.displayName,
      attendance: entry.status === 'EMPTY' && !pending ? null : {
        status: entry.status === 'EMPTY' ? null : entry.status.toLowerCase(),
        symbol: entry.symbol,
        source: entry.source,
        recordPresent: entry.status !== 'EMPTY',
        pendingTicket: pending,
        autoAbsent: false,
        ticketId: pending ? 'demo-geo-request' : null,
        excuseType: entry.excuseType,
        excuseReason: entry.excuseReason,
        excuseComment: null,
      },
    }
  })
}

export function demoActivityCounts(snapshot: DemoSnapshot) {
  const current = lessonsForDate(snapshot, demoDate).find(lesson => lesson.id === lessonId)
  const rows = current ? groupRoster(snapshot, current) : []
  return {
    positive: rows.filter(row => row.status === 'PRESENT').length,
    geo: rows.filter(row => row.source === 'student_geo').length,
    requests: snapshot.scenario === 'pending' ? 1 : 0,
  }
}

function withDemoCounts(metrics: AdminActivityMetrics, snapshot: DemoSnapshot): AdminActivityMetrics {
  const counts = demoActivityCounts(snapshot)
  const percent = counts.positive ? Math.round(counts.geo / counts.positive * 10000) / 100 : null
  function withValue(metric: AdminActivityMetrics['positiveMarks'], value: number) {
    // The synthetic comparison day is Sunday with no scheduled lesson.
    const previous = 0
    return {
      ...metric,
      value,
      comparison: metric.comparison ? {
        ...metric.comparison,
        previousValue: previous,
        absoluteDelta: value - previous,
        relativeDeltaPercent: null,
      } : null,
    }
  }
  return {
    ...metrics,
    positiveMarks: withValue(metrics.positiveMarks, counts.positive),
    requests: withValue(metrics.requests, counts.requests),
    geoMarks: {
      ...metrics.geoMarks,
      value: counts.geo,
      numerator: counts.geo,
      denominator: counts.positive,
      percent,
      comparison: null,
    },
  }
}
/** The production dashboard client reads a local transport with two explicit fixture routes. */
export function createAdminDemoClient(getSnapshot: () => DemoSnapshot) {
  return new AdminDashboardClient({
  accessToken: () => null,
  fetcher: async (input, init) => {
    init?.signal?.throwIfAborted()
    const url = new URL(String(input), 'https://fixture.invalid')
    if (url.pathname === '/api/academic/dashboard/stats')
      return Response.json({
        totalStudents: 192,
        totalTeachers: 16,
        totalGroups: 8,
        activeGroups: 8,
        activeSemesterName: schedule.semester.name,
        dailyGrowth: {
          status: 'AVAILABLE',
          baselineDate: '2026-11-08',
          studentsDelta: 2,
          teachersDelta: 0,
          groupsDelta: 0,
        },
      })
    if (url.pathname === '/api/academic/dashboard/activity') {
      const period = url.searchParams.get('period') === 'all_time' ? 'all_time' : 'today'
      const bucket = url.searchParams.get('bucket') === 'week' ? 'week' : 'day'
      const fixture = createAdminActivityFixture(period, bucket)
      const delta = Date.parse(demoDate) - Date.parse(fixture.observedAt.slice(0, 10))
      // Keep all the fixture's range and coverage timestamps in the same synthetic semester.
      const shifted = JSON.parse(JSON.stringify(fixture), (_key, value: unknown) =>
        typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(value)
          ? new Date(Date.parse(value) + delta).toISOString()
          : value,
      )
      const activity = shifted as AdminDashboardActivity
      const snapshot = getSnapshot()
      return Response.json({
        ...activity,
        summary: { ...activity.summary, metrics: withDemoCounts(activity.summary.metrics, snapshot) },
        series: activity.series.map((point, index) => index === activity.series.length - 1
          ? { ...point, metrics: withDemoCounts(point.metrics, snapshot) }
          : point),
      })
    }
    return Response.json({ title: 'Раздел не входит в демо' }, { status: 404 })
  },
})
}
