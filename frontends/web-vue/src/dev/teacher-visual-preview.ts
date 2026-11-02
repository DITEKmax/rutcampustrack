import { createTeacherReviewFixtures, teacherReviewDate } from '../../../mobile-core/src/dev/teacher-review-fixtures'
import type { TeacherDayLesson, TeacherStatsQuery } from '../../../mobile-core/src/features/teacher/teacher-client'

// Reuse the existing synthetic domain through its public, validating client.
// The outer production client validates the encoded wire response again.
const source = createTeacherReviewFixtures('journal')
const currentDate = new Intl.DateTimeFormat('sv', { timeZone: 'Europe/Moscow', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const serverNow = `${currentDate}T08:10:00Z`
const json = (value: unknown) => new Response(JSON.stringify(value, (key, item: unknown) =>
  typeof item === 'number' && /^(id|.*Id|decisionBy)$/.test(key) ? String(item) : item,
), { headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' } })
const number = (url: URL, name: string, fallback = 0) => Number(url.searchParams.get(name) ?? fallback)
const assignmentId = (lesson: TeacherDayLesson) => lesson.assignmentId === null ? null : lesson.assignmentId * 10 + ['lecture', 'practice', 'lab'].indexOf(lesson.lessonType)
async function dayRecords(date: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !Number.isFinite(Date.parse(`${date}T12:00:00Z`)) || date < '2026-09-01' || date > '2027-01-31') return []
  if ([0, 6].includes(new Date(`${date}T12:00:00Z`).getUTCDay())) return []
  const base = await source.api.day(7, teacherReviewDate)
  const laboratory = (await source.api.lesson(1002)).lesson
  return [...base.lessons, { ...laboratory, lessonNumber: 5, status: 'PLANNED' as const, moved: true, room: 'А-406' }]
    .map((lesson, index) => ({ sourceId: lesson.id, lesson: { ...lesson, id: Number(date.replaceAll('-', '')) * 10 + index + 1, date, assignmentId: assignmentId(lesson) } }))
}
function projectStats<T extends object>(rows: readonly T[], url: URL): T[] {
  const field = (row: T, column: string): string | number | null => {
    const value = (row as Record<string, unknown>)[column]
    if (typeof value === 'string' || typeof value === 'number' || value === null) return value
    if (value && typeof value === 'object' && 'percent' in value && typeof value.percent === 'number') return value.percent
    throw new Error('Неизвестное поле synthetic stats')
  }
  const selected = rows.filter(row => url.searchParams.getAll('filter').every(expression => {
    const match = expression.match(/^([a-zA-Z]+)(~|>=|<=)(.*)$/)
    if (!match) throw new Error('Неизвестный фильтр synthetic stats')
    const value = field(row, match[1]!)
    return match[2] === '~' ? String(value).toLocaleLowerCase('ru').includes(match[3]!.toLocaleLowerCase('ru'))
      : typeof value === 'number' && (match[2] === '>=' ? value >= Number(match[3]) : value <= Number(match[3]))
  }))
  return selected.sort((a, b) => {
    for (const sort of url.searchParams.getAll('sort')) {
      const descending = sort.startsWith('-'), column = descending ? sort.slice(1) : sort
      const first = field(a, column), second = field(b, column)
      const compare = typeof first === 'number' && typeof second === 'number' ? first - second : String(first).localeCompare(String(second), 'ru')
      if (compare) return descending ? -compare : compare
    }
    return 0
  })
}

export async function teacherPreview(url: URL, method: string): Promise<Response | null> {
  if (method !== 'GET') return null
  const path = url.pathname.replace(/^\/api\/v1\/teacher/, '')
  if (url.pathname === '/api/schedule/lesson-slots') return json(await source.api.lessonSlots())
  if (!url.pathname.startsWith('/api/v1/teacher/')) return null
  if (path === '/semester') return json(await source.api.semester())
  if (path === '/catalog') return json({ ...await source.api.catalog(number(url, 'semesterId', 7)), serverNow })
  if (path === '/assignments') {
    const catalog = await source.api.catalog(7)
    return json((await source.api.assignments(7, '2026-09-01', '2027-01-31')).flatMap(item =>
      (catalog.taughtSubjectOptions.find(option => option.groupId === item.groupId && option.subjectId === item.subjectId)?.lessonTypes ?? [item.lessonType])
        .map(lessonType => ({ ...item, id: item.id * 10 + ['lecture', 'practice', 'lab'].indexOf(lessonType), teacherId: 42, lessonType })),
    ))
  }
  if (path === '/day') {
    const date = url.searchParams.get('date') ?? currentDate
    return json({ semesterId: 7, date, lessons: (await dayRecords(date)).map(record => record.lesson), serverNow })
  }
  const detail = path.match(/^\/lessons\/(\d+)$/)
  if (detail) {
    const id = Number(detail[1])
    const dayId = detail[1]!.match(/^(\d{4})(\d{2})(\d{2})([1-5])$/)
    const dayRecord = dayId ? (await dayRecords(`${dayId[1]}-${dayId[2]}-${dayId[3]}`)).find(record => record.lesson.id === id) : undefined
    if (!dayRecord && !(id >= 401 && id <= 404) && !(id >= 1000 && id <= 1224)) return Response.json({ title: 'Синтетическая пара не найдена' }, { status: 404 })
    const value = await source.api.lesson(dayRecord?.sourceId ?? id)
    const lesson = dayRecord?.lesson ?? { ...value.lesson, assignmentId: assignmentId(value.lesson) }
    // Day examples have their own identities; historical tickets stay attached to
    // the original journal occurrences, never to a date-shifted example.
    const entries = dayRecord ? value.roster.map(item => ({ ...item, source: item.status === 'excused' ? 'NO_TICKET' : item.source, ticketId: null, pendingTicket: false, attachmentId: null, attachmentName: null, attachmentContentType: null, attachmentSize: null })) : value.roster
    const roster = lesson.status === 'PLANNED' ? entries.map(item => ({ ...item, status: null, symbol: null, source: null, recordPresent: false, pendingTicket: false, autoAbsent: false, ticketId: null, excuseType: null, excuseReason: null, excuseComment: null, attachmentId: null, attachmentName: null, attachmentContentType: null, attachmentSize: null })) : entries
    return json({ ...value, lesson, roster, serverNow })
  }
  if (path === '/journal') {
    const value = await source.api.journal({
    semesterId: number(url, 'semesterId', 7), groupId: number(url, 'groupId'), subjectId: number(url, 'subjectId'),
    lessonTypes: url.searchParams.getAll('lessonType'), page: number(url, 'page'), pageSize: number(url, 'pageSize', 100),
    ...(url.searchParams.has('dateFrom') ? { dateFrom: url.searchParams.get('dateFrom')!, dateTo: url.searchParams.get('dateTo')! } : {}),
    pastOnly: url.searchParams.get('pastOnly') === 'true', includeMetrics: url.searchParams.get('includeMetrics') === 'true',
    ...(url.searchParams.has('metricsStudentId') ? { metricsStudentId: number(url, 'metricsStudentId') } : {}),
    })
    return json({ ...value, lessons: value.lessons.map(lesson => ({ ...lesson, assignmentId: assignmentId(lesson) })) })
  }
  if (path === '/search') return json(await source.api.search(number(url, 'semesterId', 7), url.searchParams.get('q') ?? ''))
  if (path === '/stats') {
    const query: TeacherStatsQuery = {
      semesterId: number(url, 'semesterId', 7), scope: url.searchParams.get('scope') === 'groups' ? 'groups' : 'students',
      groupId: number(url, 'groupId', 101), subjectId: number(url, 'subjectId', 201), lessonTypes: url.searchParams.getAll('lessonType'),
    }
    const result = await source.api.stats(query)
    if (query.scope === 'groups' && !url.searchParams.has('groupId')) {
      const second = await source.api.stats({ ...query, groupId: 102 })
      return json({ ...result, groups: projectStats([...result.groups, ...second.groups], url), lessonsCount: result.lessonsCount + second.lessonsCount })
    }
    return json({ ...result, students: projectStats(result.students, url), groups: projectStats(result.groups, url) })
  }
  if (path === '/journal/export/formats') return json({ formats: await source.api.journalExportFormats() })
  if (path === '/stats/export/formats') return json({ formats: await source.api.statsExportFormats() })
  const attachment = path.match(/^\/excuses\/([^/]+)\/attachments\/([^/]+)$/)
  if (attachment) return new Response(await source.api.downloadAttachment(decodeURIComponent(attachment[1]!), decodeURIComponent(attachment[2]!)))
  const excuse = path.match(/^\/excuses\/([^/]+)$/)
  if (excuse) return json(await source.api.excuse(decodeURIComponent(excuse[1]!)))
  return null
}
