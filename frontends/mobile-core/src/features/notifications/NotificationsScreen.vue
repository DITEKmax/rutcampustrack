<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  NotificationsApiError,
  notificationsErrorMessage,
  type NotificationCategoryKey,
  type NotificationHistoryItem,
  type NotificationHistoryPage,
  type NotificationTarget,
  type NotificationPreferences,
  type NotificationsApi,
} from './notifications-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { MobileHostAdapter } from '../../shared/host'
import { enterNotificationsFocusScope } from './notifications-focus'
import './notifications-screen.pcss'

const props = defineProps<{
  api: NotificationsApi
  host?: MobileHostAdapter | null
  offline?: boolean
  canOpenTarget?: boolean
  realtimeRevision?: number
}>()

const emit = defineEmits<{
  close: []
  'owner-error': [error: unknown]
  'open-target': [target: NotificationTarget]
}>()

type LoadState = 'idle' | 'loading' | 'ready' | 'error'

const historyState = ref<LoadState>('idle')
const unreadCountState = ref<LoadState>('idle')
const preferencesState = ref<LoadState>('idle')
const historyError = ref<string | null>(null)
const unreadCountError = ref<string | null>(null)
const preferencesError = ref<string | null>(null)
const historyPage = ref<NotificationHistoryPage | null>(null)
const unreadCount = ref<number | null>(null)
const draftCategories = ref<Record<NotificationCategoryKey, boolean> | null>(null)
const draftMutedUntil = ref('')
const preferencesSaved = ref(false)
const preferencesFailureAction = ref<'load' | 'save'>('load')
const pageNumber = ref(0)
const unreadOnly = ref(false)
const pendingItemIds = ref(new Set<string>())
const markAllPending = ref(false)
const permissionDenied = ref(false)
const permissionDeniedMessage = ref<string | null>(null)
const dialogElement = ref<HTMLElement | null>(null)
const backButtonElement = ref<HTMLButtonElement | null>(null)
let screenEpoch = 0
let historyRequest = 0
let countRequest = 0
let preferencesRequest = 0
let stopHostBack: (() => void) | undefined
let restoreHostBack: (() => void) | undefined
let restoreFocusScope: (() => void) | undefined
let realtimeRefreshTimer: ReturnType<typeof setTimeout> | undefined
let realtimeRefreshPending = false
let realtimeRefreshRunning = false

const categories: readonly { key: NotificationCategoryKey; label: string }[] = [
  { key: 'lessons', label: 'Начало и закрытие занятий' },
  { key: 'reminders', label: 'Напоминания' },
  { key: 'homework', label: 'Домашние задания' },
  { key: 'tickets', label: 'Заявки и решения' },
  { key: 'schedule', label: 'Изменения расписания' },
  { key: 'group', label: 'События группы' },
]

const eventTitles: Readonly<Record<string, string>> = {
  EXCUSE_REQUESTED: 'Подана заявка на освобождение',
  EXCUSE_APPROVED: 'Заявка на освобождение одобрена',
  EXCUSE_REJECTED: 'Заявка на освобождение отклонена',
  LATE_CHECKIN_REQUESTED: 'Подана заявка на отметку опоздания',
  LATE_CHECKIN_APPROVED: 'Заявка на отметку опоздания одобрена',
  LATE_CHECKIN_REJECTED: 'Заявка на отметку опоздания отклонена',
  LESSON_STARTED: 'Занятие началось',
  LESSON_CLOSED: 'Занятие завершено',
  LESSON_CANCELLED: 'Занятие отменено',
  LESSON_REMINDER: 'Напоминание о занятии',
  HOMEWORK_WEEKLY_DIGEST: 'Еженедельное домашнее задание',
  HOMEWORK_PUBLISHED: 'Опубликовано домашнее задание',
  HOMEWORK_UPDATED: 'Обновлено домашнее задание',
  HOMEWORK_DUE_REMINDER: 'Срок домашнего задания скоро истекает',
  ATTENDANCE_RED_ZONE: 'Обрати внимание на посещаемость',
  ATTENDANCE_MARKED_BY_HEADMAN: 'Староста отметил посещаемость',
}

function isCurrent(epoch: number, requestId: number, currentRequest: number): boolean {
  return epoch === screenEpoch && requestId === currentRequest && !permissionDenied.value
}

