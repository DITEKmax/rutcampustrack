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
import StudentRequestFiles from './StudentRequestFiles.vue';
const props = defineProps<{
  kind: RequestKind;
  options: RequestOptions | null;
  busy: boolean;
  ambiguous: boolean;
  readOnly: boolean;
  initialLesson?: string | undefined;
}>();
const emit = defineEmits<{
  submit: [payload: ExcuseRequestPayload | { lessonId: string }];
  close: [];
}>();
const selected = ref<string[]>(props.initialLesson ? [props.initialLesson] : []),
  reason = ref(''),
  comment = ref(''),
  files = ref<RequestFileRef[]>([]),
  from = ref(''),
  to = ref(''),
  confirm = ref(false),
  error = ref('');
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
const lessons = computed(
  () =>
    props.options?.lessons?.filter(
      (o) =>
        (!from.value || (o.lesson?.date ?? '') >= from.value) &&
        (!to.value || (o.lesson?.date ?? '') <= to.value),
    ) ?? [],
);
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
  const errors =
    props.kind === 'EXCUSE'
      ? validateExcusePayload(payload.value as ExcuseRequestPayload, props.options)
      : validateLateCheckinPayload(payload.value as { lessonId: string }, props.options);
  error.value = errors.join(' ');
  if (!errors.length) confirm.value = true;
}
function submit() {
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
    <p
      v-if="kind === 'LATE_CHECKIN'"
      class="web-notice"
    >
      Доступно попыток: {{ budgetLabel(options?.budget) }}. Одна отправка расходует одну попытку.
      После отмены или отклонения попытка не возвращается.
    </p>
    <div class="student-filter">
      <label class="web-field">С даты<input
        v-model="from"
        type="date"
        :disabled="disabled"
      ></label><label class="web-field">По дату<input
        v-model="to"
        type="date"
        :min="from || undefined"
        :disabled="disabled"
      ></label>
    </div>
    <div class="student-request-lessons web-notice web-stack">
      <fieldset
        v-for="day in days"
        :key="day.date"
        class="web-stack"
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
      v-if="selected.length"
      class="web-stack"
    >
      <p role="status">
        Выбрано пар: {{ selected.length }}
      </p><button
        v-for="id in selected"
        :key="id"
        type="button"
        class="web-button"
        :disabled="disabled"
        @click="selected = selected.filter((value) => value !== id)"
      >
        Убрать пару
        {{ options?.lessons?.find((o) => o.lesson?.id === id)?.lesson?.subjectName ?? id }}
      </button>
    </div>
    <div
      v-if="kind === 'EXCUSE'"
      class="student-dashboard-bottom"
    >
      <div class="web-stack">
        <label class="web-field">Причина<select
          v-model="reason"
          :disabled="disabled"
          required
        ><option value="">Выбери причину</option><option
          v-for="option in options?.reasons ?? []"
          :key="option.code"
          :value="option.code"
        >{{ option.label }}</option></select></label><label class="web-field">Комментарий<textarea
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
    <div class="student-actions">
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
        class="web-button"
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
