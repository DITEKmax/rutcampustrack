import { createApp, defineComponent, h, ref } from 'vue'
import RequestsScreen from '../mobile-core/src/features/requests/RequestsScreen.vue'
import RequestTypeScreen from '../mobile-core/src/features/requests/RequestTypeScreen.vue'
import ExcuseRequestScreen from '../mobile-core/src/features/requests/ExcuseRequestScreen.vue'
import LateCheckinRequestScreen from '../mobile-core/src/features/requests/LateCheckinRequestScreen.vue'
import type { RequestDetail, RequestFileLimits, RequestFileRef, RequestLessonOption, RequestReasonOption, RequestTypeChoice } from '../mobile-core/src/features/requests/types'
import './harness.css'

const lessonOptions: RequestLessonOption[] = [
  { lesson: { id: 'lesson-1', subjectName: 'Основы программирования', subjectType: 'LECTURE', date: '2026-09-01', startsAt: '10:40:00', endsAt: '12:10:00' }, excuseEligible: true, lateCheckinEligible: false },
  { lesson: { id: 'lesson-2', subjectName: 'Компьютерные сети', subjectType: 'LAB', date: '2026-09-02', startsAt: '12:20:00', endsAt: '13:50:00' }, excuseEligible: true, lateCheckinEligible: false },
  { lesson: { id: 'lesson-3', subjectName: 'Математический анализ', subjectType: 'PRACTICE', date: '2026-09-01', startsAt: '09:00:00', endsAt: '10:30:00' }, excuseEligible: false, lateCheckinEligible: true, unavailableReason: 'Пара уже закрыта для заявки.' },
]
const lateOptions: RequestLessonOption[] = lessonOptions.map((option) => ({ ...option, excuseEligible: false, lateCheckinEligible: option.lesson?.id === 'lesson-3' }))
const reasons: RequestReasonOption[] = [{ code: 'ILLNESS', label: 'Болезнь', commentRequired: false }, { code: 'OTHER', label: 'Другое', commentRequired: true }]
const fileLimits: RequestFileLimits = { maxFiles: 2, maxBytesPerFile: 10 * 1024 * 1024, maxBytesTotal: 20 * 1024 * 1024, contentTypes: ['image/*', 'application/pdf'] }
const openRequest: RequestDetail = { summary: { id: 'request-1', kind: 'EXCUSE', status: 'PENDING', origin: 'MANUAL', createdAt: '2026-09-01T06:40:00Z', canCancel: true, lessons: lessonOptions.slice(0, 2).map((option) => option.lesson!) } }
const archiveRequest: RequestDetail = { summary: { id: 'request-2', kind: 'LATE_CHECKIN', status: 'APPROVED', origin: 'MANUAL', createdAt: '2026-09-01T06:40:00Z', updatedAt: '2026-09-02T06:40:00Z', lessons: [lateOptions[2]!.lesson!] }, decision: { comment: 'Заявка одобрена', decidedAt: '2026-09-02T06:40:00Z' } }
const choices: RequestTypeChoice[] = [{ kind: 'EXCUSE', label: 'Уважительная причина', symbol: 'У', available: true }, { kind: 'LATE_CHECKIN', label: 'Забыл отметиться', symbol: 'Н → +', available: true }]
type Screen = 'open' | 'archive' | 'type' | 'excuse' | 'late'
function queryScreen(): Screen {
  const value = new URLSearchParams(window.location.search).get('state')
  return value === 'archive' || value === 'type' || value === 'excuse' || value === 'late' ? value : 'open'
}

const App = defineComponent({
  setup() {
    const screen = ref<Screen>(queryScreen())
    const excuseLessonIds = ref<string[]>(['lesson-1', 'lesson-3', 'lesson-missing'])
    const excuseReason = ref<string | null>('ILLNESS')
    const excuseComment = ref('')
    const excuseFiles = ref<RequestFileRef[]>([])
    const lateLessonId = ref<string | null>('lesson-3')
    const setScreen = (next: Screen): void => { screen.value = next }

    return () => {
      const hostDock = screen.value === 'open' || screen.value === 'archive'
        ? h('nav', { class: 'host-dock', 'aria-label': 'Навигация хоста' }, [h('span', 'Сегодня'), h('span', 'Задания'), h('span', 'Учёт'), h('span', { class: 'host-dock__active' }, 'Ещё'), h('span', 'Профиль')])
        : null
      let view
      if (screen.value === 'open' || screen.value === 'archive') {
        view = h(RequestsScreen, { bucket: screen.value === 'archive' ? 'archive' : 'open', requests: screen.value === 'archive' ? [archiveRequest] : [openRequest], loading: false, error: null, offline: false, access: 'allowed', onNewRequest: () => setScreen('type'), onSelectBucket: (bucket: 'open' | 'archive') => setScreen(bucket) })
      } else if (screen.value === 'type') {
        view = h(RequestTypeScreen, { choices, onBack: () => setScreen('open'), onChoose: (kind: 'EXCUSE' | 'LATE_CHECKIN') => setScreen(kind === 'EXCUSE' ? 'excuse' : 'late') })
      } else if (screen.value === 'excuse') {
        view = h(ExcuseRequestScreen, { access: 'allowed', lessons: lessonOptions, lessonIds: excuseLessonIds.value, reasons, reason: excuseReason.value, comment: excuseComment.value, files: excuseFiles.value, fileLimits, onBack: () => setScreen('type'), 'onUpdate:lessonIds': (value: string[]) => { excuseLessonIds.value = value }, 'onUpdate:reason': (value: string | null) => { excuseReason.value = value }, 'onUpdate:comment': (value: string) => { excuseComment.value = value }, 'onUpdate:files': (value: RequestFileRef[]) => { excuseFiles.value = value }, onSubmit: () => undefined })
      } else {
        view = h(LateCheckinRequestScreen, { access: 'allowed', lessons: lateOptions, lessonId: lateLessonId.value, budget: { remaining: 5, limit: 5, used: 0, semesterId: 'semester-1' }, onBack: () => setScreen('type'), onCancel: () => setScreen('type'), 'onUpdate:lessonId': (value: string | null) => { lateLessonId.value = value }, onSubmit: () => undefined })
      }
      return h('div', { class: 'harness-app' }, [view, hostDock])
    }
  },
})
createApp(App).mount('#app')
