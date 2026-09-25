<script setup lang="ts">
import { computed, ref } from 'vue'
import HeadmanStatsTrendChart from './HeadmanStatsTrendChart.vue'
import type {
  HeadmanStatsExcuseEntry,
  HeadmanStatsLateCheckinEntry,
  HeadmanStatsPersonalMetric,
  HeadmanStatsStudentDetailResponse,
  HeadmanStatsTicketPage,
  HeadmanStatsTrendPoint,
} from './headman-stats-client'
import './headman-stats-detail-panel.pcss'

const props = defineProps<{
  studentId: number
  detail: HeadmanStatsStudentDetailResponse | null
  loading: boolean
  error: string | null
  offline: boolean
}>()

const emit = defineEmits<{
  close: []
  retry: []
  page: [kind: 'late' | 'excuse', page: number]
}>()

const panel = ref<HTMLElement | null>(null)
const visibleLateCheckins = computed(() => props.detail?.lateCheckins.items ?? [])
const visibleExcuses = computed(() => props.detail?.excuses.items.filter((entry) => entry.status !== 'DRAFT') ?? [])

function focus(): void {
  panel.value?.focus()
}

function metricText(metric: HeadmanStatsPersonalMetric): string {
  const percent = metric.percent === null ? 'Нет данных' : `${metric.percent.toFixed(1)}%`
  return `${metric.numerator} из ${metric.denominator} · ${percent}`
}

function dateText(value: string): string {
  const date = new Date(`${value}T12:00:00.000Z`)
  return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeZone: 'UTC' }).format(date)
}

function instantText(value: string | null): string {
  if (value === null) return 'Не решена'
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Moscow',
  }).format(date)
}

function statusText(status: string): string {
  switch (status) {
    case 'PENDING': return 'На рассмотрении'
    case 'DRAFT': return 'Черновик'
    case 'SUBMITTED': return 'Отправлена'
    case 'APPROVED': return 'Одобрена'
    case 'REJECTED': return 'Отклонена'
    case 'CANCELLED': return 'Отменена'
    default: return status
  }
}

function originText(origin: HeadmanStatsLateCheckinEntry['origin']): string {
  return origin === 'AUTO_GEO_FAILURE' ? 'Автоматически после сбоя геолокации' : 'По обращению студента'
}

function ticketPageLabel(page: HeadmanStatsTicketPage<unknown>): string {
  return page.totalPages === 0 ? 'Нет страниц' : `Страница ${page.page + 1} из ${page.totalPages}`
}

function toTrendPoints(detail: HeadmanStatsStudentDetailResponse): readonly HeadmanStatsTrendPoint[] {
  return detail.weeks.map((week) => ({
    key: week.weekStart,
    label: dateText(week.weekStart),
    from: week.from,
    to: week.to,
    present: week.present,
    presentOrExcused: week.presentOrExcused,
  }))
}

function lateHeading(entry: HeadmanStatsLateCheckinEntry): string {
  const lesson = [entry.subjectName, entry.lessonType, entry.lessonNumber === null ? null : `пара ${entry.lessonNumber}`]
    .filter(Boolean).join(' · ')
  return lesson || 'Занятие без описания'
}

function excuseLessons(entry: HeadmanStatsExcuseEntry): string {
  return entry.lessons.map((lesson) => [
    dateText(lesson.lessonDate), lesson.subjectName, lesson.lessonType,
    lesson.lessonNumber === null ? null : `пара ${lesson.lessonNumber}`,
  ].filter(Boolean).join(' · ')).join('; ')
}

defineExpose({ focus })
</script>

