<script setup lang="ts">
import { computed, ref } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import RequestLessonSelector from './RequestLessonSelector.vue';
import { requestSelectionEligible } from './state';
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
  }>(),
  {
    optionsLoaded: false,
    loading: false,
    error: null,
    offline: false,
    readOnly: false,
    disabled: false,
  },
);
const emit = defineEmits<{
  back: [];
  retry: [];
  continue: [];
  'update:lessonIds': [ids: string[]];
}>();
const dateFrom = ref('');
const dateTo = ref('');
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
const selectedOutsideRange = computed(() =>
  props.lessons.filter(
    (option) => props.lessonIds.includes(option.lesson?.id ?? '') && !inRange(option),
  ),
);
// A date filter never deletes or hides the student's existing selection.
const visibleLessons = computed(() =>
  props.lessons.filter(
    (option) => inRange(option) || props.lessonIds.includes(option.lesson?.id ?? ''),
  ),
);
function updateDate(boundary: 'from' | 'to', event: Event): void {
  const value = (event.target as HTMLInputElement).value;
  if (boundary === 'from') dateFrom.value = value;
  else dateTo.value = value;
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
const canContinue = computed(
  () =>
    optionsResolved.value &&
    props.access === 'allowed' &&
    !props.loading &&
    !props.error &&
    !props.offline &&
    !props.readOnly &&
    !props.disabled &&
    !rangeInvalid.value &&
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
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="request-selection-title">
        Выбери {{ props.kind === 'EXCUSE' ? 'пары' : 'пару' }}
      </h1>
    </header>
    <div class="requests-form requests-selection-scroll">
      <p class="requests-screen__description">
        {{
          props.kind === 'EXCUSE'
            ? 'Можно выбрать несколько пар для одной уважительной причины.'
            : 'Выбери одну пару, на которой ты забыл отметиться.'
        }}
      </p>
      <fieldset
        v-if="props.kind === 'EXCUSE'"
        class="request-date-range"
      >
        <legend class="request-field__label">
          Период пар
        </legend>
        <label
          class="request-field"
          for="request-date-from"
        >
          <span class="request-field__label">С даты</span>
          <input
            id="request-date-from"
            type="date"
            :value="dateFrom"
            :disabled="rangeDisabled"
            :aria-invalid="rangeInvalid || undefined"
            :aria-describedby="rangeInvalid ? 'request-date-error' : undefined"
            @input="updateDate('from', $event)"
          >
        </label>
        <label
          class="request-field"
          for="request-date-to"
        >
          <span class="request-field__label">По дату</span>
          <input
            id="request-date-to"
            type="date"
            :value="dateTo"
            :disabled="rangeDisabled"
            :aria-invalid="rangeInvalid || undefined"
            :aria-describedby="rangeInvalid ? 'request-date-error' : undefined"
            @input="updateDate('to', $event)"
          >
        </label>
        <p
          v-if="rangeInvalid"
          id="request-date-error"
          class="requests-form-status"
          role="alert"
        >
          Дата окончания должна быть не раньше даты начала.
        </p>
        <p
          v-if="selectedOutsideRange.length"
          class="requests-form-status"
          role="status"
        >
          Выбрано вне диапазона: {{ selectedOutsideRange.length }}. Выбор сохранён. Расширь период или сними выбор с этих пар ниже, чтобы продолжить.
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
      <RequestLessonSelector
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
    <footer class="requests-selection-footer">
      <p
        class="requests-form__selection-count"
        role="status"
      >
        Выбрано: {{ props.lessonIds.length }}
      </p>
      <button
        class="requests-submit-action"
        type="button"
        :disabled="!canContinue"
        @click="canContinue && emit('continue')"
      >
        Продолжить
      </button>
    </footer>
  </main>
</template>
<style src="./requests.pcss"></style>
<style src="./requests-today.pcss"></style>
