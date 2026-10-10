<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, shallowRef, watch } from 'vue'
import HeadmanStatsSelect from '../headman-stats/HeadmanStatsSelect.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import TeacherLessonCard from './TeacherLessonCard.vue'
import { TeacherAttendanceFeed, type TeacherAttendanceFeedState } from './teacher-attendance-feed'
import { teacherLessonTypeLabel } from './teacher-ui-format'
import { TeacherApiError, teacherJournalTypes, type TeacherApi, type TeacherDayLesson, type TeacherJournalQuery, type TeacherStatsSubjectOption } from './teacher-client'
import './teacher-screen.pcss'
import './teacher-attendance-screen.pcss'

const props = withDefaults(defineProps<{ api: TeacherApi | null; semesterId: number | null; initialQuery?: TeacherJournalQuery | null; active?: boolean }>(), { initialQuery: null, active: true })
const emit = defineEmits<{ 'change-context': [query: TeacherJournalQuery]; 'open-lesson': [lessonId: number, query: TeacherJournalQuery]; 'open-journal': [query: TeacherJournalQuery]; error: [cause: unknown] }>()
const semester = ref<{ id: number; dateFrom: string; dateTo: string } | null>(null)
const groups = ref<readonly { id: number; name: string }[]>([])
const subjectOptions = ref<readonly TeacherStatsSubjectOption[]>([])
const selectedGroupId = ref<number | null>(null)
const selectedSubjectId = ref<number | null>(null)
const selectedTypes = ref<string[]>([])
const dateFrom = ref('')
const dateTo = ref('')
const loading = ref(false)
const error = ref<string | null>(null)
const errorSeverity = ref<'warning' | 'error'>('error')
const screen = ref<HTMLElement | null>(null)
const period = ref<HTMLElement | null>(null)
const sentinel = ref<HTMLElement | null>(null)
const showTop = ref(false)
const feedState = shallowRef<TeacherAttendanceFeedState>({ lessons: [], nextPage: 0, loading: false, hasMore: false, error: null })
const feed = new TeacherAttendanceFeed(value => { feedState.value = value })
let revision = 0
let disposed = false
let mounted = false
let cached = false
let savedScroll = 0
let frame: number | null = null
let observer: IntersectionObserver | null = null
let restoring = false
let suspended = false

const subjects = computed(() => {
  const merged = new Map<number, TeacherStatsSubjectOption>()
  for (const option of subjectOptions.value) {
    if (option.groupId !== selectedGroupId.value) continue
    const old = merged.get(option.subjectId)
    merged.set(option.subjectId, old ? { ...old, lessonTypes: [...new Set([...old.lessonTypes, ...option.lessonTypes])] } : option)
  }
  return [...merged.values()].sort((a, b) => a.subjectName.localeCompare(b.subjectName, 'ru'))
})
const types = computed(() => [...new Set(subjects.value.find(item => item.subjectId === selectedSubjectId.value)?.lessonTypes ?? [])])
const periodError = computed(() => {
  if (!semester.value) return null
  if (!isDate(dateFrom.value) || !isDate(dateTo.value)) return 'Укажи обе даты.'
  if (dateFrom.value > dateTo.value) return 'Начало периода позже окончания.'
  if (dateFrom.value < semester.value.dateFrom || dateTo.value > semester.value.dateTo) return 'Выбери период внутри семестра.'
  return null
})
const journalContext = computed<TeacherJournalQuery | null>(() => {
  if (loading.value || error.value || !props.api || !props.semesterId || !groups.value.some(group => group.id === selectedGroupId.value) || !subjects.value.some(subject => subject.subjectId === selectedSubjectId.value) || selectedTypes.value.some(type => !types.value.includes(type)) || periodError.value) return null
  return { semesterId: props.semesterId, groupId: selectedGroupId.value!, subjectId: selectedSubjectId.value!, lessonTypes: [...selectedTypes.value], dateFrom: dateFrom.value, dateTo: dateTo.value, page: 0, pageSize: 100 }
})
const query = computed(() => selectedTypes.value.length ? journalContext.value : null)
const days = computed(() => {
  const grouped = new Map<string, TeacherDayLesson[]>()
  for (const lesson of feedState.value.lessons) {
    const day = grouped.get(lesson.date) ?? []
    day.push(lesson)
    grouped.set(lesson.date, day)
  }
  return [...grouped].map(([date, lessons]) => ({ date, lessons }))
})
const feedError = computed(() => {
  const cause = feedState.value.error
  return cause && !(cause instanceof StaleSessionGenerationError)
    ? cause instanceof TeacherApiError ? cause.message : 'Не удалось загрузить занятия. Попробуй ещё раз.'
    : null
})