<template>
  <section
    ref="panel"
    class="headman-stats-detail"
    role="region"
    tabindex="-1"
    :aria-labelledby="`headman-stats-detail-title-${studentId}`"
  >
    <header class="headman-stats-detail__header">
      <div>
        <p class="headman-stats-detail__eyebrow">
          Личные подробности · ID {{ studentId }}
        </p>
        <h2 :id="`headman-stats-detail-title-${studentId}`">
          {{ detail?.student.displayName || `Студент ${studentId}` }}
        </h2>
      </div>
      <button
        type="button"
        class="headman-stats-detail__button"
        @click="emit('close')"
      >
        Закрыть подробности
      </button>
    </header>

    <p
      v-if="offline"
      class="headman-stats-detail__state"
      role="status"
    >
      Подробности доступны только при подключении к интернету.
    </p>
    <div
      v-else-if="error"
      class="headman-stats-detail__state headman-stats-detail__state--error"
      role="alert"
    >
      <span>{{ error }}</span>
      <button
        type="button"
        class="headman-stats-detail__button"
        :disabled="loading"
        @click="emit('retry')"
      >
        Повторить
      </button>
    </div>
    <p
      v-else-if="loading"
      class="headman-stats-detail__state"
      role="status"
      aria-live="polite"
    >
      Загружаем подробности студента…
    </p>
    <template v-else-if="detail">
      <p
        v-if="detail.emptyState === 'NO_ACTIVE_SEMESTER'"
        class="headman-stats-detail__state"
        role="status"
      >
        Нет активного семестра.
      </p>
      <p
        v-else-if="detail.emptyState === 'NO_COMPLETED_LESSONS'"
        class="headman-stats-detail__state"
        role="status"
      >
        В активном семестре ещё нет завершённых занятий.
      </p>

      <section
        class="headman-stats-detail__section"
        aria-label="Показатели студента"
      >
        <h3>Посещаемость за семестр</h3>
        <dl class="headman-stats-detail__metrics">
          <div><dt>«+» · присутствовал</dt><dd>{{ metricText(detail.metrics.present) }}</dd></div>
          <div><dt>«+ и у» · присутствовал или уважительно</dt><dd>{{ metricText(detail.metrics.presentOrExcused) }}</dd></div>
          <div><dt>«у» · уважительная причина</dt><dd>{{ metricText(detail.metrics.excused) }}</dd></div>
          <div><dt>«н» · отсутствие</dt><dd>{{ metricText(detail.metrics.absent) }}</dd></div>
        </dl>
      </section>

      <section
        class="headman-stats-detail__section"
        aria-label="Посещаемость по предметам и типам занятий"
      >
        <h3>Предметы и типы занятий</h3>
        <div
          class="headman-stats-detail__table-wrap"
          tabindex="0"
          aria-label="Показатели по предметам, прокручиваются по горизонтали"
        >
          <table>
            <thead>
              <tr>
                <th scope="col">
                  Предмет / тип
                </th>
                <th scope="col">
                  «+»
                </th>
                <th scope="col">
                  «+ и у»
                </th>
                <th scope="col">
                  «у»
                </th>
                <th scope="col">
                  «н»
                </th>
              </tr>
            </thead>
            <tbody>
              <template
                v-for="subject in detail.subjects"
                :key="subject.subjectId"
              >
                <tr>
                  <th scope="row">
                    {{ subject.subjectName }}
                  </th>
                  <td>{{ metricText(subject.metrics.present) }}</td>
                  <td>{{ metricText(subject.metrics.presentOrExcused) }}</td>
                  <td>{{ metricText(subject.metrics.excused) }}</td>
                  <td>{{ metricText(subject.metrics.absent) }}</td>
                </tr>
                <tr
                  v-for="type in subject.lessonTypes"
                  :key="`${subject.subjectId}:${type.code}`"
                >
                  <th
                    scope="row"
                    class="headman-stats-detail__type"
                  >
                    {{ type.label || type.code }}
                  </th>
                  <td>{{ metricText(type.metrics.present) }}</td>
                  <td>{{ metricText(type.metrics.presentOrExcused) }}</td>
                  <td>{{ metricText(type.metrics.excused) }}</td>
                  <td>{{ metricText(type.metrics.absent) }}</td>
                </tr>
              </template>
              <tr v-if="detail.subjects.length === 0">
                <td colspan="5">
                  Нет данных по предметам.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section
        class="headman-stats-detail__section"
        aria-label="Динамика студента по неделям"
      >
        <h3>По неделям</h3>
        <HeadmanStatsTrendChart
          :points="toTrendPoints(detail)"
          present-label="Присутствовал"
          present-or-excused-label="Присутствовал или уважительно"
        />
      </section>

      <section
        class="headman-stats-detail__section"
        aria-labelledby="headman-stats-detail-late-title"
      >
        <h3 id="headman-stats-detail-late-title">
          Опоздания и поздние отметки
        </h3>
        <ul
          v-if="visibleLateCheckins.length"
          class="headman-stats-detail__tickets"
        >
          <li
            v-for="entry in visibleLateCheckins"
            :key="entry.id"
          >
            <strong>{{ dateText(entry.lessonDate) }} · {{ lateHeading(entry) }}</strong>
            <span>Статус: {{ statusText(entry.status) }} · {{ originText(entry.origin) }}</span>
            <span>Создано: {{ instantText(entry.submittedAt) }} · Решение: {{ instantText(entry.decidedAt) }}</span>
          </li>
        </ul>
        <p
          v-else
          class="headman-stats-detail__state"
          role="status"
        >
          Заявок этой категории нет.
        </p>
        <nav
          class="headman-stats-detail__pagination"
          aria-label="Страницы заявок об опоздании"
        >
          <button
            class="headman-stats-detail__button"
            type="button"
            :disabled="loading || !detail.lateCheckins.hasPrevious"
            @click="emit('page', 'late', detail.lateCheckins.page - 1)"
          >
            Предыдущая
          </button>
          <span>{{ ticketPageLabel(detail.lateCheckins) }}</span>
          <button
            class="headman-stats-detail__button"
            type="button"
            :disabled="loading || !detail.lateCheckins.hasNext"
            @click="emit('page', 'late', detail.lateCheckins.page + 1)"
          >
            Следующая
          </button>
        </nav>
      </section>

      <section
        class="headman-stats-detail__section"
        aria-labelledby="headman-stats-detail-excuse-title"
      >
        <h3 id="headman-stats-detail-excuse-title">
          Заявления об уважительной причине
        </h3>
        <ul
          v-if="visibleExcuses.length"
          class="headman-stats-detail__tickets"
        >
          <li
            v-for="entry in visibleExcuses"
            :key="entry.id"
          >
            <strong>Заявление · {{ statusText(entry.status) }}</strong>
            <span>Занятия: {{ excuseLessons(entry) || 'Не указаны' }}</span>
            <span>Создано: {{ instantText(entry.submittedAt) }} · Решение: {{ instantText(entry.decidedAt) }}</span>
          </li>
        </ul>
        <p
          v-else
          class="headman-stats-detail__state"
          role="status"
        >
          Заявок этой категории нет.
        </p>
        <nav
          class="headman-stats-detail__pagination"
          aria-label="Страницы заявлений об уважительной причине"
        >
          <button
            class="headman-stats-detail__button"
            type="button"
            :disabled="loading || !detail.excuses.hasPrevious"
            @click="emit('page', 'excuse', detail.excuses.page - 1)"
          >
            Предыдущая
          </button>
          <span>{{ ticketPageLabel(detail.excuses) }}</span>
          <button
            class="headman-stats-detail__button"
            type="button"
            :disabled="loading || !detail.excuses.hasNext"
            @click="emit('page', 'excuse', detail.excuses.page + 1)"
          >
            Следующая
          </button>
        </nav>
      </section>
    </template>
  </section>
</template>
