<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue';
import MobileIcon from '../../../mobile-core/src/shared/components/MobileIcon.vue';
import { civilDate, monday, shiftDate } from '../../../mobile-core/src/features/attendance/attendance-periods';
import { useFieldPopover } from './use-field-popover';
import './web-fields.pcss';

const props = defineProps<{
  modelValue: string;
  label: string;
  min?: string | undefined;
  max?: string | undefined;
  today?: string | undefined;
  disabled?: boolean;
  required?: boolean;
  triggerLabel?: string;
  allowedDates?: readonly string[] | undefined;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: string]; change: [value: string]; validity: [valid: boolean] }>();
const id = useId();
const root = ref<HTMLElement | null>(null);
const input = ref<HTMLInputElement | null>(null);
const trigger = ref<HTMLButtonElement | null>(null);
const anchor = computed<HTMLElement | null>(() => props.triggerLabel === undefined ? input.value : trigger.value);
const panel = ref<HTMLElement | null>(null);
const draft = ref('');
const issue = ref('');
const focused = ref('');
const month = ref('');
const fullDate = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
const monthDate = new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric', timeZone: 'UTC' });
const popup = useFieldPopover(anchor, panel, focusDay, false, root);
function validDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith('0000')) return false;
  const parsed = civilDate(value);
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}
function parse(value: string) {
  const parts = /^(\d{2})\.(\d{2})\.(\d{4})$/.exec(value);
  const iso = parts ? `${parts[3]}-${parts[2]}-${parts[1]}` : value;
  return validDate(iso) ? iso : '';
}
function display(value: string) {
  return validDate(value) ? value.split('-').reverse().join('.') : value;
}
function allowed(value: string) {
  return validDate(value) && (!props.min || value >= props.min) && (!props.max || value <= props.max) && (props.allowedDates === undefined || props.allowedDates.includes(value));
}
function validation(value: string) {
  if (!value) return props.required ? 'Укажи дату.' : '';
  const iso = parse(value);
  if (!iso) return 'Введи дату в формате ДД.ММ.ГГГГ.';
  if (props.min && iso < props.min) return `Выбери дату не раньше ${display(props.min)}.`;
  if (props.max && iso > props.max) return `Выбери дату не позже ${display(props.max)}.`;
  if (props.allowedDates !== undefined && !props.allowedDates.includes(iso)) return 'Выбери доступную дату в календаре.';
  return '';
}
function validate() {
  issue.value = validation(draft.value);
  input.value?.setCustomValidity(issue.value);
  emit('validity', !issue.value && Boolean(parse(draft.value)));
}
function update(value: string) {
  if (value === props.modelValue) {
    // Restricted calendars also confirm a previously invalid draft of the same date.
    if (props.allowedDates !== undefined) emit('change', value);
    return;
  }
  emit('update:modelValue', value);
  emit('change', value);
}
function typed(event: Event) {
  draft.value = (event.target as HTMLInputElement).value;
  validate();
  if (!issue.value) update(draft.value ? parse(draft.value) : '');
}
function normalize() {
  validate();
  if (!issue.value && draft.value) draft.value = display(parse(draft.value));
}
watch(() => props.modelValue, (value) => {
  draft.value = display(value);
  void nextTick(validate);
}, { immediate: true });
watch(() => [props.min, props.max, props.required, props.allowedDates], validate, { flush: 'post' });
watch(() => props.disabled, (disabled) => { if (disabled) popup.close(false); });
function initialDate() {
  let value = parse(draft.value) || (props.today && validDate(props.today) ? props.today : props.min || props.max || '2000-01-01');
  if (props.min && value < props.min) value = props.min;
  if (props.max && value > props.max) value = props.max;
  if (props.allowedDates !== undefined && !allowed(value)) {
    const available = props.allowedDates.filter(allowed).slice().sort();
    value = available.find(date => date >= value) ?? available.at(-1) ?? value;
  }
  return value;
}
function open() {
  if (props.disabled) return;
  focused.value = initialDate();
  month.value = `${focused.value.slice(0, 7)}-01`;
  popup.open();
}
function focusDay() {
  const day = panel.value?.querySelector<HTMLButtonElement>(`[data-date="${focused.value}"]:not(:disabled)`);
  (day ?? panel.value?.querySelector<HTMLButtonElement>('button:not(:disabled)'))?.focus({ preventScroll: true });
}
const monthLabel = computed(() => month.value ? monthDate.format(civilDate(month.value)) : '');
const weeks = computed(() => {
  if (!month.value) return [];
  const start = monday(month.value);
  return Array.from({ length: 6 }, (_, row) => Array.from({ length: 7 }, (_, column) => {
    const date = shiftDate(start, row * 7 + column);
    return { date, day: civilDate(date).getUTCDate(), outside: date.slice(0, 7) !== month.value.slice(0, 7) };
  }));
});
function monthShift(date: string, amount: number) {
  const parsed = civilDate(date);
  const day = parsed.getUTCDate();
  parsed.setUTCDate(1);
  parsed.setUTCMonth(parsed.getUTCMonth() + amount);
  const end = new Date(parsed.getTime());
  end.setUTCMonth(end.getUTCMonth() + 1, 0);
  parsed.setUTCDate(Math.min(day, end.getUTCDate()));
  return parsed.toISOString().slice(0, 10);
}
function canNavigate(direction: -1 | 1) {
  if (!month.value) return false;
  const next = monthShift(month.value, direction);
  return (!props.min || next.slice(0, 7) >= props.min.slice(0, 7)) && (!props.max || next.slice(0, 7) <= props.max.slice(0, 7));
}
function navigate(direction: -1 | 1) {
  if (!canNavigate(direction)) return;
  month.value = `${monthShift(month.value, direction).slice(0, 7)}-01`;
  focused.value = month.value;
  if (props.min && focused.value < props.min) focused.value = props.min;
  if (props.max && focused.value > props.max) focused.value = props.max;
  if (props.allowedDates !== undefined) {
    focused.value = props.allowedDates.filter(date => date.slice(0, 7) === month.value.slice(0, 7) && allowed(date)).slice().sort()[0] ?? '';
  }
  void nextTick(popup.place);
}
function choose(date: string) {
  if (props.disabled || !allowed(date)) return;
  draft.value = display(date);
  validate();
  update(date);
  popup.close();
}
function clear() {
  if (props.disabled || props.required) return;
  draft.value = '';
  validate();
  update('');
  popup.close();
}
function dayKey(event: KeyboardEvent, date: string) {
  let target = '';
  switch (event.key) {
    case 'ArrowLeft': target = shiftDate(date, -1); break;
    case 'ArrowRight': target = shiftDate(date, 1); break;
    case 'ArrowUp': target = shiftDate(date, -7); break;
    case 'ArrowDown': target = shiftDate(date, 7); break;
    case 'Home': target = monday(date); break;
    case 'End': target = shiftDate(monday(date), 6); break;
    case 'PageUp': target = monthShift(date, event.shiftKey ? -12 : -1); break;
    case 'PageDown': target = monthShift(date, event.shiftKey ? 12 : 1); break;
    default: return;
  }
  event.preventDefault();
  if (props.min && target < props.min) target = props.min;
  if (props.max && target > props.max) target = props.max;
  if (!allowed(target) && props.allowedDates !== undefined) {
    const available = props.allowedDates.filter(allowed).slice().sort();
    target = target < date ? available.reverse().find(value => value <= target) ?? '' : available.find(value => value >= target) ?? '';
  }
  if (!target || !allowed(target)) return;
  focused.value = target;
  month.value = `${target.slice(0, 7)}-01`;
  void nextTick(() => { popup.place(); focusDay(); });
}
</script>

