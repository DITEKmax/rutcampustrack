<script setup lang="ts">
import { computed, ref } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import {
  canRequestLesson,
  formatLessonDate,
  formatLessonTime,
  lessonTypeLabel,
  pendingReason,
  removeSelectedLessonId,
  updateSelectedLessonIds,
} from './state'
import type { RequestLesson, RequestLessonOption } from './types'

const props = withDefaults(defineProps<{
  mode: 'excuse' | 'late'
  options: readonly RequestLessonOption[]
  modelValue: readonly string[] | string | null
  disabled?: boolean
  grouped?: boolean
  announceSelection?: boolean
  optionsResolved?: boolean
  recoveryDisabled?: boolean | null
}>(), {
  disabled: false,
  grouped: false,
  announceSelection: true,
  optionsResolved: true,
  recoveryDisabled: null,
})

const emit = defineEmits<{
  'update:modelValue': [value: string[] | string | null]
}>()

interface LessonGroup {
  key: string
  label: string
  options: RequestLessonOption[]
}

const selectionStatus = ref('')
const recoveryDisabled = computed(() => props.recoveryDisabled ?? props.disabled)

const groups = computed<LessonGroup[]>(() => {
  const byDate = new Map<string, RequestLessonOption[]>()
  for (const option of props.options) {
    const lesson = option.lesson
    if (!lesson || !lesson.id) continue
    const key = lesson.date || ''
    const current = byDate.get(key)
    if (current) current.push(option)
    else byDate.set(key, [option])
  }
  return [...byDate.entries()].map(([key, options]) => ({
    key: key || 'unknown-date',
    label: formatLessonDate(key || null),
    options: props.grouped
      ? [...options].sort((a, b) => (a.lesson?.startsAt || '99:99').localeCompare(b.lesson?.startsAt || '99:99') || (a.lesson?.lessonNumber ?? 0) - (b.lesson?.lessonNumber ?? 0))
      : options,
  }))
})

const selectedIds = computed(() => {
  if (Array.isArray(props.modelValue)) return [...props.modelValue]
  return props.modelValue ? [props.modelValue] : []
})

const missingSelectedIds = computed(() => selectedIds.value.filter((id) => !props.options.some((option) => option.lesson?.id === id)))

function lessonFor(option: RequestLessonOption): RequestLesson | null {
  return option.lesson
}

function lessonId(option: RequestLessonOption): string {
  return option.lesson?.id || ''
}

function lessonTimeFor(option: RequestLessonOption): string {
  return option.lesson ? formatLessonTime(option.lesson) : 'Время не указано'
}

function optionId(option: RequestLessonOption, index: number): string {
  return 'request-lesson-' + props.mode + '-' + index + '-' + encodeURIComponent(lessonId(option))
}

function isSelected(option: RequestLessonOption): boolean {
  return selectedIds.value.includes(lessonId(option))
}

function isEligible(option: RequestLessonOption): boolean {
  return canRequestLesson(option, props.mode === 'excuse' ? 'EXCUSE' : 'LATE_CHECKIN')
}

function unavailableText(option: RequestLessonOption): string {
  return option.unavailableReason || pendingReason(option.pendingRequests) || 'Эта пара недоступна для такой заявки.'
}

function selectedCountLabel(count: number): string {
  if (count % 10 === 1 && count % 100 !== 11) return count + ' пара'
  if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) return count + ' пары'
  return count + ' пар'
}

function missingLessonLabel(index: number): string {
  if (!props.optionsResolved) return 'Сохранённая пара №' + (index + 1) + ' пока не загружена'
  return index === 0 ? 'Сохранённая пара больше недоступна' : 'Сохранённая пара №' + (index + 1) + ' больше недоступна'
}

function removeLesson(id: string): void {
  const next = removeSelectedLessonId(selectedIds.value, id)
  selectionStatus.value = 'Пара убрана из заявки. Выбрано ' + selectedCountLabel(next.length)
  emit('update:modelValue', props.mode === 'late' ? next[0] ?? null : next)
}

function onExcuseChange(event: Event, id: string): void {
  const checked = (event.target as HTMLInputElement).checked
  const value = props.mode === 'late' ? checked ? [id] : [] : updateSelectedLessonIds(selectedIds.value, id, checked)
  selectionStatus.value = 'Выбрано ' + selectedCountLabel(value.length)
  emit('update:modelValue', value)
}

function onLateChange(event: Event): void {
  const value = (event.target as HTMLSelectElement).value || null
  selectionStatus.value = value ? 'Пара выбрана' : 'Пара не выбрана'
  emit('update:modelValue', value)
}
</script>

