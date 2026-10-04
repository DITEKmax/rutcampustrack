import type { StudentSemesterSchedule, StudentToday, TodayLesson } from '../api/types'
import type {
  RequestAccessState,
  RequestBudget,
  RequestFileLimits,
  RequestFileRef,
  RequestLessonOption,
  RequestReasonOption,
} from '../features/requests/types'

/** Visual review data only. These scenes never issue or acknowledge an API request. */
export interface TodayReviewScenario {
  id: string
  label: string
  kind: 'today' | 'excuse' | 'late'
  today: StudentToday | null
  semesterSchedule?: StudentSemesterSchedule | null
  loading?: boolean
  error?: string | null
  offline?: boolean
  readOnly?: boolean
  submittingLessonId?: string | null
  acquiringLessonId?: string | null
  checkinError?: string | null
  expandedLessonId?: string | null
  task?: boolean
  requestOptions?: readonly RequestLessonOption[]
  optionsLoading?: boolean
  optionsError?: string | null
  form?: {
    access: RequestAccessState
    lessons: readonly RequestLessonOption[]
    lessonIds: readonly string[]
    reasons: readonly RequestReasonOption[]
    reason: string | null
    comment: string
    files: readonly RequestFileRef[]
    fileLimits: RequestFileLimits | null
    budget: RequestBudget | null
    lessonsLoading?: boolean
    lessonsError?: string | null
    offline?: boolean
    disabled?: boolean
    submitting?: boolean
    submitError?: string | null
    ambiguous?: boolean
  }
  enlargedText?: boolean
}

type ReviewForm = NonNullable<TodayReviewScenario['form']>

const REFERENCE_DATE = '2026-10-04'
const ACTIVE_LESSON_ID = 'review-programming'
const longSubject = 'Проектирование распределённых информационных систем и высоконагруженных сетевых приложений'
const longRoom = 'Учебный корпус А · аудитория 401 · лаборатория распределённых вычислений'
const longError = 'Не удалось подтвердить отправку: соединение с сервером прервалось во время обработки заявки. Проверь подключение и повтори отправку, чтобы получить подтверждённый результат.'
const longComment = 'Участвовал в заранее согласованном обследовании и не смог присутствовать на занятии. Прикладываю подтверждение; прошу учесть уважительную причину отсутствия. Дополнительные сведения могу предоставить старосте после возвращения в университет.'

const reasons: readonly RequestReasonOption[] = [
  { code: 'ILLNESS', label: 'Болезнь', commentRequired: false },
  { code: 'MEDICAL_EXAMINATION', label: 'Медицинское обследование', commentRequired: false },
  { code: 'COMPETITION_PARTICIPATION', label: 'Участие в соревнованиях', commentRequired: false },
  { code: 'FAMILY_CIRCUMSTANCES', label: 'Семейные обстоятельства', commentRequired: false },
  { code: 'OTHER', label: 'Другое', commentRequired: true },
]
const fileLimits: RequestFileLimits = {
  maxFiles: 2,
  maxBytesPerFile: 10 * 1024 * 1024,
  maxBytesTotal: 20 * 1024 * 1024,
  contentTypes: ['image/jpeg', 'image/png', 'application/pdf'],
  extensions: ['.jpg', '.jpeg', '.png', '.pdf'],
}
const budget: RequestBudget = { semesterId: 'review-semester', limit: 3, used: 1, remaining: 2 }
const attachments: readonly RequestFileRef[] = [
  { id: 'review-file-1', name: 'Подтверждение_уважительной_причины_отсутствия_на_занятии_04_октября_2026.pdf', size: 2_457_600, type: 'application/pdf', lastModified: 0 },
  { id: 'review-file-2', name: 'Скан_справки_для_старосты_и_учебного_отдела_оборотная_сторона.jpg', size: 1_228_800, type: 'image/jpeg', lastModified: 0 },
]

