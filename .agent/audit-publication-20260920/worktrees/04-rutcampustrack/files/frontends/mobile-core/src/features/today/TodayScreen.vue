<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { countdownLabel, eligibilityLabel, remainingSeconds } from '../../domain/checkin'
import type { StudentSemesterSchedule, StudentToday, TodayLesson } from '../../api/types'
import chevronDown from '../../assets/chevron-down.svg'
import lessonTypeDark from '../../assets/lesson-type-dark.svg'
import lessonTypeLight from '../../assets/lesson-type-light.svg'
import roomChangeStrike from '../../assets/room-change-strike.svg'
import roomDark from '../../assets/room-dark.svg'
import roomLight from '../../assets/room-light.svg'
import MobileShell from '../../shared/components/MobileShell.vue'
import { rootRoute, type MobileBottomNavItems, type MobileRootRouteId } from '../../shared/navigation'
import attendanceTab from '../../assets/attendance-tab.svg'
import moreTab from '../../assets/more-tab.svg'
import profileTab from '../../assets/profile-tab.svg'
import scheduleTab from '../../assets/schedule-tab.svg'
import todayTabActive from '../../assets/today-tab-active.svg'
import './today-screen.pcss'

const props = defineProps<{
  today: StudentToday | null
  loading: boolean
  error: string | null
  offline: boolean
  updatedAt: string | null
  submittingLessonId: string | null
  semesterSchedule: StudentSemesterSchedule | null
  selectedDate: string
  keyboardVisible?: boolean
}>()

const emit = defineEmits<{
  checkin: [lesson: TodayLesson]
  selectDate: [date: string]
  navigate: [route: MobileRootRouteId]
}>()

const todayRoute = rootRoute('today')
const todayNavigationItems = [
  { id: 'today', label: 'Сегодня', icon: todayTabActive, route: 'today' },
  { id: 'homework', label: 'Задания', icon: scheduleTab, route: 'homework', disabled: true, disabledReason: 'Раздел пока недоступен' },
  { id: 'attendance', label: 'Учёт', accessibleLabel: 'Посещаемость', icon: attendanceTab, route: 'attendance', disabled: true, disabledReason: 'Раздел пока недоступен' },
  { id: 'more', label: 'Ещё', icon: moreTab, route: 'more', disabled: true, disabledReason: 'Раздел пока недоступен' },
  { id: 'profile', label: 'Профиль', icon: profileTab, route: 'profile', disabled: true, disabledReason: 'Раздел пока недоступен' },
] as const satisfies MobileBottomNavItems

const now = ref(Date.now())
let timer: number | null = null

onMounted(() => { timer = window.setInterval(() => { now.value = Date.now() }, 1000) })
onBeforeUnmount(() => { if (timer !== null) window.clearInterval(timer) })

const hero = computed(() => props.today?.lessons.find((lesson) => lesson.schedule.status === 'ACTIVE') ?? props.today?.lessons[0] ?? null)
const semesterDates = computed(() => [...new Set(props.semesterSchedule?.lessons.map((lesson) => lesson.date) ?? [])].sort())
const selectedLessons = computed(() => props.semesterSchedule?.lessons.filter((lesson) => lesson.date === props.selectedDate) ?? [])

function countdown(lesson: TodayLesson): number {
  return remainingSeconds(props.today?.serverNow ?? new Date(now.value).toISOString(), lesson.checkinEligibility.retryAt, now.value)
}

function stateLabel(lesson: TodayLesson): string {
  return eligibilityLabel(lesson, countdown(lesson))
}

function lessonTime(lesson: TodayLesson): string {
  return `${lesson.schedule.startsAt.slice(0, 5)}–${lesson.schedule.endsAt.slice(0, 5)}`
}

function lessonKind(lesson: TodayLesson): string {
  const labels = { LECTURE: 'Лекция', PRACTICE: 'Практика', LAB: 'Лабораторная' } as const
  return labels[lesson.schedule.subject.type]
}

function canCheckin(lesson: TodayLesson): boolean {
  const retryAllowed = countdown(lesson) === 0 && (lesson.checkinEligibility.reason === 'PENDING_CONFIRMATION' || lesson.checkinEligibility.reason === 'COOLDOWN')
  return !props.offline && (lesson.checkinEligibility.allowed || retryAllowed) && props.submittingLessonId === null
}

function isPending(lesson: TodayLesson): boolean {
  return lesson.request?.status === 'PENDING' && countdown(lesson) > 0
}

function isConfirmed(lesson: TodayLesson): boolean {
  return lesson.attendance?.status === 'PRESENT'
}

function isAccentCurrent(lesson: TodayLesson): boolean {
  return lesson.schedule.status === 'ACTIVE' && !isConfirmed(lesson)
}

function heroActionLabel(lesson: TodayLesson): string {
  if (props.submittingLessonId === lesson.schedule.id) return 'Отправляем отметку'
  if (isConfirmed(lesson)) return 'Отмечено'
  if (isPending(lesson)) return 'Отправлен запрос старосте'
  if (props.offline) return 'Отметка доступна онлайн'
  if (canCheckin(lesson)) return lesson.checkinEligibility.allowed ? 'Отметиться' : 'Отметиться повторно'
  return stateLabel(lesson)
}

function roomIcon(lesson: TodayLesson): string {
  return isAccentCurrent(lesson) ? roomDark : roomLight
}

function kindIcon(lesson: TodayLesson): string {
  return isAccentCurrent(lesson) ? lessonTypeDark : lessonTypeLight
}

