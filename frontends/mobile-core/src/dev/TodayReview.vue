<script setup lang="ts">
import { computed, onBeforeUnmount, nextTick, ref, shallowRef, watch } from 'vue'
import type { TodayLesson } from '../api/types'
import TodayScreen from '../features/today/TodayScreen.vue'
import ExcuseRequestScreen from '../features/requests/ExcuseRequestScreen.vue'
import LateCheckinRequestScreen from '../features/requests/LateCheckinRequestScreen.vue'
import type { RequestFileRef, RequestKind } from '../features/requests/types'
import MobileShell from '../shared/components/MobileShell.vue'
import RoleSwitchDialog from '../shared/components/RoleSwitchDialog.vue'
import { type ProfileRole, type ProfileSnapshot } from '../features/profile/profile-types'
import type { MobileHostAdapter } from '../shared/host'
import { createStudentNavigationItems } from '../shared/mobile-navigation-items'
import { createMobileNavigationStack, nestedRoute, rootRoute, type MobileRootRouteId } from '../shared/navigation'
import { createRoleReviewSnapshot, createTodayReviewScenarios, selectedTodayLesson, type TodayReviewScenario } from './today-fixtures'
import './today-review.pcss'

const query = new URLSearchParams(window.location.search)
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const catalogVisible = query.get('catalog') === '1'
const scenarios = createTodayReviewScenarios(Date.now())
const initialId = query.get('state') ?? 'default'
const scenario = shallowRef<TodayReviewScenario>(scenarios.find((item) => item.id === initialId) ?? scenarios[0]!)
const status = ref('')
const roleDialogOpen = ref(false)
const roleSnapshot = ref<ProfileSnapshot | null>(createRoleReviewSnapshot())
const expandedLessonId = ref<string | null>(null)
const lessonIds = ref<string[]>([])
const reason = ref<string | null>(null)
const comment = ref('')
const files = ref<RequestFileRef[]>([])
const selectedDate = ref('2026-10-04')
const hostBackVisible = ref(false)
const navItems = createStudentNavigationItems({ homeworkEnabled: true, attendanceEnabled: true, moreEnabled: true, profileEnabled: true })
const navigation = createMobileNavigationStack()
const route = computed(() => scenario.value.kind !== 'today'
  ? nestedRoute('today', `today/review-${scenario.value.kind}`, 'editor')
  : scenario.value.task ? nestedRoute('today', 'today/review-request-sent', 'task') : rootRoute('today'))
const form = computed(() => scenario.value.form)
const todayLesson = computed(() => selectedTodayLesson(form.value ? { ...scenario.value, form: { ...form.value, lessonIds: lessonIds.value } } : scenario.value)
  ?? (form.value ? scenario.value.today?.lessons[0] ?? null : null))
const formStateProps = computed(() => ({
  lessonsLoading: form.value?.lessonsLoading ?? false,
  lessonsError: form.value?.lessonsError ?? null,
  offline: form.value?.offline ?? scenario.value.offline ?? false,
  submitting: form.value?.submitting ?? false,
  submitError: form.value?.submitError ?? null,
  disabled: form.value?.disabled ?? false,
  ambiguous: form.value?.ambiguous ?? false,
}))

// A browser-only adapter exercises the shell contract; no Telegram SDK is connected.
const host: MobileHostAdapter = {
  backOwner: shell === 'tma' ? 'host' : 'product',
  primaryActionOwner: 'product',
  setBackVisible: (visible) => { hostBackVisible.value = visible },
  subscribeBack: (listener) => {
    window.addEventListener('today-review-host-back', listener)
    return () => window.removeEventListener('today-review-host-back', listener)
  },
}
const previousRootFont = document.documentElement.style.fontSize
watch(scenario, (value) => {
  roleDialogOpen.value = value.roleDialog ?? false
  roleSnapshot.value = value.roleState?.snapshot === undefined ? createRoleReviewSnapshot() : value.roleState.snapshot
  expandedLessonId.value = value.expandedLessonId ?? null
  lessonIds.value = [...value.form?.lessonIds ?? []]
  reason.value = value.form?.reason ?? null
  comment.value = value.form?.comment ?? ''
  files.value = [...value.form?.files ?? []]
  selectedDate.value = value.today?.date ?? '2026-10-04'
  document.documentElement.style.fontSize = value.enlargedText ? '20px' : previousRootFont
  document.title = `${value.label} · ${shell.toUpperCase()} · визуальная симуляция`
  navigation.replace(route.value)
}, { immediate: true })
onBeforeUnmount(() => { document.documentElement.style.fontSize = previousRootFont })

