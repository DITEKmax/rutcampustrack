<script setup lang="ts">
import MobileIcon from '../../shared/components/MobileIcon.vue';
import AttendanceDocumentPreview from './AttendanceDocumentPreview.vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import chevronDown from '../../assets/chevron-down.svg';
import {
  attendanceExplanationText,
  attendanceDocuments,
  displayPercent,
  historyStatusLabel,
  lessonTypeLabel,
  LESSON_TYPE_ORDER,
  type AttendanceDocumentResolver,
  type AttendanceSubject,
  type AttendanceSubjectTypeSummary,
} from './attendance-view-model';

const props = withDefaults(
  defineProps<{
    subjects: readonly AttendanceSubject[];
    documentForLesson?: AttendanceDocumentResolver | undefined;
    expandedSubjectId?: string | null;
  }>(),
  {
    documentForLesson: undefined,
    expandedSubjectId: null,
  },
);

const emit = defineEmits<{
  toggle: [subjectId: string];
  'explanation-visible': [visible: boolean];
}>();

function subjectIsExpanded(subject: AttendanceSubject): boolean {
  return subject.id === props.expandedSubjectId;
}

function orderedTypeCards(subject: AttendanceSubject): readonly AttendanceSubjectTypeSummary[] {
  return LESSON_TYPE_ORDER.map((type) =>
    subject.typeCards.find((card) => card.type === type),
  ).filter((card): card is AttendanceSubjectTypeSummary => Boolean(card));
}

function historyTone(status: AttendanceSubjectTypeSummary['history'][number]['status']): string {
  return status.toLowerCase().replace('_', '-');
}

function metricLabel(status: 'present' | 'excused' | 'absent', percent: number | null): string {
  const labels: Record<typeof status, string> = {
    present: 'Присутствовал',
    excused: 'Уважительная причина',
    absent: 'Отсутствовал',
  };
  return `${labels[status]}: ${displayPercent(percent)}`;
}

const documentPreviews = ref<InstanceType<typeof AttendanceDocumentPreview>[]>([]);
function closeOverlay(): boolean {
  if (documentPreviews.value.some((preview) => preview.closeOverlay())) return true;
  if (explanationId.value === null) return false;
  explanationId.value = null;
  return true;
}
defineExpose({ closeOverlay });
const explanationId = ref<string | null>(null);
const explanationInteraction = ref<'hover' | 'focus' | 'tap'>('hover');
function showExplanation(id: string, interaction: 'hover' | 'focus' | 'tap'): void {
  if (interaction === 'hover' && explanationId.value !== null) return;
  explanationId.value = id;
  explanationInteraction.value = interaction;
}
function hideHoverExplanation(event: MouseEvent): void {
  if ((event.relatedTarget as HTMLElement | null)?.closest?.('[data-attendance-explanation]'))
    return;
  if (explanationInteraction.value === 'hover') explanationId.value = null;
}
function closeExplanationOnBlur(event: FocusEvent): void {
  if (
    explanationInteraction.value === 'tap' ||
    (event.relatedTarget as HTMLElement | null)?.closest?.('[data-attendance-document]')
  )
    return;
  explanationId.value = null;
}
function segmentDocument(segment: AttendanceSubjectTypeSummary['history'][number]) {
  return segment.status === 'EXCUSED' ? attendanceDocuments(props.documentForLesson?.(segment.id, segment.explanation)) : [];
}

onBeforeUnmount(() => emit('explanation-visible', false));
watch(explanationId, (id) => emit('explanation-visible', id !== null));
watch(
  () => [props.subjects, props.expandedSubjectId],
  () => {
    explanationId.value = null;
  },
);
function segmentExplanation(segment: AttendanceSubjectTypeSummary['history'][number]): string {
  return attendanceExplanationText(segment.status, segment.explanation);
}
function segmentId(subjectId: string, type: string, id: string): string {
  return `attendance-history-explanation-${subjectId}-${type}-${id}`;
}
const hasSubjects = computed(() => props.subjects.length > 0);
</script>

