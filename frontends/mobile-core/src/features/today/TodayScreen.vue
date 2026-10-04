<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { countdownLabel, eligibilityLabel, isPendingCheckin, remainingSeconds } from '../../domain/checkin'
import type { StudentSemesterSchedule, StudentToday, TodayLesson } from '../../api/types'
import type { RequestKind, RequestLessonOption } from '../requests/types'
import MobileShell from '../../shared/components/MobileShell.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentLessonCard from '../../shared/components/StudentLessonCard.vue'
import { rootRoute, type MobileBottomNavItems, type MobileNavigationStack, type MobileRootRouteId, type MobileRoute } from '../../shared/navigation'
import type { MobileHostAdapter } from '../../shared/host'
import { createStudentNavigationItems } from '../../shared/mobile-navigation-items'
import './today-screen.pcss'

const props = withDefaults(defineProps<{
  today: StudentToday | null
  loading: boolean
  error: string | null
  offline: boolean
  updatedAt: string | null
  submittingLessonId: string | null
  semesterSchedule: StudentSemesterSchedule | null
  selectedDate: string
  navItems?: MobileBottomNavItems
  route?: MobileRoute | null
  navigation?: MobileNavigationStack | null
  activeId?: MobileRootRouteId
  keyboardVisible?: boolean
  host?: MobileHostAdapter | null
  readOnly?: boolean
  roleSwitchDisabled?: boolean
  acquiringLessonId?: string | null
  checkinError?: string | null
  expandedLessonId?: string | null
  requestOptions?: readonly RequestLessonOption[]
  optionsLoading?: boolean
  optionsError?: string | null
}>(), {
  navItems: undefined as never, route: null, navigation: null, activeId: 'today', keyboardVisible: undefined as never,
  host: null, readOnly: false, roleSwitchDisabled: true, acquiringLessonId: null, checkinError: null,
  expandedLessonId: null, requestOptions: () => [], optionsLoading: false, optionsError: null,
})
const emit = defineEmits<{
  checkin: [lesson: TodayLesson]
  selectDate: [date: string]
  navigate: [route: MobileRootRouteId]
  roleSwitch: []
  retry: []
  retryOptions: []
  toggleActions: [lesson: TodayLesson]
  openRequest: [lesson: TodayLesson, kind: RequestKind]
  back: []
}>()
const todayRoute = rootRoute('today')
const navigationItems = computed(() => props.navItems ?? createStudentNavigationItems())
const shellOptionalProps = computed(() => props.keyboardVisible === undefined ? {} : { keyboardVisible: props.keyboardVisible })
const task = computed(() => props.route?.kind !== undefined && props.route.kind !== 'root')
const now = ref(Date.now())
let timer: number | null = null
onMounted(() => { timer = window.setInterval(() => { now.value = Date.now() }, 1000) })
onBeforeUnmount(() => { if (timer !== null) window.clearInterval(timer) })
const hero = computed(() => props.today?.lessons.find((lesson) => lesson.schedule.status === 'ACTIVE')
  ?? props.today?.lessons.find((lesson) => lesson.schedule.status === 'PLANNED') ?? null)
