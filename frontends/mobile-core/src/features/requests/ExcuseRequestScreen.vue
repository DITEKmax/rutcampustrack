<script setup lang="ts">
import { computed } from 'vue'
import type { TodayLesson } from '../../api/types'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentLessonCard from '../../shared/components/StudentLessonCard.vue'
import RequestAttachmentField from './RequestAttachmentField.vue'
import RequestLessonSelector from './RequestLessonSelector.vue'
import type {
  ExcuseRequestPayload,
  RequestAccessState,
  RequestFileLimits,
  RequestFileRef,
  RequestLessonOption,
  RequestReasonOption,
} from './types'

const props = withDefaults(defineProps<{
  access: RequestAccessState
  lessons: readonly RequestLessonOption[]
  lessonIds: readonly string[]
  reasons: readonly RequestReasonOption[]
  reason: string | null
  comment: string
  files: readonly RequestFileRef[]
  fileLimits: RequestFileLimits | null
  lessonsLoading?: boolean
  lessonsError?: string | null
  offline?: boolean
  submitting?: boolean
  submitError?: string | null
  disabled?: boolean
  ambiguous?: boolean
  todayLesson?: TodayLesson | null
}>(), {
  lessonsLoading: false,
  lessonsError: null,
  offline: false,
  submitting: false,
  submitError: null,
  disabled: false,
  ambiguous: false,
  todayLesson: null,
})

const emit = defineEmits<{
  back: []
  retryLessons: []
  'update:lessonIds': [lessonIds: string[]]
  'update:reason': [reason: string | null]
  'update:comment': [comment: string]
  'update:files': [files: RequestFileRef[]]
  abandon: []
  submit: [payload: ExcuseRequestPayload]
}>()

const selectedEligible = computed(() => props.lessonIds.length > 0 && props.lessonIds.every((id) => props.lessons.some((option) => option.lesson?.id === id && option.excuseEligible === true)))
const selectedReason = computed(() => props.reasons.find((option) => option.code === props.reason))
const reasonAvailable = computed(() => Boolean(props.reason) && selectedReason.value !== undefined)
const retainedReasonUnavailable = computed(() => Boolean(props.reason) && props.reasons.length > 0 && !reasonAvailable.value)
const commentRequired = computed(() => selectedReason.value?.commentRequired === true)
const commentTooLong = computed(() => props.comment.length > 1000)
const commentValid = computed(() => !commentTooLong.value && (!commentRequired.value || props.comment.trim().length > 0))
const todaySelectionMatches = computed(() => !props.todayLesson || (props.lessonIds.length === 1 && props.lessonIds[0] === props.todayLesson.schedule.id))
const todayOption = computed(() => props.lessons.find((option) => option.lesson?.id === props.todayLesson?.schedule.id))
const todayUnavailable = computed(() => props.todayLesson && !props.lessonsLoading && !props.lessonsError && (!todaySelectionMatches.value || todayOption.value?.excuseEligible !== true))
const canSubmit = () => props.access === 'allowed'
  && !props.offline
  && !props.disabled
  && !props.submitting
  && !props.ambiguous
  && !props.lessonsLoading
  && !props.lessonsError
  && selectedEligible.value
  && todaySelectionMatches.value
  && reasonAvailable.value
  && commentValid.value

function selectedCountLabel(count: number): string {
  if (count % 10 === 1 && count % 100 !== 11) return count + ' пара'
  if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) return count + ' пары'
  return count + ' пар'
}

function updateLessonIds(value: string[] | string | null): void {
  const next = Array.isArray(value) ? [...value] : value ? [value] : []
  emit('update:lessonIds', next)
}

function updateFiles(files: RequestFileRef[] | readonly RequestFileRef[]): void {
  emit('update:files', [...files])
}

function onSubmit(): void {
  if (!canSubmit() || !props.reason) return
  emit('submit', {
    lessonIds: [...props.lessonIds],
    reason: props.reason,
    comment: props.comment,
    files: [...props.files],
  })
}
</script>

