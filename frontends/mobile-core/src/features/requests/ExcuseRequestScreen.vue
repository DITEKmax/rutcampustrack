<script setup lang="ts">
import { computed } from 'vue'
import { canRequestLesson, requestSelectionEligible } from './state'
import type { TodayLesson } from '../../api/types'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import RequestLessonSummary from './RequestLessonSummary.vue'
import AutoGrowTextarea from './AutoGrowTextarea.vue'
import ReasonSelect from './ReasonSelect.vue'
import RequestAttachmentField from './RequestAttachmentField.vue'
import RequestLessonSelector from './RequestLessonSelector.vue'
import type {
  ExcuseRequestPayload,
  RequestAccessState,
  RequestFileLimits,
  RequestFileRef,
  RequestLesson,
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
  readOnly?: boolean
  submitting?: boolean
  submitError?: string | null
  disabled?: boolean
  ambiguous?: boolean
  todayLesson?: TodayLesson | null
  selectedLesson?: RequestLesson | null
  attendanceStatus?: 'PRESENT' | 'ABSENT' | 'EXCUSED' | null
  backLabel?: string
  selectionComplete?: boolean
}>(), {
  lessonsLoading: false,
  lessonsError: null,
  offline: false,
  readOnly: false,
  submitting: false,
  submitError: null,
  disabled: false,
  ambiguous: false,
  todayLesson: null,
  selectedLesson: null,
  attendanceStatus: null,
  backLabel: 'Вернуться назад',
  selectionComplete: false,
})

const emit = defineEmits<{
  back: []
  editSelection: []
  retryLessons: []
  'update:lessonIds': [lessonIds: string[]]
  'update:reason': [reason: string | null]
  'update:comment': [comment: string]
  'update:files': [files: RequestFileRef[]]
  abandon: []
  submit: [payload: ExcuseRequestPayload]
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

const selectedEligible = computed(() => requestSelectionEligible(props.lessons, props.lessonIds, 'EXCUSE'))
const selectedReason = computed(() => props.reasons.find((option) => option.code === props.reason))
const reasonAvailable = computed(() => Boolean(props.reason) && selectedReason.value !== undefined)
const retainedReasonUnavailable = computed(() => Boolean(props.reason) && props.reasons.length > 0 && !reasonAvailable.value)
const commentRequired = computed(() => selectedReason.value?.commentRequired === true)
const commentTooLong = computed(() => props.comment.length > 1000)
const commentValid = computed(() => !commentTooLong.value && (!commentRequired.value || props.comment.trim().length > 0))
const todaySelectionMatches = computed(() => !contextLesson.value || (props.lessonIds.length === 1 && props.lessonIds[0] === contextLesson.value.id))
const todayOption = computed(() => props.lessons.find((option) => option.lesson?.id === contextLesson.value?.id))
const todayUnavailable = computed(() => contextLesson.value && !props.lessonsLoading && !props.lessonsError && (!todaySelectionMatches.value || !canRequestLesson(todayOption.value, 'EXCUSE')))
const selectedLessons = computed(() => props.lessons.flatMap((option) => option.lesson && props.lessonIds.includes(option.lesson.id) ? [option.lesson] : []))
const canSubmit = () => props.access === 'allowed'
  && !props.offline
  && !props.disabled
  && !props.readOnly
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
    aria-labelledby="excuse-request-title"
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
      <h1 id="excuse-request-title">
        {{ props.selectionComplete ? 'Уважительная причина' : 'Подтверждение пропуска' }}
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
      v-if="props.readOnly && !props.offline && props.access === 'allowed'"
      title="Только чтение"
      message="Подача заявок отключена. Твой черновик сохранён."
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
      <section
        class="requests-form__section"
        :class="{ 'requests-selected-summary': props.selectionComplete && !contextLesson }"
      >
        <p
          v-if="!contextLesson && !props.selectionComplete"
          class="requests-form__kicker"
        >
          Пары
        </p>
        <p
          v-if="!contextLesson"
          class="requests-form__selection-count"
          role="status"
          aria-live="polite"
        >
          {{ props.selectionComplete ? 'Выбрано ' : '' }}{{ selectedCountLabel(props.lessonIds.length) }}
        </p>
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
          v-if="todayUnavailable"
          class="request-validation-hint"
          role="alert"
        >
          {{ todayOption?.unavailableReason || 'Эта пара больше недоступна для заявки. Вернись назад и выбери доступную пару.' }}
        </p>
        <RequestLessonSelector
          v-if="!contextLesson && !props.selectionComplete && !props.lessonsLoading"
          mode="excuse"
          :options="props.lessons"
          :model-value="props.lessonIds"
          :disabled="props.disabled || Boolean(props.lessonsError)"
          @update:model-value="updateLessonIds"
        />
        <template v-if="!contextLesson">
          <RequestLessonSummary
            v-for="lesson in selectedLessons"
            :key="lesson.id"
            :lesson="lesson"
          />
        </template>
        <button
          v-if="props.selectionComplete && !contextLesson"
          class="requests-inline-action requests-edit-selection"
          type="button"
          :disabled="props.submitting"
          @click="emit('editSelection')"
        >
          Изменить пары
        </button>
        <p
          v-if="props.selectionComplete && !selectedEligible"
          class="request-validation-hint"
          role="alert"
        >
          Выбор больше недоступен. Измени пары перед отправкой.
        </p>
      </section>

      <div class="request-field">
        <label
          class="request-field__label"
          for="request-excuse-reason"
        >Причина</label>
        <ReasonSelect
          id="request-excuse-reason"
          :model-value="props.reason"
          :options="props.reasons"
          :disabled="props.disabled || props.submitting || props.reasons.length === 0"
          :invalid="!reasonAvailable"
          describedby="request-excuse-reason-help request-excuse-reason-error"
          @update:model-value="emit('update:reason', $event)"
        />
      </div>
      <p
        id="request-excuse-reason-help"
        class="request-field__help"
        :class="{ 'request-field__help--error': props.reasons.length === 0, 'request-visually-hidden': props.reasons.length > 0 }"
      >
        {{ props.reasons.length === 0 ? 'Причины пока недоступны.' : 'Причина нужна для отправки заявки.' }}
      </p>
      <p
        v-if="retainedReasonUnavailable"
        id="request-excuse-reason-error"
        role="alert"
        class="request-validation-hint"
      >
        Выбранная причина больше недоступна. Выбери доступную причину.
      </p>

      <label
        class="request-field"
        for="request-excuse-comment"
      >
        <span class="request-field__label">Комментарий</span>
        <AutoGrowTextarea
          id="request-excuse-comment"
          :model-value="props.comment"
          :disabled="props.disabled"
          aria-describedby="request-excuse-comment-help"
          :aria-invalid="commentTooLong || (commentRequired && !props.comment.trim()) || undefined"
          maxlength="1000"
          placeholder="Коротко опиши ситуацию"
          @update:model-value="emit('update:comment', $event)"
        />
      </label>
      <p
        id="request-excuse-comment-help"
        class="request-field__help"
        :class="{ 'request-visually-hidden': !commentRequired }"
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
          :compact="true"
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
      <StudentWarningBlock
        v-if="props.submitError"
        severity="error"
        title="Не удалось отправить заявку"
        :message="props.submitError"
      />
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

