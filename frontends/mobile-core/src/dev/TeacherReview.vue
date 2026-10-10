<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import TeacherFeatureOwner from '../shared/components/TeacherFeatureOwner.vue'
import type { MobileHostAdapter } from '../shared/host'
import { createMobileTheme } from '../shared/theme'
import { createTeacherReviewFixtures, teacherReviewDate, teacherReviewStates, type TeacherReviewState } from './teacher-review-fixtures'
import PasswordRecoveryScreen from '../../../pwa-vue/src/features/password-recovery/PasswordRecoveryScreen.vue'
import { publicPasswordRecoveryUrl } from '../../../tma-vue/src/password-recovery-link'
import './teacher-review.pcss'

const query = new URLSearchParams(window.location.search)
const requested = query.get('state') ?? 'home'
const state: TeacherReviewState = teacherReviewStates.includes(requested as TeacherReviewState) ? requested as TeacherReviewState : 'home'
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const fixtures = createTeacherReviewFixtures(state)
const theme = createMobileTheme({ mode: query.get('theme') === 'light' ? 'light' : 'dark' })
const recoveryOpen = ref(false)
const recoveryFetcher: typeof fetch = async () => { throw new Error('Teacher recovery dry run does not send credentials or contact a provider') }
function openRecovery(): void {
  if (shell === 'tma') {
    // Record the same public target as App's synchronous host action, without opening another app.
    document.documentElement.dataset.teacherRecoveryUrl = publicPasswordRecoveryUrl(window.location.origin)
  } else recoveryOpen.value = true
}
const hostBackVisible = ref(false)
const hostBack = ref<(() => void) | null>(null)
const host: MobileHostAdapter = {
  backOwner: shell === 'tma' ? 'host' : 'product', primaryActionOwner: 'product',
  setBackVisible(value) { hostBackVisible.value = value },
  subscribeBack(listener) { hostBack.value = listener; return () => { hostBack.value = null } },
}
const originalRootFont = document.documentElement.style.fontSize
if (query.get('rootFont') === '20') document.documentElement.style.fontSize = '20px'
// Clear recoverable product URL context only in this isolated dev entry.
for (const key of [...query.keys()]) if (key.startsWith('teacherStats')) query.delete(key)
const statsSurface = state.startsWith('stats-') || state.startsWith('journal') || state.startsWith('group')
const incompleteStats = state.startsWith('stats-search-') || state.startsWith('stats-unselected-') || state === 'stats-group-options' || state === 'stats-subject-options' || state.includes('stats-catalog-') || state.startsWith('stats-no-groups')
const studentStats = ['stats-students', 'stats-sort-students', 'stats-filters-students', 'stats-group-options', 'stats-subject-options', 'stats-no-groups-students', 'stats-unselected-group-students'].includes(state)
if (statsSurface) {
  query.set('teacherStats', '1')
  query.set('teacherStatsScope', studentStats ? 'students' : 'groups')
  query.set('teacherStatsSemester', '7')
  query.set('teacherStatsCriteria', '1')
  if (!incompleteStats || ['stats-subject-options', 'stats-unselected-subject', 'stats-unselected-types'].includes(state)) query.set('teacherStatsGroup', '101')
  if (!incompleteStats || state === 'stats-unselected-types') query.set('teacherStatsSubject', '201')
  if (!incompleteStats) for (const type of ['lecture', 'practice', 'lab']) query.append('teacherStatsType', type)
}
history.replaceState(null, '', `${location.pathname}?${query}`)
const actions: (() => boolean)[] = []
let fontsReady = false
let disposed = false
let processing = false
let observer: MutationObserver | null = null
let imageEventRoot: HTMLElement | null = null
let timeout: number | undefined
function button(label: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.teacher-feature-owner button, .mobile-bottom-nav button')).find(item => item.getClientRects().length > 0 && (item.getAttribute('aria-label') === label || item.textContent?.trim() === label || item.querySelector('.profile-role-card__title')?.textContent?.trim() === label))
}
function click(label: string): boolean { const item = button(label); if (!item || item.disabled) return false; item.click(); return true }
function action(name: string): boolean {
  const item = [...document.querySelectorAll<HTMLElement>(`[data-teacher-action="${name}"]`)].find(element => element.getClientRects().length > 0)
  const control = item instanceof HTMLButtonElement ? item : item?.querySelector<HTMLButtonElement>('button')
  if (!control || control.disabled) return false
  control.click(); return true
}
function back(): boolean { return click('Назад') }
const target = state.split('-')[0]
const groupJournal = target === 'journal' || target === 'group' || state.startsWith('stats-export-')
let journalReturnContext: { scrollLeft: number; selectedTypes: string; requests: number } | null = null
if (target === 'attendance') {
  actions.push(() => click('Посещаемость') || click('Учёт'))
  const noSelection = ['attendance-unselected-group', 'attendance-group-options', 'attendance-catalog-loading', 'attendance-catalog-error', 'attendance-no-groups'].includes(state)
  if (!noSelection) actions.push(() => click('Группа'), () => click('ИВТ-241'))
  if (!noSelection && !['attendance-unselected-subject', 'attendance-subject-options'].includes(state)) {
    actions.push(() => click('Предмет'), () => click('Программирование'))
  }
}
if (state === 'attendance-retry') actions.push(() => click('Повторить загрузку'))
if (target === 'stats' || groupJournal) actions.push(() => click('Итоги'))
if (state === 'lesson-active' || state === 'lesson-planned') actions.push(() => {
  const lessonId = state === 'lesson-active' ? '402' : '403'
  const control = document.querySelector<HTMLButtonElement>(`button[data-teacher-action="lesson-open"][data-lesson-id="${lessonId}"]`)
  if (!control || control.disabled) return false
  control.click(); return true
})
else if (target === 'lesson' || target === 'excuse') actions.push(() => action('lesson-open'))
if (groupJournal) actions.push(() => {
  const row = document.querySelector<HTMLElement>('.teacher-stats__row-name')
  const control = row?.closest<HTMLButtonElement>('button')
  if (!control) return false
  control.click(); return true
})
if (groupJournal && state !== 'group-subjects') actions.push(() => click(state === 'journal-no-scheduled-subject' ? 'Дискретная математика' : 'Программирование'))
if (groupJournal && !['group-subjects', 'journal-unselected-types'].includes(state)) {
  actions.push(() => click('Лекция'))
  if (state !== 'journal-no-scheduled-subject') actions.push(() => click('Практика'), () => click('Лабораторная'))
}
if (target === 'excuse') actions.push(() => action('excuse-open'))
if (state === 'lesson-manual-reason') actions.push(() => action('manual-excuse-open'))
if (state === 'journal-manual-reason') actions.push(() => {
  const control = document.querySelector<HTMLButtonElement>('.teacher-journal__table button[aria-label^="Васильева"]')
  if (!control) return false
  control.click(); return true
})
if (state === 'journal-excuse' || state === 'journal-excuse-return') actions.push(() => {
  const region = document.querySelector<HTMLElement>('.teacher-journal__table-scroll')
  const control = region?.querySelector<HTMLButtonElement>('button[aria-label^="Белов"]')
  if (!region || !control) return false
  region.scrollLeft = Math.min(64, region.scrollWidth - region.clientWidth)
  journalReturnContext = { scrollLeft: region.scrollLeft, selectedTypes: [...document.querySelectorAll<HTMLButtonElement>('.teacher-journal__types button[aria-pressed="true"]')].map(item => item.textContent?.trim()).join('|'), requests: fixtures.requests.filter(path => path.startsWith('/api/v1/teacher/journal?')).length }
  control.click(); return true
}, () => [...document.querySelectorAll<HTMLElement>('[data-pdf-ready="true"]')].some(element => element.getClientRects().length > 0))
if (state === 'journal-excuse-return') actions.push(back, () => {
  const region = document.querySelector<HTMLElement>('.teacher-journal__table-scroll')
  if (!region || region.getClientRects().length === 0 || !journalReturnContext) return false
  const selectedTypes = [...document.querySelectorAll<HTMLButtonElement>('.teacher-journal__types button[aria-pressed="true"]')].map(item => item.textContent?.trim()).join('|')
  const requests = fixtures.requests.filter(path => path.startsWith('/api/v1/teacher/journal?')).length
  if (Math.abs(region.scrollLeft - journalReturnContext.scrollLeft) > 1 || selectedTypes !== journalReturnContext.selectedTypes || requests !== journalReturnContext.requests) {
    document.documentElement.dataset.teacherHarnessError = 'Journal Back did not retain table scroll, types and completed response'
    return false
  }
  document.documentElement.dataset.teacherJournalRetained = 'true'
  return true
})
if (state === 'journal-chart-group' || state === 'journal-chart-student') actions.push(() => click('Показать график'))
if (state === 'journal-chart-student') actions.push(() => click('Студент'), () => click('Жукова Анна Дмитриевна'))
if (state === 'journal-subject-switch') actions.push(() => click('Предмет'), () => click('Базы данных'), () => click('Лекция'))
if (state === 'journal-table-wide' || state === 'journal-pagination') actions.push(() => {
  const region = document.querySelector<HTMLElement>('.teacher-journal__table-scroll')
  if (!region || !region.querySelector('table')) return false
  region.scrollLeft = region.scrollWidth - region.clientWidth
  return true
})
if (['profile', 'roles', 'appearance', 'security', 'sessions', 'history', 'map'].includes(target!)) actions.push(() => click(target === 'map' ? 'Карта' : 'Профиль'))
if (state === 'map-building-options' || state === 'map-floor-options') actions.push(() => {
  const control = document.getElementById(state === 'map-building-options' ? 'map-building' : 'map-floor') as HTMLButtonElement | null
  if (!control || control.disabled) return false
  control.click(); return true
})
if (target === 'roles') actions.push(() => click('Сменить роль'))
if (state === 'roles-pending' || state === 'roles-error') actions.push(() => click('Администратор'))
if (['appearance', 'security', 'sessions', 'history'].includes(target!)) {
  const labels = { appearance: 'Оформление', security: 'Безопасность', sessions: 'Активные сеансы', history: 'История аккаунта' }
  actions.push(() => click(labels[target as keyof typeof labels]))
}
if (state === 'security-recovery') actions.push(() => click('Забыли пароль?'))
if (state === 'home-date-choice') actions.push(() => {
  const control = document.querySelector<HTMLButtonElement>('.teacher-home__date-trigger')
  if (!control) return false
  control.click(); return true
})
if (state === 'attendance-pagination' || state === 'attendance-journal-page-return') actions.push(() => {
  const feed = document.querySelector<HTMLElement>('.teacher-attendance__feed')
  const sentinel = feed?.querySelector<HTMLElement>('.teacher-attendance__feed-state')
  if (!feed || !sentinel || feed.querySelectorAll('[data-teacher-action="lesson-open"]').length < 100) return false
  sentinel.scrollIntoView({ block: 'end' })
  return true
}, () => {
  const feed = document.querySelector<HTMLElement>('.teacher-attendance__feed')
  const count = feed?.querySelectorAll('[data-teacher-action="lesson-open"]').length ?? 0
  if (!feed || count <= 100) return false
  document.documentElement.dataset.teacherFeedCount = String(count)
  window.scrollTo(0, Math.max(0, feed.getBoundingClientRect().top + window.scrollY + 800))
  return true
})
if (state === 'attendance-journal-page-return') actions.push(() => action('journal-open'),
  () => document.querySelector('.teacher-journal__types') !== null,
  back,
  () => document.querySelector('.teacher-attendance__feed') !== null)
