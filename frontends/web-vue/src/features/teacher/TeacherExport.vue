<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import {
  exportStateLabel,
  type ReportDownloadFormat,
  type ReportDownloadTicketRequest,
} from '../../../../mobile-core/src/shared/report-download-client';
import type { TeacherScope } from './teacher-state';
import { teacherError } from './teacher-state';
import { createTeacherReportPort } from './teacher-report';
import WebSelect from '../../shared/WebSelect.vue';

const props = defineProps<{
  scope: TeacherScope;
  request: ((format: ReportDownloadFormat) => ReportDownloadTicketRequest) | null;
  disabledReason?: string;
}>();
const format = ref<ReportDownloadFormat>('pdf');
const busy = ref(false);
const status = ref('');
const error = ref('');
const unknown = ref(false);
const recoveryRequest = shallowRef<ReportDownloadTicketRequest | null>(null);
const port = createTeacherReportPort(props.scope.transport);
let revision = 0;
let alive = true;
const selector = computed(() => (props.request ? JSON.stringify(props.request(format.value)) : ''));
watch(selector, () => {
  if (recoveryRequest.value) return;
  revision++;
  busy.value = false;
  error.value = '';
  status.value = '';
  unknown.value = false;
});
onBeforeUnmount(() => {
  alive = false;
  revision++;
});
async function download() {
  if (!props.scope.current() || busy.value || (!recoveryRequest.value && !props.request)) return;
  const request = recoveryRequest.value ?? props.request!(format.value);
  recoveryRequest.value = structuredClone(request);
  const run = ++revision;
  const isCurrent = () => alive && props.scope.current() && run === revision;
  busy.value = true;
  error.value = '';
  unknown.value = false;
  try {
    const result = await port.download(request, isCurrent, (state) => {
      if (isCurrent()) status.value = exportStateLabel(state);
    });
    if (isCurrent()) {
      recoveryRequest.value = null;
      if (result === 'accepted') status.value = 'Файл скачан.';
    }
  } catch (cause) {
    if (!isCurrent()) return;
    unknown.value =
      cause instanceof TypeError || (cause instanceof DOMException && cause.name === 'AbortError');
    recoveryRequest.value = unknown.value ? structuredClone(request) : null;
    error.value = unknown.value
      ? 'Ответ не получен. Проверь ту же выгрузку — повторная проверка сохранит исходный запрос.'
      : teacherError(cause);
    status.value = '';
  } finally {
    if (isCurrent()) busy.value = false;
  }
}
</script>
<template>
  <div class="teacher-export web-stack">
    <div class="teacher-toolbar">
      <WebSelect
        v-model="format"
        class="teacher-export-format"
        label="Формат"
        :options="[{ value: 'pdf', label: 'PDF' }, { value: 'docx', label: 'DOCX' }, { value: 'xlsx', label: 'XLSX' }, { value: 'png', label: 'PNG (ZIP)' }, { value: 'html', label: 'HTML' }]"
        :disabled="busy || !!recoveryRequest"
      />
      <button
        class="web-button"
        :disabled="!scope.current() || (!request && !recoveryRequest) || busy"
        @click="download"
      >
        {{ busy ? 'Подготовка…' : unknown ? 'Проверить выгрузку' : 'Экспортировать' }}
      </button>
    </div>
    <p
      v-if="!request && !recoveryRequest"
      class="web-caption"
    >
      {{ disabledReason ?? 'Выбери контекст и дождись данных для выгрузки.' }}
    </p>
    <p
      v-if="status"
      role="status"
    >
      {{ status }}
    </p><p
      v-if="error"
      class="web-error"
      role="alert"
    >
      {{ error }}
    </p>
  </div>
</template>
