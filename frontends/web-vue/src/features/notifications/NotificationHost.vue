<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import { webSession } from '../../auth/session';
import { webNotifications } from './notifications';
import NotificationRecovery from './NotificationRecovery.vue';
const n = webNotifications;
const popover = ref<HTMLElement | null>(null);
const expanded = ref(false);
const id = useId();
const label = computed(() =>
  n.value?.count.value === null || !n.value
    ? 'Уведомления, счётчик недоступен'
    : n.value.count.value > 0
      ? `Уведомления, непрочитанных: ${n.value.count.value}`
      : 'Уведомления',
);
function toggled(event: Event) {
  expanded.value = (event as ToggleEvent).newState === 'open';
}
function close() {
  if (popover.value?.matches(':popover-open')) popover.value.hidePopover();
}
watch(webSession.identity, close);
function notificationDate(value: string) {
  return new Intl.DateTimeFormat('ru', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}
</script>
<template>
  <button
    class="web-button web-notification-bell"
    :aria-label="label"
    :aria-expanded="expanded"
    :aria-controls="id"
    :popovertarget="id"
  >
    <MobileIcon name="bell" /><span
      v-if="n?.count.value"
      class="web-notification-badge"
      aria-hidden="true"
    >{{ n.count.value > 99 ? '99+' : n.count.value }}</span>
  </button>
  <div
    :id="id"
    ref="popover"
    popover="auto"
    role="dialog"
    :aria-labelledby="id + '-title'"
    class="web-notification-popover web-stack"
    @toggle="toggled"
  >
    <div class="web-notification-heading">
      <h2 :id="id + '-title'">Уведомления</h2>
      <span v-if="n?.count.value" class="web-notification-total" aria-hidden="true">{{ n.count.value }}</span>
    </div><NotificationRecovery /><p
      v-if="n?.error.value"
      class="web-error"
      role="alert"
    >
      {{ n.error.value }}
    </p><p
      v-if="n && !n.loading.value && !n.error.value && !n.items.value.length"
      class="web-caption"
    >
      Уведомлений пока нет.
    </p><ul class="web-notification-preview">
      <li
        v-for="item in n?.items.value.slice(0, 3)"
        :key="item.id"
        :class="{ 'is-unread': item.readAt === null }"
      >
        <div class="web-notification-preview-heading">
          <h3>{{ item.heading }}<span v-if="item.readAt === null" class="web-sr-only"> · Не прочитано</span></h3>
          <span v-if="item.readAt === null" class="web-notification-unread-dot" aria-hidden="true" />
        </div>
        <p v-if="item.summary && item.summary !== item.heading" class="web-notification-summary">{{ item.summary }}</p>
        <time class="web-caption" :datetime="item.sentAt">{{ notificationDate(item.sentAt) }}</time>
      </li>
    </ul><RouterLink
      class="web-button web-notification-history"
      :to="{ name: 'notifications' }"
      @click="close"
    >
      Вся история
    </RouterLink>
  </div>
  <aside
    v-if="n?.toasts.value.length"
    class="web-notification-toasts"
    aria-label="Новые уведомления"
    aria-live="polite"
    aria-relevant="additions"
  >
    <article
      v-for="toast in n.toasts.value"
      :key="toast.item.id"
      class="web-panel web-notification-toast web-stack"
      :data-notification-toast="toast.item.id"
    >
      <h2>{{ toast.item.heading }}</h2><p v-if="toast.item.summary && toast.item.summary !== toast.item.heading">
        {{ toast.item.summary }}
      </p><div class="web-notification-actions">
        <RouterLink
          class="web-button"
          :to="{ name: 'notifications', hash: '#notification-' + toast.item.id }"
          @click="n.dismiss(toast.item.id)"
        >
          Открыть
        </RouterLink><button
          class="web-button web-icon-button web-notification-close"
          :aria-label="'Закрыть уведомление ' + toast.item.heading"
          @click="n.dismiss(toast.item.id)"
        >
          <MobileIcon name="close" />
        </button>
      </div>
    </article>
  </aside>
</template>
