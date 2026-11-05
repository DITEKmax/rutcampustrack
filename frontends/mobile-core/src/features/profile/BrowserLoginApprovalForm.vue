<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import { parseBrowserLoginProof } from './browser-login-approval';
import type {
  BrowserLoginApprovalPort,
  BrowserLoginPreview,
  BrowserLoginProof,
} from './browser-login-approval';

const browserLoginDateFormatter = new Intl.DateTimeFormat('ru-RU', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'Europe/Moscow',
});
function formatBrowserLoginInstant(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : browserLoginDateFormatter.format(parsed);
}

const props = defineProps<{
  port: BrowserLoginApprovalPort;
  ownerKey?: string | null;
  scanOnly?: boolean;
}>();
const emit = defineEmits<{ close: []; busy: [value: boolean] }>();
const input = ref('');
const details = ref<HTMLElement | null>(null);
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
  input.value = '';
  stopDeadline();
  revision++;
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
async function inspectScan(value: string): Promise<void> {
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
    candidate = parseBrowserLoginProof(value);
  } catch {
    error.value = 'Это не QR для входа. Отсканируй LOGIN QR со страницы входа WEB';
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
    input.value = '';
    proof = value.status === 'pending' ? candidate : null;
    if (proof) armDeadline(request, port);
  } catch {
    if (request === revision && !current(request, port)) {
      clear();
      return;
    }
    if (current(request, port)) error.value = 'Не удалось проверить вход. Отсканируй QR ещё раз';
  } finally {
    if (request === revision) {
      busy.value = false;
      await nextTick();
      if (current(request, port)) details.value?.focus();
    }
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
defineExpose({ inspectScan, clear });
watch(() => [props.port, props.ownerKey], clear, { flush: 'sync' });
watch(busy, (value) => emit('busy', value), { flush: 'sync' });
onBeforeUnmount(clear);
</script>

<template>
  <section
    v-if="!scanOnly || busy || preview || error || result"
    ref="details"
    class="profile-note profile-browser-login"
    aria-label="Сведения о входе в браузере"
    :aria-busy="busy"
    tabindex="-1"
    @keydown.esc.stop.prevent="close"
  >
    <template v-if="!scanOnly">
      <h2 class="profile-note__title">Вход в браузере</h2>
      <p class="profile-note__body"
        >Вставь текст LOGIN QR из окна входа в браузере. Подтверждай только свой вход.</p
      >
      <form
        class="profile-form"
        :aria-busy="busy"
        @submit.prevent="inspectScan(input)"
      >
        <label class="profile-field"
          ><span>Данные QR для входа</span
          ><span class="profile-field__control">
            <input
              v-model="input"
              type="text"
              autocomplete="off"
              autocapitalize="off"
              spellcheck="false"
              maxlength="1024"
              :disabled="busy"
              @input="edit"
            /> </span
        ></label>
        <button
          class="profile-secondary-button"
          type="submit"
          :disabled="busy || !input.trim()"
          >Проверить вход</button
        >
      </form>
    </template>
    <button
      v-if="scanOnly"
      class="profile-browser-login__close"
      type="button"
      aria-label="Закрыть"
      @click="close"
    >
      <MobileIcon name="close" />
    </button>
    <p
      v-if="busy"
      class="profile-note__body"
      role="status"
    >
      Проверяем вход…
    </p>
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
      <p class="profile-note__body"> Создан: {{ formatBrowserLoginInstant(preview.issuedAt) }} </p>
      <p class="profile-note__body">
        Действует до: {{ formatBrowserLoginInstant(preview.expiresAt) }}
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
      <p v-else> Вход больше не ожидает подтверждения </p>
    </div>
    <p
      v-if="error"
      id="browser-login-error"
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
      v-if="!scanOnly"
      class="profile-secondary-button"
      type="button"
      @click="close"
      >Закрыть</button
    >
  </section>
</template>
