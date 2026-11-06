<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import AdminNotice from '../../shared/components/AdminNotice.vue';
import AdminCheckbox from '../../shared/components/AdminCheckbox.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import {
  AdminUsersApiError,
  type AdminCredentialView,
  type AdminUsersClient,
} from './admin-users-client';
import './admin-credential-panel.pcss';

const props = withDefaults(
  defineProps<{
    client: AdminUsersClient;
    userId: number;
    scopeKey: string;
    selfTarget: boolean;
    disabled?: boolean;
    initialOnly?: boolean;
    covered?: () => boolean;
  }>(),
  { initialOnly: false, covered: () => false },
);
const emit = defineEmits<{
  ownerError: [cause: unknown];
  pending: [value: boolean];
  selfReset: [];
}>();
const credential = ref<AdminCredentialView | null>(null);
const busy = ref(false),
  copying = ref(false),
  uncertain = ref(false);
const confirmation = ref<string | null>(null),
  confirmed = ref(false);
const error = ref<string | null>(null),
  copied = ref(false);
const selfReset = ref(false);
let revision = 0,
  disposed = false;
let abort: AbortController | null = null;
function clear(): void {
  ++revision;
  abort?.abort();
  abort = null;
  credential.value = null;
  confirmation.value = null;
  confirmed.value = false;
  error.value = null;
  copied.value = false;
  uncertain.value = false;
  busy.value = false;
  copying.value = false;
  emit('pending', false);
}
function visible(): boolean {
  return typeof document === 'undefined' || document.visibilityState !== 'hidden';
}
function blocked(): boolean {
  return props.disabled || props.covered() || !visible();
}
function clearIfHidden(): void {
  if (!visible()) clear();
}
async function perform(generate = false): Promise<void> {
  if (busy.value || selfReset.value || blocked()) return;
  const version = confirmation.value;
  if (
    generate &&
    (props.initialOnly ||
      !confirmed.value ||
      !version ||
      uncertain.value ||
      credential.value?.credentialsVersion !== version)
  )
    return;
  const request = ++revision;
  const { client, userId, scopeKey, selfTarget } = props;
  abort?.abort();
  const controller = new AbortController();
  abort = controller;
  credential.value = null;
  confirmation.value = null;
  confirmed.value = false;
  error.value = null;
  copied.value = false;
  copying.value = false;
  busy.value = true;
  emit('pending', true);
  const current = () =>
    !disposed &&
    request === revision &&
    !controller.signal.aborted &&
    !blocked() &&
    props.client === client &&
    props.userId === userId &&
    props.scopeKey === scopeKey &&
    props.selfTarget === selfTarget;
  try {
    const result = generate
      ? await client.generatePassword(userId, version!, controller.signal)
      : await client.readCredential(userId, controller.signal);
    if (!current()) return;
    if (props.initialOnly && (result.state !== 'GENERATED' || result.origin !== 'initial')) {
      error.value = 'Начальный пароль больше недоступен.';
      return;
    }
    credential.value = result;
    uncertain.value = false;
    if (generate && selfTarget) {
      // This ACK can be copied in this view. The server already revoked the actor's bearer.
      selfReset.value = true;
      emit('selfReset');
    }
  } catch (cause) {
    if (!current() || cause instanceof StaleSessionGenerationError) return;
    if (cause instanceof AdminUsersApiError && [401, 403, 426].includes(cause.response.status))
      emit('ownerError', cause);
    if (!current()) return;
    // CAS conflicts and unknown POST outcomes both require a fresh GET; never repeat POST.
    if (generate) uncertain.value = true;
    error.value = generate
      ? 'Результат генерации не подтверждён. Проверь текущий пароль перед следующим действием.'
      : 'Текущие данные для входа не удалось получить. Повтори проверку.';
  } finally {
    if (request === revision && !disposed) {
      abort = null;
      busy.value = false;
      emit('pending', false);
    }
  }
}
function prepare(): void {
  if (
    busy.value ||
    props.initialOnly ||
    selfReset.value ||
    uncertain.value ||
    !credential.value ||
    blocked()
  )
    return;
  confirmation.value = credential.value.credentialsVersion;
  confirmed.value = false;
  copied.value = false;
}
async function copy(): Promise<void> {
  const value = credential.value;
  const request = revision;
  const current = () =>
    !disposed &&
    request === revision &&
    !blocked() &&
    credential.value === value &&
    Boolean(value?.password);
  if (copying.value || busy.value || !current()) return;
  copying.value = true;
  copied.value = false;
  error.value = null;
  try {
    await navigator.clipboard.writeText(value!.password!);
    if (current()) copied.value = true;
  } catch {
    if (current()) error.value = 'Копирование недоступно. Попробуй ещё раз.';
  } finally {
    if (request === revision) copying.value = false;
  }
}
watch(
  () => [props.client, props.userId, props.scopeKey, props.selfTarget, props.initialOnly] as const,
  () => {
    selfReset.value = false;
    clear();
  },
  { flush: 'sync' },
);
watch(() => props.disabled, clear, { flush: 'sync' });
watch(() => props.covered(), clear, { flush: 'sync' });
onMounted(() => {
  globalThis.document?.addEventListener?.('visibilitychange', clearIfHidden);
  globalThis.window?.addEventListener?.('pagehide', clear);
});
onBeforeUnmount(() => {
  disposed = true;
  globalThis.document?.removeEventListener?.('visibilitychange', clearIfHidden);
  globalThis.window?.removeEventListener?.('pagehide', clear);
  clear();
});
</script>

