<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, shallowRef, watch } from 'vue';
import { useRoute } from 'vue-router';
import { message } from '../../auth/session';
import { useWebLiveRefresh } from '../../app/live';
import {
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
import {
  addDays,
  dateLabel,
  lessonTypes,
  lessonTypeCode,
  markLabels,
  markSymbols,
  today,
  useHeadmanFeature,
  weekStart,
} from './context';
import HeadmanPage from './HeadmanPage.vue';
import JournalExport from './JournalExport.vue';
import { initialExcuseType, markReadbackMatches, LessonActionsReader } from './journal-state';

const feature = useHeadmanFeature();
const route = useRoute();
const loading = ref(false),
  error = ref<string | null>(null),
  busy = ref(false);
const start = ref(weekStart(today())),
  mode = ref<'week' | 'subject'>('week'),
  subjectId = ref(0),
  type = ref('');
const subjects = shallowRef<readonly HeadmanSubject[]>([]),
  semesters = shallowRef<readonly HeadmanSemester[]>([]);
const semesterId = ref(0);
const semester = computed(() => semesters.value.find((item) => item.id === semesterId.value));
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
  excuseType: 'ILLNESS' as HeadmanJournalExcuseType | '',
  comment: '',
  file: null as File | null,
});
const uncertain = ref(false),
  detail = ref<HTMLElement | null>(null);
