<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import RequestAttachmentPreview from '../requests/RequestAttachmentPreview.vue'
import { requestStatusLabel, requestStatusTone, lessonTypeLabel } from '../requests/state'
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client'
import { HeadmanRequestsApiError, type HeadmanRequestDetail, type HeadmanRequestKind, type HeadmanRequestLesson, type HeadmanRequestPage, type HeadmanRequestSummary, type HeadmanRequestsApi } from './headman-requests-client'
import { freezeRequestDecision, sendRequestDecision, type FrozenRequestDecision, type RequestDecision } from './request-decision'
import { formatRequestLessonTime } from './request-lesson-time'
import './headman-requests-screen.pcss'

const props = withDefaults(defineProps<{ api: HeadmanRequestsApi | null; assistantPermissions?: readonly HeadmanAssistantPermission[] | null; offline?: boolean; readOnly?: boolean; showBack?: boolean }>(), { assistantPermissions: null, offline: false, readOnly: false, showBack: true })
export type HeadmanRequestsView = 'queue' | 'archive' | 'detail' | 'approve' | 'reject'
const emit = defineEmits<{ back: []; error: [cause: unknown]; 'view-change': [view: HeadmanRequestsView] }>()
const view = ref<HeadmanRequestsView>('queue'), bucket = ref<'OPEN' | 'ARCHIVE'>('OPEN'), kind = ref<HeadmanRequestKind | ''>('')
const studentName = ref(''), dateFrom = ref(''), dateTo = ref(''), filterOpen = ref(false), currentPage = ref(0)
const page = ref<HeadmanRequestPage | null>(null), details = ref<Record<string, HeadmanRequestDetail>>({}), selected = ref<HeadmanRequestDetail | null>(null), selectedRequest = shallowRef<HeadmanRequestSummary | null>(null)
const loading = ref(false), busy = ref(false), error = ref(''), notice = ref(''), reason = ref(''), counts = ref<Partial<Record<'all' | HeadmanRequestKind, number>>>({})
const pending = shallowRef<FrozenRequestDecision | null>(null)
let revision = 0, disposed = false
const allowed = computed(() => props.assistantPermissions === null || props.assistantPermissions.includes('MANAGE_EXCUSES'))
const writable = computed(() => !!props.api && allowed.value && !props.offline && !props.readOnly && !busy.value && !pending.value)
const root = computed(() => view.value === 'queue' || view.value === 'archive')
// Historical snapshots are evidence. Confirmation uses only the additive current projection.
const affected = computed(() => selected.value?.currentAffectedLessons ?? [])
const transferred = computed(() => !!selected.value?.currentAffectedLessons && JSON.stringify(selected.value.lessons.map((lesson) => [lesson.lessonId, lesson.date])) !== JSON.stringify(selected.value.currentAffectedLessons.map((lesson) => [lesson.lessonId, lesson.date])))
const title = computed(() => ({ queue: 'Заявки', archive: 'Архив заявок', detail: 'Заявка', approve: 'Одобрить заявку', reject: 'Отклонить заявку' })[view.value])
const ownerKey = computed(() => `${revision}:${selected.value?.summary.id ?? ''}`)
function setView(next: HeadmanRequestsView): void { view.value = next; emit('view-change', next) }
function current(run: number, api: HeadmanRequestsApi | null): boolean { return !disposed && run === revision && api === props.api && allowed.value && !props.offline }
function failure(cause: unknown, fallback: string): string {
  if (cause instanceof HeadmanRequestsApiError) {
    if (cause.response.status === 401) return 'Сессия истекла. Войди снова.'
    if (cause.response.status === 403) return 'Нет права управлять заявками этой группы.'
    if (cause.response.status === 409) return 'Заявка изменилась. Проверь актуальные данные.'
  }
  return cause instanceof Error ? cause.message : fallback
}
function filters() { return { bucket: bucket.value, studentName: studentName.value, coverageDateFrom: dateFrom.value, coverageDateTo: dateTo.value } }
async function load(): Promise<void> {
  const run = ++revision, api = props.api
  page.value = null; details.value = {}; error.value = ''; counts.value = {}; loading.value = false
  if (!api || !allowed.value || props.offline) return
  if (dateFrom.value && dateTo.value && dateFrom.value > dateTo.value) { error.value = 'Начало периода должно быть раньше его конца.'; return }
  loading.value = true
  try {
    const data = await api.list({ ...filters(), type: kind.value || null, page: currentPage.value, size: 20 })
    if (!current(run, api)) return
    page.value = data
    // Hydrate one bounded page, with four concurrent authenticated detail reads.
    const hydrated: Record<string, HeadmanRequestDetail> = {}
    let cursor = 0
    await Promise.all(Array.from({ length: Math.min(4, data.content.length) }, async () => {
      while (cursor < data.content.length && current(run, api)) {
        const item = data.content[cursor++]!
        try { const detail = await api.get(item.id); if (current(run, api) && detail.summary.id === item.id) hydrated[item.id] = detail } catch { /* A failed detail remains an honest ungrouped ticket. */ }
      }
    }))
    if (!current(run, api)) return
    details.value = hydrated
    const countQueries = await Promise.allSettled(([['all', null], ['EXCUSE', 'EXCUSE'], ['LATE_CHECKIN', 'LATE_CHECKIN']] as const).map(async ([key, type]) => ({ key, count: (await api.list({ ...filters(), type, size: 1, page: 0 })).totalElements })))
    if (!current(run, api)) return
    counts.value = Object.fromEntries(countQueries.flatMap((result) => result.status === 'fulfilled' ? [[result.value.key, result.value.count]] : []))
  } catch (cause) { if (current(run, api)) { error.value = failure(cause, 'Не удалось загрузить заявки.'); emit('error', cause) } }
  finally { if (run === revision) loading.value = false }
}
const groups = computed(() => {
  const result = new Map<string, { lesson: HeadmanRequestLesson | null; items: HeadmanRequestSummary[] }>()
  for (const item of page.value?.content ?? []) {
    const lessons = details.value[item.id]?.currentAffectedLessons ?? []
    const contexts = bucket.value === 'ARCHIVE' ? [null] : lessons.length ? lessons : [null]
    for (const lesson of contexts) {
      const key = lesson ? `${lesson.lessonId}:${lesson.date}` : `unknown:${item.id}`
      const group = result.get(key) ?? { lesson, items: [] }
      if (!group.items.some((entry) => entry.id === item.id)) group.items.push(item)
      result.set(key, group)
    }
  }
  return [...result.entries()].map(([key, value]) => ({ key, ...value }))
})
function choose(next: HeadmanRequestKind | '', archive = false): void {
  if (busy.value || pending.value) return
  kind.value = next; bucket.value = archive ? 'ARCHIVE' : 'OPEN'; currentPage.value = 0; selected.value = null; notice.value = ''; setView(archive ? 'archive' : 'queue'); void load()
}
async function open(item: HeadmanRequestSummary): Promise<void> {
  if (!props.api || busy.value || pending.value || props.offline || !allowed.value) return
  const api = props.api, run = ++revision
  selectedRequest.value = item; selected.value = null; reason.value = ''; error.value = ''; notice.value = ''; setView('detail'); loading.value = true
  try { const detail = await api.get(item.id); if (!current(run, api)) return; if (detail.summary.id !== item.id) throw new Error('Сервер вернул другую заявку.'); selected.value = detail }
  catch (cause) { if (current(run, api)) { error.value = failure(cause, 'Не удалось открыть заявку.'); emit('error', cause) } }
  finally { if (run === revision) loading.value = false }
}
async function confirm(decision: RequestDecision): Promise<void> {
  const item = selected.value?.summary
  if (!item || !writable.value) return
  await open(item) // Fresh GET before entering either confirmation.
  if (!selected.value || !writable.value || selected.value.summary.status !== 'PENDING') return
  setView(decision === 'APPROVED' ? 'approve' : 'reject')
}
async function decide(replay = false): Promise<void> {
  const api = props.api, detail = selected.value
  if (!api || !detail || props.offline || props.readOnly || !allowed.value || busy.value) return
  if (pending.value && pending.value.actor !== api) return
  if (!pending.value) {
    if (view.value === 'approve' && !detail.currentAffectedLessons) { error.value = 'Не удалось проверить текущие пары. Обнови заявку перед решением.'; return }
    try { pending.value = freezeRequestDecision(api, detail, view.value === 'approve' ? 'APPROVED' : 'REJECTED', reason.value) }
    catch (cause) { error.value = failure(cause, 'Проверь причину решения.'); return }
  }
  const frozen = pending.value, run = revision
  busy.value = true; error.value = ''
  const outcome = await sendRequestDecision(frozen, replay, () => current(run, api) && !props.readOnly && pending.value === frozen)
  if (!current(run, api) || props.readOnly) { busy.value = false; return }
  busy.value = false
  if (outcome.status === 'unknown') { error.value = 'Результат решения неизвестен. Проверь результат: сначала обновим заявку, затем при необходимости повторим то же решение.'; emit('error', outcome.cause); return }
  if (outcome.status === 'blocked') { error.value = 'Решение не отправлено: проверь доступ и повтори то же решение.'; return }
  pending.value = null
  if (outcome.status === 'rejected') { error.value = failure(outcome.cause, 'Решение не принято.'); emit('error', outcome.cause); return }
  selected.value = outcome.detail; setView('detail')
  notice.value = outcome.status === 'done' ? 'Решение сохранено. Заявка находится в архиве.' : 'Заявка уже обработана. Показано актуальное решение.'
}
function back(): boolean {
  if (busy.value || pending.value) return true
  if (filterOpen.value) { filterOpen.value = false; return true }
  if (view.value === 'approve' || view.value === 'reject') { error.value = ''; setView('detail'); return true }
  if (view.value === 'detail') { selected.value = null; error.value = ''; setView(bucket.value === 'ARCHIVE' ? 'archive' : 'queue'); void load(); return true }
  return false
}
function handleBack(): void { if (!back()) emit('back') }
function apply(): void { filterOpen.value = false; currentPage.value = 0; void load() }
function changePage(next: number): void { if (next < 0 || next >= (page.value?.totalPages ?? 0) || busy.value) return; currentPage.value = next; void load() }
function date(value: string | null): string { if (!value) return 'Дата не указана'; const d = new Date(value); return Number.isNaN(d.getTime()) ? value : new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', timeZone: 'UTC' }).format(d) }
function time(lesson: HeadmanRequestLesson): string { return formatRequestLessonTime(lesson) }
function statusSymbol(status: string | null): string { return status === 'PRESENT' ? '+' : status === 'EXCUSED' ? 'у' : status === 'ABSENT' ? 'н' : '—' }
function statusText(status: string | null): string { return status === 'PRESENT' ? 'Присутствовал' : status === 'EXCUSED' ? 'Уважительная причина' : status === 'ABSENT' ? 'Отсутствовал' : 'Не отмечено' }
function target(lesson: HeadmanRequestLesson): string { return lesson.attendanceStatus === 'PRESENT' || selected.value?.summary.kind === 'LATE_CHECKIN' ? 'PRESENT' : 'EXCUSED' }
watch(() => props.api, () => {
  revision += 1; busy.value = false; selected.value = null; pending.value = null; error.value = ''; setView(bucket.value === 'ARCHIVE' ? 'archive' : 'queue'); void load()
}, { immediate: true, flush: 'sync' })
watch(() => [props.offline, props.assistantPermissions?.join(','), props.readOnly] as const, () => {
  revision += 1; busy.value = false; loading.value = false
  if (pending.value) error.value = 'Решение отправлено, но результат ещё не подтверждён. После восстановления доступа проверь и повтори то же решение.'
  if (root.value) void load()
  // Same actor's uncertain write stays frozen through connectivity/permission changes.
}, { flush: 'sync' })
onBeforeUnmount(() => { disposed = true; revision += 1 })
defineExpose({ back })
</script>

<template>
  <main
    class="headman-requests"
    :data-request-view="view"
    :aria-busy="loading || busy"
    aria-labelledby="headman-requests-title"
  >
    <header class="headman-requests__header">
      <button
        v-if="!root || showBack"
        class="headman-requests__back"
        type="button"
        aria-label="Назад"
        @click="handleBack"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="headman-requests-title">
        {{ title }}
      </h1>
      <button
        v-if="root"
        type="button"
        class="headman-requests__filter"
        aria-label="Поиск и период"
        @click="filterOpen = !filterOpen"
      >
        <MobileIcon name="search" />
      </button>
    </header>
    <div
      v-if="root"
      class="headman-requests__tabs"
      aria-label="Фильтры заявок"
    >
      <button
        type="button"
        :aria-pressed="bucket === 'OPEN' && !kind"
        @click="choose('')"
      >
        Все <span v-if="counts.all !== undefined">{{ counts.all }}</span>
      </button>
      <button
        type="button"
        :aria-pressed="bucket === 'OPEN' && kind === 'EXCUSE'"
        @click="choose('EXCUSE')"
      >
        <span
          class="headman-requests__symbol"
          data-status="EXCUSED"
        >у</span><span v-if="counts.EXCUSE !== undefined">{{ counts.EXCUSE }}</span><span class="headman-requests__sr">Уважительные причины</span>
      </button>
      <button
        type="button"
        :aria-pressed="bucket === 'OPEN' && kind === 'LATE_CHECKIN'"
        @click="choose('LATE_CHECKIN')"
      >
        <span
          class="headman-requests__symbol"
          data-status="ABSENT"
        >н</span><span v-if="counts.LATE_CHECKIN !== undefined">{{ counts.LATE_CHECKIN }}</span><span class="headman-requests__sr">Забыл отметиться</span>
      </button>
      <button
        type="button"
        :aria-pressed="bucket === 'ARCHIVE'"
        @click="choose('', true)"
      >
        <MobileIcon name="homework" />Архив
      </button>
    </div>
    <form
      v-if="root && filterOpen"
      class="headman-requests__filters"
      @submit.prevent="apply"
    >
      <label>ФИО<input
        v-model="studentName"
        type="search"
        placeholder="Начни вводить фамилию"
      ></label>
      <label>Период от<input
        v-model="dateFrom"
        type="date"
      ></label><label>Период до<input
        v-model="dateTo"
        type="date"
      ></label>
      <button
        class="headman-requests__primary"
        type="submit"
      >
        Применить
      </button>
    </form>
    <StudentWarningBlock
      v-if="!allowed"
      severity="error"
      title="Нет доступа"
      message="Для заявок нужно право управления уважительными причинами."
    />
    <StudentWarningBlock
      v-else-if="offline"
      title="Ты офлайн"
      message="Подключись к интернету, чтобы открыть заявки и принять решение."
    />
    <StudentWarningBlock
      v-else-if="!api"
      severity="error"
      title="Заявки недоступны"
      message="Источник данных не подключён."
    />
    <StudentWarningBlock
      v-if="error"
      severity="error"
      :title="pending ? 'Проверь результат' : 'Не удалось выполнить действие'"
      :message="error"
      :action-label="pending ? 'Проверить и повторить' : root ? 'Повторить' : selectedRequest ? 'Обновить заявку' : ''"
      :action-disabled="busy || offline || readOnly || !allowed || !api"
      @action="pending ? decide(true) : root ? load() : selectedRequest ? open(selectedRequest) : undefined"
    />
    <StudentWarningBlock
      v-if="notice"
      title="Заявка обработана"
      :message="notice"
    />
    <p
      v-if="loading"
      class="headman-requests__state"
      role="status"
    >
      Загружаем {{ root ? 'заявки' : 'заявку' }}…
    </p>
    <template v-else-if="root && page && allowed && !offline">
      <p
        v-if="!page.content.length"
        class="headman-requests__state"
      >
        {{ bucket === 'ARCHIVE' ? 'В архиве пока нет заявок.' : 'Заявок по заданным условиям нет.' }}
      </p>
      <section
        v-for="group in groups"
        :key="group.key"
        class="headman-requests__group"
      >
        <header
          v-if="group.lesson && bucket === 'OPEN'"
          class="headman-requests__context"
        >
          <h2>{{ group.lesson.subjectName ?? 'Предмет не указан' }}</h2><p><MobileIcon name="clock" />{{ time(group.lesson) }} · {{ date(group.lesson.date) }}</p><p><MobileIcon name="lesson" />{{ lessonTypeLabel(group.lesson.subjectType) }}</p>
        </header>
        <button
          v-for="item in group.items"
          :key="item.id"
          type="button"
          class="headman-requests__ticket"
          :data-request-id="item.id"
          @click="open(item)"
        >
          <span
            class="headman-requests__kind"
            :data-kind="item.kind"
          >{{ item.kind === 'EXCUSE' ? 'у' : 'н' }}</span><span class="headman-requests__ticket-copy"><strong>{{ item.studentName ?? 'Студент' }}</strong><span>{{ item.reason ?? (item.kind === 'LATE_CHECKIN' ? 'Не успел отметиться' : 'Уважительная причина') }} · {{ date(item.coverageStart) }}</span><span
            v-if="bucket === 'ARCHIVE'"
            class="headman-requests__badge"
            :data-tone="requestStatusTone(item.status)"
          >{{ requestStatusLabel(item.status) }}</span><span v-if="!group.lesson && bucket === 'OPEN'">Контекст пары недоступен</span></span><MobileIcon
            name="back"
            class="headman-requests__next"
          />
        </button>
      </section>
      <nav
        v-if="page.totalPages > 1"
        class="headman-requests__pagination"
        aria-label="Страницы заявок"
      >
        <button
          type="button"
          :disabled="!currentPage"
          @click="changePage(currentPage - 1)"
        >
          Назад
        </button><span>{{ currentPage + 1 }} / {{ page.totalPages }}</span><button
          type="button"
          :disabled="currentPage + 1 >= page.totalPages"
          @click="changePage(currentPage + 1)"
        >
          Дальше
        </button>
      </nav>
      <p
        v-if="page.totalPages > 1"
        class="headman-requests__hint"
      >
        Заявки сгруппированы по парам на этой странице.
      </p>
    </template>
    <template v-else-if="selected && allowed && !offline">
      <section
        class="headman-requests__student"
        :data-status="selected.summary.status"
      >
        <strong>{{ selected.summary.studentName ?? 'Студент' }}</strong><p>{{ selected.summary.reason ?? (selected.summary.kind === 'LATE_CHECKIN' ? 'Не успел отметиться' : 'Уважительная причина') }}</p><p>
          Исходный период: {{ date(selected.summary.coverageStart) }}<template v-if="selected.summary.coverageEnd !== selected.summary.coverageStart">
            — {{ date(selected.summary.coverageEnd) }}
          </template>
        </p><span
          v-if="selected.summary.status !== 'PENDING'"
          class="headman-requests__badge"
          :data-tone="requestStatusTone(selected.summary.status)"
        >{{ requestStatusLabel(selected.summary.status) }}</span>
      </section>
      <template v-if="view === 'detail'">
        <h2 class="headman-requests__section-title">
          Комментарий студента
        </h2><p class="headman-requests__comment">
          {{ selected.summary.comment || 'Комментарий не добавлен.' }}
        </p>
        <RequestAttachmentPreview
          v-for="attachment in selected.attachments"
          :key="attachment.id"
          :api="api"
          :request-id="selected.summary.id"
          :attachment="attachment"
          :owner-key="ownerKey"
          :allowed="allowed"
          :offline="offline"
        />
        <p
          v-if="!selected.attachments.length"
          class="headman-requests__hint"
        >
          Вложений нет.
        </p>
        <p
          v-if="selected.summary.decisionComment"
          class="headman-requests__comment"
        >
          Причина решения: {{ selected.summary.decisionComment }}
        </p>
      </template>
      <template v-if="view !== 'reject'">
        <h2 class="headman-requests__section-title">
          {{ view === 'approve' ? 'Как изменятся отметки' : selected.currentAffectedLessons ? 'Текущие пары заявки' : 'Исходные пары заявки' }}
        </h2>
        <StudentWarningBlock
          v-if="!selected.currentAffectedLessons"
          title="Текущие пары недоступны"
          message="Сохранённые данные заявки показаны ниже. Одобрение доступно после проверки текущих пар."
        />

        <ul class="headman-requests__lessons">
          <li
            v-for="(lesson, index) in (selected.currentAffectedLessons ?? selected.lessons)"
            :key="`${lesson.lessonId}:${index}`"
          >
            <span class="headman-requests__lesson-copy"><strong>{{ lesson.subjectName ?? 'Предмет не указан' }}</strong><span>{{ date(lesson.date) }} · {{ time(lesson) }}</span><span>{{ lessonTypeLabel(lesson.subjectType) }}</span></span><span
              class="headman-requests__attendance"
              :data-status="view === 'approve' ? target(lesson) : lesson.attendanceStatus"
              :aria-label="statusText(view === 'approve' ? target(lesson) : lesson.attendanceStatus)"
            >{{ statusSymbol(view === 'approve' ? target(lesson) : lesson.attendanceStatus) }}</span>
          </li>
        </ul>
      </template>
      <section
        v-if="view === 'approve'"
        class="headman-requests__after"
      >
        <MobileIcon name="attendance" /><h2>После одобрения</h2><p>{{ selected.summary.kind === 'LATE_CHECKIN' ? 'Во всех парах заявки появится отметка «Присутствовал». Подтверждённое присутствие сохранится.' : 'Во всех парах заявки появится уважительная причина. Подтверждённое присутствие сохранится.' }}</p>
      </section>
      <section
        v-if="view === 'detail' && transferred"
        class="headman-requests__original"
      >
        <h2 class="headman-requests__section-title">
          Исходные сведения заявки
        </h2><p class="headman-requests__hint">
          Пары перенесены. Решение применяется к текущим парам выше.
        </p><p
          v-for="(lesson, index) in selected.lessons"
          :key="index"
        >
          {{ lesson.subjectName }} · {{ date(lesson.date) }} · {{ time(lesson) }}
        </p>
      </section>
      <template v-if="view === 'reject'">
        <StudentWarningBlock
          title="Заявка будет отклонена"
          message="Отметки посещаемости не изменятся. Студент увидит твою причину отказа."
        /><label class="headman-requests__reason">Причина отклонения<textarea
          v-model="reason"
          maxlength="300"
          rows="5"
          placeholder="Напиши, почему отклоняешь заявку"
          :disabled="!!pending || busy"
        /><span>{{ reason.length }} / 300</span></label>
      </template>
      <div
        v-if="selected.summary.status === 'PENDING'"
        class="headman-requests__actions"
      >
        <template v-if="view === 'detail'">
          <button
            type="button"
            class="headman-requests__primary"
            :disabled="!writable"
            @click="confirm('APPROVED')"
          >
            Одобрить
          </button><button
            type="button"
            class="headman-requests__danger"
            :disabled="!writable"
            @click="confirm('REJECTED')"
          >
            Отклонить
          </button>
        </template>
        <button
          v-else-if="view === 'approve'"
          type="button"
          class="headman-requests__primary"
          :disabled="!writable || !selected.currentAffectedLessons"
          @click="decide()"
        >
          {{ busy ? 'Сохраняем…' : `Одобрить ${affected.length} пары` }}
        </button>
        <button
          v-else
          type="button"
          class="headman-requests__danger"
          :disabled="!writable || !reason.trim()"
          @click="decide()"
        >
          {{ busy ? 'Сохраняем…' : 'Отклонить заявку' }}
        </button>
      </div>
      <p
        v-if="readOnly"
        class="headman-requests__hint"
      >
        Режим просмотра. Решения недоступны.
      </p>
    </template>
  </main>
</template>