function clearSensitiveData(): void {
  screenEpoch += 1
  historyRequest += 1
  countRequest += 1
  preferencesRequest += 1
  historyPage.value = null
  unreadCount.value = null
  draftCategories.value = null
  draftMutedUntil.value = ''
  pendingItemIds.value = new Set()
  markAllPending.value = false
  realtimeRefreshPending = false
  if (realtimeRefreshTimer !== undefined) clearTimeout(realtimeRefreshTimer)
  realtimeRefreshTimer = undefined
}

function queueRealtimeRefresh(): void {
  if (permissionDenied.value || props.offline) return
  realtimeRefreshPending = true
  if (realtimeRefreshRunning || realtimeRefreshTimer !== undefined) return
  realtimeRefreshTimer = setTimeout(() => {
    realtimeRefreshTimer = undefined
    if (!realtimeRefreshPending || permissionDenied.value || props.offline) return
    if (historyState.value === 'loading' || unreadCountState.value === 'loading') {
      queueRealtimeRefresh()
      return
    }
    realtimeRefreshPending = false
    realtimeRefreshRunning = true
    void Promise.all([loadHistory(), loadUnreadCount()]).finally(() => {
      realtimeRefreshRunning = false
      if (realtimeRefreshPending) queueRealtimeRefresh()
    })
  }, 250)
}

function handleFailure(error: unknown): string {
  if (error instanceof StaleSessionGenerationError) {
    clearSensitiveData()
    return ''
  }
  if (error instanceof NotificationsApiError && error.status === 403) {
    permissionDenied.value = true
    clearSensitiveData()
    permissionDeniedMessage.value = errorMessage(error)
    return permissionDeniedMessage.value
  }
  if (error instanceof NotificationsApiError && error.status === 401) {
    clearSensitiveData()
    emit('owner-error', error)
  }
  return errorMessage(error)
}

function errorMessage(error: unknown): string {
  if (props.offline || (typeof navigator !== 'undefined' && navigator.onLine === false)) {
    return 'Нет подключения к интернету. Проверь соединение и попробуй снова.'
  }
  return notificationsErrorMessage(error)
}

async function loadHistory(): Promise<void> {
  if (permissionDenied.value) return
  const requestId = ++historyRequest
  const epoch = screenEpoch
  historyState.value = 'loading'
  historyError.value = null
  historyPage.value = null
  try {
    const result = await props.api.listHistory(pageNumber.value, unreadOnly.value)
    if (!isCurrent(epoch, requestId, historyRequest)) return
    historyPage.value = result
    pageNumber.value = result.pageNumber
    historyState.value = 'ready'
  } catch (error) {
    if (!isCurrent(epoch, requestId, historyRequest)) return
    historyError.value = handleFailure(error)
    historyState.value = historyError.value ? 'error' : 'idle'
  }
}

async function loadUnreadCount(): Promise<void> {
  if (permissionDenied.value) return
  const requestId = ++countRequest
  const epoch = screenEpoch
  unreadCountState.value = 'loading'
  unreadCountError.value = null
  try {
    const result = await props.api.unreadCount()
    if (!isCurrent(epoch, requestId, countRequest)) return
    unreadCount.value = result
    unreadCountState.value = 'ready'
  } catch (error) {
    if (!isCurrent(epoch, requestId, countRequest)) return
    unreadCountError.value = handleFailure(error)
    unreadCountState.value = unreadCountError.value ? 'error' : 'idle'
  }
}

async function loadPreferences(): Promise<void> {
  if (permissionDenied.value) return
  preferencesFailureAction.value = 'load'
  const requestId = ++preferencesRequest
  const epoch = screenEpoch
  preferencesState.value = 'loading'
  preferencesError.value = null
  preferencesSaved.value = false
  try {
    const result = await props.api.getPreferences()
    if (!isCurrent(epoch, requestId, preferencesRequest)) return
    applyPreferences(result)
    preferencesState.value = 'ready'
  } catch (error) {
    if (!isCurrent(epoch, requestId, preferencesRequest)) return
    preferencesError.value = handleFailure(error)
    preferencesState.value = preferencesError.value ? 'error' : 'idle'
  }
}

function applyPreferences(value: NotificationPreferences): void {
  draftCategories.value = { ...value.categories }
  draftMutedUntil.value = toLocalDateTime(value.mutedUntil)
}

async function retryAll(): Promise<void> {
  permissionDenied.value = false
  permissionDeniedMessage.value = null
  historyState.value = 'idle'
  unreadCountState.value = 'idle'
  preferencesState.value = 'idle'
  await Promise.all([loadHistory(), loadUnreadCount(), loadPreferences()])
}

