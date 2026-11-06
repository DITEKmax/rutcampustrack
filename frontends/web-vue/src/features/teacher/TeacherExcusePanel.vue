<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import { RouterLink } from 'vue-router';
import type { TeacherExcuseResponse, TeacherRosterEntry } from '../../../../mobile-core/src/features/teacher/teacher-client';
import {
  teacherExcuseTypeLabel,
  teacherLessonTypeLabel,
} from '../../../../mobile-core/src/features/teacher/teacher-ui-format';
import { shortDate, useTeacherResource, type TeacherScope } from './teacher-state';
import TeacherNotice from './TeacherNotice.vue';
import TeacherAttachment from './TeacherAttachment.vue';
import { formatRequestDate, requestTime } from '../../../../mobile-core/src/features/requests/state';
import {
  manualAttachment,
  matchesTeacherRequest,
  teacherAttachmentKey,
  type TeacherEvidenceSource,
} from './teacher-evidence';

const props = withDefaults(defineProps<{
  scope: TeacherScope;
  source: TeacherEvidenceSource;
  trigger: HTMLElement | null;
  entry?: TeacherRosterEntry | null;
}>(), { entry: null });
defineEmits<{ close: [] }>();
const excuse = useTeacherResource<TeacherExcuseResponse>(props.scope);
const heading = ref<HTMLElement | null>(null);
const panel = ref<HTMLElement | null>(null);
const statusLabels: Record<string, string> = {
  APPROVED: 'Подтверждена',
  REJECTED: 'Отклонена',
  SUBMITTED: 'На рассмотрении',
  PENDING: 'На рассмотрении',
  CANCELLED: 'Отменена',
};
const request = computed(() => excuse.error.value ? null : excuse.data.value);
const manual = computed(() => 'ticketId' in props.source ? undefined : props.entry);
const attachment = computed(() => manual.value ? manualAttachment(manual.value) : null);
const typeLabel = computed(() => teacherExcuseTypeLabel(request.value?.excuseType ?? manual.value?.excuseType ?? null));
const pending = computed(() => ['SUBMITTED', 'PENDING'].includes(request.value?.status.toUpperCase() ?? ''));
const normalizedReason = (value: string) => value.trim().replace(/\s+/gu, ' ').toLocaleLowerCase('ru');
const extraReason = computed(() => {
  const reason = (request.value?.reason ?? manual.value?.excuseReason)?.trim();
  return reason && normalizedReason(reason) !== normalizedReason(typeLabel.value) ? reason : null;
});
const trigger = props.trigger;
function load(background = false) {
  const source = props.source;
  if (!('ticketId' in source)) return;
  void excuse.load(async () => {
    const data = await props.scope.api.excuse(source.ticketId);
    if (!matchesTeacherRequest(source, data)) throw new Error('Заявка для этой пары недоступна.');
    return data;
  }, undefined, background === true && !excuse.error.value);
}
watch(() => props.scope.refreshed.value, () => load(props.scope.backgroundRefresh.value));
onMounted(() => {
  load();
  heading.value?.focus();
});
onBeforeUnmount(() => {
  if (trigger?.isConnected && panel.value?.contains(document.activeElement)) trigger.focus();
});
</script>
<template>
  <section
    ref="panel"
    class="teacher-panel teacher-excuse web-stack"
    aria-labelledby="teacher-excuse-heading"
    @keydown.esc.stop.prevent="$emit('close')"
  >
    <div class="teacher-excuse-header">
      <h2
        id="teacher-excuse-heading"
        ref="heading"
        tabindex="-1"
      >
        {{ manual ? 'Ручная причина' : 'Заявка' }}
      </h2><button
        class="web-button web-icon-button"
        type="button"
        :aria-label="manual ? 'Закрыть причину' : 'Закрыть заявку'"
        @click="$emit('close')"
      >
        <MobileIcon name="close" />
      </button>
    </div>
    <template v-if="manual">
      <p>{{ manual.displayName }}</p>
      <p>
        {{ typeLabel }}<template v-if="extraReason">
          · {{ extraReason }}
        </template>
      </p>
      <p v-if="manual.excuseComment">
        Комментарий: {{ manual.excuseComment }}
      </p>
      <p class="web-caption">
        Причина указана в отметке. Только чтение.
      </p>
      <TeacherAttachment
        v-if="attachment"
        :key="teacherAttachmentKey(attachment)"
        :scope="scope"
        :source="source"
        :attachment="attachment"
      />
      <p
        v-else
        class="web-caption"
      >
        Доступных вложений нет.
      </p>
    </template>
    <TeacherNotice
      v-else
      :loading="excuse.loading.value"
      :error="excuse.error.value"
      @retry="load"
    />
    <template v-if="request">
      <p>
        {{ request.studentName }}<template v-if="request.groupName">
          · {{ request.groupName }}
        </template>
      </p>
      <p v-if="request.kind.toUpperCase() === 'EXCUSE' || request.excuseType || request.reason">
        {{ typeLabel }}<template v-if="extraReason">
          · {{ extraReason }}
        </template>
      </p>
      <p v-if="request.comment">
        Комментарий: {{ request.comment }}
      </p>
      <dl class="teacher-definitions">
        <div>
          <dt>Статус</dt><dd :class="{ 'teacher-request-pending': pending }">
            {{ statusLabels[request.status.toUpperCase()] ?? 'Статус уточняется' }}
          </dd>
        </div>
        <div>
          <dt>Подана</dt><dd>
            <time
              v-if="requestTime(request.createdAt)"
              :datetime="request.createdAt ?? undefined"
            >{{ formatRequestDate(request.createdAt, '').trim() }} · {{ requestTime(request.createdAt) }} МСК</time><span v-else>Дата не указана</span>
          </dd>
        </div><div v-if="requestTime(request.decisionAt)">
          <dt>Решение принято</dt><dd>
            <time :datetime="request.decisionAt ?? undefined">{{ formatRequestDate(request.decisionAt, '').trim() }} · {{ requestTime(request.decisionAt) }} МСК</time>
          </dd>
        </div>
        <div v-if="request.decisionByName">
          <dt>Решение принял</dt><dd>{{ request.decisionByName }}</dd>
        </div><div v-if="request.decisionComment">
          <dt>Комментарий к решению</dt><dd>{{ request.decisionComment }}</dd>
        </div>
      </dl>
      <ul class="teacher-links">
        <li
          v-for="lesson in request.lessons"
          :key="lesson.id"
        >
          <RouterLink :to="{ name: 'teacher-lesson', params: { lessonId: lesson.id } }">
            {{ shortDate(lesson.date) }} · {{ lesson.subjectName }} ·
            {{ teacherLessonTypeLabel(lesson.lessonType) }}
          </RouterLink>
        </li>
      </ul>
      <TeacherAttachment
        v-for="file in request.attachments"
        :key="teacherAttachmentKey(file)"
        :scope="scope"
        :source="source"
        :attachment="file"
      />
      <p
        v-if="!request.attachments.length"
        class="web-caption"
      >
        Доступных вложений нет.
      </p>
    </template>
  </section>
</template>
