<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import './install-offer.pcss'
import type { InstallPromptController, InstallPromptState } from './install-prompt'

const props = defineProps<{
  controller: InstallPromptController
  eligible: boolean
}>()

const state = ref<InstallPromptState>(props.controller.getState())
const busy = ref(false)
let stopSubscription = (): void => undefined

const visible = computed(() => props.eligible
  && !state.value.dismissed
  && !state.value.installed
  && !state.value.standalone
  && (state.value.canInstall || state.value.ios))
const mode = computed(() => state.value.canInstall ? 'native' : 'ios')

onMounted(() => {
  stopSubscription = props.controller.subscribe((nextState) => {
    state.value = nextState
  })
})

onBeforeUnmount(() => {
  stopSubscription()
})

async function install(): Promise<void> {
  if (busy.value) return
  busy.value = true
  await props.controller.install()
  busy.value = false
  state.value = props.controller.getState()
}

function dismiss(): void {
  props.controller.dismiss()
}
</script>

<template>
  <aside
    v-if="visible"
    class="install-offer"
    :data-install-mode="mode"
    aria-label="Установка RutCampusTrack"
  >
    <span
      class="install-offer__mark"
      aria-hidden="true"
    >↗</span>
    <div class="install-offer__copy">
      <h2 class="install-offer__title">
        Добавь RutCampusTrack на главный экран
      </h2>
      <p
        v-if="mode === 'native'"
        class="install-offer__hint"
      >
        Открывай расписание и домашнее задание быстрее.
      </p>
      <p
        v-else
        class="install-offer__hint"
      >
        Открой эту страницу в Safari, нажми «Поделиться» → «На экран «Домой»».
      </p>
    </div>
    <button
      v-if="mode === 'native'"
      class="install-offer__install"
      type="button"
      :disabled="busy"
      :aria-busy="busy"
      @click="install"
    >
      {{ busy ? 'Открываем…' : 'Установить' }}
    </button>
    <button
      class="install-offer__dismiss"
      type="button"
      aria-label="Скрыть предложение установки навсегда"
      @click="dismiss"
    >
      <span aria-hidden="true">×</span>
    </button>
  </aside>
</template>