let revision = 0;
const visible = computed(() =>
  lessons.value.filter(
    (lesson) =>
      (!subjectId.value || lesson.subjectId === subjectId.value) &&
      (!type.value || lessonTypeCode(lesson.lessonType) === type.value),
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
    feature.writable('MARK_ATTENDANCE') &&
    !!semester.value?.active &&
    lesson.current &&
    reports.value.get(lesson.id)?.editable &&
    entry.editable &&
    !['CANCELLED', 'TRANSFERRED', 'UNSUPPORTED'].includes(lesson.status) &&
    !entry.approvedTicket &&
    !!entry.markRevision &&
    ['EMPTY', 'PRESENT', 'ABSENT', 'EXCUSED'].includes(entry.status)
  );
}
async function load() {
  if (busy.value) return;
  const run = ++revision;
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
    const linked = Number(route.query.lesson);
    if (linked && result.some((lesson) => lesson.id === linked)) await loadAudit(linked);
  } catch (cause) {
    if (run === revision) error.value = message(cause);
  } finally {
    if (run === revision) loading.value = false;
  }
}
async function choose(lesson: HeadmanJournalLesson, entry: HeadmanJournalRosterEntry) {
  if (busy.value || uncertain.value) return;
  selected.value = { lesson, entry };
  draft.status = entry.status === 'EMPTY' ? 'PRESENT' : entry.status;
  draft.excuseType = initialExcuseType(entry);
  draft.comment = entry.comment ?? '';
  draft.file = null;
  error.value = null;
  await nextTick();
  detail.value?.focus();
}
async function refreshSelected() {
  const owner = selected.value;
  if (!owner) return;
  busy.value = true;
  try {
    const report = await feature.journal.getLessonAttendance(owner.lesson.id);
    feature.assertCurrent();
    reports.value = new Map(reports.value).set(owner.lesson.id, report);
    const entry = report.entries.find((item) => item.userId === owner.entry.userId);
    selected.value = entry ? { lesson: owner.lesson, entry } : null;
    if (uncertain.value)
      error.value =
        'Показана текущая отметка сервера. Проверь её и прими как новый черновик перед следующим изменением.';
    else error.value = null;
  } catch (cause) {
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
async function save(clear = false) {
  const owner = selected.value;
  if (!owner || busy.value || uncertain.value || !canEdit(owner.lesson, owner.entry)) return;
  const expectedStatus = owner.entry.status;
  if (!['PRESENT', 'ABSENT', 'EXCUSED', 'EMPTY'].includes(expectedStatus)) return;
  if (!clear && draft.status === 'EXCUSED' && !draft.excuseType) {
    error.value = 'Выбери поддерживаемую причину перед сохранением.';
    return;
  }
  const command: HeadmanJournalMarkCommand | null = clear
    ? null
    : draft.status === 'EXCUSED'
      ? {
          status: 'EXCUSED',
          excuseType: draft.excuseType as HeadmanJournalExcuseType,
          comment: draft.comment,
          file: draft.file,
        }
      : { status: draft.status as 'PRESENT' | 'ABSENT' };
  busy.value = true;
  error.value = null;
  try {
    feature.assertCurrent();
    if (clear) {
      if (expectedStatus === 'EMPTY') return;
      await feature.journal.clear(owner.lesson.id, owner.entry.userId, {
        expectedStatus: expectedStatus as 'PRESENT' | 'ABSENT' | 'EXCUSED',
        expectedRevision: owner.entry.markRevision!,
      });
    } else {
      await feature.journal.mark(owner.lesson.id, owner.entry.userId, command!, {
        expectedStatus: expectedStatus as 'EMPTY' | 'PRESENT' | 'ABSENT' | 'EXCUSED',
        expectedRevision: owner.entry.markRevision!,
      });
    }
    feature.assertCurrent();
    await refreshSelected();
  } catch (cause) {
    // Readback resolves an uncertain write. The original command is never replayed.
    uncertain.value = true;
    try {
      const report = await feature.journal.getLessonAttendance(owner.lesson.id);
      feature.assertCurrent();
      reports.value = new Map(reports.value).set(owner.lesson.id, report);
      const entry = report.entries.find((item) => item.userId === owner.entry.userId);
      selected.value = entry ? { lesson: owner.lesson, entry } : null;
      uncertain.value = !markReadbackMatches(entry, command);
      if (uncertain.value)
        error.value = `${message(cause)}. Сервер не подтвердил отправленные причину, комментарий или файл. Показана текущая отметка.`;
    } catch {
      error.value = 'Результат изменения неизвестен. Проверь отметку перед следующей записью.';
    }
  } finally {
    busy.value = false;
  }
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
onMounted(load);
useWebLiveRefresh(load, {
  blocked: () => busy.value || loading.value || !!selected.value || uncertain.value,
});
</script>
<template>
  <HeadmanPage
    title="Посещаемость"
    :loading="loading"
    :error="error"
    @reload="load"
  >
    <form
      class="headman-panel headman-toolbar"
      @submit.prevent="load"
    >
      <label class="web-field"
        >Срез<select v-model="mode"
          ><option value="week">По неделе</option
          ><option value="subject">По предмету</option></select
        ></label
      >
      <label class="web-field"
        >Семестр<select v-model.number="semesterId"
          ><option
            v-for="item in semesters"
            :key="item.id"
            :value="item.id"
            >{{ item.name }}{{ item.active ? '' : ' · архив' }}</option
          ></select
        ></label
      >
      <label
        v-if="mode === 'week'"
        class="web-field"
        >Начало недели<input
          v-model="start"
          type="date"
          required
      /></label>
      <label class="web-field"
        >Предмет<select v-model.number="subjectId"
          ><option :value="0">Все предметы</option
          ><option
            v-for="subject in subjects"
            :key="subject.id"
            :value="subject.id"
            >{{ subject.name }}</option
          ></select
        ></label
      >
      <label class="web-field"
        >Тип занятия<select v-model="type"
          ><option value="">Все типы</option
          ><option
            v-for="(label, code) in lessonTypes"
            :key="code"
            :value="code"
            >{{ label }}</option
          ></select
        ></label
      >
      <button
        class="web-button"
        :disabled="loading || busy"
        >Показать</button
      >
    </form>
    <div
      v-if="mode === 'week'"
      class="headman-actions"
      ><button
        class="web-button"
        :disabled="busy || loading"
        @click="
          start = addDays(start, -7);
          load();
        "
        >Предыдущая неделя</button
      ><span>{{ dateLabel(start) }} — {{ dateLabel(addDays(start, 6)) }}</span
      ><button
        class="web-button"
        :disabled="busy || loading"
        @click="
          start = addDays(start, 7);
          load();
        "
        >Следующая неделя</button
      ><button
        class="web-button"
        :disabled="busy || loading"
        @click="
          start = weekStart(today());
          load();
        "
        >Текущая неделя</button
      ></div
    >
    <p
      v-if="semester && !semester.active"
      class="web-notice"
      >Архивный семестр · только чтение</p
    >
    <div
      v-if="visible.length"
      class="headman-table-scroll"
      tabindex="0"
      aria-label="Журнал группы, горизонтальная прокрутка"
    >
      <table
        class="headman-table headman-journal"
        :style="{ '--headman-columns': visible.length }"
        ><caption class="web-sr-only">Журнал посещаемости</caption
        ><thead
          ><tr
            ><th scope="col">Студенты</th
            ><th
              v-for="lesson in visible"
              :key="lesson.id"
              scope="col"
              :class="{ 'is-current': lesson.status === 'ACTIVE' }"
              ><button
                class="web-button"
                @click="loadAudit(lesson.id)"
                >{{ dateLabel(lesson.date) }} · {{ lesson.lessonNumber }}</button
              ><div>{{ subjectName(lesson.subjectId) }}</div
              ><span
                >{{ lessonTypes[lessonTypeCode(lesson.lessonType)] ?? lesson.lessonType }} ·
                {{ lesson.status === 'CANCELLED' ? 'Отменено' : (lesson.room ?? '—') }}</span
              ></th
            ></tr
          ></thead
        ><tbody
          ><tr
            v-for="[userId, name] in roster"
            :key="userId"
            ><th scope="row">{{ name }}</th
            ><td
              v-for="lesson in visible"
              :key="lesson.id"
              ><template v-if="entryFor(lesson.id, userId)"
                ><button
                  class="headman-mark"
                  :data-status="entryFor(lesson.id, userId)!.status"
                  :class="{
                    'is-auto':
                      entryFor(lesson.id, userId)!.source === 'AUTO' ||
                      entryFor(lesson.id, userId)!.source === 'AUTO_ABSENT',
                  }"
                  :aria-label="`${name}, ${dateLabel(lesson.date)}, ${subjectName(lesson.subjectId)}: ${markLabels[entryFor(lesson.id, userId)!.status]}`"
                  :disabled="busy || uncertain"
                  @click="choose(lesson, entryFor(lesson.id, userId)!)"
                  >{{ markSymbols[entryFor(lesson.id, userId)!.status] }}</button
                ></template
              ><span v-else>—</span></td
            ></tr
          ></tbody
        ></table
      >
    </div>
    <p
      v-else-if="!loading && !error"
      class="web-notice"
      >В выбранном периоде пар нет.</p
    >
    <JournalExport
      :feature="feature"
      :subject-id="subjectId"
      :lesson-types="
        type ? [type] : (subjects.find((subject) => subject.id === subjectId)?.lessonTypes ?? [])
      "
      :active="!!semester?.active"
      :mode="mode"
      :week-start="start"
    />
    <section
      v-if="selected"
      ref="detail"
      class="headman-panel headman-detail web-stack"
      tabindex="-1"
      aria-label="Отметка студента"
    >
      <div class="headman-heading"
        ><h2>{{ selected.entry.displayName }} · {{ dateLabel(selected.lesson.date) }}</h2
        ><button
          class="web-button"
          :disabled="busy || uncertain"
          @click="selected = null"
          >Закрыть</button
        ></div
      >
      <p
        >{{ subjectName(selected.lesson.subjectId) }} · {{ markLabels[selected.entry.status]
        }}{{ selected.entry.source ? ` · ${selected.entry.source}` : '' }}</p
      >
      <p
        v-if="selected.entry.approvedTicket"
        class="web-notice"
        >Отметка связана с одобренной заявкой.
        <RouterLink
          :to="{ name: 'headman-requests', query: { request: selected.entry.approvedTicket.id } }"
          >Открыть заявку</RouterLink
        ></p
      >
      <p
        v-if="uncertain"
        class="web-notice"
        >Результат неизвестен.
        <button
          class="web-button"
          :disabled="busy"
          @click="refreshSelected"
          >Проверить отметку</button
        ></p
      >
      <button
        v-if="uncertain && selected"
        class="web-button"
        :disabled="busy"
        @click="
          draft.status = selected.entry.status;
          draft.excuseType = initialExcuseType(selected.entry);
          draft.comment = selected.entry.comment ?? '';
          draft.file = null;
          uncertain = false;
          error = null;
        "
        >Использовать показанную отметку как новый черновик</button
      >
      <form
        v-if="canEdit(selected.lesson, selected.entry)"
        class="web-stack"
        @submit.prevent="save()"
        ><fieldset :disabled="busy || uncertain"
          ><legend>Отметка</legend
          ><div class="headman-actions"
            ><label
              v-for="status in ['PRESENT', 'EXCUSED', 'ABSENT']"
              :key="status"
              ><input
                v-model="draft.status"
                type="radio"
                :value="status"
              />
              {{ markLabels[status] }}</label
            ></div
          ><template v-if="draft.status === 'EXCUSED'"
            ><label class="web-field"
              >Причина<select v-model="draft.excuseType"
                ><option value="ILLNESS">Болезнь</option
                ><option value="MEDICAL_EXAMINATION">Медосмотр</option
                ><option value="COMPETITION_PARTICIPATION">Соревнование</option
                ><option value="FAMILY_CIRCUMSTANCES">Семейные обстоятельства</option
                ><option value="SUMMONS">Повестка</option
                ><option value="UNIVERSITY_ORDER">Приказ университета</option
                ><option value="EXEMPTION">Освобождение</option
                ><option value="OTHER">Другое</option></select
              ></label
            ><label class="web-field"
              >Комментарий<textarea
                v-model="draft.comment"
                maxlength="512"
              /></label
            ><label class="web-field"
              >Подтверждение<input
                type="file"
                accept="image/jpeg,image/png,application/pdf"
                @change="
                  draft.file = ($event.target as HTMLInputElement).files?.[0] ?? null
                " /></label></template></fieldset
        ><div class="headman-actions"
          ><button
            class="web-button web-button-primary"
            :disabled="busy || uncertain"
            >Сохранить отметку</button
          ><button
            v-if="selected.entry.status !== 'EMPTY'"
            type="button"
            class="web-button"
            :disabled="busy || uncertain"
            @click="save(true)"
            >Снять отметку</button
          ></div
        ></form
      >
      <p
        v-else
        class="headman-muted"
        >Только чтение. Изменение этой отметки недоступно.</p
      >
    </section>
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
          {{ action.actor?.displayName ?? 'Система' }} · {{ action.action }} ·
          {{ action.outcome === 'APPLIED' ? 'Применено' : 'Отклонено'
          }}<span v-if="action.student"> · {{ action.student.displayName }}</span
          ><p v-if="action.errorCode">{{ action.errorCode }}</p
          ><p v-if="action.geo"
            >Геопроверка: {{ action.geo.verdict }} · расстояние
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
