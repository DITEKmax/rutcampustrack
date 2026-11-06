<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { NotificationsApiError, notificationsErrorMessage } from './notifications-client';
import { NotificationsConflictError } from './notifications-v2-client';
import { notificationCatalogMatchesPreferences } from './notifications-v2-models';
import {
  mergeNotificationPreferenceEdits,
  notificationPreferenceEdits,
  notificationPreferencesDiffer,
  notificationPreferencesUpdate,
  reapplyNotificationPreferences,
  type NotificationDeliveryChannel,
  type NotificationPreferencesEdit,
} from './notification-preferences-draft';
import type {
  NotificationAction,
  NotificationCatalog,
  NotificationItem,
  NotificationPreferencesV2,
  NotificationPreferencesV2Update,
  NotificationsV2Api,
} from './notifications-v2-models';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import type { MobileHostAdapter } from '../../shared/host';
import { NotificationsUnreadState } from './notifications-unread';
import { NotificationsMutationQueue } from './notifications-mutations';
import { enterNotificationsFocusScope } from './notifications-focus';
import NotificationInbox from './NotificationInbox.vue';
import NotificationSettings from './NotificationSettings.vue';
import NotificationColors from './NotificationColors.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import './notifications-screen.pcss';

const props = defineProps<{
  studentUi?: boolean;
  api: NotificationsV2Api;
  contextKey: string;
  unreadOwner: NotificationsUnreadState;
  host?: MobileHostAdapter | null;
  offline?: boolean;
  canOpenTarget?: boolean;
  realtimeRevision?: number;
  openAction?: (action: NotificationAction) => boolean | Promise<boolean>;
}>();
const emit = defineEmits<{ close: []; 'owner-error': [error: unknown] }>();
const page = ref<'inbox' | 'settings' | 'colors'>('inbox');
const title = computed(() =>
  page.value === 'inbox'
    ? 'Уведомления'
    : page.value === 'settings'
      ? 'Настройки уведомлений'
      : 'Цвета уведомлений',
);
const items = shallowRef<NotificationItem[]>([]);
const nextCursor = ref<string | null>(null);
const unreadOnly = ref(false);
const loading = ref(false);
const loadingMore = ref(false);
const error = shallowRef<{ message: string; tone: 'warning' | 'error' } | null>(null);
const pendingIds = shallowRef(new Set<string>());
const markAllPending = ref(false);
const catalog = shallowRef<NotificationCatalog | null>(null);
const preferences = shallowRef<NotificationPreferencesV2 | null>(null);
const draft = shallowRef<NotificationPreferencesV2 | null>(null);
const preferencesBusy = ref(false);
const preferencesError = ref<string | null>(null);
const preferencesBlocked = ref(false);
const catalogChangedMessage =
  'Каталог уведомлений изменился. Черновик сохранён. Загрузи актуальные настройки, чтобы продолжить.';
