import type { StudentTraceSection } from './pwa-trace'

type ActionTrace = {
  beginAction(label: string, section?: StudentTraceSection): number
  localResult(actionId: number, result: string): void
}
const disabled = ':disabled, [aria-disabled="true"], [inert]'
const clicks = [
  'button', 'a[href]', 'summary', 'input[type="button"]', 'input[type="reset"]',
  'input[type="image"]', 'input[type="submit"]',
  '[role="button"]', '[role="tab"]', '[role="option"]', '[role^="menuitem"]',
  '[role="switch"]', '[role="checkbox"]', '[role="radio"]', '[role="combobox"]',
].join(',')
const field = 'select, textarea, input, [contenteditable="true"]'
const backdrops = '.teacher-day-calendar__backdrop, .admin-map-preview-overlay, .teacher-attachment-full'
const viewport = '.map-preview, .teacher-journal__table-scroll'
const customNavigationKeys = [
  '[role="slider"]', '[role="listbox"]', '[role="combobox"]', '[role="tablist"]',
  '.attendance-graph__column', '.attendance-day-rail__item', '.semester-calendar',
  '.request-date-calendar', '.teacher-day-calendar',
].join(',')
const tabSections = new Map<string, StudentTraceSection>([
  ['Сегодня', 'today'], ['Задания', 'homework'], ['Учёт', 'attendance'], ['Профиль', 'profile'],
])
const moreSections = new Map<string, StudentTraceSection>([
  ['Статистика', 'statistics'], ['Расписание семестра', 'schedule'], ['Заявки', 'requests'],
  ['Карта', 'map'], ['Уведомления', 'notifications'],
])

const text = (value: string | null | undefined) => value?.replace(/\s+/g, ' ').trim().slice(0, 96) || ''
function caption(element: Element): string {
  const references = element.getAttribute('aria-labelledby')?.trim().split(/\s+/) ?? []
  const ariaLabel = text(element.getAttribute('aria-label')) ||
    text(references.map(id => document.getElementById(id)?.textContent ?? '').join(' '))
  if (ariaLabel) return ariaLabel
  if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement ||
      element instanceof HTMLSelectElement || element instanceof HTMLButtonElement) {
    const label = text(Array.from(element.labels ?? []).map(node => node.textContent ?? '').join(' '))
    if (label) return label
  }
  if (element.matches(backdrops)) return 'Закрытие окна'
  if (element.matches(field)) return text(element.getAttribute('placeholder'))
  // Do not copy an input value, even when the action was triggered by keyboard or paste.
  if (element.matches('form,[role="dialog"]')) return ''
  return text(element.querySelector('.mobile-bottom-nav__label')?.textContent) ||
    text(element.textContent) || text(element.getAttribute('title'))
}

function sectionFor(element: Element): StudentTraceSection | undefined {
  if (element.matches('.mobile-bottom-nav[data-student="true"] .mobile-bottom-nav__item'))
    return tabSections.get(text(element.querySelector('.mobile-bottom-nav__label')?.textContent))
  if (element.matches('.profile-more .profile-more-row'))
    return moreSections.get(text(element.lastElementChild?.textContent))
  if (element.matches('.notifications-entry')) return 'notifications'
  if (element.matches('.notifications-screen__gear')) return 'notificationSettings'
  // Colors are local. Back from colors returns to the settings already loaded with the inbox.
  if (element.matches('.notifications-screen__back') && document.querySelector('.notification-colors'))
    return 'notificationSettings'
}

function visible(selector: string): HTMLElement | undefined {
  return [...document.querySelectorAll<HTMLElement>(selector)]
    .find(el => !el.closest('[hidden], [aria-hidden="true"], [inert]') && el.getClientRects().length > 0)
}

function screen(): string {
  return text((visible('[role="dialog"] h1, [role="dialog"] h2') ??
    visible('#app main h1, #app h1, #app [role="heading"][aria-level="1"]') ??
    visible('.mobile-bottom-nav__item[aria-current="page"] .mobile-bottom-nav__label'))?.textContent)
}

