<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ReportDownloadFormat, ReportDownloadTicketRequest } from '../../../mobile-core/src/shared/report-download-client'
import LocalScenarioPanel from './LocalScenarioPanel.vue'

const formats = [
  { id: 'docx', label: 'DOCX', extension: 'docx', mime: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', preview: 'Страницы DOCX' },
  { id: 'pdf', label: 'PDF', extension: 'pdf', mime: 'application/pdf', preview: 'Страницы PDF' },
  { id: 'png', label: 'PNG (ZIP)', extension: 'zip', mime: 'application/zip', preview: 'Страницы PNG в архиве' },
  { id: 'html', label: 'HTML', extension: 'html', mime: 'text/html; charset=UTF-8', preview: 'Таблица HTML' },
  { id: 'xlsx', label: 'XLSX', extension: 'xlsx', mime: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', preview: 'Таблица на листе XLSX' },
] as const satisfies readonly { id: ReportDownloadFormat; label: string; extension: string; mime: string; preview: string }[]
type Member = keyof typeof names
type Symbol = '+' | 'н' | 'у'
type Lesson = { id: string; date: string; number: number; week: string; members: Member[]; marks: Partial<Record<Member, Symbol>> }
const names = { 1: 'Анна', 2: 'Артём', 3: 'Вера', 4: 'Лена' } as const
const lessons: readonly Lesson[] = [
  { id: '731', date: '2026-11-16', number: 2, week: '2026-11-16', members: [1, 2, 3], marks: { 1: '+', 2: 'н', 3: 'у' } },
  { id: '736', date: '2026-11-18', number: 2, week: '2026-11-16', members: [2, 3], marks: { 2: '+', 3: '+' } },
  { id: '741', date: '2026-11-23', number: 2, week: '2026-11-23', members: [2, 3, 4], marks: { 2: '+', 3: 'у', 4: 'н' } },
  { id: '742', date: '2026-11-25', number: 2, week: '2026-11-23', members: [2, 3, 4], marks: { 2: 'н', 3: '+', 4: '+' } },
]
const modes = [
  { id: 'week', label: 'Неделя · староста' },
  { id: 'lesson', label: 'Одна пара · преподаватель' },
] as const
const mode = ref<'week' | 'lesson'>('week')
const format = ref<ReportDownloadFormat>('docx')
const week = ref('2026-11-16')
const lesson = ref(lessons[0]!.id)
const selectedLesson = computed(() => lessons.find(row => row.id === lesson.value) ?? lessons[0]!)
const selectedWeeks = computed(() => week.value === 'both' ? ['2026-11-16', '2026-11-23'] : [week.value])
const selectedFormat = computed(() => formats.find(item => item.id === format.value)!)
const paged = computed(() => format.value === 'docx' || format.value === 'pdf' || format.value === 'png')
const selection = computed<ReportDownloadTicketRequest>(() => mode.value === 'week' ? {
  kind: 'HEADMAN_WEEKLY_SELECTED',
  headmanWeeklySelected: { weekStarts: selectedWeeks.value, format: format.value },
} : {
  kind: 'TEACHER_JOURNAL',
  teacherJournal: {
    semesterId: 7, groupId: 41, subjectId: 9, lessonTypes: ['PRACTICE'],
    dateFrom: selectedLesson.value.date, dateTo: selectedLesson.value.date,
    pastOnly: true, lessonId: selectedLesson.value.id, format: format.value,
  },
})
function shortDate(date: string) {
  return date.slice(8) + '.' + date.slice(5, 7)
}
const tables = computed(() => {
  const scopes = mode.value === 'week'
    ? selectedWeeks.value.map(date => ({ label: 'Неделя с ' + shortDate(date), lessons: lessons.filter(item => item.week === date) }))
    : [{ label: 'Пара ' + selectedLesson.value.id, lessons: [selectedLesson.value] }]
  return scopes.map(scope => {
    const members = [...new Set(scope.lessons.flatMap(item => item.members))].sort((a, b) => names[a].localeCompare(names[b], 'ru'))
    return {
      label: scope.label,
      columns: scope.lessons.map(item => ({ lessonId: item.id, date: item.date, number: item.number })),
      rows: members.map(member => {
        const cells = scope.lessons.map(item => item.members.includes(member) ? item.marks[member] ?? null : null)
        const mark = cells[0]
        const summary = mode.value === 'lesson' ? {
          held: 1,
          present: mark === '+' ? 1 : 0,
          presentOrExcused: mark === '+' || mark === 'у' ? 1 : 0,
        } : null
        return { id: member, name: names[member], cells, summary }
      }),
    }
  })
})
const file = computed(() => ({
  name: (mode.value === 'week' ? 'headman-weekly-selected' : 'teacher-journal') + '.' + selectedFormat.value.extension,
  contentType: selectedFormat.value.mime,
  preview: paged.value ? 'paginated' : 'table',
}))
const changed = computed(() => mode.value !== 'week' || week.value !== '2026-11-16' ||
  lesson.value !== lessons[0]!.id || format.value !== 'docx')
function reset() {
  mode.value = 'week'
  week.value = '2026-11-16'
  lesson.value = lessons[0]!.id
  format.value = 'docx'
}
</script>

<template>
  <LocalScenarioPanel id="demo-journal-export" title="Превью выгрузки журнала"
    :json="{ selection, file, tables }" :changed="changed" @reset="reset">
    <p>Локальное превью выборки и формата. Файл не создаётся.</p>
    <div class="local-scene-controls" role="group" aria-label="Вариант выгрузки">
      <button v-for="choice in modes" :key="choice.id" type="button"
        :aria-pressed="mode === choice.id" @click="mode = choice.id">{{ choice.label }}</button>
    </div>
    <div class="local-scene-controls">
      <label v-if="mode === 'week'">Недели
        <select v-model="week">
          <option value="2026-11-16">16–22 ноября</option>
          <option value="2026-11-23">23–29 ноября</option>
          <option value="both">Обе недели</option>
        </select>
      </label>
      <label v-else>Пара
        <select v-model="lesson">
          <option v-for="item in lessons" :key="item.id" :value="item.id">{{ shortDate(item.date) }} · №{{ item.number }}</option>
        </select>
      </label>
      <label>Формат
        <select v-model="format">
          <option v-for="item in formats" :key="item.id" :value="item.id">{{ item.label }}</option>
        </select>
      </label>
    </div>
    <div class="export-preview__meta" role="status">
      <strong>{{ selectedFormat.preview }}</strong>
      <span>{{ file.name }}</span><span>{{ file.contentType }}</span>
    </div>
    <div class="export-preview" :data-layout="file.preview">
      <div v-for="(table, index) in tables" :key="table.label" class="export-preview__table">
        <table>
          <caption>{{ paged ? 'Страница ' + (index + 1) + ' · ' : '' }}{{ table.label }}</caption>
          <thead><tr>
            <th scope="col">Студент</th>
            <th v-for="column in table.columns" :key="column.lessonId" scope="col">{{ shortDate(column.date) }} · №{{ column.number }}</th>
            <th v-if="mode === 'lesson'" scope="col">Был</th>
            <th v-if="mode === 'lesson'" scope="col">Был или уваж.</th>
          </tr></thead>
          <tbody>
            <tr v-for="row in table.rows" :key="row.id">
              <th scope="row">{{ row.name }}</th>
              <td v-for="(cell, cellIndex) in row.cells" :key="cellIndex">{{ cell }}</td>
              <td v-if="row.summary">{{ row.summary.present * 100 }}%</td>
              <td v-if="row.summary">{{ row.summary.presentOrExcused * 100 }}%</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </LocalScenarioPanel>
</template>

<style scoped>
.export-preview__meta { display: grid; overflow-wrap: anywhere; }
.export-preview { display: grid; gap: .65rem; min-inline-size: 0; }
.export-preview__table { overflow-x: auto; min-inline-size: 0; }
.export-preview[data-layout='paginated'] .export-preview__table {
  border: 1px solid var(--story-line); border-radius: .35rem; padding: .55rem;
  background: color-mix(in srgb, var(--story-text) 5%, var(--landing-bg));
}
.export-preview table { width: 100%; border-collapse: collapse; color: var(--story-text); }
.export-preview caption { color: var(--story-text); text-align: start; margin-block-end: .3rem; font-size: .78rem; }
.export-preview th, .export-preview td { border: 1px solid var(--story-line); text-align: start; padding: .24rem .3rem; }
.export-preview th { font-weight: 500; font-size: .75rem; }
.export-preview td { font-variant-numeric: tabular-nums; font-size: .8rem; }
</style>
