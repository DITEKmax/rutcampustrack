<script setup lang="ts">
import { computed, ref } from 'vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import HeadmanStatsSelect from './HeadmanStatsSelect.vue'
import HeadmanStatsTrendChart from './HeadmanStatsTrendChart.vue'
import type { StatisticsGraphRange } from '../statistics/statistics-view-model'
import type { HeadmanStatsOverview, HeadmanStatsSemesterCatalog } from './headman-stats-history-client'
import type { HeadmanStatsBlock, HeadmanStatsResponse, HeadmanStatsStudentDetailResponse, HeadmanStatsTrendResponse } from './headman-stats-client'
import { headmanStatsPercent, headmanStatsSemesterDates, headmanStatsSources } from './headman-stats-view-model'
import autumnArtwork from '../../assets/headman-autumn.svg'
import springArtwork from '../../assets/headman-spring.svg'
import './headman-stats-mobile.pcss'

const props = defineProps<{
  response: HeadmanStatsResponse | null
  catalog: HeadmanStatsResponse | null
  block: HeadmanStatsBlock
  subjectId: number | null
  selectedTypes: readonly string[]
  typesEmpty: boolean
  trend: HeadmanStatsTrendResponse | null
  trendLoading: boolean
  trendError: string | null
  detail: HeadmanStatsStudentDetailResponse | null
  detailStudentId: number | null
  detailLoading: boolean
  detailError: string | null
  showBack: boolean
  historyOpen: boolean
  semesterCatalog: HeadmanStatsSemesterCatalog | null
  semesterId: number | null
  overview: HeadmanStatsOverview | null
  range: StatisticsGraphRange
  personalTrend: HeadmanStatsTrendResponse | null
  personalTrendLoading: boolean
  personalTrendError: string | null
}>()
const emit = defineEmits<{
  back: []
  block: [block: HeadmanStatsBlock]
  subject: [value: string]
  types: [types: readonly string[]]
  student: [studentId: number, event: MouseEvent]
  'close-student': []
  retry: []
  'retry-personal-trend': []
  'retry-detail': []
  semester: [id: string]
  range: [range: StatisticsGraphRange]
}>()
const heading = ref<HTMLElement | null>(null)
defineExpose({ focus: () => heading.value?.focus() })
const semester = computed(() => props.detail?.context ?? props.catalog?.context ?? null)
const spring = computed(() => /^0[2-6]$/.test(semester.value?.semesterFrom?.slice(5, 7) ?? ''))
const subject = computed(() => props.catalog?.subjects.find((item) => item.id === props.subjectId) ?? null)
const rows = computed(() => props.response?.rows ?? [])
const metrics = computed(() => props.detail?.metrics ?? props.response?.summary ?? null)
const sources = computed(() => headmanStatsSources(props.detail?.sources ? [{ sources: props.detail.sources }] : []))
const activeTrend = computed(() => props.detail ? props.personalTrend : props.trend)
const personName = computed(() => { const names = (props.detail?.student.displayName ?? '').trim().split(/\s+/); return [names[0], names.slice(1).join(' ')] })
function toggleType(code: string): void {
  emit('types', props.selectedTypes.includes(code) ? props.selectedTypes.filter((item) => item !== code) : [...props.selectedTypes, code])
}
</script>

