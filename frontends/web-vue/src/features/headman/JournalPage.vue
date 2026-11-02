<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch, watchPostEffect } from 'vue';
import { useRoute } from 'vue-router';
import { message } from '../../auth/session';
import AttendanceFilterIllustration from '../../../../shared/illustrations/AttendanceFilterIllustration.vue';
import { useWebLiveRefresh } from '../../app/live';
import {
  HeadmanJournalApiError,
  type HeadmanJournalMarkCondition,
  type HeadmanJournalLesson,
  type HeadmanJournalReport,
  type HeadmanJournalRosterEntry,
  type HeadmanJournalExcuseType,
  type HeadmanJournalMarkCommand,
} from '../../../../mobile-core/src/features/headman-journal/headman-journal-client';
import type {
  HeadmanSubject,
  HeadmanSemester,
} from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client';
import type { HeadmanLessonAction } from '../../../../mobile-core/src/features/headman-journal/headman-journal-actions';
import { academicWeekOrdinal, type AcademicWeekPolicy } from '../../../../mobile-core/src/domain/academic-week';
import {
  addDays,
  dateLabel,
  lessonTypes,
  lessonTypeCode,
  actionLabel,
  today,
  useHeadmanFeature,
  weekStart,
} from './context';
import HeadmanPage from './HeadmanPage.vue';
import JournalExport from './JournalExport.vue';
import HeadmanJournalGrid from './HeadmanJournalGrid.vue';
import StudentPeriodPager from '../student/StudentPeriodPager.vue';
import WebSelect from '../../shared/WebSelect.vue';
import WebDateField from '../../shared/WebDateField.vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import '../student/student.pcss';
import './headman-analytics.pcss';
import { initialExcuseType, markReadbackMatches, LessonActionsReader } from './journal-state';

const feature = useHeadmanFeature();
const route = useRoute();
const loading = ref(false),
  error = ref<string | null>(null),
  busy = ref(false);
const start = ref(
    weekStart(
      typeof route.query.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date)
        ? route.query.date
        : today(),
    ),
  ),
  mode = ref<'week' | 'subject'>('week'),
  subjectId = ref(0),
  selectedTypes = ref<readonly string[] | null>(null);
const subjects = shallowRef<readonly HeadmanSubject[]>([]),
  semesters = shallowRef<readonly HeadmanSemester[]>([]);
const semesterId = ref(0);
const semester = computed(() => semesters.value.find((item) => item.id === semesterId.value));
const weekPolicy = shallowRef<AcademicWeekPolicy | null>(null);
const weekPolicyContext = ref('');
const ordinalDate = computed(() => {
  const from = semester.value?.dateFrom;
  return from && start.value < from && addDays(start.value, 6) >= from ? from : start.value;
});
const weekLabel = computed(() => {
  const ordinal = academicWeekOrdinal(ordinalDate.value, semester.value?.dateFrom ?? null, semester.value?.dateTo ?? null);
  const policy = weekPolicyContext.value === `${semesterId.value}/${start.value}` ? weekPolicy.value : null;
  const parity = policy ? `${policy.parityNumber} (${policy.weekType === 'ODD' ? 'нечётная' : 'чётная'})` : 'чётность —';
  return `${dateLabel(start.value)} — ${dateLabel(addDays(start.value, 6))} · ${ordinal === null ? 'неделя —' : `${ordinal}-я неделя`} · ${parity}`;
});
const lessons = shallowRef<readonly HeadmanJournalLesson[]>([]);
const reports = shallowRef<ReadonlyMap<number, HeadmanJournalReport>>(new Map());
const selected = shallowRef<{
  lesson: HeadmanJournalLesson;
  entry: HeadmanJournalRosterEntry;
} | null>(null);
const audit = shallowRef<readonly HeadmanLessonAction[]>([]),
  auditLesson = ref(0),
  cursor = ref<string | null>(null);
