<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import type { TeacherExcuseResponse } from '../../../../mobile-core/src/features/teacher/teacher-client';
import {
  teacherExcuseTypeLabel,
  teacherLessonTypeLabel,
} from '../../../../mobile-core/src/features/teacher/teacher-ui-format';
import { shortDate, useTeacherResource, type TeacherScope } from './teacher-state';
import TeacherNotice from './TeacherNotice.vue';
import TeacherAttachment from './TeacherAttachment.vue';

const props = defineProps<{ scope: TeacherScope; ticketId: string }>();
defineEmits<{ close: [] }>();
const excuse = useTeacherResource<TeacherExcuseResponse>(props.scope);
const heading = ref<HTMLElement | null>(null);
const statusLabels: Record<string, string> = {
  APPROVED: 'Подтверждена',
  REJECTED: 'Отклонена',
  PENDING: 'На рассмотрении',
  CANCELLED: 'Отменена',
};
let trigger: HTMLElement | null = null;
function load() {
  void excuse.load(() => props.scope.api.excuse(props.ticketId));
}
watch(() => props.scope.refreshed.value, load);
onMounted(() => {
  trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  load();
  heading.value?.focus();
});
onBeforeUnmount(() => {
  if (trigger?.isConnected) trigger.focus();
});
</script>
<template>
  <section
    class="teacher-panel teacher-excuse web-stack"
    aria-labelledby="teacher-excuse-heading"
  >
    <div class="teacher-toolbar">
      <h2
        id="teacher-excuse-heading"
        ref="heading"
        tabindex="-1"
      >
        Заявка
      </h2><button
        class="web-button"
        @click="$emit('close')"
      >
        Закрыть заявку
      </button>
    </div>
    <TeacherNotice
      :loading="excuse.loading.value"
      :error="excuse.error.value"
      @retry="load"
    />
    <template v-if="excuse.data.value">
      <p>
        {{ excuse.data.value.studentName }}<template v-if="excuse.data.value.groupName">
          · {{ excuse.data.value.groupName }}
        </template>
      </p>
      <p>
        {{ teacherExcuseTypeLabel(excuse.data.value.excuseType)
        }}<template v-if="excuse.data.value.reason">
          · {{ excuse.data.value.reason }}
        </template>
      </p>
      <p v-if="excuse.data.value.comment">
        Комментарий: {{ excuse.data.value.comment }}
      </p>
      <dl class="teacher-definitions">
        <div>
          <dt>Статус</dt><dd>
            {{
              statusLabels[excuse.data.value.status.toUpperCase()] ?? excuse.data.value.status
            }}
          </dd>
        </div><div v-if="excuse.data.value.decisionByName">
          <dt>Решение принял</dt><dd>{{ excuse.data.value.decisionByName }}</dd>
        </div><div v-if="excuse.data.value.decisionComment">
          <dt>Комментарий к решению</dt><dd>{{ excuse.data.value.decisionComment }}</dd>
        </div>
      </dl>
      <p class="web-caption">
        Показаны только пары и вложения, доступные тебе по текущим назначениям. Управление заявкой
        выполняет староста.
      </p>
      <ul class="teacher-links">
        <li
          v-for="lesson in excuse.data.value.lessons"
          :key="lesson.id"
        >
          <RouterLink :to="{ name: 'teacher-lesson', params: { lessonId: lesson.id } }">
            {{ shortDate(lesson.date) }} · {{ lesson.subjectName }} ·
            {{ teacherLessonTypeLabel(lesson.lessonType) }}
          </RouterLink>
        </li>
      </ul>
      <TeacherAttachment
        v-for="attachment in excuse.data.value.attachments"
        :key="attachment.id"
        :scope="scope"
        :ticket-id="ticketId"
        :attachment="attachment"
      />
      <p
        v-if="!excuse.data.value.attachments.length"
        class="web-caption"
      >
        Доступных вложений нет.
      </p>
    </template>
  </section>
</template>