function controlState(control: Element | null): string {
  if (!control?.isConnected) return ''
  if (control.matches('.map-zoom-button'))
    return controlState(control.closest('.map-viewer')?.querySelector('.map-preview') ?? null)
  if (control instanceof HTMLInputElement && ['checkbox', 'radio'].includes(control.type))
    return control.checked ? 'Отмечено' : 'Снята отметка'
  if (control instanceof HTMLSelectElement) return 'Вариант выбран'
  if (control instanceof HTMLDetailsElement) return control.open ? 'Раздел раскрыт' : 'Раздел свёрнут'
  if (control.matches('summary') && control.parentElement instanceof HTMLDetailsElement)
    return controlState(control.parentElement)
  if (control.matches(viewport)) {
    const area = control as HTMLElement
    const map = area.matches('.map-preview')
    const scale = Number(area.style.getPropertyValue(map ? '--map-zoom' : '--teacher-journal-scale')) || 1
    return [
      `Масштаб: ${Math.round(scale * 100)} %`,
      ...(area.scrollLeft || area.scrollTop
        ? [`Смещение: ${Math.round(area.scrollLeft)} × ${Math.round(area.scrollTop)}`] : []),
    ].join('. ')
  }
  if (control.getAttribute('role') === 'slider')
    return text(control.getAttribute('aria-valuetext')) || text(control.getAttribute('aria-valuenow'))
  if (control.hasAttribute('aria-expanded'))
    return control.getAttribute('aria-expanded') === 'true' ? 'Раздел раскрыт' : 'Раздел свёрнут'
  if (control.hasAttribute('aria-checked'))
    return control.getAttribute('aria-checked') === 'true' ? 'Включено' : 'Выключено'
  if (control.hasAttribute('aria-pressed'))
    return control.getAttribute('aria-pressed') === 'true' ? 'Выбрано' : 'Выбор снят'
  if (control.getAttribute('aria-selected') === 'true' || control.getAttribute('aria-current') === 'page')
    return 'Выбрано'
  return ''
}

const keys: Record<string, string> = {
  ArrowUp: 'Стрелка вверх', ArrowDown: 'Стрелка вниз', ArrowLeft: 'Стрелка влево',
  ArrowRight: 'Стрелка вправо', Enter: 'Enter', ' ': 'Пробел', Escape: 'Escape',
  Home: 'Home', End: 'End', PageUp: 'Page Up', PageDown: 'Page Down',
  ContextMenu: 'Контекстное меню', F10: 'Shift + F10', Tab: 'Tab',
  '+': 'Плюс', '=': 'Плюс', '-': 'Минус', '−': 'Минус', '0': 'Ноль',
}

