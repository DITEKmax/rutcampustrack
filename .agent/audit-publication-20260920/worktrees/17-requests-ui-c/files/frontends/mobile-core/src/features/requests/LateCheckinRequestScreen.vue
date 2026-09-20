<script setup lang="ts">
import { computed } from 'vue'
import RequestLessonSelector from './RequestLessonSelector.vue'
import { budgetLabel, formatLessonDate, formatLessonTime, lessonTypeLabel } from './state'
import type {
  LateCheckinRequestPayload,
  RequestAccessState,
  RequestBudget,
  RequestLessonOption,
} from './types'

const props = withDefaults(defineProps<{
  access: RequestAccessState
  lessons: readonly RequestLessonOption[]
  lessonId: string | null
  budget: RequestBudget | null
  lessonsLoading?: boolean
  lessonsError?: string | null
  offline?: boolean
  submitting?: boolean
  submitError?: string | null
  disabled?: boolean
}>(), {
  lessonsLoading: false,
  lessonsError: null,
  offline: false,
  submitting: false,
  submitError: null,
  disabled: false,
})

const emit = defineEmits<{
  back: []
  cancel: []
  retryLessons: []
  'update:lessonId': [lessonId: string | null]
  submit: [payload: LateCheckinRequestPayload]
}>()

const selectedOption = computed(() => props.lessons.find((option) => option.lesson?.id === props.lessonId) ?? null)
const retainedLessonUnavailable = computed(() => Boolean(props.lessonId) && !selectedOption.value)
const budgetExhausted = computed(() => {
  const remaining = props.budget?.remaining
  return typeof remaining === 'number' && Number.isFinite(remaining) && remaining <= 0
})
const selectedEligible = computed(() => selectedOption.value?.lateCheckinEligible === true)
const budgetLimitText = computed(() => {
  const limit = props.budget?.limit
  return typeof limit === 'number' && Number.isFinite(limit) ? 'Одна из ' + limit + ' попыток будет использована.' : 'При отправке будет использована одна попытка.'
})
const canSubmit = computed(() => props.access === 'allowed'
  && !props.offline
  && !props.disabled
  && !props.submitting
  && !props.lessonsLoading
  && !props.lessonsError
  && Boolean(props.lessonId)
  && selectedEligible.value
  && !budgetExhausted.value)

function updateLessonId(value: string[] | string | null): void {
  emit('update:lessonId', typeof value === 'string' && value ? value : null)
}

function onSubmit(): void {
  if (!canSubmit.value || !props.lessonId) return
  emit('submit', { lessonId: props.lessonId })
}
</script>

<template>
  <main
    class="requests-form-screen requests-late-screen"
    aria-labelledby="late-request-title"
  >
    <header class="requests-form-header">
      <button
        class="requests-back-button"
        type="button"
        aria-label="Вернуться к выбору типа заявки"
        @click="emit('back')"
      >
        <svg
          viewBox="0 0 16 16"
          aria-hidden="true"
        >
          <path
            d="M10.5 3L5.5 8L10.5 13"
            stroke="currentColor"
            stroke-width="2"
          />
        </svg>
      </button>
      <h1 id="late-request-title">
        Забыл отметиться
      </h1>
    </header>

    <p
      v-if="props.offline"
      class="requests-offline"
      role="status"
    >
      Офлайн · отправка заявки недоступна
    </p>

    <section
      v-if="props.access !== 'allowed'"
      class="requests-state"
      :aria-label="props.access === 'forbidden' ? 'Раздел недоступен' : 'Нет активного семестра'"
    >
      <h2>{{ props.access === 'forbidden' ? 'Раздел недоступен' : 'Нет активного семестра' }}</h2>
      <p>{{ props.access === 'forbidden' ? 'У тебя сейчас нет доступа к заявкам.' : 'Подать заявку можно только в активном семестре.' }}</p>
    </section>

    <form
      v-else
      class="requests-form"
      :aria-busy="props.submitting || props.lessonsLoading"
      @submit.prevent="onSubmit"
    >
      <section class="requests-form__section">
        <p
          v-if="props.lessonsLoading"
          class="requests-form-status"
          role="status"
          aria-live="polite"
        >
          Загружаем пары…
        </p>
        <p
          v-if="props.lessonsError"
          class="request-form-error"
          role="alert"
        >
          {{ props.lessonsError }}
          <button
            class="requests-inline-action"
            type="button"
            @click="emit('retryLessons')"
          >
            Повторить
          </button>
        </p>
        <RequestLessonSelector
          v-if="!props.lessonsLoading"
          mode="late"
          :options="props.lessons"
          :model-value="props.lessonId"
          :disabled="props.disabled || Boolean(props.lessonsError)"
          @update:model-value="updateLessonId"
        />
      </section>

      <article
        v-if="selectedOption?.lesson"
        class="request-late-lesson-card"
        aria-label="Выбранная пара"
      >
        <div class="request-late-lesson-card__head">
          <h2>{{ selectedOption.lesson.subjectName || 'Предмет не указан' }}</h2>
          <span
            class="request-late-mark"
            aria-label="Отметка н"
          >н</span>
        </div>
        <p>
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
          {{ formatLessonDate(selectedOption.lesson.date) }}
        </p>
        <p>
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
          {{ formatLessonTime(selectedOption.lesson) }}
        </p>
        <p>
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
          {{ lessonTypeLabel(selectedOption.lesson.subjectType) }}
        </p>
      </article>
      <p
        v-else
        class="request-validation-hint"
      >
        {{ retainedLessonUnavailable ? 'Выбранная пара больше недоступна. Выбери доступную пару.' : 'Выбери пару, по которой нужно отправить запрос.' }}
      </p>

      <section class="request-budget-card">
        <p>Осталось попыток</p>
        <strong>{{ budgetLabel(props.budget) }}</strong>
      </section>

      <section
        class="request-confirmation-panel"
        aria-labelledby="late-confirmation-title"
      >
        <h2 id="late-confirmation-title">
          Отправить запрос?
        </h2>
        <p v-if="budgetExhausted">
          Попытки закончились. Подать запрос сейчас нельзя.
        </p>
        <p v-else>
          {{ budgetLimitText }}
        </p>
        <p
          v-if="props.submitError"
          class="request-form-error"
          role="alert"
        >
          {{ props.submitError }}
        </p>
        <button
          class="requests-submit-action"
          type="submit"
          :disabled="!canSubmit"
        >
          {{ props.submitting ? 'Отправляем…' : 'Отправить запрос' }}
        </button>
        <button
          class="requests-secondary-action"
          type="button"
          :disabled="props.submitting"
          @click="emit('cancel')"
        >
          Отмена
        </button>
      </section>
    </form>
  </main>
</template>

<style src="./requests.pcss"></style>

