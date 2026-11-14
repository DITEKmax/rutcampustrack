<script setup lang="ts">
import { computed, ref } from 'vue'
import { displayPercent } from '../../../mobile-core/src/features/statistics/statistics-view-model'
import LocalScenarioPanel from './LocalScenarioPanel.vue'

type LessonState = 'CLOSED' | 'ACTIVE' | 'CANCELLED' | 'TRANSFERRED_OUT'
type Mark = 'PRESENT' | 'EXCUSED' | 'ABSENT'
const options = [
  { id: 'present', label: 'Завершена · был', status: 'CLOSED', mark: 'PRESENT' },
  { id: 'excused', label: 'Завершена · уважительная', status: 'CLOSED', mark: 'EXCUSED' },
  { id: 'absent', label: 'Завершена · не был', status: 'CLOSED', mark: 'ABSENT' },
  { id: 'missing', label: 'Завершена · без записи', status: 'CLOSED', mark: null },
  { id: 'active', label: 'Идёт · был', status: 'ACTIVE', mark: 'PRESENT' },
  { id: 'cancelled', label: 'Отменена', status: 'CANCELLED', mark: null },
  { id: 'transferred', label: 'Исходная пара переноса', status: 'TRANSFERRED_OUT', mark: null },
] as const satisfies readonly { id: string; label: string; status: LessonState; mark: Mark | null }[]
type Choice = (typeof options)[number]['id']
const initial: Choice[] = ['present', 'excused', 'missing', 'active']
const choices = ref([...initial])
const changed = computed(() => choices.value.some((choice, index) => choice !== initial[index]))
const lessons = computed(() => choices.value.map((choice, index) => {
  const selected = options.find(option => option.id === choice)!
  return {
    occurrenceId: 513 + index,
    status: selected.status,
    mark: selected.mark,
    countedAs: selected.status === 'CLOSED' ? selected.mark ?? 'ABSENT' : null,
  }
}))
const projection = computed(() => {
  const closed = lessons.value.filter(lesson => lesson.status === 'CLOSED')
  const H = closed.length
  const present = closed.filter(lesson => lesson.countedAs === 'PRESENT').length
  const excused = closed.filter(lesson => lesson.countedAs === 'EXCUSED').length
  const ratio = (numerator: number) => ({
    numerator,
    denominator: H,
    percent: H === 0 ? null : Math.round(numerator * 10000 / H) / 100,
  })
  return {
    lessons: lessons.value,
    H,
    present: ratio(present),
    presentOrExcused: ratio(present + excused),
    absent: H - present - excused,
  }
})
</script>

<template>
  <LocalScenarioPanel id="demo-attendance-calculator" title="Калькулятор четырёх пар"
    :json="projection" :changed="changed" @reset="choices = [...initial]">
    <p>В H входят завершённые пары. Пустая запись в такой паре считается отсутствием.</p>
    <div class="calculator-choices">
      <label v-for="(_, index) in choices" :key="index">
        Пара {{ index + 1 }}
        <select v-model="choices[index]">
          <option v-for="option in options" :key="option.id" :value="option.id">{{ option.label }}</option>
        </select>
      </label>
    </div>
    <dl class="local-scene-facts" aria-live="polite">
      <div><dt>Был</dt><dd>{{ projection.present.numerator }} / {{ projection.H }} · {{ displayPercent(projection.present.percent) }}</dd></div>
      <div><dt>Был или уважительная</dt><dd>{{ projection.presentOrExcused.numerator }} / {{ projection.H }} · {{ displayPercent(projection.presentOrExcused.percent) }}</dd></div>
    </dl>
  </LocalScenarioPanel>
</template>

<style scoped>
.calculator-choices { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .45rem; }
.calculator-choices label { display: grid; min-inline-size: 0; gap: .25rem; }
@media (max-width: 25rem) {
  .calculator-choices { grid-template-columns: minmax(0, 1fr); }
}
</style>
