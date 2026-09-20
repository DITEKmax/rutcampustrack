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
import handleIcon from '../../assets/homework-handle.svg'
import completedHandleIcon from '../../assets/homework-handle-completed.svg'
import directionIcon from '../../assets/homework-direction.svg'
import checkIcon from '../../assets/homework-check.svg'
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
}>(), {
  offline: false,
  readOnly: false,
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
const focusRequest = ref<HomeworkFocusRequest | null>(null)
const drag = ref<{ id: string; startX: number; latestX: number; pointerId: number; width: number } | null>(null)
const suppressClickId = ref<string | null>(null)

const groups = computed<HomeworkGroup[]>(() => props.homework ? groupHomework(props.homework) : [])
const isReadOnly = computed(() => props.offline || props.readOnly)

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
  if (item.completed) return 'Выполнено'
  if (isReadOnly.value) return 'Доступно только онлайн'
  return 'Проведи, чтобы выполнить'
}

function completionAriaLabel(item: StudentHomeworkItem): string {
  if (itemPending(item.id)) return `Сохраняем состояние задания «${item.subject.name}»`
  if (isReadOnly.value) return `Задание «${item.subject.name}». Изменение доступно только онлайн`
  return item.completed
    ? `Снять отметку «Выполнено» с задания «${item.subject.name}»`
    : `Отметить задание «${item.subject.name}» выполненным`
}

function requestCompletion(item: StudentHomeworkItem): void {
  if (isReadOnly.value || itemPending(item.id)) return
  rememberCompletionFocus(item, !item.completed)
  emit('complete', item, !item.completed)
}