const draft = reactive({
  status: 'PRESENT',
  excuseType: '' as HeadmanJournalExcuseType | '',
  comment: '',
  file: null as File | null,
});
type JournalStatus = 'PRESENT' | 'EXCUSED' | 'ABSENT';
type MarkIntent = Readonly<{ lesson: HeadmanJournalLesson; entry: HeadmanJournalRosterEntry; command: HeadmanJournalMarkCommand | null; condition: HeadmanJournalMarkCondition }>;
const uncertain = ref(false), detail = ref<HTMLDialogElement | null>(null);
const modalOpen = ref(false), inlineRequest = ref(false), markReadback = shallowRef<MarkIntent | null>(null), pendingMark = shallowRef<MarkIntent | null>(null);
const canAcceptMark = computed(() => !!pendingMark.value && markReadback.value === pendingMark.value && !busy.value);
const clock = ref(Date.now()), timingRefreshing = ref(false);
let clockTimer: ReturnType<typeof setInterval> | null = null;
const startedLessons = computed<ReadonlySet<number>>((previous) => {
  const now = clock.value;
  const next = new Set(lessons.value.filter(lesson => lesson.startTime && Number.isFinite(Date.parse(`${lesson.date}T${lesson.startTime}+03:00`)) && Date.parse(`${lesson.date}T${lesson.startTime}+03:00`) <= now).map(lesson => lesson.id));
  return previous && previous.size === next.size && [...next].every(id => previous.has(id)) ? previous : next;
});
const hasStarted = (lesson: HeadmanJournalLesson) => startedLessons.value.has(lesson.id);
watchPostEffect(() => {
  if (modalOpen.value && !detail.value?.open) detail.value?.showModal();
  else if (!modalOpen.value && detail.value?.open) detail.value.close();
});
let revision = 0, journalAlive = true;
let loadedContext = '';
const locked = computed(() => busy.value || uncertain.value || loading.value || modalOpen.value || timingRefreshing.value);
const subjectOptions = computed(() => [{ value: 0, label: 'Все предметы' }, ...subjects.value.map(subject => ({ value: subject.id, label: subject.name }))]);
const typeCodes = ['LECTURE', 'PRACTICE', 'LAB'] as const;
const availableTypes = computed<readonly string[]>(() => subjectId.value ? subjects.value.find(subject => subject.id === subjectId.value)?.lessonTypes ?? [] : [...typeCodes]);
const exportTypes = computed(() => selectedTypes.value ?? availableTypes.value);
function toggleType(code: string, checked: boolean) {
  if (locked.value || !availableTypes.value.includes(code)) return;
  const next = new Set(selectedTypes.value ?? availableTypes.value);
  if (checked) next.add(code); else next.delete(code);
  selectedTypes.value = [...next];
}
const reasonOptions = [
  { value: 'ILLNESS', label: 'Болезнь' }, { value: 'MEDICAL_EXAMINATION', label: 'Медосмотр' },
  { value: 'COMPETITION_PARTICIPATION', label: 'Соревнование' }, { value: 'FAMILY_CIRCUMSTANCES', label: 'Семейные обстоятельства' },
  { value: 'SUMMONS', label: 'Повестка' }, { value: 'UNIVERSITY_ORDER', label: 'Приказ университета' }, { value: 'EXEMPTION', label: 'Освобождение' }, { value: 'OTHER', label: 'Другое' },
];
function stepWeek(direction: -1 | 1) { start.value = addDays(start.value, direction * 7); void load(); }
const visible = computed(() =>
  lessons.value.filter(
    (lesson) =>
      (!subjectId.value || lesson.subjectId === subjectId.value) &&
      (selectedTypes.value === null || selectedTypes.value.includes(lessonTypeCode(lesson.lessonType))),
  ),
);
const roster = computed(() => {
  const students = new Map<number, string>();
  for (const report of reports.value.values())
    for (const entry of report.entries) students.set(entry.userId, entry.displayName);
  return [...students].sort((a, b) => a[1].localeCompare(b[1], 'ru'));
});
function entryFor(lessonId: number, userId: number) {
  return reports.value.get(lessonId)?.entries.find((entry) => entry.userId === userId);
}
function subjectName(id: number | null) {
  return subjects.value.find((subject) => subject.id === id)?.name ?? 'Предмет недоступен';
}
function canEdit(lesson: HeadmanJournalLesson, entry: HeadmanJournalRosterEntry) {
  return (
    feature.writable(entry.status === 'EXCUSED' ? 'MANAGE_EXCUSES' : 'MARK_ATTENDANCE') &&
    !!semester.value?.active &&
    lesson.current && hasStarted(lesson) && lesson.groupId === feature.groupId && lesson.transferState !== 'PENDING' &&
    lesson.date >= semester.value!.dateFrom && lesson.date <= semester.value!.dateTo &&
    reports.value.get(lesson.id)?.lessonId === lesson.id &&
    (reports.value.get(lesson.id)?.groupId === null || reports.value.get(lesson.id)?.groupId === feature.groupId) &&
    (reports.value.get(lesson.id)?.lessonDate === null || reports.value.get(lesson.id)?.lessonDate === lesson.date) &&
    (reports.value.get(lesson.id)?.semesterId === null || reports.value.get(lesson.id)?.semesterId === semester.value!.id) &&
    reports.value.get(lesson.id)?.editable &&
    entry.editable &&
    !['CANCELLED', 'TRANSFERRED', 'UNSUPPORTED'].includes(lesson.status) &&
    !entry.approvedTicket &&
    (entry.status === 'EMPTY' || !!entry.markRevision) &&
    ['EMPTY', 'PRESENT', 'ABSENT', 'EXCUSED'].includes(entry.status)
  );
}
type StartedInvalidation = Readonly<{ lesson: HeadmanJournalLesson; run: number; context: string }>;
const startedInvalidations = new Map<number, StartedInvalidation>();
function viewContext() {
  const from = mode.value === 'subject' ? semester.value?.dateFrom : start.value;
  const to = mode.value === 'subject' ? semester.value?.dateTo : addDays(start.value, 6);
  return `${mode.value}/${semesterId.value}/${from}/${to}/${mode.value === 'subject' ? subjectId.value : 0}`;
}
function queueStartedReport(lesson: HeadmanJournalLesson) {
  startedInvalidations.set(lesson.id, { lesson, run: revision, context: loadedContext });
}
async function flushStartedReports() {
  if (!journalAlive || timingRefreshing.value || loading.value || busy.value || uncertain.value || modalOpen.value || !startedInvalidations.size) return;
  timingRefreshing.value = true;
  try {
    while (startedInvalidations.size && journalAlive && !loading.value && !busy.value && !uncertain.value && !modalOpen.value) {
      const batch = [...startedInvalidations.values()].slice(0, 8);
      for (const item of batch) startedInvalidations.delete(item.lesson.id);
      const valid = (item: StartedInvalidation) => journalAlive && item.run === revision && item.context === loadedContext && item.context === viewContext() && lessons.value.some(lesson => lesson.id === item.lesson.id && lesson.date === item.lesson.date && lesson.occurrenceId === item.lesson.occurrenceId && lesson.occurrenceRevision === item.lesson.occurrenceRevision);
      const results = await Promise.all(batch.map(async item => {
        if (!valid(item)) return null;
        try {
          const report = await feature.journal.getLessonAttendance(item.lesson.id);
          feature.assertCurrent();
          if (!valid(item)) return null;
          if (report.lessonId !== item.lesson.id || (report.groupId !== null && report.groupId !== item.lesson.groupId) || (report.lessonDate !== null && report.lessonDate !== item.lesson.date) || (report.semesterId !== null && report.semesterId !== semesterId.value)) throw new Error('Сервер вернул доступность другой пары.');
          return [item, report] as const;
        } catch (cause) {
          if (valid(item) && !error.value) error.value = `Не удалось обновить доступность начавшейся пары. ${message(cause)}`;
          return null;
        }
      }));
      const next = new Map(reports.value);
      let published = false;
      for (const result of results) if (result && valid(result[0])) { next.set(result[0].lesson.id, result[1]); published = true; }
      if (published) reports.value = next;
    }
  } finally { timingRefreshing.value = false; }
}
watch(clock, (now, previous) => {
  if (now <= previous) return;
  for (const lesson of lessons.value) {
    const instant = lesson.startTime ? Date.parse(`${lesson.date}T${lesson.startTime}+03:00`) : NaN;
    if (instant > previous && instant <= now) queueStartedReport(lesson);
  }
  void flushStartedReports();
});
watch([busy, uncertain, modalOpen, loading], () => { void flushStartedReports(); });
function canChooseStatus(lesson: HeadmanJournalLesson, entry: HeadmanJournalRosterEntry, status: JournalStatus) {
  return !!canEdit(lesson, entry) && feature.writable(entry.status === 'EXCUSED' || status === 'EXCUSED' ? 'MANAGE_EXCUSES' : 'MARK_ATTENDANCE');
}
async function readWeekPolicy(run: number) {
  const selectedStart = start.value, selectedSemester = semesterId.value, date = ordinalDate.value;
  try {
    const policy = await feature.schedule.academicWeekPolicy(date);
    feature.assertCurrent();
    if (run !== revision || mode.value !== 'week' || start.value !== selectedStart || semesterId.value !== selectedSemester || policy.weekStart !== selectedStart) return;
    weekPolicy.value = policy;
    weekPolicyContext.value = `${selectedSemester}/${selectedStart}`;
  } catch {
    // Missing parity must not prevent the attendance journal from loading.
    if (run === revision && start.value === selectedStart && semesterId.value === selectedSemester) {
      weekPolicy.value = null;
      weekPolicyContext.value = '';
    }
  }
}
async function load() {
  if (busy.value || uncertain.value || modalOpen.value) return;
  const run = ++revision, loadClock = clock.value;
  startedInvalidations.clear();
  weekPolicy.value = null;
  weekPolicyContext.value = '';
  loading.value = true;
  error.value = null;
  selected.value = null;
  try {
    await feature.initialize();
    if (!feature.allowed('MARK_ATTENDANCE') && !feature.allowed('VIEW_STATS'))
      throw new Error('Нет права просмотра журнала.');
    if (!semesters.value.length) {
      const [subjectList, semesterList] = await Promise.all([
        feature.subjects.listSubjects(feature.groupId),
        feature.subjects.listSemesters(),
      ]);
      feature.assertCurrent();
      if (run !== revision) return;
      subjects.value = subjectList;
      semesters.value = semesterList;
      semesterId.value = semesterList.find((item) => item.active)?.id ?? semesterList[0]?.id ?? 0;
    }
    const from = mode.value === 'subject' ? semester.value?.dateFrom : start.value;
    const to = mode.value === 'subject' ? semester.value?.dateTo : addDays(start.value, 6);
    if (!from || !to) throw new Error('Нет доступного семестра.');
    if (mode.value === 'week') void readWeekPolicy(run);
    const context = `${mode.value}/${semesterId.value}/${from}/${to}/${mode.value === 'subject' ? subjectId.value : 0}`;
    if (context !== loadedContext) { lessons.value = []; reports.value = new Map(); loadedContext = context; }
    const result = await feature.journal.listLessons(feature.groupId, from, to);
    feature.assertCurrent();
    const relevant = result.filter(
      (lesson) => mode.value === 'week' || !subjectId.value || lesson.subjectId === subjectId.value,
    );
    const attendance = new Map<number, HeadmanJournalReport>();
    // Bound concurrency for dense semester journals; every lesson remains accessible.
    for (let offset = 0; offset < relevant.length; offset += 8) {
      const batch = await Promise.all(
        relevant
          .slice(offset, offset + 8)
          .map(
            async (lesson) =>
              [lesson.id, await feature.journal.getLessonAttendance(lesson.id)] as const,
          ),
      );
      feature.assertCurrent();
      if (run !== revision) return;
      for (const [id, report] of batch) attendance.set(id, report);
    }
    if (run !== revision) return;
    lessons.value = result;
    reports.value = attendance;
    // A start may cross while the initial batch is in flight, before its lessons were published.
    for (const lesson of result) {
      const instant = lesson.startTime ? Date.parse(`${lesson.date}T${lesson.startTime}+03:00`) : NaN;
      if (instant > loadClock && instant <= clock.value) queueStartedReport(lesson);
    }
    const linked = Number(route.query.lesson);
    if (linked && result.some((lesson) => lesson.id === linked)) {
      await loadAudit(linked);
      const userId = Number(route.query.student),
        lesson = result.find((item) => item.id === linked),
        entry = attendance.get(linked)?.entries.find((item) => item.userId === userId);
      if (lesson && entry) await choose(lesson, entry);
    }
  } catch (cause) {
    if (run === revision) error.value = message(cause);
  } finally {
    if (run === revision) loading.value = false;
  }
}
async function choose(lesson: HeadmanJournalLesson, entry: HeadmanJournalRosterEntry) {
  if (busy.value || uncertain.value || timingRefreshing.value) return;
  selected.value = { lesson, entry };
  inlineRequest.value = false;
  draft.status = 'EXCUSED';
  draft.excuseType = entry.status === 'EXCUSED' ? initialExcuseType(entry) : '';
  draft.comment = entry.status === 'EXCUSED' ? entry.comment ?? '' : '';
  draft.file = null;
  error.value = null;
  modalOpen.value = true;
  await nextTick();
  detail.value?.querySelector<HTMLButtonElement>('.web-field-trigger')?.focus({ preventScroll: true });
}
function closeMark() {
  if (busy.value || uncertain.value) return;
  modalOpen.value = false;
  selected.value = null;
}
async function chooseStatus(lesson: HeadmanJournalLesson, entry: HeadmanJournalRosterEntry, status: JournalStatus) {
  if (locked.value || !canChooseStatus(lesson, entry, status)) return;
  if (status === 'EXCUSED') { await choose(lesson, entry); return; }
  selected.value = { lesson, entry };
  inlineRequest.value = true;
  draft.status = status;
  draft.file = null;
  await save(entry.status === status);
  if (!uncertain.value) { selected.value = null; inlineRequest.value = false; }
}
async function readMarkResult(intent: MarkIntent) {
  const report = await feature.journal.getLessonAttendance(intent.lesson.id);
  feature.assertCurrent();
  if (pendingMark.value !== intent) return false;
  if (report.lessonId !== intent.lesson.id || (report.groupId !== null && report.groupId !== intent.lesson.groupId) || (report.lessonDate !== null && report.lessonDate !== intent.lesson.date))
    throw new Error('Сервер вернул отметки другой пары.');
  reports.value = new Map(reports.value).set(intent.lesson.id, report);
  const entry = report.entries.find(item => item.userId === intent.entry.userId);
  if (entry) selected.value = { lesson: intent.lesson, entry };
  markReadback.value = intent;
  if (!markReadbackMatches(entry, intent.command)) {
    uncertain.value = true;
    error.value = entry ? 'Изменение пока не подтверждено. Показана текущая отметка сервера.' : 'Студент отсутствует в текущем составе пары. Проверь результат или прими показанное состояние.';
    return false;
  }
  pendingMark.value = null;
  markReadback.value = null;
  uncertain.value = false;
  error.value = null;
  modalOpen.value = false;
  selected.value = null;
  return true;
}
async function refreshSelected() {
  const intent = pendingMark.value;
  if (!intent || busy.value) return;
  busy.value = true;
  markReadback.value = null;
  let confirmed = false;
  try { confirmed = await readMarkResult(intent); }
  catch (cause) { error.value = `${message(cause)} Результат изменения остаётся неизвестным.`; }
  finally { busy.value = false; }
  if (confirmed && auditLesson.value === intent.lesson.id) void loadAudit(intent.lesson.id);
}
function acceptCurrentMark() {
  if (!canAcceptMark.value) return;
  pendingMark.value = null;
  markReadback.value = null;
  uncertain.value = false;
  error.value = null;
  if (!modalOpen.value) { selected.value = null; inlineRequest.value = false; return; }
  if (selected.value) {
    draft.status = 'EXCUSED';
    draft.excuseType = selected.value.entry.status === 'EXCUSED' ? initialExcuseType(selected.value.entry) : '';
    draft.comment = selected.value.entry.status === 'EXCUSED' ? selected.value.entry.comment ?? '' : '';
    draft.file = null;
  }
}
async function save(clear = false) {
  const owner = selected.value;
  if (!owner || busy.value || uncertain.value || timingRefreshing.value || !canEdit(owner.lesson, owner.entry)) return;
  const expectedStatus = owner.entry.status;
  if (!['PRESENT', 'ABSENT', 'EXCUSED', 'EMPTY'].includes(expectedStatus)) return;
  const targetStatus = clear ? expectedStatus : draft.status;
  if (!canChooseStatus(owner.lesson, owner.entry, targetStatus as JournalStatus)) return;
  const current = entryFor(owner.lesson.id, owner.entry.userId);
  if (!current || current.status !== expectedStatus || current.markRevision !== owner.entry.markRevision || current.approvedTicket) { error.value = 'Отметка изменилась. Обнови журнал перед следующим действием.'; return; }
  if (clear && expectedStatus === 'EMPTY') return;
  if (!clear && draft.status === 'EXCUSED' && !draft.excuseType) { error.value = 'Выбери причину уважительного отсутствия.'; return; }
  const command: HeadmanJournalMarkCommand | null = clear ? null : draft.status === 'EXCUSED'
    ? { status: 'EXCUSED', excuseType: draft.excuseType as HeadmanJournalExcuseType, comment: draft.comment, file: draft.file }
    : { status: draft.status as 'PRESENT' | 'ABSENT' };
  const intent: MarkIntent = Object.freeze({ lesson: owner.lesson, entry: Object.freeze({ ...owner.entry }), command: command ? Object.freeze(command) : null, condition: Object.freeze({ expectedStatus: expectedStatus as HeadmanJournalMarkCondition['expectedStatus'], expectedRevision: expectedStatus === 'EMPTY' ? 'EMPTY' : owner.entry.markRevision! }) });
  pendingMark.value = intent;
  markReadback.value = null;
  busy.value = true;
  error.value = null;
  let acknowledged = false, confirmed = false;
  try {
    feature.assertCurrent();
    if (intent.command) {
      const ack = await feature.journal.mark(intent.lesson.id, intent.entry.userId, intent.command, intent.condition);
      acknowledged = true;
      if (ack.lessonId !== intent.lesson.id || ack.userId !== intent.entry.userId || ack.status !== intent.command.status) throw new Error('Сервер вернул подтверждение другой отметки.');
    } else {
      await feature.journal.clear(intent.lesson.id, intent.entry.userId, { expectedStatus: expectedStatus as 'PRESENT' | 'ABSENT' | 'EXCUSED', expectedRevision: intent.condition.expectedRevision });
      acknowledged = true;
    }
    feature.assertCurrent();
    confirmed = await readMarkResult(intent);
  } catch (cause) {
    if (!acknowledged && cause instanceof HeadmanJournalApiError && [400, 401, 403, 404, 409, 410, 415, 422].includes(cause.response.status)) {
      pendingMark.value = null;
      uncertain.value = false;
      error.value = message(cause);
    } else {
      uncertain.value = true;
      try { confirmed = await readMarkResult(intent); }
      catch { error.value = 'Результат изменения неизвестен. Проверь отметку перед следующей записью.'; }
    }
  } finally { busy.value = false; }
  if (confirmed && auditLesson.value === intent.lesson.id) void loadAudit(intent.lesson.id);
}
const actionsReader = new LessonActionsReader(feature.actions);
async function loadAudit(id: number, more = false) {
  const previous = more && auditLesson.value === id ? audit.value : [];
  const nextCursor = more && auditLesson.value === id ? cursor.value : null;
  if (more && !nextCursor) return;
  auditLesson.value = id;
  if (!more) {
    audit.value = [];
    cursor.value = null;
  }
  try {
    const result = await actionsReader.read(id, nextCursor, previous);
    feature.assertCurrent();
    if (!result) return;
    auditLesson.value = result.lessonId;
    audit.value = result.items;
    cursor.value = result.cursor;
  } catch (cause) {
    if (auditLesson.value === id) error.value = message(cause);
  }
}