/** Supplying now makes the complete catalog reproducible, including the 04:36 timer. */
export function createTodayReviewScenarios(now = Date.now()): readonly TodayReviewScenario[] {
  const serverNow = new Date(now).toISOString()
  const retryAt = new Date(now + 276_000).toISOString()
  const attendanceMarkedAt = new Date(now - 60_000).toISOString()
  const math: TodayLesson = {
    schedule: {
      id: 'review-math', date: REFERENCE_DATE, lessonNumber: 1,
      startsAt: '09:00:00', endsAt: '10:30:00', status: 'CLOSED',
      subject: { id: 'review-subject-math', name: 'Математический анализ', type: 'PRACTICE' },
      room: { current: 'А-312', previous: null, changeState: 'UNCHANGED' },
    },
    attendance: { status: 'PRESENT', source: 'STUDENT_GEO', markedAt: attendanceMarkedAt },
    request: null,
    checkinEligibility: { allowed: false, reason: 'ALREADY_PRESENT', retryAt: null },
  }
  const programming: TodayLesson = {
    schedule: {
      id: ACTIVE_LESSON_ID, date: REFERENCE_DATE, lessonNumber: 2,
      startsAt: '10:40:00', endsAt: '12:10:00', status: 'ACTIVE',
      subject: { id: 'review-subject-programming', name: 'Основы программирования', type: 'LECTURE' },
      room: { current: 'А-401', previous: null, changeState: 'UNCHANGED' },
    },
    attendance: null, request: null,
    checkinEligibility: { allowed: true, reason: 'ELIGIBLE', retryAt: null },
  }
  const networks: TodayLesson = {
    schedule: {
      id: 'review-networks', date: REFERENCE_DATE, lessonNumber: 3,
      startsAt: '12:20:00', endsAt: '13:50:00', status: 'PLANNED',
      subject: { id: 'review-subject-networks', name: 'Компьютерные сети', type: 'LAB' },
      room: { current: 'А-508', previous: 'А-214', changeState: 'CHANGED' },
    },
    attendance: null, request: null,
    checkinEligibility: { allowed: false, reason: 'TOO_EARLY', retryAt: null },
  }
  function todayWith(active: TodayLesson = programming, first: TodayLesson = math, next: TodayLesson = networks): StudentToday {
    return { date: REFERENCE_DATE, serverNow, timeZone: 'Europe/Moscow', lessons: [first, active, next], _links: {} }
  }
  const today = todayWith()
  const networksUnchanged: TodayLesson = { ...networks, schedule: { ...networks.schedule, room: { current: 'А-214', previous: null, changeState: 'UNCHANGED' } } }
  const pendingRequest: TodayLesson['request'] = {
    id: 'review-auto-geo', origin: 'AUTO_GEO_FAILURE', status: 'PENDING', resolutionReason: null,
  }
  const pending: TodayLesson = {
    ...programming, request: pendingRequest,
    checkinEligibility: { allowed: false, reason: 'COOLDOWN', retryAt },
  }
  const absent: TodayLesson = {
    ...math,
    attendance: { status: 'ABSENT', source: 'SYSTEM', markedAt: attendanceMarkedAt },
    checkinEligibility: { allowed: false, reason: 'WINDOW_CLOSED', retryAt: null },
  }
  const absenceToday = { ...todayWith(programming, absent), lessons: [absent, programming] }
  function optionFor(lesson: TodayLesson): RequestLessonOption {
    return {
      lesson: {
        id: lesson.schedule.id, lessonNumber: lesson.schedule.lessonNumber, status: 'ABSENT', blocked: false,
        subjectId: lesson.schedule.subject.id, subjectName: lesson.schedule.subject.name,
        subjectType: lesson.schedule.subject.type, semesterId: 'review-semester', date: lesson.schedule.date,
        startsAt: lesson.schedule.startsAt, endsAt: lesson.schedule.endsAt,
      },
      excuseEligible: true, lateCheckinEligible: true, unavailableReason: null, pendingRequests: [],
    }
  }
  const requestOptions: readonly RequestLessonOption[] = [optionFor(absent)]
  const form: ReviewForm = {
    access: 'allowed', lessons: requestOptions, lessonIds: [absent.schedule.id],
    reasons, reason: 'ILLNESS', comment: '', files: [], fileLimits, budget,
  }
  function scene(id: string, label: string, patch: Partial<TodayReviewScenario> = {}): TodayReviewScenario {
    return { id, label, kind: 'today', today, requestOptions, ...patch }
  }
  function formScene(
    id: string,
    label: string,
    kind: 'excuse' | 'late',
    patch: Partial<ReviewForm> = {},
    options: Partial<TodayReviewScenario> = {},
  ): TodayReviewScenario {
    return { id, label, kind, today: absenceToday, requestOptions, ...options, form: { ...form, ...patch } }
  }
  function blocked(reason: TodayLesson['checkinEligibility']['reason'], patch: Partial<TodayLesson> = {}): TodayLesson {
    return { ...programming, checkinEligibility: { allowed: false, reason, retryAt: null }, ...patch }
  }
  const confirmed: TodayLesson = {
    ...programming,
    attendance: { status: 'PRESENT', source: 'STUDENT_GEO', markedAt: attendanceMarkedAt },
    checkinEligibility: { allowed: false, reason: 'ALREADY_PRESENT', retryAt: null },
  }
  const longLesson: TodayLesson = {
    ...absent,
    schedule: {
      ...absent.schedule,
      subject: { ...absent.schedule.subject, name: longSubject },
      room: { current: longRoom, previous: 'Корпус Б · аудитория 214', changeState: 'CHANGED' },
    },
  }
  const longOptions: readonly RequestLessonOption[] = [optionFor(longLesson)]
  const semesterSchedule: StudentSemesterSchedule = {
    semester: { id: 'review-semester', name: 'Осенний семестр', startsOn: '2026-09-01', endsOn: '2026-12-31' },
    dateFrom: '2026-09-01', dateTo: '2026-12-31', updatedAt: serverNow, _links: {},
    lessons: [math.schedule, programming.schedule, { ...longLesson.schedule, id: 'review-next-date', date: '2026-10-05' }],
  }
  const sentOptions: readonly RequestLessonOption[] = [{
    ...optionFor(absent), excuseEligible: false, lateCheckinEligible: false,
    unavailableReason: 'По этой паре уже есть заявка',
    pendingRequests: [{ id: 'review-manual-excuse', kind: 'EXCUSE', origin: 'MANUAL' }],
  }]
  const scenarios: TodayReviewScenario[] = [
    // The six owner reference compositions stay first and keep stable identifiers.
    scene('default', 'Сегодня · текущая пара'),
    scene('geo-pending-cooldown', 'Геопроверка · запрос старосте и 04:36', { today: todayWith(pending, math, networksUnchanged) }),
    scene('confirmed', 'Геопроверка · присутствие подтверждено', { today: todayWith(confirmed, math, networksUnchanged) }),
    scene('absence-actions', 'Отсутствие · действия раскрыты', { today: absenceToday, expandedLessonId: absent.schedule.id }),
    formScene('excuse-form', 'Уважительная причина · готовая форма', 'excuse'),
    scene('request-sent', 'Заявка · подтверждение отправки (визуальный пример)', {
      today: absenceToday, task: true, requestOptions: sentOptions, form: { ...form, lessons: sentOptions },
    }),
    scene('today-loading', 'Сегодня · загрузка', { today: null, loading: true }),
    scene('today-error', 'Сегодня · ошибка загрузки', { today: null, error: 'Не удалось загрузить расписание. Проверь подключение и попробуй снова.' }),
    scene('today-error-with-cache', 'Сегодня · ошибка обновления сохранённых данных', { error: 'Не удалось обновить расписание. Показаны сохранённые данные.' }),
    scene('today-empty', 'Сегодня · пар нет', { today: { ...today, lessons: [] } }),
    scene('today-between-classes', 'Сегодня · между парами', {
      today: todayWith({ ...programming, schedule: { ...programming.schedule, status: 'CLOSED' }, checkinEligibility: { allowed: false, reason: 'WINDOW_CLOSED', retryAt: null } }),
    }),
    scene('today-all-ended', 'Сегодня · все пары закончились', {
      today: { ...today, lessons: today.lessons.map((lesson) => ({ ...lesson, schedule: { ...lesson.schedule, status: 'CLOSED' }, checkinEligibility: { allowed: false, reason: 'WINDOW_CLOSED', retryAt: null } })) },
    }),
    scene('today-cancelled', 'Сегодня · пара отменена', {
      today: todayWith(blocked('LESSON_CANCELLED', { schedule: { ...programming.schedule, status: 'CANCELLED' } })),
    }),
    scene('today-excused', 'Сегодня · уважительная причина принята', {
      today: todayWith(blocked('WINDOW_CLOSED', { attendance: { status: 'EXCUSED', source: 'HEADMAN', markedAt: attendanceMarkedAt } })),
    }),
    scene('today-too-early', 'Сегодня · отметка ещё не открыта', {
      today: { ...today, lessons: [blocked('TOO_EARLY', { schedule: { ...programming.schedule, status: 'PLANNED' } }), networks] },
    }),
    scene('today-window-closed', 'Сегодня · окно отметки закрыто', { today: todayWith(blocked('WINDOW_CLOSED')) }),
    scene('today-geo-blocked', 'Сегодня · геопроверка недоступна', { today: todayWith(blocked('GEO_BLOCKED')) }),
    scene('today-manual-absence', 'Сегодня · отсутствие поставлено старостой', {
      today: todayWith(blocked('HEADMAN_ABSENT_REQUIRES_APPEAL', { attendance: { status: 'ABSENT', source: 'HEADMAN', markedAt: attendanceMarkedAt } })),
      expandedLessonId: ACTIVE_LESSON_ID,
      requestOptions: [{ ...optionFor(programming), lateCheckinEligible: false, unavailableReason: 'Отсутствие поставлено старостой; доступна уважительная причина' }],
    }),
    scene('today-pending-without-retry', 'Сегодня · запрос ждёт решения без повтора', {
      today: todayWith(blocked('PENDING_CONFIRMATION', { request: pendingRequest })),
    }),
    scene('today-retry-eligible', 'Сегодня · повтор разрешён сервером после таймера', {
      today: todayWith({ ...pending, checkinEligibility: { allowed: true, reason: 'ELIGIBLE', retryAt: null } }),
    }),
    scene('today-cooldown-expired', 'Сегодня · таймер закончился до следующего обновления', {
      today: todayWith({ ...pending, checkinEligibility: { allowed: false, reason: 'COOLDOWN', retryAt: new Date(now - 1000).toISOString() } }),
    }),
    scene('today-manual-request-pending', 'Сегодня · ручная заявка ждёт решения', {
      today: todayWith(blocked('PENDING_CONFIRMATION', { attendance: { status: 'ABSENT', source: 'SYSTEM', markedAt: attendanceMarkedAt } })),
      expandedLessonId: ACTIVE_LESSON_ID,
      requestOptions: [{ ...optionFor(programming), excuseEligible: false, lateCheckinEligible: false, pendingRequests: [{ id: 'review-late-pending', kind: 'LATE_CHECKIN', origin: 'MANUAL' }] }],
    }),
    scene('today-cooldown-without-request', 'Сегодня · таймер без ожидающей заявки', {
      today: todayWith(blocked('COOLDOWN', { checkinEligibility: { allowed: false, reason: 'COOLDOWN', retryAt } })),
    }),
    scene('today-dependency-unavailable', 'Сегодня · сервис геопроверки недоступен', { today: todayWith(blocked('DEPENDENCY_UNAVAILABLE')) }),
    scene('today-journal-only', 'Сегодня · отметка через журнал', { today: todayWith(blocked('HEADMAN_USES_JOURNAL')) }),
    scene('today-offline-cache', 'Сегодня · офлайн с сохранёнными данными', { offline: true }),
    scene('today-offline-no-cache', 'Сегодня · офлайн без сохранённых данных', { today: null, offline: true }),
    scene('today-read-only', 'Сегодня · доступ только для чтения', { readOnly: true }),
    scene('today-gps-acquiring', 'Сегодня · получение геолокации', { acquiringLessonId: ACTIVE_LESSON_ID }),
    scene('today-gps-submitting', 'Сегодня · отправка отметки', { submittingLessonId: ACTIVE_LESSON_ID }),
    scene('today-checkin-error', 'Сегодня · ошибка отметки', { checkinError: 'Не удалось получить геопозицию. Разреши доступ к местоположению и повтори попытку.' }),
    scene('today-actions-loading', 'Отсутствие · варианты заявки загружаются', { today: absenceToday, expandedLessonId: absent.schedule.id, requestOptions: [], optionsLoading: true }),
    scene('today-actions-error', 'Отсутствие · ошибка вариантов заявки', { today: absenceToday, expandedLessonId: absent.schedule.id, requestOptions: [], optionsError: 'Не удалось получить доступные действия. Попробуй снова.' }),
    scene('today-actions-unavailable', 'Отсутствие · обе заявки недоступны', {
      today: absenceToday, expandedLessonId: absent.schedule.id,
      requestOptions: [{ ...optionFor(absent), excuseEligible: false, lateCheckinEligible: false, unavailableReason: 'Срок подачи заявки истёк' }],
    }),
    scene('today-room-unknown', 'Сегодня · аудитория уточняется', {
      today: todayWith({ ...programming, schedule: { ...programming.schedule, room: { current: null, previous: null, changeState: 'UNKNOWN' } } }),
    }),
    scene('today-long-content', 'Сегодня · длинные предмет, аудитория и ошибка', { today: todayWith(programming, longLesson), checkinError: longError, expandedLessonId: absent.schedule.id, requestOptions: longOptions }),
    scene('today-enlarged-text', 'Сегодня · увеличенный текст', { enlargedText: true }),
    scene('today-semester', 'Сегодня · расписание семестра', { semesterSchedule }),
    scene('today-semester-empty', 'Сегодня · пустое расписание семестра', { semesterSchedule: { ...semesterSchedule, lessons: [] } }),
    formScene('excuse-missing-reason', 'Уважительная причина · причина не выбрана', 'excuse', { reason: null }),
    formScene('excuse-other-comment-required', 'Уважительная причина · «Другое» без комментария', 'excuse', { reason: 'OTHER' }),
    formScene('excuse-validation', 'Уважительная причина · пара и причина не выбраны', 'excuse', { lessonIds: [], reason: null }),
    formScene('excuse-retained-invalid-reason', 'Уважительная причина · прежняя причина недоступна', 'excuse', { reasons: reasons.filter((reason) => reason.code !== 'ILLNESS') }),
    formScene('excuse-reasons-unavailable', 'Уважительная причина · причины недоступны', 'excuse', { reasons: [], reason: null }),
    formScene('excuse-attachments', 'Уважительная причина · два файла с длинными именами', 'excuse', { files: attachments, comment: longComment }),
    formScene('excuse-long-content', 'Уважительная причина · длинный текст и ошибка', 'excuse', { lessons: longOptions, comment: longComment, reason: 'OTHER', files: attachments, submitError: longError }, { today: todayWith(programming, longLesson), requestOptions: longOptions }),
    formScene('excuse-enlarged-text', 'Уважительная причина · увеличенный текст', 'excuse', { comment: longComment }, { enlargedText: true }),
    formScene('excuse-retained-comment-too-long', 'Уважительная причина · сохранённый комментарий длиннее лимита', 'excuse', { comment: 'А'.repeat(1001) }),
    formScene('late-form', 'Забыл отметиться · готовая форма', 'late'),
    formScene('late-budget-exhausted', 'Забыл отметиться · попытки закончились', 'late', { budget: { ...budget, used: 3, remaining: 0 } }),
    formScene('late-budget-unknown', 'Забыл отметиться · лимит пока неизвестен', 'late', { budget: null }),
    formScene('late-no-selection', 'Забыл отметиться · пара не выбрана', 'late', { lessonIds: [] }),
    formScene('late-retained-unavailable-lesson', 'Забыл отметиться · прежняя пара недоступна', 'late', { lessons: [], lessonIds: ['review-removed-lesson'] }),
    formScene('late-long-content', 'Забыл отметиться · длинный предмет и ошибка', 'late', { lessons: longOptions, submitError: longError }, { today: todayWith(programming, longLesson), requestOptions: longOptions }),
    formScene('late-enlarged-text', 'Забыл отметиться · увеличенный текст', 'late', {}, { enlargedText: true }),
  ]
  // Shared form branches are exercised once in each actual renderer, without a Cartesian grid.
  for (const kind of ['excuse', 'late'] satisfies readonly ('excuse' | 'late')[]) {
    const title = kind === 'excuse' ? 'Уважительная причина' : 'Забыл отметиться'
    scenarios.push(
      formScene(`${kind}-options-loading`, `${title} · загрузка вариантов`, kind, { lessons: [], lessonIds: [], reasons: [], lessonsLoading: true }),
      formScene(`${kind}-options-error`, `${title} · ошибка вариантов`, kind, { lessons: [], lessonsError: 'Не удалось загрузить пары для заявки. Попробуй снова.' }),
      formScene(`${kind}-options-empty`, `${title} · доступных пар нет`, kind, { lessons: [], lessonIds: [] }),
      formScene(`${kind}-selected-ineligible`, `${title} · выбранная пара недоступна`, kind, { lessons: sentOptions }),
      formScene(`${kind}-submit-error`, `${title} · ошибка отправки`, kind, { submitError: 'Не удалось отправить заявку. Сохранили заполненную форму — попробуй снова.' }),
      formScene(`${kind}-submitting`, `${title} · отправка`, kind, { submitting: true }),
      formScene(`${kind}-ambiguous`, `${title} · результат отправки неизвестен`, kind, { ambiguous: true, submitError: longError }),
      formScene(`${kind}-offline`, `${title} · офлайн с заполненным черновиком`, kind, { offline: true, comment: longComment }),
      formScene(`${kind}-read-only`, `${title} · доступ только для чтения`, kind, { disabled: true }),
      formScene(`${kind}-forbidden`, `${title} · нет доступа`, kind, { access: 'forbidden' }),
      formScene(`${kind}-no-semester`, `${title} · нет активного семестра`, kind, { access: 'no-active-semester' }),
    )
  }
  return scenarios
}

export function selectedTodayLesson(scenario: TodayReviewScenario): TodayLesson | null {
  const selectedId = scenario.form?.lessonIds[0]
  return scenario.today?.lessons.find((lesson) => lesson.schedule.id === selectedId) ?? null
}
