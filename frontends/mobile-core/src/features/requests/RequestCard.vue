<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import {
  formatDecisionDate,
  formatLessonDate,
  formatLessonTime,
  formatRequestDate,
  lessonTypeLabel,
  requestKindLabel,
  requestStatusLabel,
  requestStatusTone,
  requestTime,
} from './state'
import type { RequestAttachment, RequestAttachmentViewState, RequestDetail, RequestLesson } from './types'

const props = withDefaults(defineProps<{
  detail: RequestDetail
  offline: boolean
  cancelling?: boolean
  attachmentStates?: Readonly<Record<string, RequestAttachmentViewState | undefined>> | undefined
  focusTarget?: boolean
}>(), {
  cancelling: false,
  attachmentStates: undefined,
  focusTarget: false,
})

const emit = defineEmits<{
  cancel: [id: string]
  openAttachment: [value: { requestId: string; attachment: RequestAttachment }]
}>()

const summary = computed(() => props.detail.summary)
const lessons = computed(() => summary.value.lessons ?? [])
const attachments = computed(() => props.detail.attachments ?? [])
const showCancel = computed(() => summary.value.canCancel === true)
const detailUnavailable = computed(() => props.detail.detailState !== undefined && props.detail.detailState !== 'ready')
const statusTone = computed(() => requestStatusTone(summary.value.status))
const lessonsExpanded = ref(true)
const lessonsRegionId = computed(() => 'request-lessons-' + summary.value.id)
const titleElement = ref<HTMLHeadingElement | null>(null)
const decision = computed(() => props.detail.decision ?? null)
const decisionComment = computed(() => props.detail.decision?.comment?.trim() || null)
const decisionDate = computed(() => {
  const value = decision.value?.decidedAt?.trim()
  return value || null
})
const decisionLabel = computed(() => {
  if (!decision.value) return null

  const parsedDate = decisionDate.value ? new Date(decisionDate.value) : null
  const dateLabel = parsedDate && !Number.isNaN(parsedDate.getTime())
    ? formatDecisionDate(decisionDate.value)
    : null
  const values = [dateLabel, decisionComment.value].filter((value): value is string => Boolean(value))
  return values.length > 0 ? values.join(' · ') : null
})

function subjectName(lesson: RequestLesson): string {
  return lesson.subjectName || 'Предмет не указан'
}

function attachmentName(attachment: RequestAttachment): string {
  return attachment.name || 'Вложение без названия'
}

function attachmentIsExpired(attachment: RequestAttachment): boolean {
  return attachment.state === 'EXPIRED' || Boolean(attachment.expiredAt)
}

function attachmentStateKey(attachment: RequestAttachment): string {
  return summary.value.id + '\u0000' + attachment.id
}

function attachmentActionState(attachment: RequestAttachment): RequestAttachmentViewState | null {
  return props.attachmentStates?.[attachmentStateKey(attachment)] ?? null
}

function attachmentErrorId(attachment: RequestAttachment): string {
  return 'request-attachment-error-' + summary.value.id + '-' + attachment.id
}

watch(() => props.focusTarget, async (focus) => {
  if (!focus) return
  await nextTick()
  titleElement.value?.focus()
}, { immediate: true, flush: 'post' })
</script>

