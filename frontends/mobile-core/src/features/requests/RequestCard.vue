<script setup lang="ts">
import { computed } from 'vue'
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
import type { RequestAttachment, RequestDetail, RequestLesson } from './types'

const props = withDefaults(defineProps<{
  detail: RequestDetail
  offline: boolean
  cancelling?: boolean
}>(), {
  cancelling: false,
})

const emit = defineEmits<{
  cancel: [id: string]
  openAttachment: [attachment: RequestAttachment]
}>()

const summary = computed(() => props.detail.summary)
const lessons = computed(() => summary.value.lessons ?? [])
const attachments = computed(() => props.detail.attachments ?? [])
const showCancel = computed(() => summary.value.canCancel === true)
const statusTone = computed(() => requestStatusTone(summary.value.status))
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
</script>

<template>
  <article
    class="request-card"
    :aria-labelledby="'request-title-' + summary.id"
  >
    <div class="request-card__head">
      <h2 :id="'request-title-' + summary.id">
        {{ requestKindLabel(summary.kind) }}
      </h2>
      <span
        class="request-status"
        :class="'request-status--' + statusTone"
      >
        <span aria-hidden="true">{{ summary.status === 'APPROVED' ? '✓' : summary.status === 'REJECTED' ? '!' : summary.status === 'CANCELLED' ? '×' : '…' }}</span>
        {{ requestStatusLabel(summary.status) }}
      </span>
    </div>

    <div class="request-card__meta">
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
        {{ formatRequestDate(summary.createdAt) }}
      </p>
      <p v-if="requestTime(summary.createdAt)">
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
        {{ requestTime(summary.createdAt) }}
      </p>
    </div>

    <ul
      v-if="lessons.length > 0"
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
          {{ formatLessonDate(lesson.date) }}
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
          {{ formatLessonTime(lesson) }}
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
      v-if="decisionLabel"
      class="request-card__decision"
      role="status"
    >
      {{ decisionLabel }}
    </p>

    <ul
      v-if="attachments.length > 0"
      class="request-card__attachments"
      aria-label="Вложения"
    >
      <li
        v-for="attachment in attachments"
        :key="attachment.id"
      >
        <span>{{ attachmentName(attachment) }}</span>
        <span
          v-if="attachmentIsExpired(attachment)"
          class="request-card__attachment-state"
        >Срок истёк</span>
        <button
          v-else-if="attachment.state === 'ACTIVE'"
          class="request-card__attachment-action"
          type="button"
          :disabled="offline"
          :aria-label="'Открыть ' + attachmentName(attachment)"
          @click="emit('openAttachment', attachment)"
        >
          Открыть
        </button>
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