function heroRoomIcon(lesson: TodayLesson): string {
  return isConfirmed(lesson) ? roomLight : roomDark
}

function heroKindIcon(lesson: TodayLesson): string {
  return isConfirmed(lesson) ? lessonTypeLight : lessonTypeDark
}

function semesterDate(date: string): string {
  return new Date(`${date}T12:00:00Z`).toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'long' })
}
</script>

<template>
  <MobileShell
    :route="todayRoute"
    :nav-items="todayNavigationItems"
    active-id="today"
    :keyboard-visible="keyboardVisible"
    @navigate="emit('navigate', $event)"
  >
    <main
      class="today-shell"
      aria-labelledby="today-title"
    >
      <header class="today-topbar">
        <button
          class="today-role"
          type="button"
          disabled
          aria-label="Активная роль: студент"
        >
          Студент
          <img
            :src="chevronDown"
            alt=""
            aria-hidden="true"
          >
        </button>
        <h1
          id="today-title"
          class="today-visually-hidden"
        >
          Сегодня
        </h1>
        <p
          v-if="offline"
          class="today-offline"
          role="status"
        >
          Офлайн · данные обновлены {{ updatedAt ? new Date(updatedAt).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' }) : 'ранее' }}
        </p>
      </header>

      <section
        v-if="loading"
        class="today-state"
        aria-live="polite"
      >
        <span
          class="today-state__spinner"
          aria-hidden="true"
        />
        Загружаем сегодняшнее расписание…
      </section>
      <section
        v-else-if="error"
        class="today-state today-state--error"
        role="alert"
      >
        <h2>Не удалось получить данные</h2>
        <p>{{ error }}</p>
      </section>
      <section
        v-else-if="!hero"
        class="today-state"
      >
        <h2>На сегодня пар нет</h2>
        <p>Следи за расписанием, когда появится следующая пара.</p>
      </section>
      <template v-else>
        <section
          class="today-hero"
          :data-state="isPending(hero) ? 'pending' : isConfirmed(hero) ? 'confirmed' : 'default'"
          aria-label="Текущая или ближайшая пара"
        >
          <p class="today-hero__time">
            {{ lessonTime(hero) }}
          </p>
          <h2>{{ hero.schedule.subject.name }}</h2>
          <div class="today-hero__metadata">
            <p>
              <img
                :src="heroRoomIcon(hero)"
                alt=""
                aria-hidden="true"
              >
              {{ hero.schedule.room.current ?? 'Аудитория уточняется' }}
            </p>
            <p>
              <img
                :src="heroKindIcon(hero)"
                alt=""
                aria-hidden="true"
              >
              {{ lessonKind(hero) }}
            </p>
          </div>
          <button
            class="today-hero__action"
            type="button"
            :disabled="!canCheckin(hero)"
            @click="emit('checkin', hero)"
          >
            <span
              v-if="submittingLessonId === hero.schedule.id"
              class="today-state__spinner"
              aria-hidden="true"
            />
            {{ heroActionLabel(hero) }}
          </button>
          <p
            v-if="isPending(hero)"
            class="today-hero__retry"
            role="status"
          >
            Отметиться повторно через: {{ countdownLabel(countdown(hero)) }}
          </p>
        </section>

        <section
          class="today-list"
          aria-label="Пары на сегодня"
        >
          <h2>Пары на сегодня</h2>
          <ol>
            <li
              v-for="lesson in today?.lessons"
              :key="lesson.schedule.id"
              class="today-row"
              :data-current="isAccentCurrent(lesson)"
              :aria-label="`${lessonTime(lesson)}. ${lesson.schedule.subject.name}. ${stateLabel(lesson)}`"
            >
              <p class="today-row__time">
                <span>{{ lesson.schedule.startsAt.slice(0, 5) }}</span>
                <span>{{ lesson.schedule.endsAt.slice(0, 5) }}</span>
              </p>
              <article class="today-card">
                <h3>{{ lesson.schedule.subject.name }}</h3>
                <p class="today-card__meta">
                  <img
                    :src="roomIcon(lesson)"
                    alt=""
                    aria-hidden="true"
                  >
                  <span :class="{ 'today-card__room--changed': lesson.schedule.room.changeState === 'CHANGED' }">{{ lesson.schedule.room.current ?? 'Аудитория уточняется' }}</span>
                  <span
                    v-if="lesson.schedule.room.previous"
                    class="today-card__room-change"
                  >
                    <img
                      :src="roomChangeStrike"
                      alt=""
                      aria-hidden="true"
                    >
                    <s>{{ lesson.schedule.room.previous }}</s>
                  </span>
                </p>
                <p class="today-card__meta">
                  <img
                    :src="kindIcon(lesson)"
                    alt=""
                    aria-hidden="true"
                  >
                  {{ lessonKind(lesson) }}
                </p>
                <span
                  v-if="isConfirmed(lesson)"
                  class="today-card__present"
                  aria-label="Отметка подтверждена"
                >+</span>
              </article>
            </li>
          </ol>
        </section>

        <details
          v-if="semesterSchedule"
          class="today-semester"
        >
          <summary>Расписание семестра</summary>
          <label>
            Дата
            <select
              :value="selectedDate"
              @change="emit('selectDate', ($event.target as HTMLSelectElement).value)"
            >
              <option
                v-for="date in semesterDates"
                :key="date"
                :value="date"
              >{{ semesterDate(date) }}</option>
            </select>
          </label>
          <p
            v-if="selectedLessons.length === 0"
            class="today-semester__empty"
          >
            На выбранную дату пар нет.
          </p>
          <ul v-else>
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
