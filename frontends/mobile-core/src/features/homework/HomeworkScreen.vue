<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { StudentHomework, StudentHomeworkItem } from '../../api/types'
import {
  groupHomework,
  validateHomeworkLink,
  type HomeworkGroup,
  type HomeworkLinkValidation,
} from '../../domain/homework'
import MobileShell from '../../shared/components/MobileShell.vue'
import { rootRoute, type MobileBottomNavItems, type MobileNavigationStack, type MobileRootRouteId, type MobileRoute } from '../../shared/navigation'
import type { MobileHostAdapter } from '../../shared/host'
import externalLinkIcon from '../../assets/homework-external-link.svg'
import expandChevronIcon from '../../assets/homework-expand-chevron.svg'
import HomeworkCompletion from './HomeworkCompletion.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import returnTodayChevronIcon from '../../assets/homework-return-today-chevron.svg'
import {
  canRestoreHomeworkFocus,
  homeworkFeedIdentity,
  type HomeworkFocusRequest,
} from './homework-focus'
import './homework-screen.pcss'

const props = withDefaults(defineProps<{
  homework: StudentHomework | null
  loading: boolean
  error: string | null
  navItems: MobileBottomNavItems
  offline?: boolean
  readOnly?: boolean
  updatedAt?: string | null
  route?: MobileRoute | null
  navigation?: MobileNavigationStack | null
  activeId?: MobileRootRouteId
  keyboardVisible?: boolean
  host?: MobileHostAdapter | null
  /** Stable adapter scope identity; stale requests never restore focus. */
  ownerKey?: string | null
  historical?: boolean
  canLoadPrevious?: boolean
  pendingIds?: ReadonlySet<string> | readonly string[]
  itemErrors?: Readonly<Record<string, string | null>>
  isItemPending?: (id: string) => boolean
  itemError?: (id: string) => string | null
  focusItemId?: string | null
  focusRequestId?: number | null
  unavailableMessage?: string | null
}>(), {
  offline: false,
  readOnly: false,
  updatedAt: null,
  route: null,
  navigation: null,
  activeId: 'homework',
  keyboardVisible: undefined as never,
  host: null,
  ownerKey: null,
  historical: false,
  canLoadPrevious: false,
  pendingIds: undefined as never,
  itemErrors: undefined as never,
  isItemPending: undefined as never,
  itemError: undefined as never,
  focusItemId: null,
  focusRequestId: null,
  unavailableMessage: null,
})

const emit = defineEmits<{
  complete: [item: StudentHomeworkItem, completed: boolean]
  retry: [item: StudentHomeworkItem]
  retryFeed: []
  openMaterial: [originalUrl: string, item: StudentHomeworkItem]
  previous: []
  returnToday: []
  navigate: [route: MobileRootRouteId]
  back: [route: MobileRoute | null]
}>()

const homeworkRoute = rootRoute('homework')
const shellOptionalProps = computed(() => props.keyboardVisible === undefined
  ? {}
  : { keyboardVisible: props.keyboardVisible })
const expandedIds = ref<Set<string>>(new Set())
const trackElements = new Map<string, HTMLElement>()
const cardHeadingElements = new Map<string, HTMLElement>()
const focusRequest = ref<HomeworkFocusRequest | null>(null)

const groups = computed<HomeworkGroup[]>(() => props.homework ? groupHomework(props.homework) : [])
const isReadOnly = computed(() => props.offline || props.readOnly)
const persistedUpdatedAt = computed(() => {
  if (!props.updatedAt || !Number.isFinite(Date.parse(props.updatedAt))) return null
  return new Date(props.updatedAt).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })
})

function itemPending(id: string): boolean {
  if (props.isItemPending) return props.isItemPending(id)
  const pendingIds = props.pendingIds
  if (!pendingIds) return false
  if ('has' in pendingIds) return pendingIds.has(id)
  return pendingIds.includes(id)
}

function getItemError(item: StudentHomeworkItem): string | null {
  return props.itemError?.(item.id) ?? props.itemErrors?.[item.id] ?? null
}