const hasLessons = computed(() => (props.today?.lessons.length ?? 0) > 0)
const semesterDates = computed(() => [...new Set(props.semesterSchedule?.lessons.map((lesson) => lesson.date) ?? [])].sort())
const selectedLessons = computed(() => props.semesterSchedule?.lessons.filter((lesson) => lesson.date === props.selectedDate) ?? [])
const busy = computed(() => props.submittingLessonId !== null || props.acquiringLessonId !== null)
function countdown(lesson: TodayLesson): number { return remainingSeconds(props.today?.serverNow ?? new Date(now.value).toISOString(), lesson.checkinEligibility.retryAt, now.value) }
function option(lesson: TodayLesson): RequestLessonOption | undefined { return props.requestOptions.find((value) => value.lesson?.id === lesson.schedule.id) }
function pendingRequest(lesson: TodayLesson): boolean { return isPendingCheckin(lesson) || (option(lesson)?.pendingRequests?.length ?? 0) > 0 }
function isConfirmed(lesson: TodayLesson): boolean { return lesson.attendance?.status === 'PRESENT' }
function isCurrent(lesson: TodayLesson): boolean { return lesson.schedule.status === 'ACTIVE' && !isConfirmed(lesson) }
function canCheckin(lesson: TodayLesson): boolean {
  if (props.offline || props.readOnly || busy.value || isConfirmed(lesson)) return false
  const pending = isPendingCheckin(lesson)
  if (pending && lesson.request?.origin !== 'AUTO_GEO_FAILURE') return false
  if (option(lesson)?.pendingRequests?.some((request) => request.origin !== 'AUTO_GEO_FAILURE')) return false
  return lesson.checkinEligibility.allowed || (lesson.checkinEligibility.reason === 'COOLDOWN'
    && lesson.checkinEligibility.retryAt !== null && countdown(lesson) === 0)
}
function heroActionLabel(lesson: TodayLesson): string {
  if (props.acquiringLessonId === lesson.schedule.id) return 'Проверяем геолокацию…'
  if (props.submittingLessonId === lesson.schedule.id) return 'Отправляем отметку…'
  if (isConfirmed(lesson)) return 'Отмечено'
  if (props.offline) return 'Отметка доступна онлайн'
  if (props.readOnly) return 'Доступен только просмотр'
  if (canCheckin(lesson)) return isPendingCheckin(lesson) || lesson.checkinEligibility.reason === 'COOLDOWN' ? 'Отметиться повторно' : 'Отметиться'
  if (isPendingCheckin(lesson)) return 'Отправлен запрос старосте'
  return eligibilityLabel(lesson, countdown(lesson))
}
function retryText(lesson: TodayLesson): string {
  if (lesson.checkinEligibility.reason === 'COOLDOWN' && lesson.checkinEligibility.retryAt) {
    return countdown(lesson) > 0 ? `Отметиться повторно через: ${countdownLabel(countdown(lesson))}` : 'Можешь повторить геопроверку'
  }
  return 'Запрос на рассмотрении у старосты'
}
function canRequest(lesson: TodayLesson, kind: RequestKind): boolean {
  if (props.offline || props.readOnly || props.optionsLoading || props.optionsError || pendingRequest(lesson)) return false
  return kind === 'EXCUSE' ? option(lesson)?.excuseEligible === true : option(lesson)?.lateCheckinEligible === true
}
function requestMessage(lesson: TodayLesson): string | null {
  return lesson.attendance?.status === 'ABSENT' && pendingRequest(lesson) ? 'Запрос отправлен' : null
}
function semesterDate(date: string): string { return new Date(`${date}T12:00:00Z`).toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'long' }) }
function lessonKind(lesson: TodayLesson): string { return { LECTURE: 'Лекция', PRACTICE: 'Практика', LAB: 'Лабораторная' }[lesson.schedule.subject.type] }
</script>

