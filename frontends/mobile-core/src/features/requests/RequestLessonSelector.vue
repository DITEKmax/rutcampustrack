<script setup lang="ts">
import { computed, ref } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import checkIcon from '../../assets/admin/check.svg';
import {
  canRequestLesson,
  formatLessonDate,
  formatLessonTime,
  lessonTypeLabel,
  pendingReason,
  removeSelectedLessonId,
  updateSelectedLessonIds,
} from './state';
import type { RequestLesson, RequestLessonOption } from './types';

const props = withDefaults(
  defineProps<{
    mode: 'excuse' | 'late';
    options: readonly RequestLessonOption[];
    modelValue: readonly string[] | string | null;
    disabled?: boolean;
    grouped?: boolean;
    announceSelection?: boolean;
    optionsResolved?: boolean;
    recoveryDisabled?: boolean | null;
  }>(),
  {
    disabled: false,
    grouped: false,
    announceSelection: true,
    optionsResolved: true,
    recoveryDisabled: null,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string[] | string | null];
}>();

interface LessonGroup {
  key: string;
  label: string;
  options: RequestLessonOption[];
}

const selectionStatus = ref('');
const recoveryDisabled = computed(() => props.recoveryDisabled ?? props.disabled);

// Both pickers use their existing source eligibility; stale selections remain
// separate and recoverable until the student explicitly removes them.
const candidateOptions = computed(() =>
  props.options.filter((option) => Boolean(option.lesson?.id) && isEligible(option)),
);
const groups = computed<LessonGroup[]>(() => {
  const byDate = new Map<string, RequestLessonOption[]>();
  for (const option of candidateOptions.value) {
    const lesson = option.lesson;
    if (!lesson || !lesson.id) continue;
    const key = lesson.date || '';
    const current = byDate.get(key);
    if (current) current.push(option);
    else byDate.set(key, [option]);
  }
  return [...byDate.entries()].map(([key, options]) => ({
    key: key || 'unknown-date',
    label: formatLessonDate(key || null),
    options: props.grouped
      ? [...options].sort(
          (a, b) =>
            (a.lesson?.startsAt || '99:99').localeCompare(b.lesson?.startsAt || '99:99') ||
            (a.lesson?.lessonNumber ?? 0) - (b.lesson?.lessonNumber ?? 0),
        )
      : options,
  }));
});

const selectedIds = computed(() => {
  if (Array.isArray(props.modelValue)) return [...props.modelValue];
  return props.modelValue ? [props.modelValue] : [];
});

const retainedSelectedIds = computed(() =>
  selectedIds.value.filter(
    (id) => !candidateOptions.value.some((option) => option.lesson?.id === id),
  ),
);
function retainedOption(id: string): RequestLessonOption | undefined {
  return props.options.find((option) => option.lesson?.id === id);
}

function lessonFor(option: RequestLessonOption): RequestLesson | null {
  return option.lesson;
}

function lessonId(option: RequestLessonOption): string {
  return option.lesson?.id || '';
}

function lessonTimeFor(option: RequestLessonOption): string {
  return option.lesson ? formatLessonTime(option.lesson) : 'Время не указано';
}

function optionId(option: RequestLessonOption, index: number): string {
  return 'request-lesson-' + props.mode + '-' + index + '-' + encodeURIComponent(lessonId(option));
}

function isSelected(option: RequestLessonOption): boolean {
  return selectedIds.value.includes(lessonId(option));
}

function isEligible(option: RequestLessonOption): boolean {
  return canRequestLesson(option, props.mode === 'excuse' ? 'EXCUSE' : 'LATE_CHECKIN');
}

function isAbsent(option: RequestLessonOption): boolean {
  return (
    option.lateCheckinEligible === true ||
    (option.lesson?.status === 'CLOSED' && option.excuseEligible === true)
  );
}

function lifecycleLabel(option: RequestLessonOption): string | null {
  return option.lesson?.status === 'PLANNED'
    ? 'Запланирована'
    : option.lesson?.status === 'ACTIVE'
      ? 'Идёт'
      : null;
}

