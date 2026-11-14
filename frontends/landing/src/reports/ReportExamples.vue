<script setup lang="ts">
import './report-examples.pcss'

const assetBase = `${import.meta.env.BASE_URL}report-examples/`

const examples = [
  {
    name: 'weekly-journal',
    role: 'Староста',
    title: 'Недельный журнал группы',
    alt: 'Первая страница журнала с занятиями за неделю и отметками студентов',
    width: 1500,
    height: 1061,
    format: 'pdf',
    action: 'Открыть PDF',
  },
  {
    name: 'subject-journal',
    role: 'Преподаватель',
    title: 'Журнал по предмету',
    alt: 'Первая страница журнала с датами занятий по предмету и отметками студентов',
    width: 1500,
    height: 1061,
    format: 'pdf',
    action: 'Открыть PDF',
  },
  {
    name: 'attendance-stats',
    role: 'Преподаватель',
    title: 'Статистика посещаемости по студентам',
    alt: 'Лист Excel с количеством отметок и долей посещённых занятий по каждому студенту',
    width: 1600,
    height: 419,
    format: 'webp',
    action: 'Увеличить предпросмотр',
  },
  {
    name: 'groups-attendance',
    role: 'Администратор',
    title: 'Посещаемость групп',
    alt: 'HTML-отчёт с показателями посещаемости по учебным группам',
    width: 1140,
    height: 768,
    format: 'html',
    action: 'Открыть HTML',
  },
]
</script>

<template>
  <section id="report-examples" class="rct-story" aria-labelledby="report-examples-title">
    <header class="rct-story__chapter">
      <h2 id="report-examples-title" class="rct-story__wrap">Примеры отчётов</h2>
    </header>
    <ul role="list">
      <li v-for="example in examples" :key="example.name" class="rct-story__operation">
        <div class="rct-story__wrap rct-story__operation-grid">
          <div class="rct-story__operation-copy report-example__copy">
            <p>{{ example.role }}</p>
            <h3>{{ example.title }}</h3>
          </div>
          <a
            class="rct-story__operation-code report-example__preview"
            :href="`${assetBase}${example.name}.${example.format}`"
            :aria-label="`${example.action} — ${example.title}`"
            :aria-describedby="`${example.name}-preview`"
          >
            <img :id="`${example.name}-preview`" :src="`${assetBase}${example.name}.webp`" :alt="example.alt"
              :width="example.width" :height="example.height" loading="lazy" decoding="async" />
          </a>
        </div>
      </li>
    </ul>
  </section>
</template>
