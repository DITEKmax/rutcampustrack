<script setup lang="ts">
import { computed, ref, useId } from 'vue';
import type { NotificationItem } from '../../../../mobile-core/src/features/notifications/notifications-v2-models';
import LinkifiedText from '../../shared/LinkifiedText.vue';
const props = defineProps<{ item: NotificationItem; pending: boolean; uncertain: boolean }>();
defineEmits<{ mark: [item: NotificationItem, read: boolean]; open: [item: NotificationItem] }>();
const expanded = ref(false);
const detailsId = useId();
const summary = computed(() => props.item.summary !== props.item.heading ? props.item.summary : null);
const hasDetails = computed(() => Boolean(summary.value || props.item.details.length));
function date(value: string) {
  return new Intl.DateTimeFormat('ru', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  );
}
</script>
<template>
  <article
    :id="'notification-' + item.id"
    class="web-panel web-notification-card web-stack"
    :class="{ 'is-unread': item.readAt === null }"
    :data-notification-id="item.id"
    :aria-busy="pending"
  >
    <header>
      <h2 tabindex="-1">
        {{ item.heading
        }}<span
          v-if="item.readAt === null"
          class="web-sr-only"
        >
          · Не прочитано</span>
      </h2><time
        class="web-caption"
        :datetime="item.sentAt"
      >{{ date(item.sentAt) }}</time>
    </header>
    <div class="web-notification-actions">
      <button
        v-if="hasDetails"
        class="web-button"
        type="button"
        :aria-expanded="expanded"
        :aria-controls="detailsId"
        @click="expanded = !expanded"
      >
        {{ expanded ? 'Скрыть подробности' : 'Подробности' }}
      </button>
      <button
        v-if="item.action"
        class="web-button"
        type="button"
        :disabled="pending"
        @click="$emit('open', item)"
      >
        Перейти к событию
      </button>
      <button
        class="web-button"
        type="button"
        :disabled="pending || uncertain"
        @click="$emit('mark', item, item.readAt === null)"
      >
        {{ item.readAt === null ? 'Отметить прочитанным' : 'Отметить непрочитанным' }}
      </button>
    </div>
    <div
      v-if="hasDetails"
      v-show="expanded"
      :id="detailsId"
      class="web-notification-details web-stack"
    >
      <p v-if="summary">
        <LinkifiedText :text="summary" />
      </p>
      <dl
        v-if="item.details.length"
        class="web-profile-definitions"
      >
        <template
          v-for="(detail, index) in item.details"
          :key="index"
        >
          <dt>{{ detail.label || 'Подробность' }}</dt>
          <dd><LinkifiedText :text="detail.value" /></dd>
        </template>
      </dl>
    </div>
  </article>
</template>
