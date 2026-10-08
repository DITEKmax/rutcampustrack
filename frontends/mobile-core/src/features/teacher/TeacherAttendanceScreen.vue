<script setup lang="ts">
import { teacherLessonTypeLabel } from './teacher-ui-format'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import HeadmanStatsSelect from '../headman-stats/HeadmanStatsSelect.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import TeacherJournalScreen from './TeacherJournalScreen.vue'
import { TeacherApiError, teacherJournalTypes, type TeacherApi, type TeacherJournalQuery, type TeacherStatsSubjectOption } from './teacher-client'
import './teacher-screen.pcss'
import './teacher-attendance-screen.pcss'
const props = withDefaults(defineProps<{ api: TeacherApi | null; semesterId: number | null; initialQuery?: TeacherJournalQuery | null }>(), { initialQuery: null })
const emit = defineEmits<{ apply: [query: TeacherJournalQuery]; 'change-context': [query: TeacherJournalQuery]; 'open-lesson': [lessonId: number, query: TeacherJournalQuery]; 'open-journal': [query: TeacherJournalQuery]; 'open-excuse': [requestId: string]; error: [cause: unknown] }>()
const semester = ref<{ id: number; dateFrom: string; dateTo: string } | null>(null)
const groups = ref<readonly { id: number; name: string }[]>([])
const subjectOptions = ref<readonly TeacherStatsSubjectOption[]>([])
const selectedGroupId = ref<number | null>(null); const selectedSubjectId = ref<number | null>(null)
const selectedTypes = ref<string[]>([]); const dateFrom = ref(''); const dateTo = ref(''); const periodOpen = ref(false)
const loading = ref(false); const error = ref<string | null>(null)
const restoredQuery = ref<TeacherJournalQuery | null>(null)
const pages = ref<Record<string,number>>({})
function contextKey(): string { return [props.semesterId,selectedGroupId.value,selectedSubjectId.value,[...selectedTypes.value].sort().join(','),dateFrom.value,dateTo.value].join(':') }
function rememberPage(page: number): void { const key = contextKey(); if (pages.value[key] !== page) pages.value = {...pages.value,[key]:page} }
let revision = 0; let disposed = false
const subjects = computed(() => { const merged = new Map<number,TeacherStatsSubjectOption>(); for (const option of subjectOptions.value) { if (option.groupId !== selectedGroupId.value) continue; const old = merged.get(option.subjectId); merged.set(option.subjectId,old ? { ...old, lessonTypes: [...new Set([...old.lessonTypes,...option.lessonTypes])] } : option) }; return [...merged.values()].sort((a,b) => a.subjectName.localeCompare(b.subjectName,'ru')) })
const types = computed(() => [...new Set(subjects.value.find(item => item.subjectId === selectedSubjectId.value)?.lessonTypes ?? [])])
const periodError = computed(() => { if (!semester.value) return null; if (!isDate(dateFrom.value) || !isDate(dateTo.value)) return 'Укажи обе даты.'; if (dateFrom.value > dateTo.value) return 'Начало периода позже окончания.'; if (dateFrom.value < semester.value.dateFrom || dateTo.value > semester.value.dateTo) return 'Выбери период внутри семестра.'; return null })
const query = computed<TeacherJournalQuery | null>(() => {
  if (loading.value || error.value || !props.api || !props.semesterId || !selectedGroupId.value || !selectedSubjectId.value || !selectedTypes.value.length || periodError.value) return null
  return { semesterId: props.semesterId, groupId: selectedGroupId.value, subjectId: selectedSubjectId.value, lessonTypes: [...selectedTypes.value], dateFrom: dateFrom.value, dateTo: dateTo.value, page: pages.value[contextKey()] ?? initialPage(), pageSize: 100 }
})
function initialPage(): number { const initial = restoredQuery.value; return initial && initial.semesterId === props.semesterId && initial.groupId === selectedGroupId.value && initial.subjectId === selectedSubjectId.value && [...teacherJournalTypes(initial)].sort().join() === [...selectedTypes.value].sort().join() && initial.dateFrom === dateFrom.value && initial.dateTo === dateTo.value ? initial.page ?? 0 : 0 }
watch(query, value => { if (value) emit('change-context',value) })
watch(() => [props.api,props.semesterId] as const, () => { void loadContext() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; revision += 1 })
function current(run: number): boolean { return !disposed && run === revision }
async function loadContext(): Promise<void> {
  const api = props.api; const semesterId = props.semesterId; const run = ++revision
  semester.value = null; groups.value = []; subjectOptions.value = []; selectedGroupId.value = null; selectedSubjectId.value = null; selectedTypes.value = []; error.value = null; loading.value = false; restoredQuery.value = null; pages.value = {}
  if (!api || !semesterId) return
  loading.value = true
  try {
    const active = await api.semester(); if (!current(run)) return
    if (active.id !== semesterId) throw new Error('Семестр изменился. Обнови экран.')
    const data = await api.catalog(semesterId); if (!current(run)) return
    semester.value = active; groups.value = [...data.groups].sort((a,b) => a.name.localeCompare(b.name,'ru')); subjectOptions.value = data.subjectOptions
    const initial = props.initialQuery?.semesterId === semesterId ? props.initialQuery : null
    restoredQuery.value = initial; pages.value = {}
    selectedGroupId.value = groups.value.some(group => group.id === initial?.groupId) ? initial!.groupId : groups.value[0]?.id ?? null
    selectedSubjectId.value = subjects.value.some(subject => subject.subjectId === initial?.subjectId) ? initial!.subjectId : subjects.value[0]?.subjectId ?? null
    selectedTypes.value = initial ? teacherJournalTypes(initial).filter(type => types.value.includes(type)).slice(0,3) : types.value.slice(0,3)
    if (!selectedTypes.value.length) selectedTypes.value = types.value.slice(0,3)
    dateFrom.value = initial?.dateFrom ?? active.dateFrom; dateTo.value = initial?.dateTo ?? active.dateTo
  } catch (cause) { if (!current(run) || cause instanceof StaleSessionGenerationError) return; error.value = cause instanceof TeacherApiError ? cause.message : cause instanceof Error ? cause.message : 'Не удалось получить группы и предметы.'; emit('error',cause) }
  finally { if (current(run)) loading.value = false }
}
function changeGroup(value: string): void { selectedGroupId.value = Number(value); selectedSubjectId.value = subjects.value[0]?.subjectId ?? null; selectedTypes.value = types.value.slice(0,3) }
function changeSubject(value: string): void { selectedSubjectId.value = Number(value); selectedTypes.value = types.value.slice(0,3) }
function toggleType(type: string): void { selectedTypes.value = selectedTypes.value.includes(type) ? selectedTypes.value.filter(value => value !== type) : [...selectedTypes.value,type].slice(0,3) }
function openLesson(id: number, page: number): void { if (!query.value) return; const context = { ...query.value,page }; emit('change-context',context); emit('open-lesson',id,context) }
function isDate(value: string): boolean { if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false; const date = new Date(`${value}T00:00:00Z`); return Number.isFinite(date.getTime()) && date.toISOString().slice(0,10) === value }
</script>
<template>
  <main
    class="teacher-screen teacher-attendance"
    aria-labelledby="teacher-attendance-title"
    :aria-busy="loading"
  >
    <header class="teacher-screen__header">
      <h1 id="teacher-attendance-title">
        Посещаемость
      </h1>
    </header>
    <p
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем группы и предметы…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      title="Посещаемость недоступна"
      :message="error"
      severity="error"
      action-label="Повторить"
      @action="loadContext"
    />
    <p
      v-else-if="!groups.length"
      class="teacher-screen__state"
      role="status"
    >
      Доступных групп пока нет.
    </p>
    <template v-else>
      <section
        class="teacher-attendance__filters"
        aria-label="Контекст посещаемости"
      >
        <div class="teacher-attendance__field">
          <span>Группа</span><HeadmanStatsSelect
            label="Группа"
            :model-value="String(selectedGroupId ?? '')"
            :options="groups.map(group => ({value: String(group.id), label: group.name}))"
            @update:model-value="changeGroup"
          />
        </div>
        <div class="teacher-attendance__field">
          <span>Предмет</span><HeadmanStatsSelect
            label="Предмет"
            :model-value="String(selectedSubjectId ?? '')"
            :options="subjects.map(subject => ({value: String(subject.subjectId), label: subject.subjectName}))"
            :disabled="!subjects.length"
            @update:model-value="changeSubject"
          />
        </div>
        <fieldset class="teacher-attendance__types">
          <legend>Типы занятий</legend><button
            v-for="type in types"
            :key="type"
            class="teacher-attendance__type"
            type="button"
            :aria-pressed="selectedTypes.includes(type)"
            :disabled="!selectedTypes.includes(type) && selectedTypes.length >= 3"
            @click="toggleType(type)"
          >
            {{ teacherLessonTypeLabel(type) }}
          </button>
        </fieldset>
        <p
          v-if="!selectedTypes.length"
          class="teacher-attendance__field-error"
          role="status"
        >
          Выбери хотя бы один тип занятия.
        </p>
        <button
          class="teacher-attendance__period-toggle"
          type="button"
          data-teacher-action="period-toggle"
          :aria-expanded="periodOpen"
          aria-controls="teacher-attendance-period"
          @click="periodOpen = !periodOpen"
        >
          <MobileIcon name="calendar" />Период<MobileIcon name="chevron-down" />
        </button>
        <div
          v-if="periodOpen"
          id="teacher-attendance-period"
          class="teacher-attendance__dates"
        >
          <label class="teacher-attendance__field"><span>С</span><input
            v-model="dateFrom"
            class="teacher-screen__date"
            type="date"
            :min="semester?.dateFrom"
            :max="semester?.dateTo"
            :aria-invalid="Boolean(periodError)"
            :aria-describedby="periodError ? 'teacher-period-error' : undefined"
          ></label>
          <label class="teacher-attendance__field"><span>По</span><input
            v-model="dateTo"
            class="teacher-screen__date"
            type="date"
            :min="semester?.dateFrom"
            :max="semester?.dateTo"
            :aria-invalid="Boolean(periodError)"
            :aria-describedby="periodError ? 'teacher-period-error' : undefined"
          ></label>
        </div>
        <p
          v-if="periodError"
          id="teacher-period-error"
          class="teacher-attendance__field-error"
          role="alert"
        >
          {{ periodError }}
        </p>
      </section>
      <div class="teacher-screen__section-heading">
        <h2 class="teacher-screen__section-title">
          Пары
        </h2><button
          class="teacher-screen__text-button"
          type="button"
          data-teacher-action="journal-open"
          :disabled="!query"
          @click="query && emit('open-journal',query)"
        >
          Журнал<MobileIcon
            name="back"
            class="teacher-screen__next"
          />
        </button>
      </div>
      <TeacherJournalScreen
        v-if="query"
        :api="api"
        :query="query"
        embedded
        :show-back="false"
        :show-export="false"
        @open-lesson="openLesson"
        @change-page="rememberPage"
        @open-excuse="emit('open-excuse',$event)"
        @error="emit('error',$event)"
      />
    </template>
  </main>
</template>