watch([mode, semesterId], () => {
  if (semesters.value.length && !loading.value) void load();
});
onMounted(() => { void load(); clockTimer = setInterval(() => { clock.value = Date.now(); }, 1000); });
onBeforeUnmount(() => { journalAlive = false; revision++; startedInvalidations.clear(); if (clockTimer !== null) clearInterval(clockTimer); });
useWebLiveRefresh(load, {
  blocked: () => busy.value || loading.value || !!selected.value || uncertain.value || timingRefreshing.value,
});
</script>
<template>
  <HeadmanPage
    title="Посещаемость"
    class="headman-analytics headman-attendance-page"
    hide-title
    :loading="loading"
    :error="error"
    @reload="load"
  >
    <div class="headman-period-toolbar">
      <StudentPeriodPager v-if="mode === 'week'" :label="weekLabel" unit="период журнала" :busy="locked" :previous-disabled="!!semester && addDays(start, -7) < weekStart(semester.dateFrom)" :next-disabled="!!semester && addDays(start, 7) > semester.dateTo" @step="stepWeek" @reset="start = weekStart(today()); load()">
        <WebDateField v-model="start" class="headman-journal-date" label="Дата недели журнала" :trigger-label="weekLabel" :min="semester ? weekStart(semester.dateFrom) : undefined" :max="semester?.dateTo" :today="today()" :disabled="locked" required @change="start = weekStart(start); load()" />
      </StudentPeriodPager>
      <p v-else class="headman-period-title">{{ semester?.name ?? 'Журнал по предмету' }}</p>
      <div class="headman-actions" role="group" aria-label="Режим журнала"><button type="button" class="web-button" :class="{ 'web-button-selected': mode === 'week' }" :aria-pressed="mode === 'week'" :disabled="locked" @click="mode = 'week'">По неделе</button><button type="button" class="web-button" :class="{ 'web-button-selected': mode === 'subject' }" :aria-pressed="mode === 'subject'" :disabled="locked" @click="mode = 'subject'">По предмету</button></div>
    </div>
    <div class="headman-filter-bar">
      <WebSelect v-model="semesterId" label="Семестр" :options="semesters.map(item => ({ value: item.id, label: item.name + (item.active ? '' : ' · архив') }))" :disabled="locked" />
      <WebSelect v-model="subjectId" label="Предмет" :options="subjectOptions" :disabled="locked" @change="selectedTypes = null; load()" />
      <fieldset class="headman-type-picker headman-journal-types" :disabled="locked"><legend>Тип занятия</legend><div><button type="button" class="web-button" :class="{ 'web-button-selected': selectedTypes === null }" :aria-pressed="selectedTypes === null" @click="selectedTypes = null">Все типы</button><label v-for="code in typeCodes" :key="code" class="headman-choice"><input type="checkbox" :checked="selectedTypes === null ? availableTypes.includes(code) : selectedTypes.includes(code)" :disabled="!availableTypes.includes(code)" @change="toggleType(code, ($event.target as HTMLInputElement).checked)" /><span>{{ lessonTypes[code] }}</span></label></div></fieldset>
    </div>
    <p
      v-if="semester && !semester.active"
      class="web-notice"
      >Архивный семестр · только чтение</p
    >
    <HeadmanJournalGrid v-if="visible.length" :lessons="visible" :reports="reports" :roster="roster" :subjects="subjects" :compact="mode === 'subject'" :locked="locked" :has-started="hasStarted" :can-mark="canChooseStatus" @choose="choose" @mark="chooseStatus" @audit="loadAudit" />
    <div
      v-else-if="!loading && !error"
      class="headman-journal-empty"
      role="status"
    >
      <AttendanceFilterIllustration />
      <p>В выбранном периоде пар нет.</p>
    </div>
    <JournalExport
      :feature="feature"
      :subject-id="subjectId"
      :lesson-types="exportTypes"
      :active="!!semester?.active"
      :mode="mode"
      :week-start="start"
    />
    <div v-if="uncertain && !modalOpen" class="web-notice headman-journal-recovery" role="alert"><p>{{ selected?.entry.displayName }} · результат изменения неизвестен.</p><div class="headman-actions"><button type="button" class="web-button" :disabled="busy" @click="refreshSelected">Проверить отметку</button><button v-if="canAcceptMark" type="button" class="web-button" @click="acceptCurrentMark">Принять текущее состояние</button></div></div>
    <dialog ref="detail" class="web-confirm-dialog headman-journal-excuse-dialog web-stack" aria-labelledby="headman-journal-mark-title" :aria-busy="busy" @cancel.prevent="closeMark">
      <template v-if="selected"><div class="headman-heading"><h2 id="headman-journal-mark-title">{{ selected.entry.displayName }}</h2><button type="button" class="web-button web-icon-button" aria-label="Закрыть отметку студента" :disabled="busy || uncertain" @click="closeMark"><MobileIcon name="close" /></button></div>
        <p class="web-caption">{{ subjectName(selected.lesson.subjectId) }} · {{ dateLabel(selected.lesson.date) }} · {{ selected.lesson.lessonNumber }} пара</p>
        <p v-if="selected.entry.approvedTicket" class="web-notice">Отметка связана с одобренной заявкой. <RouterLink :to="{ name: 'headman-requests', query: { request: selected.entry.approvedTicket.id } }">Заявка № {{ selected.entry.approvedTicket.id }}</RouterLink></p>
        <p v-if="error" class="web-error" role="alert">{{ error }}</p>
        <div v-if="uncertain" class="web-notice web-stack"><p>Результат изменения неизвестен.</p><div class="headman-actions"><button type="button" class="web-button" :disabled="busy" @click="refreshSelected">Проверить отметку</button><button v-if="canAcceptMark" type="button" class="web-button" @click="acceptCurrentMark">Принять текущее состояние</button></div></div>
        <form v-if="canChooseStatus(selected.lesson, selected.entry, 'EXCUSED')" class="web-stack" @submit.prevent="save()"><fieldset :disabled="busy || uncertain" class="headman-journal-excuse-fields"><WebSelect v-model="draft.excuseType" label="Причина уважительного отсутствия" :options="reasonOptions" :disabled="busy || uncertain" required /><label class="web-field">Комментарий<textarea v-model="draft.comment" maxlength="512" /></label><label class="web-field">Подтверждение<input type="file" accept="image/jpeg,image/png,application/pdf" @change="draft.file = ($event.target as HTMLInputElement).files?.[0] ?? null" /></label></fieldset><button v-if="selected.entry.status === 'EXCUSED'" type="button" class="web-button" :disabled="busy || uncertain" @click="save(true)">Снять «у»</button><div class="web-confirm-actions"><button type="button" class="web-button" :disabled="busy || uncertain" @click="closeMark">Отмена</button><button class="web-button web-button-primary" :disabled="busy || uncertain || !draft.excuseType">Сохранить «у»</button></div></form>
        <p v-else class="web-caption">Изменение этой отметки недоступно.</p>
      </template>
    </dialog>
    <section
      v-if="auditLesson"
      class="headman-panel web-stack"
      ><h2>Действия пары №{{ auditLesson }}</h2
      ><p v-if="!audit.length">Действий пока нет.</p
      ><ol class="headman-list"
        ><li
          v-for="action in audit"
          :key="action.id"
          ><time>{{ new Date(action.occurredAt).toLocaleString('ru-RU') }}</time> ·
          {{ action.actor?.displayName ?? 'Система' }} · {{ actionLabel(action.action).startsWith('Другое действие') ? 'Другое действие' : actionLabel(action.action) }} ·
          {{ action.outcome === 'APPLIED' ? 'Применено' : 'Отклонено'
          }}<span v-if="action.student"> · {{ action.student.displayName }}</span
          ><p v-if="action.errorCode">Сервер отклонил действие.</p
          ><p v-if="action.geo"
            >Геопроверка · расстояние
            {{ action.geo.distanceMeters ?? '—' }} м · радиус
            {{ action.geo.radiusMeters ?? '—' }} м</p
          ></li
        ></ol
      ><button
        v-if="cursor"
        class="web-button"
        @click="loadAudit(auditLesson, true)"
        >Ещё действия</button
      ></section
    >
  </HeadmanPage>
</template>
