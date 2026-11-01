<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { NotificationsApiError, notificationsErrorMessage } from './notifications-client';
import { NotificationsConflictError } from './notifications-v2-client';
import { notificationCatalogMatchesPreferences } from './notifications-v2-models';
import { reapplyNotificationPreferences } from './notification-preferences-draft';
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
const preferencesRefreshing = ref(false);
const preferencesError = ref<string | null>(null);
const preferencesBlocked = ref(false);
const catalogChangedMessage =
  'Каталог уведомлений изменился. Черновик сохранён. Загрузи актуальные настройки, чтобы продолжить.';
const preferencesErrorTone = ref<'warning' | 'error'>('error');
const conflict = shallowRef<NotificationPreferencesV2 | null>(null);
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
const nativeBack = computed(() => props.host?.backOwner === 'host' && !!props.host?.claimBack);
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
  if (props.offline || permissionDenied.value || preferencesBusy.value) return;
  const run = epoch,
    request = ++prefsRequest;
  preferencesBusy.value = true;
  preferencesRefreshing.value = preserveDraft && preferences.value !== null;
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
    const hasDraftChanges =
      draft.value &&
      previous &&
      (draft.value.splitChannels !== previous.splitChannels ||
        JSON.stringify(draft.value.types) !== JSON.stringify(previous.types));
    if (
      preserveDraft &&
      hasDraftChanges &&
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
    preferences.value = saved;
    preferencesBlocked.value = false;
    if (!preserveDraft || !hasDraftChanges) {
      draft.value = structuredClone(saved);
      conflict.value = null;
    } else if (previous?.revision !== saved.revision) conflict.value = saved;
  } catch (cause) {
    if (current(run) && request === prefsRequest) {
      preferencesErrorTone.value =
        cause instanceof NotificationsApiError && cause.status === 503 ? 'warning' : 'error';
      preferencesError.value = fail(cause);
    }
  } finally {
    if (!disposed && run === epoch && request === prefsRequest) {
      preferencesBusy.value = false;
      preferencesRefreshing.value = false;
    }
  }
}
async function savePreferences(update: NotificationPreferencesV2Update): Promise<void> {
  if (
    props.offline ||
    permissionDenied.value ||
    preferencesBusy.value ||
    preferencesBlocked.value ||
    !preferences.value ||
    !catalog.value
  )
    return;
  if (!notificationCatalogMatchesPreferences(catalog.value, update)) {
    preferencesBlocked.value = true;
    preferencesError.value = catalogChangedMessage;
    return;
  }
  const run = epoch,
    request = ++prefsRequest;
  preferencesBusy.value = true;
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
    preferences.value = saved;
    draft.value = structuredClone(saved);
    conflict.value = null;
  } catch (cause) {
    if (!current(run) || request !== prefsRequest) return;
    preferencesErrorTone.value =
      cause instanceof NotificationsApiError && cause.status === 503 ? 'warning' : 'error';
    preferencesError.value = fail(cause);
    if (cause instanceof NotificationsConflictError) {
      conflict.value = cause.currentPreferences;
      if (
        conflict.value &&
        draft.value &&
        !reapplyNotificationPreferences(draft.value, conflict.value)
      ) {
        preferencesBlocked.value = true;
        preferencesError.value = catalogChangedMessage;
      }
    }
  } finally {
    if (!disposed && run === epoch && request === prefsRequest) preferencesBusy.value = false;
  }
}
function reloadPreferences(): void {
  void loadPreferences(conflict.value === null && !preferencesBlocked.value);
}
function reapplyPreferences(): void {
  if (!draft.value || !conflict.value) return;
  const update = reapplyNotificationPreferences(draft.value, conflict.value);
  if (!update) {
    preferencesBlocked.value = true;
    preferencesError.value = catalogChangedMessage;
    return;
  }
  void savePreferences(update);
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
      if (current(run)) emit('close');
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
  if (page.value === 'colors') void changePage('settings');
  else if (page.value === 'settings') void changePage('inbox');
  else emit('close');
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
        preferences.value ? loadPreferences(true) : Promise.resolve(),
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
          <NotificationSettings
            v-if="page === 'settings'"
            :catalog="catalog"
            :preferences="preferences"
            :draft="draft"
            :busy="preferencesBusy"
            :refreshing="preferencesRefreshing"
            :blocked="preferencesBlocked"
            :error="preferencesError"
            :error-tone="preferencesErrorTone"
            :conflict="conflict"
            :offline="offline"
            :app-push-available="pushState?.status !== 'unsupported'"
            @update:draft="draft = $event"
            @save="savePreferences"
            @reload="reloadPreferences"
            @reapply="reapplyPreferences"
            @colors="changePage('colors')"
          />
          <NotificationColors
            v-else
            :catalog="catalog"
            :preferences="preferences"
            :draft="draft"
            :busy="preferencesBusy"
            :blocked="preferencesBlocked"
            :error="preferencesError"
            :error-tone="preferencesErrorTone"
            :conflict="conflict"
            :offline="offline"
            :app-push-available="pushState?.status !== 'unsupported'"
            @update:draft="draft = $event"
            @save="savePreferences"
            @reload="reloadPreferences"
            @reapply="reapplyPreferences"
          />
          <section
            v-if="page === 'settings' && host?.push && pushState"
            class="notifications-screen__device"
            aria-labelledby="notifications-device-title"
          >
            <h2 id="notifications-device-title">
              Уведомления на устройстве
            </h2><p role="status">
              {{ pushState.message }}
            </p>
            <button
              v-if="!['unsupported', 'signed-out', 'enabled'].includes(pushState.status)"
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