function sceneHref(id: string): string {
  const params = new URLSearchParams({ state: id, shell })
  return `?${params.toString()}`
}
function selectScene(id: string): void {
  const next = scenarios.find((item) => item.id === id)
  if (!next) return
  scenario.value = next
  window.history.replaceState(null, '', sceneHref(id))
}
function simulate(message: string): void { status.value = `Визуальная симуляция · ${message}. API не вызывается.` }
function simulateRoleSelection(role: ProfileRole): void {
  if (roleSnapshot.value) roleSnapshot.value.activeRole = role
  simulate('роль выбрана')
  roleDialogOpen.value = false
}
function toggleActions(lesson: TodayLesson): void {
  expandedLessonId.value = expandedLessonId.value === lesson.schedule.id ? null : lesson.schedule.id
  simulate(expandedLessonId.value ? 'действия пары раскрыты' : 'действия пары закрыты')
}
function openRequest(_lesson: TodayLesson, kind: RequestKind): void {
  selectScene(kind === 'EXCUSE' ? 'excuse-form' : 'late-form')
  simulate('открыта форма заявки')
}
function simulateSubmit(): void {
  selectScene('request-sent')
  simulate('показано подтверждение отправки заявки')
}
function goBack(): void {
  selectScene('absence-actions')
  simulate('возврат к сегодняшним парам')
}
function navigate(id: MobileRootRouteId): void {
  if (id === 'today') selectScene('default')
  simulate(`выбран раздел «${id}»`)
}
function retryOptions(): void {
  const draft = { lessonIds: [...lessonIds.value], reason: reason.value, comment: comment.value, files: [...files.value] }
  selectScene(scenario.value.kind === 'excuse' ? 'excuse-form' : scenario.value.kind === 'late' ? 'late-form' : 'absence-actions')
  void nextTick(() => { lessonIds.value = draft.lessonIds; reason.value = draft.reason; comment.value = draft.comment; files.value = draft.files })
  simulate('показаны загруженные варианты заявки; черновик сохранён')
}
</script>

<template>
  <div
    class="today-review"
    :data-preview-shell="shell === 'tma' ? 'tma-local' : 'pwa-local'"
    :data-scenario="scenario.id"
    data-simulation="true"
    :data-host-back-visible="hostBackVisible"
  >
    <main
      v-if="catalogVisible"
      class="today-review-catalog"
    >
      <h1>StudentToday · {{ shell.toUpperCase() }}</h1>
      <p>Визуальные симуляции: {{ scenarios.length }} состояний реальных компонентов. Действия и отправки не вызывают API. TMA здесь — локальный браузерный preview без подключения Telegram.</p>
      <ul>
        <li
          v-for="item in scenarios"
          :key="item.id"
        >
          <a :href="sceneHref(item.id)">{{ item.label }}</a><small>{{ item.id }} · {{ item.kind }}</small>
        </li>
      </ul>
    </main>
    <TodayScreen
      v-else-if="scenario.kind === 'today'"
      :today="scenario.today"
      :role-switch-disabled="false"
      :loading="scenario.loading ?? false"
      :error="scenario.error ?? null"
      :offline="scenario.offline ?? false"
      :updated-at="scenario.today?.serverNow ?? null"
      :submitting-lesson-id="scenario.submittingLessonId ?? null"
      :acquiring-lesson-id="scenario.acquiringLessonId ?? null"
      :semester-schedule="scenario.semesterSchedule ?? null"
      :selected-date="selectedDate"
      :nav-items="navItems"
      :route="route"
      :navigation="navigation"
      :host="host"
      :read-only="scenario.readOnly ?? false"
      :checkin-error="scenario.checkinError ?? null"
      :expanded-lesson-id="expandedLessonId"
      :request-options="scenario.requestOptions ?? []"
      :options-loading="scenario.optionsLoading ?? false"
      :options-error="scenario.optionsError ?? null"
      @toggle-actions="toggleActions"
      @open-request="openRequest"
      @checkin="selectScene('confirmed'); simulate('показана успешная геопроверка')"
      @retry="selectScene('default'); simulate('показано загруженное расписание')"
      @retry-options="retryOptions"
      @navigate="navigate"
      @back="goBack"
      @select-date="selectedDate = $event; simulate(`выбрана дата ${$event}`)"
      @role-switch="roleDialogOpen = true"
    />
    <MobileShell
      v-else-if="form"
      custom-back
      :route="route"
      :navigation="navigation"
      :nav-items="navItems"
      :host="host"
      @back="goBack"
      @navigate="navigate"
    >
      <template #back />
      <ExcuseRequestScreen
        v-if="scenario.kind === 'excuse'"
        :access="form.access"
        :lessons="form.lessons"
        :lesson-ids="lessonIds"
        :today-lesson="todayLesson"
        :reasons="form.reasons"
        :reason="reason"
        :comment="comment"
        :files="files"
        :file-limits="form.fileLimits"
        v-bind="formStateProps"
        @update:lesson-ids="lessonIds = $event"
        @update:reason="reason = $event"
        @update:comment="comment = $event"
        @update:files="files = $event"
        @retry-lessons="retryOptions"
        @abandon="goBack"
        @back="goBack"
        @submit="simulateSubmit"
      />
      <LateCheckinRequestScreen
        v-else
        :access="form.access"
        :lessons="form.lessons"
        :lesson-id="lessonIds[0] ?? null"
        :today-lesson="todayLesson"
        :budget="form.budget"
        v-bind="formStateProps"
        @update:lesson-id="lessonIds = $event ? [$event] : []"
        @retry-lessons="retryOptions"
        @abandon="goBack"
        @back="goBack"
        @cancel="goBack"
        @submit="simulateSubmit"
      />
    </MobileShell>
    <RoleSwitchDialog
      v-if="roleDialogOpen && !catalogVisible"
      :snapshot="roleSnapshot"
      :loading="scenario.roleState?.loading ?? false"
      :error="scenario.roleState?.error ?? null"
      :offline="scenario.roleState?.offline ?? false"
      :pending-role="scenario.roleState?.pendingRole ?? null"
      :on-select-role="simulateRoleSelection"
      @close="roleDialogOpen = false"
    />
    <p
      v-if="scenario.diagnostic && !catalogVisible"
      class="today-review-status"
      role="status"
    >
      {{ scenario.diagnostic }}
    </p>
    <p
      v-if="status && !catalogVisible"
      class="today-review-status"
      role="status"
    >
      {{ status }}
    </p>
  </div>
</template>
