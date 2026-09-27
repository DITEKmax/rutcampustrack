<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { acceptsRequestFile, requestAttachmentFormatHint } from './attachment-validation'
import { RequestAttachmentPreviewUrls, requestAttachmentPreviewKind } from './request-attachment-preview'
import type { RequestFileLimits, RequestFileRef } from './types'

const props = withDefaults(defineProps<{
  modelValue: readonly RequestFileRef[]
  limits: RequestFileLimits | null
  disabled?: boolean
  error?: string | null
}>(), {
  disabled: false,
  error: null,
})

const emit = defineEmits<{
  'update:modelValue': [files: RequestFileRef[]]
}>()

const validationMessage = ref<string | null>(null)
const statusMessage = ref<string | null>(null)
const inputId = 'request-attachments-input'
const previewResources = new RequestAttachmentPreviewUrls()
const previewUrls = shallowRef<ReadonlyMap<string, string>>(new Map())
const previewUnavailableIds = shallowRef<ReadonlySet<string>>(new Set())
const previewErrors = shallowRef<ReadonlyMap<string, string>>(new Map())

const selectedFiles = computed(() => props.modelValue)
const limitDescription = computed(() => {
  const limits = props.limits
  const parts: string[] = []
  if (limits?.maxBytesPerFile !== null && limits?.maxBytesPerFile !== undefined) {
    parts.push('до ' + formatBytes(limits.maxBytesPerFile) + ' на файл')
  }
  if (limits?.maxFiles !== null && limits?.maxFiles !== undefined) {
    parts.push('не более ' + limits.maxFiles + ' ' + fileWord(limits.maxFiles))
  }
  if (parts.length === 0) return 'Выбери фото или файл'
  return parts.join(' · ')
})

const acceptValue = computed(() => {
  const types = props.limits?.contentTypes ?? []
  const extensions = props.limits?.extensions ?? []
  return [...types, ...extensions].join(',')
})

watch(selectedFiles, (files) => {
  const previews = previewResources.sync(files)
  previewUrls.value = previews.urls
  previewUnavailableIds.value = previews.unavailableIds
  previewErrors.value = new Map(
    [...previewErrors.value].filter(([id, url]) => previews.urls.get(id) === url),
  )
}, { immediate: true })

onBeforeUnmount(() => previewResources.clear())

function previewUrl(file: RequestFileRef): string | null {
  return previewUrls.value.get(file.id) ?? null
}

function hasPreviewError(file: RequestFileRef): boolean {
  const url = previewUrl(file)
  return url !== null && previewErrors.value.get(file.id) === url
}

function markPreviewError(fileId: string, event: Event): void {
  const target = event.currentTarget
  if (!(target instanceof Element)) return
  const url = target.getAttribute('src') ?? target.getAttribute('data')
  if (!url || previewUrls.value.get(fileId) !== url) return
  const next = new Map(previewErrors.value)
  next.set(fileId, url)
  previewErrors.value = next
}

function previewKind(file: RequestFileRef): ReturnType<typeof requestAttachmentPreviewKind> {
  return requestAttachmentPreviewKind(file.file)
}

function previewUnavailableMessage(file: RequestFileRef): string {
  if (!file.file) return 'Предпросмотр недоступен для этого вложения.'
  if (previewUnavailableIds.value.has(file.id)) {
    return 'Не удалось подготовить предпросмотр. Вложение можно удалить или выбрать снова.'
  }
  if (hasPreviewError(file)) {
    return 'Не удалось показать предпросмотр. Вложение можно удалить или выбрать снова.'
  }
  return 'Предпросмотр доступен для JPEG, PNG и PDF.'
}

function formatBytes(value: number): string {
  if (value >= 1024 * 1024) return (value / (1024 * 1024)).toFixed(value % (1024 * 1024) === 0 ? 0 : 1) + ' МБ'
  if (value >= 1024) return (value / 1024).toFixed(0) + ' КБ'
  return value + ' Б'
}

function fileWord(value: number): string {
  if (value % 10 === 1 && value % 100 !== 11) return 'файл'
  if (value % 10 >= 2 && value % 10 <= 4 && (value % 100 < 10 || value % 100 >= 20)) return 'файла'
  return 'файлов'
}

function fileId(file: File): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return file.name + '-' + file.size + '-' + file.lastModified
}

function validateFile(file: File): string | null {
  const limits = props.limits
  if (limits?.maxBytesPerFile !== null && limits?.maxBytesPerFile !== undefined && file.size > limits.maxBytesPerFile) {
    return file.name + ': размер больше ' + formatBytes(limits.maxBytesPerFile) + '.'
  }
  if (!acceptsRequestFile(file, limits)) {
    const accepted = [...(limits?.contentTypes ?? []), ...(limits?.extensions ?? [])].join(', ')
    const hint = requestAttachmentFormatHint(limits)
    return file.name + ': формат не поддерживается' + (accepted ? ' (' + accepted + ')' : '') + (hint ? '. ' + hint : '.')
  }
  return null
}

