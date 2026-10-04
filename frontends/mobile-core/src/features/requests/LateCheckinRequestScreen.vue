<script setup lang="ts">
import { computed } from 'vue'
import type { TodayLesson } from '../../api/types'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import RequestLessonSummary from './RequestLessonSummary.vue'
import RequestLessonSelector from './RequestLessonSelector.vue'
import { budgetLabel } from './state'
import type {
  LateCheckinRequestPayload,
  RequestAccessState,
  RequestBudget,
  RequestLesson,
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
  ambiguous?: boolean
  todayLesson?: TodayLesson | null
  selectedLesson?: RequestLesson | null
  attendanceStatus?: 'PRESENT' | 'ABSENT' | 'EXCUSED' | null
  backLabel?: string
}>(), {
  lessonsLoading: false,
  lessonsError: null,
  offline: false,
  submitting: false,
  submitError: null,
  disabled: false,
  ambiguous: false,
  todayLesson: null,
  selectedLesson: null,
  attendanceStatus: null,
  backLabel: 'Вернуться назад',
})

const emit = defineEmits<{
  back: []
  cancel: []
  retryLessons: []
  abandon: []
  'update:lessonId': [lessonId: string | null]
  submit: [payload: LateCheckinRequestPayload]
}>()

const contextLesson = computed<RequestLesson | null>(() => props.todayLesson ? {
  id: props.todayLesson.schedule.id,
  subjectName: props.todayLesson.schedule.subject.name,
  subjectType: props.todayLesson.schedule.subject.type,
  date: props.todayLesson.schedule.date,
  startsAt: props.todayLesson.schedule.startsAt,
  endsAt: props.todayLesson.schedule.endsAt,
  status: props.todayLesson.schedule.status,
} : props.selectedLesson)
const contextStatus = computed(() => props.todayLesson ? props.todayLesson.attendance?.status ?? null : props.attendanceStatus)

const selectedOption = computed(() => props.lessons.find((option) => option.lesson?.id === props.lessonId) ?? null)
const retainedLessonUnavailable = computed(() => Boolean(props.lessonId) && !selectedOption.value)
const budgetExhausted = computed(() => {
  const remaining = props.budget?.remaining
  return typeof remaining === 'number' && Number.isFinite(remaining) && remaining <= 0
})
const selectedEligible = computed(() => selectedOption.value?.lateCheckinEligible === true)
const todaySelectionMatches = computed(() => !contextLesson.value || props.lessonId === contextLesson.value.id)
const budgetLimitText = computed(() => {
  const limit = props.budget?.limit
  return typeof limit === 'number' && Number.isFinite(limit) ? 'Одна из ' + limit + ' попыток будет использована.' : 'При отправке будет использована одна попытка.'
})
const canSubmit = computed(() => props.access === 'allowed'
  && !props.offline
  && !props.disabled
  && !props.submitting
  && !props.ambiguous
  && !props.lessonsLoading
  && !props.lessonsError
  && Boolean(props.lessonId)
  && selectedEligible.value
  && todaySelectionMatches.value
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
        :aria-label="props.backLabel"
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="late-request-title">
        Забыл отметиться
      </h1>
    </header>

    <StudentWarningBlock
      v-if="props.offline"
      title="Нет подключения"
      message="Отправка заявки недоступна. Твой черновик сохранён; повтори загрузку, когда появится интернет."
      action-label="Перезагрузить"
      :action-disabled="props.lessonsLoading || props.submitting"
      @action="emit('retryLessons')"
    />

    <StudentWarningBlock
      v-if="props.access !== 'allowed'"
      severity="error"
      :title="props.access === 'forbidden' ? 'Раздел недоступен' : 'Нет активного семестра'"
      :message="props.access === 'forbidden' ? 'У тебя сейчас нет доступа к заявкам.' : 'Подать заявку можно только в активном семестре.'"
    />

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
        <StudentWarningBlock
          v-if="props.lessonsError"
          severity="error"
          title="Не удалось загрузить варианты"
          :message="props.lessonsError"
          action-label="Повторить"
          :action-disabled="props.lessonsLoading || props.submitting"
          @action="emit('retryLessons')"
        />
        <RequestLessonSummary
          v-if="contextLesson"
          :lesson="contextLesson"
          :attendance-status="contextStatus"
        />
        <p
          v-if="contextLesson && !props.lessonsLoading && !props.lessonsError && (!todaySelectionMatches || !selectedEligible)"
          class="request-validation-hint"
          role="alert"
        >
          {{ selectedOption?.unavailableReason || 'Эта пара больше недоступна для запроса. Вернись назад и выбери доступную пару.' }}
        </p>
        <RequestLessonSelector
          v-if="!contextLesson && !props.lessonsLoading"
          mode="late"
          :options="props.lessons"
          :model-value="props.lessonId"
          :disabled="props.disabled || Boolean(props.lessonsError)"
          @update:model-value="updateLessonId"
        />
      </section>

      <RequestLessonSummary
        v-if="!contextLesson && selectedOption?.lesson"
        :lesson="selectedOption.lesson"
      />
      <p
        v-else-if="!contextLesson"
        class="request-validation-hint"
      >
        {{ retainedLessonUnavailable ? 'Выбранная пара больше недоступна. Выбери доступную пару.' : 'Выбери пару, по которой нужно отправить запрос.' }}
      </p>

      <p
        v-if="!contextLesson && selectedOption && !selectedEligible"
        class="request-validation-hint"
        role="alert"
      >
        {{ selectedOption.unavailableReason || 'Эта пара недоступна для запроса.' }}
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
        <p
          v-if="budgetExhausted"
          class="request-validation-hint"
          role="alert"
        >
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
          v-if="props.ambiguous"
          class="requests-secondary-action"
          type="button"
          @click="emit('abandon')"
        >
          Отказаться от неподтверждённой отправки
        </button>
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
<style src="./requests-today.pcss"></style>

