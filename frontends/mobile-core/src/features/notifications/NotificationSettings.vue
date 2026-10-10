<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue'
import AdminCheckbox from '../../shared/components/AdminCheckbox.vue'
import type { NotificationCatalog, NotificationPreferencesV2, NotificationPreferencesV2Update, NotificationTypeId } from './notifications-v2-models'
import { changeNotificationChannelMode, changeNotificationDelivery, notificationPreferencesUpdate, type NotificationDeliveryChannel } from './notification-preferences-draft'
import './notification-settings.pcss'

const props = defineProps<{
  catalog: NotificationCatalog
  preferences: NotificationPreferencesV2
  draft: NotificationPreferencesV2 | null
  busy?: boolean
  error?: string | null
  conflict?: NotificationPreferencesV2 | null
  offline?: boolean
  appPushAvailable?: boolean
}>()
const emit = defineEmits<{
  'update:draft': [value: NotificationPreferencesV2]
  save: [value: NotificationPreferencesV2Update]
  reload: []
  reapply: []
  colors: []
}>()
const value = computed(() => props.draft ?? props.preferences)
const disabled = computed(() => Boolean(props.busy || props.offline || props.conflict))
const groups = computed(() => props.catalog.groups.map((group) => ({
  ...group, types: props.catalog.types.filter((type) => type.groupId === group.id),
})).filter((group) => group.types.length))
const choiceOpen = ref(false)
const switchButton = ref<HTMLButtonElement | null>(null)
const choiceElement = ref<HTMLElement | null>(null)
const choiceId = useId()
watch(() => props.preferences.revision, () => { choiceOpen.value = false })

function save(next = value.value): void {
  if (disabled.value || choiceOpen.value) return
  emit('save', notificationPreferencesUpdate(next, props.preferences.revision))
}
function commitMode(next: NotificationPreferencesV2): void {
  choiceOpen.value = false
  emit('update:draft', next)
  save(next)
}
async function switchMode(): Promise<void> {
  if (disabled.value || choiceOpen.value) return
  const next = changeNotificationChannelMode(value.value, !value.value.splitChannels)
  if (next) commitMode(next)
  else {
    choiceOpen.value = true
    await nextTick()
    choiceElement.value?.querySelector<HTMLButtonElement>('button')?.focus()
  }
}
function chooseColumn(channel: NotificationDeliveryChannel): void {
  if (disabled.value) return
  const next = changeNotificationChannelMode(value.value, false, channel)
  if (next) commitMode(next)
  void nextTick(() => switchButton.value?.focus())
}
function cancelChoice(): void {
  choiceOpen.value = false
  void nextTick(() => switchButton.value?.focus())
}
function setDelivery(id: NotificationTypeId, channel: NotificationDeliveryChannel, enabled: boolean): void {
  if (disabled.value || choiceOpen.value) return
  emit('update:draft', changeNotificationDelivery(value.value, id, channel, enabled))
}
</script>