if (state === 'attendance-group-options' || state === 'stats-group-options') actions.push(() => click('Группа'))
if (state === 'attendance-subject-options' || state === 'stats-subject-options') actions.push(() => click('Предмет'))
// Initial scope is pinned above; changing scope here would clear explicit context.
if (state === 'stats-sort' || state === 'stats-sort-students') actions.push(() => action('stats-sort') || click('Сортировка'))
if (state === 'stats-filters' || state === 'stats-filters-students' || state === 'stats-filter-error') actions.push(() => action('stats-filters') || click('Фильтры'))
if (state === 'stats-filter-error') actions.push(() => {
  const minimum = document.getElementById('teacher-stats-present-min') as HTMLInputElement | null
  const maximum = document.getElementById('teacher-stats-present-max') as HTMLInputElement | null
  if (!minimum || !maximum) return false
  minimum.value = '90'; minimum.dispatchEvent(new Event('input', { bubbles: true }))
  maximum.value = '20'; maximum.dispatchEvent(new Event('input', { bubbles: true }))
  return true
}, () => click('Применить'))
if (state.startsWith('stats-multisort')) actions.push(() => click('Добавить условие сортировки') || click('Добавить условие'))
if (state === 'stats-multisort-options') actions.push(() => click('Условие сортировки 2'))
if (state === 'stats-multisort-removed') actions.push(() => click('Удалить условие 2'))
if (state.startsWith('stats-search-')) {
  actions.push(() => {
    const input = document.getElementById('teacher-stats-directory') as HTMLInputElement | null
    if (!input) return false
    input.value = state === 'stats-search-empty' ? 'Несуществующий' : 'ив'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    return true
  }, () => fixtures.requests.some(path => path.startsWith('/api/v1/teacher/search?')))
  if (state === 'stats-search-cleared') actions.push(() => document.querySelector('[aria-label="Результаты поиска"]') !== null, () => click('Очистить поиск'))
}
if (state.startsWith('lesson-export-')) actions.push(() => click('Скачать посещаемость этой пары'))
if (state.endsWith('export-options') && !state.startsWith('lesson-export-')) actions.push(() => action('export-select') || click('Формат выгрузки'))
if (state.endsWith('export-loading') || state.endsWith('export-error')) actions.push(() => state.startsWith('lesson-export-') ? click('Word') : action('export-download'))
async function advance(): Promise<void> {
  if (disposed || processing || !fontsReady || document.documentElement.dataset.teacherReady === 'true') return
  processing = true
  let actionPerformed = false
  try {
    const next = actions[0]
    if (next) {
      if (next()) { actions.shift(); actionPerformed = true; await nextTick() }
      else return
    }
    const main = [...document.querySelectorAll<HTMLElement>('.teacher-feature-owner main, .teacher-feature-owner .profile-screen, .teacher-feature-owner .profile-page, .password-recovery-screen')].find(element => element.getClientRects().length > 0)
    if (!main || actions.length) return
    const text = main.textContent ?? ''
    if (!state.endsWith('loading') && (/Загружаем|Загрузка/.test(text) || main.getAttribute('aria-busy') === 'true')) return
    if (state.endsWith('options') && !main.querySelector('[role="listbox"], .teacher-lesson-export__popup')) return
    if (['excuse', 'excuse-pdf', 'excuse-pending'].includes(state) && !main.querySelector('[data-pdf-ready="true"]')) return
    if (state === 'excuse-image') {
      const image = main.querySelector<HTMLImageElement>('.teacher-attachment__image img')
      if (!image?.complete || image.naturalWidth === 0) return
    }
    if (state === 'home-date-choice' && !main.querySelector('[role="dialog"]')) return
    const surface = document.querySelector('.mobile-shell')?.getAttribute('data-surface')
    const backCount = Array.from(document.querySelectorAll<HTMLButtonElement>('.mobile-shell button')).filter(item => item.getClientRects().length > 0 && (item.getAttribute('aria-label') === 'Назад' || item.textContent?.trim() === 'Назад')).length
    const expectedBackCount = recoveryOpen.value ? 0 : surface !== 'root' ? 1 : 0
    document.documentElement.dataset.teacherBackCount = String(backCount)
    if (backCount !== expectedBackCount) { document.documentElement.dataset.teacherHarnessError = `Expected ${expectedBackCount} product Back, found ${backCount}`; return }
    if (incompleteStats && fixtures.requests.some(path => path.startsWith('/api/v1/teacher/stats?'))) {
      document.documentElement.dataset.teacherHarnessError = 'Incomplete context or directory search must not query statistics'
      return
    }
    if (state === 'stats-no-groups-students' && fixtures.requests.some(path => path.startsWith('/api/v1/teacher/stats?'))) {
      document.documentElement.dataset.teacherHarnessError = 'Empty catalog must not query statistics'
      return
    }
    const searchResults = main.querySelector<HTMLElement>('[aria-label="Результаты поиска"]')
    if (state.startsWith('stats-search-') && state !== 'stats-search-loading' && searchResults?.getAttribute('aria-busy') === 'true') return
    if (state === 'stats-search-results' && (!main.querySelector('[aria-label="Найденные группы"] button') || !main.querySelector('[aria-label="Найденные студенты"] button'))) return
    if (state === 'stats-search-empty' && (!main.textContent?.includes('Групп не найдено') || !main.textContent?.includes('Студентов не найдено'))) return
    if (state === 'stats-search-error' && !main.textContent?.includes('Поиск недоступен')) return
    if (state === 'stats-search-cleared' && (main.querySelector<HTMLInputElement>('#teacher-stats-directory')?.value !== '' || searchResults)) return
    if (state === 'security-recovery' && shell === 'tma' && document.documentElement.dataset.teacherRecoveryUrl !== publicPasswordRecoveryUrl(window.location.origin)) return
    document.documentElement.dataset.teacherRequests = JSON.stringify(fixtures.requests)
    document.documentElement.dataset.teacherReady = 'true'
    document.documentElement.dataset.teacherState = state
    document.documentElement.dataset.teacherHostBack = String(hostBackVisible.value)
    document.documentElement.dataset.teacherHeading = main.querySelector('h1')?.textContent?.trim() ?? ''
    if (timeout !== undefined) window.clearTimeout(timeout)
  } finally { processing = false; if (actionPerformed) void nextTick(advance) }
}
function onImageEvent(event: Event): void {
  if (event.target instanceof HTMLImageElement) void advance()
}
onMounted(() => {
  document.title = `Преподаватель · ${state} · ${shell.toUpperCase()} · синтетический API`
  if (state === 'excuse-image') {
    imageEventRoot = document.getElementById('app')
    imageEventRoot?.addEventListener('load', onImageEvent, true)
    imageEventRoot?.addEventListener('error', onImageEvent, true)
  }
  observer = new MutationObserver(() => { void advance() })
  observer.observe(document.getElementById('app')!, { childList: true, subtree: true, attributes: true })
  timeout = window.setTimeout(() => { document.documentElement.dataset.teacherHarnessError = `State not ready: ${state}; remaining actions ${actions.length}` }, 20000)
  void Promise.all([document.fonts.load('400 16px "Onest Variable"', 'Преподаватель ABC123'), document.fonts.load('600 16px "Onest Variable"', 'Преподаватель ABC123')]).then(() => { fontsReady = document.fonts.check('400 16px "Onest Variable"', 'Преподаватель ABC123') && document.fonts.check('600 16px "Onest Variable"', 'Преподаватель ABC123'); document.documentElement.dataset.teacherFontsReady = String(fontsReady); void advance() })
})
onBeforeUnmount(() => { disposed = true; imageEventRoot?.removeEventListener('load', onImageEvent, true); imageEventRoot?.removeEventListener('error', onImageEvent, true); imageEventRoot = null; observer?.disconnect(); if (timeout !== undefined) window.clearTimeout(timeout); theme.dispose(); document.documentElement.style.fontSize = originalRootFont })
</script>

<template>
  <aside
    v-if="query.get('capture') !== '1'"
    class="teacher-review-catalog"
  >
    <p>Реальные Vue-компоненты · синтетический API · {{ shell.toUpperCase() }}</p>
    <a
      v-for="id in teacherReviewStates"
      :key="id"
      :href="`?state=${id}&shell=${shell}&theme=${query.get('theme') ?? 'dark'}`"
    >{{ id }}</a>
    <button
      v-if="hostBackVisible"
      type="button"
      @click="hostBack?.()"
    >
      Назад хоста
    </button>
  </aside>
  <PasswordRecoveryScreen
    v-if="recoveryOpen"
    :fetcher="recoveryFetcher"
    @cancel="recoveryOpen = false"
    @completed="recoveryOpen = false"
  />
  <TeacherFeatureOwner
    v-else
    :api="fixtures.api"
    :semester-id="7"
    :selected-date="teacherReviewDate"
    :profile-port="fixtures.profile"
    :on-recover="openRecovery"
    :map-client="fixtures.map"
    :theme-controller="theme"
    :host="host"
  />
</template>
