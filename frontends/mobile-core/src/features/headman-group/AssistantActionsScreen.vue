<script setup lang="ts">
import { computed, ref } from 'vue'
import AssistantHomeworkScreen from '../homework/AssistantHomeworkScreen.vue'
import type { HeadmanHomeworkApi } from '../homework/headman-homework-client'
import HeadmanJournalScreen from '../headman-journal/HeadmanJournalScreen.vue'
import type { HeadmanJournalApi } from '../headman-journal/headman-journal-client'
import HeadmanRequestsScreen from '../headman-requests/HeadmanRequestsScreen.vue'
import type { HeadmanRequestsApi } from '../headman-requests/headman-requests-client'
import type { HeadmanAssistantPermission } from './headman-group-client'
import './assistant-actions-screen.pcss'

const props = withDefaults(defineProps<{
  permissions: readonly HeadmanAssistantPermission[]
  groupId: number | null
  journalApi: HeadmanJournalApi | null
  requestsApi: HeadmanRequestsApi | null
  homeworkApi: HeadmanHomeworkApi | null
  userId: number | null
  offline?: boolean
  readOnly?: boolean
}>(), {
  offline: false,
  readOnly: false,
})

const emit = defineEmits<{
  error: [cause: unknown]
}>()

const surface = ref<'home' | 'journal' | 'requests' | 'homework'>('home')
const canJournal = computed(() => props.permissions.includes('MARK_ATTENDANCE')
  || props.permissions.includes('VIEW_STATS')
  || props.permissions.includes('CANCEL_LESSONS'))
const canRequests = computed(() => props.permissions.includes('MANAGE_EXCUSES'))
const canHomework = computed(() => props.permissions.includes('MANAGE_HOMEWORK'))

function openJournal(): void {
  if (canJournal.value && props.groupId !== null && props.journalApi) surface.value = 'journal'
}

function openRequests(): void {
  if (canRequests.value && props.requestsApi) surface.value = 'requests'
}

function openHomework(): void {
  if (canHomework.value && props.homeworkApi && props.journalApi && props.groupId !== null) surface.value = 'homework'
}

function reportError(cause: unknown): void {
  emit('error', cause)
}
</script>

<template>
  <main
    v-if="surface === 'home'"
    class="assistant-actions"
    aria-labelledby="assistant-actions-title"
  >
    <header class="assistant-actions__header">
      <p class="assistant-actions__eyebrow">Помощник группы</p>
      <h1 id="assistant-actions-title">Доступные действия</h1>
      <p class="assistant-actions__hint">Права проверяются сервером для каждой операции.</p>
    </header>

    <p v-if="offline" class="assistant-actions__state" role="status">
      Действия доступны только онлайн.
    </p>
    <p v-if="permissions.length === 0" class="assistant-actions__state">
      У тебя нет активных прав помощника.
    </p>
    <div v-else class="assistant-actions__list">
      <button
        v-if="canJournal"
        class="assistant-actions__button"
        type="button"
        :disabled="offline || !journalApi || groupId === null"
        @click="openJournal"
      >
        Посещаемость и журнал
      </button>
      <button
        v-if="canRequests"
        class="assistant-actions__button"
        type="button"
        :disabled="offline || !requestsApi"
        @click="openRequests"
      >
        Уважительные причины и заявки
      </button>
      <button
        v-if="canHomework"
        class="assistant-actions__button"
        type="button"
        :disabled="offline || !homeworkApi || !journalApi || groupId === null"
        @click="openHomework"
      >
        Домашние задания
      </button>
    </div>
  </main>

  <HeadmanJournalScreen
    v-else-if="surface === 'journal'"
    :api="journalApi"
    :group-id="groupId"
    :assistant-permissions="permissions"
    :offline="offline"
    :read-only="readOnly"
    @error="reportError"
  />
  <HeadmanRequestsScreen
    v-else-if="surface === 'requests'"
    :api="requestsApi"
    :assistant-permissions="permissions"
    :offline="offline"
    :read-only="readOnly"
    @back="surface = 'home'"
    @error="reportError"
  />
  <AssistantHomeworkScreen
    v-else-if="surface === 'homework'"
    :api="homeworkApi"
    :journal-api="journalApi"
    :group-id="groupId"
    :user-id="userId"
    :offline="offline"
    :read-only="readOnly"
    @error="reportError"
  />
</template>
