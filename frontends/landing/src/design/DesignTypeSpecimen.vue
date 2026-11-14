<script setup lang="ts">
import { ref } from 'vue'
import StoryIde from '../story/StoryIde.vue'
import type { StoryFrame } from '../story/story-source'

const defaultText = 'Расписание ИВТ-241'
const sample = ref(defaultText)
const digits = ref<'tabular' | 'proportional'>('tabular')
const scale = [
  { id: 'display', label: 'Header 1', size: '2rem · 32' },
  { id: 'title-screen', label: 'Header 2', size: '1.5rem · 24' },
  { id: 'title-block', label: 'Header 3', size: '1.125rem · 18' },
  { id: 'body', label: 'Header 4', size: '1rem · 16' },
  { id: 'dense', label: 'Header 5', size: '0.875rem · 14' },
  { id: 'caption', label: 'Header 6', size: '0.8125rem · 13' },
  { id: 'micro', label: 'Header 7', size: '0.75rem · 12' },
]
const numbers = ['08:30', '11:11', '11:40', '16,7 %']
const baseStyles = `html {
  font-family: var(--rct-font-family-sans);
  font-feature-settings:
    'tnum' 1,
    'lnum' 1;
  font-variant-numeric: tabular-nums;
}`
const baseFrames: readonly StoryFrame[] = [{
  title: 'Базовые стили шрифта',
  language: 'css',
  example: baseStyles.split('\n'),
}]
</script>

<template>
  <section id="design-type" class="design-chapter" aria-labelledby="design-type-title">
    <div class="landing-shell">
      <header class="design-chapter-heading">
        <h2 id="design-type-title">Буквы, цифры и ритм</h2>
        <p>Onest Variable связывает заголовки, расписание и плотные таблицы журнала. Гарнитура одна, а кегль и вес помогают отличить главное от деталей.</p>
      </header>

      <div class="design-type-layout">
        <div class="design-panel design-type-scale">
          <div class="design-type-input">
            <label for="design-type-sample">Текст образца</label>
            <input id="design-type-sample" v-model="sample" type="text"
              :placeholder="defaultText" maxlength="80" />
          </div>
          <ul class="design-type-rows">
            <li v-for="step in scale" :key="step.id" :data-size="step.id">
              <div class="design-type-row-label"><span>{{ step.label }}</span><code>{{ step.size }}</code></div>
              <span class="design-type-text">{{ sample.trim() || defaultText }}</span>
            </li>
          </ul>
        </div>

        <div class="design-type-numbers">
          <div class="design-panel design-numeric-panel">
            <fieldset class="design-fieldset">
              <legend>Ширина цифр</legend>
              <div class="design-choices">
                <label class="design-choice">
                  <input v-model="digits" type="radio" name="design-digit-width" value="tabular" />
                  <span>Табличные</span>
                </label>
                <label class="design-choice">
                  <input v-model="digits" type="radio" name="design-digit-width" value="proportional" />
                  <span>Пропорциональные</span>
                </label>
              </div>
            </fieldset>
            <ul class="design-digit-specimen" :data-digits="digits" aria-label="Образцы времени и процента">
              <li v-for="number in numbers" :key="number"><span>{{ number }}</span></li>
            </ul>
          </div>
          <p>В приложении <code>tabular-nums</code> включён в базовых стилях. Когда 8 сменяется на 1, цифра занимает столько же места. Это удерживает колонки времени и числа в журнале.</p>
          <StoryIde :frames="baseFrames" glass-context="Табличные цифры" />
        </div>
      </div>
    </div>
  </section>
</template>
