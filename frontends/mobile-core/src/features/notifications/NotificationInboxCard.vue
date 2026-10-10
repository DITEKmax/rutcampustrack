<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import type { NotificationItem, NotificationPaletteId } from './notifications-v2-models'
import { NotificationInboxHold } from './notification-inbox-gesture'
import './notification-inbox.pcss'

const props = withDefaults(defineProps<{
  item: NotificationItem
  colorId?: NotificationPaletteId
  pending?: boolean
  offline?: boolean
  canOpenAction?: boolean
}>(), { colorId: 'violet', pending: false, offline: false, canOpenAction: true })
const emit = defineEmits<{
  'read-state': [item: NotificationItem, read: boolean]
  'open-item': [item: NotificationItem]
}>()
const expanded = ref(false)
const menuOpen = ref(false)
const menuAction = ref<HTMLButtonElement | null>(null)
const mainAction = ref<HTMLButtonElement | null>(null)
const detailsId = `${useId()}-details`
const menuId = `${useId()}-menu`
const instructionsId = `${useId()}-instructions`
const unread = computed(() => props.item.readAt === null)
const commandsDisabled = computed(() => props.pending || props.offline)
const readRequestedRevision = ref<string | null>(null)
const displayedSummary = computed(() => props.item.summary?.trim() && props.item.summary.trim() !== props.item.heading.trim() ? props.item.summary : null)
const sentLabel = computed(() => {
  const date = new Date(props.item.sentAt)
  return Number.isNaN(date.getTime()) ? 'Время неизвестно' : new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(date)
})
const hold = new NotificationInboxHold(() => { menuOpen.value = true })

function expand(): void {
  expanded.value = !expanded.value
  if (expanded.value && unread.value && !commandsDisabled.value && readRequestedRevision.value !== props.item.revision) {
    readRequestedRevision.value = props.item.revision
    emit('read-state', props.item, true)
  }
}
function activate(): void {
  if (props.item.action) {
    if (!commandsDisabled.value && props.canOpenAction) emit('open-item', props.item)
  } else expand()
}
function beginHold(event: PointerEvent): void {
  if (unread.value || commandsDisabled.value || event.button !== 0 || !event.isPrimary) return
  hold.start(event.pointerId, event.clientX, event.clientY)
}
function suppressHeldClick(event: MouseEvent): void {
  if (!hold.consumeClick(event.detail > 0)) return
  event.preventDefault()
  event.stopImmediatePropagation()
}
function showKeyboardMenu(event: KeyboardEvent): void {
  if (unread.value || commandsDisabled.value || !(event.key === 'ContextMenu' || (event.shiftKey && event.key === 'F10'))) return
  event.preventDefault()
  menuOpen.value = true
}
function markUnread(): void {
  if (unread.value || commandsDisabled.value) return
  menuOpen.value = false
  emit('read-state', props.item, false)
  mainAction.value?.focus()
}
function closeMenu(): void {
  if (!menuOpen.value) return
  menuOpen.value = false
  mainAction.value?.focus()
}
function cancelHold(): void { hold.cancel() }
watch(menuAction, (element) => { if (element) element.focus() })
watch(() => props.item.id, () => {
  expanded.value = false
  menuOpen.value = false
  readRequestedRevision.value = null
  hold.cancel()
})
watch(() => props.item.revision, () => { readRequestedRevision.value = null; hold.cancel() })
watch(() => [props.pending, props.offline, props.item.readAt], () => { hold.cancel(); menuOpen.value = false })
onMounted(() => { window.addEventListener('scroll', cancelHold, true) })
onBeforeUnmount(() => { hold.cancel(); window.removeEventListener('scroll', cancelHold, true) })
</script>

<template>
  <article
    class="notification-card"
    :data-unread="unread"
    :aria-busy="pending"
    @click.capture="suppressHeldClick"
    @keydown.esc="closeMenu"
  >
    <div class="notification-card__row">
      <button
        ref="mainAction"
        type="button"
        class="notification-card__main"
        :disabled="Boolean(item.action) && commandsDisabled"
        :aria-disabled="Boolean(item.action) && !canOpenAction"
        :aria-describedby="instructionsId"
        :aria-expanded="item.action ? undefined : expanded"
        :aria-controls="item.action ? undefined : detailsId"
        @click="activate"
        @pointerdown="beginHold"
        @pointermove="hold.move($event.pointerId, $event.clientX, $event.clientY)"
        @pointerup="hold.end($event.pointerId)"
        @pointercancel="cancelHold"
        @pointerleave="cancelHold"
        @keydown="showKeyboardMenu"
      >
        <span
          class="notification-card__dot"
          :data-color="colorId"
          aria-hidden="true"
        />
        <span class="notification-card__compact">
          <span class="notification-card__heading">{{ item.heading }}</span>
          <time
            class="notification-card__time"
            :datetime="item.sentAt"
          >{{ sentLabel }}</time>
        </span>
      </button>
      <button
        type="button"
        class="notification-card__disclosure"
        :aria-label="`${expanded ? 'Свернуть' : 'Подробности'}: ${item.heading}`"
        :aria-expanded="expanded"
        :aria-controls="detailsId"
        @click="expand"
      >
        <MobileIcon name="chevron-down" />
      </button>
      <button
        v-if="!unread"
        type="button"
        class="notification-card__more"
        :disabled="commandsDisabled"
        :aria-label="`Действия: ${item.heading}`"
        :aria-expanded="menuOpen"
        :aria-controls="menuId"
        @click="menuOpen = !menuOpen"
      >
        <MobileIcon name="more" />
      </button>
    </div>
    <span
      :id="instructionsId"
      class="notification-inbox__sr-only"
    >
      {{ unread ? 'Не прочитано' : 'Прочитано. Действие «Отметить непрочитанным» доступно через кнопку действий или Shift и F10.' }}
    </span>
    <div
      :id="detailsId"
      :hidden="!expanded"
      class="notification-card__details"
    >
      <p
        v-if="displayedSummary"
        class="notification-card__summary"
      >
        {{ displayedSummary }}
      </p>
      <dl
        v-if="item.details.length"
        class="notification-card__facts"
      >
        <div
          v-for="(field, index) in item.details"
          :key="index"
        >
          <dt v-if="field.label">
            {{ field.label }}
          </dt>
          <dd>{{ field.value }}</dd>
        </div>
      </dl>
      <p
        v-if="!displayedSummary && !item.details.length"
        class="notification-card__hint"
      >
        Дополнительных сведений нет.
      </p>
      <p
        v-if="item.action && !canOpenAction"
        class="notification-card__hint"
        role="status"
      >
        Переход сейчас недоступен.
      </p>
    </div>
    <div
      v-if="menuOpen && !unread"
      :id="menuId"
      class="notification-card__menu"
    >
      <button
        ref="menuAction"
        type="button"
        class="notification-inbox__button"
        :disabled="commandsDisabled"
        @click="markUnread"
      >
        Отметить непрочитанным
      </button>
    </div>
  </article>
</template>
