<script setup lang="ts">
import { computed, ref } from 'vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import type { RequestDetail } from '../../../../mobile-core/src/features/requests/types';
import {
  canCancelRequest,
  formatDecisionDate,
  formatLessonDate,
  formatLessonTime,
  formatRequestDate,
  lessonTypeLabel,
  requestKindLabel,
  requestStatusLabel,
  requestStatusTone,
  requestTime,
} from '../../../../mobile-core/src/features/requests/state';
import type { StudentFeatureScope } from '../../../../mobile-core/src/shared/session-owner';
import type { StudentScope } from './student-state';
import StudentRequestAttachment from './StudentRequestAttachment.vue';
const props = defineProps<{
  request: RequestDetail;
  scope: StudentScope;
  authority: StudentFeatureScope | null;
  busy: boolean;
  cancelBlocked: boolean;
  initialExpanded?: boolean;
}>();
defineEmits<{ cancel: []; retry: [] }>();
const expanded = ref(Boolean(props.initialExpanded));
const canCancel = computed(
  () =>
    canCancelRequest(props.request.summary, props.authority, {
      offline: !navigator.onLine,
      readOnly: props.scope.readOnly.value,
    }) && !props.cancelBlocked,
);
const reasons: Record<string, string> = {
  ILLNESS: 'Болезнь',
  MEDICAL_EXAMINATION: 'Медицинское обследование',
  COMPETITION_PARTICIPATION: 'Участие в соревнованиях',
  FAMILY_CIRCUMSTANCES: 'Семейные обстоятельства',
  OTHER: 'Другая причина',
  UNIVERSITY_ORDER: 'Распоряжение университета',
  SUMMONS: 'Повестка',
};
const kindAndReason = computed(() => {
  const kind = requestKindLabel(props.request.summary.kind);
  const reason = props.request.reason;
  if (!reason) return kind;
  const friendly = reasons[reason];
  const label = typeof friendly === 'string'
    ? friendly.charAt(0).toLocaleLowerCase('ru') + friendly.slice(1)
    : reason;
  return `${kind}: ${label}`;
});
</script>
<template>
  <article
    class="student-request-card web-panel web-stack"
    :data-request-id="request.summary.id"
    tabindex="-1"
  >
    <header class="student-request-heading">
      <h2>{{ kindAndReason }}</h2>
      <div class="student-actions">
        <span
          class="student-request-status"
          :data-tone="requestStatusTone(request.summary.status)"
        >{{ requestStatusLabel(request.summary.status) }}</span><button
          class="web-button web-icon-button student-request-expand"
          type="button"
          :aria-expanded="expanded"
          :aria-label="`${expanded ? 'Свернуть' : 'Раскрыть'} заявку ${request.summary.id}`"
          @click="expanded = !expanded"
        >
          <MobileIcon name="chevron-down" />
        </button>
      </div>
    </header>
    <p class="student-request-meta">
      Пар: {{ request.summary.lessons?.length ?? 0 }}
      <time
        v-if="request.summary.createdAt"
        :datetime="request.summary.createdAt"
      >
        · {{ formatRequestDate(request.summary.createdAt) }}, {{ requestTime(request.summary.createdAt) }} МСК
      </time>
    </p>
    <div
      v-if="expanded"
      class="student-request-detail web-stack"
    >
      <p class="web-caption">
        Заявка №{{ request.summary.id }}
      </p>
      <p
        v-if="request.summary.origin === 'AUTO_GEO_FAILURE'"
        class="web-caption"
      >
        Создана автоматически после геопроверки
      </p>
      <p v-if="request.comment">
        {{ request.comment }}
      </p>
      <div
        v-for="lesson in request.summary.lessons ?? []"
        :key="lesson.id"
        class="student-request-lesson web-stack"
      >
        <strong>{{ lesson.subjectName ?? 'Предмет не указан' }}</strong><p>
          {{ formatLessonDate(lesson.date) }} · {{ formatLessonTime(lesson) }} ·
          {{ lessonTypeLabel(lesson.subjectType) }}
        </p>
      </div><p
        v-if="request.detailState === 'error' || request.detailState === 'unavailable'"
        class="web-error"
        role="alert"
      >
        {{ request.detailError ?? 'Подробности заявки недоступны.'
        }}<button
          class="web-button"
          @click="$emit('retry')"
        >
          Повторить
        </button>
      </p><template v-else>
        <p
          v-if="!request.attachments?.length"
          class="web-caption"
        >
          Вложений нет.
        </p><StudentRequestAttachment
          v-for="attachment in request.attachments ?? []"
          :key="`${attachment.id}:${attachment.state}:${attachment.expiresAt}`"
          :scope="scope"
          :request-id="request.summary.id"
          :attachment="attachment"
        />
      </template><section
        v-if="request.decisionSelection"
        class="web-notice web-stack"
      >
        <h3>Пары в решении</h3><p
          v-for="lesson in request.decisionSelection.includedLessons"
          :key="`in:${lesson.id}`"
        >
          Учтена: {{ lesson.subjectName }} · {{ formatLessonDate(lesson.date) }}
        </p><p
          v-for="lesson in request.decisionSelection.excludedLessons"
          :key="`out:${lesson.id}`"
        >
          Не включена в решение: {{ lesson.subjectName }} · {{ formatLessonDate(lesson.date) }}
        </p>
      </section>
      <p v-if="request.decision?.comment">
        Решение: {{ request.decision.comment }}
      </p>
      <p
        v-if="request.decision?.decidedAt"
        class="web-caption"
      >
        <time :datetime="request.decision.decidedAt">
          {{ formatDecisionDate(request.decision.decidedAt) }}, {{ requestTime(request.decision.decidedAt) }} МСК
        </time>
      </p>
      <button
        v-if="canCancel"
        class="web-button web-button-danger"
        :disabled="busy"
        @click="$emit('cancel')"
      >
        Отменить заявку
      </button>
    </div>
  </article>
</template>
