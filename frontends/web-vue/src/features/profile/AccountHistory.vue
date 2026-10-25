<script setup lang="ts">
import type { ProfileHistoryEvent } from '../../../../mobile-core/src/features/profile/profile-types';
defineProps<{
  items: readonly ProfileHistoryEvent[];
  loading: boolean;
  error?: string | undefined;
  next: string | null;
}>();
defineEmits<{ more: []; retry: [] }>();
const labels: Record<ProfileHistoryEvent['type'], string> = {
  LOGIN: 'Вход в аккаунт',
  ROLE_CHANGED: 'Смена роли',
  CURRENT_LOGOUT: 'Выход с устройства',
  LOGOUT_ALL: 'Завершение всех входов',
  PASSWORD_CHANGED: 'Смена пароля',
  SECURITY_REVOKED: 'Сессия завершена из соображений безопасности',
};
function date(value: string) {
  return new Intl.DateTimeFormat('ru', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  );
}
</script>
<template>
  <section
    class="web-panel web-stack"
    aria-labelledby="history-title"
    :aria-busy="loading"
  >
    <h2 id="history-title">
      История аккаунта
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
        Повторить
      </button>
    </p>
    <p
      v-if="!items.length && !loading && !error"
      class="web-caption"
    >
      История пуста.
    </p>
    <ul class="web-profile-list">
      <li
        v-for="item in items"
        :key="item.id"
      >
        <div>
          <h3>{{ labels[item.type] }}</h3><p class="web-caption">
            {{ [item.clientLabel, item.locationLabel].filter(Boolean).join(' · ') }}
          </p>
        </div><time
          class="web-caption"
          :datetime="item.occurredAt"
        >{{ date(item.occurredAt) }}</time>
      </li>
    </ul>
    <button
      v-if="next"
      class="web-button"
      :disabled="loading"
      @click="$emit('more')"
    >
      Показать ещё
    </button>
  </section>
</template>
