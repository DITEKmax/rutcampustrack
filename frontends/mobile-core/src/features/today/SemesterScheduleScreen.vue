<script setup lang="ts">
import { computed } from 'vue'
import type { StudentSemesterSchedule } from '../../api/types'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import './semester-schedule-screen.pcss'
const props = defineProps<{ schedule: StudentSemesterSchedule | null; selectedDate: string; offline: boolean; loading?: boolean; error?: string | null; retryEnabled?: boolean }>()
const emit = defineEmits<{ back: []; retry: []; selectDate: [date: string] }>()
const dates = computed(() => [...new Set(props.schedule?.lessons.map((lesson) => lesson.date) ?? [])].sort())
const lessons = computed(() => props.schedule?.lessons.filter((lesson) => lesson.date === props.selectedDate) ?? [])
function dateLabel(date: string): string { return new Date(`${date}T12:00:00Z`).toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'long' }) }
</script>
<template>
  <main
    class="semester-screen"
    aria-labelledby="semester-title"
  >
    <header class="today-task-header">
      <button
        class="today-back"
        type="button"
        aria-label="Вернуться в Ещё"
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button><h1 id="semester-title">
        Расписание семестра
      </h1>
    </header>
    <p
      v-if="loading"
      role="status"
      aria-busy="true"
    >
      Загружаем расписание семестра…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      severity="error"
      title="Не удалось получить расписание"
      :message="error"
      action-label="Повторить"
      :action-disabled="!retryEnabled"
      @action="emit('retry')"
    />
    <StudentWarningBlock
      v-else-if="!schedule"
      title="Расписание пока недоступно"
      :message="offline ? 'Сохранённого расписания семестра нет. Открой PWA онлайн, чтобы загрузить данные.' : 'Данные расписания семестра пока недоступны.'"
    />
    <template v-else>
      <StudentWarningBlock
        v-if="offline"
        title="Сохранённое расписание"
        message="Ты офлайн. Показаны последние сохранённые данные семестра."
      />
      <h2>{{ schedule.semester.name }}</h2>
      <label v-if="dates.length">Дата<select
        :value="selectedDate"
        @change="emit('selectDate', ($event.target as HTMLSelectElement).value)"
      ><option
        v-for="date in dates"
        :key="date"
        :value="date"
      >{{ dateLabel(date) }}</option></select></label>
      <p
        v-if="lessons.length === 0"
        role="status"
      >
        На выбранную дату пар нет.
      </p>
      <ol v-else>
        <li
          v-for="lesson in lessons"
          :key="lesson.id"
          :data-cancelled="lesson.status === 'CANCELLED'"
        >
          <p>{{ lesson.startsAt.slice(0, 5) }}–{{ lesson.endsAt.slice(0, 5) }}</p><h3>{{ lesson.subject.name }}</h3><p>{{ { LECTURE: 'Лекция', PRACTICE: 'Практика', LAB: 'Лабораторная' }[lesson.subject.type] }}</p><p
            v-if="lesson.status === 'CANCELLED'"
            class="semester-screen__cancelled"
          >
            Пара отменена
          </p>
        </li>
      </ol>
    </template>
  </main>
</template>
