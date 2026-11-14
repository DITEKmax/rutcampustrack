<script setup lang="ts">
import { computed, ref } from 'vue'
import { displayPercent } from '../../../mobile-core/src/features/statistics/statistics-view-model'
import LocalScenarioPanel from './LocalScenarioPanel.vue'

const mark = ref(true)
const roster = [
  { id: 1, name: 'Анна', present: 66, held: 100 },
  { id: 2, name: 'Артём', present: 2, held: 3 },
  { id: 3, name: 'Вера', present: 4, held: 6 },
  { id: 4, name: 'Кирилл', present: 1, held: 2 },
  { id: 5, name: 'Мария', present: 0, held: 0 },
] as const
const ranking = computed(() => {
  const participants = roster.map(row => ({ ...row, present: row.present + (row.id === 1 && mark.value ? 1 : 0) }))
  const ranked = participants.filter(row => row.held > 0).sort((a, b) =>
    b.present * a.held - a.present * b.held || a.id - b.id,
  )
  return [...ranked, ...participants.filter(row => row.held === 0)].map(row => ({
    ...row,
    position: row.held > 0 ? 1 + ranked.filter(peer => peer.present * row.held > row.present * peer.held).length : null,
    percentage: row.held > 0 ? Math.round(row.present * 10000 / row.held) / 100 : null,
  }))
})
</script>

<template>
  <LocalScenarioPanel id="demo-student-ranking" title="Место по точной доле"
    :json="ranking" :changed="!mark" @reset="mark = true">
    <p>Учебный пример с разными знаменателями. 2/3 и 4/6 делят место, а 67/100 выше, хотя экран показывает 67% для всех трёх.</p>
    <div class="local-scene-controls">
      <button type="button" :aria-pressed="mark" @click="mark = !mark">Анна · отметка {{ mark ? '«Был»' : '«Не был»' }}</button>
    </div>
    <table class="rank-table">
      <thead><tr><th scope="col">Студент</th><th scope="col">Доля</th><th scope="col">На экране</th><th scope="col">Место</th></tr></thead>
      <tbody>
        <tr v-for="row in ranking" :key="row.id">
          <th scope="row">{{ row.name }}</th><td>{{ row.present }}/{{ row.held }}</td>
          <td>{{ displayPercent(row.percentage) }}</td><td>{{ row.position ?? '—' }}</td>
        </tr>
      </tbody>
    </table>
    <p role="status">Место Анны · {{ ranking.find(row => row.id === 1)?.position ?? '—' }}</p>
  </LocalScenarioPanel>
</template>

<style scoped>
.rank-table { width: 100%; border-collapse: collapse; color: var(--story-text); font-variant-numeric: tabular-nums; }
.rank-table th, .rank-table td { border-block-end: 1px solid var(--story-line); padding: .3rem .35rem; text-align: start; }
.rank-table thead th { color: var(--story-muted); font-weight: 500; font-size: .76rem; }
</style>
