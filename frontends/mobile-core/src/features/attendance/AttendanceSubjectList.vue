<script setup lang="ts">
import { computed } from 'vue'
import chevronDown from '../../assets/chevron-down.svg'
import {
  displayPercent,
  historyStatusLabel,
  lessonTypeLabel,
  LESSON_TYPE_ORDER,
  type AttendanceSubject,
  type AttendanceSubjectTypeSummary,
} from './attendance-view-model'

const props = withDefaults(defineProps<{
  subjects: readonly AttendanceSubject[]
  expandedSubjectId?: string | null
}>(), {
  expandedSubjectId: null,
})

const emit = defineEmits<{ toggle: [subjectId: string] }>()

function subjectIsExpanded(subject: AttendanceSubject): boolean {
  return subject.id === props.expandedSubjectId
}

function orderedTypeCards(subject: AttendanceSubject): readonly AttendanceSubjectTypeSummary[] {
  return LESSON_TYPE_ORDER
    .map((type) => subject.typeCards.find((card) => card.type === type))
    .filter((card): card is AttendanceSubjectTypeSummary => Boolean(card))
}

function historyTone(status: AttendanceSubjectTypeSummary['history'][number]['status']): string {
  return status.toLowerCase().replace('_', '-')
}

function metricLabel(
  status: 'present' | 'excused' | 'absent',
  percent: number | null,
): string {
  const labels: Record<typeof status, string> = {
    present: 'Присутствовал',
    excused: 'Уважительная причина',
    absent: 'Отсутствовал',
  }
  return `${labels[status]}: ${displayPercent(percent)}`
}

const hasSubjects = computed(() => props.subjects.length > 0)
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
      >
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
            <img
              :src="chevronDown"
              alt=""
            >
          </span>
        </button>
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
            <h3>Пары {{ card.metrics.held }}/{{ card.metrics.planned }}</h3>
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
              role="list"
              :aria-label="`История ${lessonTypeLabel(card.type)}`"
            >
              <span
                v-for="(segment, index) in card.history"
                :key="segment.id"
                class="attendance-type-card__segment"
                :class="`attendance-type-card__segment--${historyTone(segment.status)}`"
                role="listitem"
                :aria-label="`Занятие ${index + 1}: ${historyStatusLabel(segment.status)}`"
              />
            </div>
          </article>
        </div>
      </li>
    </ul>
  </section>
</template>