<template>
  <article
    class="request-card"
    :aria-labelledby="'request-title-' + summary.id"
  >
    <div class="request-card__head">
      <h2
        :id="'request-title-' + summary.id"
        ref="titleElement"
        tabindex="-1"
      >
        {{ requestKindLabel(summary.kind) }}
      </h2>
      <span
        class="request-status"
        :class="'request-status--' + statusTone"
      >
        {{ requestStatusLabel(summary.status) }}
      </span>
    </div>

    <div class="request-card__details-head">
      <div class="request-card__meta">
        <p>
          <MobileIcon name="calendar" />
          {{ formatRequestDate(summary.createdAt) }}
        </p>
        <p v-if="requestTime(summary.createdAt)">
          <MobileIcon name="clock" />
          {{ requestTime(summary.createdAt) }}
        </p>
      </div>
      <button
        v-if="lessons.length > 0"
        class="request-card__toggle"
        type="button"
        :aria-expanded="lessonsExpanded"
        :aria-controls="lessonsRegionId"
        :aria-label="lessonsExpanded ? 'Свернуть пары в заявке' : 'Развернуть пары в заявке'"
        @click="lessonsExpanded = !lessonsExpanded"
      >
        <MobileIcon
          name="chevron-down"
          :class="{ 'request-card__toggle-icon--expanded': lessonsExpanded }"
        />
      </button>
    </div>

    <ul
      v-if="lessons.length > 0"
      v-show="lessonsExpanded"
      :id="lessonsRegionId"
      class="request-card__lessons"
      aria-label="Пары в заявке"
    >
      <li
        v-for="lesson in lessons"
        :key="lesson.id"
        class="request-lesson-card"
      >
        <h3>{{ subjectName(lesson) }}</h3>
        <p>
          <MobileIcon name="calendar" />
          {{ formatLessonDate(lesson.date) }}
        </p>
        <p>
          <MobileIcon name="clock" />
          {{ formatLessonTime(lesson) }}
        </p>
        <p>
          <MobileIcon name="lesson" />
          {{ lessonTypeLabel(lesson.subjectType) }}
        </p>
      </li>
    </ul>
    <p
      v-else
      class="request-card__missing"
    >
      Данные о парах не указаны.
    </p>

    <p
      v-if="detailUnavailable"
      class="request-card__missing"
      role="status"
    >
      Детали заявки недоступны{{ props.detail.detailError ? ': ' + props.detail.detailError : '.' }}
    </p>

    <p
      v-if="!detailUnavailable && decisionLabel"
      class="request-card__decision"
      role="status"
    >
      {{ decisionLabel }}
    </p>

    <ul
      v-if="!detailUnavailable && attachments.length > 0"
      class="request-card__attachments"
      aria-label="Вложения"
    >
      <li
        v-for="attachment in attachments"
        :key="attachment.id"
      >
        <span class="request-card__attachment-name">{{ attachmentName(attachment) }}</span>
        <span
          v-if="attachmentIsExpired(attachment)"
          class="request-card__attachment-state"
        >Срок истёк</span>
        <span
          v-else-if="attachment.state !== 'ACTIVE'"
          class="request-card__attachment-state"
        >Недоступно</span>
        <span
          v-else
          class="request-card__attachment-actions"
        >
          <button
            class="request-card__attachment-action"
            type="button"
            :disabled="offline || attachmentActionState(attachment)?.status === 'pending'"
            :aria-busy="attachmentActionState(attachment)?.status === 'pending' ? 'true' : undefined"
            :aria-describedby="attachmentActionState(attachment)?.status === 'error' ? attachmentErrorId(attachment) : undefined"
            :aria-label="(attachmentActionState(attachment)?.status === 'error' ? 'Повторить открытие ' : 'Открыть ') + attachmentName(attachment)"
            @click="emit('openAttachment', { requestId: summary.id, attachment })"
          >
            {{ attachmentActionState(attachment)?.status === 'pending' ? 'Открываем…' : attachmentActionState(attachment)?.status === 'error' ? 'Повторить' : 'Открыть' }}
          </button>
          <span
            v-if="attachmentActionState(attachment)?.status === 'error'"
            :id="attachmentErrorId(attachment)"
            class="request-card__attachment-error"
            role="alert"
          >
            {{ attachmentActionState(attachment)?.error || 'Не удалось открыть вложение.' }}
          </span>
        </span>
      </li>
    </ul>

    <button
      v-if="showCancel"
      class="requests-danger-action"
      type="button"
      :disabled="offline || cancelling"
      @click="emit('cancel', summary.id)"
    >
      {{ cancelling ? 'Отменяем…' : 'Отменить заявку' }}
    </button>
  </article>
</template>

<style src="./requests.pcss"></style>
