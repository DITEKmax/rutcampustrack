<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import HeadmanStatsSelect from './HeadmanStatsSelect.vue';
import HeadmanStatsTrendChart from './HeadmanStatsTrendChart.vue';
import HeadmanStatsMetricTable from './HeadmanStatsMetricTable.vue';
import type { StatisticsGraphRange } from '../statistics/statistics-view-model';
import type {
  HeadmanStatsOverview,
  HeadmanStatsSemesterCatalog,
} from './headman-stats-history-client';
import type {
  HeadmanStatsBlock,
  HeadmanStatsResponse,
  HeadmanStatsStudentDetailResponse,
  HeadmanStatsTrendResponse,
} from './headman-stats-client';
import {
  headmanStatsPercent,
  headmanStatsSemesterDates,
  headmanStatsSources,
} from './headman-stats-view-model';
import autumnArtwork from '../../assets/headman-autumn.svg';
import springArtwork from '../../assets/headman-spring.svg';
import './headman-stats-mobile.pcss';

const props = defineProps<{
  response: HeadmanStatsResponse | null;
  catalog: HeadmanStatsResponse | null;
  block: HeadmanStatsBlock;
  subjectId: number | null;
  selectedTypes: readonly string[];
  typesEmpty: boolean;
  trend: HeadmanStatsTrendResponse | null;
  trendLoading: boolean;
  trendError: string | null;
  detail: HeadmanStatsStudentDetailResponse | null;
  detailStudentId: number | null;
  detailLoading: boolean;
  detailError: string | null;
  showBack: boolean;
  historyOpen: boolean;
  filtersActive: boolean;
  semesterCatalog: HeadmanStatsSemesterCatalog | null;
  semesterId: number | null;
  overview: HeadmanStatsOverview | null;
  range: StatisticsGraphRange;
  personalTrend: HeadmanStatsTrendResponse | null;
  personalTrendLoading: boolean;
  personalTrendError: string | null;
}>();
const emit = defineEmits<{
  back: [];
  block: [block: HeadmanStatsBlock];
  subject: [value: string];
  types: [types: readonly string[]];
  student: [studentId: number];
  'close-student': [];
  'detail-tab': [showHistory: boolean];
  'clear-filters': [];
  retry: [];
  'retry-personal-trend': [];
  'retry-detail': [];
  semester: [id: string];
  range: [range: StatisticsGraphRange];
}>();
const heading = ref<HTMLElement | null>(null);
const root = ref<HTMLElement | null>(null);
const summaryTab = ref<HTMLButtonElement | null>(null);
const historyTab = ref<HTMLButtonElement | null>(null);
const tabId = useId();
defineExpose({
  focus: () => heading.value?.focus(),
  focusStudent(studentId: number) {
    const row = root.value?.querySelector<HTMLButtonElement>(`[data-stats-student="${studentId}"]`);
    (row ?? heading.value)?.focus();
  },
  focusSummary: () => summaryTab.value?.focus(),
});
const expandedSubjects = new Set<number>();
watch(() => props.detailStudentId, () => expandedSubjects.clear(), { flush: 'sync' });
function rememberSubject(subjectId: number, event: Event): void {
  const element = event.currentTarget as HTMLDetailsElement;
  if (!element.isConnected) return;
  if (element.open) expandedSubjects.add(subjectId);
  else expandedSubjects.delete(subjectId);
}
function navigateTabs(event: KeyboardEvent): void {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const showHistory = event.key === 'Home' ? false : event.key === 'End' ? true : !props.historyOpen;
  emit('detail-tab', showHistory);
  (showHistory ? historyTab : summaryTab).value?.focus();
}
const semester = computed(() => props.detail?.context ?? props.catalog?.context ?? null);
const spring = computed(() => /^0[2-6]$/.test(semester.value?.semesterFrom?.slice(5, 7) ?? ''));
const subject = computed(
  () => props.catalog?.subjects.find((item) => item.id === props.subjectId) ?? null,
);
const rows = computed(() => props.response?.rows ?? []);
const metrics = computed(() => props.detail?.metrics ?? props.response?.summary ?? null);
const sources = computed(() =>
  headmanStatsSources(props.detail?.sources ? [{ sources: props.detail.sources }] : []),
);
const activeTrend = computed(() => (props.detail ? props.personalTrend : props.trend));
const personName = computed(() => {
  const names = (props.detail?.student.displayName ?? '').trim().split(/\s+/);
  return [names[0], names.slice(1).join(' ')];
});
function toggleType(code: string): void {
  emit(
    'types',
    props.selectedTypes.includes(code)
      ? props.selectedTypes.filter((item) => item !== code)
      : [...props.selectedTypes, code],
  );
}
</script>

