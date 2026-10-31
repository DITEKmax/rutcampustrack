<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import type {
  RequestFileLimits,
  RequestFileRef,
} from '../../../../mobile-core/src/features/requests/types';
import { requestAttachmentFormatHint } from '../../../../mobile-core/src/features/requests/attachment-validation';
import { RequestAttachmentPreviewUrls } from '../../../../mobile-core/src/features/requests/request-attachment-preview';
const props = defineProps<{
  files: readonly RequestFileRef[];
  limits: RequestFileLimits | null;
  disabled: boolean;
}>();
const emit = defineEmits<{ change: [files: RequestFileRef[]] }>();
const input = ref<HTMLInputElement | null>(null);
const urls = new RequestAttachmentPreviewUrls();
const previews = shallowRef(urls.sync([]));
const AttachmentPdfPreview = defineAsyncComponent(
  () => import('../../../../mobile-core/src/shared/components/AttachmentPdfPreview.vue'),
);
watch(
  () => props.files,
  (files) => {
    previews.value = urls.sync(files);
  },
  { immediate: true },
);
onBeforeUnmount(() => urls.clear());
const accept = computed(() =>
  [...(props.limits?.contentTypes ?? []), ...(props.limits?.extensions ?? [])].join(','),
);
function add(files: FileList | null) {
  if (!files || props.disabled) return;
  emit('change', [
    ...props.files,
    ...Array.from(files).map((file) => ({
      id: crypto.randomUUID(),
      name: file.name,
      size: file.size,
      type: file.type,
      lastModified: file.lastModified,
      file,
    })),
  ]);
  if (input.value) input.value.value = '';
}
function openPreview(id: string) {
  const url = previews.value.urls.get(id);
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
}
</script>
<template>
  <section
    class="web-stack"
    aria-label="Вложения заявки"
  >
    <div
      class="student-dropzone web-stack"
      @dragover.prevent
      @drop.prevent="add($event.dataTransfer?.files ?? null)"
    >
      <p>Добавь подтверждающие файлы перетаскиванием или выбери их с компьютера.</p><p class="web-caption">
        {{ requestAttachmentFormatHint(limits)
        }}<template v-if="limits?.maxFiles !== undefined">
          · не более {{ limits?.maxFiles }} файлов
        </template><template v-if="limits?.maxBytesPerFile">
          · до {{ Math.floor(limits.maxBytesPerFile / 1024 / 1024) }} МБ каждый
        </template>
      </p><label class="web-field">Выбрать файлы<input
        ref="input"
        type="file"
        multiple
        :accept="accept"
        :disabled="disabled"
        @change="add(($event.target as HTMLInputElement).files)"
      ></label>
    </div>
    <article
      v-for="file in files"
      :key="file.id"
      class="web-notice web-stack"
    >
      <div class="web-page-header">
        <strong>{{ file.name }}</strong><button
          class="web-button"
          :disabled="disabled"
          :aria-label="`Удалить вложение ${file.name}`"
          @click="
            emit(
              'change',
              files.filter((f) => f.id !== file.id),
            )
          "
        >
          Убрать
        </button>
      </div><p class="web-caption">
        {{ Math.ceil(file.size / 1024) }} КБ
      </p><template v-if="previews.urls.get(file.id)">
        <img
          v-if="file.type === 'image/png' || file.type === 'image/jpeg'"
          class="student-attachment-image"
          :src="previews.urls.get(file.id)"
          :alt="file.name"
        ><AttachmentPdfPreview
          v-else-if="file.type === 'application/pdf'"
          :url="previews.urls.get(file.id)!"
          :label="file.name"
          :open-file="() => openPreview(file.id)"
        />
      </template>
    </article>
  </section>
</template>
