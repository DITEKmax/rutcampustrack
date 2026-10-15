<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { parseBrowserLoginProof } from './browser-login-approval';
import type {
  BrowserLoginApprovalPort,
  BrowserLoginPreview,
  BrowserLoginProof,
} from './browser-login-approval';

const props = defineProps<{ port: BrowserLoginApprovalPort }>();
const emit = defineEmits<{ close: [] }>();
const input = ref('');
const preview = ref<BrowserLoginPreview | null>(null);
const busy = ref(false);
const error = ref('');
const result = ref('');
let proof: BrowserLoginProof | null = null;
let revision = 0;
let deadlineTimer: ReturnType<typeof setTimeout> | undefined;
function stopDeadline(): void {
  clearTimeout(deadlineTimer);
  deadlineTimer = undefined;
}
function expired(value: BrowserLoginPreview): boolean {
  const deadline = Date.parse(value.expiresAt);
  return !Number.isFinite(deadline) || deadline <= Date.now();
}
function expire(): void {
  clear();
  error.value = 'Срок подтверждения истёк. Проверь вход заново';
}
function armDeadline(request: number, port: BrowserLoginApprovalPort): void {
  stopDeadline();
  if (request !== revision || props.port !== port || preview.value?.status !== 'pending') return;
  if (!port.isOnline()) {
    clear();
    return;
  }
  if (expired(preview.value)) {
    expire();
    return;
  }
  deadlineTimer = setTimeout(
    () => armDeadline(request, port),
    Math.max(0, Math.min(Date.parse(preview.value.expiresAt) - Date.now(), 2_147_483_647)),
  );
}
function clear(): void {
  stopDeadline();
  revision++;
  input.value = '';
  proof = null;
  preview.value = null;
  busy.value = false;
  error.value = '';
  result.value = '';
}
function edit(): void {
  stopDeadline();
  revision++;
  proof = null;
  preview.value = null;
  busy.value = false;
  error.value = '';
  result.value = '';
}
function current(request: number, port: BrowserLoginApprovalPort): boolean {
  return request === revision && props.port === port && port.isOnline();
}
async function inspect(): Promise<void> {
  if (busy.value) return;
  stopDeadline();
  const request = ++revision;
  const port = props.port;
  preview.value = null;
  proof = null;
  error.value = '';
  result.value = '';
  if (!port.isOnline()) {
    error.value = 'Подключись к сети и открой профиль заново';
    return;
  }
  let candidate: BrowserLoginProof;
  try {
    candidate = parseBrowserLoginProof(input.value);
  } catch {
    error.value = 'Вставь данные LOGIN QR из окна входа в браузере';
    return;
  }
  busy.value = true;
  try {
    const value = await port.preview(candidate);
    if (!current(request, port)) {
      if (request === revision) clear();
      return;
    }
    if (value.status === 'pending' && expired(value)) {
      expire();
      return;
    }
    preview.value = value;
    proof = value.status === 'pending' ? candidate : null;
    input.value = '';
    if (proof) armDeadline(request, port);
  } catch {
    if (request === revision && !current(request, port)) {
      clear();
      return;
    }
    if (current(request, port)) error.value = 'Не удалось проверить вход. Проверь данные и повтори';
  } finally {
    if (request === revision) busy.value = false;
  }
}
async function decide(decision: 'CONFIRM' | 'REJECT'): Promise<void> {
  const candidate = proof;
  const decisionPreview = preview.value;
  if (busy.value || !candidate || decisionPreview?.status !== 'pending') return;
  // Timers may pause in a background tab; the browser security deadline still applies.
  if (expired(decisionPreview)) {
    expire();
    return;
  }
  const request = ++revision;
  const port = props.port;
  if (!port.isOnline()) {
    clear();
    error.value = 'Подключись к сети и проверь вход заново';
    return;
  }
  busy.value = true;
  error.value = '';
  armDeadline(request, port);
  if (!current(request, port)) return;
  try {
    const value = await port.decide(candidate, decision);
    if (!current(request, port)) {
      if (request === revision) clear();
      return;
    }
    if (expired(decisionPreview)) {
      expire();
      return;
    }
    stopDeadline();
    proof = null;
    preview.value = null;
    result.value =
      value.status === 'confirmed'
        ? 'Вход в браузере подтверждён'
        : value.status === 'rejected'
          ? 'Вход в браузере отклонён'
          : 'Вход больше не ожидает подтверждения';
  } catch {
    if (request === revision && !current(request, port)) {
      clear();
      return;
    }
    if (current(request, port)) {
      if (expired(decisionPreview)) {
        expire();
        return;
      }
      stopDeadline();
      proof = null;
      preview.value = null;
      error.value = 'Результат не подтверждён. Проверь вход заново';
    }
  } finally {
    if (request === revision) busy.value = false;
  }
}
function close(): void {
  clear();
  emit('close');
}
watch(() => props.port, clear);
onBeforeUnmount(clear);
</script>

<template>
  <section
    class="profile-note"
    aria-labelledby="browser-login-title"
  >
    <h2
      id="browser-login-title"
      class="profile-note__title"
    >
      Вход в браузере
    </h2>
    <p class="profile-note__body">
      Вставь текст LOGIN QR из окна входа в браузере. Подтверждай только свой вход.
    </p>
    <form
      class="profile-form"
      :aria-busy="busy"
      @submit.prevent="inspect"
    >
      <label class="profile-field">
        <span>Данные QR для входа</span>
        <span class="profile-field__control">
          <input
            v-model="input"
            type="text"
            autocomplete="off"
            autocapitalize="off"
            spellcheck="false"
            maxlength="1024"
            :disabled="busy"
            @input="edit"
          >
        </span>
      </label>
      <button
        class="profile-secondary-button"
        type="submit"
        :disabled="busy || !input.trim()"
      >
        Проверить вход
      </button>
    </form>
    <div
      v-if="preview"
      class="profile-form"
      aria-live="polite"
    >
      <p class="profile-note__body">
        {{ preview.browserLabel }}
      </p>
      <p class="profile-note__body">
        {{ preview.warning }}
      </p>
      <p class="profile-note__body">
        Создан: {{ preview.issuedAt }}
      </p>
      <p class="profile-note__body">
        Действует до: {{ preview.expiresAt }}
      </p>
      <template v-if="preview.status === 'pending'">
        <button
          class="profile-primary-button"
          type="button"
          :disabled="busy"
          @click="decide('CONFIRM')"
        >
          Подтвердить вход
        </button>
        <button
          class="profile-secondary-button"
          type="button"
          :disabled="busy"
          @click="decide('REJECT')"
        >
          Отклонить вход
        </button>
      </template>
      <p v-else>
        Вход больше не ожидает подтверждения
      </p>
    </div>
    <p
      v-if="error"
      class="profile-inline-error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="result"
      role="status"
    >
      {{ result }}
    </p>
    <button
      class="profile-secondary-button"
      type="button"
      @click="close"
    >
      Закрыть
    </button>
  </section>
</template>