function iconStyle(icon: string): Record<string, string> {
  return { '--rct-icon-mask': `url("${icon}")` }
}

function isExpanded(id: string): boolean {
  return expandedIds.value.has(id)
}

function hasDescription(item: StudentHomeworkItem): boolean {
  return item.description.trim().length > 0
}

function toggleExpanded(id: string): void {
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}

function materialState(item: StudentHomeworkItem): HomeworkLinkValidation {
  return validateHomeworkLink(item.link)
}

function openMaterial(item: StudentHomeworkItem): void {
  const result = materialState(item)
  if (result.supported) emit('openMaterial', result.original, item)
}

function completionLabel(item: StudentHomeworkItem): string {
  if (itemPending(item.id)) return 'Сохраняем…'
  if (item.archived) return item.completed ? 'Выполнено · архив' : 'Архив · только чтение'
  if (item.completed) return 'Выполнено'
  if (isReadOnly.value) return 'Только просмотр'
  return 'Проведи, чтобы выполнить'
}

function requestCompletion(item: StudentHomeworkItem, desired: boolean): void {
  if (isReadOnly.value || item.archived || itemPending(item.id)) return
  rememberCompletionFocus(item, desired)
  emit('complete', item, desired)
}

function retryItem(item: StudentHomeworkItem): void {
  if (!isReadOnly.value && !item.archived && !itemPending(item.id)) {
    rememberCompletionFocus(item, !item.completed)
    emit('retry', item)
  }
}

function setTrackRef(id: string, element: unknown): void {
  if (typeof HTMLElement !== 'undefined' && element instanceof HTMLElement) {
    trackElements.set(id, element)
    return
  }
  // Vue can clear the previous ref after assigning the moved keyed node. Keep
  // a connected replacement so ACK reorder still has a focus target.
  if (!trackElements.get(id)?.isConnected) trackElements.delete(id)
}

function setCardHeadingRef(id: string, element: unknown): void {
  if (typeof HTMLElement !== 'undefined' && element instanceof HTMLElement) {
    cardHeadingElements.set(id, element)
    return
  }
  if (!cardHeadingElements.get(id)?.isConnected) cardHeadingElements.delete(id)
}

function rememberCompletionFocus(item: StudentHomeworkItem, expectedCompleted: boolean): void {
  focusRequest.value = {
    id: item.id,
    expectedCompleted,
    origin: typeof document === 'undefined' ? null : document.activeElement,
    feedIdentity: homeworkFeedIdentity(props.homework),
    ownerKey: props.ownerKey ?? null,
  }
}

function groupHasMonth(index: number): boolean {
  return index === 0 || groups.value[index - 1]?.monthLabel !== groups.value[index]?.monthLabel
}

function groupId(group: HomeworkGroup): string {
  return `homework-group-${group.key.replace(/[^a-zA-Z0-9_-]/g, '-')}`
}

const completionSignature = computed(() => {
  const feed = props.homework
  const items = feed?.items.map((item) => [
    item.id,
    item.completed ? '1' : '0',
    item.completedAt ?? '',
    itemPending(item.id) ? 'pending' : 'settled',
  ].join(':')).join('|') ?? ''
  return `${homeworkFeedIdentity(feed) ?? ''}|${items}`
})

function currentActiveElement(): Element | null {
  return typeof document === 'undefined' ? null : document.activeElement
}

function currentDocumentBody(): Element | null {
  return typeof document === 'undefined' ? null : document.body
}

function clearStaleFocusRequest(request: HomeworkFocusRequest): boolean {
  const feedIdentity = homeworkFeedIdentity(props.homework)
  const ownerKey = props.ownerKey ?? null
  if (request.feedIdentity !== feedIdentity || request.ownerKey !== ownerKey) {
    if (focusRequest.value === request) focusRequest.value = null
    return true
  }
  const activeElement = currentActiveElement()
  const body = currentDocumentBody()
  if (activeElement && activeElement !== body && activeElement !== request.origin) {
    if (focusRequest.value === request) focusRequest.value = null
    return true
  }
  return false
}