function onPick(event: Event): void {
  validationMessage.value = null
  statusMessage.value = null
  const input = event.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  const limits = props.limits
  const maxFiles = limits?.maxFiles
  const availableSlots = maxFiles === null || maxFiles === undefined
    ? picked.length
    : Math.max(0, maxFiles - selectedFiles.value.length)
  const accepted: RequestFileRef[] = []
  const errors: string[] = []

  if (picked.length > availableSlots && maxFiles !== null && maxFiles !== undefined) {
    errors.push('Можно добавить не более ' + maxFiles + ' ' + fileWord(maxFiles) + '.')
  }

  for (const file of picked.slice(0, availableSlots)) {
    const reason = validateFile(file)
    if (reason) errors.push(reason)
    else accepted.push({
      id: fileId(file),
      name: file.name,
      size: file.size,
      type: file.type,
      lastModified: file.lastModified,
      file,
    })
  }

  const currentTotal = selectedFiles.value.reduce((total, file) => total + file.size, 0)
  const acceptedWithinTotal: RequestFileRef[] = []
  let total = currentTotal
  for (const file of accepted) {
    if (limits?.maxBytesTotal !== null && limits?.maxBytesTotal !== undefined && total + file.size > limits.maxBytesTotal) {
      errors.push(file.name + ': общий размер вложений больше ' + formatBytes(limits.maxBytesTotal) + '.')
    } else {
      acceptedWithinTotal.push(file)
      total += file.size
    }
  }

  if (acceptedWithinTotal.length > 0) {
    emit('update:modelValue', [...selectedFiles.value, ...acceptedWithinTotal])
    statusMessage.value = 'Добавлено файлов: ' + acceptedWithinTotal.length + '.'
  }
  validationMessage.value = errors.length > 0 ? errors.join(' ') : null
  input.value = ''
}

function removeFile(id: string): void {
  emit('update:modelValue', selectedFiles.value.filter((file) => file.id !== id))
  statusMessage.value = 'Вложение удалено.'
}
</script>

<template>
  <div class="request-attachment-field">
    <label
      class="request-attachment-picker"
      :class="{ 'request-attachment-picker--disabled': props.disabled }"
      :for="inputId"
    >
      <span>Выбери фото или файл</span>
      <span class="request-attachment-picker__hint">{{ limitDescription }}</span>
      <input
        :id="inputId"
        class="request-visually-hidden"
        type="file"
        :accept="acceptValue || undefined"
        :multiple="(props.limits?.maxFiles ?? 2) !== 1"
        :disabled="props.disabled"
        @change="onPick"
      >
    </label>

    <p
      v-if="props.error || validationMessage"
      class="request-form-error"
      role="alert"
    >
      {{ props.error || validationMessage }}
    </p>
    <p
      v-if="statusMessage"
      class="request-form-status"
      role="status"
      aria-live="polite"
    >
      {{ statusMessage }}
    </p>

    <ul
      v-if="selectedFiles.length > 0"
      class="request-attachment-list"
      aria-label="Выбранные вложения"
    >
      <li
        v-for="file in selectedFiles"
        :key="file.id"
      >
        <div class="request-attachment-list__item">
          <span>
            <strong>{{ file.name }}</strong>
            <small>{{ formatBytes(file.size) }}</small>
          </span>
          <button
            type="button"
            :disabled="props.disabled"
            :aria-label="'Удалить вложение ' + file.name"
            @click="removeFile(file.id)"
          >
            Удалить
          </button>
        </div>

        <div
          class="request-attachment-preview"
          role="group"
          :aria-label="'Предпросмотр вложения ' + file.name"
        >
          <template v-if="previewKind(file) === 'image' && previewUrl(file) && !hasPreviewError(file)">
            <img
              class="request-attachment-preview__image"
              :src="previewUrl(file) ?? undefined"
              :alt="'Предварительный просмотр изображения: ' + file.name"
              @error="markPreviewError(file.id, $event)"
            >
          </template>

          <template v-else-if="previewKind(file) === 'pdf' && previewUrl(file) && !hasPreviewError(file)">
            <object
              class="request-attachment-preview__pdf"
              :data="previewUrl(file) ?? undefined"
              type="application/pdf"
              :aria-label="'Предпросмотр PDF: ' + file.name"
              @error="markPreviewError(file.id, $event)"
            >
              <p class="request-attachment-preview__message">
                Встроенный просмотр PDF недоступен.
              </p>
            </object>
            <p class="request-attachment-preview__message">
              Если встроенный просмотр не открылся, открой файл отдельно.
            </p>
            <a
              class="request-attachment-preview__open"
              :href="previewUrl(file) ?? undefined"
              target="_blank"
              rel="noopener noreferrer"
              :aria-label="'Открыть PDF в новой вкладке: ' + file.name"
            >
              Открыть PDF отдельно
            </a>
          </template>

          <template v-else>
            <p
              class="request-attachment-preview__message"
              role="status"
              aria-live="polite"
            >
              {{ previewUnavailableMessage(file) }}
            </p>
            <a
              v-if="previewKind(file) === 'pdf' && previewUrl(file)"
              class="request-attachment-preview__open"
              :href="previewUrl(file) ?? undefined"
              target="_blank"
              rel="noopener noreferrer"
              :aria-label="'Открыть PDF в новой вкладке: ' + file.name"
            >
              Открыть PDF отдельно
            </a>
          </template>
        </div>
      </li>
    </ul>
  </div>
</template>

<style src="./requests.pcss"></style>
