<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import type {
  ExcuseRequestPayload,
  RequestFileRef,
  RequestKind,
  RequestOptions,
} from '../../../../mobile-core/src/features/requests/types';
import {
  canRequestLesson,
  budgetLabel,
  lessonTypeLabel,
  formatLessonDate,
  formatLessonTime,
} from '../../../../mobile-core/src/features/requests/state';
import {
  validateExcusePayload,
  validateLateCheckinPayload,
} from '../../../../mobile-core/src/features/requests/requests-controller';
import ConfirmDialog from '../../shared/ConfirmDialog.vue';
import WebDateField from '../../shared/WebDateField.vue';
import WebSelect from '../../shared/WebSelect.vue';
import StudentRequestFiles from './StudentRequestFiles.vue';
const props = defineProps<{
  kind: RequestKind;
  options: RequestOptions | null;
  busy: boolean;
  ambiguous: boolean;
  readOnly: boolean;
  initialLesson?: string | undefined;
  today?: string | undefined;
}>();
const emit = defineEmits<{
  submit: [payload: ExcuseRequestPayload | { lessonId: string }];
  close: [];
}>();
const selected = ref<string[]>([]),
  reason = ref(''),
  comment = ref(''),
  files = ref<RequestFileRef[]>([]),
  from = ref(''),
  to = ref(''),
  confirm = ref(false),
  error = ref('');
