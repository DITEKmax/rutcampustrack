<script setup lang="ts">
import {
  exportStateLabel,
  reportDownloadAcceptedMessage,
} from '../../shared/report-download-client';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import HeadmanStatsSelect from '../headman-stats/HeadmanStatsSelect.vue';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import type { ReportDownloadPort } from '../../shared/report-download-client';
import {
  TeacherApiError,
  teacherJournalTypes,
  toTeacherJournalReportRequest,
  type TeacherApi,
  type TeacherExportFormat,
  type TeacherExportFormatCode,
  type TeacherJournalQuery,
  type TeacherJournalResponse,
  type TeacherStatsSubjectOption,
  type TeacherSemester,
} from './teacher-client';
import { journalContextKey, journalPassedEnd, loadJournalPages } from './teacher-journal-model';
import { teacherLessonTypeLabel } from './teacher-ui-format';
import TeacherJournalTable from './TeacherJournalTable.vue';
import TeacherJournalChart from './TeacherJournalChart.vue';
import './teacher-journal-screen.pcss';
const props = withDefaults(
  defineProps<{
    api: TeacherApi | null;
    query: TeacherJournalQuery | null;
    selectedLessonId?: number | null;
    reportDownload?: ReportDownloadPort | null;
    showBack?: boolean;
    embedded?: boolean;
    showExport?: boolean;
    active?: boolean;
    subjectOptions?: readonly TeacherStatsSubjectOption[];
    groupName?: string;
    semester?: TeacherSemester | null;
  }>(),
  {
    showBack: true,
    embedded: false,
    showExport: true,
    active: true,
    selectedLessonId: null,
    reportDownload: null,
    subjectOptions: () => [],
    groupName: '',
    semester: null,
  },
);
const emit = defineEmits<{
  back: [];
  'open-lesson': [lessonId: number, page: number];
  'open-excuse': [requestId: string];
  'open-manual-excuse': [lessonId: number, studentId: number];
  'change-page': [page: number];
  'update-query': [query: TeacherJournalQuery];
  error: [cause: unknown];
}>();
const localQuery = ref<TeacherJournalQuery | null>(null);
watch(
  () => props.query,
  (query) => {
    localQuery.value = query;
  },
  { immediate: true, flush: 'sync' },
);
const effectiveQuery = computed<TeacherJournalQuery | null>(() =>
  localQuery.value ? { ...localQuery.value, pastOnly: true, page: 0, pageSize: 100 } : null,
);
const state = ref<TeacherJournalResponse | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
const chart = ref(false);
const studentId = ref<number | null>(null);
const formats = ref<readonly TeacherExportFormat[]>([]);
const selectedFormat = ref<TeacherExportFormatCode>('docx');
const formatsLoading = ref(false);
const exportLoading = ref(false);
const exportError = ref<string | null>(null);
const exportStatus = ref<string | null>(null);
let revision = 0,
  formatsRevision = 0,
  exportRevision = 0;
