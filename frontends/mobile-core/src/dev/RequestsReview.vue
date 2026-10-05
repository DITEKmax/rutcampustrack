<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { StudentApi } from '../api/student-client'
import type { StudentExcuseRequest, StudentRequestDetail } from '../api/types'
import RequestsScreen from '../features/requests/RequestsScreen.vue'
import RequestTypeScreen from '../features/requests/RequestTypeScreen.vue'
import RequestLessonSelectionScreen from '../features/requests/RequestLessonSelectionScreen.vue'
import ExcuseRequestScreen from '../features/requests/ExcuseRequestScreen.vue'
import LateCheckinRequestScreen from '../features/requests/LateCheckinRequestScreen.vue'
import { useRequests } from '../features/requests/use-requests'
import { getOrCreateRequestsDraft, purgeRequestsDrafts, requestsSessionGeneration, updateRequestsDraft, requestSelectionEligible } from '../features/requests/state'
import type { ExcuseRequestPayload, LateCheckinRequestPayload, RequestKind, RequestsDraftPatch, RequestsView } from '../features/requests/types'
import MobileShell from '../shared/components/MobileShell.vue'
import { createStudentNavigationItems } from '../shared/mobile-navigation-items'
import { nestedRoute } from '../shared/navigation'
import type { StudentFeatureScope } from '../shared/session-owner'
import type { MobileHostAdapter } from '../shared/host'
import { reviewRequestOptions, reviewRequests, requestsReviewScenarios } from './requests-fixtures'
import './requests-review.pcss'
const query = new URLSearchParams(window.location.search)
const scene = query.get('state') ?? 'open'
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const catalog = query.get('catalog') === '1'
const offline = ref(scene === 'offline' || query.get('offline') === '1')
const readOnly = ref(scene === 'readonly' || query.get('readonly') === '1')
const scope = ref<StudentFeatureScope>({ userId: 'requests-review-student', activeRole: 'STUDENT', groupId: 'review-group', semesterId: 'semester-review', sessionId: 'requests-review-session', sessionVersion: '1', rolesVersion: '1', resetGeneration: 0, readOnly: readOnly.value })
const draftOwnerId = 'requests-review-student'
const session = requestsSessionGeneration(scope.value)
purgeRequestsDrafts(draftOwnerId, session)
const draft = shallowRef(getOrCreateRequestsDraft(draftOwnerId, session))
function patch(value: RequestsDraftPatch): void { draft.value = updateRequestsDraft(draftOwnerId, session, value) }
const view = computed(() => draft.value.view)
const selectedKind = computed<RequestKind>(() => view.value === 'select-late' || view.value === 'late' ? 'LATE_CHECKIN' : 'EXCUSE')
const selectedIds = computed(() => selectedKind.value === 'EXCUSE' ? draft.value.excuseLessonIds : draft.value.lateLessonId ? [draft.value.lateLessonId] : [])
const options = reviewRequestOptions(scene === 'long', scene === 'exhausted')
if (scene === 'selection-empty') options.lessons = []
let records = reviewRequests(scene === 'long')
if (scene === 'empty') records = records.filter((item) => item.summary.status !== 'PENDING')
let feedFailure = scene === 'error'
let optionsFailure = scene === 'options-error'
let submitFailure = scene === 'submit-error'
const commands = ref(0)
const requests = ref(0)
const status = ref('')
const timers = new Map<number, () => void>()
function delay(ms: number): Promise<void> { return new Promise((resolve) => { const timer = window.setTimeout(() => { timers.delete(timer); resolve() }, ms); timers.set(timer, resolve) }) }
function response(value: unknown, code = 200): Response { return new Response(JSON.stringify(value), { status: code, headers: { 'Content-Type': 'application/json' } }) }
const idempotentResults = new Map<string, StudentRequestDetail>()
const fetcher: typeof fetch = async (input, init) => {
  requests.value += 1
  const url = new URL(String(input), window.location.origin)
  if (scene === 'loading') await delay(60000)
  if (scene === 'forbidden') return response({ title: 'Раздел недоступен', detail: 'У тебя сейчас нет доступа к заявкам.' }, 403)
  if (init?.method === 'POST') {
    commands.value += 1
    await delay(650)
    if (submitFailure) { submitFailure = false; return response({ title: 'Связь прервалась', detail: 'Результат отправки неизвестен. Повтори ту же отправку.' }, 503) }
    if (url.pathname.endsWith('/cancel')) {
      const id = url.pathname.split('/').at(-2)
      const item = records.find((value) => value.summary.id === id)
      if (!item) return response({ title: 'Заявка недоступна' }, 404)
      if (item.summary.status !== 'PENDING') return response({ title: 'Решение уже принято' }, 409)
      item.summary.status = 'CANCELLED'
      for (const option of options.lessons) option.pendingRequests = option.pendingRequests.filter((entry) => entry.id !== id)
      return response(item)
    }
    const key = new Headers(init.headers).get('Idempotency-Key') ?? ''
    if (idempotentResults.has(key)) return response(idempotentResults.get(key))
    const late = url.pathname.endsWith('/late-checkin')
    const payload = late ? JSON.parse(String(init.body)) as { lessonId: string } : JSON.parse(await ((init.body as FormData).get('request') as Blob).text()) as StudentExcuseRequest
    const ids = 'lessonId' in payload ? [payload.lessonId] : payload.lessonIds
    const item: StudentRequestDetail = { summary: { id: 'submitted-' + commands.value, kind: late ? 'LATE_CHECKIN' : 'EXCUSE', status: 'PENDING', origin: 'MANUAL', createdAt: '2026-10-05T08:00:00Z', updatedAt: '2026-10-05T08:00:00Z', lessons: options.lessons.filter((entry) => ids.includes(entry.lesson.id)).map((entry) => entry.lesson) }, reason: 'reason' in payload ? payload.reason : null, comment: 'comment' in payload ? payload.comment : null, attachments: [], decision: { comment: null, decidedAt: null } }
    records.unshift(item)
    idempotentResults.set(key, item)
    for (const option of options.lessons.filter((entry) => ids.includes(entry.lesson.id))) option.pendingRequests.push({ id: item.summary.id, kind: item.summary.kind, origin: 'MANUAL' })
    if (late) { options.budget.used += 1; options.budget.remaining -= 1 }
    return response(item, 201)
  }
  if (url.pathname.endsWith('/options')) {
    if (optionsFailure) return response({ title: 'Пары не загружены', detail: 'Не удалось связаться с сервером. Попробуй ещё раз.' }, 503)
    return response(options)
  }
  if (url.pathname.endsWith('/requests')) {
    if (feedFailure) return response({ title: 'Заявки не загружены', detail: 'Не удалось связаться с сервером. Попробуй ещё раз.' }, 503)
    const content = records.filter((item) => (url.searchParams.get('bucket') === 'ARCHIVE') === (item.summary.status !== 'PENDING')).map((item) => item.summary)
    return response({ content, page: 0, size: 10, totalPages: 1, totalElements: content.length })
  }
  return response(records.find((item) => item.summary.id === url.pathname.split('/').at(-1)) ?? { title: 'Заявка недоступна' }, records.some((item) => item.summary.id === url.pathname.split('/').at(-1)) ? 200 : 404)
}
const api = new StudentApi({ accessToken: () => 'synthetic-requests-review', fetcher })
const owner = useRequests(api, scope, { offline, readOnly })
const lessons = computed(() => owner.view.options?.lessons ?? [])
const bucket = computed(() => owner.bucketView())
const route = computed(() => nestedRoute('more', `more/requests${view.value === 'inbox' ? '' : '/' + view.value}`, view.value === 'inbox' ? 'overview' : 'editor'))
const navItems = createStudentNavigationItems({ homeworkEnabled: true, attendanceEnabled: true, moreEnabled: true, profileEnabled: true })
const hostBackVisible = ref(false)
const host: MobileHostAdapter = { backOwner: shell === 'tma' ? 'host' : 'product', primaryActionOwner: 'product', setBackVisible: (visible) => { hostBackVisible.value = visible } }
const choices = computed(() => [
  { kind: 'EXCUSE' as const, label: 'Уважительная причина', symbol: 'у', available: !offline.value && !readOnly.value && owner.view.access === 'allowed' },
  { kind: 'LATE_CHECKIN' as const, label: 'Забыл отметиться', symbol: 'н', available: !offline.value && !readOnly.value && owner.view.access === 'allowed' },
])
const command = computed(() => owner.commandState(selectedKind.value))
const formProps = computed(() => ({ access: owner.view.access, lessons: lessons.value, lessonsLoading: owner.view.optionsLoading, lessonsError: owner.view.optionsError, offline: offline.value, readOnly: readOnly.value, disabled: readOnly.value, submitting: owner.view.mutation === 'submitting', submitError: owner.view.mutationError, ambiguous: command.value?.status === 'ambiguous', selectionComplete: true }))
function sceneHref(id: string): string { return '?' + new URLSearchParams({ state: id, shell }) }
function error(cause: unknown): void { status.value = cause instanceof Error ? cause.message : String(cause) }
function setView(next: RequestsView): void { patch({ view: next }); status.value = '' }
function choose(kind: RequestKind): void { setView(kind === 'EXCUSE' ? 'select-excuse' : 'select-late'); void owner.loadOptions().catch(error) }
function selection(ids: string[]): void { patch(selectedKind.value === 'EXCUSE' ? { excuseLessonIds: ids } : { lateLessonId: ids[0] ?? null }) }
function continueSelection(): void { if (requestSelectionEligible(lessons.value, selectedIds.value, selectedKind.value)) setView(selectedKind.value === 'EXCUSE' ? 'excuse' : 'late') }
function back(): void { setView(view.value === 'excuse' ? 'select-excuse' : view.value === 'late' ? 'select-late' : view.value === 'select-excuse' || view.value === 'select-late' ? 'type' : 'inbox') }
async function submit(payload: ExcuseRequestPayload | LateCheckinRequestPayload): Promise<void> {
  try { if ('lessonIds' in payload) await owner.submitExcuse(payload); else await owner.submitLateCheckin(payload); setView('inbox'); patch('lessonIds' in payload ? { excuseLessonIds: [], excuseReason: null, excuseComment: '', excuseFiles: [] } : { lateLessonId: null }); await owner.load('open'); await owner.loadOptions() } catch (cause) { error(cause) }
}
function retry(): void { feedFailure = false; optionsFailure = false; void owner.loadOptions().catch(error); void owner.load().catch(error) }
function selectBucket(value: 'open' | 'archive'): void { patch({ bucket: value }); owner.selectBucket(value); void owner.load(value).catch(error) }
const initialView: RequestsView = scene === 'type' ? 'type' : scene === 'selection-late' ? 'select-late' : scene.startsWith('selection') || scene === 'options-error' ? 'select-excuse' : scene === 'late' || scene === 'exhausted' ? 'late' : ['excuse', 'custom', 'files', 'submit-error'].includes(scene) ? 'excuse' : 'inbox'
patch({ view: initialView, bucket: scene === 'archive' ? 'archive' : 'open', excuseLessonIds: ['excuse', 'custom', 'files', 'submit-error'].includes(scene) ? ['lesson-programming', 'lesson-networks'] : scene === 'selection-missing' ? ['missing-lesson', 'lesson-unavailable'] : [], excuseReason: scene === 'custom' ? 'OTHER' : ['excuse', 'files', 'submit-error'].includes(scene) ? 'ILLNESS' : null, excuseComment: scene === 'custom' ? 'Участвовал в семейном мероприятии. Нужна уважительная причина для выбранных пар.' : '', lateLessonId: scene === 'late' || scene === 'exhausted' ? 'lesson-maths' : null })
if (scene === 'files') { const file = new File(['%PDF-1.7 synthetic fixture'], 'справка.pdf', { type: 'application/pdf' }); patch({ excuseFiles: [{ id: 'review-proof', name: file.name, type: file.type, size: file.size, file }] }) }
owner.selectBucket(draft.value.bucket)
const previousFont = document.documentElement.style.fontSize
if (query.get('root20') === '1') document.documentElement.style.fontSize = '20px'
watch([commands, requests], () => { document.documentElement.dataset.requestsFixtureCommands = String(commands.value); document.documentElement.dataset.requestsFixtureReads = String(requests.value) }, { immediate: true })
onMounted(() => { if (!offline.value && !catalog) { void owner.load().catch(error); void owner.loadOptions().catch(error) } })
onBeforeUnmount(() => { document.documentElement.style.fontSize = previousFont; for (const [timer, resolve] of timers) { window.clearTimeout(timer); resolve() }; timers.clear(); purgeRequestsDrafts(draftOwnerId, session) })
</script>
<template>
  <main
    v-if="catalog"
    class="requests-review-catalog"
  >
    <h1>Заявки · {{ shell.toUpperCase() }}</h1><p>Синтетический API: настоящие StudentApi, useRequests и MobileShell. Сервер, БД и Telegram не подключены.</p><ul>
      <li
        v-for="item in requestsReviewScenarios"
        :key="item[0]"
      >
        <a :href="sceneHref(item[0])">{{ item[1] }}</a> · {{ item[0] }}
      </li>
    </ul>
  </main>
  <div
    v-else
    class="requests-review"
    :data-scenario="scene"
    :data-view="view"
    :data-host-back-visible="hostBackVisible"
    data-synthetic-api="true"
  >
    <MobileShell
      :route="route"
      :nav-items="navItems"
      :host="host"
      custom-back
      @back="back"
      @navigate="status = 'API fixture: выбран раздел ' + $event"
    >
      <RequestsScreen
        v-if="view === 'inbox'"
        :bucket="owner.view.bucket"
        :requests="bucket.requests"
        :loading="bucket.loading"
        :error="bucket.error"
        :offline="offline"
        :read-only="readOnly"
        :access="owner.view.access"
        :cancelling-id="owner.view.mutation === 'cancelling' ? bucket.requests.find((item) => item.summary.status === 'PENDING')?.summary.id ?? null : null"
        @new-request="setView('type')"
        @select-bucket="selectBucket"
        @retry="retry"
        @cancel="owner.cancelRequest($event).catch(error)"
      />
      <RequestTypeScreen
        v-else-if="view === 'type'"
        :choices="choices"
        :busy="owner.view.optionsLoading"
        :error="owner.view.optionsError"
        @choose="choose"
        @back="back"
      />
      <RequestLessonSelectionScreen
        v-else-if="view === 'select-excuse' || view === 'select-late'"
        :kind="selectedKind"
        :lessons="lessons"
        :lesson-ids="selectedIds"
        :access="owner.view.access"
        :options-loaded="owner.view.options !== null"
        :loading="owner.view.optionsLoading"
        :error="owner.view.optionsError"
        :offline="offline"
        :read-only="readOnly"
        @update:lesson-ids="selection"
        @continue="continueSelection"
        @retry="retry"
        @back="back"
      />
      <ExcuseRequestScreen
        v-else-if="view === 'excuse'"
        v-bind="formProps"
        :lesson-ids="draft.excuseLessonIds"
        :reasons="owner.view.options?.reasons ?? []"
        :reason="draft.excuseReason"
        :comment="draft.excuseComment"
        :files="draft.excuseFiles"
        :file-limits="owner.view.options?.files ?? null"
        @update:reason="patch({ excuseReason: $event })"
        @update:comment="patch({ excuseComment: $event })"
        @update:files="patch({ excuseFiles: $event })"
        @edit-selection="setView('select-excuse')"
        @back="back"
        @retry-lessons="retry"
        @abandon="owner.abandonCommand('EXCUSE')"
        @submit="submit"
      />
      <LateCheckinRequestScreen
        v-else
        v-bind="formProps"
        :lesson-id="draft.lateLessonId"
        :budget="owner.view.options?.budget ?? null"
        @edit-selection="setView('select-late')"
        @back="back"
        @cancel="back"
        @retry-lessons="retry"
        @abandon="owner.abandonCommand('LATE_CHECKIN')"
        @submit="submit"
      />
    </MobileShell>
    <p
      v-if="status"
      class="requests-review-status"
      role="status"
    >
      {{ status }}
    </p>
  </div>
</template>