const fromValid = ref(false), toValid = ref(false);
let datesEdited = false;
let initialApplied = false;
let pendingInitial: string | null = null;
const form = ref<HTMLElement | null>(null);
watch(
  () => props.kind,
  async () => {
    await nextTick();
    form.value?.querySelector<HTMLElement>('input,select')?.focus();
  },
  { immediate: true },
);
const disabled = computed(() => props.busy || props.ambiguous || props.readOnly);
const rangeReady = computed(() => Boolean(fromValid.value && toValid.value && from.value && to.value && from.value <= to.value));
function editDates() {
  datesEdited = true;
  pendingInitial = null;
  confirm.value = false;
}
watch(() => [props.initialLesson, props.options], () => {
  if (initialApplied || datesEdited || from.value || to.value || !props.initialLesson) return;
  const date = props.options?.lessons?.find((option) => option.lesson?.id === props.initialLesson)?.lesson?.date;
  if (!date) return;
  initialApplied = true;
  pendingInitial = props.initialLesson;
  from.value = date;
  to.value = date;
}, { immediate: true });
const lessons = computed(
  () =>
    rangeReady.value ? props.options?.lessons?.filter(
      (option) => Boolean(option.lesson?.date) && option.lesson!.date! >= from.value && option.lesson!.date! <= to.value,
    ) ?? [] : [],
);
watch([lessons, rangeReady, disabled], () => {
  if (disabled.value) return;
  confirm.value = false;
  const visible = new Set(lessons.value.flatMap((option) => option.lesson ? [option.lesson.id] : []));
  selected.value = selected.value.filter((id) => visible.has(id));
  if (pendingInitial && rangeReady.value && visible.has(pendingInitial)) {
    selected.value = [pendingInitial];
    pendingInitial = null;
  }
}, { flush: 'sync' });
const days = computed(() =>
  [...new Set(lessons.value.map((l) => l.lesson?.date ?? ''))]
    .sort()
    .map((date) => ({
      date,
      lessons: lessons.value.filter((l) => (l.lesson?.date ?? '') === date),
    })),
);
const payload = computed<ExcuseRequestPayload | { lessonId: string }>(() =>
  props.kind === 'EXCUSE'
    ? {
        lessonIds: [...selected.value],
        reason: reason.value,
        comment: comment.value,
        files: [...files.value],
      }
    : { lessonId: selected.value[0] ?? '' },
);
const selectedLessons = computed(
  () =>
    props.options?.lessons?.filter((o) => o.lesson && selected.value.includes(o.lesson.id)) ?? [],
);
function select(id: string, checked: boolean) {
  selected.value =
    props.kind === 'LATE_CHECKIN'
      ? checked
        ? [id]
        : []
      : checked
        ? [...selected.value, id]
        : selected.value.filter((v) => v !== id);
}
function prepare() {
  if (disabled.value) return;
  if (!rangeReady.value) {
    error.value = 'Укажи корректные даты начала и конца периода.';
    return;
  }
  if (selected.value.some((id) => !lessons.value.some((option) => option.lesson?.id === id))) {
    error.value = 'Выбери пары в указанном периоде.';
    return;
  }
  const errors =
    props.kind === 'EXCUSE'
      ? validateExcusePayload(payload.value as ExcuseRequestPayload, props.options)
      : validateLateCheckinPayload(payload.value as { lessonId: string }, props.options);
  error.value = errors.join(' ');
  if (!errors.length) confirm.value = true;
}
function submit() {
  if (disabled.value || !rangeReady.value) return;
  confirm.value = false;
  emit('submit', payload.value);
}
</script>
<template>
  <form
    ref="form"
    class="student-request-form web-stack"
    :aria-busy="busy"
    @submit.prevent="prepare"
  >
    <h2>{{ kind === 'EXCUSE' ? 'Уважительная причина' : 'Оспорить отсутствие' }}</h2>
    <div
      v-if="kind === 'LATE_CHECKIN'"
      class="student-request-budget"
      role="status"
    >
      <template v-if="options?.budget?.remaining != null && options.budget.limit != null">
        <p class="student-metric-value">{{ options.budget.remaining }} / {{ options.budget.limit }}</p>
        <p class="web-caption">доступных попыток</p>
      </template>
      <p v-else class="web-caption">{{ budgetLabel(options?.budget) }}</p>
    </div>
    <div class="student-filter">
      <WebDateField
        v-model="from"
        label="С даты"
        :today="today"
        :disabled="disabled"
        @validity="fromValid = $event"
        @input="editDates"
        @change="editDates"
      /><WebDateField
        v-model="to"
        label="По дату"
        :today="today"
        :min="from || undefined"
        :disabled="disabled"
        @validity="toValid = $event"
        @input="editDates"
        @change="editDates"
      />
    </div>
    <div v-if="rangeReady" class="student-request-lessons web-stack" tabindex="0" aria-label="Пары для заявки">
      <fieldset
        v-for="day in days"
        :key="day.date"
        class="student-request-day"
      >
        <legend>{{ day.date ? formatLessonDate(day.date) : 'Дата не указана' }}</legend><label
          v-for="option in day.lessons"
          :key="option.lesson!.id"
          class="student-request-option"
        ><input
          :checked="selected.includes(option.lesson!.id)"
          :type="kind === 'EXCUSE' ? 'checkbox' : 'radio'"
          name="request-lesson"
          :disabled="
            disabled ||
              !canRequestLesson(option, kind) ||
              (kind === 'LATE_CHECKIN' && (options?.budget?.remaining ?? 0) <= 0)
          "
          @change="select(option.lesson!.id, ($event.target as HTMLInputElement).checked)"
        ><span><strong>{{ option.lesson?.subjectName ?? 'Предмет не указан' }}</strong><br>{{ lessonTypeLabel(option.lesson?.subjectType) }} ·
          {{ formatLessonTime(option.lesson!)
          }}<br v-if="!canRequestLesson(option, kind)"><small
            v-if="!canRequestLesson(option, kind)"
            class="web-caption"
          >{{
            option.pendingRequests?.some((p) => p.kind === kind)
              ? 'По этой паре уже есть активная заявка'
              : option.unavailableReason || 'Пара недоступна для этой заявки'
          }}</small></span></label>
      </fieldset><p v-if="!days.length">
        Для выбранного периода пар нет.
      </p>
    </div>
    <div
      v-if="kind === 'EXCUSE'"
      class="student-dashboard-bottom"
    >
      <div class="web-stack">
        <WebSelect
          v-model="reason"
          label="Причина"
          :options="[{ value: '', label: 'Выбери причину' }, ...(options?.reasons ?? []).map((option) => ({ value: option.code, label: option.label }))]"
          :disabled="disabled"
          required
        /><label class="web-field">Комментарий<textarea
          v-model="comment"
          rows="6"
          maxlength="4000"
          :disabled="disabled"
          :required="options?.reasons?.find((r) => r.code === reason)?.commentRequired"
        />
        </label>
      </div><StudentRequestFiles
        :files="files"
        :limits="options?.files ?? null"
        :disabled="disabled"
        @change="files = $event"
      />
    </div>
    <p
      v-if="error"
      class="web-error"
      role="alert"
    >
      {{ error }}
    </p>
    <div class="student-request-actions">
      <button
        type="button"
        class="web-button"
        :disabled="busy || ambiguous"
        @click="emit('close')"
      >
        Закрыть форму
      </button><button
        class="web-button web-button-primary"
        :disabled="disabled || !options"
        type="submit"
      >
        Отправить заявку
      </button><button
        v-if="ambiguous"
        type="button"
        class="web-button student-request-retry"
        :disabled="busy || readOnly"
        @click="emit('submit', payload)"
      >
        Уточнить результат той же отправки
      </button>
    </div>
    <ConfirmDialog
      :open="confirm"
      title="Отправить заявку?"
      confirm-label="Подтвердить отправку"
      :busy="busy"
      @cancel="confirm = false"
      @confirm="submit"
    >
      <p>Пары, включённые в заявку:</p><ul>
        <li
          v-for="option in selectedLessons"
          :key="option.lesson!.id"
        >
          {{ option.lesson?.subjectName }} · {{ formatLessonDate(option.lesson?.date) }} ·
          {{ formatLessonTime(option.lesson!) }}
        </li>
      </ul><p v-if="kind === 'LATE_CHECKIN'">
        Будет потрачена одна попытка из {{ budgetLabel(options?.budget) }}.
      </p>
    </ConfirmDialog>
  </form>
</template>
