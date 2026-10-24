<script setup lang="ts">
import { useRoute } from 'vue-router';
import { nextTick, ref, watch } from 'vue';
import type { NotificationItem } from '../../../../mobile-core/src/features/notifications/notifications-v2-models';
import { webNotifications } from './notifications';
import NotificationCard from './NotificationCard.vue';
import { openWebNotification } from './actions';
const n = webNotifications;
const route = useRoute();
let focused = '';
watch(() => [n.value?.items.value, route.hash], async () => {
  if (!/^#notification-[A-Za-z0-9._:-]+$/.test(route.hash) || focused === route.hash) return;
  await nextTick();
  const target = document.getElementById(route.hash.slice(1));
  if (target) { focused = route.hash; target.scrollIntoView({ block: 'center' }); target.querySelector('h2')?.focus(); }
});
const actionError = ref('');
async function open(item: NotificationItem) {
  if (!item.action) return;
  actionError.value = '';
  try {
    if (!(await openWebNotification(item.action)))
      actionError.value = 'Это событие больше недоступно в текущем контексте.';
  } catch {
    actionError.value = 'Не удалось открыть событие. Проверь доступ и попробуй снова.';
  }
}
</script>
<template>
  <section
    class="web-stack"
    data-screen-id="WEB-SHARED-NOTIFICATIONS"
  >
    <div class="web-page-header">
      <h1>Уведомления</h1><button
        class="web-button"
        :disabled="!n || n.pending.value || n.uncertain.value || !n.count.value"
        @click="n?.markAll()"
      >
        Прочитать всё
      </button>
    </div>
    <p
      v-if="n?.notice.value"
      class="web-notice"
      role="status"
    >
      {{ n.notice.value }}
    </p>
    <p
      v-if="actionError"
      class="web-error"
      role="alert"
    >
      {{ actionError }}
    </p>
    <div
      v-if="n?.error.value"
      class="web-notice web-stack"
      role="alert"
    >
      <p>{{ n.error.value }}</p><button
        class="web-button"
        @click="n.load()"
      >
        Повторить
      </button>
    </div>
    <p
      v-if="n?.loading.value"
      role="status"
    >
      Загрузка уведомлений…
    </p>
    <p
      v-else-if="!n?.items.value.length && !n?.error.value"
      class="web-caption"
    >
      Уведомлений пока нет.
    </p>
    <template v-if="n">
      <NotificationCard
        v-for="item in n.items.value"
        :key="item.id"
        :item="item"
        :pending="n.pending.value"
        :uncertain="n.uncertain.value"
        @mark="(item, read) => n?.mark(item, read)"
        @open="open"
      /><button
        v-if="n.nextCursor.value"
        class="web-button"
        :disabled="n.loadingMore.value || n.pending.value"
        @click="n.more()"
      >
        {{ n.loadingMore.value ? 'Загружаем…' : 'Показать ещё' }}
      </button>
    </template>
  </section>
</template>