const preferencesErrorTone = ref<'warning' | 'error'>('error');
const conflict = shallowRef<NotificationPreferencesV2 | null>(null);
const uncertainPreferences = shallowRef<ReturnType<typeof notificationPreferenceEdits>>(new Set());
const confirmDiscardPreferences = ref(false);
const permissionDenied = ref(false);
const unread = shallowRef({ ...props.unreadOwner.view });
const stopUnread = props.unreadOwner.subscribe(() => {
  unread.value = { ...props.unreadOwner.view };
});
const pushState = shallowRef(props.host?.push?.snapshot() ?? null);
const stopPush = props.host?.push?.subscribe((value) => {
  pushState.value = value;
});
const dialogElement = ref<HTMLElement | null>(null);
const backButtonElement = ref<HTMLButtonElement | null>(null);
const headingElement = ref<HTMLElement | null>(null);
const mutations = new NotificationsMutationQueue();
let epoch = 0;
let listRequest = 0;
let prefsRequest = 0;
let disposed = false;
let restoreFocus: (() => void) | undefined;
let restoreHostBack: (() => void) | undefined;
let stopHostBack: (() => void) | undefined;
let refreshPending = false;
let refreshRunning = false;
let preferencesRefreshPending = false;
let preferencesWriteFailed = false;
let savingPreferences = false;
let closeAfterPreferences = false;
let unifiedChannel: NotificationDeliveryChannel = 'appPush';
const nativeBack = computed(() => props.host?.backOwner === 'host' && !!props.host?.claimBack);
const appPushAvailable = computed(
  () => !props.host || (!!props.host.push && pushState.value?.status !== 'unsupported'),
);
const dirtyPreferences = computed(
  () => !!draft.value && !!preferences.value && notificationPreferencesDiffer(draft.value, preferences.value),
);
const pendingPreferences = computed(
  () => dirtyPreferences.value || uncertainPreferences.value.size > 0 || (!!draft.value && !!conflict.value),
);
function current(run: number): boolean {
  return !disposed && run === epoch && !permissionDenied.value;
}
function clearPrivate(): void {
  items.value = [];
  nextCursor.value = null;
  catalog.value = null;
  preferences.value = null;
  draft.value = null;
  conflict.value = null;
  uncertainPreferences.value = new Set();
  confirmDiscardPreferences.value = false;
  preferencesBlocked.value = false;
}
function fail(cause: unknown): string | null {
  if (cause instanceof StaleSessionGenerationError) return null;
  if (cause instanceof NotificationsApiError && (cause.status === 401 || cause.status === 403)) {
    permissionDenied.value = true;
    clearPrivate();
    props.unreadOwner.bind(null, null);
    emit('owner-error', cause);
  }
  return notificationsErrorMessage(cause);
}
function confirmPreferences(saved: NotificationPreferencesV2, pending: NotificationPreferencesV2 | null = null): void {
  preferences.value = saved;
  draft.value = pending && notificationPreferencesDiffer(pending, saved) ? pending : null;
  conflict.value = null;
  uncertainPreferences.value = new Set();
  preferencesWriteFailed = false;
  preferencesError.value = null;
  if (!draft.value) confirmDiscardPreferences.value = false;
}
function rebasedPreferences(currentPreferences: NotificationPreferencesV2): NotificationPreferencesV2 | null {
  if (!draft.value || !preferences.value) return null;
  const update = reapplyNotificationPreferences(
    draft.value, currentPreferences, preferences.value, uncertainPreferences.value, unifiedChannel,
  );
  return update
    ? { ...currentPreferences, splitChannels: update.splitChannels, types: update.types }
    : null;
}
function reconcilePreferences(saved: NotificationPreferencesV2): void {
  const next = rebasedPreferences(saved);
  if (!next) {
    preferencesBlocked.value = true;
    preferencesErrorTone.value = 'error';
    preferencesError.value = catalogChangedMessage;
  } else if (!notificationPreferencesDiffer(next, saved)) {
    confirmPreferences(saved);
    return;
  }
  conflict.value = saved;
}
async function loadInbox(append = false): Promise<void> {
  if (props.offline || permissionDenied.value || (append && !nextCursor.value)) return;
  const run = epoch,
    request = ++listRequest;
  if (append) loadingMore.value = true;
  else loading.value = true;
  error.value = null;
  try {
    const result = await props.api.inbox({
      unreadOnly: unreadOnly.value,
      ...(append && nextCursor.value ? { cursor: nextCursor.value } : {}),
    });
    if (!current(run) || request !== listRequest) return;
    const merged = append ? [...items.value, ...result.items] : result.items;
    items.value = [...new Map(merged.map((item) => [item.id, item])).values()];
    nextCursor.value = result.nextCursor;
  } catch (cause) {
    if (!current(run) || request !== listRequest) return;
    const message = fail(cause);
    if (message)
      error.value = {
        message,
        tone: cause instanceof NotificationsApiError && cause.status === 503 ? 'warning' : 'error',
      };
  } finally {
    if (!disposed && run === epoch && request === listRequest) {
      loading.value = false;
      loadingMore.value = false;
    }
  }
}
async function loadPreferences(preserveDraft = false): Promise<void> {
  if (!current(epoch) || props.offline || preferencesBusy.value) return;
  preferencesRefreshPending = false;
  const run = epoch,
    request = ++prefsRequest;
  let staleSession = false;
  preferencesBusy.value = true;
  preferencesError.value = null;
  try {
    const [nextCatalog, saved] = await Promise.all([
      props.api.catalog(),
      props.api.getPreferences(),
    ]);
    if (!current(run) || request !== prefsRequest) return;
    if (!notificationCatalogMatchesPreferences(nextCatalog, saved)) {
      preferencesBlocked.value = true;
      throw new NotificationsApiError(
        'Каталог и настройки уведомлений не совпадают. Загрузи актуальные настройки.',
      );
    }
    const previous = preferences.value;
    const keepDraft = preserveDraft && pendingPreferences.value;
    if (
      keepDraft &&
      draft.value &&
      !notificationCatalogMatchesPreferences(nextCatalog, draft.value)
    ) {
      conflict.value = saved;
      preferencesBlocked.value = true;
      preferencesErrorTone.value = 'error';
      preferencesError.value = catalogChangedMessage;
      return;
    }
    catalog.value = nextCatalog;
    preferencesBlocked.value = false;
    if (!keepDraft) {
      confirmPreferences(saved);
    } else if (previous?.revision !== saved.revision) {
      reconcilePreferences(saved);
    } else {
      preferences.value = saved;
    }
  } catch (cause) {
    if (current(run) && request === prefsRequest) {
      staleSession = cause instanceof StaleSessionGenerationError;
      preferencesErrorTone.value =
        cause instanceof NotificationsApiError && cause.status === 503 ? 'warning' : 'error';
      preferencesError.value = fail(cause);
    }
  } finally {
    if (!disposed && run === epoch && request === prefsRequest) {
      preferencesBusy.value = false;
      if (!staleSession) advancePreferences();
    }
  }
}
async function savePreferences(update: NotificationPreferencesV2Update): Promise<void> {
  if (
    props.offline ||
    !current(epoch) ||
    preferencesBusy.value ||
    preferencesBlocked.value ||
    !preferences.value ||
    !catalog.value ||
    !draft.value
  )
    return;
  if (!notificationCatalogMatchesPreferences(catalog.value, update)) {
    preferencesBlocked.value = true;
    preferencesErrorTone.value = 'error';
    preferencesError.value = catalogChangedMessage;
    return;
  }
  const run = epoch,
    request = ++prefsRequest;
  let staleSession = false;
  const baseline = preferences.value;
  const submitted = draft.value;
  savingPreferences = true;
  preferencesBusy.value = true;
  preferencesWriteFailed = false;
  preferencesError.value = null;
  try {
    const saved = await props.api.updatePreferences(update);
    if (!current(run) || request !== prefsRequest) return;
    if (!catalog.value || !notificationCatalogMatchesPreferences(catalog.value, saved)) {
      conflict.value = saved;
      preferencesBlocked.value = true;
      preferencesErrorTone.value = 'error';
      preferencesError.value = catalogChangedMessage;
      return;
    }
    const pending = draft.value;
    const remaining =
      pending && notificationPreferencesDiffer(pending, submitted)
        ? mergeNotificationPreferenceEdits(submitted, pending, saved)
        : null;
    confirmPreferences(saved, remaining);
  } catch (cause) {
    if (!current(run) || request !== prefsRequest) return;
    staleSession = cause instanceof StaleSessionGenerationError;
    preferencesErrorTone.value =
      cause instanceof NotificationsApiError && cause.status === 503 ? 'warning' : 'error';
    preferencesError.value = fail(cause);
    preferencesWriteFailed = !!preferencesError.value && !(cause instanceof NotificationsConflictError);
    if (preferencesWriteFailed) {
      uncertainPreferences.value = notificationPreferenceEdits(
        baseline, submitted, uncertainPreferences.value,
      );
    } else if (cause instanceof NotificationsConflictError) {
      if (draft.value) {
        uncertainPreferences.value = notificationPreferenceEdits(
          submitted, draft.value, uncertainPreferences.value,
        );
      }
      if (cause.currentPreferences) reconcilePreferences(cause.currentPreferences);
    }
  } finally {
    if (!disposed && run === epoch && request === prefsRequest) {
      savingPreferences = false;
      preferencesBusy.value = false;
      if (!staleSession) advancePreferences();
    }
  }
}
function advancePreferences(): void {
  if (!current(epoch) || preferencesBusy.value) return;
  if (
    !props.offline && !preferencesBlocked.value && !conflict.value && !preferencesError.value &&
    dirtyPreferences.value && preferences.value && draft.value
  ) {
    void savePreferences(notificationPreferencesUpdate(draft.value, preferences.value.revision));
    return;
  }
  if (closeAfterPreferences) {
    closeAfterPreferences = false;
    if (pendingPreferences.value) offerCloseWithoutSaving();
    else emit('close');
    return;
  }
  if (preferencesRefreshPending && !preferencesWriteFailed && !conflict.value && !props.offline)
    void loadPreferences(true);
}
function editPreferences(change: NotificationPreferencesEdit, keep?: NotificationDeliveryChannel): void {
  if (!current(epoch) || !preferences.value || props.offline || preferencesBlocked.value || conflict.value)
    return;
  draft.value = change(draft.value ?? preferences.value);
  if (keep) unifiedChannel = keep;
  advancePreferences();
}
function reloadPreferences(): void {
  if (preferencesWriteFailed && preferences.value && draft.value) {
    void savePreferences(notificationPreferencesUpdate(draft.value, preferences.value.revision));
    return;
  }
  preferencesWriteFailed = false;
  void loadPreferences(conflict.value === null && !preferencesBlocked.value);
}
function reapplyPreferences(): void {
  if (!conflict.value || preferencesBusy.value || props.offline || preferencesBlocked.value) return;
  const currentPreferences = conflict.value;
  const next = rebasedPreferences(currentPreferences);
  if (!next) return;
  confirmPreferences(currentPreferences, next);
  advancePreferences();
}
async function setReadState(item: NotificationItem, read: boolean): Promise<boolean> {
  if (props.offline || permissionDenied.value || pendingIds.value.has(item.id)) return false;
  if ((item.readAt !== null) === read) return true;
  const run = epoch;
  pendingIds.value = new Set([...pendingIds.value, item.id]);
  try {
    const ack = await mutations.run(() =>
      current(run) ? props.api.setReadState(item, read) : Promise.resolve(null),
    );
    if (!ack || !current(run)) return false;
    listRequest += 1;
    loading.value = false;
    loadingMore.value = false;
    props.unreadOwner.acknowledge(ack.unreadCount);
    items.value = items.value
      .map((row) => (row.id === ack.item.id ? ack.item : row))
      .filter((row) => !unreadOnly.value || row.readAt === null)
      .sort(
        (left, right) =>
          right.orderAt.localeCompare(left.orderAt) || right.id.localeCompare(left.id),
      );
    return true;
  } catch (cause) {
    if (!current(run)) return false;
    const message = fail(cause);
    if (message)
      error.value = {
        message,
        tone: cause instanceof NotificationsApiError && cause.status === 503 ? 'warning' : 'error',
      };
    return false;
  } finally {
    if (!disposed && run === epoch)
      pendingIds.value = new Set([...pendingIds.value].filter((id) => id !== item.id));
  }
}
async function openItem(item: NotificationItem): Promise<void> {
  const run = epoch;
  if (!item.action || !props.canOpenTarget || props.offline) return;
  if (!(await setReadState(item, true)) || !current(run)) return;
  try {
    if (await props.openAction?.(item.action)) {
      if (current(run)) requestClose();
    } else if (current(run))
      error.value = {
        message: 'Объект сейчас недоступен в выбранной роли. Детали уведомления сохранены.',
        tone: 'warning',
      };
  } catch (cause) {
    if (current(run)) {
      const message = fail(cause);
      if (message) error.value = { message, tone: 'warning' };
    }
  }
}
async function markAllRead(): Promise<void> {
  if (props.offline || permissionDenied.value || markAllPending.value || !unread.value.count)
    return;
  const run = epoch;
  markAllPending.value = true;
  try {
    const ack = await mutations.run(() =>
      current(run) ? props.api.markAllRead() : Promise.resolve(null),
    );
    if (!ack || !current(run)) return;
    listRequest += 1;
    loading.value = false;
    loadingMore.value = false;
    props.unreadOwner.acknowledge(ack.unreadCount);
    await loadInbox();
  } catch (cause) {
    if (current(run)) {
      const message = fail(cause);
      if (message)
        error.value = {
          message,
          tone:
            cause instanceof NotificationsApiError && cause.status === 503 ? 'warning' : 'error',
        };
    }
  } finally {
    if (!disposed && run === epoch) markAllPending.value = false;
  }
}
function filter(value: boolean): void {
  if (unreadOnly.value === value) return;
  unreadOnly.value = value;
  nextCursor.value = null;
  items.value = [];
  void loadInbox();
}
async function changePage(value: typeof page.value): Promise<void> {
  page.value = value;
  if (value !== 'inbox' && !preferences.value) void loadPreferences();
  await nextTick();
  headingElement.value?.focus();
  dialogElement.value?.querySelector('.notifications-screen__content')?.scrollTo({ top: 0 });
}
function back(): void {
  if (confirmDiscardPreferences.value) confirmDiscardPreferences.value = false;
  else if (page.value === 'colors') void changePage('settings');
  else if (page.value === 'settings') void changePage('inbox');
  else requestClose();
}
function requestClose(): void {
  if (savingPreferences || (preferencesBusy.value && pendingPreferences.value)) {
    closeAfterPreferences = true;
  } else if (pendingPreferences.value) {
    offerCloseWithoutSaving();
  } else {
    emit('close');
  }
}
function offerCloseWithoutSaving(): void {
  confirmDiscardPreferences.value = true;
  void changePage('settings');
}
async function refreshPreferences(): Promise<void> {
  if (preferencesWriteFailed || conflict.value) return;
  if (preferencesBusy.value) {
    preferencesRefreshPending = true;
  } else {
    await loadPreferences(true);
  }
}
async function refresh(): Promise<void> {
  refreshPending = true;
  if (refreshRunning || props.offline || permissionDenied.value) return;
  refreshRunning = true;
  try {
    while (refreshPending && !disposed && !props.offline && !permissionDenied.value) {
      refreshPending = false;
      await Promise.all([
        loadInbox(),
        props.unreadOwner.invalidate(),
        preferences.value ? refreshPreferences() : Promise.resolve(),
      ]);
    }
  } finally {
    refreshRunning = false;
  }
}
onMounted(() => {
  if (dialogElement.value)
    restoreFocus = enterNotificationsFocusScope(
      dialogElement.value,
      document.getElementById('app'),
      backButtonElement.value ?? headingElement.value,
    );
  if (props.host?.backOwner === 'host') {
    if (props.host.claimBack) restoreHostBack = props.host.claimBack(back);
    else if (props.host.suspendBack) restoreHostBack = props.host.suspendBack();
    else {
      props.host.setBackVisible?.(true);
      stopHostBack = props.host.subscribeBack?.(back);
    }
  }
  void loadInbox();
  void loadPreferences();
  void props.unreadOwner.refresh();
});
watch(
  () => props.contextKey,
  () => {
    epoch += 1;
    clearPrivate();
    emit('close');
  },
);
watch(
  () => props.realtimeRevision,
  () => {
    void refresh();
  },
);
watch(
  () => props.offline,
  (offline) => {
    if (!offline) void refresh();
  },
);
onBeforeUnmount(() => {
  disposed = true;
  epoch += 1;
  listRequest += 1;
  prefsRequest += 1;
  stopUnread();
  stopPush?.();
  restoreFocus?.();
  stopHostBack?.();
  if (restoreHostBack) restoreHostBack();
  else if (nativeBack.value) props.host?.setBackVisible?.(false);
  clearPrivate();
});
</script>

