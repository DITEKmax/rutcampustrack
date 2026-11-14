<script setup lang="ts">
import { computed } from 'vue'
import { attendance, lessonsForDate, selectedDemoLesson, today } from './fixtures'
import { demoDate, emptyDate, lessonId, type DemoSetupScenario, type DemoSnapshot } from './model'
import { demoActivityCounts } from './role-fixtures'

const props = defineProps<{ snapshot: DemoSnapshot }>()
const emit = defineEmits<{
  'select-date': [date: string]
  'select-lesson': [lesson: string]
  'choose-scenario': [scenario: DemoSetupScenario]
  'show-phone': []
}>()

const week = [
  { date: demoDate, label: 'Пн, 9 ноября' },
  { date: '2026-11-10', label: 'Вт, 10 ноября' },
  { date: '2026-11-11', label: 'Ср, 11 ноября' },
  { date: '2026-11-12', label: 'Чт, 12 ноября' },
  { date: '2026-11-13', label: 'Пт, 13 ноября' },
  { date: emptyDate, label: 'Сб, 14 ноября' },
] as const
const options = [
  { id: 'standard', label: 'Обычный день' },
  { id: 'cancelled', label: 'Отменена' },
  { id: 'geo-unavailable', label: 'Геолокация недоступна' },
  { id: 'empty-day', label: 'Пустой день' },
] as const
const lessons = computed(() => lessonsForDate(props.snapshot))
const selected = computed(() => selectedDemoLesson(props.snapshot))
const projection = computed(() => {
  const current = today(props.snapshot).lessons.find(item => item.schedule.id === selected.value?.id)
  const scenario = props.snapshot.scenario
  const geoUnavailable = (scenario === 'geo-unavailable' || scenario === 'pending') &&
    selected.value?.id === lessonId
  return JSON.stringify({
    date: props.snapshot.date,
    lesson: current ? {
      id: current.schedule.id,
      number: current.schedule.lessonNumber,
      status: current.schedule.status,
      ...(geoUnavailable ? { geo: { kind: 'UNAVAILABLE', reason: 'POSITION_UNAVAILABLE' } } : {}),
      attendance: current.attendance?.status ?? null,
      outcome: current.request ? 'PENDING_CONFIRMATION' : null,
      request: current.request ? { status: current.request.status, origin: current.request.origin } : null,
    } : null,
    activity: demoActivityCounts(props.snapshot),
    held: attendance(props.snapshot).metrics.held,
  }, null, 2)
})
const dateLabel = computed(() => week.find(item => item.date === props.snapshot.date)?.label ?? props.snapshot.date)
</script>

<template>
  <div class="demo-week" aria-labelledby="demo-week-title">
    <h3 id="demo-week-title">Учебная неделя</h3>
    <div class="demo-week__selectors">
      <label>День
        <select :value="snapshot.date" @change="emit('select-date', ($event.target as HTMLSelectElement).value)">
          <option v-if="!week.some(item => item.date === snapshot.date)" :value="snapshot.date">{{ dateLabel }}</option>
          <option v-for="day in week" :key="day.date" :value="day.date">{{ day.label }}</option>
        </select>
      </label>
      <label>Пара
        <select :disabled="!lessons.length" :value="selected?.id ?? ''" @change="emit('select-lesson', ($event.target as HTMLSelectElement).value)">
          <option v-if="!lessons.length" value="">Пар нет</option>
          <option v-for="item in lessons" :key="item.id" :value="item.id">№{{ item.lessonNumber }} · {{ item.subject.name }}</option>
        </select>
      </label>
    </div>
    <fieldset>
      <legend>Сценарий</legend>
      <div class="demo-week__options">
        <button v-for="option in options" :key="option.id" type="button"
          :aria-pressed="snapshot.scenario === option.id || (snapshot.scenario === 'pending' && option.id === 'geo-unavailable')"
          @click="emit('choose-scenario', option.id)">{{ option.label }}</button>
      </div>
    </fieldset>
    <p v-if="snapshot.scenario === 'geo-unavailable'">Нажми «Отметиться» в телефоне, чтобы отправить учебную заявку.</p>
    <p v-if="snapshot.scenario === 'pending'" role="status">На подтверждении у старосты</p>
    <button type="button" class="text-action" @click="emit('show-phone')">К экрану телефона ↓</button>
    <details>
      <summary>Состояние · JSON</summary>
      <pre aria-label="Локальное состояние">{{ projection }}</pre>
    </details>
  </div>
</template>

<style scoped>
.demo-week { display: grid; gap: .75rem; width: min(100%, 25rem); margin-block-start: 1.5rem; }
.demo-week h3, .demo-week p { margin: 0; }
.demo-week p, .demo-week label, .demo-week legend, .demo-week summary { color: var(--landing-muted); font-size: .83rem; }
.demo-week label { min-inline-size: 0; display: grid; gap: .25rem; }
.demo-week__selectors { display: grid; grid-template-columns: minmax(0, .85fr) minmax(0, 1.15fr); gap: .5rem; }
.demo-week fieldset { min-inline-size: 0; border: 0; padding: 0; margin: 0; }
.demo-week legend { padding: 0; margin-block-end: .35rem; }
.demo-week__options { display: flex; flex-wrap: wrap; gap: .35rem; }
.demo-week select, .demo-week__options button {
  min-height: 2.25rem; min-inline-size: 0; max-inline-size: 100%;
  padding: .35rem .55rem; border: 1px solid var(--landing-border); border-radius: .45rem;
  color: var(--landing-text); background: var(--rct-color-bg-surface, #21182a); font: inherit; font-size: .82rem;
}
.demo-week__options button[aria-pressed='true'] { border-color: var(--landing-accent); }
.demo-week button, .demo-week summary { justify-self: start; cursor: pointer; }
.demo-week pre {
  overflow: auto; max-inline-size: 100%; border: 1px solid var(--landing-border);
  padding: .65rem; margin-block: .4rem 0; border-radius: .45rem; font-size: .72rem;
}
</style>