async function changeUnreadFilter(value: boolean): Promise<void> {
  unreadOnly.value = value
  pageNumber.value = 0
  await loadHistory()
}

async function changePage(value: number): Promise<void> {
  const page = historyPage.value
  if (!page || value < 0 || value >= page.totalPages || historyState.value === 'loading') return
  pageNumber.value = value
  await loadHistory()
}

async function markRead(item: NotificationHistoryItem): Promise<void> {
  if (item.readAt || pendingItemIds.value.has(item.id) || permissionDenied.value) return
  const epoch = screenEpoch
  pendingItemIds.value = new Set(pendingItemIds.value).add(item.id)
  try {
    await props.api.markRead(item.id)
    if (epoch !== screenEpoch || permissionDenied.value) return
    const acknowledgedAt = new Date().toISOString()
    if (historyPage.value) {
      historyPage.value = {
        ...historyPage.value,
        items: historyPage.value.items.map((current) => current.id === item.id
          ? { ...current, readAt: acknowledgedAt }
          : current),
      }
    }
    if (unreadCount.value !== null) unreadCount.value = Math.max(0, unreadCount.value - 1)
    if (unreadOnly.value) await loadHistory()
    await loadUnreadCount()
  } catch (error) {
    if (epoch !== screenEpoch) return
    const message = handleFailure(error)
    historyError.value = message
    if (message) historyState.value = 'error'
  } finally {
    if (epoch === screenEpoch) {
      const next = new Set(pendingItemIds.value)
      next.delete(item.id)
      pendingItemIds.value = next
    }
  }
}

async function markAllRead(): Promise<void> {
  if (markAllPending.value || !unreadCount.value || permissionDenied.value) return
  const epoch = screenEpoch
  markAllPending.value = true
  historyError.value = null
  try {
    await props.api.markAllRead()
    if (epoch !== screenEpoch || permissionDenied.value) return
    const acknowledgedAt = new Date().toISOString()
    if (historyPage.value) {
      historyPage.value = {
        ...historyPage.value,
        items: historyPage.value.items.map((item) => item.readAt ? item : { ...item, readAt: acknowledgedAt }),
      }
    }
    unreadCount.value = 0
    if (unreadOnly.value) {
      pageNumber.value = 0
      await loadHistory()
    }
  } catch (error) {
    if (epoch !== screenEpoch) return
    const message = handleFailure(error)
    historyError.value = message
    if (message) historyState.value = 'error'
  } finally {
    if (epoch === screenEpoch) markAllPending.value = false
  }
}

function setCategory(key: NotificationCategoryKey, checked: boolean): void {
  if (!draftCategories.value) return
  draftCategories.value = { ...draftCategories.value, [key]: checked }
  preferencesSaved.value = false
}

function setMuteUntil(value: string): void {
  draftMutedUntil.value = value
  preferencesSaved.value = false
}

async function savePreferences(): Promise<void> {
  if (!draftCategories.value || preferencesState.value === 'loading' || permissionDenied.value) return
  preferencesFailureAction.value = 'save'
  const epoch = screenEpoch
  const requestId = ++preferencesRequest
  preferencesState.value = 'loading'
  preferencesError.value = null
  preferencesSaved.value = false
  try {
    const saved = await props.api.updatePreferences({
      categories: { ...draftCategories.value },
      mutedUntil: fromLocalDateTime(draftMutedUntil.value),
    })
    if (!isCurrent(epoch, requestId, preferencesRequest)) return
    applyPreferences(saved)
    preferencesState.value = 'ready'
    preferencesSaved.value = true
  } catch (error) {
    if (!isCurrent(epoch, requestId, preferencesRequest)) return
    preferencesError.value = handleFailure(error)
    preferencesState.value = preferencesError.value ? 'error' : 'idle'
  }
}

function title(item: NotificationHistoryItem): string {
  return eventTitles[item.type] ?? 'Новое уведомление'
}

function targetActionLabel(target: NotificationTarget): string {
  if (target.kind === 'homework') return 'Открыть домашнее задание'
  if (target.kind === 'request') return 'Открыть заявку'
  return 'Открыть занятие'
}

function openTarget(item: NotificationHistoryItem): void {
  const target = item.target
  if (!props.canOpenTarget || !target) return
  // Mark-read is its own best-effort request. The target transition remains
  // bound to the notification generation by the app adapter.
  void markRead(item)
  emit('open-target', target)
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isFinite(date.getTime())
    ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
    : 'Дата неизвестна'
}

