<script setup lang="ts">
import ConfirmDialog from '../../shared/ConfirmDialog.vue';
import { ref } from 'vue';
import type { ProfileSessionSummary } from '../../../../mobile-core/src/features/profile/profile-types';
defineProps<{
  items: readonly ProfileSessionSummary[];
  loading: boolean;
  busy: boolean;
  error?: string | undefined;
  next: string | null;
  uncertain: boolean;
}>();
const emit = defineEmits<{ more: []; retry: []; revoke: [id: string] }>();
const confirming = ref<string | null>(null);
function revoke(id: string) {
  confirming.value = null;
  emit('revoke', id);
}
function date(value: string) {
  return new Intl.DateTimeFormat('ru', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  );
}
</script>
<template>
  <section
    class="web-panel web-stack"
    aria-labelledby="sessions-title"
    :aria-busy="loading || busy"
  >
    <h2 id="sessions-title">
      Активные сессии
    </h2>
    <p
      v-if="error"
      class="web-error"
      role="alert"
    >
      {{ error }}
      <button
        class="web-button"
        @click="$emit('retry')"
      >
        Проверить состояние
      </button>
    </p>
    <p
      v-if="!items.length && !loading && !error"
      class="web-caption"
    >
      Активных сессий нет.
    </p>
    <ul class="web-profile-list">
      <li
        v-for="item in items"
        :key="item.sessionId"
        :data-session-id="item.sessionId"
      >
        <div>
          <h3>
            {{ item.clientLabel || 'Устройство' }}
            <span
              v-if="item.current"
              class="web-caption"
            >· Текущая</span>
          </h3><p class="web-caption">
            {{ item.locationLabel || 'Местоположение неизвестно' }} · Последняя активность
            {{ date(item.lastSeenAt) }}
          </p>
        </div>
        <button
          class="web-button web-button-danger"
          :disabled="busy || uncertain"
          @click="confirming = item.sessionId"
        >
          Завершить сессию
        </button>
      </li>
    </ul>
    <ConfirmDialog
      :open="confirming !== null"
      title="Завершить вход?"
      confirm-label="Подтвердить завершение"
      :busy="busy"
      danger
      @cancel="confirming = null"
      @confirm="confirming && revoke(confirming)"
    >
      <p>Для этого устройства потребуется повторный вход.</p>
    </ConfirmDialog>
    <button
      v-if="next"
      class="web-button"
      :disabled="loading || busy"
      @click="$emit('more')"
    >
      Показать ещё
    </button>
  </section>
</template>