function unavailableText(option: RequestLessonOption): string {
  return (
    option.unavailableReason ||
    pendingReason(option.pendingRequests) ||
    'Эта пара недоступна для такой заявки.'
  );
}

function selectedCountLabel(count: number): string {
  if (count % 10 === 1 && count % 100 !== 11) return count + ' пара';
  if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20))
    return count + ' пары';
  return count + ' пар';
}

function missingLessonLabel(index: number): string {
  if (!props.optionsResolved) return 'Сохранённая пара №' + (index + 1) + ' пока не загружена';
  return index === 0
    ? 'Сохранённая пара больше недоступна'
    : 'Сохранённая пара №' + (index + 1) + ' больше недоступна';
}

function removeLesson(id: string): void {
  const next = removeSelectedLessonId(selectedIds.value, id);
  selectionStatus.value = 'Пара убрана из заявки. Выбрано ' + selectedCountLabel(next.length);
  emit('update:modelValue', props.mode === 'late' ? (next[0] ?? null) : next);
}

function onExcuseChange(event: Event, id: string): void {
  const checked = (event.target as HTMLInputElement).checked;
  const value =
    props.mode === 'late'
      ? checked
        ? [id]
        : []
      : updateSelectedLessonIds(selectedIds.value, id, checked);
  selectionStatus.value = 'Выбрано ' + selectedCountLabel(value.length);
  emit('update:modelValue', value);
}

function onLateChange(event: Event): void {
  const value = (event.target as HTMLSelectElement).value || null;
  selectionStatus.value = value ? 'Пара выбрана' : 'Пара не выбрана';
  emit('update:modelValue', value);
}
</script>

