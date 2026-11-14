<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import LocalScenarioPanel from './LocalScenarioPanel.vue'

const stage = ref(0)
const status = ref<HTMLElement | null>(null)
const occurrenceId = '513'
const source = { id: '811', date: '2026-11-19', number: 2 }
const target = { id: '812', date: '2026-11-20', number: 3 }
const started = computed(() => stage.value > 0)
const attendanceApplied = computed(() => stage.value >= 2)
const academicApplied = computed(() => Math.max(0, stage.value - 2))
const complete = computed(() => stage.value === 4)
const operation = computed(() => started.value ? {
  operationId: '10000000-0000-4000-8000-000000000411',
  state: complete.value ? 'COMPLETED' : 'PENDING',
  occurrenceId,
  sourceLessonId: source.id,
  targetLessonId: target.id,
  revision: '5',
  retryable: !complete.value,
  errorCode: null,
  targetDate: target.date,
} : null)
const steps = [
  'Перенести на 20 ноября',
  'Применить в Attendance',
  'Применить пакет Academic 1',
  'Применить пакет Academic 2',
] as const
const markLessonId = computed(() => attendanceApplied.value ? target.id : source.id)
const homeworkLessonId = computed(() => academicApplied.value ? target.id : source.id)
const state = computed(() => ({
  operation: operation.value,
  occurrence: { id: occurrenceId, currentLessonId: started.value ? target.id : source.id },
  lessons: [
    { ...source, status: started.value ? 'transferred' : 'planned' },
    ...(started.value ? [{ ...target, status: 'planned' }] : []),
  ],
  links: {
    mark: { lessonId: markLessonId.value, status: 'PRESENT', revision: attendanceApplied.value ? '2' : '1' },
    pendingRequest: { lessonId: markLessonId.value, status: 'PENDING', originalLessonId: source.id },
    homework: { id: '902', lessonId: homeworkLessonId.value },
  },
  receipts: {
    Attendance: attendanceApplied.value ? 'APPLIED' : null,
    Academic: Array.from({ length: academicApplied.value }, (_, batchIndex) => ({ batchIndex, result: 'APPLIED' })),
  },
  outbox: complete.value ? ['lesson.transferred'] : [],
}))
async function advance() {
  if (complete.value) return
  stage.value++
  if (complete.value) {
    await nextTick()
    status.value?.focus({ preventScroll: true })
  }
}
</script>

<template>
  <LocalScenarioPanel id="demo-lesson-transfer" title="Перенести пару" :json="state"
    :changed="started" @reset="stage = 0">
    <p>Одна логическая пара {{ occurrenceId }}. В исходной записи остаётся история переноса.</p>
    <dl class="local-scene-facts">
      <div><dt>19 ноября · №2</dt><dd>Пара {{ source.id }} · {{ started ? 'перенесена' : 'запланирована' }}</dd></div>
      <div><dt>20 ноября · №3</dt><dd>{{ started ? 'Пара ' + target.id + ' · запланирована' : 'Свободный слот' }}</dd></div>
    </dl>
    <p>Отметка и ожидающая заявка → {{ markLessonId }}. ДЗ → {{ homeworkLessonId }}.</p>
    <div class="local-scene-controls">
      <button type="button" :disabled="complete" @click="advance">{{ complete ? 'Перенос завершён' : steps[stage] }}</button>
    </div>
    <p ref="status" role="status" tabindex="-1">
      {{ operation?.state ?? 'Готово к переносу' }} · Attendance {{ attendanceApplied ? '1/1' : '0/1' }} · Academic {{ academicApplied }}/2
      <template v-if="complete"> · lesson.transferred в outbox</template>
    </p>
  </LocalScenarioPanel>
</template>