<template>
  <main
    ref="dialogElement"
    class="notifications-screen"
    :data-student-ui="studentUi ? '' : undefined"
    role="dialog"
    aria-modal="true"
    aria-labelledby="notifications-title"
    tabindex="-1"
    @keydown.esc.prevent="back"
  >
    <header class="notifications-screen__header">
      <button
        v-if="!nativeBack"
        ref="backButtonElement"
        type="button"
        class="notifications-screen__back"
        aria-label="Вернуться к предыдущему экрану"
        @click="back"
      >
        <MobileIcon name="back" />
      </button>
      <h1
        id="notifications-title"
        ref="headingElement"
        tabindex="-1"
      >
        {{ title }}
      </h1>
      <button
        v-if="page === 'inbox'"
        class="notifications-screen__gear"
        type="button"
        aria-label="Настройки уведомлений"
        @click="changePage('settings')"
      >
        <MobileIcon name="settings" />
      </button>
    </header>
    <div class="notifications-screen__content">
      <section
        v-if="permissionDenied"
        class="notifications-screen__notice"
        role="alert"
      >
        {{ error?.message ?? preferencesError ?? 'Доступ к уведомлениям не подтверждён.' }}
      </section>
      <template v-else>
        <div
          v-if="unread.status === 'error' && page === 'inbox'"
          class="notifications-screen__notice"
          role="alert"
        >
          Счётчик непрочитанных недоступен.
          <button
            type="button"
            @click="unreadOwner.refresh()"
          >
            Повторить
          </button>
        </div>
        <NotificationInbox
          v-if="page === 'inbox'"
          :student-ui="studentUi"
          :items="items"
          :preferences="preferences"
          :loading="loading"
          :loading-more="loadingMore"
          :error="error"
          :pending-ids="pendingIds"
          :offline="offline"
          :next-cursor="nextCursor"
          :unread-only="unreadOnly"
          :can-mark-all-read="Boolean(unread.count)"
          :mark-all-pending="markAllPending"
          :context-key="contextKey"
          :can-open-action="canOpenTarget"
          @read-state="setReadState"
          @open-item="openItem"
          @reload="loadInbox()"
          @load-more="loadInbox(true)"
          @mark-all-read="markAllRead"
          @filter="filter"
        />
        <template
          v-else-if="
            catalog &&
              preferences &&
              notificationCatalogMatchesPreferences(catalog, preferences) &&
              (!draft || notificationCatalogMatchesPreferences(catalog, draft))
          "
        >
          <div
            v-if="page === 'settings' && confirmDiscardPreferences && pendingPreferences"
            class="notifications-screen__notice"
            role="group"
            aria-label="Несохранённые настройки"
          >
            <p>Есть несохранённые изменения.</p>
            <button
              type="button"
              @click="confirmDiscardPreferences = false"
            >
              Остаться
            </button>
            <button
              type="button"
              :disabled="preferencesBusy"
              @click="emit('close')"
            >
              Закрыть без сохранения
            </button>
          </div>
          <NotificationSettings
            v-if="page === 'settings'"
            :student-ui="studentUi"
            :catalog="catalog"
            :preferences="preferences"
            :draft="draft"
            :busy="preferencesBusy"
            :blocked="preferencesBlocked"
            :error="preferencesError"
            :error-tone="preferencesErrorTone"
            :conflict="conflict"
            :offline="offline"
            :app-push-available="appPushAvailable"
            @edit="editPreferences"
            @reload="reloadPreferences"
            @reapply="reapplyPreferences"
            @colors="changePage('colors')"
          />
          <NotificationColors
            v-else
            :student-ui="studentUi"
            :catalog="catalog"
            :preferences="preferences"
            :draft="draft"
            :busy="preferencesBusy"
            :blocked="preferencesBlocked"
            :error="preferencesError"
            :error-tone="preferencesErrorTone"
            :conflict="conflict"
            :offline="offline"
            @edit="editPreferences"
            @reload="reloadPreferences"
            @reapply="reapplyPreferences"
          />
          <section
            v-if="page === 'settings' && host?.push && pushState && pushState.status !== 'unsupported'"
            class="notifications-screen__device"
            aria-labelledby="notifications-device-title"
          >
            <h2 id="notifications-device-title">
              Уведомления на устройстве
            </h2><p role="status">
              {{ pushState.message }}
            </p>
            <button
              v-if="!['signed-out', 'enabled'].includes(pushState.status)"
              type="button"
              :disabled="offline || ['busy', 'denied'].includes(pushState.status)"
              @click="host.push.enable()"
            >
              Разрешить уведомления на устройстве
            </button>
            <button
              v-if="['enabled', 'error'].includes(pushState.status)"
              type="button"
              :disabled="offline || pushState.status === 'busy'"
              @click="host.push.disable()"
            >
              Отключить уведомления на устройстве
            </button>
          </section>
        </template>
        <section
          v-else
          class="notifications-screen__notice"
          :role="
            preferencesError ? (preferencesErrorTone === 'warning' ? 'status' : 'alert') : 'status'
          "
          :data-tone="preferencesError ? preferencesErrorTone : undefined"
        >
          {{
            preferencesError ?? (offline ? 'Нет подключения к интернету.' : 'Загружаем настройки…')
          }}<button
            v-if="preferencesError"
            type="button"
            :disabled="preferencesBusy || offline"
            @click="reloadPreferences"
          >
            Повторить
          </button>
        </section>
      </template>
    </div>
  </main>
</template>