<template>
  <div class="request-lesson-selector">
    <template v-if="props.mode === 'excuse' || props.grouped">
      <p
        v-if="props.optionsResolved && props.options.length === 0"
        class="request-lesson-selector__empty"
      >
        В выбранные даты пар нет.
      </p>
      <section
        v-if="missingSelectedIds.length > 0"
        class="request-lesson-recovery-panel"
        aria-labelledby="request-lesson-recovery-title"
      >
        <div class="request-lesson-recovery-panel__head">
          <MobileIcon name="warning" />
          <h3 id="request-lesson-recovery-title">
            {{ props.optionsResolved ? 'Есть недоступные выбранные пары' : 'Выбранные пары пока не загружены' }}
          </h3>
        </div>
        <p>
          {{ props.optionsResolved ? 'Обнови выбор: эти пары больше не пришли с сервера.' : 'Твой выбор сохранён. Дождись загрузки или убери пару из черновика.' }}
        </p>
        <ul class="request-lesson-recovery-list">
          <li
            v-for="(id, index) in missingSelectedIds"
            :key="'missing-' + id"
          >
            <span>{{ missingLessonLabel(index) }}</span>
            <button
              class="request-lesson-recovery-action"
              type="button"
              :disabled="recoveryDisabled"
              :aria-label="'Убрать из заявки: сохранённая пара №' + (index + 1)"
              @click="removeLesson(id)"
            >
              Убрать из заявки
            </button>
          </li>
        </ul>
      </section>
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
              :class="{ 'request-lesson-option--selected': isSelected(option), 'request-lesson-option--disabled': !isEligible(option) }"
            >
              <input
                :id="optionId(option, index)"
                type="checkbox"
                :value="lessonId(option)"
                :checked="isSelected(option)"
                :disabled="props.disabled || !isEligible(option)"
                :aria-describedby="!isEligible(option) ? optionId(option, index) + '-reason' : undefined"
                @change="onExcuseChange($event, lessonId(option))"
              >
              <span class="request-lesson-option__copy">
                <span class="request-lesson-option__subject">{{ lessonFor(option)?.subjectName || 'Предмет не указан' }}</span>
                <span class="request-lesson-option__meta">
                  <span>
                    <svg
                      class="request-icon request-icon--date"
                      viewBox="0 0 15.04 15.04"
                      aria-hidden="true"
                    >
                      <path
                        d="M3.76 5.64H13.16V13.16H3.76V5.828M5.64 3.76V7.52M11.28 3.76V7.52M3.76 8.46H13.16"
                        stroke="currentColor"
                      />
                    </svg>
                    {{ formatLessonDate(lessonFor(option)?.date) }}
                  </span>
                  <span>
                    <svg
                      class="request-icon request-icon--time"
                      viewBox="0 0 7.5 7.5"
                      aria-hidden="true"
                    >
                      <path
                        d="M3.75 1.82692V3.75L5.07212 4.47115M3.75 0.625C5.47596 0.625 6.875 2.02404 6.875 3.75C6.875 5.47596 5.47596 6.875 3.75 6.875C2.02404 6.875 0.625 5.47596 0.625 3.75C0.625 2.02404 2.02404 0.625 3.75 0.625Z"
                        stroke="currentColor"
                      />
                    </svg>
                    {{ lessonTimeFor(option) }}
                  </span>
                  <span>
                    <svg
                      class="request-icon request-icon--lesson-type"
                      viewBox="0 0 10 10"
                      aria-hidden="true"
                    >
                      <path
                        d="M5 6.7663V8.125M3.55769 8.125H6.44231M3.07692 3.50543H6.92308M3.07692 5.13587H5.72115M1.875 1.875H8.125V6.7663H1.875V1.875Z"
                        stroke="currentColor"
                      />
                    </svg>
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
            </label>
            <button
              v-if="isSelected(option) && !isEligible(option)"
              class="request-lesson-recovery-action"
              type="button"
              :disabled="recoveryDisabled"
              :aria-label="'Убрать из заявки: ' + (lessonFor(option)?.subjectName || 'недоступная пара')"
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
              v-for="option in props.options"
              :key="lessonId(option)"
              :value="lessonId(option)"
              :disabled="!isEligible(option)"
            >
              {{ lessonFor(option)?.subjectName || 'Предмет не указан' }} · {{ formatLessonDate(lessonFor(option)?.date) }} · {{ lessonTimeFor(option) }}
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
      <ul
        v-if="props.options.some((option) => !isEligible(option))"
        class="request-lesson-selector__unavailable"
        aria-label="Недоступные пары"
      >
        <li
          v-for="option in props.options.filter((candidate) => !isEligible(candidate))"
          :key="'unavailable-' + lessonId(option)"
        >
          {{ lessonFor(option)?.subjectName || 'Предмет не указан' }}: {{ unavailableText(option) }}
        </li>
      </ul>
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
