import { h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { expect, it } from 'vitest'
import type { StudentToday, TodayLesson } from '../../api/types'
import TodayScreen from './TodayScreen.vue'

it('shows a disabled confirmed action ahead of stale pending and acquisition state', async () => {
  const serverNow = '2026-09-08T08:30:00Z'
  const lesson: TodayLesson = {
    schedule: {
      id: '1',
      date: '2026-09-08',
      startsAt: '11:00:00',
      endsAt: '12:30:00',
      lessonNumber: 2,
      status: 'ACTIVE',
      subject: { id: '7', name: 'Математика', type: 'LECTURE' },
      room: { current: '401', previous: null, changeState: 'UNCHANGED' },
    },
    attendance: { status: 'PRESENT', source: 'HEADMAN', markedAt: serverNow },
    request: { id: '9', status: 'PENDING', origin: 'AUTO_GEO_FAILURE', resolutionReason: null },
    checkinEligibility: { allowed: false, reason: 'ALREADY_PRESENT', retryAt: null },
  }
  const today: StudentToday = {
    date: lesson.schedule.date,
    serverNow,
    timeZone: 'Europe/Moscow',
    lessons: [lesson],
    _links: {},
  }
  const html = await renderToString(h(TodayScreen, {
    today,
    loading: false,
    error: null,
    offline: false,
    updatedAt: null,
    submittingLessonId: null,
    acquiringLessonId: lesson.schedule.id,
    semesterSchedule: null,
    selectedDate: lesson.schedule.date,
  }))
  const action = html.match(/<button class="today-hero__action"[^>]*>[\s\S]*?<\/button>/)?.[0]
  expect(action?.replace(/<!--[\s\S]*?-->/g, '')).toMatch(
    /^<button[^>]*\bdisabled\b[^>]*>Отмечено<\/button>$/,
  )
  expect(html).not.toContain('today-hero__retry')
  expect(html).not.toContain('Проверяем геолокацию')
  expect(html).not.toContain('student-lesson__pending')
})