async function restoreCompletionFocus(): Promise<void> {
  const request = focusRequest.value
  if (!request || clearStaleFocusRequest(request)) return
  const feed = props.homework
  const item = feed?.items.find((entry) => entry.id === request.id)
  if (!item || itemPending(request.id)) return

  if (!canRestoreHomeworkFocus({
    request,
    item,
    pending: false,
    activeElement: currentActiveElement(),
    documentBody: currentDocumentBody(),
    feedIdentity: homeworkFeedIdentity(feed),
    ownerKey: props.ownerKey ?? null,
  })) return

  await nextTick()
  if (focusRequest.value !== request || clearStaleFocusRequest(request)) return
  const target = trackElements.get(request.id)
  if (!target || !target.isConnected) return
  target.focus()
  if (focusRequest.value === request) focusRequest.value = null
}

watch(completionSignature, () => { void restoreCompletionFocus() }, { flush: 'post' })

watch(
  () => [props.focusItemId, props.focusRequestId, props.homework?.items] as const,
  async ([id, requestId]) => {
    if (!id || requestId === null || !props.homework?.items.some((item) => item.id === id)) return
    const next = new Set(expandedIds.value)
    next.add(id)
    expandedIds.value = next
    await nextTick()
    if (props.focusItemId !== id || props.focusRequestId !== requestId) return
    const target = trackElements.get(id)
    if (target?.isConnected && !target.matches(':disabled')) target.focus()
    else {
      const heading = cardHeadingElements.get(id)
      if (heading?.isConnected) heading.focus()
    }
  },
  { flush: 'post', immediate: true },
)

watch(
  () => `${props.ownerKey ?? ''}|${props.route?.id ?? ''}|${props.activeId ?? ''}`,
  () => { focusRequest.value = null },
)

onBeforeUnmount(() => {
  focusRequest.value = null
  trackElements.clear()
  cardHeadingElements.clear()
})
</script>

