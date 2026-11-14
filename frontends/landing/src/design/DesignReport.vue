<script setup lang="ts">
import { onMounted, ref } from 'vue'
import DesignColorPalette from './DesignColorPalette.vue'
import DesignTypeSpecimen from './DesignTypeSpecimen.vue'
import StoryIde from '../story/StoryIde.vue'
import type { StoryFrame } from '../story/story-source'
import pwaToday from './assets/pwa-today.webp'
import './design-report.pcss'

const emit = defineEmits<{ ready: [] }>()
onMounted(() => emit('ready'))

const annotations = [
  {
    number: 1,
    title: 'Текущая пара',
    description: 'Сиреневая карточка сразу показывает, что идёт сейчас. В ней собраны время, предмет, аудитория и кнопка отметки.',
  },
  {
    number: 2,
    title: 'Время в одной колонке',
    description: 'У каждой пары начало набрано заметнее конца. Цифры одинаковой ширины держат разряды на одной вертикали.',
  },
  {
    number: 3,
    title: 'Детали под названием',
    description: 'Тип занятия и обычная аудитория набраны спокойнее. Если аудиторию поменяли, выделяем новую, зачёркиваем прежнюю и подписываем изменение.',
  },
]

const theme = ref<'dark' | 'light'>('dark')
const palette = [
  { id: 'base', title: 'Фон', token: '--rct-color-surface-base', dark: '#0B0B0C', light: '#F2F2F4' },
  { id: 'raised', title: 'Поверхность', token: '--rct-color-surface-raised', dark: '#1C1922', light: '#FFFFFF' },
  { id: 'text', title: 'Текст', token: '--rct-color-text-primary', dark: '#EDEAF3', light: '#292531' },
  { id: 'accent', title: 'Сейчас', token: '--rct-color-accent-now', dark: '#B79CFA', light: '#6D28D9' },
] as const

const lessonStyles = `.student-lesson[data-current='true'] .student-lesson__card {
  color: var(--rct-color-accent-on-now);
  background: var(--rct-color-accent-now);
}`
const lessonFrames: readonly StoryFrame[] = [{
  title: 'Стили карточки текущей пары',
  language: 'css',
  example: lessonStyles.split('\n'),
}]
</script>

<template>
  <div class="design-report">
    <section id="design-today" class="design-chapter" aria-labelledby="design-today-title">
      <div class="landing-shell">
        <header class="design-chapter-heading">
          <h2 id="design-today-title">Учебный день</h2>
        </header>

        <div class="design-today-layout">
          <figure class="design-today-figure">
            <div class="design-today-screen">
              <img :src="pwaToday" width="390" height="844" decoding="async"
                alt="Экран «Сегодня». Текущая пара — Основы программирования, 10:05–11:25. Ниже показано расписание с изменением аудитории на следующей паре." />
              <span v-for="annotation in annotations" :key="annotation.number" aria-hidden="true"
                class="design-marker design-marker--overlay" :data-index="annotation.number">{{ annotation.number }}</span>
            </div>
          </figure>

          <div class="design-today-notes">
            <ol class="design-annotations">
              <li v-for="annotation in annotations" :key="annotation.number">
                <span class="design-marker" aria-hidden="true">{{ annotation.number }}</span>
                <div>
                  <h3>{{ annotation.title }}</h3>
                  <p>{{ annotation.description }}</p>
                </div>
              </li>
            </ol>
            <div class="design-links">
              <a class="design-primary-link" href="/presentation/#demo">Открыть демо PWA <span aria-hidden="true">↗</span></a>
            </div>
          </div>
        </div>
      </div>
    </section>

    <DesignTypeSpecimen />

    <section id="design-tokens" class="design-chapter" aria-labelledby="design-tokens-title">
      <div class="landing-shell">
        <header class="design-chapter-heading">
          <h2 id="design-tokens-title">Цвет и переменные</h2>
          <p>Текущую пару выделяет один и тот же токен. В тёмной теме он даёт светло-сиреновую заливку, в светлой — более глубокий фиолетовый с белым текстом.</p>
        </header>

        <div class="design-token-layout">
          <div class="design-panel design-token-sample">
            <fieldset class="design-fieldset">
              <legend>Тема карточки</legend>
              <div class="design-choices">
                <label class="design-choice">
                  <input v-model="theme" type="radio" name="design-lesson-theme" value="dark" />
                  <span>Тёмная</span>
                </label>
                <label class="design-choice">
                  <input v-model="theme" type="radio" name="design-lesson-theme" value="light" />
                  <span>Светлая</span>
                </label>
              </div>
            </fieldset>
            <div class="design-theme-preview" :data-theme="theme">
              <article class="design-preview-lesson">
                <p class="design-preview-time">10:05–11:25 <span>Идёт сейчас</span></p>
                <h3>Основы программирования</h3>
                <p>А-401 · Лекция</p>
              </article>
              <article class="design-preview-lesson design-preview-lesson--next">
                <p class="design-preview-time">11:40–13:00 <span>Следующая пара</span></p>
                <h3>Компьютерные сети</h3>
                <p>А-508 · Лабораторная</p>
              </article>
            </div>
            <dl class="design-palette" :data-theme="theme">
              <div v-for="swatch in palette" :key="swatch.id">
                <dt>{{ swatch.title }}</dt>
                <dd>
                  <span class="design-swatch" :data-swatch="swatch.id" aria-hidden="true"></span>
                  <span>{{ swatch[theme] }}</span>
                  <code>{{ swatch.token }}</code>
                </dd>
              </div>
            </dl>
          </div>

          <div class="design-token-explanation">
            <h3>От оттенка к карточке</h3>
            <ol class="design-token-chain">
              <li><span>Палитра</span><code>{{ theme === 'dark' ? 'color/violet/300' : 'color/violet/600' }}</code></li>
              <li><span>Назначение</span><code>color/accent/now</code></li>
              <li><span>Переменная в коде</span><code>--rct-color-accent-now</code></li>
            </ol>
            <p>В макете назначение «сейчас» ссылается на оттенок палитры. В CSS значение подставляет <code>data-theme</code>.</p>
            <StoryIde :frames="lessonFrames" glass-context="Акцент текущей пары" />
          </div>
        </div>
      </div>
    </section>

    <DesignColorPalette />
  </div>
</template>
