<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import SemesterCalendar from '../admin-semester/SemesterCalendar.vue';
import './teacher-home-r3.pcss';

const props = defineProps<{ selectedDate: string; returnFocus: HTMLElement | null }>();
const emit = defineEmits<{ select: [date: string]; close: [] }>();
const dialog = ref<HTMLElement | null>(null);
onMounted(async () => {
  await nextTick();
  const selected = dialog.value?.querySelector<HTMLButtonElement>('[aria-pressed="true"]');
  (selected ?? dialog.value?.querySelector<HTMLButtonElement>('button'))?.focus();
});
onBeforeUnmount(() => {
  props.returnFocus?.focus();
});
function keyboard(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault();
    event.stopPropagation();
    emit('close');
    return;
  }
  if (event.key !== 'Tab') return;
  const buttons = [
    ...(dialog.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? []),
  ];
  const first = buttons[0],
    last = buttons.at(-1);
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}
</script>
<template>
  <div
    class="teacher-day-calendar__backdrop"
    @click.self="emit('close')"
    @keydown="keyboard"
  >
    <section
      id="teacher-date-choice"
      ref="dialog"
      class="teacher-day-calendar"
      role="dialog"
      aria-modal="true"
      aria-labelledby="teacher-day-calendar-title"
    >
      <header class="teacher-day-calendar__heading">
        <h2 id="teacher-day-calendar-title">
          Выбери день
        </h2>
        <button
          class="teacher-screen__icon-button"
          type="button"
          aria-label="Закрыть календарь"
          @click="emit('close')"
        >
          <MobileIcon name="close" />
        </button>
      </header>
      <SemesterCalendar
        :model-value="selectedDate"
        :show-today="false"
        label="Расписание"
        @update:model-value="emit('select', $event)"
      />
    </section>
  </div>
</template>
