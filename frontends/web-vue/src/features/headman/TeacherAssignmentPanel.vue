<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import type { HeadmanFeature } from './context';
import { addDays, dateLabel, today } from './context';
import { message } from '../../auth/session';
import type { HeadmanTeacher, HeadmanSemester, HeadmanSubjectType, HeadmanSubjectAssignment, HeadmanSubjectInitialAssignment } from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client';
import WebDateField from '../../shared/WebDateField.vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';

const props = defineProps<{
  feature: HeadmanFeature; semester: HeadmanSemester; type: HeadmanSubjectType; label: string;
  teachers: readonly HeadmanTeacher[]; assignments: readonly HeadmanSubjectAssignment[];
  staged: readonly HeadmanSubjectInitialAssignment[]; existing: boolean; locked: boolean; assignmentEnabled: boolean;
}>();
const emit = defineEmits<{
  remember: [teachers: readonly HeadmanTeacher[]];
  add: [teacher: HeadmanTeacher, value: HeadmanSubjectInitialAssignment];
  remove: [assignment: HeadmanSubjectAssignment, from: string];
  replace: [assignment: HeadmanSubjectAssignment, teacher: HeadmanTeacher, from: string];
  unstage: [assignment: HeadmanSubjectInitialAssignment];
}>();
const query = ref(''), results = shallowRef<readonly HeadmanTeacher[]>([]), chosen = shallowRef<HeadmanTeacher | null>(null);
const searchBusy = ref(false), searchError = ref<string | null>(null), completed = ref(false), page = ref(0), more = ref(false);
const from = ref(today() < props.semester.dateFrom ? props.semester.dateFrom : today()), until = ref(''), replacingId = ref(0), issue = ref<string | null>(null);
const dates = ref<HTMLElement | null>(null), fromField = ref<HTMLElement | null>(null);
const pendingStage = shallowRef<HeadmanSubjectInitialAssignment | null>(null);
let assignmentFrom = from.value;
const target = computed(() => props.assignments.find(item => item.id === replacingId.value) ?? null);
let alive = true, revision = 0, applied = '';
let timer: ReturnType<typeof setTimeout> | undefined;
function resetSearch() {
  revision++;
  if (timer !== undefined) clearTimeout(timer);
  timer = undefined;
  results.value = []; page.value = 0; more.value = false; searchBusy.value = false; searchError.value = null; completed.value = false; applied = '';
}
function schedule() {
  resetSearch();
  if (!alive || props.locked || !query.value.trim()) return;
  searchBusy.value = true;
  timer = setTimeout(() => { timer = undefined; void search(); }, 250);
}
async function search(append = false) {
  const text = query.value.trim();
  if (!alive || props.locked || !text || (append && (searchBusy.value || !more.value || applied !== text))) return;
  if (timer !== undefined) clearTimeout(timer);
  timer = undefined;
  const run = ++revision, nextPage = append ? page.value + 1 : 0;
  const feature = props.feature, owner = `${props.semester.id}:${props.type}`;
  const current = () => alive && !props.locked && run === revision && query.value.trim() === text && feature === props.feature && owner === `${props.semester.id}:${props.type}`;
  searchBusy.value = true; searchError.value = null;
  try {
    const result = await feature.subjects.searchTeachers(text, nextPage);
    feature.assertCurrent();
    if (!current()) return;
    if (result.page !== nextPage) throw new Error('Поиск вернул другую страницу. Повтори запрос.');
    results.value = [...new Map([...(append ? results.value : []), ...result.items].map(teacher => [teacher.id, teacher])).values()];
    emit('remember', result.items);
    page.value = nextPage; applied = text; completed.value = true;
    more.value = result.totalPages !== null && result.page + 1 < result.totalPages;
  } catch (cause) { if (current()) searchError.value = message(cause); }
  finally { if (alive && run === revision) searchBusy.value = false; }
}
function select(teacher: HeadmanTeacher) {
  if (props.locked || !results.value.some(item => item.id === teacher.id)) return;
  chosen.value = teacher; issue.value = null; pendingStage.value = null;
  query.value = ''; resetSearch();
}
function teacherName(id: number) {
  return props.teachers.find(teacher => teacher.id === id)?.fullName ?? `Преподаватель №${id}`;
}
function datesValid(all: boolean) {
  return [...((all ? dates.value : fromField.value)?.querySelectorAll<HTMLInputElement>('input') ?? [])].every(input => input.reportValidity());
}
function add() {
  if (props.locked || !props.assignmentEnabled || target.value) return;
  if (!chosen.value) { issue.value = 'Выбери преподавателя в результатах поиска.'; return; }
  if (!datesValid(true)) { issue.value = 'Проверь даты назначения.'; return; }
  issue.value = null;
  const value: HeadmanSubjectInitialAssignment = { teacherId: chosen.value.id, semesterId: props.semester.id, lessonType: props.type, validFrom: from.value, validUntilExclusive: until.value || null };
  if (!props.existing) pendingStage.value = value;
  emit('add', chosen.value, value);
}
function remove(assignment: HeadmanSubjectAssignment) {
  if (props.locked || !datesValid(false) || !props.assignments.some(item => item.id === assignment.id)) return;
  emit('remove', assignment, from.value);
}
function beginReplacement(assignment: HeadmanSubjectAssignment) {
  if (props.locked || !props.assignmentEnabled || !props.assignments.some(item => item.id === assignment.id)) return;
  if (!target.value) assignmentFrom = from.value;
  replacingId.value = assignment.id; chosen.value = null; pendingStage.value = null;
  query.value = ''; resetSearch(); from.value = ''; issue.value = null;
}
function resetReplacement() {
  replacingId.value = 0; chosen.value = null; query.value = ''; resetSearch();
  from.value = assignmentFrom; issue.value = null;
}
function cancelReplacement() { if (!props.locked) resetReplacement(); }
function replace() {
  if (props.locked || !props.assignmentEnabled || !target.value) return;
  if (!chosen.value || chosen.value.id === target.value.teacherId) { issue.value = 'Выбери другого преподавателя в результатах поиска.'; return; }
  if (!from.value || !datesValid(false)) { issue.value = 'Проверь дату замены.'; return; }
  issue.value = null;
  emit('replace', target.value, chosen.value, from.value);
}
function period(from: string, until: string | null | undefined) {
  return until ? `${dateLabel(from)} — ${dateLabel(until)}` : `С ${dateLabel(from)}`;
}
watch(query, schedule, { flush: 'sync' });
watch(() => props.locked, locked => { if (locked) resetSearch(); else schedule(); }, { flush: 'sync' });
watch(() => props.staged, staged => {
  const pending = pendingStage.value;
  if (pending && staged.some(item => item.teacherId === pending.teacherId && item.semesterId === pending.semesterId && item.lessonType === pending.lessonType && item.validFrom === pending.validFrom && (item.validUntilExclusive ?? null) === (pending.validUntilExclusive ?? null))) {
    pendingStage.value = null; chosen.value = null; query.value = ''; resetSearch();
  }
});
watch(() => `${props.semester.id}:${props.type}`, () => { resetSearch(); query.value = ''; chosen.value = null; pendingStage.value = null; replacingId.value = 0; from.value = today() < props.semester.dateFrom ? props.semester.dateFrom : today(); until.value = ''; issue.value = null; }, { flush: 'sync' });
watch(() => props.assignments, () => { if (replacingId.value && (!target.value || (target.value.validUntilExclusive !== null && from.value && from.value >= target.value.validUntilExclusive))) resetReplacement(); });
onBeforeUnmount(() => { alive = false; resetSearch(); });
</script>
<template>
  <section class="headman-teacher-panel web-stack" :aria-label="`Преподаватели: ${label}`">
    <h3>{{ label }}</h3>
    <fieldset :disabled="locked" class="headman-teacher-fields">
      <header v-if="target" class="headman-replacement-heading"><h4>Замена преподавателя</h4><div class="headman-replacement-people"><strong>{{ teacherName(target.teacherId) }}</strong><span aria-hidden="true">→</span><div><strong>{{ chosen?.fullName ?? 'Выбери нового преподавателя' }}</strong><small v-if="chosen?.employeeNumber" class="web-caption">Таб. № {{ chosen.employeeNumber }}</small></div><button v-if="chosen" type="button" class="web-button web-icon-button" :disabled="locked" aria-label="Убрать нового преподавателя" @click="chosen = null"><MobileIcon name="close" /></button></div></header>
      <div class="headman-teacher-search">
        <label class="web-field">{{ target ? 'Новый преподаватель' : 'Поиск преподавателя' }}<input v-model="query" type="search" placeholder="ФИО или табельный номер" autocomplete="off" :disabled="locked" @keydown.enter.stop.prevent /></label>
        <p v-if="searchBusy" class="web-caption" role="status">Поиск преподавателей…</p>
        <p v-if="searchError" class="web-error" role="alert">{{ searchError }}</p>
        <ul v-if="results.length" class="headman-teacher-results" :aria-label="`Результаты поиска: ${label}`"><li v-for="teacher in results" :key="teacher.id"><button type="button" class="web-button" :class="{ 'web-button-selected': chosen?.id === teacher.id }" :aria-pressed="chosen?.id === teacher.id" :disabled="locked" @click="select(teacher)"><span>{{ teacher.fullName }}</span><small v-if="teacher.employeeNumber">Таб. № {{ teacher.employeeNumber }}</small></button></li></ul>
        <p v-else-if="completed && !searchBusy && !searchError" class="web-caption" role="status">Преподаватели не найдены.</p>
        <button v-if="more" type="button" class="web-button" :disabled="locked || searchBusy" @click="search(true)">Ещё преподаватели</button>
      </div>
      <div v-if="chosen && !target" class="headman-teacher-chosen"><div><strong>{{ chosen.fullName }}</strong><span v-if="chosen.employeeNumber" class="web-caption">Таб. № {{ chosen.employeeNumber }}</span></div><button type="button" class="web-button web-icon-button" :disabled="locked" :aria-label="`Убрать выбранного преподавателя: ${chosen.fullName}`" @click="chosen = null"><MobileIcon name="close" /></button></div>
      <div ref="dates" class="headman-teacher-dates" :class="{ 'is-replacement': target }"><div ref="fromField"><WebDateField v-model="from" :label="target ? 'Дата замены' : 'С даты'" :min="semester.dateFrom" :max="semester.dateTo" :today="today()" :disabled="locked" required /></div><WebDateField v-if="!target" v-model="until" label="До даты, не включая" :min="from ? addDays(from, 1) : undefined" :max="addDays(semester.dateTo, 1)" :today="today()" :disabled="locked" /></div>
      <p v-if="!assignmentEnabled" class="web-caption">Сначала сохрани новый тип занятия в предмете.</p>
      <p v-if="issue" class="web-error" role="alert">{{ issue }}</p>
      <button v-if="!target" type="button" class="web-button" :disabled="locked || !chosen || !assignmentEnabled" @click="add">Добавить преподавателя</button>
      <ul v-if="staged.length" class="headman-teacher-assignments"><li v-for="(item, index) in staged" :key="index"><strong>{{ teacherName(item.teacherId) }}</strong><span class="web-caption">{{ period(item.validFrom, item.validUntilExclusive) }}</span><button type="button" class="web-button" :disabled="locked" @click="$emit('unstage', item)">Убрать из черновика</button></li></ul>
      <ul v-if="assignments.length && !target" class="headman-teacher-assignments"><li v-for="item in assignments" :key="item.id"><strong>{{ teacherName(item.teacherId) }}</strong><span class="web-caption">{{ period(item.validFrom, item.validUntilExclusive) }}</span><div class="headman-actions"><button type="button" class="web-button" :disabled="locked" @click="remove(item)">Снять с выбранной даты</button><button type="button" class="web-button" :disabled="locked || !assignmentEnabled" :aria-pressed="replacingId === item.id" :class="{ 'web-button-selected': replacingId === item.id }" @click="beginReplacement(item)">Заменить</button></div></li></ul>
      <div v-if="target" class="headman-teacher-replacement web-stack"><p class="web-caption">{{ from ? `С ${dateLabel(from)} будущие пары перейдут новому преподавателю. Прошедшие пары сохраняются.` : 'Выбери нового преподавателя и дату замены.' }}</p><p v-if="chosen?.id === target.teacherId" class="web-error">Для замены выбери другого преподавателя.</p><div class="headman-replacement-actions"><button type="button" class="web-button" :disabled="locked" @click="cancelReplacement">Отмена</button><button type="button" class="web-button web-button-primary" :disabled="locked || !chosen || chosen.id === target.teacherId || !from || !assignmentEnabled" @click="replace">Подтвердить замену</button></div></div>
    </fieldset>
  </section>
</template>
