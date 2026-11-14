<script setup lang="ts">
import { computed, ref } from 'vue'
import { journalThreshold, journalThresholdResult } from '../../../mobile-core/src/features/teacher/teacher-journal-threshold'
import { journalMetricPercent } from '../../../mobile-core/src/features/teacher/teacher-journal-model'
import LocalScenarioPanel from './LocalScenarioPanel.vue'

const initial = '66,7'
const input = ref(initial)
const empty = ref(false)
const threshold = computed(() => journalThreshold(input.value))
const metrics = computed(() => [
  { id: 'present', label: 'Был', metric: empty.value ? { numerator: 0, denominator: 0, percent: 0 } : { numerator: 2, denominator: 3, percent: 66.67 } },
  { id: 'presentOrExcused', label: 'Был или уважительная', metric: empty.value ? { numerator: 0, denominator: 0, percent: 0 } : { numerator: 3, denominator: 3, percent: 100 } },
].map(row => ({ ...row, result: journalThresholdResult(row.metric, threshold.value) })))
const labels = { meets: 'Порог пройден', below: 'Ниже порога' }
function reset() {
  input.value = initial
  empty.value = false
}
</script>

<template>
  <LocalScenarioPanel id="demo-journal-threshold" title="Порог и округление"
    :json="{ threshold, metrics }" :changed="input !== initial || empty" @reset="reset">
    <p>Сервер передал 66,67%. В журнале видно 66,7%. Сравнение использует 66,67%, поэтому порог 66,7% ещё не пройден.</p>
    <div class="local-scene-controls">
      <label for="demo-threshold-value">Порог, %</label>
      <input id="demo-threshold-value" v-model="input" type="text" inputmode="decimal" maxlength="12" :aria-invalid="threshold.state === 'invalid'" />
      <label><input v-model="empty" type="checkbox" /> Нет закрытых пар</label>
    </div>
    <p v-if="threshold.state === 'invalid'" role="status">Введи число от 0 до 100.</p>
    <dl class="local-scene-facts" aria-live="polite">
      <div v-for="row in metrics" :key="row.id" :data-result="row.result">
        <dt>{{ row.label }}</dt>
        <dd>{{ journalMetricPercent(row.metric) }}</dd>
        <dd>{{ row.result ? labels[row.result] : 'Без подсветки' }}</dd>
      </div>
    </dl>
  </LocalScenarioPanel>
</template>

<style scoped>
#demo-threshold-value {
  width: 5rem; padding: .35rem .5rem; border-radius: .35rem; border: 1px solid var(--story-line);
  background: var(--landing-bg); color: var(--story-text); font: inherit;
}
#demo-threshold-value:focus-visible { outline: 2px solid var(--landing-accent); outline-offset: 2px; }
[data-result='meets'] { background: color-mix(in srgb, var(--rct-color-status-success, #62b58a) 16%, transparent); }
[data-result='below'] { background: color-mix(in srgb, var(--rct-color-status-error, #db7b91) 14%, transparent); }
</style>