function toLocalDateTime(value: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return ''
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function fromLocalDateTime(value: string): string | null {
  if (!value) return null
  const date = new Date(value)
  return Number.isFinite(date.getTime()) ? date.toISOString() : null
}

function close(): void {
  emit('close')
}

onMounted(() => {
  if (dialogElement.value) {
    restoreFocusScope = enterNotificationsFocusScope(
      dialogElement.value,
      document.getElementById('app'),
      backButtonElement.value,
    )
  }
  if (props.host?.backOwner === 'host') {
    if (props.host.suspendBack) {
      restoreHostBack = props.host.suspendBack()
    } else {
      props.host.setBackVisible?.(true)
      stopHostBack = props.host.subscribeBack?.(close)
    }
  }
  void retryAll()
})

watch(() => props.realtimeRevision ?? 0, queueRealtimeRefresh)
watch(() => props.offline, (offline) => {
  if (!offline && realtimeRefreshPending) queueRealtimeRefresh()
})

onBeforeUnmount(() => {
  screenEpoch += 1
  historyRequest += 1
  countRequest += 1
  preferencesRequest += 1
  realtimeRefreshPending = false
  if (realtimeRefreshTimer !== undefined) clearTimeout(realtimeRefreshTimer)
  realtimeRefreshTimer = undefined
  restoreFocusScope?.()
  stopHostBack?.()
  if (restoreHostBack) restoreHostBack()
  else props.host?.setBackVisible?.(false)
  clearSensitiveData()
})
</script>

<template>
  <main
    ref="dialogElement"
    class="notifications-screen"
    role="dialog"
    aria-modal="true"
    aria-labelledby="notifications-title"
    tabindex="-1"
  >
    <header class="notifications-screen__header">
      <button
        ref="backButtonElement"
        type="button"
        class="notifications-screen__back"
        aria-label="Вернуться к предыдущему экрану"
        @click="close"
      >
        Назад
      </button>
      <h1 id="notifications-title">
        Уведомления
      </h1>
      <p>Личная история уведомлений, сохранённая сервером.</p>
    </header>

    <section
      v-if="permissionDenied"
      class="notifications-screen__notice notifications-screen__notice--error"
      role="alert"
    >
      <p>{{ permissionDeniedMessage ?? 'Доступ к данным уведомлений не подтверждён.' }}</p>
      <button
        type="button"
        @click="retryAll"
      >
        Повторить
      </button>
    </section>

    <template v-else>
      <section
        class="notifications-screen__section"
        aria-labelledby="notifications-history-title"
      >
        <div class="notifications-screen__section-heading">
          <div>
            <h2 id="notifications-history-title">
              История
            </h2>
            <p
              class="notifications-screen__hint"
              aria-live="polite"
            >
              {{ unreadCountState === 'loading' ? 'Считаем непрочитанные…' : unreadCount !== null ? `Непрочитанных: ${unreadCount}` : 'Счётчик непрочитанных недоступен' }}
            </p>
            <div
              v-if="unreadCountState === 'error'"
              class="notifications-screen__count-error"
              role="alert"
            >
              <span>{{ unreadCountError }}</span>
              <button
                type="button"
                class="notifications-screen__inline-action"
                @click="loadUnreadCount"
              >
                Повторить
              </button>
            </div>
          </div>
          <button
            v-if="unreadCount"
            type="button"
            class="notifications-screen__secondary-action"
            :disabled="markAllPending"
            @click="markAllRead"
          >
            {{ markAllPending ? 'Отмечаем…' : 'Прочитать все' }}
          </button>
        </div>

        <div
          class="notifications-screen__filters"
          role="group"
          aria-label="Фильтр истории"
        >
          <button
            type="button"
            :aria-pressed="!unreadOnly"
            @click="changeUnreadFilter(false)"
          >
            Все
          </button>
          <button
            type="button"
            :aria-pressed="unreadOnly"
            @click="changeUnreadFilter(true)"
          >
            Непрочитанные
          </button>
        </div>

        <div
          v-if="historyError"
          class="notifications-screen__notice notifications-screen__notice--error"
          role="alert"
        >
          {{ historyError }}
          <button
            type="button"
            @click="loadHistory"
          >
            Повторить
          </button>
        </div>
        <div
          v-else-if="historyState === 'loading'"
          class="notifications-screen__notice"
          role="status"
          aria-live="polite"
        >
          Загружаем историю…
        </div>
        <div
          v-else-if="historyState === 'ready' && historyPage?.items.length === 0"
          class="notifications-screen__notice"
        >
          {{ unreadOnly ? 'Непрочитанных уведомлений нет.' : 'Сохранённых уведомлений пока нет.' }}
        </div>
        <ul
          v-else-if="historyPage"
          class="notifications-screen__list"
        >
          <li
            v-for="item in historyPage.items"
            :key="item.id"
            class="notifications-screen__item"
            :class="{ 'notifications-screen__item--unread': !item.readAt }"
          >
            <div class="notifications-screen__item-heading">
              <h3>{{ title(item) }}</h3>
              <span
                v-if="!item.readAt"
                class="notifications-screen__unread-mark"
              >
                Не прочитано
              </span>
            </div>
            <dl
              v-if="item.context.length"
              class="notifications-screen__context"
            >
              <div
                v-for="field in item.context"
                :key="field.label"
              >
                <dt>{{ field.label }}</dt>
                <dd>{{ field.value }}</dd>
              </div>
            </dl>
            <button
              v-if="canOpenTarget && item.target"
              class="notifications-screen__secondary-action"
              type="button"
              @click="openTarget(item)"
            >
              {{ targetActionLabel(item.target) }}
            </button>
            <time :datetime="item.sentAt">{{ formatDate(item.sentAt) }}</time>
            <button
              v-if="!item.readAt"
              type="button"
              class="notifications-screen__secondary-action"
              :disabled="pendingItemIds.has(item.id)"
              @click="markRead(item)"
            >
              {{ pendingItemIds.has(item.id) ? 'Отмечаем…' : 'Отметить прочитанным' }}
            </button>
          </li>
        </ul>

        <nav
          v-if="historyPage && historyPage.totalPages > 1 && !historyError"
          class="notifications-screen__pagination"
          aria-label="Страницы уведомлений"
        >
          <button
            type="button"
            :disabled="pageNumber <= 0 || historyState === 'loading'"
            @click="changePage(pageNumber - 1)"
          >
            Назад
          </button>
          <span>Страница {{ pageNumber + 1 }} из {{ historyPage.totalPages }}</span>
          <button
            type="button"
            :disabled="pageNumber + 1 >= historyPage.totalPages || historyState === 'loading'"
            @click="changePage(pageNumber + 1)"
          >
            Дальше
          </button>
        </nav>
      </section>

      <section
        class="notifications-screen__section"
        aria-labelledby="notifications-preferences-title"
      >
        <div class="notifications-screen__section-heading">
          <h2 id="notifications-preferences-title">
            Настройки
          </h2>
          <button
            v-if="preferencesState === 'error'"
            type="button"
            class="notifications-screen__secondary-action"
            @click="preferencesFailureAction === 'save' ? savePreferences() : loadPreferences()"
          >
            Повторить
          </button>
        </div>
        <p class="notifications-screen__hint">
          Настройки сохраняются на сервере. Они не запрашивают разрешение web-push и не подтверждают доставку в Telegram.
        </p>
        <div
          v-if="preferencesError"
          class="notifications-screen__notice notifications-screen__notice--error"
          role="alert"
        >
          {{ preferencesError }}
        </div>
        <p
          v-if="preferencesState === 'loading' && !draftCategories"
          class="notifications-screen__notice"
          role="status"
        >
          Загружаем настройки…
        </p>
        <form
          v-if="draftCategories"
          class="notifications-screen__settings"
          @submit.prevent="savePreferences"
        >
          <fieldset :disabled="preferencesState === 'loading'">
            <label
              v-for="category in categories"
              :key="category.key"
              class="notifications-screen__toggle"
            >
              <input
                type="checkbox"
                :checked="draftCategories[category.key]"
                @change="setCategory(category.key, ($event.target as HTMLInputElement).checked)"
              >
              <span>{{ category.label }}</span>
            </label>
            <label class="notifications-screen__mute">
              <span>Пауза до</span>
              <input
                type="datetime-local"
                :value="draftMutedUntil"
                @input="setMuteUntil(($event.target as HTMLInputElement).value)"
              >
              <small>Оставь поле пустым, чтобы снять паузу.</small>
            </label>
            <p
              v-if="preferencesSaved"
              class="notifications-screen__saved"
              role="status"
            >
              Настройки сохранены.
            </p>
            <button
              class="notifications-screen__save"
              type="submit"
            >
              {{ preferencesState === 'loading' ? 'Сохраняем…' : 'Сохранить настройки' }}
            </button>
          </fieldset>
        </form>
      </section>
    </template>
  </main>
</template>
