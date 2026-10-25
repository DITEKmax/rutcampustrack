<script setup lang="ts">
import { computed } from 'vue';
import type { TeacherSelection } from './teacher-state';
import { teacherLessonTypeLabel } from '../../../../mobile-core/src/features/teacher/teacher-ui-format';
const props = defineProps<{ selection: TeacherSelection; disabled?: boolean; hideContext?: boolean }>();
defineEmits<{ change: [] }>();
const selectedGroup = computed({ get: () => props.selection.groupId.value, set: value => props.selection.setGroup(value) });
const selectedSubject = computed({ get: () => props.selection.subjectId.value, set: value => props.selection.setSubject(value) });
const selectedTypes = computed({ get: () => props.selection.lessonTypes.value, set: value => props.selection.setTypes(value) });
</script>
<template>
  <div class="teacher-filters">
    <template v-if="!hideContext">
      <label class="web-field">Группа
        <select
          v-model="selectedGroup"
          :disabled="disabled || !selection.groups.value.length"
          @change="$emit('change')"
        >
          <option
            v-for="group in selection.groups.value"
            :key="group.id"
            :value="group.id"
          >{{ group.name }}</option>
        </select>
      </label>
      <label class="web-field">Предмет
        <select
          v-model="selectedSubject"
          :disabled="disabled || !selection.subjects.value.length"
          @change="$emit('change')"
        >
          <option
            v-for="subject in selection.subjects.value"
            :key="subject.subjectId"
            :value="subject.subjectId"
          >{{ subject.subjectName }}</option>
        </select>
      </label>
    </template>
    <fieldset
      class="teacher-types"
      :disabled="disabled"
    >
      <legend>Типы занятий</legend>
      <label
        v-for="type in selection.types.value"
        :key="type"
      ><input
         v-model="selectedTypes"
         type="checkbox"
         :value="type"
         @change="$emit('change')"
       >
        {{ teacherLessonTypeLabel(type) }}</label>
    </fieldset>
  </div>
</template>