<template>
  <section
    class="attendance-subject-list"
    aria-labelledby="attendance-subjects-title"
  >
    <h2
      id="attendance-subjects-title"
      class="attendance-visually-hidden"
    >
      По предметам
    </h2>
    <p
      v-if="!hasSubjects"
      class="attendance-subject-list__empty"
    >
      Предметы появятся после первой пары.
    </p>
    <ul v-else>
      <li
        v-for="subject in subjects"
        :key="subject.id"
        class="attendance-subject-list__item"
        :class="{ 'attendance-subject-list__item--expanded': subjectIsExpanded(subject) }"
      >
        <h3 class="attendance-subject-list__heading">
          <button
            class="attendance-subject-list__trigger"
            type="button"
            :aria-expanded="subjectIsExpanded(subject)"
            :aria-controls="`attendance-subject-${subject.id}`"
            @click="emit('toggle', subject.id)"
          >
            <span class="attendance-subject-list__trigger-label">{{ subject.name }}</span>
            <span
              class="attendance-subject-list__disclosure"
              aria-hidden="true"
            >
              <SvgIcon :src="chevronDown" />
            </span>
          </button>
        </h3>
        <div
          v-if="subjectIsExpanded(subject)"
          :id="`attendance-subject-${subject.id}`"
          class="attendance-subject-list__cards"
        >
          <article
            v-for="card in orderedTypeCards(subject)"
            :key="card.type"
            class="attendance-type-card"
          >
            <div class="attendance-type-card__pill">
              {{ lessonTypeLabel(card.type) }}
            </div>
            <h4>Пары {{ card.metrics.held }}/{{ card.metrics.planned }}</h4>
            <div
              class="attendance-type-card__metrics"
              role="group"
              aria-label="Метрики посещаемости"
            >
              <span
                class="attendance-type-card__metric attendance-type-card__metric--present"
                :aria-label="metricLabel('present', card.metrics.present.percent)"
              >
                <i aria-hidden="true" />{{ displayPercent(card.metrics.present.percent) }}
              </span>
              <span
                class="attendance-type-card__metric attendance-type-card__metric--excused"
                :aria-label="metricLabel('excused', card.metrics.excused.percent)"
              >
                <i aria-hidden="true" />{{ displayPercent(card.metrics.excused.percent) }}
              </span>
              <span
                class="attendance-type-card__metric attendance-type-card__metric--absent"
                :aria-label="metricLabel('absent', card.metrics.absent.percent)"
              >
                <i aria-hidden="true" />{{ displayPercent(card.metrics.absent.percent) }}
              </span>
            </div>
            <div
              class="attendance-type-card__history"
              role="group"
              :aria-label="`История ${lessonTypeLabel(card.type)}`"
            >
              <template
                v-for="(segment, index) in card.history"
                :key="segment.id"
              >
                <button
                  v-if="segmentExplanation(segment) || segmentDocument(segment).length"
                  class="attendance-type-card__segment attendance-type-card__segment--explanation"
                  :class="`attendance-type-card__segment--${historyTone(segment.status)}`"
                  type="button"
                  :aria-label="`Занятие ${index + 1}: ${historyStatusLabel(segment.status)} · ${segmentExplanation(segment)}`"
                  :aria-expanded="explanationId === segmentId(subject.id, card.type, segment.id)"
                  :aria-describedby="
                    explanationId === segmentId(subject.id, card.type, segment.id)
                      ? explanationId
                      : undefined
                  "
                  @mouseenter="
                    showExplanation(segmentId(subject.id, card.type, segment.id), 'hover')
                  "
                  @mouseleave="hideHoverExplanation"
                  @focus="showExplanation(segmentId(subject.id, card.type, segment.id), 'focus')"
                  @blur="closeExplanationOnBlur"
                  @click="showExplanation(segmentId(subject.id, card.type, segment.id), 'tap')"
                  @keydown.esc="explanationId = null"
                />
                <span
                  v-else
                  class="attendance-type-card__segment"
                  :class="`attendance-type-card__segment--${historyTone(segment.status)}`"
                  role="img"
                  :aria-label="`Занятие ${index + 1}: ${historyStatusLabel(segment.status)}`"
                />
              </template>
            </div>
            <template
              v-for="segment in card.history"
              :key="segment.id"
            >
              <div
                v-if="explanationId === segmentId(subject.id, card.type, segment.id)"
                class="attendance-status-explanation"
                data-attendance-explanation
                @mouseleave="hideHoverExplanation"
              >
                <p
                  :id="explanationId"
                  role="tooltip"
                >
                  {{ segmentExplanation(segment) }}
                </p>
                <AttendanceDocumentPreview
                  v-for="(document, documentIndex) in segmentDocument(segment)"
                  :key="documentIndex"
                  ref="documentPreviews"
                  :document="document"
                  @blur="closeExplanationOnBlur"
                />
                <button
                  v-if="explanationInteraction === 'tap'"
                  class="attendance-explanation-close"
                  type="button"
                  aria-label="Закрыть причину"
                  @click="explanationId = null"
                >
                  <MobileIcon name="close" />
                </button>
              </div>
            </template>
          </article>
        </div>
      </li>
    </ul>
  </section>
</template>
