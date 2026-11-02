import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { message } from '../../auth/session'
import { useWebLiveRefresh } from '../../app/live'
import { HeadmanJournalApiError, type HeadmanJournalLesson, type HeadmanJournalReport, type HeadmanJournalRosterEntry, type HeadmanJournalMarkCommand, type HeadmanJournalMarkCondition, type HeadmanJournalExcuseType } from '../../../../mobile-core/src/features/headman-journal/headman-journal-client'
import type { HeadmanLessonAction } from '../../../../mobile-core/src/features/headman-journal/headman-journal-actions'
import type { HeadmanSubject } from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client'
import type { HeadmanManagedHomework, HeadmanHomeworkSemester } from '../../../../mobile-core/src/features/homework/headman-homework-client'
import type { HeadmanRequestPage, HeadmanRequestSummary } from '../../../../mobile-core/src/features/headman-requests/headman-requests-client'
import { LessonActionsReader, markReadbackMatches } from './journal-state'
import { today, type HeadmanFeature } from './context'

const requestDay = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Moscow', year: 'numeric', month: '2-digit', day: '2-digit' })
/** The OPEN endpoint is created_at DESC; each type already contains at most three latest rows. */
export function homeRequestsCreatedToday(page: HeadmanRequestPage | null, now: number): readonly HeadmanRequestSummary[] {
  if (!page || !Number.isFinite(now)) return []
  const day = requestDay.format(new Date(now))
  return page.content.filter(item => {
    if (item.status !== 'PENDING' || !item.createdAt) return false
    const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?(Z|[+-]\d{2}:\d{2})$/.exec(item.createdAt)
    if (!match || Number(match[2]) > 23 || Number(match[3]) > 59 || Number(match[4]) > 59) return false
    const civil = new Date(`${match[1]}T00:00:00Z`), instant = Date.parse(item.createdAt)
    return Number.isFinite(civil.getTime()) && civil.toISOString().slice(0, 10) === match[1] &&
      Number.isFinite(instant) && instant <= now && requestDay.format(new Date(instant)) === day
  }).slice(0, 3)
}

type HomeMarkStatus = 'PRESENT' | 'EXCUSED' | 'ABSENT'
type HomeMarkTarget = Readonly<{ lesson: HeadmanJournalLesson; entry: HeadmanJournalRosterEntry }>
type HomeMarkPending = HomeMarkTarget & Readonly<{ command: HeadmanJournalMarkCommand | null; condition: HeadmanJournalMarkCondition }>

