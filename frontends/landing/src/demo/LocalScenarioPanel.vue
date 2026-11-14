<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import StoryIde from '../story/StoryIde.vue'
import type { StoryFrame } from '../story/story-source'

const props = defineProps<{ id: string; title: string; json: unknown; changed: boolean }>()
const emit = defineEmits<{ reset: [] }>()
const heading = ref<HTMLElement | null>(null)
const frames = computed<readonly StoryFrame[]>(() => [{
  title: 'Локальное состояние · JSON',
  language: 'json',
  example: (JSON.stringify(props.json, null, 2) ?? '').split('\n'),
}])
async function reset() {
  emit('reset')
  await nextTick()
  heading.value?.focus({ preventScroll: true })
}
</script>

<template>
  <section :id="id" class="local-scenario" :aria-labelledby="id + '-title'">
    <div class="local-scenario__main">
      <div class="local-scenario__header">
        <h3 :id="id + '-title'" ref="heading" tabindex="-1">{{ title }}</h3>
        <button type="button" :disabled="!changed" @click="reset">Сбросить сценарий</button>
      </div>
      <slot />
    </div>
    <div class="local-scenario__data">
      <StoryIde :frames="frames" glass-context="Данные сценария" :caption-id="id + '-json-title'" snapshot />
    </div>
  </section>
</template>

<style scoped>
.local-scenario {
  scroll-margin-block-start: calc(var(--story-header) + 1rem);
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(0, 1fr);
  gap: clamp(.85rem, 2vw, 1.5rem);
  margin-block: 1.5rem .5rem;
  padding: clamp(.75rem, 2.3vw, 1.5rem);
  border: 1px solid var(--story-line);
  border-radius: .7rem;
  background: color-mix(in srgb, var(--story-line) 22%, transparent);
  color: var(--story-muted);
  font-size: .88rem;
}
.local-scenario__main, .local-scenario__data {
  display: grid; grid-template-columns: minmax(0, 1fr); align-content: start; gap: .65rem; min-inline-size: 0;
}
.local-scenario__header {
  display: flex; align-items: baseline; flex-wrap: wrap; gap: .55rem 1rem; justify-content: space-between;
}
.local-scenario h3 { margin: 0; color: var(--story-text); }
.local-scenario h3 { font-size: clamp(1.05rem, 2vw, 1.38rem); }
.local-scenario :deep(p) { margin: 0; }
.local-scenario :deep(button), .local-scenario :deep(select) {
  min-block-size: 2.35rem;
  max-inline-size: 100%;
  padding: .35rem .65rem;
  border: 1px solid var(--story-line);
  border-radius: .4rem;
  background: var(--landing-bg);
  color: var(--story-text);
  font: inherit;
}
.local-scenario :deep(button[aria-pressed="true"]) { border-color: var(--landing-accent); }
.local-scenario :deep(button:hover:not(:disabled)) { border-color: var(--landing-accent); }
.local-scenario :deep(button:disabled) { opacity: .55; cursor: default; }
.local-scenario :deep(button:focus-visible), .local-scenario :deep(select:focus-visible) {
  outline: 2px solid var(--landing-accent); outline-offset: 2px;
}
.local-scenario :deep(.local-scene-controls) { display: flex; flex-wrap: wrap; align-items: center; gap: .45rem; }
.local-scenario :deep(.local-scene-facts) {
  margin: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .5rem;
}
.local-scenario :deep(.local-scene-facts > div) { padding: .55rem .65rem; border: 1px solid var(--story-line); border-radius: .45rem; }
.local-scenario :deep(.local-scene-facts dt) { color: var(--story-muted); font-size: .77rem; }
.local-scenario :deep(.local-scene-facts dd) { margin: .16rem 0 0; color: var(--story-text); }
@media (max-width: 44rem) {
  .local-scenario { grid-template-columns: minmax(0, 1fr); }
}
</style>
