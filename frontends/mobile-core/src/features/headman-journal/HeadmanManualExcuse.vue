<script setup lang="ts">
import {
  defineAsyncComponent,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  useId,
  watch,
} from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import HeadmanStatsSelect from '../headman-stats/HeadmanStatsSelect.vue';
import '../../shared/components/mobile-upload.pcss';
import type {
  HeadmanJournalApi,
  HeadmanJournalExcuseType,
  HeadmanJournalRosterEntry,
} from './headman-journal-client';
import { HeadmanManualPreview, type ManualPreviewState } from './headman-manual-preview';
import {
  excuseLabel,
  excuseTypes,
  manualStatusLabel,
  isAttendanceDenied,
  type ExcuseCommand,
  type ManualRowIssue,
} from './headman-manual-marks';
import './headman-manual-attendance.pcss';

const AttachmentPdfPreview = defineAsyncComponent(
  () => import('../../shared/components/AttachmentPdfPreview.vue'),
);
const props = defineProps<{
  entry: HeadmanJournalRosterEntry;
  api: HeadmanJournalApi | null;
  lessonId: number;
  context: string;
  allowed: boolean;
  pending: boolean;
  blocked: boolean;
  stale: boolean;
  issue: ManualRowIssue | null;
}>();
const emit = defineEmits<{
  back: [];
  save: [command: ExcuseCommand];
  reviewConflict: [];
  refresh: [source: EventTarget | null];
  accessDenied: [cause: unknown];
}>();
const id = useId();
const backButton = ref<HTMLButtonElement | null>(null);
const reasonField = ref<InstanceType<typeof HeadmanStatsSelect> | null>(null);
const reason = ref<HeadmanJournalExcuseType | ''>(
  excuseTypes.find((item) => item.value === props.entry.excuseType)?.value ?? '',
);
function selectReason(value: string): void {
  const option = excuseTypes.find((item) => item.value === value);
  if (!option) return;
  reason.value = option.value;
  reasonError.value = false;
}
const comment = ref(props.entry.comment ?? '');
const file = shallowRef<File | null>(null);
const reasonError = ref(false);
const commentError = ref(false);
const preview = ref<ManualPreviewState>({ url: null, mime: null, loading: false, error: null });

const previewOwner = new HeadmanManualPreview(
  () => ({
    api: props.api,
    lessonId: props.lessonId,
    userId: props.entry.userId,
    attachmentId: props.entry.attachmentId,
    file: file.value,
    ownerKey: props.entry.markRevision ?? '',
    allowed: props.allowed,
  }),
  (state) => {
    preview.value = state;
  },
  undefined,
  (cause) => {
    if (isAttendanceDenied(cause)) emit('accessDenied', cause);
  },
);
watch(
  () => [props.api, props.lessonId, props.entry, props.allowed, file.value] as const,
  () => void previewOwner.load(),
  { immediate: true, flush: 'sync' },
);
onMounted(() => backButton.value?.focus?.({ preventScroll: true }));
onBeforeUnmount(() => previewOwner.dispose());

function chooseFile(event: Event): void {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null;
}
function openAttachment(): void {
  const url = previewOwner.openUrl();
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
}
function submit(): void {
  if (props.blocked || !props.allowed || props.issue?.kind === 'conflict') return;
  reasonError.value = !excuseTypes.some((option) => option.value === reason.value);
  commentError.value = Array.from(comment.value).length > 1000;
  if (reasonError.value) reasonField.value?.focus();
  if (reasonError.value || commentError.value || !reason.value) return;
  emit('save', {
    status: 'EXCUSED',
    excuseType: reason.value,
    ...(comment.value.trim() ? { comment: comment.value } : {}),
    ...(file.value ? { file: file.value } : {}),
  });
}
</script>