/** Each panel owns its selection and reads; the ongoing counters never follow a pager. */
export function useHeadmanHome(feature: HeadmanFeature) {
  const loading = ref(false), error = ref<string | null>(null)
  const lessons = shallowRef<readonly HeadmanJournalLesson[]>([]), subjects = shallowRef<readonly HeadmanSubject[]>([])
  const semester = shallowRef<HeadmanHomeworkSemester | null>(null)
  const ongoing = shallowRef<HeadmanJournalLesson | null>(null), currentReport = shallowRef<HeadmanJournalReport | null>(null), currentError = ref<string | null>(null)
  const manualId = ref(0), manualReport = shallowRef<HeadmanJournalReport | null>(null), manualLoading = ref(false), manualError = ref<string | null>(null)
  const auditId = ref(0), audit = shallowRef<readonly HeadmanLessonAction[]>([]), cursor = ref<string | null>(null), auditLoading = ref(false), auditError = ref<string | null>(null)
  const homeworks = shallowRef<readonly HeadmanManagedHomework[] | null>(null), homeworkError = ref<string | null>(null)
  const excuses = shallowRef<HeadmanRequestPage | null>(null), late = shallowRef<HeadmanRequestPage | null>(null), excuseError = ref<string | null>(null), lateError = ref<string | null>(null)
  const blockageBusy = ref(false), blockageError = ref<string | null>(null), blockageNotice = ref('')
  const blockagePending = shallowRef<{ lesson: HeadmanJournalLesson; blocked: boolean } | null>(null)
  const blockageLocked = computed(() => blockageBusy.value || blockagePending.value !== null)
  const markBusy = ref(false), markError = ref<string | null>(null), markNotice = ref('')
  const markPending = shallowRef<HomeMarkPending | null>(null), excuseDraft = shallowRef<HomeMarkTarget | null>(null)
  const excuseType = ref<HeadmanJournalExcuseType | ''>('')
  const markReadback = shallowRef<HomeMarkPending | null>(null)
  const markCanAccept = computed(() => !!markPending.value && markReadback.value === markPending.value && !markBusy.value)
  const interactionLocked = computed(() => blockageLocked.value || markBusy.value || !!markPending.value || !!excuseDraft.value)
  const clock = ref(Date.now())
  const todayExcuses = computed(() => homeRequestsCreatedToday(excuses.value, clock.value))
  const todayLate = computed(() => homeRequestsCreatedToday(late.value, clock.value))
  const manualLesson = computed(() => lessons.value.find((lesson) => lesson.id === manualId.value) ?? null)
  const canChangeBlockage = computed(() => {
    const lesson = manualLesson.value, period = semester.value
    return feature.isHeadman && feature.writable() && !!period?.active && !!lesson?.current &&
      lesson.groupId === feature.groupId && lesson.date === loadedDay && lesson.date === today() &&
      (!period.dateFrom || lesson.date >= period.dateFrom) && (!period.dateTo || lesson.date <= period.dateTo) &&
      ['PLANNED', 'ACTIVE'].includes(lesson.status) && typeof lesson.blockedByHeadman === 'boolean' &&
      !loading.value && !manualLoading.value && !auditLoading.value && !interactionLocked.value
  })
  const auditLesson = computed(() => lessons.value.find((lesson) => lesson.id === auditId.value) ?? null)
  const orderedAudit = computed(() => [...new Map(audit.value.map((item) => [item.id, item])).values()].sort((a, b) => Date.parse(b.occurredAt) - Date.parse(a.occurredAt)))
  const progress = computed(() => {
    const lesson = ongoing.value
    if (!lesson?.startTime || !lesson.endTime) return null
    const start = Date.parse(`${lesson.date}T${lesson.startTime}+03:00`), end = Date.parse(`${lesson.date}T${lesson.endTime}+03:00`)
    return Number.isFinite(start) && end > start ? Math.max(0, Math.min(100, (clock.value - start) / (end - start) * 100)) : null
  })
  const actionsReader = new LessonActionsReader(feature.actions)
  let alive = true, revision = 0, manualRevision = 0, auditRevision = 0
  let loadedDay: string | null = null
  let timer: ReturnType<typeof setInterval> | null = null
  async function readManual(id: number, sharedReport?: HeadmanJournalReport | null) {
    if (interactionLocked.value) return
    const run = ++manualRevision
    manualId.value = id
    if (manualReport.value?.lessonId !== id) manualReport.value = null
    manualError.value = null
    if (!id) { manualLoading.value = false; return }
    manualLoading.value = true
    try {
      const value = sharedReport?.lessonId === id ? sharedReport : await feature.journal.getLessonAttendance(id)
      feature.assertCurrent()
      if (value.lessonId !== id) throw new Error('Сервер вернул отметки другой пары.')
      if (alive && run === manualRevision && manualId.value === id) manualReport.value = value
    } catch (cause) {
      if (alive && run === manualRevision) manualError.value = message(cause)
    } finally {
      if (alive && run === manualRevision) manualLoading.value = false
    }
  }
  async function readAudit(id: number, more = false) {
    if (interactionLocked.value) return
    if (more && (auditLoading.value || !cursor.value)) return
    const run = ++auditRevision
    const previous = more && auditId.value === id ? audit.value : []
    const next = more && auditId.value === id ? cursor.value : null
    if (auditId.value !== id) { audit.value = []; cursor.value = null }
    auditId.value = id
    auditError.value = null
    if (!id) { auditLoading.value = false; return }
    auditLoading.value = true
    try {
      const result = await actionsReader.read(id, next, previous)
      feature.assertCurrent()
      if (alive && run === auditRevision && result && auditId.value === id) {
        audit.value = result.items
        cursor.value = result.cursor
      }
    } catch (cause) {
      if (alive && run === auditRevision) auditError.value = message(cause)
    } finally {
      if (alive && run === auditRevision) auditLoading.value = false
    }
  }
  function step(panel: 'manual' | 'audit', direction: -1 | 1) {
    if (loading.value || interactionLocked.value) return
    const id = panel === 'manual' ? manualId.value : auditId.value
    const index = lessons.value.findIndex((lesson) => lesson.id === id)
    const next = lessons.value[index + direction]
    if (next) void (panel === 'manual' ? readManual(next.id) : readAudit(next.id))
  }
  function reset(panel: 'manual' | 'audit') {
    if (loading.value || interactionLocked.value) return
    const id = ongoing.value?.id ?? lessons.value[0]?.id ?? 0
    void (panel === 'manual' ? readManual(id) : readAudit(id))
  }
  async function load() {
    if (interactionLocked.value) return
    const run = ++revision
    loading.value = true
    error.value = null
    try {
      await feature.initialize()
      const day = today()
      const [dayLessons, subjectList, period] = await Promise.all([
        feature.journal.listLessons(feature.groupId, day, day), feature.subjects.listSubjects(feature.groupId), feature.homework.activeSemester(),
      ])
      feature.assertCurrent()
      if (!alive || run !== revision) return
      const contextChanged = semester.value?.id !== period?.id || (loadedDay !== null && loadedDay !== day)
      loadedDay = day
      lessons.value = [...dayLessons].sort((a, b) => (a.lessonNumber ?? 0) - (b.lessonNumber ?? 0) || (a.startTime ?? '').localeCompare(b.startTime ?? ''))
      subjects.value = subjectList
      semester.value = period
      ongoing.value = dayLessons.find((lesson) => lesson.status === 'ACTIVE') ?? null
      if (contextChanged || currentReport.value?.lessonId !== ongoing.value?.id) currentReport.value = null
      currentError.value = null
      const active = ongoing.value
      const initial = active?.id ?? lessons.value[0]?.id ?? 0
      const manual = lessons.value.some((lesson) => lesson.id === manualId.value) ? manualId.value : initial
      const selectedAudit = lessons.value.some((lesson) => lesson.id === auditId.value) ? auditId.value : initial
      // Retained rows belong to one day/semester/lesson, never to a new pager label.
      if (contextChanged || manual !== manualId.value) {
        manualRevision++
        manualId.value = manual
        manualReport.value = null
        manualError.value = null
        manualLoading.value = false
      }
      if (contextChanged || selectedAudit !== auditId.value) {
        auditRevision++
        auditId.value = selectedAudit
        audit.value = []
        cursor.value = null
        auditError.value = null
        auditLoading.value = false
      }
      if (contextChanged) { homeworks.value = null; homeworkError.value = null }
      const [attendance, homework, excusePage, latePage] = await Promise.allSettled([
        active ? feature.journal.getLessonAttendance(active.id) : Promise.resolve(null),
        period ? feature.homework.listHomeworks(feature.groupId, period.id) : Promise.resolve(null),
        feature.requests.list({ bucket: 'OPEN', type: 'EXCUSE', size: 3 }),
        feature.requests.list({ bucket: 'OPEN', type: 'LATE_CHECKIN', size: 3 }),
      ])
      feature.assertCurrent()
      if (!alive || run !== revision) return
      if (attendance.status === 'fulfilled') currentReport.value = attendance.value
      else currentError.value = message(attendance.reason)
      if (homework.status === 'fulfilled') { homeworks.value = homework.value?.filter((item) => item.lessonDate === day).sort((a, b) => (a.lessonNumber ?? 0) - (b.lessonNumber ?? 0)) ?? []; homeworkError.value = null }
      else homeworkError.value = message(homework.reason)
      if (excusePage.status === 'fulfilled') { excuses.value = excusePage.value; excuseError.value = null }
      else excuseError.value = message(excusePage.reason)
      if (latePage.status === 'fulfilled') { late.value = latePage.value; lateError.value = null }
      else lateError.value = message(latePage.reason)
      await Promise.all([readManual(manual, attendance.status === 'fulfilled' ? attendance.value : null), readAudit(selectedAudit)])
    } catch (cause) {
      if (alive && run === revision) {
        const detail = message(cause)
        error.value = detail
        if (!currentReport.value) currentError.value = detail
        if (!manualReport.value) manualError.value = detail
        if (!audit.value.length) auditError.value = detail
        if (homeworks.value === null) homeworkError.value = detail
        if (!excuses.value) excuseError.value = detail
        if (!late.value) lateError.value = detail
      }
    } finally {
      if (alive && run === revision) loading.value = false
    }
  }
  function canChooseMark(entry: HeadmanJournalRosterEntry, status: HomeMarkStatus) {
    const lesson = manualLesson.value, period = semester.value, report = manualReport.value
    const permission = entry.status === 'EXCUSED' || status === 'EXCUSED' ? 'MANAGE_EXCUSES' : 'MARK_ATTENDANCE'
    return !!lesson && !!period?.active && feature.writable(permission) && lesson.groupId === feature.groupId &&
      lesson.current && !['CANCELLED', 'TRANSFERRED', 'UNSUPPORTED'].includes(lesson.status) &&
      (!period.dateFrom || lesson.date >= period.dateFrom) && (!period.dateTo || lesson.date <= period.dateTo) &&
      report?.lessonId === lesson.id && report.editable && entry.editable && !entry.approvedTicket &&
      ['EMPTY', 'PRESENT', 'ABSENT', 'EXCUSED'].includes(entry.status) && (entry.status === 'EMPTY' || !!entry.markRevision)
  }
  function currentMarkTarget(owner: HomeMarkTarget) {
    if (manualLesson.value?.id !== owner.lesson.id || manualLesson.value?.date !== owner.lesson.date || manualLesson.value?.occurrenceId !== owner.lesson.occurrenceId) return null
    const entry = manualReport.value?.entries.find(item => item.userId === owner.entry.userId)
    return entry && entry.status === owner.entry.status && entry.markRevision === owner.entry.markRevision ? entry : null
  }
  async function chooseMark(entry: HeadmanJournalRosterEntry, status: HomeMarkStatus) {
    const lesson = manualLesson.value
    if (!lesson || interactionLocked.value || loading.value || manualLoading.value || auditLoading.value || !canChooseMark(entry, status)) return
    const owner = Object.freeze({ lesson, entry: Object.freeze({ ...entry }) })
    if (!currentMarkTarget(owner)) { markError.value = 'Отметка изменилась. Обнови список перед следующим действием.'; return }
    markError.value = null
    markNotice.value = ''
    if (status === 'EXCUSED' && entry.status !== 'EXCUSED') {
      excuseDraft.value = owner
      excuseType.value = ''
      return
    }
    await submitMark(owner, entry.status === status ? null : { status: status as 'PRESENT' | 'ABSENT' })
  }
  function closeExcuse() {
    if (markBusy.value || markPending.value) return
    excuseDraft.value = null
    excuseType.value = ''
    markError.value = null
  }
  async function saveExcuse() {
    const owner = excuseDraft.value
    if (!owner || !excuseType.value || markBusy.value || markPending.value || blockageLocked.value || !currentMarkTarget(owner) || !canChooseMark(owner.entry, 'EXCUSED')) return
    await submitMark(owner, { status: 'EXCUSED', excuseType: excuseType.value })
  }
  async function readPendingMark(pending: HomeMarkPending) {
    const report = await feature.journal.getLessonAttendance(pending.lesson.id)
    feature.assertCurrent()
    if (!alive || markPending.value !== pending) return false
    if (report.lessonId !== pending.lesson.id || (report.groupId !== null && report.groupId !== pending.lesson.groupId) || (report.lessonDate !== null && report.lessonDate !== pending.lesson.date) || manualLesson.value?.id !== pending.lesson.id || manualLesson.value?.date !== pending.lesson.date)
      throw new Error('Сервер вернул отметки другой пары.')
    markReadback.value = pending
    manualReport.value = report
    manualError.value = null
    if (ongoing.value?.id === pending.lesson.id && ongoing.value.date === pending.lesson.date) { currentReport.value = report; currentError.value = null }
    const entry = report.entries.find(item => item.userId === pending.entry.userId)
    if (!markReadbackMatches(entry, pending.command)) {
      markError.value = 'Отправленное изменение пока не подтверждено. Показана текущая отметка. Проверь результат ещё раз; повторная запись не отправляется.'
      return false
    }
    markPending.value = null
    markReadback.value = null
    excuseDraft.value = null
    excuseType.value = ''
    markError.value = null
    markNotice.value = pending.command ? `Отметка ${pending.entry.displayName} сохранена.` : `Отметка ${pending.entry.displayName} снята.`
    return true
  }
  async function submitMark(owner: HomeMarkTarget, command: HeadmanJournalMarkCommand | null) {
    const expectedStatus = owner.entry.status
    if (markBusy.value || markPending.value || blockageLocked.value || !currentMarkTarget(owner) || !canChooseMark(owner.entry, command?.status ?? expectedStatus as HomeMarkStatus) || !['EMPTY', 'PRESENT', 'ABSENT', 'EXCUSED'].includes(expectedStatus)) return
    if (!command && expectedStatus === 'EMPTY') return
    const pending: HomeMarkPending = Object.freeze({ ...owner, command: command ? Object.freeze({ ...command }) : null, condition: Object.freeze({ expectedStatus: expectedStatus as HeadmanJournalMarkCondition['expectedStatus'], expectedRevision: expectedStatus === 'EMPTY' ? 'EMPTY' : owner.entry.markRevision! }) })
    markPending.value = pending
    markReadback.value = null
    markBusy.value = true
    markError.value = null
    let confirmed = false, acknowledged = false
    try {
      feature.assertCurrent()
      if (pending.command) {
        const ack = await feature.journal.mark(owner.lesson.id, owner.entry.userId, pending.command, pending.condition)
        acknowledged = true
        if (ack.lessonId !== owner.lesson.id || ack.userId !== owner.entry.userId || ack.status !== pending.command.status)
          throw new Error('Сервер вернул подтверждение другой отметки.')
      } else await feature.journal.clear(owner.lesson.id, owner.entry.userId, { expectedStatus: expectedStatus as 'PRESENT' | 'ABSENT' | 'EXCUSED', expectedRevision: pending.condition.expectedRevision })
      acknowledged = true
      feature.assertCurrent()
      confirmed = await readPendingMark(pending)
    } catch (cause) {
      if (!alive) return
      if (!acknowledged && cause instanceof HeadmanJournalApiError && [400, 401, 403, 404, 409, 410, 422].includes(cause.response.status)) {
        markPending.value = null
        markError.value = message(cause)
      } else {
        markError.value = 'Результат изменения неизвестен. Проверь отметку перед следующей записью; повторная запись не отправляется.'
      }
    } finally {
      if (alive) markBusy.value = false
    }
    if (confirmed && alive && auditId.value === owner.lesson.id) void readAudit(owner.lesson.id)
  }
  async function reconcileMark() {
    const pending = markPending.value
    if (!pending || markBusy.value) return
    markBusy.value = true
    markReadback.value = null
    let confirmed = false
    try { confirmed = await readPendingMark(pending) }
    catch (cause) { if (alive) markError.value = `${message(cause)} Результат изменения остаётся неизвестным.` }
    finally { if (alive) markBusy.value = false }
    if (confirmed && alive && auditId.value === pending.lesson.id) void readAudit(pending.lesson.id)
  }
  function acceptCurrentMark() {
    if (!markCanAccept.value) return
    markPending.value = null
    markReadback.value = null
    excuseDraft.value = null
    excuseType.value = ''
    markError.value = null
    markNotice.value = 'Текущее состояние принято. Можно выбрать новое действие.'
  }
  function sameBlockageTarget(actual: HeadmanJournalLesson, expected: HeadmanJournalLesson) {
    return actual.id === expected.id && actual.date === expected.date && actual.groupId === expected.groupId &&
      (!expected.occurrenceId || actual.occurrenceId === expected.occurrenceId)
  }
  function publishBlockage(actual: HeadmanJournalLesson, expected: HeadmanJournalLesson) {
    if (!sameBlockageTarget(actual, expected) || manualId.value !== expected.id || manualLesson.value?.date !== expected.date)
      throw new Error('Сервер вернул состояние другой пары. Проверь выбранную пару.')
    lessons.value = lessons.value.map(lesson => sameBlockageTarget(lesson, expected) ? actual : lesson)
    if (ongoing.value && sameBlockageTarget(ongoing.value, expected)) ongoing.value = actual
  }
  async function changeBlockage() {
    const lesson = manualLesson.value
    if (!lesson || !canChangeBlockage.value) return
    const pending = { lesson, blocked: !lesson.blockedByHeadman }
    blockagePending.value = pending
    blockageBusy.value = true
    blockageError.value = null
    blockageNotice.value = ''
    try {
      const actual = await feature.journal.setHeadmanBlock(lesson.id, pending.blocked)
      feature.assertCurrent()
      if (!alive || blockagePending.value !== pending) return
      publishBlockage(actual, lesson)
      blockagePending.value = null
      blockageNotice.value = pending.blocked ? 'Только ручная отметка включена.' : 'Только ручная отметка выключена.'
    } catch (cause) {
      if (!alive) return
      // A rejected command has no effect; transport/server/decoding failures may have committed.
      if (cause instanceof HeadmanJournalApiError && [400, 401, 403, 404, 409, 410, 422].includes(cause.response.status)) {
        blockagePending.value = null
        blockageError.value = message(cause)
      } else {
        blockageError.value = `${message(cause)} Результат изменения неизвестен. Проверь состояние пары; повторная запись не отправляется.`
      }
    } finally {
      if (alive) blockageBusy.value = false
    }
  }
  async function reconcileBlockage() {
    const pending = blockagePending.value
    if (!pending || blockageBusy.value) return
    blockageBusy.value = true
    try {
      const actual = (await feature.journal.listLessons(feature.groupId, pending.lesson.date, pending.lesson.date))
        .find(lesson => sameBlockageTarget(lesson, pending.lesson))
      feature.assertCurrent()
      if (!alive || blockagePending.value !== pending) return
      if (!actual || typeof actual.blockedByHeadman !== 'boolean')
        throw new Error('Сервер не подтвердил состояние выбранной пары.')
      publishBlockage(actual, pending.lesson)
      if (actual.blockedByHeadman === pending.blocked) {
        blockagePending.value = null
        blockageError.value = null
        blockageNotice.value = pending.blocked ? 'Только ручная отметка включена.' : 'Только ручная отметка выключена.'
      } else {
        blockageError.value = 'Изменение пока не подтверждено. Повторная запись не отправляется; проверь состояние ещё раз.'
      }
    } catch (cause) {
      if (alive) blockageError.value = `${message(cause)} Результат изменения остаётся неизвестным.`
    } finally {
      if (alive) blockageBusy.value = false
    }
  }
  onMounted(() => { void load(); timer = setInterval(() => { clock.value = Date.now() }, 1000) })
  onBeforeUnmount(() => { alive = false; revision++; manualRevision++; auditRevision++; if (timer !== null) clearInterval(timer) })
  useWebLiveRefresh(load, { blocked: () => loading.value || manualLoading.value || auditLoading.value || interactionLocked.value })
  return { interactionLocked, markCanAccept, acceptCurrentMark, markBusy, markError, markNotice, markPending, excuseDraft, excuseType, canChooseMark, chooseMark, closeExcuse, saveExcuse, reconcileMark, blockageBusy, blockageError, blockageNotice, blockagePending, blockageLocked, canChangeBlockage, changeBlockage, reconcileBlockage, loading, error, lessons, subjects, semester, ongoing, currentReport, currentError, manualId, manualLesson, manualReport, manualLoading, manualError, auditId, auditLesson, audit: orderedAudit, cursor, auditLoading, auditError, homeworks, homeworkError, excuses, late, todayExcuses, todayLate, excuseError, lateError, progress, readManual, readAudit, step, reset, load }
}
