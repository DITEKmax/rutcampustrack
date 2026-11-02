<script setup lang="ts">
import ConfirmDialog from '../../shared/ConfirmDialog.vue';
import FeedbackToast from '../../shared/FeedbackToast.vue';
import { computed, ref } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { AVATAR_PRESETS } from '../../../../shared/profile/avatar-presets';
import { webSession } from '../../auth/session';
import { webAssistantAccess } from '../../app/assistant';
import { roleHomeNotice } from '../../app/role-home';
import { useWebProfile } from './use-profile';
import AccountHistory from './AccountHistory.vue';
import SessionList from './SessionList.vue';
import SecurityForm from './SecurityForm.vue';
import WebAvatar from '../../shared/WebAvatar.vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
const dirty = ref(false);
const confirmAll = ref(false);
const p = useWebProfile(() => dirty.value);
const view = p.state.view;
const initials = computed(() => webSession.snapshot.value?.displayName.split(/\s+/u).filter(Boolean).slice(0, 2).map((part) => part[0]).join('') ?? '');
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
    <FeedbackToast :notice="p.success.value" />
    <div
      v-if="roleHomeNotice"
      class="web-notice web-stack"
      role="status"
    >
      <p>{{ roleHomeNotice }}</p>
      <RouterLink
        class="web-button"
        :to="{ name: 'role-home' }"
      >
        Открыть главную роли
      </RouterLink>
    </div>
    <p
      v-if="p.notice.value"
      class="web-notice"
      :class="{ 'web-profile-warning': p.uncertain.value }"
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
        <table class="web-profile-details-table">
          <caption class="web-sr-only">Данные профиля</caption>
          <tbody>
            <tr><th scope="row">Имя</th><td>{{ p.own.value?.firstName || '—' }}</td></tr>
            <tr><th scope="row">Фамилия</th><td>{{ p.own.value?.lastName || '—' }}</td></tr>
            <tr><th scope="row">Отчество</th><td>{{ p.own.value?.middleName || '—' }}</td></tr>
            <tr><th scope="row">Группа</th><td>{{ webSession.snapshot.value?.groupLabel || '—' }}</td></tr>
            <tr><th scope="row">Telegram ID</th><td>{{ p.own.value?.telegramId || 'Не привязан' }}</td></tr>
          </tbody>
        </table>
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
        <legend class="web-sr-only">Аватар профиля</legend><div class="web-avatar-options">
          <button
            class="web-button"
            aria-label="Инициалы"
            :class="{ 'web-button-selected': !p.own.value?.avatarId }"
            :aria-pressed="!p.own.value?.avatarId"
            @click="p.saveAvatar('')"
          >
            <WebAvatar :initials="initials" /><span>Инициалы</span>
          </button><button
            v-for="preset in AVATAR_PRESETS"
            :key="preset.id"
            class="web-button"
            :aria-label="preset.label"
            :class="{ 'web-button-selected': p.own.value?.avatarId === preset.id }"
            :aria-pressed="p.own.value?.avatarId === preset.id"
            @click="p.saveAvatar(preset.id)"
          >
            <WebAvatar :avatar-id="preset.id" :initials="initials" /><span>{{ preset.label }}</span>
          </button>
          <button class="web-button web-avatar-upload" type="button" disabled>
            <span class="web-avatar" aria-hidden="true"><MobileIcon name="plus" /></span>
            <span>Загрузить фото</span><small class="web-avatar-upload-state">Скоро</small>
          </button>
        </div>
      </fieldset>
    </div>
    <AccountHistory
      :items="view.history"
      :loading="view.historyStatus === 'loading' || p.historyBusy.value"
      :error="view.historyError?.message"
      :next="view.historyNextCursor"
      @retry="act(() => p.loadHistory())"
      @more="act(() => p.loadHistory(true))"
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
    >
      <template #actions>
        <button
          class="web-button web-button-danger"
          type="button"
          :disabled="p.busy.value || p.uncertain.value"
          @click="confirmAll = true"
        >
          Выйти на всех устройствах
        </button>
      </template>
    </SessionList>
    <SecurityForm
      v-if="webSession.snapshot.value"
      :policy="webSession.snapshot.value.passwordPolicy"
      :busy="p.busy.value"
      :uncertain="p.uncertain.value"
      @dirty="dirty = $event"
      @change="(value) => act(() => p.state.changePassword(value))"
    />
    <ConfirmDialog
      class="web-logout-all-dialog"
      :open="confirmAll"
      title="Выйти на всех устройствах?"
      confirm-label="Подтвердить"
      cancel-label="Отменить"
      :busy="p.busy.value"
      danger
      @cancel="confirmAll = false"
      @confirm="act(() => p.state.logoutAll())"
    >
      <p>Текущий вход тоже будет завершён.</p>
    </ConfirmDialog>
    <button
      class="web-button web-button-danger"
      type="button"
      :disabled="p.busy.value || p.uncertain.value || webSession.busy.value"
      @click="webSession.logout"
    >
      Выйти на этом устройстве
    </button>
  </section>
</template>