<template>
  <section
    class="admin-credential-panel"
    :aria-label="initialOnly ? 'Начальный пароль' : 'Временный пароль'"
    :aria-busy="busy"
  >
    <h3>{{ initialOnly ? 'Начальный пароль' : 'Временный пароль' }}</h3>
    <p
      v-if="busy"
      class="admin-users-muted"
      role="status"
    >
      Проверяем данные для входа…
    </p>
    <template v-else-if="credential">
      <button
        v-if="credential.state === 'GENERATED'"
        type="button"
        class="admin-users-credential"
        :aria-label="initialOnly ? 'Скопировать начальный пароль' : 'Скопировать временный пароль'"
        :disabled="disabled || covered() || copying"
        @click="copy"
      >
        <span><small>{{
          credential.origin === 'initial'
            ? 'Начальный пароль'
            : credential.origin === 'headman_reset'
              ? 'Сгенерирован старостой'
              : 'Сгенерирован администратором'
        }}</small><strong>{{ credential.password }}</strong></span>
        <MobileIcon name="copy" />
      </button>
      <p
        v-else
        class="admin-users-muted"
      >
        {{
          credential.state === 'USER_CHANGED'
            ? 'Пароль изменён пользователем.'
            : 'Сохранённый временный пароль недоступен.'
        }}
      </p>
      <p
        v-if="selfReset"
        class="admin-users-muted"
        role="status"
      >
        Твой сеанс отозван. Скопируй пароль, пока этот экран открыт.
      </p>
      <template v-else-if="confirmation">
        <ul class="admin-users-muted admin-credential-panel__consequences">
          <li>Новый пароль заменит прежний.</li>
          <li>Сеансы пользователя будут отозваны.</li>
          <li>Ссылки восстановления будут отозваны.</li>
        </ul>
        <p
          v-if="selfTarget"
          class="admin-users-muted"
        >
          Твой текущий сеанс тоже завершится. Скопируй новый пароль перед выходом с экрана.
        </p>
        <AdminCheckbox
          v-model="confirmed"
          label="Подтверждаю генерацию"
          :disabled="disabled"
        />
        <button
          class="admin-users-action"
          type="button"
          :disabled="disabled || !confirmed"
          @click="perform(true)"
        >
          Сгенерировать пароль
        </button>
      </template>
      <button
        v-else-if="!initialOnly"
        class="admin-users-action admin-users-action--secondary"
        type="button"
        :disabled="disabled || uncertain"
        @click="prepare"
      >
        Создать новый пароль
      </button>
    </template>
    <p
      v-else-if="selfReset"
      class="admin-users-muted"
      role="status"
    >
      Твой сеанс отозван, пароль больше не показан.
    </p>
    <AdminNotice
      v-if="error"
      :tone="uncertain ? 'warning' : 'danger'"
      :title="uncertain ? 'Результат не подтверждён' : 'Данные для входа'"
    >
      {{ error }}
    </AdminNotice>
    <AdminNotice
      v-if="copied"
      tone="success"
      title="Пароль скопирован"
      dismissible
      @dismiss="copied = false"
    />
    <button
      v-if="!selfReset && (!initialOnly || !credential)"
      class="admin-users-action admin-users-action--secondary"
      type="button"
      :disabled="disabled || covered() || busy"
      @click="perform()"
    >
      {{
        initialOnly
          ? 'Показать начальный пароль'
          : credential || uncertain
            ? 'Проверить текущий пароль'
            : 'Показать текущий пароль'
      }}
    </button>
  </section>
</template>
