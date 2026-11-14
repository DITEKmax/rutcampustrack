<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { TerminalExample } from './topic-terminals'
import './architecture-snippets.pcss'

const props = defineProps<{ id: string; example: TerminalExample }>()
const headingId = computed(() => props.id === 'observability' ? 'architecture-terminal-heading' : props.id + '-terminal-heading')
const terminal = ref<HTMLElement | null>(null)
const inView = ref(false)
const paused = ref(false)
let observer: IntersectionObserver | undefined

onMounted(() => {
  if (!terminal.value) return
  if (!('IntersectionObserver' in window)) {
    inView.value = true
    return
  }
  observer = new IntersectionObserver(([entry]) => {
    inView.value = Boolean(entry?.isIntersecting)
  })
  observer.observe(terminal.value)
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <section ref="terminal" class="architecture-terminal" :data-playing="inView && !paused"
    :aria-labelledby="headingId">
    <header class="architecture-terminal__header">
      <h3 :id="headingId">{{ example.title }}</h3>
      <button type="button" class="architecture-terminal__toggle" :aria-pressed="paused"
        @click="paused = !paused">Пауза анимации</button>
    </header>
    <div class="architecture-terminal__body">
      <p class="architecture-terminal__command">
        <span aria-hidden="true">$</span>
        <code>{{ example.command }}</code>
      </p>
      <ul role="list" aria-label="Результаты симуляции">
        <li v-for="line in example.output" :key="line.detail">
          <strong>{{ line.channel }}</strong>
          <span>{{ line.detail }}</span>
        </li>
      </ul>
    </div>
  </section>
</template>
