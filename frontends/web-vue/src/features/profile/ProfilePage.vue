<script setup lang="ts">
import ConfirmDialog from '../../shared/ConfirmDialog.vue';
import { ref } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { AVATAR_PRESETS } from '../../../../web-panel/src/app/core/profile/preset-avatars';
import { webSession } from '../../auth/session';
import { webAssistantAccess } from '../../app/assistant';
import ThemeSelect from '../../shared/ThemeSelect.vue';
import { useWebProfile } from './use-profile';
import AccountHistory from './AccountHistory.vue';
import SessionList from './SessionList.vue';
import SecurityForm from './SecurityForm.vue';
const dirty = ref(false);
const confirmAll = ref(false);
const p = useWebProfile(() => dirty.value);
const view = p.state.view;
async function act(operation: () => Promise<unknown>) {
  try {
    await operation();
  } catch {
    /* Domain view owns the operation error. */
  }
}
onBeforeRouteLeave(
  () => !dirty.value || window.confirm('Поля смены пароля будут очищены. Перейти?'),
);
const permissionLabels = {
  MARK_ATTENDANCE: 'Отмечать посещаемость',
  MANAGE_EXCUSES: 'Управлять уважительными причинами',
  MANAGE_HOMEWORK: 'Управлять домашними заданиями',
  CANCEL_LESSONS: 'Отменять занятия',
  VIEW_STATS: 'Просматривать статистику',
};
</script>
<template>
  <section
    class="web-stack"
    data-screen-id="WEB-STUDENT-SETTINGS"
  >
    <h1>Настройки</h1>
    <p
      v-if="p.notice.value"
      class="web-notice"
      role="status"
    >
      {{ p.notice.value }}
    </p>
    <p
      v-if="view.error || view.snapshotError"
      class="web-error"
      role="alert"
    >
      {{ (view.error || view.snapshotError)?.message }}
      <button
        class="web-button"
        @click="p.load"
      >
        Проверить состояние
      </button>
    </p>
    <div class="web-profile-data">
      <div class="web-stack">
        <dl class="web-profile-definitions">
          <dt>Имя</dt><dd>{{ p.own.value?.firstName || '—' }}</dd><dt>Фамилия</dt><dd>{{ p.own.value?.lastName || '—' }}</dd><dt>Отчество</dt><dd>{{ p.own.value?.middleName || '—' }}</dd><dt>Группа</dt><dd>{{ webSession.snapshot.value?.groupLabel || '—' }}</dd><dt>Telegram ID</dt><dd>{{ p.own.value?.telegramId || 'Не привязан' }}</dd>
        </dl>
        <p
          v-if="p.ownError.value"
          class="web-error"
          role="alert"
        >
          {{ p.ownError.value }}
          <button
            class="web-button"
            @click="p.loadOwn"
          >
            Повторить
          </button>
        </p>
        <ul
          v-if="webAssistantAccess.permissions.value.length"
          class="web-profile-permissions"
        >
          <li
            v-for="permission in webAssistantAccess.permissions.value"
            :key="permission"
          >
            {{ permissionLabels[permission] }}
          </li>
        </ul>
      </div>
      <fieldset
        class="web-panel web-stack"
        :disabled="p.busy.value || p.uncertain.value"
      >
        <legend>Аватар профиля</legend><div class="web-avatar-options">
          <button
            class="web-button"
            :aria-pressed="!p.own.value?.avatarId"
            @click="p.saveAvatar('')"
          >
            Инициалы
          </button><button
            v-for="preset in AVATAR_PRESETS"
            :key="preset.id"
            class="web-button"
            :class="{ 'web-button-selected': p.own.value?.avatarId === preset.id }"
            :aria-pressed="p.own.value?.avatarId === preset.id"
            @click="p.saveAvatar(preset.id)"
          >
            {{ preset.label }}
          </button>
        </div>
      </fieldset>
    </div>
    <AccountHistory
      :items="view.history"
      :loading="view.historyStatus === 'loading'"
      :error="view.historyError?.message"
      :next="view.historyNextCursor"
      @retry="act(() => p.state.loadHistory())"
      @more="act(() => p.state.loadHistory({ cursor: view.historyNextCursor! }))"
    />
    <SessionList
      :items="view.sessions"
      :loading="view.sessionsStatus === 'loading'"
      :busy="p.busy.value"
      :uncertain="p.uncertain.value"
      :error="(view.terminationError || view.sessionsError)?.message"
      :next="view.sessionsNextCursor"
      @retry="act(() => p.state.loadAllSessions())"
      @more="act(() => p.state.loadSessions({ cursor: view.sessionsNextCursor! }))"
      @revoke="(id) => act(() => p.state.terminateSession(id))"
    />
    <SecurityForm
      v-if="webSession.snapshot.value"
      :policy="webSession.snapshot.value.passwordPolicy"
      :busy="p.busy.value"
      :uncertain="p.uncertain.value"
      @dirty="dirty = $event"
      @change="(value) => act(() => p.state.changePassword(value))"
    />
    <div class="web-stack">
      <h2>Тема интерфейса</h2><ThemeSelect />
    </div>
    <ConfirmDialog
      :open="confirmAll"
      title="Выйти на всех устройствах?"
      confirm-label="Подтвердить выход на всех устройствах"
      :busy="p.busy.value"
      danger
      @cancel="confirmAll = false"
      @confirm="act(() => p.state.logoutAll())"
    >
      <p>Текущий вход тоже будет завершён.</p>
    </ConfirmDialog>
    <button
      class="web-button web-button-danger"
      :disabled="p.busy.value || p.uncertain.value"
      @click="confirmAll = true"
    >
      Выйти на всех устройствах
    </button>
  </section>
</template>