<template>
  <MobileShell
    :route="route ?? homeworkRoute"
    :navigation="navigation"
    :nav-items="navItems"
    :active-id="activeId"
    v-bind="shellOptionalProps"
    :host="host"
    @navigate="emit('navigate', $event)"
    @back="emit('back', $event)"
  >
    <main
      class="homework-screen"
      aria-labelledby="homework-title"
    >
      <div class="homework-content">
        <h1 id="homework-title">
          Задания
        </h1>

        <StudentWarningBlock
          v-if="offline || readOnly"
          :title="offline ? 'Ты офлайн' : 'Только просмотр'"
          :message="offline ? `Задания доступны для просмотра.${persistedUpdatedAt ? ` Данные обновлены ${persistedUpdatedAt}.` : ''}` : 'Изменение выполнения сейчас недоступно.'"
        />

        <button
          v-if="canLoadPrevious && !historical"
          class="homework-previous"
          type="button"
          @click="emit('previous')"
        >
          Посмотреть предыдущие
        </button>

        <section
          v-if="loading && !homework"
          class="homework-skeleton"
          aria-label="Загружаем задания"
          role="status"
          aria-busy="true"
        >
          <div
            v-for="index in 3"
            :key="index"
            class="homework-skeleton__card"
            aria-hidden="true"
          >
            <span class="homework-skeleton__line" />
            <span class="homework-skeleton__line" />
            <span class="homework-skeleton__track" />
          </div>
        </section>
        <StudentWarningBlock
          v-else-if="error && !homework"
          severity="error"
          title="Не удалось получить задания"
          :message="error"
          action-label="Повторить"
          @action="emit('retryFeed')"
        />
        <StudentWarningBlock
          v-else-if="unavailableMessage"
          title="Задания недоступны"
          :message="unavailableMessage"
        />
        <section
          v-else-if="!homework || groups.length === 0"
          class="homework-state"
        >
          <h2>Заданий пока нет</h2>
          <p>Новые задания появятся здесь после публикации.</p>
        </section>
        <template v-else>
          <StudentWarningBlock
            v-if="error"
            severity="error"
            title="Не удалось обновить задания"
            :message="error"
            action-label="Повторить"
            @action="emit('retryFeed')"
          />

          <section
            v-for="(group, groupIndex) in groups"
            :key="group.key"
            class="homework-group"
            :aria-labelledby="groupId(group)"
          >
            <p
              v-if="groupHasMonth(groupIndex) && group.monthLabel"
              class="homework-month"
            >
              {{ group.monthLabel }}
            </p>
            <h2 :id="groupId(group)">
              {{ group.label }}
            </h2>
            <div class="homework-group__cards">
              <article
                v-for="item in group.items"
                :key="item.id"
                class="homework-card"
                :data-completion="item.completed ? 'completed' : 'open'"
                :data-pending="itemPending(item.id)"
                :data-material="item.link === null ? 'none' : materialState(item).supported ? 'available' : 'unsafe'"
              >
                <h3
                  :ref="(element) => setCardHeadingRef(item.id, element)"
                  tabindex="-1"
                >
                  {{ item.subject.name }}
                </h3>
                <p class="homework-card__title">
                  {{ item.title }}
                </p>
                <p
                  v-if="hasDescription(item) && isExpanded(item.id)"
                  :id="`homework-description-${item.id}`"
                  class="homework-card__description"
                >
                  {{ item.description }}
                </p>
                <div
                  v-if="item.link !== null || hasDescription(item)"
                  class="homework-card__actions"
                  :class="{ 'homework-card__actions--no-material': item.link === null }"
                >
                  <button
                    v-if="item.link !== null && materialState(item).supported"
                    class="homework-material"
                    type="button"
                    :aria-label="`Материалы к заданию: ${item.subject.name}`"
                    @click="openMaterial(item)"
                  >
                    <span
                      class="homework-icon homework-material__icon"
                      :style="iconStyle(externalLinkIcon)"
                      aria-hidden="true"
                    />
                    Материалы
                  </button>
                  <p
                    v-else-if="item.link !== null"
                    class="homework-material-error"
                    role="status"
                  >
                    Материалы недоступны: ссылка не поддерживается.
                  </p>
                  <button
                    v-if="hasDescription(item)"
                    class="homework-expand"
                    type="button"
                    :aria-expanded="isExpanded(item.id)"
                    :aria-controls="isExpanded(item.id) ? `homework-description-${item.id}` : undefined"
                    :aria-label="isExpanded(item.id) ? 'Свернуть описание' : 'Раскрыть описание'"
                    @click="toggleExpanded(item.id)"
                  >
                    <span
                      class="homework-icon homework-expand__icon"
                      :style="iconStyle(expandChevronIcon)"
                      aria-hidden="true"
                      :class="{ 'homework-expand__icon--open': isExpanded(item.id) }"
                    />
                  </button>
                </div>
                <HomeworkCompletion
                  :key="`${item.id}:${ownerKey ?? ''}`"
                  :completed="item.completed"
                  :disabled="isReadOnly || item.archived || itemPending(item.id) || !!getItemError(item)"
                  :pending="itemPending(item.id)"
                  :label="completionLabel(item)"
                  :subject="item.subject.name"
                  :reset-key="`${ownerKey ?? ''}:${homeworkFeedIdentity(homework) ?? ''}`"
                  @handle-ready="setTrackRef(item.id, $event)"
                  @complete="requestCompletion(item, $event)"
                />
                <StudentWarningBlock
                  v-if="getItemError(item)"
                  severity="error"
                  title="Отметка не сохранена"
                  :message="getItemError(item) ?? ''"
                  action-label="Повторить"
                  :action-disabled="isReadOnly || item.archived || itemPending(item.id)"
                  @action="retryItem(item)"
                />
              </article>
            </div>
          </section>
        </template>
      </div>

      <button
        v-if="historical"
        class="homework-return-today"
        type="button"
        aria-label="Вернуться к сегодняшним заданиям"
        @click="emit('returnToday')"
      >
        <span
          class="homework-icon homework-return-today__icon"
          :style="iconStyle(returnTodayChevronIcon)"
          aria-hidden="true"
        />
      </button>
    </main>
  </MobileShell>
</template>