<template>
  <MobileShell
    custom-back
    :route="route ?? todayRoute"
    :navigation="navigation"
    :nav-items="navigationItems"
    :active-id="activeId"
    v-bind="shellOptionalProps"
    :host="host"
    @navigate="emit('navigate', $event)"
  >
    <template #back />
    <main
      class="today-shell"
      aria-labelledby="today-title"
    >
      <header
        v-if="task"
        class="today-task-header"
      >
        <button
          class="today-back"
          type="button"
          aria-label="Вернуться на сегодня"
          @click="emit('back')"
        >
          <MobileIcon name="back" />
        </button>
        <h1 id="today-title">
          Запрос на отметку
        </h1>
      </header>
      <header
        v-else
        class="today-topbar"
      >
        <button
          class="today-role"
          type="button"
          :disabled="offline || roleSwitchDisabled"
          aria-label="Сменить роль, активная роль: студент"
          @click="emit('roleSwitch')"
        >
          Студент<MobileIcon name="chevron-down" />
        </button>
        <h1
          id="today-title"
          class="today-visually-hidden"
        >
          Сегодня
        </h1>
      </header>
      <p
        v-if="offline"
        class="today-notice"
        role="status"
      >
        Офлайн · {{ updatedAt ? `обновлено ${new Date(updatedAt).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })}` : 'сохранённых данных пока нет' }}. Действия доступны после подключения.
      </p>
      <p
        v-else-if="readOnly"
        class="today-notice"
        role="status"
      >
        Доступен только просмотр. Отметка и отправка заявок недоступны.
      </p>
      <section
        v-if="loading"
        class="today-loading"
        aria-busy="true"
        aria-label="Загружаем сегодняшнее расписание"
      >
        <span
          class="today-visually-hidden"
          role="status"
        >Загружаем сегодняшнее расписание…</span>
        <div
          class="today-skeleton today-skeleton--hero"
          aria-hidden="true"
        >
          <i /><i /><i /><i />
        </div>
        <div
          class="today-skeleton today-skeleton--heading"
          aria-hidden="true"
        />
        <div
          v-for="number in 3"
          :key="number"
          class="today-skeleton-row"
          aria-hidden="true"
        >
          <i /><div class="today-skeleton">
            <i /><i /><i />
          </div>
        </div>
      </section>
      <section
        v-else-if="error && !hasLessons"
        class="today-state today-state--error"
        role="alert"
      >
        <MobileIcon name="warning" /><h2>Не удалось получить данные</h2><p>{{ error }}</p>
        <button
          class="today-secondary-action"
          type="button"
          :disabled="offline"
          @click="emit('retry')"
        >
          Повторить
        </button>
      </section>
      <section
        v-else-if="!hasLessons"
        class="today-state"
        role="status"
      >
        <MobileIcon name="calendar" /><h2>{{ offline ? 'Нет сохранённого расписания' : 'На сегодня пар нет' }}</h2>
        <p>{{ offline ? 'Подключись к интернету, чтобы загрузить сегодняшние пары.' : 'Когда появится следующая пара, ты увидишь её здесь.' }}</p>
        <button
          v-if="!offline"
          class="today-secondary-action"
          type="button"
          @click="emit('retry')"
        >
          Обновить
        </button>
      </section>
      <template v-else>
        <p
          v-if="error"
          class="today-inline-error"
          role="alert"
        >
          {{ error }}<button
            type="button"
            @click="emit('retry')"
          >
            Обновить
          </button>
        </p>
        <section
          v-if="hero"
          class="today-hero"
          :data-state="isConfirmed(hero) ? 'confirmed' : isPendingCheckin(hero) ? 'pending' : 'default'"
          :aria-busy="busy"
          aria-label="Текущая или ближайшая пара"
        >
          <p class="today-hero__time">
            {{ hero.schedule.startsAt.slice(0, 5) }}–{{ hero.schedule.endsAt.slice(0, 5) }}
          </p>
          <h2>{{ hero.schedule.subject.name }}</h2>
          <div class="today-hero__metadata">
            <p><MobileIcon name="room" /><span>{{ hero.schedule.room.current ?? 'Аудитория уточняется' }}</span></p><p><MobileIcon name="lesson" /><span>{{ lessonKind(hero) }}</span></p>
          </div>
          <button
            class="today-hero__action"
            type="button"
            :disabled="!canCheckin(hero)"
            @click="emit('checkin', hero)"
          >
            <span
              v-if="busy"
              class="today-state__spinner"
              aria-hidden="true"
            />{{ heroActionLabel(hero) }}
          </button>
          <p
            v-if="isPendingCheckin(hero) || hero.checkinEligibility.reason === 'COOLDOWN'"
            class="today-hero__retry"
            role="status"
          >
            {{ retryText(hero) }}
          </p>
        </section>
        <section
          v-else
          class="today-state today-state--ended"
          role="status"
        >
          <h2>Пары на сегодня закончились</h2><p>Отметки и доступные действия — в списке ниже.</p>
        </section>
        <p
          v-if="checkinError"
          class="today-inline-error"
          role="alert"
        >
          {{ checkinError }}
        </p>
        <section
          class="today-list"
          aria-label="Пары на сегодня"
        >
          <h2>Пары на сегодня</h2>
          <ol>
            <li
              v-for="lesson in today?.lessons"
              :key="lesson.schedule.id"
            >
              <StudentLessonCard
                :lesson="lesson"
                :current="isCurrent(lesson)"
                :pending="pendingRequest(lesson)"
                :request-message="requestMessage(lesson)"
                :expanded="expandedLessonId === lesson.schedule.id"
                :interactive="lesson.attendance?.status === 'ABSENT' && lesson.schedule.status !== 'CANCELLED'"
                @activate="emit('toggleActions', lesson)"
              >
                <button
                  type="button"
                  :disabled="!canRequest(lesson, 'EXCUSE')"
                  @click="emit('openRequest', lesson, 'EXCUSE')"
                >
                  Пропускаю по уважительной
                </button>
                <button
                  type="button"
                  :disabled="!canRequest(lesson, 'LATE_CHECKIN')"
                  @click="emit('openRequest', lesson, 'LATE_CHECKIN')"
                >
                  Забыл отметиться
                </button>
                <p v-if="pendingRequest(lesson)">
                  Запрос уже на рассмотрении.
                </p>
                <p
                  v-else-if="optionsLoading"
                  role="status"
                >
                  Загружаем доступные действия…
                </p>
                <p
                  v-else-if="optionsError"
                  role="alert"
                >
                  {{ optionsError }}<button
                    type="button"
                    @click="emit('retryOptions')"
                  >
                    Повторить
                  </button>
                </p>
                <p v-else-if="!canRequest(lesson, 'EXCUSE') && !canRequest(lesson, 'LATE_CHECKIN')">
                  {{ offline ? 'Заявки доступны онлайн.' : readOnly ? 'Доступен только просмотр.' : 'Подать заявку на эту пару сейчас нельзя.' }}
                </p>
              </StudentLessonCard>
            </li>
          </ol>
        </section>
        <details
          v-if="semesterSchedule"
          class="today-semester"
        >
          <summary>Расписание семестра</summary><label>Дата<select
            :value="selectedDate"
            @change="emit('selectDate', ($event.target as HTMLSelectElement).value)"
          ><option
            v-for="date in semesterDates"
            :key="date"
            :value="date"
          >{{ semesterDate(date) }}</option></select></label><p v-if="selectedLessons.length === 0">
            На выбранную дату пар нет.
          </p><ul v-else>
            <li
              v-for="lesson in selectedLessons"
              :key="lesson.id"
            >
              {{ lesson.startsAt.slice(0, 5) }}–{{ lesson.endsAt.slice(0, 5) }} · {{ lesson.subject.name }}
            </li>
          </ul>
        </details>
      </template>
    </main>
  </MobileShell>
</template>