export function observeDemoActions(trace: ActionTrace) {
  let pendingFrame = 0
  let pointer: { id: number; source: Element; x: number; y: number; generation: number; state: string } | undefined
  let gestureClick: { source: Element; until: number } | undefined
  let lastGeneration = 0
  let submitting: { form: HTMLFormElement; generation: number } | undefined

  function begin(action: string, target: Element | null = null, section?: StudentTraceSection, result?: string) {
    const previousScreen = screen()
    const previousFocus = document.activeElement
    const previousState = controlState(target)
    const label = target && caption(target)
    const actionId = trace.beginAction(label ? `${action}: ${label}` : action, section)
    lastGeneration = actionId
    cancelAnimationFrame(pendingFrame)
    // Vue commits before the next frame. The generation check also protects this result
    // when fast input, pointer activity or a cross-frame navigation starts a new action.
    pendingFrame = requestAnimationFrame(() => {
      if (lastGeneration !== actionId) return
      const currentScreen = screen()
      const currentState = controlState(target)
      const focused = document.activeElement
      const focusLabel = focused && focused !== previousFocus && focused !== target && focused.matches(clicks)
        ? caption(focused) : ''
      const observed = [
        result,
        currentScreen && currentScreen !== previousScreen ? `Экран: ${currentScreen}` : '',
        currentState && currentState !== previousState ? currentState : '',
        focusLabel ? `Фокус: ${focusLabel}` : '',
      ].filter(Boolean).join('. ')
      trace.localResult(actionId, observed || [
        'Видимых изменений не обнаружено', currentScreen ? `Экран: ${currentScreen}` : '',
      ].filter(Boolean).join('. '))
    })
    return actionId
  }

  document.addEventListener('click', event => {
    if (!event.isTrusted || !(event.target instanceof Element)) return
    const control = event.target.closest(clicks) ?? (event.target.matches(backdrops) ? event.target : null)
    if (!control || control.closest(disabled) || control.closest('[role="slider"]')) return
    if (event.detail > 0 && gestureClick?.source === control && event.timeStamp <= gestureClick.until) {
      gestureClick = undefined
      return
    }
    if ((control instanceof HTMLButtonElement || control instanceof HTMLInputElement) &&
        ['submit', 'image'].includes(control.type) && control.form) {
      // Capture click handlers too; the default submit from the same activation must
      // preserve any fetch already dispatched by them. Invalid forms may never submit.
      const generation = begin('Отправка формы', control)
      const current = { form: control.form, generation }
      submitting = current
      window.setTimeout(() => { if (submitting === current) submitting = undefined }, 0)
      return
    }
    const verb = control.hasAttribute('download') ? 'Скачивание'
      : control.matches('[role="switch"]') ? 'Переключение'
      : control.matches('[role="tab"], [role="option"], .mobile-bottom-nav__item') ? 'Выбор' : 'Нажатие'
    begin(verb, control, sectionFor(control), control.hasAttribute('download') ? 'Запрошено скачивание файла' : undefined)
  }, true)

  document.addEventListener('submit', event => {
    if (!event.isTrusted || !(event.target instanceof HTMLFormElement)) return
    if (submitting?.form === event.target && submitting.generation === lastGeneration) return
    const submitter = event.submitter
    begin('Отправка формы', submitter ?? event.target)
  }, true)

  document.addEventListener('change', event => {
    const input = event.target
    if (!event.isTrusted || !(input instanceof Element) || input.closest(disabled)) return
    if (input instanceof HTMLSelectElement)
      begin('Выбор варианта', input, undefined, 'Вариант выбран')
    else if (input instanceof HTMLInputElement) {
      if (['checkbox', 'radio'].includes(input.type))
        begin('Изменение выбора', input, undefined, controlState(input))
      if (input.type === 'file')
        begin('Выбор файла', input, undefined, input.files?.length ? `Выбрано файлов: ${input.files.length}` : 'Файл не выбран')
    }
  }, true)

  document.addEventListener('input', event => {
    const input = event.target
    if (!event.isTrusted || !(input instanceof Element) || input.closest(disabled)) return
    // The change event is the PWA's only owner for native selection controls.
    if (input instanceof HTMLTextAreaElement || input.matches('[contenteditable="true"]') ||
        (input instanceof HTMLInputElement && !['checkbox', 'radio', 'file'].includes(input.type)))
      begin('Изменение поля', input, undefined, input instanceof HTMLInputElement && input.type === 'range'
        ? 'Положение ползунка изменено' : 'Ввод изменён')
  }, true)

  document.addEventListener('keydown', event => {
    const target = event.target
    if (!event.isTrusted || event.isComposing || event.altKey || event.ctrlKey || event.metaKey ||
        !(target instanceof Element) || target.closest(disabled) || !keys[event.key]) return
    // Native activation keys are recorded by click/change/submit. Limit the
    // remaining keys to widgets that implement their own keyboard operations.
    const mapOrJournal = target.closest(viewport)
    const custom = target.closest(customNavigationKeys)
    const namedControl = target.closest(`${clicks}, ${field}`)
    const arrow = event.key.startsWith('Arrow') || ['Home', 'End', 'PageUp', 'PageDown'].includes(event.key)
    const customActivation = (event.key === 'Enter' || event.key === ' ') &&
      custom?.matches('[role="slider"], [role="combobox"], [role="listbox"]')
    const menu = (event.key === 'ContextMenu' || (event.key === 'F10' && event.shiftKey)) &&
      target.closest('.notification-card[data-unread="false"] .notification-card__main')
    const search = event.key === 'Enter' && target.matches('.headman-subjects__search input, .admin-users-group-search input, input[aria-describedby*="replacement"]')
    const modal = target.closest('dialog, [role="dialog"], .request-date-calendar, .notification-card, .teacher-stats__tool-panel, .notification-settings__choice')
    const focusTrap = event.key === 'Tab' && modal && modal.matches('[aria-modal="true"]')
    const viewportKey = mapOrJournal &&
      (['+', '=', '-', '−', '0'].includes(event.key) || (arrow && mapOrJournal.matches('.map-preview--zoomed')))
    if (viewportKey || (arrow && custom && !(target instanceof HTMLSelectElement)) ||
        customActivation || menu || search || focusTrap ||
        (event.key === 'Escape' && (modal || custom?.matches('[role="combobox"], [role="slider"], [role="listbox"]'))))
      begin(`Клавиша ${keys[event.key]}`, viewportKey ? mapOrJournal : namedControl ?? custom ?? modal)
  }, true)

  document.addEventListener('pointerdown', event => {
    if (!event.isTrusted || !event.isPrimary || event.button !== 0 ||
        !(event.target instanceof Element)) return
    gestureClick = undefined
    pointer = undefined
    const control = event.target.closest('[role="slider"], .map-preview--zoomed, .notification-card[data-unread="false"] .notification-card__main')
    if (!control || control.closest(disabled)) return
    if (control.matches('[role="slider"]:not(.homework-completion__handle)')) {
      begin('Выбор на шкале', control)
      return
    }
    pointer = {
      id: event.pointerId, source: control, x: event.clientX, y: event.clientY, generation: lastGeneration,
      state: control.matches('.map-preview') ? `${control.scrollLeft}/${control.scrollTop}` : '',
    }
  }, true)

  document.addEventListener('pointerup', event => {
    if (!event.isTrusted || !pointer || pointer.id !== event.pointerId) return
    const { source, x, y, generation, state } = pointer
    pointer = undefined
    if (generation !== lastGeneration || !source.isConnected) return
    if (source.matches('.homework-completion__handle')) {
      begin('Перемещение ползунка', source)
    } else if (source.matches('.map-preview') && Math.hypot(event.clientX - x, event.clientY - y) > 3) {
      if (state !== `${source.scrollLeft}/${source.scrollTop}`)
        begin('Перемещение схемы', source, undefined, controlState(source))
    } else if (source.closest('.notification-card')?.querySelector('.notification-card__more[aria-expanded="true"]')) {
      gestureClick = { source, until: event.timeStamp + 400 }
      begin('Долгое нажатие', source, undefined, 'Открыто меню уведомления')
    }
  }, true)
  document.addEventListener('pointercancel', event => {
    if (pointer?.id === event.pointerId) pointer = undefined
  }, true)

  document.addEventListener('wheel', event => {
    if (!event.isTrusted || (!event.ctrlKey && !event.metaKey) || !event.deltaY ||
        !(event.target instanceof Element)) return
    const map = event.target.closest('.map-preview')
    if (map) begin('Масштабирование схемы', map)
  }, { capture: true, passive: true })

  let journalPinch: { viewport: Element; generation: number; state: string } | undefined
  document.addEventListener('touchstart', event => {
    if (!event.isTrusted || event.touches.length !== 2 || !(event.target instanceof Element)) return
    const area = event.target.closest('.teacher-journal__table-scroll')
    if (area) journalPinch = { viewport: area, generation: lastGeneration, state: controlState(area) }
  }, { capture: true, passive: true })
  function endPinch(event: TouchEvent) {
    if (!event.isTrusted || !journalPinch || event.touches.length >= 2) return
    const { viewport: area, generation, state } = journalPinch
    journalPinch = undefined
    if (generation === lastGeneration && area.isConnected && controlState(area) !== state)
      begin('Масштабирование журнала', area, undefined, controlState(area))
  }
  document.addEventListener('touchend', endPinch, { capture: true, passive: true })
  document.addEventListener('touchcancel', endPinch, { capture: true, passive: true })
  window.addEventListener('popstate', () => begin('Переход по истории браузера'))

  return (route: 'today' | 'attendance' | 'homework' | 'map') => {
    const routeLabels = { today: 'Сегодня', homework: 'Задания', attendance: 'Учёт', map: 'Карта' }
    begin(`Переход к экрану «${routeLabels[route]}»`, null, route)
  }
}