watch(query, value => {
  const api = props.api
  feed.reset(value, api ? request => api.journal(request) : null)
  if (value) { emit('change-context', value); if (props.active && !cached) void loadMore() }
})
watch(() => [props.api, props.semesterId] as const, () => { void loadContext() }, { immediate: true })
watch([period, sentinel, feedState], () => { void nextTick(connectViewport) }, { flush: 'post' })
watch(() => props.active, active => { if (active) void resume(); else suspend() }, { flush: 'sync' })
onMounted(() => { mounted = true; void resume() })
onActivated(() => { cached = false; void resume() })
onDeactivated(() => { suspend(); cached = true })
onBeforeUnmount(() => { disposed = true; revision += 1; feed.reset(null, null); disconnectViewport() })

function warningSeverity(cause: unknown): 'warning' | 'error' {
  return cause instanceof TeacherApiError && cause.response.status === 503 ? 'warning' : 'error'
}
function current(run: number): boolean { return !disposed && run === revision }
async function loadContext(): Promise<void> {
  const api = props.api
  const semesterId = props.semesterId
  const run = ++revision
  loading.value = true
  semester.value = null; groups.value = []; subjectOptions.value = []; selectedGroupId.value = null; selectedSubjectId.value = null; selectedTypes.value = []; error.value = null
  feed.reset(null, null)
  if (!api || !semesterId) { loading.value = false; return }
  try {
    const active = await api.semester()
    if (!current(run)) return
    if (active.id !== semesterId) throw new Error('Семестр изменился. Обнови экран.')
    const data = await api.catalog(semesterId)
    if (!current(run)) return
    semester.value = active
    groups.value = [...data.groups].sort((a, b) => a.name.localeCompare(b.name, 'ru'))
    subjectOptions.value = data.subjectOptions
    const initial = props.initialQuery?.semesterId === semesterId ? props.initialQuery : null
    selectedGroupId.value = groups.value.some(group => group.id === initial?.groupId) ? initial!.groupId : null
    selectedSubjectId.value = selectedGroupId.value !== null && subjects.value.some(subject => subject.subjectId === initial?.subjectId) ? initial!.subjectId : null
    selectedTypes.value = initial && selectedSubjectId.value !== null ? teacherJournalTypes(initial).filter(type => types.value.includes(type)).slice(0, 3) : []
    dateFrom.value = initial?.dateFrom ?? active.dateFrom
    dateTo.value = initial?.dateTo ?? active.dateTo
  } catch (cause) {
    if (!current(run) || cause instanceof StaleSessionGenerationError) return
    errorSeverity.value = warningSeverity(cause)
    error.value = cause instanceof Error ? cause.message : 'Не удалось получить группы и предметы.'
    emit('error', cause)
  } finally { if (current(run)) loading.value = false }
}
function changeGroup(value: string): void {
  selectedGroupId.value = groups.value.find(group => String(group.id) === value)?.id ?? null
  selectedSubjectId.value = null
  selectedTypes.value = []
}
function changeSubject(value: string): void {
  selectedSubjectId.value = subjects.value.find(subject => String(subject.subjectId) === value)?.subjectId ?? null
  selectedTypes.value = selectedSubjectId.value !== null ? types.value.slice(0, 3) : []
}
function toggleType(type: string): void { selectedTypes.value = selectedTypes.value.includes(type) ? selectedTypes.value.filter(value => value !== type) : [...selectedTypes.value, type].slice(0, 3) }
function rememberScroll(): void { if (typeof window !== 'undefined') savedScroll = window.scrollY }
function openLesson(id: number): void { if (query.value) { suspend(); emit('open-lesson', id, query.value) } }
function openJournal(): void { if (journalContext.value) { suspend(); emit('open-journal', journalContext.value) } }
async function loadMore(): Promise<void> {
  if (disposed || cached || !props.active || !query.value || feedState.value.loading || !feedState.value.hasMore) return
  const context = query.value
  await feed.loadNext()
  if (disposed || query.value !== context) return
  const cause = feedState.value.error
  if (cause && !(cause instanceof StaleSessionGenerationError)) emit('error', cause)
}
function suspend(): void { if (!cached && !suspended) rememberScroll(); suspended = true; disconnectViewport() }
async function resume(): Promise<void> {
  if (!mounted || disposed || !props.active || cached || typeof window === 'undefined') return
  restoring = true
  await nextTick()
  if (!disposed && props.active && !cached) window.scrollTo({ top: savedScroll, behavior: 'instant' })
  restoring = false
  suspended = false
  connectViewport()
  if (feedState.value.nextPage === 0 && !feedState.value.loading && !feedState.value.error) void loadMore()
}
function disconnectViewport(): void {
  observer?.disconnect(); observer = null
  if (typeof window !== 'undefined') {
    window.removeEventListener('scroll', scheduleViewport)
    window.removeEventListener('resize', scheduleViewport)
    if (frame !== null) window.cancelAnimationFrame(frame)
  }
  frame = null
  showTop.value = false
}
function connectViewport(): void {
  disconnectViewport()
  if (!mounted || disposed || cached || suspended || !props.active || typeof window === 'undefined') return
  window.addEventListener('scroll', scheduleViewport, { passive: true })
  window.addEventListener('resize', scheduleViewport, { passive: true })
  if (typeof IntersectionObserver !== 'undefined' && sentinel.value instanceof Element) {
    observer = new IntersectionObserver(scheduleViewport)
    observer.observe(sentinel.value)
  }
  scheduleViewport()
}
function scheduleViewport(): void {
  if (frame !== null || typeof window === 'undefined' || disposed || cached || suspended || !props.active) return
  frame = window.requestAnimationFrame(() => { frame = null; checkViewport() })
}
function checkViewport(): void {
  if (disposed || cached || suspended || !props.active || restoring) return
  const periodRect = period.value?.getBoundingClientRect?.()
  showTop.value = Boolean(periodRect && periodRect.bottom <= 0)
  const edge = sentinel.value?.getBoundingClientRect?.()
  if (edge && edge.top <= window.innerHeight && edge.bottom >= 0 && !feedState.value.loading && !feedState.value.error && feedState.value.hasMore) void loadMore()
}
function goTop(): void {
  savedScroll = 0
  window.scrollTo({ top: 0, behavior: 'instant' })
  screen.value?.querySelector<HTMLElement>('#teacher-attendance-title')?.focus({ preventScroll: true })
  scheduleViewport()
}
function dayLabel(date: string): string { return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', weekday: 'long' }).format(new Date(`${date}T12:00:00`)) }
function isDate(value: string): boolean { if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false; const date = new Date(`${value}T00:00:00Z`); return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value }
</script>

<template>
  <main
    ref="screen"
    class="teacher-screen teacher-attendance"
    aria-labelledby="teacher-attendance-title"
    :aria-busy="loading"
  >
    <header class="teacher-screen__header teacher-attendance__header">
      <h1
        id="teacher-attendance-title"
        tabindex="-1"
      >
        Посещаемость
      </h1>
      <button
        class="teacher-screen__icon-button"
        type="button"
        aria-label="Открыть журнал и выгрузку"
        data-teacher-action="journal-open"
        :disabled="!journalContext"
        @click="openJournal"
      >
        <MobileIcon name="download" />
      </button>
    </header>
    <p
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем группы и предметы…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      title="Посещаемость недоступна"
      :message="error"
      :severity="errorSeverity"
      action-label="Повторить"
      @action="loadContext"
    />
    <p
      v-else-if="!groups.length"
      class="teacher-screen__state"
      role="status"
    >
      Доступных групп пока нет.
    </p>
    <template v-else>
      <section
        class="teacher-attendance__filters"
        aria-label="Контекст посещаемости"
      >
        <div class="teacher-attendance__field">
          <span>Группа</span>
          <HeadmanStatsSelect
            label="Группа"
            placeholder="Выбери группу"
            :model-value="String(selectedGroupId ?? '')"
            :options="groups.map(group => ({ value: String(group.id), label: group.name }))"
            @update:model-value="changeGroup"
          />
        </div>
        <div
          v-if="selectedGroupId !== null"
          class="teacher-attendance__field"
        >
          <span>Предмет</span>
          <HeadmanStatsSelect
            label="Предмет"
            placeholder="Выбери предмет"
            :model-value="String(selectedSubjectId ?? '')"
            :options="subjects.map(subject => ({ value: String(subject.subjectId), label: subject.subjectName }))"
            :disabled="!subjects.length"
            @update:model-value="changeSubject"
          />
        </div>
        <template v-if="selectedSubjectId !== null">
          <fieldset class="teacher-attendance__types">
            <legend>Типы занятий</legend>
            <div class="teacher-attendance__type-row">
              <button
                v-for="type in types"
                :key="type"
                class="teacher-attendance__type"
                type="button"
                :aria-pressed="selectedTypes.includes(type)"
                :disabled="!selectedTypes.includes(type) && selectedTypes.length >= 3"
                @click="toggleType(type)"
              >
                {{ teacherLessonTypeLabel(type) }}
              </button>
            </div>
          </fieldset>
          <p
            v-if="!selectedTypes.length"
            class="teacher-attendance__field-error"
            role="status"
          >
            Выбери хотя бы один тип занятия.
          </p>
          <div
            id="teacher-attendance-period"
            ref="period"
            class="teacher-attendance__dates"
          >
            <label class="teacher-attendance__field"><span>С</span><input
              v-model="dateFrom"
              class="teacher-screen__date"
              type="date"
              :min="semester?.dateFrom"
              :max="semester?.dateTo"
              :aria-invalid="Boolean(periodError)"
              :aria-describedby="periodError ? 'teacher-period-error' : undefined"
            ></label>
            <label class="teacher-attendance__field"><span>По</span><input
              v-model="dateTo"
              class="teacher-screen__date"
              type="date"
              :min="semester?.dateFrom"
              :max="semester?.dateTo"
              :aria-invalid="Boolean(periodError)"
              :aria-describedby="periodError ? 'teacher-period-error' : undefined"
            ></label>
          </div>
          <p
            v-if="periodError"
            id="teacher-period-error"
            class="teacher-attendance__field-error"
            role="alert"
          >
            {{ periodError }}
          </p>
        </template>
      </section>
      <section
        v-if="query"
        class="teacher-attendance__feed"
        aria-label="Пары"
        :aria-busy="feedState.loading"
      >
        <section
          v-for="day in days"
          :key="day.date"
          class="teacher-attendance__day"
          :aria-label="dayLabel(day.date)"
        >
          <h2 class="teacher-attendance__day-title">
            {{ dayLabel(day.date) }}
          </h2>
          <TeacherLessonCard
            v-for="lesson in day.lessons"
            :key="lesson.id"
            :lesson="lesson"
            attendance
            @open="openLesson"
          />
        </section>
        <div
          ref="sentinel"
          class="teacher-attendance__feed-state"
        >
          <p
            v-if="feedState.loading"
            role="status"
          >
            Загружаем занятия…
          </p>
          <StudentWarningBlock
            v-else-if="feedError"
            title="Занятия недоступны"
            :message="feedError"
            :severity="warningSeverity(feedState.error)"
            action-label="Повторить загрузку"
            :action-disabled="feedState.loading"
            @action="loadMore"
          />
          <p
            v-else-if="!feedState.lessons.length && !feedState.hasMore"
            role="status"
          >
            За выбранный период занятий нет.
          </p>
          <p
            v-else-if="!feedState.hasMore"
            role="status"
          >
            Все занятия за период показаны.
          </p>
        </div>
      </section>
    </template>
    <button
      v-if="showTop && active"
      class="teacher-attendance__top"
      type="button"
      data-teacher-action="feed-top"
      @click="goTop"
    >
      <MobileIcon
        name="chevron-down"
        class="teacher-attendance__up"
      />
      Наверх
    </button>
  </main>
</template>
