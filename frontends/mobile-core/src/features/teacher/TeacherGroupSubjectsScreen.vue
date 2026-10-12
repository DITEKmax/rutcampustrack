<script setup lang="ts">
import { computed } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import type { TeacherStatsSubjectOption } from './teacher-client';
import './teacher-group-subjects-screen.pcss';
const props = withDefaults(
  defineProps<{
    groupId: number;
    groupName: string;
    subjectOptions: readonly TeacherStatsSubjectOption[];
    showBack?: boolean;
  }>(),
  { showBack: true },
);
const emit = defineEmits<{ back: []; 'open-subject': [subject: TeacherStatsSubjectOption] }>();
const subjects = computed(() =>
  props.subjectOptions
    .filter((option) => option.groupId === props.groupId)
    .sort((a, b) => a.subjectName.localeCompare(b.subjectName, 'ru')),
);
</script>
<template>
  <main
    class="teacher-subjects"
    aria-labelledby="teacher-subjects-title"
  >
    <header class="teacher-subjects__header">
      <button
        v-if="showBack"
        type="button"
        aria-label="Назад"
        @click="emit('back')"
      >
        <MobileIcon name="back" /> </button
      ><h1 id="teacher-subjects-title">
        {{ groupName }}
      </h1>
    </header>
    <h2>Мои предметы</h2>
    <ul v-if="subjects.length">
      <li
        v-for="subject in subjects"
        :key="subject.subjectId"
      >
        <button
          type="button"
          @click="emit('open-subject', subject)"
        >
          {{ subject.subjectName }}<MobileIcon name="chevron-down" />
        </button>
      </li>
    </ul>
    <p
      v-else
      role="status"
    >
      В этой группе у тебя сейчас нет назначенных предметов.
    </p>
  </main>
</template>
