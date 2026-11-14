<script setup lang="ts">
import { computed, ref } from 'vue'
import { tokenizeStoryLine } from './java-tokens'
import type { StoryFrame, StoryLanguage, StoryLine } from './story-source'
import './story-ide.pcss'
import './architecture-snippets.pcss'

const props = defineProps<{
  frames: readonly StoryFrame[]
  glassContext: string
  languageContexts?: Partial<Record<StoryLanguage, string>> | undefined
  snapshot?: boolean
  captionId?: string
}>()

const languageLabels: Record<StoryLanguage, string> = {
  java: 'Java',
  json: 'JSON',
  http: 'HTTP',
  proto: 'Protobuf',
  config: 'Конфигурация',
  python: 'Python',
  typescript: 'TypeScript',
  sql: 'SQL',
  css: 'CSS',
}

const page = ref(0)
const current = computed(() => props.frames[page.value]!)
const scrollSource = computed(() => 'lines' in current.value || current.value.language === 'css' || props.snapshot)
const frameTitle = computed(() => current.value.title.replace(/ · демо$/, ''))
const context = computed(() => props.languageContexts?.[current.value.language] ?? props.glassContext)
const languageLabel = computed(() => {
  const frame = current.value
  if (frame.language === 'http' && 'example' in frame) {
    return frame.example.join('\n').match(/\bHTTP\/\d+(?:\.\d+)?\b/)?.[0] ?? languageLabels.http
  }
  if (frame.language === 'config' && 'path' in frame) {
    if (/\.ya?ml$/i.test(frame.path)) return 'YAML'
    if (frame.path.endsWith('.alloy')) return 'Alloy'
    if (frame.path.startsWith('nginx/')) return 'Nginx config'
  }
  return languageLabels[frame.language]
})
const lines = computed(() => {
  const frame = current.value
  const rows: readonly StoryLine[] = 'lines' in frame
    ? frame.lines
    : frame.example.map((text, index) => [index + 1, text])
  const indents = rows.map(([, text]) => text.search(/\S/)).filter((size) => size >= 0)
  const common = indents.length ? Math.min(...indents) : 0
  return rows.map(([number, text], index) => ({
    number,
    gapStart: index && number > rows[index - 1]![0] + 1 ? rows[index - 1]![0] + 1 : null,
    tokens: tokenizeStoryLine(text.slice(common), frame.language),
  }))
})

function step(direction: -1 | 1) {
  page.value = Math.max(0, Math.min(props.frames.length - 1, page.value + direction))
}
</script>

<template>
  <div class="architecture-glass" :class="{ 'architecture-glass--snapshot': snapshot }">
    <figure class="rct-story__ide rct-story__ide--glass" :aria-label="frameTitle">
      <figcaption :id="captionId" class="rct-story__ide-bar" @keydown.left.prevent="step(-1)" @keydown.right.prevent="step(1)">
        <strong :aria-live="frames.length > 1 ? 'polite' : undefined">{{ frameTitle }}</strong>
        <div v-if="frames.length > 1" class="rct-story__ide-nav">
          <button type="button" aria-label="Предыдущий фрагмент" :aria-disabled="page === 0" @click="step(-1)">
            <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m12 5-5 5 5 5" /></svg>
          </button>
          <span :aria-label="'Кадр ' + (page + 1) + ' из ' + frames.length">{{ page + 1 }} / {{ frames.length }}</span>
          <button type="button" aria-label="Следующий фрагмент" :aria-disabled="page === frames.length - 1" @click="step(1)">
            <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m8 5 5 5-5 5" /></svg>
          </button>
        </div>
      </figcaption>
      <div class="rct-story__glass-tags">
        <span>{{ languageLabel }}</span>
        <span>{{ context }}</span>
      </div>
      <div :key="scrollSource ? page : undefined" :class="{ 'rct-story__glass-scroll': scrollSource }"
        :role="scrollSource ? 'region' : undefined" :aria-label="scrollSource ? 'Код · ' + frameTitle : undefined"
        :tabindex="scrollSource ? 0 : undefined">
        <ol class="rct-story__ide-lines" role="list">
          <template v-for="line in lines" :key="line.number">
            <li v-if="line.gapStart" class="rct-story__ide-gap">
              <span aria-hidden="true">…</span>
              <span class="rct-story__sr">Пропуск строк {{ line.gapStart }}–{{ line.number - 1 }}</span>
            </li>
            <li :data-source-line="line.number">
              <span class="rct-story__ide-number">{{ line.number }}</span>
              <code><span v-for="(token, index) in line.tokens" :key="index" :data-token="token.kind">{{ token.text }}</span></code>
            </li>
          </template>
        </ol>
      </div>
    </figure>
  </div>
</template>
