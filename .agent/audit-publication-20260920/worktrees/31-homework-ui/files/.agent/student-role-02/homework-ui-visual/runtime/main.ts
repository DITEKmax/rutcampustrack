import '@fontsource-variable/onest'
import { computed, defineComponent, h, ref } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import {
  HomeworkScreen,
  StudentApi,
  rootRoute,
  useHomework,
  type StudentHomework,
  type StudentHomeworkItem,
  type StudentHomeworkQueryScope,
} from '../../../../frontends/mobile-core/src/index'
import type { MobileRootRouteId } from '../../../../frontends/mobile-core/src/shared/navigation'
import todayIcon from '../../../../frontends/mobile-core/src/assets/today-tab-active.svg'
import homeworkIcon from '../../../../frontends/mobile-core/src/assets/schedule-tab.svg'
import attendanceIcon from '../../../../frontends/mobile-core/src/assets/attendance-tab.svg'
import moreIcon from '../../../../frontends/mobile-core/src/assets/more-tab.svg'
import profileIcon from '../../../../frontends/mobile-core/src/assets/profile-tab.svg'
import './fixture.pcss'

const requestedRootFont = Number(new URLSearchParams(window.location.search).get('fixtureRootFont'))
if (requestedRootFont === 16 || requestedRootFont === 20 || requestedRootFont === 24) {
  document.documentElement.style.fontSize = `${requestedRootFont}px`
}

const fixtureScene = new URLSearchParams(window.location.search).get('fixtureScene') ?? 'runtime'
const sourceServerNow = '2026-09-01T09:30:00Z'
const runtimeServerNow = '2026-09-07T09:30:00Z'
const serverNow = fixtureScene === 'runtime' ? runtimeServerNow : sourceServerNow

const sourceOpenItems: StudentHomeworkItem[] = [
  {
    id: 'source-programming',
    subject: { id: 'programming', name: 'Основы программирования' },
    title: 'Решить задачи 1–8 и приложить ссылку на репозиторий.',
    description: 'Решить задачи 1–8 и приложить ссылку на репозиторий.',
    link: 'https://example.test/programming',
    lessonDate: '2026-09-01',
    lessonNumber: 1,
    completed: false,
    completedAt: null,
  },
  {
    id: 'source-networks',
    subject: { id: 'networks', name: 'Компьютерные сети' },
    title: 'Подготовить схему адресации для лабораторной работы.',
    description: 'Подготовить схему адресации для лабораторной работы.',
    link: 'https://example.test/networks',
    lessonDate: '2026-09-02',
    lessonNumber: 1,
    completed: false,
    completedAt: null,
  },
]

const sourceCompletedItems: StudentHomeworkItem[] = sourceOpenItems.map((item) => ({
  ...item,
  completed: true,
  completedAt: '2026-09-01T06:30:00Z',
}))

const runtimeItems: StudentHomeworkItem[] = [
  {
    id: 'history-today',
    subject: { id: 'history', name: 'История транспорта' },
    title: 'Прочитать главу 4',
    description: 'Задание выполнено сегодня по времени сервера.',
    link: 'https://example.test/history',
    lessonDate: '2026-08-28',
    lessonNumber: 1,
    completed: true,
    completedAt: '2026-09-07T06:30:00Z',
  },
  {
    id: 'math-tomorrow',
    subject: { id: 'math', name: 'Математика' },
    title: 'Решить задачи 1–10',
    description: 'Оформить решение в тетради.\nПроверить единицы измерения.',
    link: 'https://example.test/math?week=2',
    lessonDate: '2026-09-08',
    lessonNumber: 2,
    completed: false,
    completedAt: null,
  },
  {
    id: 'networks-unsafe',
    subject: { id: 'networks', name: 'Компьютерные сети' },
    title: 'Повторить протоколы',
    description: 'Составить таблицу сравнения протоколов.',
    link: 'javascript:alert(1)',
    lessonDate: '2026-09-09',
    lessonNumber: 1,
    completed: false,
    completedAt: null,
  },
  {
    id: 'physics-no-materials',
    subject: { id: 'physics', name: 'Физика' },
    title: 'Лабораторная работа',
    description: 'Подготовить отчёт к следующему занятию.',
    link: null,
    lessonDate: '2026-09-09',
    lessonNumber: 2,
    completed: false,
    completedAt: null,
  },
]

const noMaterialsItems: StudentHomeworkItem[] = sourceOpenItems.map((item, index) => index === 0
  ? { ...item, link: null }
  : item)

const initialItems = fixtureScene === 'completed'
  ? sourceCompletedItems
  : fixtureScene === 'open' || fixtureScene === 'expanded'
    ? sourceOpenItems
    : fixtureScene === 'no-materials'
      ? noMaterialsItems
      : runtimeItems

const fixtureFeed = ref<StudentHomework>({
  semester: { id: 'semester-2026-fall', name: 'Осень 2026', dateFrom: '2026-08-20', dateTo: '2026-12-31' },
  from: fixtureScene === 'runtime' ? '2026-09-01' : '2026-09-01',
  to: '2026-09-30',
  serverNow,
  items: initialItems,
})

