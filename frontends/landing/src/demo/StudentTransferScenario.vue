<script setup lang="ts">
import { computed, ref } from 'vue'
import LocalScenarioPanel from './LocalScenarioPanel.vue'

const initialDate = '2026-11-17'
const transferDate = ref(initialDate)
const dates = [
  { date: '2026-11-16', label: '16 ноября', lessons: { 41: '731', 42: '751' } },
  { date: '2026-11-18', label: '18 ноября', lessons: { 41: '736', 42: '756' } },
] as const
const groups = [
  { id: 41, members: ['Артём', 'Вера'] },
  { id: 42, members: ['Илья', 'Лена'] },
] as const
const rows = computed(() => dates.flatMap(day => groups.map(group => {
  const included = (day.date < transferDate.value) === (group.id === 41)
  const roster = included ? [...group.members, 'Анна'] : [...group.members]
  return {
    date: day.date,
    label: day.label,
    groupId: group.id,
    lessonId: day.lessons[group.id],
    status: 'CLOSED',
    roster,
    denominator: roster.length,
  }
})))
const totals = computed(() => groups.map(group => ({
  groupId: group.id,
  denominator: rows.value.filter(row => row.groupId === group.id)
    .reduce((total, row) => total + row.denominator, 0),
})))
const state = computed(() => ({
  effectiveDate: transferDate.value,
  timeZone: 'Europe/Moscow',
  membership: [
    { groupId: 41, joinedAt: '2026-09-01', leftAt: transferDate.value },
    { groupId: 42, joinedAt: transferDate.value, leftAt: null },
  ],
  journal: rows.value.map(({ label: _label, ...row }) => row),
  totals: totals.value,
}))
</script>

<template>
  <LocalScenarioPanel id="demo-student-transfer" title="Состав групп после перевода"
    :json="state" :changed="transferDate !== initialDate" @reset="transferDate = initialDate">
    <p>Выбранная дата моделирует день выполнения операции по Москве. Две пары каждой группы завершены.</p>
    <label class="local-scene-controls">День перевода
      <select v-model="transferDate">
        <option value="2026-11-16">16 ноября</option>
        <option value="2026-11-17">17 ноября</option>
        <option value="2026-11-18">18 ноября</option>
      </select>
    </label>
    <dl class="local-scene-facts">
      <div v-for="row in rows" :key="row.lessonId">
        <dt>{{ row.label }} · группа {{ row.groupId }} · пара {{ row.lessonId }}</dt>
        <dd>{{ row.roster.join(', ') }}</dd>
        <dd>{{ row.denominator }} в знаменателе</dd>
      </div>
    </dl>
    <p role="status">Всего мест в знаменателе · {{ totals.map(row => row.groupId + ': ' + row.denominator).join(' · ') }}</p>
  </LocalScenarioPanel>
</template>