<template>
  <section
    class="notification-settings"
    aria-label="Настройки доставки"
    :aria-busy="busy || false"
  >
    <button
      ref="switchButton"
      class="notification-settings__mode"
      type="button"
      role="switch"
      :aria-checked="value.splitChannels"
      :aria-controls="choiceOpen ? choiceId : undefined"
      :disabled="disabled || choiceOpen"
      @click="switchMode"
    >
      <span class="notification-settings__mode-label">Раздельные настройки<span class="notification-settings__mode-state">{{ value.splitChannels ? 'Включены' : 'Выключены' }}</span></span>
      <span
        class="notification-settings__slider"
        aria-hidden="true"
      ><span /></span>
    </button>
    <p
      class="notification-settings__status"
      role="status"
    >
      {{ value.splitChannels ? 'Каналы настроены отдельно' : 'Одинаковые настройки каналов' }}
    </p>
    <div
      v-if="choiceOpen"
      :id="choiceId"
      ref="choiceElement"
      class="notification-settings__choice"
      role="group"
      aria-label="Объединить настройки каналов"
      @keydown.esc.prevent="cancelChoice"
    >
      <p>В каналах выбраны разные уведомления</p>
      <button
        type="button"
        :disabled="disabled"
        @click="chooseColumn('appPush')"
      >
        Оставить как в TMA/PWA
      </button>
      <button
        type="button"
        :disabled="disabled"
        @click="chooseColumn('telegram')"
      >
        Оставить как в Telegram
      </button>
      <button
        type="button"
        @click="cancelChoice"
      >
        Отмена
      </button>
    </div>
    <div
      v-if="conflict"
      class="notification-settings__notice"
      role="alert"
    >
      <p>Настройки изменились на другом устройстве. Твой выбор сохранён в черновике.</p>
      <button
        type="button"
        :disabled="busy || offline"
        @click="emit('reload')"
      >
        Загрузить актуальные
      </button>
      <button
        type="button"
        :disabled="busy || offline"
        @click="emit('reapply')"
      >
        Применить мой выбор
      </button>
    </div>
    <p
      v-else-if="error"
      class="notification-settings__notice notification-settings__notice--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="offline"
      class="notification-settings__notice"
      role="status"
    >
      Нет подключения. Настройки доступны для просмотра.
    </p>
    <div
      v-if="appPushAvailable === false || !preferences.telegramLinked"
      class="notification-settings__capability"
      role="status"
    >
      <p v-if="appPushAvailable === false">
        Web Push недоступен на этом устройстве
      </p>
      <p v-if="!preferences.telegramLinked">
        Telegram не привязан
      </p>
    </div>
    <slot name="capability" />
    <form @submit.prevent="save()">
      <table
        class="notification-settings__matrix"
        :data-split="value.splitChannels"
      >
        <caption class="notification-settings__sr">
          Доставка по типам уведомлений
        </caption>
        <colgroup>
          <col><col class="notification-settings__channel-col"><col
            v-if="value.splitChannels"
            class="notification-settings__channel-col"
          >
        </colgroup>
        <thead>
          <tr>
            <th scope="col">
              Уведомления
            </th><th scope="col">
              {{ value.splitChannels ? 'TMA/PWA' : 'Получать' }}
            </th><th
              v-if="value.splitChannels"
              scope="col"
            >
              Telegram
            </th>
          </tr>
        </thead>
        <tbody
          v-for="group in groups"
          :key="group.id"
        >
          <tr class="notification-settings__group">
            <th
              :colspan="value.splitChannels ? 3 : 2"
              scope="rowgroup"
            >
              {{ group.label }}
            </th>
          </tr>
          <tr
            v-for="type in group.types"
            :key="type.id"
            class="notification-settings__row"
          >
            <th scope="row">
              {{ type.label }}
            </th>
            <td class="notification-settings__channel">
              <AdminCheckbox
                :model-value="value.types[type.id].appPush"
                :label="`${type.label}: ${value.splitChannels ? 'TMA/PWA' : 'TMA/PWA и Telegram'}`"
                :disabled="disabled || choiceOpen"
                @update:model-value="setDelivery(type.id, 'appPush', $event)"
              />
            </td>
            <td
              v-if="value.splitChannels"
              class="notification-settings__channel"
            >
              <AdminCheckbox
                :model-value="value.types[type.id].telegram"
                :label="`${type.label}: Telegram`"
                :disabled="disabled || choiceOpen"
                @update:model-value="setDelivery(type.id, 'telegram', $event)"
              />
            </td>
          </tr>
        </tbody>
      </table>
      <button
        class="notification-settings__colors"
        type="button"
        :disabled="busy || choiceOpen"
        @click="emit('colors')"
      >
        Цвета уведомлений<span aria-hidden="true">›</span>
      </button>
      <button
        class="notification-settings__save"
        type="submit"
        :disabled="disabled || choiceOpen"
      >
        {{ busy ? 'Сохраняем…' : 'Сохранить настройки' }}
      </button>
    </form>
  </section>
</template>