function retryItem(item: StudentHomeworkItem): void {
  if (!isReadOnly.value && !itemPending(item.id)) {
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

function rememberCompletionFocus(item: StudentHomeworkItem, expectedCompleted: boolean): void {
  focusRequest.value = {
    id: item.id,
    expectedCompleted,
    origin: typeof document === 'undefined' ? null : document.activeElement,
    feedIdentity: homeworkFeedIdentity(props.homework),
    ownerKey: props.ownerKey ?? null,
  }
}

function beginDrag(item: StudentHomeworkItem, event: PointerEvent): void {
  if (isReadOnly.value || itemPending(item.id)) return
  const target = event.currentTarget
  if (!(target instanceof HTMLElement)) return
  const width = target.getBoundingClientRect().width
  drag.value = { id: item.id, startX: event.clientX, latestX: event.clientX, pointerId: event.pointerId, width }
  target.setPointerCapture?.(event.pointerId)
}

function moveDrag(item: StudentHomeworkItem, event: PointerEvent): void {
  if (drag.value?.id !== item.id || drag.value.pointerId !== event.pointerId) return
  drag.value.latestX = event.clientX
}

function finishDrag(item: StudentHomeworkItem, event: PointerEvent): void {
  const current = drag.value
  if (!current || current.id !== item.id || current.pointerId !== event.pointerId) return
  const distance = current.latestX - current.startX
  const threshold = current.width * 0.58
  if (Math.abs(distance) >= threshold) {
    suppressClickId.value = item.id
    const completed = item.completed ? distance > 0 ? true : false : distance > 0
    if (completed !== item.completed) requestCompletion(item)
  }
  drag.value = null
}

function cancelDrag(item: StudentHomeworkItem, event: PointerEvent): void {
  if (drag.value?.id === item.id && drag.value.pointerId === event.pointerId) drag.value = null
}

function clickCompletion(item: StudentHomeworkItem): void {
  if (suppressClickId.value === item.id) {
    suppressClickId.value = null
    return
  }
  requestCompletion(item)
}

function keyboardCompletion(item: StudentHomeworkItem): void {
  suppressClickId.value = null
  requestCompletion(item)
}

function dragOffset(item: StudentHomeworkItem): string {
  const current = drag.value
  if (!current || current.id !== item.id) return item.completed ? 'calc(100% - 3rem)' : '0.25rem'
  const distance = current.latestX - current.startX
  const rootRem = Number.parseFloat(getComputedStyle(document.documentElement).fontSize) || 16
  const maxOffset = Math.max(0, current.width - rootRem * 3.25)
  const initial = item.completed ? maxOffset : 0
  const offset = Math.min(maxOffset, Math.max(0, initial + distance))
  return `${offset}px`
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
  () => `${props.ownerKey ?? ''}|${props.route?.id ?? ''}|${props.activeId ?? ''}`,
  () => { focusRequest.value = null },
)

onBeforeUnmount(() => {
  focusRequest.value = null
  trackElements.clear()
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

        <p
          v-if="offline"
          class="homework-status homework-status--offline"
          role="status"
        >
          Офлайн · задания доступны только для просмотра
        </p>

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
          class="homework-state"
          aria-live="polite"
        >
          <span
            class="homework-state__spinner"
            aria-hidden="true"
          />
          Загружаем задания…
        </section>
        <section
          v-else-if="error && !homework"
          class="homework-state homework-state--error"
          role="alert"
        >
          <h2>Не удалось получить задания</h2>
          <p>{{ error }}</p>
          <button
            type="button"
            @click="emit('retryFeed')"
          >
            Повторить
          </button>
        </section>
        <section
          v-else-if="!homework || groups.length === 0"
          class="homework-state"
        >
          <h2>Заданий пока нет</h2>
          <p>Новые задания появятся здесь после публикации.</p>
        </section>
        <template v-else>
          <p
            v-if="error"
            class="homework-status homework-status--error"
            role="alert"
          >
            {{ error }}
            <button
              type="button"
              @click="emit('retryFeed')"
            >
              Повторить
            </button>
          </p>

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
                <h3>{{ item.subject.name }}</h3>
                <p
                  class="homework-card__description"
                  :class="{ 'homework-card__description--expanded': isExpanded(item.id) }"
                >
                  {{ item.description }}
                </p>
                <div class="homework-card__actions">
                  <button
                    v-if="item.link !== null && materialState(item).supported"
                    class="homework-material"
                    type="button"
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
                    class="homework-expand"
                    type="button"
                    :aria-expanded="isExpanded(item.id)"
                    :aria-controls="`homework-description-${item.id}`"
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
                <p
                  v-if="isExpanded(item.id)"
                  :id="`homework-description-${item.id}`"
                  class="homework-card__disclosure"
                >
                  {{ item.title }}
                </p>
                <button
                  :ref="(element) => setTrackRef(item.id, element)"
                  class="homework-completion"
                  :class="{ 'homework-completion--completed': item.completed, 'homework-completion--readonly': isReadOnly }"
                  type="button"
                  :disabled="isReadOnly || itemPending(item.id)"
                  :aria-label="completionAriaLabel(item)"
                  :aria-pressed="item.completed"
                  :data-state="itemPending(item.id) ? 'pending' : item.completed ? 'completed' : 'open'"
                  @click="clickCompletion(item)"
                  @keydown.enter.prevent="keyboardCompletion(item)"
                  @keydown.space.prevent="keyboardCompletion(item)"
                  @pointerdown="beginDrag(item, $event)"
                  @pointermove="moveDrag(item, $event)"
                  @pointerup="finishDrag(item, $event)"
                  @pointercancel="cancelDrag(item, $event)"
                >
                  <span class="homework-completion__label">{{ completionLabel(item) }}</span>
                  <span
                    class="homework-icon homework-completion__handle"
                    :class="{ 'homework-completion__handle--completed': item.completed }"
                    :style="{ ...iconStyle(item.completed ? completedHandleIcon : handleIcon), insetInlineStart: dragOffset(item) }"
                  />
                  <span
                    v-if="item.completed"
                    class="homework-icon homework-completion__check"
                    :style="iconStyle(checkIcon)"
                    aria-hidden="true"
                  />
                  <span
                    v-else
                    class="homework-icon homework-completion__direction"
                    :style="iconStyle(directionIcon)"
                    aria-hidden="true"
                  />
                </button>
                <p
                  v-if="getItemError(item)"
                  class="homework-card__error"
                  role="alert"
                >
                  {{ getItemError(item) }}
                  <button
                    type="button"
                    @click="retryItem(item)"
                  >
                    Повторить
                  </button>
                </p>
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
