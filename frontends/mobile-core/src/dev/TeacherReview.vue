<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import TeacherFeatureOwner from '../shared/components/TeacherFeatureOwner.vue'
import type { MobileHostAdapter } from '../shared/host'
import { createMobileTheme } from '../shared/theme'
import { createTeacherReviewFixtures, teacherReviewDate, teacherReviewStates, type TeacherReviewState } from './teacher-review-fixtures'
import './teacher-review.pcss'

const query = new URLSearchParams(window.location.search)
const requested = query.get('state') ?? 'home'
const state: TeacherReviewState = teacherReviewStates.includes(requested as TeacherReviewState) ? requested as TeacherReviewState : 'home'
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const fixtures = createTeacherReviewFixtures(state)
const theme = createMobileTheme({ mode: query.get('theme') === 'light' ? 'light' : 'dark' })
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
if (state === 'stats-no-groups-students') {
  query.set('teacherStats', '1')
  query.set('teacherStatsScope', 'students')
  query.set('teacherStatsSemester', '7')
  query.set('teacherStatsCriteria', '1')
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
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.teacher-feature-owner button, .mobile-bottom-nav button')).find(item => item.getAttribute('aria-label') === label || item.textContent?.trim() === label || item.querySelector('.profile-role-card__title')?.textContent?.trim() === label)
}
function click(label: string): boolean { const item = button(label); if (!item || item.disabled) return false; item.click(); return true }
function action(name: string): boolean {
  const item = document.querySelector<HTMLElement>(`[data-teacher-action="${name}"]`)
  const control = item instanceof HTMLButtonElement ? item : item?.querySelector<HTMLButtonElement>('button')
  if (!control || control.disabled) return false
  control.click(); return true
}
const target = state.split('-')[0]
if (target === 'attendance' || target === 'journal') actions.push(() => click('Посещаемость') || click('Учёт'))
if (target === 'stats') actions.push(() => click('Итоги'))
if (target === 'lesson' || target === 'excuse') actions.push(() => action('lesson-open'))
if (target === 'journal') actions.push(() => action('journal-open'))
if (target === 'excuse') actions.push(() => action('excuse-open'))
if (state === 'lesson-manual-reason') actions.push(() => action('manual-reason-toggle'))
if (['profile', 'roles', 'appearance', 'security', 'sessions', 'history', 'map'].includes(target!)) actions.push(() => click(target === 'map' ? 'Карта' : 'Профиль'))
if (state === 'map-building-options' || state === 'map-floor-options') actions.push(() => {
  const control = document.getElementById(state === 'map-building-options' ? 'map-building' : 'map-floor') as HTMLButtonElement | null
  if (!control || control.disabled) return false
  control.click(); return true
})
if (target === 'roles') actions.push(() => click('Сменить роль'))
if (state === 'roles-pending' || state === 'roles-error') actions.push(() => click('Администратор'))
if (['appearance', 'security', 'sessions', 'history'].includes(state)) {
  const labels = { appearance: 'Оформление', security: 'Безопасность', sessions: 'Активные сеансы', history: 'История аккаунта' }
  actions.push(() => click(labels[state as keyof typeof labels]))
}
if (state === 'home-date-choice') actions.push(() => {
  const control = document.querySelector<HTMLButtonElement>('.teacher-home__date-trigger')
  if (!control) return false
  control.click(); return true
})
if (state === 'journal-pagination' || state === 'attendance-journal-page-return') {
  const pageReady = (page: number) => document.querySelector('.teacher-screen__pagination')?.textContent?.includes(`Страница ${page}`) === true
  actions.push(() => click('Следующая страница'), () => pageReady(2))
  if (state === 'attendance-journal-page-return') actions.push(
    () => action('journal-open'), () => click('Следующая страница'), () => pageReady(3),
    () => { if (shell === 'tma' && hostBack.value) { hostBack.value(); return true }; return click('Назад') },
    () => pageReady(3),
  )
}
if (state === 'attendance-period') actions.push(() => action('period-toggle'))
if (state === 'attendance-group-options' || state === 'stats-group-options') actions.push(() => click('Группа'))
if (state === 'attendance-subject-options' || state === 'stats-subject-options') actions.push(() => click('Предмет'))
if (['stats-students', 'stats-group-options', 'stats-subject-options', 'stats-sort-students', 'stats-filters-students'].includes(state)) actions.splice(1, 0, () => click('Студенты') || click('По студентам'))
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
if (state.startsWith('stats-multisort')) actions.push(() => click('Добавить условие'))
if (state === 'stats-multisort-options') actions.push(() => click('Условие сортировки 2'))
if (state === 'stats-multisort-removed') actions.push(() => click('Удалить условие 2'))
if (state.endsWith('export-options')) actions.push(() => action('export-select') || click('Формат выгрузки'))
if (state.endsWith('export-loading') || state.endsWith('export-error')) actions.push(() => action('export-download'))
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
    const main = document.querySelector('.teacher-feature-owner main, .teacher-feature-owner .profile-screen, .teacher-feature-owner .profile-page')
    if (!main || actions.length) return
    const text = main.textContent ?? ''
    if (!state.endsWith('loading') && (/Загружаем|Загрузка/.test(text) || main.getAttribute('aria-busy') === 'true')) return
    if (state.endsWith('options') && !main.querySelector('[role="listbox"]')) return
    if (['excuse', 'excuse-pdf', 'excuse-pending'].includes(state) && !main.querySelector('[data-pdf-ready="true"]')) return
    if (state === 'excuse-image') {
      const image = main.querySelector<HTMLImageElement>('.teacher-attachment__image img')
      if (!image?.complete || image.naturalWidth === 0) return
    }
    if (state === 'home-date-choice' && !main.querySelector('#teacher-date-choice input[type="date"]')) return
    const surface = document.querySelector('.mobile-shell')?.getAttribute('data-surface')
    const backCount = Array.from(document.querySelectorAll<HTMLButtonElement>('.mobile-shell button')).filter(item => item.getClientRects().length > 0 && (item.getAttribute('aria-label') === 'Назад' || item.textContent?.trim() === 'Назад')).length
    const expectedBackCount = shell === 'pwa' && surface !== 'root' ? 1 : 0
    document.documentElement.dataset.teacherBackCount = String(backCount)
    if (backCount !== expectedBackCount) { document.documentElement.dataset.teacherHarnessError = `Expected ${expectedBackCount} product Back, found ${backCount}`; return }
    if (state === 'stats-no-groups-students' && fixtures.requests.some(path => path.startsWith('/api/v1/teacher/stats?'))) {
      document.documentElement.dataset.teacherHarnessError = 'Empty catalog must not query statistics'
      return
    }
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
  void Promise.all([document.fonts.load('400 16px "Onest Variable"', 'Преподаватель ABC123'), document.fonts.load('600 16px "Onest Variable"', 'Преподаватель ABC123')]).then(() => { fontsReady = true; document.documentElement.dataset.teacherFontsReady = 'true'; void advance() })
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
  <TeacherFeatureOwner
    :api="fixtures.api"
    :semester-id="7"
    :selected-date="teacherReviewDate"
    :profile-port="fixtures.profile"
    :map-client="fixtures.map"
    :theme-controller="theme"
    :host="host"
  />
</template>