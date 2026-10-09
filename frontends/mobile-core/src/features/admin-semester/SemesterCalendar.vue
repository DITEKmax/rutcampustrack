<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import './semester-calendar.pcss'
const props = withDefaults(defineProps<{ modelValue: string; label: string; disabled?: boolean; showToday?: boolean }>(), { showToday: true })
const emit = defineEmits<{ 'update:modelValue': [date: string] }>()
function iso(date: Date): string { return date.toISOString().slice(0, 10) }
const today = iso(new Date())
const month = ref((props.modelValue || today).slice(0, 7))
const calendar = ref<HTMLElement | null>(null)
watch(() => props.label, () => { month.value = (props.modelValue || today).slice(0, 7) })
const monthLabel = computed(() => new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(month.value + '-01T00:00:00Z')))
const days = computed(() => {
  const first = new Date(month.value + '-01T00:00:00Z')
  const offset = (first.getUTCDay() + 6) % 7
  return Array.from({ length: 42 }, (_, index) => { const day = new Date(first); day.setUTCDate(index - offset + 1); return iso(day) })
})
function stepMonth(step: number): void { const date = new Date(month.value + '-01T00:00:00Z'); date.setUTCMonth(date.getUTCMonth() + step); if (date.getUTCFullYear() >= 1 && date.getUTCFullYear() <= 9999) month.value = iso(date).slice(0, 7) }
function validDay(day: string): boolean { return /^\d{4}-\d{2}-\d{2}$/.test(day) && day >= '0001-01-01' && day <= '9999-12-31' }
function accessibleDate(day: string): string { if (!validDay(day)) return 'Дата недоступна'; return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(day + 'T00:00:00Z')) }
async function move(event: KeyboardEvent, day: string): Promise<void> {
  if (props.disabled) return
  const delta: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }
  if (!(event.key in delta) && event.key !== 'Home' && event.key !== 'End') return
  event.preventDefault()
  const date = new Date(day + 'T00:00:00Z'), weekday = (date.getUTCDay() + 6) % 7
  date.setUTCDate(date.getUTCDate() + (event.key === 'Home' ? -weekday : event.key === 'End' ? 6 - weekday : delta[event.key]!))
  const target = iso(date); if (!validDay(target)) return; month.value = target.slice(0, 7)
  await nextTick(); calendar.value?.querySelector<HTMLButtonElement>(`[data-date="${target}"]`)?.focus()
}
</script>
<template>
  <section
    ref="calendar"
    class="semester-calendar"
    :aria-label="label + ': выбор даты'"
  >
    <div class="semester-calendar__pager">
      <button
        type="button"
        :disabled="disabled"
        aria-label="Предыдущий месяц"
        @click="stepMonth(-1)"
      >
        <MobileIcon name="back" />
      </button><strong>{{ monthLabel }}</strong><button
        type="button"
        :disabled="disabled"
        aria-label="Следующий месяц"
        @click="stepMonth(1)"
      >
        <MobileIcon
          name="back"
          class="semester-calendar__next"
        />
      </button>
    </div>
    <p
      v-if="showToday"
      class="semester-calendar__today"
    >
      Сегодня — {{ accessibleDate(today) }}
    </p>
    <div class="semester-calendar__grid">
      <span
        v-for="day in ['ПН', 'ВТ', 'СР', 'ЧТ', 'ПТ', 'СБ', 'ВС']"
        :key="day"
        aria-hidden="true"
      >{{ day }}</span><button
        v-for="day in days"
        :key="day"
        type="button"
        :disabled="disabled || !validDay(day)"
        :data-date="day"
        :data-outside="day.slice(0, 7) !== month"
        :aria-label="accessibleDate(day)"
        :aria-pressed="day === modelValue"
        :aria-current="day === today ? 'date' : undefined"
        @keydown="move($event, day)"
        @click="emit('update:modelValue', day)"
      >
        {{ Number(day.slice(-2)) }}
      </button>
    </div>
  </section>
</template>
