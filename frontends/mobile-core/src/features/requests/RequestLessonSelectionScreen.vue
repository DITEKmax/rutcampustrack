<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import RequestLessonSelector from './RequestLessonSelector.vue';
import RequestDateCalendar from './RequestDateCalendar.vue';
import { canRequestLesson, requestSelectionEligible } from './state';
import type { RequestAccessState, RequestKind, RequestLessonOption } from './types';
const props = withDefaults(
  defineProps<{
    kind: RequestKind;
    lessons: readonly RequestLessonOption[];
    lessonIds: readonly string[];
    access: RequestAccessState;
    optionsLoaded?: boolean;
    loading?: boolean;
    error?: string | null;
    offline?: boolean;
    readOnly?: boolean;
    disabled?: boolean;
    dateRange?: { from: string; to: string } | null;
  }>(),
  {
    optionsLoaded: false,
    loading: false,
    error: null,
    offline: false,
    readOnly: false,
    disabled: false,
    dateRange: null,
  },
);
const emit = defineEmits<{
  back: [];
  retry: [];
  continue: [];
  'update:lessonIds': [ids: string[]];
  'update:dateRange': [range: { from: string; to: string }];
}>();
function validDate(date: string | null | undefined): date is string {
  return Boolean(
    date &&
      /^\d{4}-\d{2}-\d{2}$/.test(date) &&
      Number.isFinite(Date.parse(`${date}T12:00:00Z`)) &&
      new Date(`${date}T12:00:00Z`).toISOString().slice(0, 10) === date,
  );
}
const lessonDates = computed(() =>
  props.lessons
    .map((option) => option.lesson?.date)
    .filter(validDate)
    .sort(),
);
const dateBounds = computed(() => ({
  from: lessonDates.value[0] ?? '',
  to: lessonDates.value.at(-1) ?? '',
}));
const selectedDates = props.lessons
  .filter((option) => props.lessonIds.includes(option.lesson?.id ?? ''))
  .map((option) => option.lesson?.date)
  .filter(validDate)
  .sort();