<template>
  <div class="headman-stats-mobile statistics-screen">
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
        {{ detailStudentId !== null ? 'Посещаемость' : subjectId !== null && block === 'subject' ? subject?.label || 'Предмет' : 'Статистика' }}
      </h1>
    </header>

    <p
      v-if="historyOpen && detail"
      class="headman-stats-mobile__person-name"
    >
      {{ detail.student.displayName }}
    </p>
    <slot
      v-if="historyOpen"
      name="history"
    />
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
            :options="semesterCatalog.semesters.map(option => ({ value: String(option.id), label: option.name + (option.archived ? ' · архив' : '') }))"
            @update:model-value="emit('semester', $event)"
          />
          <strong v-else>{{ semester.semesterName || 'Нет активного семестра' }}</strong><span>{{ headmanStatsSemesterDates(semester.semesterFrom, semester.semesterTo) }}</span>
        </div>
      </section>
      <nav
        class="headman-stats-mobile__tabs"
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
      </nav>
      <slot name="toolbar" />
      <section
        v-if="block === 'group'"
        class="headman-stats-mobile__card"
        aria-label="Динамика посещаемости группы"
      >
        <p
          v-if="trendLoading"
          role="status"
        >
          Загружаем график…
        </p><StudentWarningBlock
          v-else-if="trendError"
          title="График недоступен"
          :message="trendError"
          action-label="Повторить"
          @action="emit('retry')"
        /><HeadmanStatsTrendChart
          v-else
          :points="trend?.points ?? []"
          mode="SEMESTER"
          :from="semester?.semesterFrom"
          :to="semester?.semesterTo"
          present-label="Чистая «+»"
          present-or-excused-label="С учётом «у»"
        />
        <nav
          class="headman-stats-mobile__ranges"
          aria-label="Период графика"
        >
          <button
            type="button"
            :aria-pressed="range === 'days'"
            @click="emit('range', 'days')"
          >
            Дни
          </button><button
            type="button"
            :aria-pressed="range === 'weeks'"
            @click="emit('range', 'weeks')"
          >
            Недели
          </button>
        </nav>
      </section>
      <section
        v-if="block === 'group' && metrics"
        class="headman-stats-mobile__card"
      >
        <h2>Состав посещаемости</h2><div
          class="headman-stats-mobile__composition"
          role="img"
          :aria-label="`Присутствовал ${headmanStatsPercent(metrics.present)}, уважительно ${headmanStatsPercent(metrics.excused)}, отсутствовал ${headmanStatsPercent(metrics.absent)}`"
        >
          <span
            v-for="kind in (['present','excused','absent'] as const)"
            :key="kind"
            :class="'headman-stats-mobile__composition--' + kind"
            :style="{flexGrow:metrics[kind].numerator}"
          />
        </div><div class="headman-stats-mobile__status-cards">
          <article
            v-for="kind in (['present','excused','absent'] as const)"
            :key="kind"
          >
            <span :class="'headman-stats-mobile__status headman-stats-mobile__status--' + kind">{{ {present:'+',excused:'у',absent:'н'}[kind] }}</span><strong>{{ headmanStatsPercent(metrics[kind]) }}</strong><small>{{ metrics[kind].numerator }} из {{ metrics[kind].denominator }}</small>
          </article>
        </div>
      </section>
      <section
        v-if="block === 'group' && metrics"
        class="headman-stats-mobile__summary"
        aria-label="Посещаемость группы за семестр"
      >
        <article
          v-for="item in [{ label: 'Чистая посещаемость', metric: metrics.present, hint: 'только «+»' }, { label: 'С учётом уважительных', metric: metrics.presentOrExcused, hint: '«+» и «у»' }]"
          :key="item.label"
          class="headman-stats-mobile__card headman-stats-mobile__metric"
        >
          <p>{{ item.label }}</p><div><strong>{{ headmanStatsPercent(item.metric) }}</strong><span>{{ item.hint }}</span></div>
          <progress
            :value="item.metric.denominator ? item.metric.percent ?? 0 : 0"
            max="100"
            :aria-label="item.label"
          />
        </article>
        <article
          v-if="overview"
          class="headman-stats-mobile__card headman-stats-mobile__metric"
        >
          <p>Посещаемость группы сегодня</p><div><strong>{{ overview.today.present.numerator }} из {{ overview.today.present.denominator }} · {{ headmanStatsPercent(overview.today.present) }}</strong></div><progress
            :value="overview.today.present.percent ?? 0"
            max="100"
            aria-label="Посещаемость группы сегодня"
          />
        </article>
      </section>
      <template v-if="block === 'subject'">
        <section
          v-if="subjectId === null"
          class="headman-stats-mobile__students"
          aria-label="Предметы группы"
        >
          <ul>
            <li
              v-for="option in [...(catalog?.subjects ?? [])].sort((a,b) => a.label.localeCompare(b.label, 'ru-RU'))"
              :key="option.id"
            >
              <button
                class="headman-stats-mobile__student"
                type="button"
                @click="emit('subject', String(option.id))"
              >
                <strong>{{ option.label }}</strong><div>{{ option.lessonTypes.map(type => type.label).join(' · ') }}</div><MobileIcon name="back" />
              </button>
            </li>
          </ul>
        </section>
        <template v-else>
          <HeadmanStatsSelect
            class="headman-stats-mobile__subject"
            label="Предмет"
            :model-value="String(subjectId ?? '')"
            :options="(catalog?.subjects ?? []).map(option => ({ value: String(option.id), label: option.label }))"
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
                    <span :class="['headman-stats-mobile__status', 'headman-stats-mobile__status--' + kind]">{{ { present: '+', excused: 'у', absent: 'н' }[kind] }}</span>
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
                      @click="emit('student', row.studentId, $event)"
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
        v-else-if="response"
        class="headman-stats-mobile__students"
        aria-label="Студенты группы"
      >
        <h2>Группа</h2>
        <slot name="search" />
        <ul>
          <li
            v-for="row in rows"
            :key="row.studentId"
          >
            <button
              type="button"
              class="headman-stats-mobile__student"
              @click="emit('student', row.studentId, $event)"
            >
              <strong>{{ row.displayName }}</strong><div>
                <span
                  v-for="kind in (['present', 'excused', 'absent'] as const)"
                  :key="kind"
                ><span :class="['headman-stats-mobile__status', 'headman-stats-mobile__status--' + kind]">{{ { present: '+', excused: 'у', absent: 'н' }[kind] }}</span>{{ headmanStatsPercent(row.metrics[kind]) }}</span>
              </div><MobileIcon name="back" />
            </button>
          </li>
        </ul>
      </section>
    </template>
    <template v-else-if="!historyOpen">
      <p
        v-if="detailLoading"
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
        <div class="headman-stats-mobile__personal-intro">
          <p class="headman-stats-mobile__person-name headman-stats-mobile__person-name--center">
            <span>{{ personName[0] }}</span><span>{{ personName[1] }}</span>
          </p>
          <slot name="toolbar" />
        </div>
        <section class="headman-stats-mobile__card headman-stats-mobile__personal">
          <h2>Посещаемость</h2><p>{{ detail.context.semesterName }}</p><div
            v-for="item in [{ label: 'Чистая «+»', metric: metrics.present }, { label: 'С учётом «у»', metric: metrics.presentOrExcused }]"
            :key="item.label"
          >
            <span>{{ item.label }}</span><strong>{{ headmanStatsPercent(item.metric) }}</strong><progress
              :value="item.metric.denominator ? item.metric.percent ?? 0 : 0"
              max="100"
              :aria-label="item.label"
            />
          </div>
        </section>
        <section class="headman-stats-mobile__card">
          <h2>Статусы и тикеты</h2><div class="headman-stats-mobile__status-cards">
            <article
              v-for="kind in (['present', 'excused', 'absent'] as const)"
              :key="kind"
            >
              <span :class="['headman-stats-mobile__status', 'headman-stats-mobile__status--' + kind]">{{ { present: '+', excused: 'у', absent: 'н' }[kind] }}</span><strong>{{ headmanStatsPercent(metrics[kind]) }}</strong><small>{{ metrics[kind].numerator }} пар</small>
            </article>
          </div><div
            class="headman-stats-mobile__ticket-cards"
          >
            <article
              v-for="item in [{ label: 'Тикеты «н»', value: detail.lateCheckin }, { label: 'Тикеты «у»', value: detail.excuse }]"
              :key="item.label"
            >
              <h3>{{ item.label }}</h3><dl>
                <div
                  v-for="counter in (['submitted', 'approved', 'rejected'] as const)"
                  :key="counter"
                >
                  <dt>{{ { submitted: 'Подано', approved: 'Принято', rejected: 'Откл.' }[counter] }}</dt><dd>{{ item.value?.[counter] ?? '—' }}</dd>
                </div>
              </dl>
            </article>
          </div>
          <p v-if="!detail.lateCheckin || !detail.excuse">
            Сервер не передал счётчики тикетов за выбранный семестр.
          </p>
        </section>
        <slot name="history" />
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
          v-for="kind in (['present', 'excused', 'absent'] as const)"
          :key="kind"
          :class="'headman-stats-mobile__composition--' + kind"
          :style="{ flexGrow: metrics[kind].numerator }"
        />
      </div><div class="headman-stats-mobile__status-cards">
        <article
          v-for="kind in (['present', 'excused', 'absent'] as const)"
          :key="kind"
        >
          <span :class="'headman-stats-mobile__status headman-stats-mobile__status--' + kind">{{ {present:'+',excused:'у',absent:'н'}[kind] }}</span><strong>{{ headmanStatsPercent(metrics[kind]) }}</strong><small>{{ metrics[kind].numerator }} из {{ metrics[kind].denominator }}</small>
        </article>
      </div>
    </section>
    <section
      v-if="!historyOpen && (subjectId !== null && !typesEmpty || detail)"
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
          v-for="option in (['days','weeks'] as const)"
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
</template>
