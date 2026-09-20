<script setup lang="ts">
import { computed, ref } from 'vue'
import { acceptsRequestFile, requestAttachmentFormatHint } from './attachment-validation'
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
      </li>
    </ul>
  </div>
</template>

<style src="./requests.pcss"></style>