<template>
  <div ref="root" class="web-field web-date-field" :class="{ 'is-label-trigger': triggerLabel !== undefined }">
    <label v-if="triggerLabel === undefined" :for="id">{{ label }}</label>
    <div v-if="triggerLabel === undefined" class="web-date-control">
      <input
        :id="id" ref="input" class="web-date-input" type="text" inputmode="numeric" autocomplete="off"
        :value="draft" :disabled="disabled" :required="required" placeholder="ДД.ММ.ГГГГ"
        :aria-invalid="Boolean(issue) || undefined" :aria-describedby="issue ? `${id}-error` : undefined"
        aria-haspopup="dialog" :aria-expanded="popup.expanded.value" :aria-controls="`${id}-calendar`"
        @input="typed" @blur="normalize" @keydown.down.prevent="open"
      >
      <button
        class="web-date-toggle" type="button" :disabled="disabled"
        :aria-label="`Открыть календарь: ${label}`" aria-haspopup="dialog"
        :aria-expanded="popup.expanded.value" :aria-controls="`${id}-calendar`"
        @click="popup.expanded.value ? popup.close() : open()"
      ><MobileIcon name="calendar" /></button>
    </div>
    <button
      v-else :id="id" ref="trigger" class="web-date-label-trigger" type="button" :disabled="disabled"
      :aria-label="`${label}: ${triggerLabel}. Открыть календарь`" aria-haspopup="dialog"
      :aria-expanded="popup.expanded.value" :aria-controls="`${id}-calendar`"
      @click="popup.expanded.value ? popup.close() : open()" @keydown.down.prevent="open"
    ><span>{{ triggerLabel }}</span><MobileIcon name="calendar" /></button>
    <p v-if="issue" :id="`${id}-error`" class="web-error" role="status">{{ issue }}</p>
    <div
      :id="`${id}-calendar`" ref="panel" class="web-field-popover web-date-calendar" popover="manual"
      role="dialog" :aria-label="`Календарь: ${label}`" @toggle="popup.toggled" @focusout="popup.focusout"
    >
      <div class="web-date-calendar-header">
        <button class="web-date-calendar-arrow" type="button" aria-label="Предыдущий месяц" :disabled="!canNavigate(-1)" @click="navigate(-1)"><MobileIcon name="back" /></button>
        <span :id="`${id}-month`" class="web-date-calendar-month" role="status">{{ monthLabel }}</span>
        <button class="web-date-calendar-arrow web-date-calendar-next" type="button" aria-label="Следующий месяц" :disabled="!canNavigate(1)" @click="navigate(1)"><MobileIcon name="back" /></button>
      </div>
      <p v-if="today && validDate(today)" class="web-date-calendar-today">Сегодня — {{ fullDate.format(civilDate(today)) }}</p>
      <!-- Keep the date picker table separate from the homework calendar layout. -->
      <table class="web-date-calendar-grid" role="grid" :aria-labelledby="`${id}-month`">
        <thead><tr><th v-for="day in ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']" :key="day" scope="col">{{ day }}</th></tr></thead>
        <tbody>
          <tr v-for="(week, index) in weeks" :key="index">
            <td v-for="day in week" :key="day.date" :aria-selected="day.date === modelValue">
              <button
                class="web-date-calendar-day" :class="{ 'is-outside': day.outside }" type="button"
                :data-date="day.date" :disabled="!allowed(day.date) || day.outside" :tabindex="day.date === focused ? 0 : -1"
                :aria-label="fullDate.format(civilDate(day.date))" :aria-current="day.date === today ? 'date' : undefined"
                :aria-pressed="day.date === modelValue" @click="choose(day.date)" @keydown="dayKey($event, day.date)"
              ><span>{{ day.day }}</span></button>
            </td>
          </tr>
        </tbody>
      </table>
      <button v-if="!required" class="web-button web-date-calendar-clear" type="button" :disabled="disabled || (!modelValue && !draft)" @click="clear">Очистить дату</button>
    </div>
  </div>
</template>
