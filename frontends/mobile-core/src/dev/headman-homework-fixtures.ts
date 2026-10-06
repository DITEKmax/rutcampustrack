import { HeadmanHomeworkApi, type HeadmanManagedHomework } from '../features/homework/headman-homework-client'
import { HeadmanSubjectsApi } from '../features/headman-subjects/headman-subjects-client'
import { HeadmanJournalApi } from '../features/headman-journal/headman-journal-client'

export const headmanHomeworkStates = ['calendar', 'detail', 'create-subject', 'create-type', 'create-calendar-schedule', 'create-calendar-any', 'create-content', 'day-mixed-active-terminal', 'terminal-detail', 'history-active', 'history-terminal', 'empty', 'loading', 'error', 'offline', 'read-only', 'no-semester', 'forbidden', 'pending', 'uncertain', 'edit-subject', 'edit-placement', 'edit-content', 'edit-conflict', 'stale-selection', 'multiple-pairs', 'long-content', 'unsafe-link'] as const
export const headmanHomeworkClock = () => new Date('2026-09-02T06:00:00Z')
const names = ['Информационные системы', 'Высшая математика', 'Иностранный язык', 'Архитектура ЭВМ']
const titles = ['Пределы функций · № 16–20', 'Интегралы: задачи 4.12–4.18', 'Подготовить диалог по теме Travel', 'Схема командного цикла процессора']
export function createHeadmanHomeworkFixtures(state: string) {
  const requests: { method: string; path: string; body: unknown }[] = []
  const noActive = state === 'no-semester' || state === 'no-active-history'
  const semester = { id: 12, name: 'Осенний семестр 2026', dateFrom: '2026-09-01', dateTo: noActive ? '2026-09-01' : '2027-01-31', active: !noActive }
  const subjects = names.map((name, index) => ({ id: 88 + index, name, type: 'LECTURE', groupId: 7, lessonTypes: ['LECTURE', 'PRACTICE', 'LAB'], teacherIds: [41],
    assignments: ['LECTURE', 'PRACTICE', 'LAB'].map((lessonType, typeIndex) => ({ id: 500 + index * 10 + typeIndex, teacherId: 41, semesterId: 12, lessonType, validFrom: '2026-09-01', validUntilExclusive: null })) }))
  const records: HeadmanManagedHomework[] = names.map((_name, index) => ({ id: 91 + index, bindingId: 501 + index, requestKey: null, title: titles[index]!,
    description: index === 0 ? 'Повтори определения предела функции и непрерывности. Реши задачи № 16–20 из методички. Для № 19 приложи краткое обоснование выбранного способа.' : titles[index]!,
    link: index === 0 ? state === 'unsafe-link' ? 'javascript:alert(1)' : 'https://rut-miit.ru/materials/limits' : null,
    subjectId: 88 + index, groupId: 7, semesterId: 12, publishedBy: 41, lessonDate: '2026-09-02', lessonNumber: 8, bindingMode: 'LESSON', lessonType: 'LECTURE', revision: 4, archived: false }))
  if (state === 'long-content') records[0] = { ...records[0]!, title: 'Подготовка развёрнутого решения задания по информационным системам', description: 'Развёрнутый текст задания с объяснениями и дополнительными материалами. '.repeat(40) + 'https://example.test/read' }
  const archive = [{ ...records[0]!, id: 19, title: 'Завершённое задание: основы функций', lessonDate: noActive ? '2026-09-01' : ['day-mixed-active-terminal','terminal-detail','history-terminal'].includes(state) || state.startsWith('read-offline-') ? '2026-09-02' : '2026-08-31', archived: true, bindingMode: 'DATE' as const, lessonNumber: null, lessonType: null }]
  const lessons = [
    ...['2026-09-02', '2026-09-09', '2026-09-16', '2026-09-23', '2026-09-30'].map((date, i) => ({ id: 300 + i, occurrenceId: 700 + i, date, lessonType: 'LECTURE', lessonNumber: 8, startTime: '18:00', endTime: '19:30' })),
    { id: 310, occurrenceId: 710, date: '2026-09-04', lessonType: 'PRACTICE', lessonNumber: 7, startTime: '16:20', endTime: '17:50' },
    { id: 311, occurrenceId: 711, date: '2026-09-03', lessonType: 'LAB', lessonNumber: 5, startTime: '13:00', endTime: '14:30' },
    ...(state === 'multiple-pairs' ? [{ id: 399, occurrenceId: 799, date: '2026-09-02', lessonType: 'LECTURE', lessonNumber: 7, startTime: '16:20', endTime: '17:50' }] : []),
  ].map((item) => ({ ...item, occurrenceRevision: 3, subjectId: 88, groupId: 7, semesterId: 12, room: 'А-401', current: true, status: 'PLANNED', transferOperationId: null, transferState: null }))
  const dayLessonReadCounts: number[] = []
  function addEligiblePair(): void {
    if (!lessons.some((item) => item.id === 399)) lessons.push({ ...lessons[0]!, id: 399, occurrenceId: 799, lessonNumber: 7 })
  }
  let mutationCount = 0
  const accepted = new Map<string, HeadmanManagedHomework>()
  let heldRead: { archived: boolean; started: () => void; wait: Promise<void> } | null = null
  function holdNextRead(archived: boolean): { started: Promise<void>; release: () => void } {
    let started!: () => void; let release!: () => void
    const whenStarted = new Promise<void>((resolve) => { started = resolve })
    const wait = new Promise<void>((resolve) => { release = resolve })
    heldRead = { archived, started, wait }
    return { started: whenStarted, release }
  }
  const fetcher: typeof fetch = async (input, init) => {
    const url = new URL(String(input), 'http://review.test'); const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) as Record<string, unknown> : null
    requests.push({ method, path: url.pathname + url.search, body })
    if (state === 'loading') return new Promise<Response>(() => undefined)
    if (state === 'forbidden') return Response.json({ detail: 'Доступ к группе отозван.' }, { status: 403 })
    if (state === 'error') return Response.json({ detail: 'Сервис заданий временно недоступен.' }, { status: 503 })
    if (url.pathname.endsWith('/semesters')) return Response.json({ _embedded: { semesterResponseList: [{ ...semester, active: !noActive }] } })
    if (url.pathname.endsWith('/subjects')) return Response.json({ _embedded: { subjectResponseList: subjects } })
    if (url.pathname.endsWith('/lessons/next')) {
      const next = lessons.find((item) => item.lessonType === url.searchParams.get('lessonType') && item.subjectId === Number(url.searchParams.get('subjectId')) && item.date >= url.searchParams.get('fromDate')!)
      return next ? Response.json({ ...next, lessonId: next.id }) : new Response(null, { status: 204 })
    }
    if (url.pathname.endsWith('/lessons')) {
      const rows = lessons.filter((item) => item.date >= url.searchParams.get('dateFrom')! && item.date <= url.searchParams.get('dateTo')!)
      dayLessonReadCounts.push(rows.filter((item) => item.date === '2026-09-02' && item.lessonType === 'LECTURE').length)
      return Response.json({ _embedded: { lessonResponseList: rows } })
    }
    if (url.pathname.endsWith('/history')) return Response.json({ content: [{ id: 1, revision: 4, actorId: 41, occurredAt: '2026-08-31T09:00:00Z', action: 'ARCHIVED', before: null, after: archive[0] }] })
    if (method === 'POST' && body) {
      mutationCount += 1
      if (state === 'stale-selection' && mutationCount === 1) return Response.json({ detail: 'Выбранная пара уже изменилась.', extras: { code: 'HOMEWORK_SELECTION_STALE', admission: 'CURRENT_ATTEMPT_REJECTED_BEFORE_ADMISSION' } }, { status: 409, headers: { 'Content-Type': 'application/problem+json' } })
      const key = String(body.requestKey)
      const created = accepted.get(key) ?? { ...records[0]!, ...body, id: 101, bindingId: 601, requestKey: key, revision: 1, archived: false } as HeadmanManagedHomework
      accepted.set(key, created)
      if (!records.some((item) => item.id === created.id)) records.push(created)
      if (state === 'pending' && mutationCount === 1) return Response.json({ homeworkId: String(created.id), bindingId: String(created.bindingId), requestKey: key, state: 'PENDING' }, { status: 202 })
      if (state === 'uncertain' && mutationCount === 1) throw new TypeError('Ответ не получен. Результат публикации пока неизвестен.')
      return Response.json(created, { status: 201 })
    }
    if (method === 'PUT' && body) {
      mutationCount += 1
      const id = Number(url.pathname.split('/').at(-1)); const index = records.findIndex((item) => item.id === id)
      if (state === 'edit-conflict' && mutationCount === 1) { records[index] = { ...records[index]!, title: 'Уточнённое задание преподавателя', revision: 5 }; return Response.json({ detail: 'Задание изменилось.' }, { status: 409 }) }
      records[index] = { ...records[index]!, ...body, revision: records[index]!.revision! + 1 } as HeadmanManagedHomework
      return Response.json(records[index])
    }
    if (url.pathname.endsWith('/homeworks') && heldRead && heldRead.archived === (url.searchParams.get('archived') === 'true')) { const held = heldRead; heldRead = null; held.started(); await held.wait }
    if (url.pathname.endsWith('/homeworks')) return Response.json({ _embedded: { homeworkResponseList: url.searchParams.get('archived') === 'true' ? archive : state === 'empty' || noActive ? [] : records } })
    const id = Number(url.pathname.split('/').at(-1)); const item = [...records, ...archive].find((row) => row.id === id)
    return item ? Response.json(item) : Response.json({ detail: 'Не найдено' }, { status: 404 })
  }
  return { requests, holdNextRead, addEligiblePair, dayLessonReadCounts, api: new HeadmanHomeworkApi({ accessToken: () => null, fetcher }), subjectsApi: new HeadmanSubjectsApi({ accessToken: () => null, fetcher }), journalApi: new HeadmanJournalApi({ accessToken: () => null, fetcher }) }
}