<template>
  <div class="request-lesson-selector">
    <div
      v-if="retainedSelectedIds.length > 0"
      class="request-lesson-retained"
      aria-label="Сохранённые недоступные пары"
    >
      <p class="request-lesson-retained__help">
        {{ props.optionsResolved ? 'Есть недоступные выбранные пары. ' : '' }}Твой выбор сохранён.
        {{
          props.optionsResolved
            ? 'Убери недоступные пары, чтобы продолжить.'
            : 'Дождись загрузки или убери пару из черновика.'
        }}
      </p>
      <div
        v-for="(id, index) in retainedSelectedIds"
        :key="'missing-' + id"
        class="request-lesson-option request-lesson-option--disabled request-lesson-retained__row"
      >
        <span class="request-lesson-option__copy">
          <span class="request-lesson-option__subject">{{
            retainedOption(id)?.lesson?.subjectName || missingLessonLabel(index)
          }}</span>
          <span
            v-if="retainedOption(id)"
            class="request-lesson-option__reason"
          >
            {{ unavailableText(retainedOption(id)!) }}
          </span>
        </span>
        <button
          class="request-lesson-recovery-action"
          type="button"
          :disabled="recoveryDisabled"
          :aria-label="'Убрать из заявки: сохранённая пара №' + (index + 1)"
          @click="removeLesson(id)"
        >
          Убрать из заявки
        </button>
      </div>
    </div>
    <template v-if="props.mode === 'excuse' || props.grouped">
      <p
        v-if="props.optionsResolved && candidateOptions.length === 0"
        class="request-lesson-selector__empty"
      >
        {{
          props.mode === 'late'
            ? 'Нет доступных пропущенных пар.'
            : props.options.length
              ? 'Доступных пар нет.'
              : 'В выбранные даты пар нет.'
        }}
      </p>
      <div
        v-for="group in groups"
        :key="group.key"
        class="request-lesson-group"
      >
        <fieldset>
          <legend>{{ group.label }}</legend>
          <div
            v-for="(option, index) in group.options"
            :key="lessonId(option)"
            class="request-lesson-option-row"
          >
            <label
              class="request-lesson-option"
              :class="{
                'request-lesson-option--selected': isSelected(option),
                'request-lesson-option--disabled': !isEligible(option),
              }"
            >
              <input
                :id="optionId(option, index)"
                type="checkbox"
                :value="lessonId(option)"
                :checked="isSelected(option)"
                :disabled="props.disabled || !isEligible(option)"
                :aria-describedby="
                  !isEligible(option) ? optionId(option, index) + '-reason' : undefined
                "
                @change="onExcuseChange($event, lessonId(option))"
              />
              <span
                class="request-lesson-option__checkbox"
                aria-hidden="true"
              >
                <SvgIcon
                  v-if="isSelected(option)"
                  :src="checkIcon"
                />
              </span>
              <span class="request-lesson-option__copy">
                <span class="request-lesson-option__subject">{{
                  lessonFor(option)?.subjectName || 'Предмет не указан'
                }}</span>
                <span class="request-lesson-option__meta">
                  <span v-if="!props.grouped">
                    <MobileIcon name="calendar" />
                    {{ formatLessonDate(lessonFor(option)?.date) }}
                  </span>
                  <span>
                    <MobileIcon name="clock" />
                    {{ lessonTimeFor(option) }}
                  </span>
                  <span>
                    <MobileIcon name="lesson" />
                    {{ lessonTypeLabel(lessonFor(option)?.subjectType) }}
                  </span>
                </span>
                <span
                  v-if="!isEligible(option)"
                  :id="optionId(option, index) + '-reason'"
                  class="request-lesson-option__reason"
                >
                  {{ unavailableText(option) }}
                </span>
              </span>
              <span
                v-if="props.grouped && isAbsent(option)"
                class="request-lesson-option__absence"
                role="img"
                aria-label="Не был"
                >н</span
              >
              <span
                v-else-if="props.grouped && lifecycleLabel(option)"
                class="request-lesson-option__lifecycle"
                >{{ lifecycleLabel(option) }}</span
              >
            </label>
            <button
              v-if="isSelected(option) && !isEligible(option)"
              class="request-lesson-recovery-action"
              type="button"
              :disabled="recoveryDisabled"
              :aria-label="
                'Убрать из заявки: ' + (lessonFor(option)?.subjectName || 'недоступная пара')
              "
              @click="removeLesson(lessonId(option))"
            >
              Убрать из заявки
            </button>
          </div>
        </fieldset>
      </div>
    </template>

    <template v-else>
      <label
        class="request-field"
        for="request-late-lesson"
      >
        <span class="request-field__label">Пара</span>
        <span class="request-select-control">
          <select
            id="request-late-lesson"
            :value="typeof props.modelValue === 'string' ? props.modelValue : ''"
            :disabled="props.disabled"
            aria-describedby="request-late-lesson-help"
            @change="onLateChange"
          >
            <option value="">Выбери пару</option>
            <option
              v-for="option in candidateOptions"
              :key="lessonId(option)"
              :value="lessonId(option)"
              :disabled="!isEligible(option)"
            >
              {{ lessonFor(option)?.subjectName || 'Предмет не указан' }} ·
              {{ formatLessonDate(lessonFor(option)?.date) }} · {{ lessonTimeFor(option) }}
            </option>
          </select>
          <svg
            class="request-select-icon"
            viewBox="0 0 16 16"
            aria-hidden="true"
          >
            <path
              d="M3 6L8 11L13 6"
              stroke="currentColor"
            />
          </svg>
        </span>
      </label>
      <p
        id="request-late-lesson-help"
        class="request-lesson-selector__help"
      >
        Выбери одну пару из списка.
      </p>
      <p
        v-if="props.optionsResolved && candidateOptions.length === 0"
        class="request-lesson-selector__empty"
      >
        Нет доступных пропущенных пар.
      </p>
    </template>

    <p
      v-if="props.announceSelection && selectionStatus"
      class="request-lesson-selector__status"
      role="status"
      aria-live="polite"
    >
      {{ selectionStatus }}
    </p>
  </div>
</template>

<style src="./requests.pcss"></style>
