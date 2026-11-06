<script setup lang="ts">
import { onBeforeUnmount, ref, useId, watch } from 'vue';
import MobileIcon from './MobileIcon.vue';
import { StaleSessionGenerationError } from '../session-owner';
import {
  exportStateLabel,
  type ExportJobState,
  type ReportDownloadFormat,
  type ReportDownloadPort,
  type ReportDownloadTicketRequest,
  type ReportFormatOption,
} from '../report-download-client';
import './export-button.pcss';
import type { HeadmanWeeklyWeekOption } from '../../features/headman-journal/headman-journal-client';
const props = withDefaults(
  defineProps<{
    port: ReportDownloadPort | null;
    formats: () => Promise<readonly ReportFormatOption[]>;
    request: (
      format: ReportDownloadFormat,
      weekStarts?: readonly string[],
    ) => ReportDownloadTicketRequest;
    weeks?: (() => Promise<readonly HeadmanWeeklyWeekOption[]>) | null;
    scopeKey: string;
    disabled?: boolean;
    inline?: boolean;
    label?: string;
  }>(),
  { disabled: false, inline: false, label: 'Выгрузка', weeks: null },
);
const emit = defineEmits<{ error: [cause: unknown] }>();
const open = ref(false),
  busy = ref(false);
const formatOptions = ref<readonly ReportFormatOption[]>([]);
const selected = ref<ReportDownloadFormat | ''>('');
const availableWeeks = ref<readonly HeadmanWeeklyWeekOption[]>([]);
const selectedWeeks = ref<string[]>([]);
const period = ref<'semester' | 'weeks'>('semester');
const periodName = useId();
const state = ref<ExportJobState | null>(null),
  status = ref<string | null>(null),
  error = ref<string | null>(null);
let revision = 0,
  disposed = false;
function invalidate(): void {
  revision += 1;
  open.value = false;
  busy.value = false;
  state.value = null;
  status.value = null;
  error.value = null;
}
onBeforeUnmount(() => {
  disposed = true;
  invalidate();
});
async function show(): Promise<void> {
  if (!props.inline && open.value) {
    open.value = false;
    return;
  }
  if (!props.port || props.disabled || busy.value) return;
  const current = ++revision,
    key = props.scopeKey,
    port = props.port;
  busy.value = true;
  error.value = null;
  try {
    const [values, weeks] = await Promise.all([
      props.formats(),
      props.weeks?.() ?? Promise.resolve([]),
    ]);
    if (disposed || current !== revision || key !== props.scopeKey || port !== props.port) return;
    formatOptions.value = values;
    selected.value = values[0]?.code ?? '';
    availableWeeks.value = weeks;
    selectedWeeks.value = weeks.map((week) => week.weekStart);
    period.value = 'semester';
    open.value = true;
  } catch (cause) {
    if (current !== revision || cause instanceof StaleSessionGenerationError) return;
    error.value = cause instanceof Error ? cause.message : 'Не удалось получить форматы.';
    emit('error', cause);
  } finally {
    if (current === revision) busy.value = false;
  }
}
watch(
  () => [props.scopeKey, props.port, props.disabled, props.inline] as const,
  () => {
    invalidate();
    if (!props.inline) return;
    formatOptions.value = [];
    selected.value = '';
    availableWeeks.value = [];
    selectedWeeks.value = [];
    void show();
  },
  { immediate: true, flush: 'sync' },
);
async function download(): Promise<void> {
  const port = props.port,
    format = selected.value;
  if (
    !port ||
    !format ||
    props.disabled ||
    busy.value ||
    (props.weeks && period.value === 'weeks' && selectedWeeks.value.length === 0)
  )
    return;
  const current = ++revision,
    key = props.scopeKey;
  const isCurrent = (): boolean =>
    !disposed &&
    current === revision &&
    key === props.scopeKey &&
    port === props.port &&
    !props.disabled &&
    format === selected.value;
  busy.value = true;
  state.value = null;
  error.value = null;
  status.value = null;
  try {
    const result = await port.download(
      props.request(
        format,
        props.weeks && period.value === 'weeks' ? [...selectedWeeks.value] : undefined,
      ),
      isCurrent,
      (value) => {
        if (isCurrent()) state.value = value;
      },
    );
    if (!isCurrent() || result === 'stale') return;
    status.value =
      result === 'unsupported'
        ? 'Скачивание недоступно в этой версии приложения.'
        : result === 'cancelled'
          ? 'Скачивание отменено.'
          : port.carrier === 'browser'
            ? 'Файл готов. Скачивание передано браузеру.'
            : 'Файл готов. Telegram принял запрос на скачивание.';
  } catch (cause) {
    if (!isCurrent() || cause instanceof StaleSessionGenerationError) return;
    state.value = 'error';
    error.value = cause instanceof Error ? cause.message : 'Не удалось подготовить файл.';
    emit('error', cause);
  } finally {
    if (current === revision) busy.value = false;
  }
}
</script>
<template>
  <section
    class="rct-export"
    :class="{ 'rct-export--inline': inline }"
    :aria-busy="busy"
  >
    <button
      v-if="!inline"
      type="button"
      :disabled="disabled || !port || busy"
      :aria-expanded="open"
      @click="show"
    >
      <MobileIcon name="download" />{{ label }}
    </button>
    <div
      v-if="open || inline"
      class="rct-export__options"
    >
      <label>Формат выгрузки<select
        v-model="selected"
        :disabled="busy || (inline && (!port || disabled || !formatOptions.length))"
      ><option
        v-for="format in formatOptions"
        :key="format.code"
        :value="format.code"
      >{{ format.label }}</option></select></label>
      <fieldset
        v-if="weeks"
        :disabled="busy"
        class="rct-export__weeks"
      >
        <legend>Период журнала</legend>
        <label class="rct-export__choice"><input
          v-model="period"
          type="radio"
          :name="periodName"
          value="semester"
        >С начала семестра по текущую неделю</label>
        <label class="rct-export__choice"><input
          v-model="period"
          type="radio"
          :name="periodName"
          value="weeks"
        >Выбранные недели</label>
        <template v-if="period === 'weeks'">
          <label
            v-for="week in availableWeeks"
            :key="week.weekStart"
            class="rct-export__choice"
          ><input
            v-model="selectedWeeks"
            type="checkbox"
            :value="week.weekStart"
          >{{ week.label }} · {{ week.weekStart }} — {{ week.weekEnd }}</label>
        </template>
      </fieldset>
      <button
        type="button"
        :disabled="
          busy ||
            !selected ||
            disabled ||
            (inline && !port) ||
            (!!weeks && period === 'weeks' && selectedWeeks.length === 0)
        "
        @click="download"
      >
        Скачать
      </button>
    </div>
    <p
      v-if="busy && state"
      role="status"
    >
      {{ exportStateLabel(state)
      }}<progress
        :value="state === 'queued' ? 1 : state === 'preparing' ? 2 : 3"
        max="3"
        aria-label="Подготовка выгрузки"
      />
    </p>
    <p
      v-else-if="busy"
      role="status"
    >
      Получаем форматы…
    </p>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <button
      v-if="inline && error && !open"
      type="button"
      :disabled="disabled || !port || busy"
      @click="show"
    >
      Повторить загрузку форматов
    </button>
    <p
      v-else-if="status"
      role="status"
    >
      {{ status }}
    </p>
  </section>
</template>