<template>
  <main
    class="requests-form-screen requests-excuse-screen"
    :class="{ 'requests-form-screen--today': props.todayLesson }"
    aria-labelledby="excuse-request-title"
  >
    <header class="requests-form-header">
      <button
        class="requests-back-button"
        type="button"
        :aria-label="props.todayLesson ? 'Вернуться на Сегодня' : 'Вернуться к выбору типа заявки'"
        @click="emit('back')"
      >
        <MobileIcon
          v-if="props.todayLesson"
          name="back"
        />
        <svg
          v-else
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
      <h1 id="excuse-request-title">
        Уважительная причина
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
          v-if="!props.todayLesson"
          class="requests-form__kicker"
        >
          Пары
        </p>
        <p
          v-if="!props.todayLesson"
          class="requests-form__selection-count"
          role="status"
          aria-live="polite"
        >
          {{ selectedCountLabel(props.lessonIds.length) }}
        </p>
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
        <StudentLessonCard
          v-if="props.todayLesson"
          :lesson="props.todayLesson"
          :heading-level="2"
        />
        <p
          v-if="todayUnavailable"
          class="request-validation-hint"
          role="status"
        >
          {{ todayOption?.unavailableReason || 'Эта пара больше недоступна для заявки. Вернись на Сегодня и выбери доступную пару.' }}
        </p>
        <RequestLessonSelector
          v-if="!props.todayLesson && !props.lessonsLoading"
          mode="excuse"
          :options="props.lessons"
          :model-value="props.lessonIds"
          :disabled="props.disabled || Boolean(props.lessonsError)"
          @update:model-value="updateLessonIds"
        />
      </section>

      <label
        class="request-field"
        for="request-excuse-reason"
      >
        <span class="request-field__label">Причина</span>
        <span class="request-select-control">
          <select
            id="request-excuse-reason"
            :value="props.reason || ''"
            :disabled="props.disabled || props.reasons.length === 0"
            aria-describedby="request-excuse-reason-help"
            @change="emit('update:reason', ($event.target as HTMLSelectElement).value || null)"
          >
            <option value="">Выбери причину</option>
            <option
              v-for="option in props.reasons"
              :key="option.code"
              :value="option.code"
            >
              {{ option.label }}
            </option>
          </select>
          <MobileIcon
            v-if="props.todayLesson"
            name="chevron-down"
            class="request-select-icon"
          />
          <svg
            v-else
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
        id="request-excuse-reason-help"
        class="request-field__help"
        :class="{ 'request-visually-hidden': props.todayLesson && props.reasons.length > 0 }"
      >
        {{ props.reasons.length === 0 ? 'Причины пока недоступны.' : 'Причина нужна для отправки заявки.' }}
      </p>
      <p
        v-if="retainedReasonUnavailable"
        class="request-validation-hint"
      >
        Выбранная причина больше недоступна. Выбери доступную причину.
      </p>

      <label
        class="request-field"
        for="request-excuse-comment"
      >
        <span class="request-field__label">Комментарий</span>
        <textarea
          id="request-excuse-comment"
          :value="props.comment"
          :disabled="props.disabled"
          aria-describedby="request-excuse-comment-help"
          :aria-invalid="commentTooLong || (commentRequired && !props.comment.trim()) || undefined"
          maxlength="1000"
          rows="3"
          placeholder="Коротко опиши ситуацию"
          @input="emit('update:comment', ($event.target as HTMLTextAreaElement).value)"
        />
      </label>
      <p
        id="request-excuse-comment-help"
        class="request-field__help"
        :class="{ 'request-visually-hidden': props.todayLesson }"
      >
        {{ commentRequired ? 'Для этой причины нужен комментарий.' : 'Комментарий можно добавить при необходимости.' }} Не более 1000 символов.
      </p>
      <p
        v-if="commentTooLong"
        class="request-validation-hint"
        role="alert"
      >
        Сократи комментарий до 1000 символов, чтобы отправить заявку.
      </p>

      <div class="request-field">
        <span class="request-field__label">Подтверждение</span>
        <RequestAttachmentField
          :model-value="props.files"
          :limits="props.fileLimits"
          :disabled="props.disabled || props.submitting"
          :compact="Boolean(props.todayLesson)"
          @update:model-value="updateFiles"
        />
      </div>

      <p
        v-if="props.lessonIds.length === 0 || !props.reason"
        class="request-validation-hint"
      >
        Выбери хотя бы одну пару и причину, чтобы отправить заявку.
      </p>
      <p
        v-if="commentRequired && !props.comment.trim()"
        class="request-validation-hint"
      >
        Добавь комментарий, чтобы отправить заявку.
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
        :disabled="!canSubmit()"
      >
        {{ props.submitting ? 'Отправляем…' : 'Отправить заявку' }}
      </button>
    </form>
  </main>
</template>

<style src="./requests.pcss"></style>
<style src="./requests-today.pcss"></style>

