<script setup lang="ts">
import { computed } from 'vue';
import WebSelect from '../../shared/WebSelect.vue';
import type { TeacherSelection } from './teacher-state';
import { teacherLessonTypeLabel } from '../../../../mobile-core/src/features/teacher/teacher-ui-format';
const props = defineProps<{ selection: TeacherSelection; disabled?: boolean; hideContext?: boolean }>();
const emit = defineEmits<{ change: [] }>();
const selectedGroup = computed({ get: () => props.selection.groupId.value, set: value => props.selection.setGroup(value) });
const selectedSubject = computed({ get: () => props.selection.subjectId.value, set: value => props.selection.setSubject(value) });
const selectedTypes = computed({ get: () => props.selection.lessonTypes.value, set: value => props.selection.setTypes(value) });
function toggleType(type: string) {
  if (props.disabled) return;
  selectedTypes.value = selectedTypes.value.includes(type)
    ? selectedTypes.value.filter(value => value !== type)
    : [...selectedTypes.value, type];
  emit('change');
}
</script>
<template>
  <div class="teacher-filters">
    <template v-if="!hideContext">
      <WebSelect
        v-model="selectedGroup"
        label="Группа"
        :options="selection.groups.value.map(group => ({ value: group.id, label: group.name }))"
        :disabled="disabled || !selection.groups.value.length"
        @change="emit('change')"
      />
      <WebSelect
        v-model="selectedSubject"
        label="Предмет"
        :options="selection.subjects.value.map(subject => ({ value: subject.subjectId, label: subject.subjectName }))"
        :disabled="disabled || !selection.subjects.value.length"
        @change="emit('change')"
      />
    </template>
    <div class="teacher-types" role="group" aria-label="Типы занятий">
      <span class="teacher-types-label" aria-hidden="true">Типы занятий</span>
      <div class="teacher-type-buttons">
        <button
          v-for="type in selection.types.value"
          :key="type"
          type="button"
          class="web-button"
          :aria-pressed="selectedTypes.includes(type)"
          :disabled="disabled"
          @click="toggleType(type)"
        >{{ teacherLessonTypeLabel(type) }}</button>
      </div>
    </div>
  </div>
</template>
