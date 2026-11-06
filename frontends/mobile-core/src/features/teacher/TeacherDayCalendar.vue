<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import SemesterCalendar from '../admin-semester/SemesterCalendar.vue';
import './teacher-home-r3.pcss';

const props = defineProps<{
  selectedDate: string;
  minDate: string;
  maxDate: string;
  returnFocus: HTMLElement | null;
  checkingSemester: boolean;
  semesterCheckFailed: boolean;
}>();
const emit = defineEmits<{ select: [date: string]; close: []; retry: [] }>();
const dialog = ref<HTMLElement | null>(null);
const closeButton = ref<HTMLButtonElement | null>(null);
const blocked = computed(() => props.checkingSemester || props.semesterCheckFailed);
onMounted(async () => {
  await nextTick();
  const selected = dialog.value?.querySelector<HTMLButtonElement>(
    '[aria-pressed="true"]:not(:disabled)',
  );
  (selected ?? closeButton.value)?.focus();
});
watch(blocked, (value) => {
  if (value) closeButton.value?.focus();
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
      :aria-busy="checkingSemester"
    >
      <header class="teacher-day-calendar__heading">
        <h2 id="teacher-day-calendar-title">
          Выбери день
        </h2>
        <button
          ref="closeButton"
          class="teacher-screen__icon-button"
          type="button"
          aria-label="Закрыть календарь"
          @click="emit('close')"
        >
          <MobileIcon name="close" />
        </button>
      </header>
      <p
        v-if="checkingSemester"
        class="teacher-screen__state"
        role="status"
      >
        Проверяем даты семестра…
      </p>
      <StudentWarningBlock
        v-else-if="semesterCheckFailed"
        title="Даты семестра недоступны"
        message="Попробуй загрузить даты ещё раз."
        severity="error"
        action-label="Повторить"
        @action="emit('retry')"
      />
      <SemesterCalendar
        :model-value="selectedDate"
        :min-date="minDate"
        :max-date="maxDate"
        :disabled="blocked"
        :show-today="false"
        label="Расписание"
        @update:model-value="emit('select', $event)"
      />
    </section>
  </div>
</template>
