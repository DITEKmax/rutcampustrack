<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import TeacherLessonCard from './TeacherLessonCard.vue'
import TeacherRoster from './TeacherRoster.vue'
import TeacherLessonExportControl from './TeacherLessonExportControl.vue'
import type { ReportDownloadPort } from '../../shared/report-download-client'
import { TeacherApiError, type TeacherApi, type TeacherLessonResponse } from './teacher-client'
import './teacher-screen.pcss'
const props = withDefaults(defineProps<{ api: TeacherApi | null; lessonId: number | null; showBack?: boolean; reportDownload?: ReportDownloadPort | null; exportOpenSignal?: number; exportCloseSignal?: number }>(), { showBack: true, reportDownload: null, exportOpenSignal: 0, exportCloseSignal: 0 })
const emit = defineEmits<{ back: []; 'open-excuse': [requestId: string]; 'open-manual-excuse': [lessonId: number, studentId: number]; 'open-export': [lessonId: number]; 'export-open': [open: boolean]; error: [cause: unknown] }>()
const state = ref<TeacherLessonResponse | null>(null); const loading = ref(false); const error = ref<string | null>(null)
const downloadButton = ref<HTMLButtonElement | null>(null)
let revision = 0
const rows = computed(() => state.value?.roster.map(entry => ({ studentId: entry.studentId,displayName: entry.displayName,attendance: entry })) ?? [])
watch(() => [props.api,props.lessonId] as const, () => { void load() }, { immediate: true })
onBeforeUnmount(() => { revision += 1 })
async function load(): Promise<void> {
  const api = props.api; const id = props.lessonId; const run = ++revision
  state.value = null; error.value = null; loading.value = false
  if (!api || id === null) return
  loading.value = true
  try { const value = await api.lesson(id); if (run === revision) state.value = value }
  catch (cause) { if (run !== revision || cause instanceof StaleSessionGenerationError) return; error.value = cause instanceof TeacherApiError ? cause.message : 'Не удалось открыть пару.'; emit('error',cause) }
  finally { if (run === revision) loading.value = false }
}
</script>
<template>
  <main
    class="teacher-screen"
    aria-labelledby="teacher-lesson-title"
    :aria-busy="loading"
  >
    <header class="teacher-screen__detail-header">
      <button
        v-if="showBack"
        class="teacher-screen__icon-button"
        type="button"
        aria-label="Назад"
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button><h1 id="teacher-lesson-title">
        Пара
      </h1>
      <div
        v-if="state"
        class="teacher-lesson-detail__download"
      >
        <button
          ref="downloadButton"
          class="teacher-screen__icon-button"
          type="button"
          aria-label="Скачать посещаемость этой пары"
          @click="emit('open-export', state.lesson.id)"
        >
          <MobileIcon name="download" />
        </button>
        <TeacherLessonExportControl
          :api="api"
          :lesson="state.lesson"
          :report-download="reportDownload"
          :open-signal="exportOpenSignal"
          :close-signal="exportCloseSignal"
          :anchor="downloadButton"
          @open="emit('export-open', $event)"
          @error="emit('error', $event)"
        />
      </div>
    </header>
    <p
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем посещаемость…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      title="Пара недоступна"
      :message="error"
      severity="error"
      action-label="Повторить"
      @action="load"
    />
    <template v-else-if="state">
      <TeacherLessonCard
        :lesson="state.lesson"
        show-date
        :interactive="false"
        show-status
      /><h2 class="teacher-screen__section-title">
        Посещаемость
      </h2><TeacherRoster
        :rows="rows"
        @open-excuse="emit('open-excuse',$event)"
        @open-manual-excuse="emit('open-manual-excuse', state.lesson.id, $event)"
      />
    </template>
    <p
      v-else
      class="teacher-screen__state"
      role="status"
    >
      Выбери пару.
    </p>
  </main>
</template>
