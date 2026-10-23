<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import type { WebTransport } from '../../app/api';
import { message, webSession } from '../../auth/session';
import {
  ReportDownloadClient,
  exportStateLabel,
  validateTicketRequest,
  type ReportDownloadTicketRequest,
} from '../../../../mobile-core/src/shared/report-download-client';
import { createBrowserReportDownloadPort } from '../../../../mobile-core/src/shared/report-download-browser';
const props = defineProps<{
  transport: WebTransport;
  report: ReportDownloadTicketRequest | null;
  scope: string;
  disabled?: boolean;
  label?: string;
}>();
interface Intent {
  id: string;
  report: ReportDownloadTicketRequest;
  jobId: string | null;
}
const intent = shallowRef<Intent | null>(null),
  busy = ref(false),
  status = ref(''),
  error = ref<string | null>(null);
const owner = webSession.identity.value;
const storageKey = `rct:web:headman:export:${owner}:${props.scope}`;
let alive = true;
onBeforeUnmount(() => {
  alive = false;
});
function current() {
  props.transport.assertCurrent();
  if (!alive) throw new Error('Страница закрыта');
}
function persist(value: Intent | null) {
  current();
  if (value) sessionStorage.setItem(storageKey, JSON.stringify(value));
  else sessionStorage.removeItem(storageKey);
  intent.value = value;
}
onMounted(() => {
  try {
    const raw = sessionStorage.getItem(storageKey);
    if (!raw) return;
    const value: unknown = JSON.parse(raw);
    if (
      !value ||
      typeof value !== 'object' ||
      !('id' in value) ||
      typeof value.id !== 'string' ||
      !('report' in value) ||
      !('jobId' in value) ||
      !(value.jobId === null || typeof value.jobId === 'string')
    )
      throw new Error('Не удалось прочитать выгрузку.');
    validateTicketRequest(value.report as ReportDownloadTicketRequest);
    intent.value = value as Intent;
    status.value = 'Есть незавершённая выгрузка.';
  } catch (cause) {
    error.value = message(cause);
  }
});
const tickets = new ReportDownloadClient({
  ...props.transport,
  fetcher: async (input, init) => {
    current();
    if (String(input) !== '/api/auth/export-jobs' || init?.method !== 'POST')
      return props.transport.fetcher(input, init);
    const pending = intent.value;
    if (!pending) throw new Error('Запрос выгрузки не сохранён.');
    // Read known jobs; a lost admission reuses its original server request identity.
    const response = pending.jobId
      ? await props.transport.fetcher(`/api/auth/export-jobs/${pending.jobId}`, {
          ...init,
          method: 'GET',
          body: null,
        })
      : await props.transport.fetcher(input, {
          ...init,
          body: JSON.stringify({ requestId: pending.id, report: pending.report }),
        });
    current();
    if (response.ok) {
      const value: unknown = await response.clone().json();
      current();
      if (value && typeof value === 'object' && 'jobId' in value && typeof value.jobId === 'string')
        persist({ ...pending, jobId: value.jobId });
    }
    return response;
  },
});
const browser = createBrowserReportDownloadPort(tickets, props.transport.fetcher);
async function download() {
  if (busy.value || props.disabled) return;
  busy.value = true;
  error.value = null;
  try {
    if (!intent.value) {
      if (!props.report) throw new Error('Выбери параметры выгрузки.');
      validateTicketRequest(props.report);
      persist({ id: crypto.randomUUID(), report: structuredClone(props.report), jobId: null });
    }
    const pending = intent.value!;
    await browser.download(
      pending.report,
      () => alive && owner === webSession.identity.value,
      (state) => {
        current();
        status.value = exportStateLabel(state);
      },
    );
    current();
    persist(null);
    status.value = 'Файл скачан.';
  } catch (cause) {
    if (alive) error.value = message(cause);
  } finally {
    if (alive) busy.value = false;
  }
}
</script>
<template>
  <div
    class="web-stack"
    aria-live="polite"
  >
    <button
      class="web-button"
      :disabled="disabled || busy || (!report && !intent)"
      @click="download"
      >{{
        busy
          ? 'Подготовка файла…'
          : intent
            ? 'Проверить и скачать прежнюю выгрузку'
            : (label ?? 'Скачать')
      }}</button
    >
    <p
      v-if="status"
      class="web-caption"
      >{{ status }}</p
    ><p
      v-if="error"
      class="web-error"
      role="alert"
      >{{ error }}</p
    >
  </div>
</template>
