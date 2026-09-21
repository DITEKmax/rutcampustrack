<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import {
  TeacherApiError,
  type TeacherApi,
  type TeacherExcuseAttachment,
  type TeacherExcuseResponse,
} from './teacher-client'
import './teacher-screen.pcss'

const props = defineProps<{
  api: TeacherApi | null
  requestId: string | null
}>()

const emit = defineEmits<{
  back: []
  error: [cause: unknown]
}>()

const state = ref<TeacherExcuseResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const pendingAttachment = ref<string | null>(null)
const objectUrls = new Set<string>()
let revision = 0

watch(
  () => [props.api, props.requestId] as const,
  () => { void load() },
  { immediate: true },
)

onBeforeUnmount(() => {
  revision += 1
  for (const url of objectUrls) URL.revokeObjectURL(url)
  objectUrls.clear()
})

async function load(): Promise<void> {
  const api = props.api
  const requestId = props.requestId
  const current = ++revision
  if (!api || !requestId) {
    state.value = null
    return
  }
  loading.value = true
  error.value = null
  try {
    const value = await api.excuse(requestId)
    if (current !== revision) return
    state.value = value
  } catch (cause) {
    if (current !== revision) return
    error.value = cause instanceof TeacherApiError ? cause.message : 'Не удалось открыть заявление.'
    emit('error', cause)
  } finally {
    if (current === revision) loading.value = false
  }
}

async function download(attachment: TeacherExcuseAttachment): Promise<void> {
  const api = props.api
  const requestId = props.requestId
  if (!api || !requestId) return
  pendingAttachment.value = attachment.id
  try {
    const blob = await api.downloadAttachment(requestId, attachment.id)
    const url = URL.createObjectURL(blob)
    objectUrls.add(url)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = attachment.fileName
    anchor.click()
    window.setTimeout(() => {
      objectUrls.delete(url)
      URL.revokeObjectURL(url)
    }, 60_000)
  } catch (cause) {
    error.value = cause instanceof TeacherApiError ? cause.message : 'Не удалось открыть вложение.'
    emit('error', cause)
  } finally {
    pendingAttachment.value = null
  }
}

function formatInstant(value: string | null): string {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' }).format(parsed)
}
</script>

<template>
  <main
    class="teacher-screen"
    aria-labelledby="teacher-excuse-title"
    :aria-busy="loading"
  >
    <button
      class="teacher-screen__back"
      type="button"
      @click="emit('back')"
    >
      ← Назад
    </button>
    <section
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем заявление…
    </section>
    <section
      v-else-if="error"
      class="teacher-screen__state teacher-screen__state--error"
      role="alert"
    >
      {{ error }}
    </section>
    <template v-else-if="state">
      <header class="teacher-screen__header">
        <div>
          <p class="teacher-screen__muted">
            Заявление {{ state.kind.toLowerCase() }}
          </p>
          <h1 id="teacher-excuse-title">
            {{ state.studentName }}
          </h1>
          <p class="teacher-screen__meta">
            {{ state.status }} · создано {{ formatInstant(state.createdAt) }}
          </p>
        </div>
      </header>
      <article class="teacher-screen__ticket">
        <section class="teacher-screen__ticket-section">
          <span class="teacher-screen__ticket-label">Причина</span>
          <p>{{ state.excuseType ?? 'не указана' }}</p>
          <p
            v-if="state.reason"
            class="teacher-screen__muted"
          >
            {{ state.reason }}
          </p>
          <p v-if="state.comment">
            {{ state.comment }}
          </p>
        </section>
        <section class="teacher-screen__ticket-section">
          <span class="teacher-screen__ticket-label">Решение старосты</span>
          <p v-if="state.decisionAt">
            {{ formatInstant(state.decisionAt) }}<span v-if="state.decisionBy"> · пользователь #{{ state.decisionBy }}</span>
          </p>
          <p
            v-else
            class="teacher-screen__muted"
          >
            Решение ещё не принято.
          </p>
          <p v-if="state.decisionComment">
            {{ state.decisionComment }}
          </p>
        </section>
        <section
          class="teacher-screen__ticket-section"
          aria-labelledby="teacher-excuse-lessons-title"
        >
          <span
            id="teacher-excuse-lessons-title"
            class="teacher-screen__ticket-label"
          >Пары заявления</span>
          <ul class="teacher-screen__list">
            <li
              v-for="lesson in state.lessons"
              :key="lesson.id"
              class="teacher-screen__lesson-meta"
            >
              <span>{{ lesson.date }} · {{ lesson.lessonType }}</span>
              <span>{{ lesson.groupName }} · {{ lesson.subjectName }}</span>
            </li>
          </ul>
        </section>
        <section
          v-if="state.attachments.length > 0"
          class="teacher-screen__ticket-section"
          aria-labelledby="teacher-excuse-files-title"
        >
          <span
            id="teacher-excuse-files-title"
            class="teacher-screen__ticket-label"
          >Вложения</span>
          <ul class="teacher-screen__list">
            <li
              v-for="attachment in state.attachments"
              :key="attachment.id"
              class="teacher-screen__attachment"
            >
              <span>{{ attachment.fileName }}<small class="teacher-screen__cell-note">{{ Math.ceil(attachment.size / 1024) }} КБ</small></span>
              <button
                class="teacher-screen__secondary"
                type="button"
                :disabled="pendingAttachment === attachment.id"
                @click="download(attachment)"
              >
                {{ pendingAttachment === attachment.id ? 'Открываем…' : 'Открыть' }}
              </button>
            </li>
          </ul>
        </section>
      </article>
    </template>
    <section
      v-else
      class="teacher-screen__state"
      role="status"
    >
      Выбери заявление из roster.
    </section>
  </main>
</template>
