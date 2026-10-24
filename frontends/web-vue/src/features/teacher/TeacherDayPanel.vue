<script setup lang="ts">
import { RouterLink } from 'vue-router';
import type {
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
    <label class="web-field"><span class="web-sr-only">Дата пар</span><input
      v-model="model"
      type="date"
      :min="semester?.dateFrom"
      :max="semester?.dateTo"
      :disabled="loading"
    ></label>
    <button
      class="web-button web-icon-button"
      aria-label="Следующий день"
      :disabled="loading || !semester || model >= semester.dateTo"
      @click="step(1)"
    >
      <SvgIcon :src="nextIcon" />
    </button>
    <button
      class="web-button"
      @click="reset"
    >
      Сегодня
    </button>
  </div>
  <TeacherNotice
    :loading="loading"
    :error="error"
    :empty="data?.lessons.length === 0 ? 'На этот день пар нет.' : ''"
    @retry="$emit('retry')"
  />
  <ol
    v-if="data?.lessons.length"
    class="teacher-day-list"
  >
    <li
      v-for="lesson in data.lessons"
      :key="lesson.id"
      :class="{
        'teacher-day-cancelled': lesson.cancelled,
        'teacher-day-active': lesson.status === 'ACTIVE',
      }"
    >
      <time>{{ lesson.startsAt?.slice(0, 5) ?? `Пара ${lesson.lessonNumber}`
      }}<template v-if="lesson.endsAt">–{{ lesson.endsAt.slice(0, 5) }}</template></time>
      <div class="teacher-day-content">
        <RouterLink :to="{ name: 'teacher-lesson', params: { lessonId: lesson.id } }">
          {{
            lesson.subjectName
          }}
        </RouterLink><span>{{ teacherLessonTypeLabel(lesson.lessonType) }} ·
          {{ lesson.room ?? 'Аудитория не указана' }} · {{ lesson.groupName }}</span><span v-if="lesson.cancelled">Отменена<template v-if="lesson.cancellationReason">
          · {{ lesson.cancellationReason }}</template></span><span v-else-if="lesson.moved">Перенесена</span><span v-else-if="lesson.oneOff">Разовая</span><span v-else-if="lesson.status === 'ACTIVE'">Сейчас</span><span v-else>{{ lesson.status === 'CLOSED' ? 'Завершена' : 'По расписанию' }}</span>
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
