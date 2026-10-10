<script setup lang="ts">
import NotificationsEntryButton from '../notifications/NotificationsEntryButton.vue'
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import { academicWeekOrdinal, type AcademicWeekPolicy } from '../../domain/academic-week'
import type { HeadmanScheduleApi, HeadmanScheduleSemester } from '../schedule/headman-schedule-client'
import { readPublishedHomeworks } from '../homework/headman-published-homework'
import { moscowDate } from '../../domain/homework'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { HeadmanJournalApiError, type HeadmanJournalApi, type HeadmanJournalLesson, type HeadmanJournalReport } from '../headman-journal/headman-journal-client'
import type { HeadmanSubjectsApi, HeadmanSubject } from '../headman-subjects/headman-subjects-client'
import type { HeadmanHomeworkApi, HeadmanManagedHomework } from '../homework/headman-homework-client'
import './headman-home-screen.pcss'

const props = withDefaults(defineProps<{
  unreadCount?: number | null | undefined
  onNotifications?: (() => void) | undefined
  api: HeadmanJournalApi | null
  weekPolicyApi?: HeadmanScheduleApi | null
  groupId: number | null
  groupLabel?: string | null
  subjectsApi?: HeadmanSubjectsApi | null
  homeworkApi?: HeadmanHomeworkApi | null
  selectedDate?: string
  offline?: boolean
  roleSwitchDisabled?: boolean
  homeworkEnabled?: boolean
  logEnabled?: boolean
}>(), { unreadCount: null, onNotifications: undefined, weekPolicyApi: null, groupLabel: null, subjectsApi: null, homeworkApi: null, selectedDate: '', offline: false, roleSwitchDisabled: true, homeworkEnabled: false, logEnabled: false })
const emit = defineEmits<{
  openLesson: [lesson: HeadmanJournalLesson]
  openHomework: []
  openLog: [lesson: HeadmanJournalLesson]
  selectDate: [date: string]
  error: [cause: unknown]
  roleSwitch: []
}>()
function todayIso(): string { return moscowDate(new Date().toISOString()) ?? new Date().toISOString().slice(0, 10) }
const selectedDate = ref(props.selectedDate || todayIso())
const lessons = shallowRef<readonly HeadmanJournalLesson[]>([])
const subjects = shallowRef<readonly HeadmanSubject[]>([])
const report = shallowRef<HeadmanJournalReport | null>(null)
const homeworks = shallowRef<readonly HeadmanManagedHomework[]>([])
const selectedLessonId = ref<number | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const error = ref<string | null>(null)
const detailError = ref<string | null>(null)
const homeworkError = ref<string | null>(null)
const homeworkExpanded = ref(false)
const homeworkLoaded = ref(false)
const weekPolicy = shallowRef<AcademicWeekPolicy | null>(null)
const semesterCatalog = shallowRef<readonly HeadmanScheduleSemester[]>([])
const weekError = ref<string | null>(null)
const weekLoading = ref(false)
let weekRevision = 0
let loadRevision = 0
let detailRevision = 0
let disposed = false
const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null)
const lessonIndex = computed(() => lessons.value.findIndex((lesson) => lesson.id === selectedLessonId.value))
const subjectName = computed(() => subjects.value.find((subject) => subject.id === selectedLesson.value?.subjectId)?.name ?? 'Занятие группы')
const excused = computed(() => report.value?.entries.filter((entry) => entry.status === 'EXCUSED') ?? [])
const presentCount = computed(() => report.value?.entries.filter((entry) => entry.status === 'PRESENT').length ?? null)
const emptyCount = computed(() => report.value?.entries.filter((entry) => entry.status === 'EMPTY').length ?? null)
const geoMetric = computed(() => {
  const present = report.value?.entries.filter((entry) => entry.status === 'PRESENT') ?? []
  const knownSources = ['student_geo', 'headman', 'auto_scheduler', 'late_checkin', 'headman_excuse']
  if (!present.length || present.some((entry) => !entry.source || !knownSources.includes(entry.source))) return null
  const geoCount = present.filter((entry) => entry.source === 'student_geo').length
  return { numerator: geoCount, denominator: present.length, percent: Math.round(geoCount / present.length * 100) }
})

