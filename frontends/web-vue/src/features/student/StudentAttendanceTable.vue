<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import type { StudentAttendanceLesson } from '../../../../mobile-core/src/api/types';
import { formatLessonDate, lessonTypeLabel, studentMark } from './student-format';
import StudentMarkDetail from './StudentMarkDetail.vue';
const props = defineProps<{ lessons: readonly StudentAttendanceLesson[]; readOnly: boolean }>();
const selected = ref<StudentAttendanceLesson | null>(null),
  detail = ref<InstanceType<typeof StudentMarkDetail> | null>(null);
let trigger: HTMLElement | null = null;
const dates = computed(() =>
  [...new Set(props.lessons.map((l) => l.date))].map((date) => ({
    date,
    count: props.lessons.filter((l) => l.date === date).length,
  })),
);
watch(
  () => props.lessons,
  (lessons) => {
    selected.value = selected.value
      ? (lessons.find((lesson) => lesson.id === selected.value?.id) ?? null)
      : null;
  },
);
async function open(lesson: StudentAttendanceLesson, event: MouseEvent) {
  trigger = event.currentTarget as HTMLElement;
  selected.value = lesson;
  await nextTick();
  detail.value?.$el.focus();
}
function close() {
  selected.value = null;
  trigger?.focus();
}
</script>
<template>
  <div class="web-stack">
    <h2 class="web-sr-only">
      Мои отметки
    </h2>
    <div
      v-if="lessons.length"
      class="student-attendance-scroll"
      tabindex="0"
      aria-label="Журнал твоей посещаемости"
    >
      <table class="student-attendance-table">
        <caption class="web-sr-only">
          Твои отметки. Выбери отметку, чтобы открыть причину.
        </caption><thead>
          <tr>
            <th
              v-for="day in dates"
              :key="day.date"
              :colspan="day.count"
              scope="colgroup"
            >
              {{ formatLessonDate(day.date) }}
            </th>
          </tr><tr>
            <th
              v-for="lesson in lessons"
              :key="lesson.id"
              scope="col"
            >
              <span class="student-attendance-subject">{{ lesson.subject.name }}</span><span class="web-caption">{{ lessonTypeLabel(lesson.type) }}</span>
            </th>
          </tr>
        </thead><tbody>
          <tr>
            <td
              v-for="lesson in lessons"
              :key="lesson.id"
            >
              <button
                class="student-mark student-mark-button"
                :data-tone="studentMark(lesson.status).tone"
                :aria-expanded="selected?.id === lesson.id"
                :aria-label="`${lesson.subject.name}, ${formatLessonDate(lesson.date)}, ${studentMark(lesson.status).label}: подробности`"
                @click="open(lesson, $event)"
              >
                {{ studentMark(lesson.status).symbol }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p
      v-else
      class="web-notice"
    >
      За выбранный период пар нет.
    </p>
    <StudentMarkDetail
      v-if="selected"
      ref="detail"
      :lesson="selected"
      :read-only="readOnly"
      @close="close"
    />
  </div>
</template>
