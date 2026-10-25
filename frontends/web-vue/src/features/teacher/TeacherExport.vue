<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import {
  exportStateLabel,
  type ReportDownloadFormat,
  type ReportDownloadTicketRequest,
} from '../../../../mobile-core/src/shared/report-download-client';
import type { TeacherScope } from './teacher-state';
import { teacherError } from './teacher-state';
import { createTeacherReportPort } from './teacher-report';

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
const port = createTeacherReportPort(props.scope.transport);
let revision = 0;
let alive = true;
const selector = computed(() => (props.request ? JSON.stringify(props.request(format.value)) : ''));
watch(selector, () => {
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
  if (!props.request || busy.value) return;
  const request = props.request(format.value);
  const run = ++revision;
  const isCurrent = () => alive && props.scope.current() && run === revision;
  busy.value = true;
  error.value = '';
  unknown.value = false;
  try {
    const result = await port.download(request, isCurrent, (state) => {
      if (isCurrent()) status.value = exportStateLabel(state);
    });
    if (isCurrent() && result === 'accepted') status.value = 'Файл скачан.';
  } catch (cause) {
    if (!isCurrent()) return;
    unknown.value =
      cause instanceof TypeError || (cause instanceof DOMException && cause.name === 'AbortError');
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
      <label class="web-field">Формат<select
        v-model="format"
        :disabled="busy"
      ><option value="pdf">PDF</option><option value="docx">DOCX</option><option value="xlsx">XLSX</option><option value="png">PNG (ZIP)</option><option value="html">HTML</option></select></label>
      <button
        class="web-button"
        :disabled="!request || busy"
        @click="download"
      >
        {{ busy ? 'Подготовка…' : unknown ? 'Проверить выгрузку' : 'Экспортировать' }}
      </button>
    </div>
    <p
      v-if="!request"
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