const dateFrom = ref(props.dateRange?.from || selectedDates[0] || dateBounds.value.from);
const dateTo = ref(props.dateRange?.to || selectedDates.at(-1) || dateBounds.value.to);
const rangeApplied = ref(props.kind !== 'EXCUSE' || props.lessonIds.length > 0);
const calendarBoundary = ref<'from' | 'to' | null>(null);
const dateFromButton = ref<HTMLButtonElement | null>(null);
const dateToButton = ref<HTMLButtonElement | null>(null);
watch(dateBounds, (bounds) => {
  if (!dateFrom.value) dateFrom.value = bounds.from;
  if (!dateTo.value) dateTo.value = bounds.to;
});
watch(
  () => props.dateRange,
  (range) => {
    if (!range || (range.from === dateFrom.value && range.to === dateTo.value)) return;
    dateFrom.value = range.from;
    dateTo.value = range.to;
    rangeApplied.value = props.lessonIds.length > 0;
  },
);
function dateLabel(date: string): string {
  return validDate(date)
    ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeZone: 'UTC' }).format(
        new Date(`${date}T12:00:00Z`),
      )
    : 'Выбери дату';
}
async function closeCalendar(): Promise<void> {
  const boundary = calendarBoundary.value;
  calendarBoundary.value = null;
  await nextTick();
  (boundary === 'from' ? dateFromButton.value : dateToButton.value)?.focus?.();
}
function back(): void {
  if (calendarBoundary.value) void closeCalendar();
  else emit('back');
}
const rangeInvalid = computed(
  () =>
    props.kind === 'EXCUSE' &&
    Boolean(dateFrom.value && dateTo.value && dateFrom.value > dateTo.value),
);
function inRange(option: RequestLessonOption): boolean {
  if (props.kind !== 'EXCUSE' || (!dateFrom.value && !dateTo.value)) return true;
  const date = option.lesson?.date;
  return (
    !rangeInvalid.value &&
    Boolean(
      date &&
        (!dateFrom.value || date >= dateFrom.value) &&
        (!dateTo.value || date <= dateTo.value),
    )
  );
}
const rangeOutsideBounds = computed(() =>
  Boolean(
    props.kind === 'EXCUSE' &&
      dateBounds.value.from &&
      dateFrom.value &&
      dateTo.value &&
      (dateFrom.value < dateBounds.value.from || dateTo.value > dateBounds.value.to),
  ),
);
const selectedOutsideRange = computed(() =>
  props.lessons.filter(
    (option) => props.lessonIds.includes(option.lesson?.id ?? '') && !inRange(option),
  ),
);
// Applying a date filter retains existing selections for explicit recovery.
const visibleLessons = computed(() =>
  props.lessons.filter(
    (option) => inRange(option) || props.lessonIds.includes(option.lesson?.id ?? ''),
  ),
);
function updateDate(date: string): void {
  if (
    rangeDisabled.value ||
    !validDate(date) ||
    date < dateBounds.value.from ||
    date > dateBounds.value.to
  )
    return;
  if (calendarBoundary.value === 'from') dateFrom.value = date;
  else if (calendarBoundary.value === 'to') dateTo.value = date;
  else return;
  rangeApplied.value = false;
  emit('update:dateRange', { from: dateFrom.value, to: dateTo.value });
  void closeCalendar();
}
function showLessons(): void {
  if (
    rangeDisabled.value ||
    rangeInvalid.value ||
    rangeOutsideBounds.value ||
    !dateFrom.value ||
    !dateTo.value
  )
    return;
  calendarBoundary.value = null;
  rangeApplied.value = true;
  emit('update:dateRange', { from: dateFrom.value, to: dateTo.value });
}
const optionsResolved = computed(
  () =>
    props.optionsLoaded &&
    props.access === 'allowed' &&
    !props.offline &&
    !props.loading &&
    !props.error,
);
const rangeDisabled = computed(
  () =>
    props.disabled ||
    props.readOnly ||
    props.offline ||
    props.access !== 'allowed' ||
    !optionsResolved.value,
);
const selectionHint = computed(() => {
  if (props.loading) return 'Дождись загрузки пар.';
  if (props.error || !optionsResolved.value)
    return 'Для продолжения нужны актуальные данные о парах.';
  if (!rangeApplied.value || rangeInvalid.value || selectedOutsideRange.value.length) return '';
  if (props.lessons.length && !props.lessons.some((option) => canRequestLesson(option, props.kind)))
    return props.kind === 'EXCUSE' ? 'Доступных пар нет.' : 'Нет доступных пропущенных пар.';
  if (!props.lessonIds.length)
    return props.kind === 'EXCUSE'
      ? 'Выбери хотя бы одну доступную пару.'
      : 'Выбери одну доступную пару.';
  if (!requestSelectionEligible(props.lessons, props.lessonIds, props.kind))
    return 'Убери недоступные пары из выбора, чтобы продолжить.';
  return '';
});
const canContinue = computed(
  () =>
    optionsResolved.value &&
    props.access === 'allowed' &&
    !props.loading &&
    !props.error &&
    !props.offline &&
    !props.readOnly &&
    !props.disabled &&
    rangeApplied.value &&
    !rangeInvalid.value &&
    !rangeOutsideBounds.value &&
    selectedOutsideRange.value.length === 0 &&
    requestSelectionEligible(props.lessons, props.lessonIds, props.kind),
);
function updateSelection(value: string[] | string | null): void {
  emit('update:lessonIds', Array.isArray(value) ? [...value] : value ? [value] : []);
}
</script>
<template>
  <main
    class="requests-form-screen requests-selection-screen"
    aria-labelledby="request-selection-title"
  >
    <header class="requests-form-header">
      <button
        class="requests-back-button"
        type="button"
        aria-label="Вернуться к типу заявки"
        @click="back"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="request-selection-title"> Выбери {{ props.kind === 'EXCUSE' ? 'пары' : 'пару' }} </h1>
    </header>
    <div class="requests-form requests-selection-scroll">
      <p
        v-if="props.kind !== 'EXCUSE'"
        class="requests-screen__description"
      >
        Выбери одну пару, на которой ты забыл отметиться.
      </p>
      <fieldset
        v-if="props.kind === 'EXCUSE'"
        class="request-date-range"
        aria-label="Даты пар"
      >
        <div class="request-date-range__fields">
          <div class="request-field">
            <span
              id="request-date-from-label"
              class="request-field__label"
              >С даты</span
            >
            <button
              id="request-date-from"
              ref="dateFromButton"
              class="request-date-trigger"
              type="button"
              :disabled="rangeDisabled || !dateBounds.from"
              :aria-expanded="calendarBoundary === 'from'"
              aria-controls="request-date-calendar"
              :aria-label="'С даты: ' + dateLabel(dateFrom)"
              :aria-invalid="rangeInvalid || rangeOutsideBounds || undefined"
              :aria-describedby="
                rangeInvalid || rangeOutsideBounds ? 'request-date-error' : undefined
              "
              @click="calendarBoundary === 'from' ? closeCalendar() : (calendarBoundary = 'from')"
            >
              <span>{{ dateLabel(dateFrom) }}</span
              ><MobileIcon name="calendar" />
            </button>
          </div>
          <div class="request-field">
            <span
              id="request-date-to-label"
              class="request-field__label"
              >По дату</span
            >
            <button
              id="request-date-to"
              ref="dateToButton"
              class="request-date-trigger"
              type="button"
              :disabled="rangeDisabled || !dateBounds.to"
              :aria-expanded="calendarBoundary === 'to'"
              aria-controls="request-date-calendar"
              :aria-label="'По дату: ' + dateLabel(dateTo)"
              :aria-invalid="rangeInvalid || rangeOutsideBounds || undefined"
              :aria-describedby="
                rangeInvalid || rangeOutsideBounds ? 'request-date-error' : undefined
              "
              @click="calendarBoundary === 'to' ? closeCalendar() : (calendarBoundary = 'to')"
            >
              <span>{{ dateLabel(dateTo) }}</span
              ><MobileIcon name="calendar" />
            </button>
          </div>
        </div>
        <RequestDateCalendar
          v-if="calendarBoundary && dateBounds.from && dateBounds.to"
          id="request-date-calendar"
          :model-value="calendarBoundary === 'from' ? dateFrom : dateTo"
          :min="dateBounds.from"
          :max="dateBounds.to"
          :label="calendarBoundary === 'from' ? 'С даты' : 'По дату'"
          :disabled="rangeDisabled"
          @update:model-value="updateDate"
          @close="closeCalendar"
        />
        <p
          v-if="rangeInvalid || rangeOutsideBounds"
          id="request-date-error"
          class="requests-form-status"
          role="alert"
        >
          {{
            rangeInvalid
              ? 'Дата окончания должна быть не раньше даты начала.'
              : 'Выбери даты в доступном диапазоне пар.'
          }}
        </p>
        <button
          class="requests-secondary-action requests-show-lessons"
          type="button"
          :disabled="rangeDisabled || rangeInvalid || rangeOutsideBounds || !dateFrom || !dateTo"
          @click="showLessons"
        >
          Показать пары
        </button>
        <p
          v-if="rangeApplied && selectedOutsideRange.length"
          class="requests-form-status"
          role="status"
        >
          Выбрано вне диапазона: {{ selectedOutsideRange.length }}. Выбор сохранён. Расширь период
          или сними выбор с этих пар ниже, чтобы продолжить.
        </p>
      </fieldset>
      <StudentWarningBlock
        v-if="props.offline || props.readOnly || props.access !== 'allowed'"
        :severity="props.access === 'allowed' ? 'warning' : 'error'"
        :title="
          props.offline
            ? 'Нет подключения'
            : props.readOnly
              ? 'Только чтение'
              : props.access === 'forbidden'
                ? 'Раздел недоступен'
                : 'Нет активного семестра'
        "
        :message="
          props.offline
            ? 'Твой черновик сохранён. Продолжить можно после подключения к интернету.'
            : 'Подача заявки сейчас недоступна. Твой выбор сохранён.'
        "
      />
      <p
        v-if="props.loading"
        role="status"
        class="requests-form-status"
      >
        Загружаем пары…
      </p>
      <StudentWarningBlock
        v-if="props.error"
        severity="error"
        title="Не удалось загрузить пары"
        :message="props.error"
        action-label="Повторить"
        :action-disabled="props.loading"
        @action="emit('retry')"
      />
      <p
        v-if="optionsResolved && !dateBounds.from && !rangeApplied"
        class="requests-form-status"
        role="status"
      >
        {{ props.lessons.length ? 'Даты пар недоступны.' : 'Доступных пар нет.' }}
      </p>
      <RequestLessonSelector
        v-if="rangeApplied"
        :mode="props.kind === 'EXCUSE' ? 'excuse' : 'late'"
        grouped
        :announce-selection="false"
        :options-resolved="optionsResolved"
        :recovery-disabled="
          props.disabled || props.readOnly || props.offline || props.access !== 'allowed'
        "
        :options="visibleLessons"
        :model-value="props.kind === 'EXCUSE' ? props.lessonIds : (props.lessonIds[0] ?? null)"
        :disabled="
          props.disabled ||
          props.readOnly ||
          props.offline ||
          props.access !== 'allowed' ||
          props.loading ||
          Boolean(props.error) ||
          !optionsResolved
        "
        @update:model-value="updateSelection"
      />
    </div>
    <footer
      v-if="rangeApplied"
      class="requests-selection-footer"
    >
      <p
        class="requests-form__selection-count request-visually-hidden"
        role="status"
      >
        {{
          props.kind === 'EXCUSE'
            ? `Выбрано пар: ${props.lessonIds.length}`
            : `Выбрана пара: ${props.lessonIds.length ? 'да' : 'нет'}`
        }}
      </p>
      <p
        v-if="selectionHint"
        id="request-selection-hint"
        class="requests-selection-hint"
      >
        {{ selectionHint }}
      </p>
      <button
        class="requests-submit-action"
        type="button"
        :disabled="!canContinue"
        :aria-describedby="selectionHint ? 'request-selection-hint' : undefined"
        @click="canContinue && emit('continue')"
      >
        Продолжить
      </button>
    </footer>
  </main>
</template>
<style src="./requests.pcss"></style>
<style src="./requests-today.pcss"></style>
