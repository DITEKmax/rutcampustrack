<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import {
  notificationCatalogMatchesPreferences,
  notificationTypeSetsMatch,
} from './notifications-v2-models';
import type {
  NotificationCatalog,
  NotificationPaletteId,
  NotificationPreferencesV2,
  NotificationTypeId,
} from './notifications-v2-models';
import { changeNotificationColor, type NotificationPreferencesEdit } from './notification-preferences-draft';
import './notification-colors.pcss';

const props = defineProps<{
  studentUi?: boolean;
  catalog: NotificationCatalog;
  preferences: NotificationPreferencesV2;
  draft: NotificationPreferencesV2 | null;
  blocked?: boolean;
  busy?: boolean;
  error?: string | null;
  errorTone?: 'warning' | 'error';
  conflict?: NotificationPreferencesV2 | null;
  offline?: boolean;
}>();
const emit = defineEmits<{
  edit: [change: NotificationPreferencesEdit];
  reload: [];
  reapply: [];
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
const disabled = computed(() =>
  Boolean(props.offline || props.conflict || props.blocked || !compatible.value),
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
const selectedType = ref<NotificationTypeId | null>(null);
const pageId = useId();
function paletteLabel(id: NotificationPaletteId): string {
  return props.catalog.palette.find((entry) => entry.id === id)?.label ?? id;
}
function dotStyle(id: NotificationPaletteId): Record<string, string> {
  return { '--notification-dot-color': `var(--m-notification-dot-${id})` };
}
async function toggleType(id: NotificationTypeId): Promise<void> {
  selectedType.value = selectedType.value === id ? null : id;
  if (selectedType.value) {
    await nextTick();
    document
      .getElementById(`${pageId}-${id}`)
      ?.querySelector<HTMLButtonElement>('[aria-pressed="true"]')
      ?.focus();
  }
}
function setColor(id: NotificationTypeId, colorId: NotificationPaletteId): void {
  if (disabled.value) return;
  emit('edit', (latest) => changeNotificationColor(latest, id, colorId));
}
</script>

<template>
  <section
    class="notification-colors"
    aria-label="Цвета уведомлений"
    :aria-busy="busy || false"
  >
    <div
      v-if="conflict"
      class="notification-colors__notice"
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
      class="notification-colors__notice"
      :class="{ 'notification-colors__notice--error': errorTone !== 'warning' }"
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
      class="notification-colors__notice"
      role="status"
    >
      Нет подключения. Цвета доступны для просмотра.
    </p>
    <section
      v-for="group in groups"
      :key="group.id"
      class="notification-colors__group"
      :aria-labelledby="`${pageId}-group-${group.id}`"
    >
      <h2 :id="`${pageId}-group-${group.id}`">
        {{ group.label }}
      </h2>
      <div
        v-for="type in group.types"
        :key="type.id"
        class="notification-colors__item"
      >
        <button
          type="button"
          class="notification-colors__type"
          :aria-label="`${type.label}: ${paletteLabel(type.preferences.colorId)}`"
          :aria-expanded="selectedType === type.id"
          :aria-controls="selectedType === type.id ? `${pageId}-${type.id}` : undefined"
          @click="toggleType(type.id)"
        >
          <span
            class="notification-colors__dot"
            :style="dotStyle(type.preferences.colorId)"
            aria-hidden="true"
          />
          <span class="notification-colors__type-text">{{ type.label }}</span>
          <MobileIcon
            class="notification-colors__chevron"
            name="back"
          />
        </button>
        <fieldset
          v-if="selectedType === type.id"
          :id="`${pageId}-${type.id}`"
          class="notification-colors__palette"
          :disabled="disabled"
        >
          <legend>Цвет: {{ type.label }}</legend>
          <div class="notification-colors__swatches">
            <button
              v-for="color in catalog.palette"
              :key="color.id"
              type="button"
              class="notification-colors__swatch"
              :style="studentUi ? dotStyle(color.id) : undefined"
              :aria-label="color.label"
              :aria-pressed="type.preferences.colorId === color.id"
              @click="setColor(type.id, color.id)"
            >
              <svg
                v-if="studentUi && type.preferences.colorId === color.id"
                class="notification-colors__selected"
                viewBox="0 0 16 16"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="m4 8 3 3 5-6" />
              </svg>
              <template v-if="!studentUi">
                <span
                  class="notification-colors__dot"
                  :style="dotStyle(color.id)"
                  aria-hidden="true"
                />
                <span>{{ color.label }}</span>
                <span
                  class="notification-colors__selected"
                  aria-hidden="true"
                >{{ type.preferences.colorId === color.id ? '✓' : '' }}</span>
              </template>
            </button>
          </div>
          <button
            v-if="!studentUi"
            type="button"
            class="notification-colors__reset"
            @click="setColor(type.id, 'violet')"
          >
            Сбросить: фиолетовый
          </button>
        </fieldset>
      </div>
    </section>
  </section>
</template>
