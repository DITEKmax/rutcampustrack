<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import AdminCheckbox from '../../shared/components/AdminCheckbox.vue';
import {
  notificationCatalogMatchesPreferences,
  notificationTypeSetsMatch,
} from './notifications-v2-models';
import type {
  NotificationCatalog,
  NotificationPreferencesV2,
  NotificationTypeId,
} from './notifications-v2-models';
import {
  changeNotificationChannelMode,
  changeNotificationDelivery,
  type NotificationDeliveryChannel,
  type NotificationPreferencesEdit,
} from './notification-preferences-draft';
import './notification-settings.pcss';

const props = defineProps<{
  catalog: NotificationCatalog;
  preferences: NotificationPreferencesV2;
  draft: NotificationPreferencesV2 | null;
  blocked?: boolean;
  busy?: boolean;
  error?: string | null;
  errorTone?: 'warning' | 'error';
  conflict?: NotificationPreferencesV2 | null;
  offline?: boolean;
  appPushAvailable?: boolean;
}>();
const emit = defineEmits<{
  edit: [change: NotificationPreferencesEdit, unifiedChannel?: NotificationDeliveryChannel];
  reload: [];
  reapply: [];
  colors: [];
}>();
const value = computed(() => props.draft ?? props.preferences);
const compatible = computed(
  () =>
    notificationCatalogMatchesPreferences(props.catalog, props.preferences) &&
    notificationCatalogMatchesPreferences(props.catalog, value.value),
);
const catalogChanged = computed(
  () =>
    !!props.blocked ||
    (!!props.conflict &&
      !notificationTypeSetsMatch(
        Object.keys(value.value.types),
        Object.keys(props.conflict.types),
      )),
);
const editingBlocked = computed(() =>
  Boolean(props.offline || props.conflict || props.blocked || !compatible.value),
);
const disabled = computed(() => Boolean(props.busy || editingBlocked.value));
const unavailable = computed(() =>
  value.value.splitChannels
    ? { appPush: props.appPushAvailable === false, telegram: !props.preferences.telegramLinked }
    : {
        appPush: props.appPushAvailable === false && !props.preferences.telegramLinked,
        telegram: false,
      },
);
const groups = computed(() =>
  compatible.value
    ? props.catalog.groups
        .map((group) => ({
          ...group,
          types: props.catalog.types
            .filter((type) => type.groupId === group.id)
            .map((type) => ({ ...type, preferences: value.value.types[type.id]! })),
        }))
        .filter((group) => group.types.length)
    : [],
);
const choiceOpen = ref(false);
const switchButton = ref<HTMLButtonElement | null>(null);
const choiceElement = ref<HTMLElement | null>(null);
const choiceId = useId();
watch(
  () => [props.preferences.revision, editingBlocked.value],
  () => {
    choiceOpen.value = false;
  },
);

function commitMode(split: boolean, keep?: NotificationDeliveryChannel): void {
  choiceOpen.value = false;
  emit(
    'edit',
    (latest) => changeNotificationChannelMode(latest, split, keep) ?? latest,
    split ? undefined : (keep ?? 'appPush'),
  );
}
async function switchMode(): Promise<void> {
  if (disabled.value || choiceOpen.value) return;
  const next = changeNotificationChannelMode(value.value, !value.value.splitChannels);
  if (next) commitMode(next.splitChannels);
  else {
    choiceOpen.value = true;
    await nextTick();
    choiceElement.value?.querySelector<HTMLButtonElement>('button')?.focus();
  }
}
function chooseColumn(channel: NotificationDeliveryChannel): void {
  if (disabled.value) return;
  commitMode(false, channel);
  void nextTick(() => switchButton.value?.focus());
}
function cancelChoice(): void {
  choiceOpen.value = false;
  void nextTick(() => switchButton.value?.focus());
}
function setDelivery(
  id: NotificationTypeId,
  channel: NotificationDeliveryChannel,
  enabled: boolean,
): void {
  if (editingBlocked.value || unavailable.value[channel] || choiceOpen.value) return;
  emit('edit', (latest) => changeNotificationDelivery(latest, id, channel, enabled));
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
      <span class="notification-settings__mode-label">Раздельные настройки</span>
      <span
        class="notification-settings__slider"
        aria-hidden="true"
      ><span /></span>
    </button>
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
      <p>
        {{
          catalogChanged
            ? 'Каталог уведомлений изменился. Черновик сохранён. Загрузи актуальные настройки, чтобы продолжить.'
            : 'Настройки изменились на другом устройстве. Твой выбор сохранён в черновике.'
        }}
      </p>
      <button
        type="button"
        :disabled="busy || offline"
        @click="emit('reload')"
      >
        Загрузить актуальные
      </button>
      <button
        type="button"
        :disabled="busy || offline || catalogChanged || blocked"
        @click="emit('reapply')"
      >
        Применить мой выбор
      </button>
    </div>
    <div
      v-else-if="error"
      class="notification-settings__notice"
      :class="{ 'notification-settings__notice--error': errorTone !== 'warning' }"
      :data-tone="errorTone ?? 'error'"
      role="alert"
    >
      <p>{{ error }}</p>
      <button
        type="button"
        :disabled="busy || offline"
        @click="emit('reload')"
      >
        Повторить
      </button>
    </div>
    <p
      v-if="offline"
      class="notification-settings__notice"
      role="status"
    >
      Нет подключения. Настройки доступны для просмотра.
    </p>
    <div>
      <div
        class="notification-settings__matrix-scroll"
        role="region"
        aria-label="Каналы доставки уведомлений"
        tabindex="0"
      >
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
                <span class="notification-settings__sr">Тип уведомления</span>
              </th><th
                scope="col"
                :data-unavailable="unavailable.appPush ? '' : undefined"
              >
                {{ value.splitChannels ? 'TMA/PWA' : 'Получать' }}
                <span
                  v-if="unavailable.appPush"
                  class="notification-settings__sr"
                >недоступно</span>
              </th><th
                v-if="value.splitChannels"
                scope="col"
                :data-unavailable="unavailable.telegram ? '' : undefined"
              >
                Telegram
                <span
                  v-if="unavailable.telegram"
                  class="notification-settings__sr"
                >недоступно</span>
              </th>
            </tr>
          </thead>
          <tbody
            v-for="group in groups"
            :key="group.id"
          >
            <tr class="notification-settings__sr">
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
              <td
                class="notification-settings__channel"
                :data-unavailable="unavailable.appPush ? '' : undefined"
              >
                <AdminCheckbox
                  :model-value="type.preferences.appPush"
                  :label="`${type.label}: ${value.splitChannels ? 'TMA/PWA' : 'TMA/PWA и Telegram'}`"
                  :disabled="editingBlocked || unavailable.appPush || choiceOpen"
                  @update:model-value="setDelivery(type.id, 'appPush', $event)"
                />
              </td>
              <td
                v-if="value.splitChannels"
                class="notification-settings__channel"
                :data-unavailable="unavailable.telegram ? '' : undefined"
              >
                <AdminCheckbox
                  :model-value="type.preferences.telegram"
                  :label="`${type.label}: Telegram`"
                  :disabled="editingBlocked || unavailable.telegram || choiceOpen"
                  @update:model-value="setDelivery(type.id, 'telegram', $event)"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <button
        class="notification-settings__colors"
        aria-label="Цвета уведомлений"
        type="button"
        :disabled="choiceOpen"
        @click="emit('colors')"
      >
        Цвета уведомлений<MobileIcon name="back" />
      </button>
    </div>
  </section>
</template>
