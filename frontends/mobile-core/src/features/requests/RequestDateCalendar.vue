<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';

const props = defineProps<{
  modelValue: string;
  min: string;
  max: string;
  label: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{ 'update:modelValue': [date: string]; close: [] }>();
const calendar = ref<HTMLElement | null>(null);
function clamp(day: string): string {
  return day < props.min ? props.min : day > props.max ? props.max : day;
}
const focusedDate = ref(clamp(props.modelValue || props.min));
const month = ref(focusedDate.value.slice(0, 7));
watch(
  () => [props.modelValue, props.min, props.max] as const,
  () => {
    focusedDate.value = clamp(props.modelValue || props.min);
    month.value = focusedDate.value.slice(0, 7);
  },
);
const monthLabel = computed(() =>
  new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(`${month.value}-01T12:00:00Z`),
  ),
);
const days = computed(() => {
  const first = new Date(`${month.value}-01T12:00:00Z`);
  const offset = (first.getUTCDay() + 6) % 7;
  const count = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate();
  return [
    ...Array.from<null>({ length: offset }).fill(null),
    ...Array.from(
      { length: count },
      (_, index) => `${month.value}-${String(index + 1).padStart(2, '0')}`,
    ),
  ];
});
function available(day: string): boolean {
  return day >= props.min && day <= props.max;
}
function dateLabel(day: string): string {
  return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long', timeZone: 'UTC' }).format(
    new Date(`${day}T12:00:00Z`),
  );
}
function canStep(step: number): boolean {
  return (
    !props.disabled &&
    (step < 0 ? month.value > props.min.slice(0, 7) : month.value < props.max.slice(0, 7))
  );
}
function stepMonth(step: number): void {
  if (!canStep(step)) return;
  const date = new Date(`${month.value}-01T12:00:00Z`);
  date.setUTCMonth(date.getUTCMonth() + step);
  month.value = date.toISOString().slice(0, 7);
  focusedDate.value = clamp(`${month.value}-01`);
}
async function focusDay(day: string): Promise<void> {
  focusedDate.value = clamp(day);
  month.value = focusedDate.value.slice(0, 7);
  await nextTick();
  calendar.value?.querySelector?.<HTMLButtonElement>(`[data-date="${focusedDate.value}"]`)?.focus();
}
async function move(event: KeyboardEvent, day: string): Promise<void> {
  if (props.disabled) return;
  const delta: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };
  if (!(event.key in delta) && !['Home', 'End', 'PageUp', 'PageDown'].includes(event.key)) return;
  event.preventDefault();
  const date = new Date(`${day}T12:00:00Z`);
  if (event.key === 'PageUp' || event.key === 'PageDown') {
    const originalDay = date.getUTCDate();
    date.setUTCDate(1);
    date.setUTCMonth(date.getUTCMonth() + (event.key === 'PageUp' ? -1 : 1));
    const lastDay = new Date(
      Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0),
    ).getUTCDate();
    date.setUTCDate(Math.min(originalDay, lastDay));
  } else {
    const weekday = (date.getUTCDay() + 6) % 7;
    date.setUTCDate(
      date.getUTCDate() +
        (event.key === 'Home' ? -weekday : event.key === 'End' ? 6 - weekday : delta[event.key]!),
    );
  }
  await focusDay(date.toISOString().slice(0, 10));
}
function choose(day: string): void {
  if (!props.disabled && available(day)) emit('update:modelValue', day);
}
function close(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return;
  event.preventDefault();
  event.stopPropagation();
  emit('close');
}
onMounted(() => {
  void focusDay(focusedDate.value);
});
</script>
<template>
  <section
    ref="calendar"
    class="request-date-calendar"
    :aria-label="label + ': выбор даты'"
    @keydown="close"
  >
    <header class="request-date-calendar__heading">
      <h2>{{ label }}</h2>
      <button
        class="request-date-calendar__close"
        type="button"
        aria-label="Закрыть календарь"
        @click="emit('close')"
      >
        <MobileIcon name="close" />
      </button>
    </header>
    <div class="request-date-calendar__pager">
      <button
        type="button"
        aria-label="Предыдущий месяц"
        :disabled="!canStep(-1)"
        @click="stepMonth(-1)"
      >
        <MobileIcon name="back" />
      </button>
      <strong aria-live="polite">{{ monthLabel }}</strong>
      <button
        type="button"
        aria-label="Следующий месяц"
        :disabled="!canStep(1)"
        @click="stepMonth(1)"
      >
        <MobileIcon
          name="back"
          class="request-date-calendar__next"
        />
      </button>
    </div>
    <div class="request-date-calendar__grid">
      <span
        v-for="weekday in ['ПН', 'ВТ', 'СР', 'ЧТ', 'ПТ', 'СБ', 'ВС']"
        :key="weekday"
        aria-hidden="true"
        >{{ weekday }}</span
      >
      <template
        v-for="(day, index) in days"
        :key="day ?? `blank-${index}`"
      >
        <button
          v-if="day"
          type="button"
          :data-date="day"
          :aria-label="dateLabel(day)"
          :aria-pressed="day === modelValue"
          :tabindex="day === focusedDate ? 0 : -1"
          :disabled="disabled || !available(day)"
          @focus="focusedDate = day"
          @keydown="move($event, day)"
          @click="choose(day)"
        >
          {{ Number(day.slice(-2)) }}
        </button>
        <span
          v-else
          aria-hidden="true"
        />
      </template>
    </div>
  </section>
</template>
<style src="./request-date-calendar.pcss"></style>