<template>
  <section
    class="headman-manual-excuse"
    :aria-labelledby="`${id}-title`"
    @keydown.esc.stop.prevent="emit('back')"
  >
    <header class="headman-manual-excuse__heading">
      <button
        ref="backButton"
        class="headman-manual-excuse__back"
        type="button"
        aria-label="Назад"
        :aria-disabled="pending || undefined"
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button>
      <h1 :id="`${id}-title`">Причина «у»</h1>
    </header>
    <div class="headman-manual-excuse__student">
      <div
        ><strong>{{ entry.displayName }}</strong
        ><p>{{ context }}</p></div
      >
      <span
        class="headman-manual__circle"
        data-mark="EXCUSED"
        aria-hidden="true"
        >у</span
      >
    </div>
    <form
      novalidate
      :aria-busy="pending"
      @submit.prevent="submit"
    >
      <fieldset :disabled="pending">
        <label :for="`${id}-reason`">Причина</label>
        <HeadmanStatsSelect
          ref="reasonField"
          class="headman-manual-excuse__select"
          label="Причина уважительной отметки"
          placeholder="Выбери причину"
          :trigger-id="`${id}-reason`"
          :model-value="reason"
          :options="excuseTypes"
          :disabled="pending"
          :invalid="reasonError"
          :error-id="reasonError ? `${id}-reason-error` : undefined"
          @update:model-value="selectReason"
        />
        <p
          v-if="reasonError"
          :id="`${id}-reason-error`"
          class="headman-manual-excuse__error"
          role="alert"
        >
          Выбери причину.
        </p>
        <label :for="`${id}-comment`">Комментарий <small>необязательно</small></label>
        <textarea
          :id="`${id}-comment`"
          v-model="comment"
          rows="3"
          maxlength="1000"
          :aria-invalid="commentError || undefined"
          :aria-describedby="commentError ? `${id}-comment-error` : undefined"
          @input="commentError = false"
        />
        <p
          v-if="commentError"
          :id="`${id}-comment-error`"
          class="headman-manual-excuse__error"
          role="alert"
        >
          Комментарий не может быть длиннее 1000 символов.
        </p>
        <label class="mobile-upload">
          <input
            type="file"
            accept="image/jpeg,image/png,application/pdf"
            @change="chooseFile"
          />
          <MobileIcon name="homework" />
          <span>{{
            file || entry.attachmentId ? 'Заменить документ' : 'Прикрепить документ'
          }}</span>
          <small>необязательно</small>
        </label>
      </fieldset>
      <section
        v-if="preview.loading || preview.url || preview.error"
        class="headman-manual-excuse__preview"
        aria-label="Вложение к причине"
        :aria-busy="preview.loading"
      >
        <p
          v-if="preview.loading"
          role="status"
          >Загружаем вложение…</p
        >
        <p
          v-if="preview.error"
          role="alert"
          >{{ preview.error }}</p
        >
        <img
          v-if="preview.url && preview.mime !== 'application/pdf'"
          :src="preview.url"
          alt="Вложение к причине"
          @error="preview.error = 'Не удалось показать изображение.'"
        />
        <AttachmentPdfPreview
          v-else-if="preview.url"
          :url="preview.url"
          :open-file="openAttachment"
          label="Первая страница PDF к причине"
        />
      </section>
      <p
        v-if="issue"
        class="headman-manual-excuse__error"
        role="alert"
        >{{ issue.message }}</p
      >
      <section
        v-if="issue?.kind === 'conflict' && !stale"
        class="headman-manual-excuse__conflict"
        :aria-labelledby="`${id}-actual`"
      >
        <h2 :id="`${id}-actual`">Актуальная отметка</h2>
        <p>Статус: {{ manualStatusLabel[entry.status] }}</p>
        <p v-if="entry.excuseReason || entry.excuseType">
          Причина: {{ excuseLabel(entry.excuseReason ?? entry.excuseType) }}
        </p>
        <p v-if="entry.comment">Комментарий: {{ entry.comment }}</p>
        <button
          class="headman-manual-roster__retry"
          type="button"
          :disabled="blocked || !allowed"
          @click="emit('reviewConflict')"
        >
          Продолжить с этой отметкой
        </button>
      </section>
      <button
        v-if="stale"
        class="headman-manual-roster__retry"
        type="button"
        :disabled="blocked"
        @click="emit('refresh', $event.currentTarget)"
      >
        Обновить отметку
      </button>
      <button
        class="headman-manual-excuse__save"
        type="submit"
        data-action="confirm"
        :aria-disabled="blocked || !allowed || issue?.kind === 'conflict' ? true : undefined"
      >
        {{ pending ? 'Сохраняем…' : 'Сохранить' }}
      </button>
    </form>
  </section>
</template>