let disposed = false;
const objectUrls = new Set<string>();
const subjects = computed(() => {
  const values = new Map<number, TeacherStatsSubjectOption>();
  for (const option of props.subjectOptions)
    if (option.groupId === effectiveQuery.value?.groupId) {
      const old = values.get(option.subjectId);
      values.set(option.subjectId, {
        ...option,
        lessonTypes: [...new Set([...(old?.lessonTypes ?? []), ...option.lessonTypes])],
      });
    }
  return [...values.values()].sort((a, b) => a.subjectName.localeCompare(b.subjectName, 'ru'));
});
const currentSubject = computed(() =>
  subjects.value.find((subject) => subject.subjectId === effectiveQuery.value?.subjectId),
);
const availableTypes = computed(
  () =>
    currentSubject.value?.lessonTypes ??
    (effectiveQuery.value ? teacherJournalTypes(effectiveQuery.value) : []),
);
const selectedTypes = computed(() =>
  effectiveQuery.value ? teacherJournalTypes(effectiveQuery.value) : [],
);
const completedJournal = computed<TeacherJournalResponse | null>(() =>
  state.value
    ? {
        ...state.value,
        lessons: state.value.lessons.filter(
          (lesson) => lesson.status === 'CLOSED' && !lesson.cancelled,
        ),
      }
    : null,
);
const studentOptions = computed(() => [
  { value: 'group', label: 'Вся группа' },
  ...(completedJournal.value?.students ?? []).map((student) => ({
    value: String(student.studentId),
    label: student.displayName,
  })),
]);
const chartFrom = computed(
  () => props.semester?.dateFrom ?? effectiveQuery.value?.dateFrom ?? null,
);
const chartTo = computed(() =>
  journalPassedEnd(props.semester?.dateTo ?? effectiveQuery.value?.dateTo, state.value?.serverNow),
);
const canExport = computed(() =>
  Boolean(
    props.api &&
      effectiveQuery.value &&
      selectedTypes.value.length &&
      completedJournal.value?.lessons.length &&
      !loading.value &&
      !error.value &&
      !formatsLoading.value &&
      !exportLoading.value &&
      formats.value.some((format) => format.code === selectedFormat.value),
  ),
);
watch(
  [() => props.api, () => journalContextKey(effectiveQuery.value)],
  () => {
    revision += 1;
    formatsRevision += 1;
    exportRevision += 1;
    state.value = null;
    error.value = null;
    formats.value = [];
    loading.value = false;
    formatsLoading.value = false;
    exportLoading.value = false;
    exportError.value = null;
    exportStatus.value = null;
    studentId.value = null;
    void load();
    void loadFormats();
  },
  { immediate: true, flush: 'sync' },
);
watch(
  () => [selectedFormat.value, props.reportDownload, props.selectedLessonId] as const,
  () => {
    exportRevision += 1;
    exportLoading.value = false;
    exportError.value = null;
    exportStatus.value = null;
  },
);
watch(
  () => props.active,
  (active) => {
    if (!active) {
      exportRevision += 1;
      exportLoading.value = false;
      exportError.value = null;
      exportStatus.value = null;
    }
  },
  { flush: 'sync' },
);
onBeforeUnmount(() => {
  disposed = true;
  revision += 1;
  formatsRevision += 1;
  exportRevision += 1;
  for (const url of objectUrls) URL.revokeObjectURL(url);
  objectUrls.clear();
});
function changeQuery(next: TeacherJournalQuery): void {
  localQuery.value = next;
  emit('update-query', next);
}
function toggleType(type: string): void {
  const value = effectiveQuery.value;
  if (!value) return;
  const types = teacherJournalTypes(value);
  changeQuery({
    semesterId: value.semesterId,
    groupId: value.groupId,
    subjectId: value.subjectId,
    lessonTypes: types.includes(type) ? types.filter((item) => item !== type) : [...types, type],
    ...(value.dateFrom !== undefined ? { dateFrom: value.dateFrom } : {}),
    ...(value.dateTo !== undefined ? { dateTo: value.dateTo } : {}),
    pastOnly: true,
    page: 0,
    pageSize: 100,
  });
}
function changeSubject(raw: string): void {
  const value = effectiveQuery.value;
  const subject = subjects.value.find((item) => String(item.subjectId) === raw);
  if (!value || !subject) return;
  changeQuery({
    semesterId: value.semesterId,
    groupId: value.groupId,
    subjectId: subject.subjectId,
    lessonTypes: [],
    ...(value.dateFrom !== undefined ? { dateFrom: value.dateFrom } : {}),
    ...(value.dateTo !== undefined ? { dateTo: value.dateTo } : {}),
    pastOnly: true,
    page: 0,
    pageSize: 100,
  });
}
async function load(): Promise<void> {
  const api = props.api;
  const value = effectiveQuery.value;
  const current = ++revision;
  if (!api || !value || !teacherJournalTypes(value).length) return;
  loading.value = true;
  error.value = null;
  state.value = null;
  const context = journalContextKey(value);
  const active = () =>
    !disposed &&
    current === revision &&
    api === props.api &&
    context === journalContextKey(effectiveQuery.value);
  try {
    const journal = await loadJournalPages(api, value, active);
    if (active()) state.value = journal;
  } catch (cause) {
    if (!active() || cause instanceof StaleSessionGenerationError) return;
    error.value =
      cause instanceof TeacherApiError
        ? cause.message
        : cause instanceof Error
          ? cause.message
          : 'Не удалось открыть журнал.';
    emit('error', cause);
  } finally {
    if (active()) loading.value = false;
  }
}
async function loadFormats(): Promise<void> {
  const api = props.api;
  const current = ++formatsRevision;
  if (!api || !effectiveQuery.value || !selectedTypes.value.length || !props.showExport) return;
  formatsLoading.value = true;
  try {
    const value = await api.journalExportFormats();
    if (disposed || current !== formatsRevision || api !== props.api) return;
    formats.value = value;
    if (!value.some((format) => format.code === selectedFormat.value))
      selectedFormat.value = value[0]?.code ?? 'docx';
  } catch (cause) {
    if (disposed || current !== formatsRevision || cause instanceof StaleSessionGenerationError)
      return;
    exportError.value =
      cause instanceof TeacherApiError ? cause.message : 'Не удалось получить форматы выгрузки.';
    emit('error', cause);
  } finally {
    if (current === formatsRevision) formatsLoading.value = false;
  }
}
async function exportJournal(): Promise<void> {
  const api = props.api;
  const value = effectiveQuery.value;
  const port = props.reportDownload;
  const lessonId = props.selectedLessonId;
  if (!api || !value || !canExport.value) return;
  const current = ++exportRevision;
  const context = journalContextKey(value);
  const format = selectedFormat.value;
  const active = () =>
    !disposed &&
    props.active &&
    current === exportRevision &&
    api === props.api &&
    port === props.reportDownload &&
    lessonId === props.selectedLessonId &&
    context === journalContextKey(effectiveQuery.value) &&
    format === selectedFormat.value;
  const selector = {
    ...value,
    pastOnly: true,
    ...(lessonId !== null ? { lessonId: String(lessonId) } : {}),
  };
  exportLoading.value = true;
  exportError.value = null;
  exportStatus.value = null;
  try {
    if (port) {
      const result = await port.download(
        toTeacherJournalReportRequest(selector, format),
        active,
        (state) => {
          if (active()) exportStatus.value = exportStateLabel(state);
        },
      );
      if (!active() || result === 'stale') return;
      if (result === 'unsupported')
        exportError.value =
          'Скачивание файлов недоступно в этой версии Telegram. Обнови Telegram до версии 8.0 или новее.';
      else
        exportStatus.value =
          result === 'accepted' ? reportDownloadAcceptedMessage(port) : 'Скачивание отменено.';
      return;
    }
    const file = await api.exportJournal({
      semesterId: value.semesterId,
      groupId: value.groupId,
      subjectId: value.subjectId,
      lessonTypes: teacherJournalTypes(value),
      ...(value.dateFrom !== undefined ? { dateFrom: value.dateFrom } : {}),
      ...(value.dateTo !== undefined ? { dateTo: value.dateTo } : {}),
      pastOnly: true,
      ...(lessonId !== null ? { lessonId: String(lessonId) } : {}),
      format,
    });
    if (!active()) return;
    const url = URL.createObjectURL(file.blob);
    objectUrls.add(url);
    const link = document.createElement('a');
    link.href = url;
    link.download = file.filename;
    link.click();
    exportStatus.value = 'Журнал подготовлен, скачивание началось.';
    window.setTimeout(() => {
      objectUrls.delete(url);
      URL.revokeObjectURL(url);
    }, 60_000);
  } catch (cause) {
    if (!active() || cause instanceof StaleSessionGenerationError) return;
    exportError.value =
      cause instanceof TeacherApiError
        ? cause.message
        : cause instanceof Error
          ? cause.message
          : 'Не удалось скачать журнал.';
    emit('error', cause);
  } finally {
    if (current === exportRevision) exportLoading.value = false;
  }
}
function changeFormat(value: string): void {
  if (formats.value.some((format) => format.code === value))
    selectedFormat.value = value as TeacherExportFormatCode;
}
function changeStudent(value: string): void {
  studentId.value = value === 'group' ? null : Number(value);
}
</script>
<template>
  <component
    :is="embedded ? 'section' : 'main'"
    class="teacher-journal"
    :aria-labelledby="embedded ? undefined : 'teacher-journal-title'"
    :aria-label="embedded ? 'Журнал группы' : undefined"
    :aria-busy="loading || exportLoading"
  >
    <section
      v-if="exportLoading"
      class="teacher-journal__preparing"
      role="status"
    >
      <span
        class="teacher-journal__spinner"
        aria-hidden="true"
      />
      {{ exportStatus ?? 'Готовим выгрузку…' }}
    </section>
    <header
      v-if="!embedded"
      class="teacher-journal__header"
    >
      <button
        v-if="showBack"
        type="button"
        class="teacher-journal__icon"
        aria-label="Назад"
        @click="emit('back')"
      >
        <MobileIcon name="back" /> </button
      ><h1 id="teacher-journal-title">
        Журнал<small v-if="groupName">{{ groupName }}</small>
      </h1>
    </header>
    <HeadmanStatsSelect
      v-if="subjects.length"
      label="Предмет"
      :model-value="String(effectiveQuery?.subjectId ?? '')"
      :options="
        subjects.map((subject) => ({
          value: String(subject.subjectId),
          label: subject.subjectName,
        }))
      "
      @update:model-value="changeSubject"
    />
    <p
      v-else-if="completedJournal?.lessons[0]"
      class="teacher-journal__subject"
    >
      {{ completedJournal.lessons[0].subjectName }}
    </p>
    <div
      v-if="effectiveQuery"
      class="teacher-journal__type-toolbar"
    >
      <div
        class="teacher-journal__types"
        role="group"
        aria-label="Типы занятий"
      >
        <button
          v-for="type in availableTypes"
          :key="type"
          type="button"
          :aria-pressed="selectedTypes.includes(type)"
          :disabled="selectedTypes.length >= 3 && !selectedTypes.includes(type)"
          @click="toggleType(type)"
        >
          {{ teacherLessonTypeLabel(type) }}
        </button> </div
      ><button
        type="button"
        class="teacher-journal__icon"
        :aria-label="chart ? 'Показать таблицу' : 'Показать график'"
        :aria-pressed="chart"
        @click="chart = !chart"
      >
        <MobileIcon :name="chart ? 'table' : 'attendance-graph'" />
      </button>
    </div>
    <p
      v-if="!api || !effectiveQuery"
      class="teacher-journal__state"
      role="status"
    >
      Сессия преподавателя недоступна.
    </p>
    <p
      v-else-if="!selectedTypes.length"
      class="teacher-journal__state"
      role="status"
    >
      Выбери один или несколько типов занятий.
    </p>
    <p
      v-else-if="loading"
      class="teacher-journal__state"
      role="status"
    >
      Загружаем журнал за прошедший период…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      title="Журнал недоступен"
      :message="error"
      severity="error"
      action-label="Повторить"
      @action="load"
    />
    <p
      v-else-if="!completedJournal?.lessons.length"
      class="teacher-journal__state"
      role="status"
    >
      В выбранном периоде прошедших занятий нет.
    </p>
    <template v-else>
      <template v-if="chart">
        <HeadmanStatsSelect
          label="Студент"
          :model-value="studentId === null ? 'group' : String(studentId)"
          :options="studentOptions"
          @update:model-value="changeStudent"
        /><TeacherJournalChart
          v-if="chartFrom && chartTo && chartFrom <= chartTo"
          :journal="completedJournal"
          :student-id="studentId"
          :from="chartFrom"
          :to="chartTo"
        /><p
          v-else
          role="status"
        >
          Границы семестра недоступны.
        </p>
      </template>
      <TeacherJournalTable
        v-else
        :journal="completedJournal"
        @open-excuse="emit('open-excuse', $event)"
        @open-manual-excuse="(lessonId, student) => emit('open-manual-excuse', lessonId, student)"
      />
      <p
        v-if="!completedJournal.students.length"
        class="teacher-journal__state"
        role="status"
      >
        В выбранных занятиях нет студентов.
      </p>
    </template>
    <section
      v-if="showExport && effectiveQuery && selectedTypes.length"
      class="teacher-journal__export"
      aria-label="Выгрузка журнала"
    >
      <div data-teacher-action="export-select">
        <HeadmanStatsSelect
          label="Формат журнала"
          :model-value="selectedFormat"
          :options="formats.map((format) => ({ value: format.code, label: format.label }))"
          :disabled="formatsLoading || exportLoading || !formats.length"
          @update:model-value="changeFormat"
        /> </div
      ><button
        class="teacher-journal__icon teacher-journal__download"
        type="button"
        data-teacher-action="export-download"
        aria-label="Скачать журнал за выбранный период"
        :disabled="!canExport"
        @click="exportJournal"
      >
        <MobileIcon name="download" />
      </button>
    </section>
    <StudentWarningBlock
      v-if="exportError"
      title="Не удалось скачать журнал"
      :message="exportError"
      severity="error"
    />
    <p
      v-else-if="exportStatus"
      role="status"
    >
      {{ exportStatus }}
    </p>
  </component>
</template>