<template>
  <div ref="root" class="headman-stats-mobile statistics-screen">
    <header class="headman-stats-mobile__header">
      <button
        v-if="showBack"
        class="headman-stats-mobile__back"
        type="button"
        aria-label="Назад"
        @click="detailStudentId !== null ? emit('close-student') : emit('back')"
      >
        <MobileIcon name="back" />
      </button>
      <h1
        id="headman-stats-title"
        ref="heading"
        tabindex="-1"
      >
        {{
          detailStudentId !== null
            ? 'Посещаемость'
            : subjectId !== null && block === 'subject'
              ? subject?.label || 'Предмет'
              : 'Статистика'
        }}
      </h1>
    </header>

    <template v-if="detailStudentId !== null">
      <p v-if="detail" class="headman-stats-mobile__person-name headman-stats-mobile__person-name--center">
        <span>{{ personName[0] }}</span><span>{{ personName[1] }}</span>
      </p>
      <div
        class="headman-stats-mobile__tabs"
        role="tablist"
        aria-label="Статистика студента"
        @keydown="navigateTabs"
      >
        <button
          :id="tabId + '-summary-tab'"
          ref="summaryTab"
          type="button"
          role="tab"
          :tabindex="historyOpen ? -1 : 0"
          :aria-selected="!historyOpen"
          :aria-controls="tabId + '-summary'"
          @click="emit('detail-tab', false)"
        >
          Сводка
        </button>
        <button
          :id="tabId + '-history-tab'"
          ref="historyTab"
          type="button"
          role="tab"
          :tabindex="historyOpen ? 0 : -1"
          :aria-selected="historyOpen"
          :aria-controls="tabId + '-history'"
          @click="emit('detail-tab', true)"
        >
          Прошедшие пары
        </button>
      </div>
      <div
        :hidden="!historyOpen"
        :id="tabId + '-history'"
        class="headman-stats-mobile__detail-panel"
        role="tabpanel"
        :aria-labelledby="tabId + '-history-tab'"
      >
        <slot v-if="historyOpen" name="history" />
      </div>
    </template>
    <template v-if="detailStudentId === null">
      <section
        v-if="semester && !(block === 'subject' && subjectId !== null)"
        class="headman-stats-mobile__semester"
        aria-label="Текущий семестр"
      >
        <img
          :src="spring ? springArtwork : autumnArtwork"
          alt=""
          aria-hidden="true"
        >
        <div>
          <HeadmanStatsSelect
            v-if="semesterCatalog?.semesters.length"
            class="headman-stats-mobile__semester-select"
            label="Семестр"
            :model-value="String(semesterId ?? '')"
            :options="
              semesterCatalog.semesters.map((option) => ({
                value: String(option.id),
                label: option.name + (option.archived ? ' · архив' : ''),
              }))
            "
            @update:model-value="emit('semester', $event)"
          />
          <strong v-else>{{ semester.semesterName || 'Нет активного семестра' }}</strong><span>{{ headmanStatsSemesterDates(semester.semesterFrom, semester.semesterTo) }}</span>
        </div>
      </section>
      <section
        v-if="block === 'group' && metrics"
        class="headman-stats-mobile__summary"
        aria-label="Посещаемость группы за семестр"
      >
        <article
          v-for="item in [
            { label: 'Чистая посещаемость', metric: metrics.present, hint: 'только «+»' },
            { label: 'С учётом уважительных', metric: metrics.presentOrExcused, hint: '«+» и «у»' },
          ]"
          :key="item.label"
          class="headman-stats-mobile__card headman-stats-mobile__metric"
        >
          <p>{{ item.label }}</p><div>
            <strong>{{ headmanStatsPercent(item.metric) }}</strong><span>{{ item.hint }}</span>
          </div>
          <progress
            :value="item.metric.denominator ? (item.metric.percent ?? 0) : 0"
            max="100"
            :aria-label="item.label"
          />
        </article>
        <article
          v-if="overview"
          class="headman-stats-mobile__card headman-stats-mobile__metric"
        >
          <p>Посещаемость группы сегодня</p><div>
            <strong>{{ overview.today.present.numerator }} из {{ overview.today.present.denominator }} ·
              {{ headmanStatsPercent(overview.today.present) }}</strong>
          </div><progress
            :value="overview.today.present.percent ?? 0"
            max="100"
            aria-label="Посещаемость группы сегодня"
          />
        </article>
      </section>
      <nav
        class="headman-stats-mobile__mode"
        aria-label="Инструменты раздела"
      >
      <div
        class="headman-stats-mobile__tabs"
        role="group"
        aria-label="Раздел статистики"
      >
        <button
          type="button"
          :aria-pressed="block === 'group'"
          @click="emit('block', 'group')"
        >
          Индивидуально
        </button><button
          type="button"
          :aria-pressed="block === 'subject'"
          @click="emit('block', 'subject')"
        >
          По предметам
        </button>
      </div>
      <slot name="dynamics" />
      </nav>
      <slot name="toolbar" />
      <template v-if="block === 'subject'">
        <section
          v-if="subjectId === null"
          class="headman-stats-mobile__students"
          aria-label="Предметы группы"
        >
          <ul>
            <li
              v-for="option in [...(catalog?.subjects ?? [])].sort((a, b) =>
                a.label.localeCompare(b.label, 'ru-RU'),
              )"
              :key="option.id"
            >
              <button
                class="headman-stats-mobile__student"
                type="button"
                @click="emit('subject', String(option.id))"
              >
                <strong>{{ option.label }}</strong><div>{{ option.lessonTypes.map((type) => type.label).join(' · ') }}</div><MobileIcon name="back" />
              </button>
            </li>
          </ul>
        </section>
        <template v-else>
          <HeadmanStatsSelect
            class="headman-stats-mobile__subject"
            label="Предмет"
            :model-value="String(subjectId ?? '')"
            :options="
              (catalog?.subjects ?? []).map((option) => ({
                value: String(option.id),
                label: option.label,
              }))
            "
            @update:model-value="emit('subject', $event)"
          />
          <div
            class="headman-stats-mobile__types"
            role="group"
            aria-label="Типы занятий"
          >
            <button
              v-for="type in subject?.lessonTypes ?? []"
              :key="type.code"
              type="button"
              :aria-pressed="selectedTypes.includes(type.code)"
              @click="toggleType(type.code)"
            >
              {{ { LECTURE: 'ЛК', PRACTICE: 'ПЗ', LAB: 'ЛЗ' }[type.code] ?? type.label }}
            </button>
          </div>
          <p
            v-if="typesEmpty"
            role="status"
          >
            Выбери хотя бы один тип занятия.
          </p>
          <div
            v-else-if="response"
            class="headman-stats-mobile__card headman-stats-mobile__table-wrap"
          >
            <table class="headman-stats-mobile__table">
              <caption class="statistics-visually-hidden">
                Посещаемость студентов по выбранному предмету, проценты
              </caption><thead>
                <tr>
                  <th scope="col">
                    Студент
                  </th><th
                    v-for="kind in ['present', 'excused', 'absent']"
                    :key="kind"
                    scope="col"
                  >
                    <span
                      :class="[
                        'headman-stats-mobile__status',
                        'headman-stats-mobile__status--' + kind,
                      ]"
                    >{{ { present: '+', excused: 'у', absent: 'н' }[kind] }}</span>
                  </th><th scope="col">
                    тик. н
                  </th>
                </tr>
              </thead><tbody>
                <tr
                  v-for="row in rows"
                  :key="row.studentId"
                >
                  <th scope="row">
                    <button
                      type="button"
                      :data-stats-student="row.studentId"
                      @click="emit('student', row.studentId)"
                    >
                      {{ row.displayName }}
                    </button>
                  </th><td class="headman-stats-mobile__present">
                    {{ headmanStatsPercent(row.metrics.present) }}
                  </td><td class="headman-stats-mobile__excused">
                    {{ headmanStatsPercent(row.metrics.excused) }}
                  </td><td class="headman-stats-mobile__absent">
                    {{ headmanStatsPercent(row.metrics.absent) }}
                  </td><td>{{ row.lateCheckin.submitted }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </template>
      <section
        v-else-if="response || catalog"
        class="headman-stats-mobile__students"
        aria-label="Студенты группы"
      >
        <h2>Группа</h2>
        <slot name="search" />
        <div
          v-if="response && !rows.length"
          class="headman-stats-mobile__empty"
        >
          <svg
            v-if="filtersActive"
            class="headman-stats-mobile__empty-art"
            viewBox="0 0 48 48"
            fill="none"
            aria-hidden="true"
          >
            <circle
              class="headman-stats-mobile__empty-halo"
              cx="24"
              cy="24"
              r="22"
            />
            <g
              stroke="currentColor"
              stroke-width="1.75"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M13 13h14a3 3 0 0 1 3 3v3M13 13a3 3 0 0 0-3 3v15a3 3 0 0 0 3 3h8M15 19h10m-10 5h5m-5 5h3" />
              <circle
                cx="30"
                cy="28"
                r="7"
              />
              <path d="m35 33 5 5" />
            </g>
          </svg>
          <p role="status">
            {{ filtersActive ? 'Нет подходящих вариантов' : 'Нет данных о студентах за выбранный семестр.' }}
          </p>
          <button
            v-if="filtersActive"
            type="button"
            @click="emit('clear-filters')"
          >
            Сбросить фильтры
          </button>
        </div>
        <ul v-else-if="rows.length">
          <li
            v-for="row in rows"
            :key="row.studentId"
          >
            <button
              type="button"
              class="headman-stats-mobile__student"
              :data-stats-student="row.studentId"
              @click="emit('student', row.studentId)"
            >
              <strong>{{ row.displayName }}</strong><div>
                <span
                  v-for="kind in ['present', 'excused', 'absent'] as const"
                  :key="kind"
                ><span
                  :class="[
                    'headman-stats-mobile__status',
                    'headman-stats-mobile__status--' + kind,
                  ]"
                >{{ { present: '+', excused: 'у', absent: 'н' }[kind] }}</span>{{ headmanStatsPercent(row.metrics[kind]) }}</span>
              </div><MobileIcon name="back" />
            </button>
          </li>
        </ul>
      </section>
      <section
        v-if="block === 'group' && (metrics || response)"
        class="headman-stats-mobile__details"
        :aria-labelledby="tabId + '-overview-details'"
      >
        <h2 :id="tabId + '-overview-details'">
          Подробная сводка
        </h2>
        <div class="headman-stats-mobile__details-content">
          <section
            v-if="metrics"
            class="headman-stats-mobile__card"
          >
            <h3>Состав посещаемости</h3>
            <div
              class="headman-stats-mobile__composition"
              role="img"
              :aria-label="`Присутствовал ${headmanStatsPercent(metrics.present)}, уважительно ${headmanStatsPercent(metrics.excused)}, отсутствовал ${headmanStatsPercent(metrics.absent)}`"
            >
              <span
                v-for="kind in ['present', 'excused', 'absent'] as const"
                :key="kind"
                :class="'headman-stats-mobile__composition--' + kind"
                :style="{ flexGrow: metrics[kind].numerator }"
              />
            </div><div class="headman-stats-mobile__status-cards">
              <article
                v-for="kind in ['present', 'excused', 'absent'] as const"
                :key="kind"
              >
                <span :class="'headman-stats-mobile__status headman-stats-mobile__status--' + kind">{{
                  { present: '+', excused: 'у', absent: 'н' }[kind]
                }}</span><strong>{{ headmanStatsPercent(metrics[kind]) }}</strong><small>{{ metrics[kind].numerator }} из {{ metrics[kind].denominator }}</small>
              </article>
            </div>
          </section>
          <section
            v-if="response"
            class="headman-stats-mobile__card"
            aria-label="Поданные уважительные причины"
          >
            <h3>Поданные уважительные причины</h3>
            <p v-if="response.reasonCounts == null">
              Разбивка причин недоступна.
            </p>
            <p v-else-if="!response.reasonCounts.length">
              Уважительные причины пока не подавали.
            </p>
            <ul v-else>
              <li
                v-for="reason in response.reasonCounts"
                :key="reason.code"
              >
                {{ reason.label }} · {{ reason.count }}
              </li>
            </ul>
          </section>
        </div>
      </section>
    </template>
    <div
      v-if="detailStudentId !== null || subjectId !== null"
      :hidden="historyOpen"
      :id="detailStudentId !== null ? tabId + '-summary' : undefined"
      class="headman-stats-mobile__detail-panel"
      :role="detailStudentId !== null ? 'tabpanel' : undefined"
      :aria-labelledby="detailStudentId !== null ? tabId + '-summary-tab' : undefined"
      :tabindex="detailStudentId !== null ? 0 : undefined"
    >
      <template v-if="detailStudentId !== null">
      <p
        v-if="detailLoading && !detail"
        role="status"
      >
        Загружаем статистику студента…
      </p>
      <StudentWarningBlock
        v-else-if="detailError"
        title="Статистика студента недоступна"
        :message="detailError"
        action-label="Повторить"
        @action="emit('retry-detail')"
      />
      <template v-else-if="detail && metrics">
        <section class="headman-stats-mobile__card headman-stats-mobile__personal">
          <h2>Посещаемость</h2><p>{{ detail.context.semesterName }}</p><div
            v-for="item in [
              { label: 'Чистая «+»', metric: metrics.present },
              { label: 'С учётом «у»', metric: metrics.presentOrExcused },
            ]"
            :key="item.label"
          >
            <span>{{ item.label }}</span><strong>{{ headmanStatsPercent(item.metric) }}</strong><small>{{ item.metric.numerator }} из {{ item.metric.denominator }}</small><progress
              :value="item.metric.denominator ? (item.metric.percent ?? 0) : 0"
              max="100"
              :aria-label="item.label"
            />
          </div>
        </section>
        <section
          class="headman-stats-mobile__card"
          aria-label="Рейтинг студента"
        >
          <h2>Рейтинг группы</h2>
          <p v-if="!detail.rank">
            Сервер не передал рейтинг студента.
          </p>
          <p v-else-if="detail.rank.visibility === 'HIDDEN'">
            Рейтинг скрыт для этой истории.
          </p>
          <p v-else-if="detail.rank.available">
            {{ detail.rank.position }} из {{ detail.rank.participantCount }}
          </p>
          <p v-else>
            Нет данных для рейтинга.
          </p>
        </section>
        <section
          class="headman-stats-mobile__card"
          aria-label="Посещаемость по предметам и типам занятий"
        >
          <h2>Предметы и типы занятий</h2>
          <p v-if="!detail.subjects.length">
            Нет завершённых занятий по предметам.
          </p>
          <details
            v-for="detailSubject in detail.subjects"
            :key="detailSubject.subjectId"
            class="headman-stats-mobile__subject-details"
            :data-subject-id="detailSubject.subjectId"
            :open="expandedSubjects.has(detailSubject.subjectId)"
            @toggle="rememberSubject(detailSubject.subjectId, $event)"
          >
            <summary>
              <span>{{ detailSubject.subjectName }}</span>
              <MobileIcon name="chevron-down" />
            </summary>
            <div class="headman-stats-mobile__subject-content">
              <HeadmanStatsMetricTable
                :label="`${detailSubject.subjectName} · все типы занятий`"
                :metrics="detailSubject.metrics"
              />
              <div
                v-for="type in detailSubject.lessonTypes"
                :key="type.code"
                :data-lesson-type="type.code"
              >
                <h3>{{ type.label }}</h3>
                <HeadmanStatsMetricTable
                  :label="`${detailSubject.subjectName} · ${type.label}`"
                  :metrics="type.metrics"
                />
              </div>
            </div>
          </details>
        </section>
        <section class="headman-stats-mobile__card">
          <h2>Статусы и тикеты</h2><div class="headman-stats-mobile__status-cards">
            <article
              v-for="kind in ['present', 'excused', 'absent'] as const"
              :key="kind"
            >
              <span
                :class="['headman-stats-mobile__status', 'headman-stats-mobile__status--' + kind]"
              >{{ { present: '+', excused: 'у', absent: 'н' }[kind] }}</span><strong>{{ headmanStatsPercent(metrics[kind]) }}</strong><small>{{ metrics[kind].numerator }} пар</small>
            </article>
          </div><div class="headman-stats-mobile__ticket-cards">
            <article
              v-for="item in [
                { label: 'Тикеты «н»', value: detail.lateCheckin },
                { label: 'Тикеты «у»', value: detail.excuse },
              ]"
              :key="item.label"
            >
              <h3>{{ item.label }}</h3><dl>
                <div
                  v-for="counter in ['submitted', 'approved', 'rejected'] as const"
                  :key="counter"
                >
                  <dt>
                    {{
                      { submitted: 'Подано', approved: 'Принято', rejected: 'Откл.' }[counter]
                    }}
                  </dt><dd>{{ item.value?.[counter] ?? '—' }}</dd>
                </div>
              </dl>
            </article>
          </div>
          <p v-if="!detail.lateCheckin || !detail.excuse">
            Сервер не передал счётчики тикетов за выбранный семестр.
          </p>
        </section>
      </template>
      </template>
    <section
      v-if="!historyOpen && detail"
      class="headman-stats-mobile__card headman-stats-mobile__sources"
    >
      <h2>Источники отметок «+»</h2><p v-if="detail && !detail.sources">
        Сервер не передал источники отметок за выбранный семестр.
      </p><dl>
        <div
          v-for="source in sources"
          :key="source.label"
        >
          <dt>{{ source.label }}</dt><dd>{{ source.percent === null ? '—' : Math.round(source.percent) + '%' }}</dd>
        </div>
      </dl>
    </section>

    <section
      v-if="!historyOpen && metrics && (subjectId !== null || detail)"
      class="headman-stats-mobile__card"
    >
      <h2>Состав посещаемости</h2><div
        class="headman-stats-mobile__composition"
        role="img"
        :aria-label="`Присутствовал ${headmanStatsPercent(metrics.present)}, уважительно ${headmanStatsPercent(metrics.excused)}, отсутствовал ${headmanStatsPercent(metrics.absent)}`"
      >
        <span
          v-for="kind in ['present', 'excused', 'absent'] as const"
          :key="kind"
          :class="'headman-stats-mobile__composition--' + kind"
          :style="{ flexGrow: metrics[kind].numerator }"
        />
      </div><div class="headman-stats-mobile__status-cards">
        <article
          v-for="kind in ['present', 'excused', 'absent'] as const"
          :key="kind"
        >
          <span :class="'headman-stats-mobile__status headman-stats-mobile__status--' + kind">{{
            { present: '+', excused: 'у', absent: 'н' }[kind]
          }}</span><strong>{{ headmanStatsPercent(metrics[kind]) }}</strong><small>{{ metrics[kind].numerator }} из {{ metrics[kind].denominator }}</small>
        </article>
      </div>
    </section>
    <section
      v-if="!historyOpen && ((subjectId !== null && !typesEmpty) || detail)"
      class="headman-stats-mobile__card"
      aria-label="График посещаемости"
    >
      <p
        v-if="detailStudentId === null ? trendLoading : personalTrendLoading"
        role="status"
      >
        Загружаем график…
      </p>
      <StudentWarningBlock
        v-else-if="detailStudentId === null ? trendError : personalTrendError"
        title="График недоступен"
        :message="(detailStudentId === null ? trendError : personalTrendError) ?? ''"
        action-label="Повторить"
        @action="detailStudentId === null ? emit('retry') : emit('retry-personal-trend')"
      />
      <HeadmanStatsTrendChart
        v-else
        :points="activeTrend?.points ?? []"
        :mode="activeTrend?.mode ?? 'SEMESTER'"
        :from="semester?.semesterFrom"
        :to="semester?.semesterTo"
      />
      <nav
        class="headman-stats-mobile__ranges"
        aria-label="Период графика"
      >
        <button
          v-for="option in ['days', 'weeks'] as const"
          :key="option"
          type="button"
          :aria-pressed="range === option"
          @click="emit('range', option)"
        >
          {{ option === 'days' ? 'Дни' : 'Недели' }}
        </button>
      </nav>
    </section>
    </div>
  </div>
</template>
