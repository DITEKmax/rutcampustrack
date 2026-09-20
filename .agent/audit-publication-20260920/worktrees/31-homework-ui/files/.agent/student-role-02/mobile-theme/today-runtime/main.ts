import '@fontsource-variable/onest'
import { createApp, defineComponent, h } from 'vue'
import type { StudentToday, TodayLesson } from '../../../../frontends/mobile-core/src/api/types'
import TodayScreen from '../../../../frontends/mobile-core/src/features/today/TodayScreen.vue'
import './fixture.pcss'

const requestedRootFont = Number(new URLSearchParams(window.location.search).get('fixtureRootFont'))
if (requestedRootFont === 16 || requestedRootFont === 20 || requestedRootFont === 24) {
  document.documentElement.style.fontSize = `${requestedRootFont}px`
}

const activeLesson: TodayLesson = {
  schedule: {
    id: 'today-active',
    date: '2026-09-07',
    lessonNumber: 1,
    startsAt: '09:00:00',
    endsAt: '10:30:00',
    status: 'ACTIVE',
    subject: { id: 'math', name: 'Математический анализ', type: 'LECTURE' },
    room: { current: 'А-312', previous: null, changeState: 'UNCHANGED' },
  },
  attendance: null,
  checkinEligibility: { allowed: true, reason: 'ELIGIBLE', retryAt: null },
  request: null,
}

const plannedLesson: TodayLesson = {
  schedule: {
    id: 'today-planned',
    date: '2026-09-07',
    lessonNumber: 2,
    startsAt: '10:40:00',
    endsAt: '12:10:00',
    status: 'PLANNED',
    subject: { id: 'networks', name: 'Компьютерные сети', type: 'PRACTICE' },
    room: { current: 'Б-218', previous: null, changeState: 'UNCHANGED' },
  },
  attendance: null,
  checkinEligibility: { allowed: false, reason: 'TOO_EARLY', retryAt: null },
  request: null,
}

const today: StudentToday = {
  date: '2026-09-07',
  timeZone: 'Europe/Moscow',
  serverNow: '2026-09-07T09:30:00Z',
  lessons: [activeLesson, plannedLesson],
  _links: { self: { href: 'https://fixture.invalid/student/today' } },
}

const FixtureApp = defineComponent({
  setup() {
    return () => h(TodayScreen, {
      today,
      loading: false,
      error: null,
      offline: false,
      updatedAt: null,
      submittingLessonId: null,
      semesterSchedule: null,
      selectedDate: today.date,
      onCheckin: () => undefined,
      onSelectDate: () => undefined,
      onNavigate: () => undefined,
    })
  },
})

createApp(FixtureApp).mount('#app')
