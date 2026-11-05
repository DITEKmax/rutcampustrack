<script setup lang="ts">
import NotificationInboxCard from './NotificationInboxCard.vue';
import NotificationReadIllustration from './NotificationReadIllustration.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import type { NotificationItem, NotificationPreferencesV2 } from './notifications-v2-models';
import './notification-inbox.pcss';

withDefaults(
  defineProps<{
    studentUi?: boolean;
    items: readonly NotificationItem[];
    preferences: NotificationPreferencesV2 | null;
    contextKey: string;
    loading?: boolean;
    loadingMore?: boolean;
    error?: { message: string; tone: 'warning' | 'error' } | null;
    pendingIds?: ReadonlySet<string>;
    offline?: boolean;
    nextCursor?: string | null;
    unreadOnly?: boolean;
    canMarkAllRead?: boolean;
    markAllPending?: boolean;
    canOpenAction?: boolean;
  }>(),
  {
    loading: false,
    loadingMore: false,
    error: null,
    pendingIds: () => new Set<string>(),
    offline: false,
    nextCursor: null,
    unreadOnly: false,
    canMarkAllRead: false,
    markAllPending: false,
    canOpenAction: true,
  },
);
const emit = defineEmits<{
  'read-state': [item: NotificationItem, read: boolean];
  'open-item': [item: NotificationItem];
  reload: [];
  'load-more': [];
  'mark-all-read': [];
  filter: [unreadOnly: boolean];
}>();
</script>

<template>
  <section
    class="notification-inbox"
    aria-label="История уведомлений"
    :aria-busy="loading || loadingMore"
  >
    <div class="notification-inbox__toolbar">
      <div
        class="notification-inbox__filters"
        role="group"
        aria-label="Показать уведомления"
      >
        <button
          type="button"
          class="notification-inbox__button"
          :aria-pressed="!unreadOnly"
          :disabled="loading || offline"
          @click="emit('filter', false)"
        >
          Все
        </button>
        <button
          type="button"
          class="notification-inbox__button"
          :aria-pressed="unreadOnly"
          :disabled="loading || offline"
          @click="emit('filter', true)"
        >
          Непрочитанные
        </button>
      </div>
      <button
        v-if="canMarkAllRead"
        type="button"
        class="notification-inbox__button notification-inbox__mark-all"
        :aria-label="markAllPending ? 'Отмечаем всё прочитанным' : 'Отметить всё прочитанным'"
        title="Отметить всё прочитанным"
        :aria-busy="markAllPending"
        :disabled="markAllPending || offline || loading || pendingIds.size > 0"
        @click="emit('mark-all-read')"
      >
        <svg
          v-if="studentUi"
          class="notification-inbox__mark-all-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.75"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="m3 12 4 4L17 6M11 16l10-10" />
        </svg>
        <template v-else>{{ markAllPending ? 'Отмечаем…' : 'Прочитать все' }}</template>
      </button>
    </div>
    <div
      v-if="error || offline"
      class="notification-inbox__notice"
      :data-tone="error?.tone ?? 'warning'"
      :role="error?.tone === 'error' ? 'alert' : 'status'"
    >
      <MobileIcon name="warning" />
      <p>{{ error?.message ?? 'Нет подключения. Показаны сохранённые уведомления.' }}</p>
      <button
        type="button"
        class="notification-inbox__button notification-inbox__reload"
        :disabled="loading || offline"
        @click="emit('reload')"
      >
        <MobileIcon name="retry" />Обновить
      </button>
    </div>
    <p
      v-if="loading"
      class="notification-inbox__state"
      role="status"
    >
      Загружаем уведомления…
    </p>
    <div
      v-if="!loading && !error && !offline && !items.length"
      class="notification-inbox__state notification-inbox__state--empty"
      role="status"
    >
      <NotificationReadIllustration v-if="studentUi && unreadOnly" />
      <p>{{ unreadOnly ? 'Непрочитанных уведомлений нет.' : 'Уведомлений пока нет.' }}</p>
    </div>
    <ul
      v-if="items.length"
      :key="contextKey"
      class="notification-inbox__list"
    >
      <li
        v-for="item in items"
        :key="item.id"
      >
        <NotificationInboxCard
          :item="item"
          :color-id="preferences?.types[item.type]?.colorId ?? 'violet'"
          :pending="pendingIds.has(item.id) || markAllPending"
          :offline="offline"
          :can-open-action="canOpenAction"
          @read-state="(value, read) => emit('read-state', value, read)"
          @open-item="emit('open-item', $event)"
        />
      </li>
    </ul>
    <button
      v-if="nextCursor"
      type="button"
      class="notification-inbox__button notification-inbox__load-more"
      :disabled="loading || loadingMore || offline"
      @click="emit('load-more')"
    >
      {{ loadingMore ? 'Загружаем…' : 'Показать ещё' }}
    </button>
  </section>
</template>
