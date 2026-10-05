<script setup lang="ts">
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import type { RequestKind, RequestTypeChoice } from './types'

const props = withDefaults(defineProps<{
  choices: readonly RequestTypeChoice[]
  busy?: boolean
  error?: string | null
}>(), {
  busy: false,
  error: null,
})

const emit = defineEmits<{
  back: []
  choose: [kind: RequestKind]
}>()
</script>

<template>
  <main
    class="requests-type-screen"
    aria-labelledby="request-type-title"
  >
    <header class="requests-form-header">
      <button
        class="requests-back-button"
        type="button"
        aria-label="Вернуться к заявкам"
        @click="emit('back')"
      >
        <svg
          viewBox="0 0 16 16"
          aria-hidden="true"
        >
          <path
            d="M10.5 3L5.5 8L10.5 13"
            stroke="currentColor"
            stroke-width="2"
          />
        </svg>
      </button>
      <h1 id="request-type-title">
        Новая заявка
      </h1>
    </header>

    <StudentWarningBlock
      v-if="props.error"
      severity="error"
      title="Не удалось загрузить варианты"
      :message="props.error"
    />

    <p
      v-if="props.busy"
      class="requests-form-status"
      role="status"
      aria-live="polite"
    >
      Открываем форму…
    </p>

    <ul
      class="requests-type-list"
      aria-label="Тип заявки"
    >
      <li
        v-for="choice in props.choices"
        :key="choice.kind"
      >
        <button
          class="requests-type-choice"
          :class="{ 'requests-type-choice--unavailable': !choice.available }"
          type="button"
          :disabled="!choice.available || props.busy"
          :aria-describedby="!choice.available ? 'request-choice-reason-' + choice.kind : undefined"
          @click="emit('choose', choice.kind)"
        >
          <span
            v-if="choice.kind === 'LATE_CHECKIN'"
            class="requests-type-choice__symbol-pair"
            aria-hidden="true"
          >
            <span class="requests-type-choice__mark requests-type-choice__mark--late">н</span>
            <span class="requests-type-choice__pair-arrow">→</span>
            <span class="requests-type-choice__mark requests-type-choice__mark--positive">+</span>
          </span>
          <span
            v-else
            class="requests-type-choice__symbol requests-type-choice__symbol--excuse"
            aria-hidden="true"
          >{{ choice.symbol }}</span>
          <span class="requests-type-choice__label">{{ choice.label }}</span>
        </button>
        <p
          v-if="!choice.available"
          :id="'request-choice-reason-' + choice.kind"
          class="requests-type-choice__reason"
        >
          {{ choice.reason || 'Подача сейчас недоступна.' }}
        </p>
      </li>
    </ul>
  </main>
</template>

<style src="./requests.pcss"></style>
