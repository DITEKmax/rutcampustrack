<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import AttendanceScreen from '../features/attendance/AttendanceScreen.vue'
import type { AttendanceGraphRange, AttendanceLesson, AttendanceMode, AttendanceRequestKind } from '../features/attendance/attendance-view-model'
import ExcuseRequestScreen from '../features/requests/ExcuseRequestScreen.vue'
import LateCheckinRequestScreen from '../features/requests/LateCheckinRequestScreen.vue'
import type { RequestFileRef, RequestLesson } from '../features/requests/types'
import MobileShell from '../shared/components/MobileShell.vue'
import { createStudentNavigationItems } from '../shared/mobile-navigation-items'
import { nestedRoute, rootRoute } from '../shared/navigation'
import { createAttendanceReviewScenarios } from './attendance-fixtures'
import './attendance-review.pcss'

const query = new URLSearchParams(window.location.search)
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const scenarios = createAttendanceReviewScenarios()
const scenario = shallowRef(scenarios.find((item) => item.id === query.get('state')) ?? scenarios[0]!)
const catalog = query.get('catalog') === '1'
const date = ref('2026-09-01')
const mode = ref<AttendanceMode>('days')
const range = ref<AttendanceGraphRange>('days')
const subject = ref<string | null>(null)
const actions = ref<string | null>(null)
const requestKind = ref<AttendanceRequestKind | null>(null)
const requestLesson = shallowRef<RequestLesson | null>(null)
const reason = ref<string | null>(null)
const comment = ref('')
const files = ref<RequestFileRef[]>([])
const status = ref('')
const previousFont = document.documentElement.style.fontSize
const navItems = createStudentNavigationItems({ homeworkEnabled: true, attendanceEnabled: true, moreEnabled: true, profileEnabled: true })
const route = computed(() => requestKind.value ? nestedRoute('attendance', 'attendance/review-form', 'editor') : rootRoute('attendance'))
const lessons = computed(() => requestLesson.value ? [{ lesson: requestLesson.value, excuseEligible: true, lateCheckinEligible: true }] : [])
function openRequest(lesson: AttendanceLesson, kind: AttendanceRequestKind): void {
  requestLesson.value = { id: lesson.id, lessonNumber: 2, subjectId: lesson.subject.id, subjectName: lesson.subject.name, subjectType: lesson.type, date: lesson.date, startsAt: lesson.schedule.startsAt, endsAt: lesson.schedule.endsAt, semesterId: 'semester' }
  requestKind.value = kind
}
watch(scenario, (value) => {
  date.value = value.date ?? '2026-09-01'; mode.value = value.mode ?? 'days'; range.value = value.range ?? 'days'; subject.value = value.expandedSubjectId ?? null; actions.value = value.actionLessonId ?? null
  requestKind.value = null; requestLesson.value = null; reason.value = null; comment.value = ''; files.value = []; status.value = ''
  if (value.id === 'attendance-request-context' && value.state.status === 'ready') openRequest(value.state.data.days[0]!.lessons[1]!, 'EXCUSE')
  document.documentElement.style.fontSize = value.enlargedText ? '20px' : previousFont
  document.title = `${value.label} · ${shell.toUpperCase()} · визуальная симуляция`
}, { immediate: true })
onBeforeUnmount(() => { document.documentElement.style.fontSize = previousFont })
function href(id: string): string { return `?${new URLSearchParams({ state: id, shell })}` }
function retry(): void { scenario.value = scenarios[0]!; status.value = 'Визуальная симуляция: данные загружены. API не вызывается.' }
function sent(): void { requestKind.value = null; status.value = 'Визуальная симуляция: показан возврат к посещаемости. API не вызывается.' }
</script>
<template>
  <main
    v-if="catalog"
    class="attendance-review-catalog"
  >
    <h1>Посещаемость · {{ shell.toUpperCase() }}</h1><p>Визуальные симуляции. API не вызывается.</p><ul>
      <li
        v-for="item in scenarios"
        :key="item.id"
      >
        <a :href="href(item.id)">{{ item.label }}</a> · {{ item.id }}
      </li>
    </ul>
  </main>
  <MobileShell
    v-else
    custom-back
    :route="route"
    :nav-items="navItems"
    @navigate="status = 'Визуальная симуляция навигации'"
  >
    <template #back />
    <ExcuseRequestScreen
      v-if="requestKind === 'EXCUSE' && requestLesson"
      access="allowed"
      :lessons="lessons"
      :lesson-ids="[requestLesson.id]"
      :selected-lesson="requestLesson"
      attendance-status="ABSENT"
      back-label="Вернуться к посещаемости"
      :reasons="[{ code: 'synthetic-illness', label: 'По болезни', commentRequired: false }, { code: 'synthetic-other', label: 'Прочее', commentRequired: true }]"
      :reason="reason"
      :comment="comment"
      :files="files"
      :file-limits="{ maxFiles: 2, maxBytesPerFile: 10485760 }"
      @update:reason="reason = $event"
      @update:comment="comment = $event"
      @update:files="files = $event"
      @back="requestKind = null"
      @abandon="requestKind = null"
      @submit="sent"
    />
    <LateCheckinRequestScreen
      v-else-if="requestKind === 'LATE_CHECKIN' && requestLesson"
      access="allowed"
      :lessons="lessons"
      :lesson-id="requestLesson.id"
      :selected-lesson="requestLesson"
      attendance-status="ABSENT"
      back-label="Вернуться к посещаемости"
      :budget="{ limit: 3, used: 1, remaining: 2 }"
      @back="requestKind = null"
      @cancel="requestKind = null"
      @abandon="requestKind = null"
      @submit="sent"
    />
    <AttendanceScreen
      v-else
      :state="scenario.state"
      :selected-date="date"
      :mode="mode"
      :graph-range="range"
      :expanded-subject-id="subject"
      :action-lesson-id="actions"
      :terminal="scenario.terminal ?? false"
      @select-date="date = $event"
      @set-mode="mode = $event"
      @set-graph-range="range = $event"
      @toggle-subject="subject = subject === $event ? null : $event"
      @toggle-actions="actions = actions === $event ? null : $event"
      @open-request="(lesson, option) => openRequest(lesson, option.kind)"
      @retry="retry"
    />
    <p
      v-if="status"
      class="attendance-review-status"
      role="status"
    >
      {{ status }}
    </p>
  </MobileShell>
</template>