const historicalItems: StudentHomeworkItem[] = [
  {
    id: 'historical-aug-28',
    subject: { id: 'history', name: 'История транспорта' },
    title: 'Конспект по теме',
    description: 'Сверить даты и подготовить пять тезисов.',
    link: 'https://example.test/history/archive',
    lessonDate: '2026-08-28',
    lessonNumber: 1,
    completed: false,
    completedAt: null,
  },
  {
    id: 'historical-aug-29',
    subject: { id: 'math', name: 'Математика' },
    title: 'Повторение формул',
    description: 'Выполнить упражнения из раздела 2.',
    link: null,
    lessonDate: '2026-08-29',
    lessonNumber: 2,
    completed: true,
    completedAt: '2026-08-30T08:00:00Z',
  },
]

const navItems = [
  { id: 'today', label: 'Сегодня', icon: todayIcon, route: 'today' },
  { id: 'homework', label: 'Задания', icon: homeworkIcon, route: 'homework' },
  { id: 'attendance', label: 'Учёт', icon: attendanceIcon, route: 'attendance', disabled: true, disabledReason: 'Раздел пока недоступен' },
  { id: 'more', label: 'Ещё', icon: moreIcon, route: 'more', disabled: true, disabledReason: 'Раздел пока недоступен' },
  { id: 'profile', label: 'Профиль', icon: profileIcon, route: 'profile', disabled: true, disabledReason: 'Раздел пока недоступен' },
] as const

const scope = ref<StudentHomeworkQueryScope>({
  userId: 'fixture-student-1',
  activeRole: 'STUDENT',
  groupId: 'fixture-group',
  semesterId: 'semester-2026-fall',
  resetGeneration: 0,
})
const offline = ref(false)
const failCompletions = ref(false)
const lastMaterial = ref('')

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

const api = new StudentApi({
  accessToken: () => 'fixture-token',
  fetcher: async (input, init) => {
    await delay(180)
    const url = new URL(String(input), window.location.origin)
    if (init?.method === 'PUT') {
      if (failCompletions.value) {
        return new Response(JSON.stringify({ title: 'Fixture failure', detail: 'Сохранение временно недоступно' }), {
          status: 503,
          headers: { 'Content-Type': 'application/problem+json' },
        })
      }
      const id = decodeURIComponent(url.pathname.split('/').at(-2) ?? '')
      const command = JSON.parse(String(init.body)) as { completed: boolean }
      const item = fixtureFeed.value.items.find((entry) => entry.id === id)
      if (!item) return new Response(JSON.stringify({ title: 'Not found' }), { status: 404 })
      const completedAt = command.completed ? serverNow : null
      fixtureFeed.value = {
        ...fixtureFeed.value,
        items: fixtureFeed.value.items.map((entry) => entry.id === id ? { ...entry, completed: command.completed, completedAt } : entry),
      }
      return new Response(JSON.stringify({ id, completed: command.completed, completedAt }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    const requestedFrom = url.searchParams.get('from')
    const requestedTo = url.searchParams.get('to')
    const responseFeed = requestedFrom && requestedFrom < fixtureFeed.value.from
      ? { ...fixtureFeed.value, from: requestedFrom, to: requestedTo ?? requestedFrom, items: historicalItems }
      : fixtureFeed.value
    return new Response(JSON.stringify(responseFeed), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  },
})

const FixtureApp = defineComponent({
  setup() {
    const selectedRoute = ref<MobileRootRouteId>('homework')
    const homework = useHomework(api, scope, { offline })
    const errorText = computed(() => homework.query.error.value instanceof Error ? homework.query.error.value.message : null)

    function complete(item: StudentHomeworkItem, completed: boolean): void {
      void homework.submitCompletion(item.id, completed).catch(() => undefined)
    }

    function retry(item: StudentHomeworkItem): void {
      void homework.retryCompletion(item.id)?.catch(() => undefined)
    }

    function changeStudent(): void {
      scope.value = { ...scope.value, userId: scope.value.userId === 'fixture-student-1' ? 'fixture-student-2' : 'fixture-student-1', resetGeneration: scope.value.resetGeneration + 1 }
    }

    return () => h('div', { class: 'fixture-page' }, [
      h(HomeworkScreen, {
        homework: homework.query.data.value ?? null,
        loading: homework.query.isPending.value,
        error: errorText.value,
        navItems,
        offline: offline.value,
        route: rootRoute(selectedRoute.value),
        activeId: selectedRoute.value,
        historical: homework.isHistorical.value,
        canLoadPrevious: homework.canLoadPrevious.value,
        isItemPending: homework.isPending,
        itemError: homework.itemError,
        onComplete: complete,
        onRetry: retry,
        onRetryFeed: () => void homework.query.refetch(),
        onPrevious: () => homework.loadPrevious(),
        onReturnToday: () => homework.returnToToday(),
        onNavigate: (route: MobileRootRouteId) => { selectedRoute.value = route },
        onOpenMaterial: (url: string) => { lastMaterial.value = url },
      }),
      h('aside', { class: 'fixture-controls', 'aria-label': 'Fixture controls' }, [
        h('button', { type: 'button', onClick: () => { offline.value = !offline.value } }, offline.value ? 'Включить сеть' : 'Отключить сеть'),
        h('button', { type: 'button', onClick: () => { failCompletions.value = !failCompletions.value } }, failCompletions.value ? 'Разрешить сохранение' : 'Ломать сохранение'),
        h('button', { type: 'button', onClick: changeStudent }, `Сменить scope (${scope.value.userId})`),
        lastMaterial.value ? h('p', { role: 'status' }, `Открыт материал: ${lastMaterial.value}`) : null,
      ]),
    ])
  },
})

const app = (await import('vue')).createApp(FixtureApp)
app.use(VueQueryPlugin, { queryClient: new QueryClient() })
app.mount('#app')