const ordinal = computed(() => {
  const semester = semesterCatalog.value.find((item) => item.dateFrom && item.dateTo && selectedDate.value >= item.dateFrom && selectedDate.value <= item.dateTo)
  return semester ? academicWeekOrdinal(selectedDate.value, semester.dateFrom ?? null, semester.dateTo ?? null) : null
})
async function loadWeekPolicy(): Promise<void> {
  const run = ++weekRevision; const api = props.weekPolicyApi; const date = selectedDate.value; const weekStart = weekDays.value[0]?.iso; const group = props.groupId
  weekPolicy.value = null; semesterCatalog.value = []; weekError.value = null; weekLoading.value = false
  if (!api || props.offline || group === null) { weekError.value = 'Чётность недели недоступна.'; return }
  weekLoading.value = true
  const current = () => !disposed && run === weekRevision && api === props.weekPolicyApi && weekStart === weekDays.value[0]?.iso && group === props.groupId && !props.offline
  try {
    const [policy, catalog] = await Promise.all([api.academicWeekPolicy(date), api.listSemesters()])
    if (!current()) return
    weekPolicy.value = policy; semesterCatalog.value = catalog
  } catch (cause) {
    if (!current() || cause instanceof StaleSessionGenerationError) return
    weekError.value = 'Не удалось загрузить чётность недели.'; emit('error', cause)
  } finally { if (!disposed && run === weekRevision) weekLoading.value = false }
}
const weekDays = computed(() => {
  const date = new Date(`${selectedDate.value}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() - (date.getUTCDay() + 6) % 7)
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(date)
    day.setUTCDate(day.getUTCDate() + index)
    return { iso: day.toISOString().slice(0, 10), number: day.getUTCDate(), name: ['Пн','Вт','Ср','Чт','Пт','Сб','Вс'][index] }
  })
})
function selectDate(value: string): void {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value === selectedDate.value) return
  selectedDate.value = value
  emit('selectDate', value)
}
function shiftWeek(delta: number): void {
  const date = new Date(`${selectedDate.value}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() + delta * 7)
  selectDate(date.toISOString().slice(0,10))
}
function pageLesson(delta: number): void {
  const lesson = lessons.value[lessonIndex.value + delta]
  if (lesson) selectedLessonId.value = lesson.id
}
function timeRange(lesson: HeadmanJournalLesson): string { return `${lesson.startTime?.slice(0,5) ?? '—'}–${lesson.endTime?.slice(0,5) ?? '—'}` }
function lessonStatus(lesson: HeadmanJournalLesson): string {
  return lesson.status === 'ACTIVE' ? 'текущая' : ({ PLANNED: 'предстоит', ACTIVE: 'идёт', CLOSED: 'прошла', CANCELLED: 'отменена', TRANSFERRED: 'перенесена', UNSUPPORTED: 'статус недоступен' })[lesson.status]
}
function typeLabel(value: string | null): string { return value === 'LECTURE' ? 'Лекция' : value === 'PRACTICE' ? 'Практика' : value === 'LAB' ? 'Лабораторная' : value ?? 'Тип не указан' }
function safeHomeworkLink(value: string | null): string | null {
  if (!value) return null
  try { const url = new URL(value); return ['https:', 'http:'].includes(url.protocol) ? url.href : null } catch { return null }
}
async function load(): Promise<void> {
  const revision = ++loadRevision
  detailRevision += 1
  const api = props.api
  const groupId = props.groupId
  const date = selectedDate.value
  error.value = null
  lessons.value = []
  report.value = null
  subjects.value = []
  selectedLessonId.value = null
  if (props.offline || !api || groupId === null) { loading.value = false; return }
  loading.value = true
  try {
    const [next, subjectList] = await Promise.all([
      api.listLessons(groupId, date, date),
      props.subjectsApi?.listSubjects(groupId) ?? Promise.resolve([]),
    ])
    if (disposed || revision !== loadRevision || api !== props.api || groupId !== props.groupId) return
    lessons.value = next.slice().sort((left,right) => (left.lessonNumber ?? 99) - (right.lessonNumber ?? 99))
    subjects.value = subjectList.filter((subject) => subject.groupId === groupId)
    selectedLessonId.value = next.find((lesson) => lesson.status === 'ACTIVE')?.id ?? lessons.value[0]?.id ?? null
  } catch (cause) {
    if (disposed || revision !== loadRevision || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof HeadmanJournalApiError && [401,403].includes(cause.response.status) ? 'Нет доступа к расписанию этой группы.' : cause instanceof Error ? cause.message : 'Не удалось загрузить занятия.'
    emit('error', cause)
  } finally { if (!disposed && revision === loadRevision) loading.value = false }
}
async function loadDetails(): Promise<void> {
  const revision = ++detailRevision
  const api = props.api
  const homeworkApi = props.homeworkApi
  const lesson = selectedLesson.value
  const groupId = props.groupId
  report.value = null
  homeworks.value = []
  homeworkLoaded.value = false
  detailError.value = null
  homeworkError.value = null
  homeworkExpanded.value = false
  if (!api || !lesson || groupId === null || props.offline) { detailLoading.value = false; return }
  const current = () => !disposed && revision === detailRevision && api === props.api && groupId === props.groupId && lesson.id === selectedLessonId.value && homeworkApi === props.homeworkApi && !props.offline
  detailLoading.value = true
  try {
    const next = await api.getLessonAttendance(lesson.id)
    if (!current()) return
    report.value = next
    if (homeworkApi && next.semesterId !== null) {
      try {
        const items = await readPublishedHomeworks(homeworkApi, groupId, next.semesterId, current)
        if (!current()) return
        homeworkLoaded.value = true
        homeworks.value = (items ?? []).filter((item) => item.subjectId === lesson.subjectId && item.lessonDate === lesson.date && (item.bindingMode === 'DATE' || item.lessonNumber === lesson.lessonNumber))
      } catch (cause) {
        if (!current() || cause instanceof StaleSessionGenerationError) return
        homeworkError.value = 'Не удалось загрузить домашнее задание.'
        emit('error', cause)
      }
    }
  } catch (cause) {
    if (!current() || cause instanceof StaleSessionGenerationError) return
    detailError.value = cause instanceof Error ? cause.message : 'Не удалось загрузить отметки.'
    homeworkError.value = 'Домашнее задание неизвестно: данные выбранной пары не загрузились.'
    emit('error', cause)
  } finally { if (current()) detailLoading.value = false }
}
watch(() => [props.api, props.subjectsApi, props.groupId, props.offline, selectedDate.value] as const, () => { void load() }, { immediate: true, flush: 'sync' })
watch(() => [selectedLessonId.value, props.homeworkApi] as const, () => { void loadDetails() }, { flush: 'sync' })
watch(() => props.selectedDate, (date) => { if (date) selectedDate.value = date })
watch(() => [props.weekPolicyApi, props.groupId, props.offline, weekDays.value[0]?.iso] as const, () => { void loadWeekPolicy() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { disposed = true; weekRevision += 1; loadRevision += 1; detailRevision += 1 })
</script>

<template>
  <main
    class="headman-home"
    aria-labelledby="headman-home-title"
    :aria-busy="loading"
    :data-lesson-id="selectedLessonId"
    :data-group-id="groupId"
  >
    <header class="headman-home__header notifications-role-header">
      <h1
        id="headman-home-title"
        class="headman-home__visually-hidden"
      >
        Сегодня
      </h1>
      <button
        class="headman-home__role"
        type="button"
        :disabled="offline || roleSwitchDisabled"
        aria-label="Сменить роль, активная роль: староста"
        @click="emit('roleSwitch')"
      >
        Староста<MobileIcon name="chevron-down" />
      </button>
      <NotificationsEntryButton
        v-if="onNotifications"
        :count="unreadCount ?? null"
        @click="onNotifications"
      />
    </header>
    <nav
      class="headman-home__week-pager"
      aria-label="Выбор недели"
    >
      <button
        type="button"
        aria-label="Предыдущая неделя"
        @click="shiftWeek(-1)"
      >
        <MobileIcon name="back" />
      </button>
      <output>{{ new Date((weekDays[0]?.iso ?? selectedDate) + 'T12:00:00Z').toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', timeZone: 'UTC' }) }} — {{ new Date((weekDays[6]?.iso ?? selectedDate) + 'T12:00:00Z').toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', timeZone: 'UTC' }) }}<small>{{ weekPolicy ? 'неделя ' + weekPolicy.parityNumber + (ordinal ? ' · №' + ordinal : '') : weekLoading ? 'Загружаем чётность…' : 'Чётность недоступна' }}</small></output>
      <button
        type="button"
        aria-label="Следующая неделя"
        @click="shiftWeek(1)"
      >
        <MobileIcon
          class="headman-home__next"
          name="back"
        />
      </button>
    </nav>
    <StudentWarningBlock
      v-if="weekError && !offline"
      title="Чётность недоступна"
      :message="weekError"
      action-label="Повторить"
      @action="loadWeekPolicy"
    />
    <nav
      class="headman-home__calendar"
      aria-label="Дни недели"
    >
      <button
        v-for="day in weekDays"
        :key="day.iso"
        type="button"
        :aria-label="day.iso"
        :aria-current="day.iso === selectedDate ? 'date' : undefined"
        @click="selectDate(day.iso)"
      >
        <span>{{ day.name }}</span><strong>{{ day.number }}</strong>
      </button>
    </nav>

    <StudentWarningBlock
      v-if="offline"
      title="Нет подключения"
      message="Расписание группы доступно при подключении к интернету."
    />
    <p
      v-else-if="groupId === null || !api"
      class="headman-home__state"
      role="alert"
    >
      Не удалось подключить расписание группы.
    </p>
    <p
      v-else-if="loading"
      class="headman-home__state"
      role="status"
    >
      Загружаем занятия…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      title="Расписание недоступно"
      :message="error"
      action-label="Повторить"
      @action="load"
    />
    <p
      v-else-if="!selectedLesson"
      class="headman-home__state"
      role="status"
    >
      На этот день занятий нет.
    </p>
    <template v-else>
      <nav
        class="headman-home__pager"
        aria-label="Выбор пары"
      >
        <button
          type="button"
          aria-label="Предыдущая пара"
          :disabled="lessonIndex === 0"
          @click="pageLesson(-1)"
        >
          <MobileIcon name="back" />
        </button><span>{{ selectedLesson.lessonNumber ?? '—' }} пара · {{ lessonStatus(selectedLesson) }}</span><button
          type="button"
          aria-label="Следующая пара"
          :disabled="lessonIndex === lessons.length - 1"
          @click="pageLesson(1)"
        >
          <MobileIcon
            class="headman-home__next"
            name="back"
          />
        </button>
      </nav>
      <button
        class="headman-home__lesson"
        :data-current="selectedLesson.status === 'ACTIVE'"
        type="button"
        aria-label="Открыть учёт выбранной пары"
        @click="emit('openLesson', selectedLesson)"
      >
        <span class="headman-home__time">{{ timeRange(selectedLesson) }}</span><strong>{{ subjectName }}</strong><span class="headman-home__meta"><span><MobileIcon name="room" />{{ selectedLesson.room ?? 'Аудитория не указана' }}</span><span><MobileIcon name="lesson" />{{ typeLabel(selectedLesson.lessonType) }}</span></span>
      </button>
      <StudentWarningBlock
        v-if="detailError"
        title="Отметки недоступны"
        :message="detailError"
        action-label="Повторить"
        @action="loadDetails"
      />
      <div
        v-else
        class="headman-home__metrics"
        :aria-busy="detailLoading"
        :aria-label="`Отметки выбранной пары${groupLabel ? `, ${groupLabel}` : ''}`"
      >
        <div>
          <span
            class="headman-home__circle"
            data-status="present"
            aria-label="Были"
          >+</span><strong>{{ presentCount ?? '—' }}</strong>
        </div><div>
          <span
            class="headman-home__circle"
            aria-label="Без отметки"
          >—</span><strong>{{ emptyCount ?? '—' }}</strong>
        </div><div><MobileIcon name="geo-approved" /><strong :aria-label="geoMetric ? `Геолокацией подтверждено ${geoMetric.numerator} из ${geoMetric.denominator} отметок присутствия, ${geoMetric.percent}%` : 'Доля подтверждённых геоотметок неизвестна: нет присутствующих или известного источника каждой отметки'">{{ geoMetric ? `${geoMetric.percent}%` : '—' }}</strong></div>
      </div>

      <button
        v-if="report"
        class="headman-home__excused"
        type="button"
        @click="emit('openLesson', selectedLesson)"
      >
        <span class="headman-home__excused-count"><span
          class="headman-home__circle"
          data-status="excused"
        >у</span><strong>{{ excused.length }}</strong></span><span class="headman-home__excused-list"><span
          v-for="entry in excused"
          :key="entry.userId"
        ><strong>{{ entry.displayName }}</strong><small>{{ entry.excuseReason ?? entry.excuseType ?? 'Уважительная причина' }}</small></span><span v-if="excused.length === 0">Уважительных причин нет</span></span>
      </button>
      <section
        class="headman-home__homework"
        :data-expanded="homeworkExpanded"
        aria-label="Домашнее задание пары"
      >
        <button
          class="headman-home__homework-toggle"
          type="button"
          :aria-expanded="homeworkExpanded"
          :disabled="!homeworks.length"
          @click="homeworkExpanded = !homeworkExpanded"
        >
          <MobileIcon name="homework" /><span><strong>{{ homeworkError ? 'ДЗ недоступно' : detailLoading ? 'Загружаем ДЗ…' : !homeworkLoaded ? 'ДЗ недоступно' : homeworks.length ? 'Домашнее задание' : 'ДЗ нет' }}</strong><small v-if="!homeworkExpanded && homeworks.length">{{ homeworks.map((item) => item.title).join(' · ') }}</small></span><MobileIcon
            v-if="homeworks.length"
            class="headman-home__next"
            name="back"
          />
        </button><template v-if="homeworkExpanded">
          <article
            v-for="item in homeworks"
            :key="item.id"
          >
            <h2>{{ item.title }}</h2><p v-if="item.description">
              {{ item.description }}
            </p><a
              v-if="safeHomeworkLink(item.link)"
              :href="safeHomeworkLink(item.link)!"
              target="_blank"
              rel="noopener noreferrer"
            >Материалы</a>
          </article>
        </template><p
          v-if="homeworkError"
          role="alert"
        >
          {{ homeworkError }}
        </p>
      </section>
      <div class="headman-home__actions">
        <button
          type="button"
          :data-active="selectedLesson.status === 'ACTIVE'"
          :disabled="offline || selectedLesson.status === 'CLOSED' || selectedLesson.date < todayIso()"
          @click="emit('openLesson', selectedLesson)"
        >
          {{ selectedLesson.status === 'ACTIVE' ? 'Пара открыта' : 'Управление парой' }}
        </button><button
          type="button"
          :disabled="offline || !homeworkEnabled"
          @click="emit('openHomework')"
        >
          Выдать ДЗ
        </button><button
          type="button"
          :disabled="offline || !logEnabled"
          @click="emit('openLog', selectedLesson)"
        >
          Журнал
        </button>
      </div>
    </template>
  </main>
</template>
