<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue';
import type { HeadmanFeature } from './context';
import { dateLabel } from './context';
import { message } from '../../auth/session';
import {
  toHeadmanWeeklyReportRequest,
  type HeadmanWeeklyExportOptions,
} from '../../../../mobile-core/src/features/headman-journal/headman-journal-client';
import type {
  ReportDownloadFormat,
  ReportDownloadTicketRequest,
  ReportFormatOption,
} from '../../../../mobile-core/src/shared/report-download-client';
import ReportExport from './ReportExport.vue';
const props = defineProps<{
  feature: HeadmanFeature;
  subjectId: number;
  lessonTypes: readonly string[];
  active: boolean;
  mode: 'week' | 'subject';
  weekStart: string;
}>();
const expanded = ref(false),
  loading = ref(false),
  error = ref<string | null>(null);
const options = shallowRef<HeadmanWeeklyExportOptions | null>(null),
  formats = shallowRef<readonly ReportFormatOption[]>([]);
const weeks = ref<string[]>([]),
  format = ref<ReportDownloadFormat>('xlsx');
const report = computed<ReportDownloadTicketRequest | null>(() => {
  if (!options.value || !weeks.value.length) return null;
  if (props.mode === 'subject')
    return props.subjectId && props.lessonTypes.length
      ? {
          kind: 'HEADMAN_SUBJECT_JOURNAL',
          headmanSubjectJournal: {
            subjectId: props.subjectId,
            lessonTypes: [...props.lessonTypes],
            weekStarts: [...weeks.value],
            format: format.value,
          },
        }
      : null;
  const option = options.value.formats.find((item) => item.code === format.value);
  return option ? toHeadmanWeeklyReportRequest(options.value.weeks, weeks.value, option) : null;
});
async function open() {
  expanded.value = !expanded.value;
  if (!expanded.value || options.value) return;
  loading.value = true;
  try {
    const [result, available] = await Promise.all([
      props.feature.journal.getWeeklyExportOptions(),
      props.feature.stats.subjectJournalFormats(),
    ]);
    props.feature.assertCurrent();
    options.value = result;
    formats.value = available;
    weeks.value = result.weeks.some((week) => week.weekStart === props.weekStart)
      ? [props.weekStart]
      : result.weeks.filter((week) => week.current).map((week) => week.weekStart);
  } catch (cause) {
    error.value = message(cause);
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <div class="web-stack"
    ><button
      class="web-button"
      :disabled="!active"
      :aria-expanded="expanded"
      @click="open"
      >Выгрузить журнал</button
    ><div
      v-if="expanded"
      class="headman-panel web-stack"
      ><p v-if="loading">Загрузка форматов…</p
      ><p
        v-if="error"
        class="web-error"
        role="alert"
        >{{ error }}</p
      ><p v-if="options">{{ options.semesterName }}</p
      ><fieldset v-if="options"
        ><legend>Недели</legend
        ><div class="headman-actions"
          ><label
            v-for="week in options.weeks"
            :key="week.weekStart"
            ><input
              v-model="weeks"
              type="checkbox"
              :value="week.weekStart"
            />{{ dateLabel(week.weekStart) }} — {{ dateLabel(week.weekEnd) }}</label
          ></div
        ></fieldset
      ><label class="web-field"
        >Формат<select v-model="format"
          ><option
            v-for="item in mode === 'subject' ? formats : options?.formats"
            :key="item.code"
            :value="item.code"
            >{{ item.label }}</option
          ></select
        ></label
      ><p
        v-if="mode === 'subject' && (!subjectId || !lessonTypes.length)"
        class="web-notice"
        >Выбери предмет и тип занятия для предметного журнала.</p
      ><ReportExport
        :key="`journal-${mode}`"
        :transport="feature.transport"
        :scope="`journal-${mode}`"
        :report="report"
        :disabled="!active || loading"
        :label="
          mode === 'subject' ? 'Скачать предметный журнал' : 'Скачать выбранные недели'
        " /></div
  ></div>
</template>
