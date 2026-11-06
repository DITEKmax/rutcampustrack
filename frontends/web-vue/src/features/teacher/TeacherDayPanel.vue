<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import WebDateField from '../../shared/WebDateField.vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import type {
  TeacherDayLesson,
  TeacherDayResponse,
  TeacherSemester,
} from '../../../../mobile-core/src/features/teacher/teacher-client';
import { teacherLessonTypeLabel } from '../../../../mobile-core/src/features/teacher/teacher-ui-format';
import { shortDate, today } from './teacher-state';
import TeacherNotice from './TeacherNotice.vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import previousIcon from '../../../../mobile-core/src/assets/headman-homework-previous.svg';
import nextIcon from '../../../../mobile-core/src/assets/headman-homework-next.svg';
const props = defineProps<{
  semester: TeacherSemester | null;
  data: TeacherDayResponse | null;
  loading: boolean;
  error: string;
}>();
const model = defineModel<string>({ required: true });
defineEmits<{ retry: [] }>();
function dayStatus(lesson: TeacherDayLesson): keyof typeof statusLabels {
  if (lesson.cancelled || lesson.status === 'CANCELLED') return 'cancelled';
  if (lesson.oneOff) return 'one-off';
  if (lesson.moved) return 'moved';
  if (lesson.status === 'ACTIVE') return 'active';
  if (lesson.status === 'CLOSED') return 'closed';
  return 'planned';
}
const statusLabels = {
  cancelled: 'Отменена',
  'one-off': 'Разовая',
  moved: 'Перенесена',
  active: 'Сейчас',
  closed: 'Завершена',
  planned: 'По расписанию',
};
const lessons = computed(() => props.data?.lessons.map(lesson => ({ lesson, status: dayStatus(lesson) })) ?? []);
function step(direction: number) {
  const date = new Date(`${model.value}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + direction);
  const next = date.toISOString().slice(0, 10);
  if (props.semester && next >= props.semester.dateFrom && next <= props.semester.dateTo)
    model.value = next;
}
function reset() {
  if (props.semester)
    model.value =
      today() < props.semester.dateFrom
        ? props.semester.dateFrom
        : today() > props.semester.dateTo
          ? props.semester.dateTo
          : today();
}
</script>
<template>
  <div class="teacher-day-pager">
    <button
      class="web-button web-icon-button"
      aria-label="Предыдущий день"
      :disabled="loading || !semester || model <= semester.dateFrom"
      @click="step(-1)"
    >
      <SvgIcon :src="previousIcon" />
    </button>
    <WebDateField
      v-model="model"
      label="Дата пар"
      :trigger-label="shortDate(model)"
      :min="semester?.dateFrom"
      :max="semester?.dateTo"
      :today="today()"
      :disabled="loading || !semester"
      required
    />
    <button
      class="web-button web-icon-button"
      aria-label="Следующий день"
      :disabled="loading || !semester || model >= semester.dateTo"
      @click="step(1)"
    >
      <SvgIcon :src="nextIcon" />
    </button>
    <button
      class="web-button web-icon-button"
      aria-label="Сегодня"
      title="Сегодня"
      :disabled="loading || !semester"
      @click="reset"
    >
      <MobileIcon name="retry" />
    </button>
  </div>
  <TeacherNotice
    :loading="loading"
    :error="error"
    :empty="data?.lessons.length === 0 ? 'На этот день пар нет.' : ''"
    @retry="$emit('retry')"
  />
  <ol
    v-if="lessons.length"
    class="teacher-day-list"
  >
    <li
      v-for="{ lesson, status } in lessons"
      :key="lesson.id"
      :class="`teacher-day-${status}`"
    >
      <time>{{ lesson.startsAt?.slice(0, 5) ?? `Пара ${lesson.lessonNumber}`
      }}<template v-if="lesson.endsAt">–{{ lesson.endsAt.slice(0, 5) }}</template></time>
      <div class="teacher-day-content">
        <RouterLink :to="{ name: 'teacher-lesson', params: { lessonId: lesson.id } }">
          {{ lesson.subjectName }}
        </RouterLink>
        <span>{{ teacherLessonTypeLabel(lesson.lessonType) }} ·
          {{ lesson.room ?? 'Аудитория не указана' }} · {{ lesson.groupName }}</span>
        <span
          class="teacher-day-status"
          :class="{ 'web-sr-only': status === 'active' || status === 'closed' || status === 'planned' }"
        >{{ statusLabels[status] }}<template v-if="status === 'cancelled' && lesson.cancellationReason">
          · {{ lesson.cancellationReason }}
        </template></span>
      </div>
    </li>
  </ol>
  <p
    class="web-sr-only"
    role="status"
  >
    {{ shortDate(model) }}
  </p>
</template>
