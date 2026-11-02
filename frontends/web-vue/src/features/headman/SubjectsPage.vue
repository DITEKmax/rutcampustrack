<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue';
import { useWebLiveRefresh } from '../../app/live';
import { message, webSession } from '../../auth/session';
import { useHeadmanFeature, today, lessonTypes, dateLabel, addDays } from './context';
import type {
  HeadmanSubject,
  HeadmanSemester,
  HeadmanSubjectType,
  HeadmanTeacher,
  HeadmanSubjectInitialAssignment,
  HeadmanSubjectAssignment,
  HeadmanSemesterProgress,
  AssignmentReplacementInput,
  AssignmentReplacementResponse,
} from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client';
import { HeadmanSubjectsApiError } from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client';
import {
  parseSubjectEditIntent,
  subjectEditStorageKey,
  type SubjectEditIntent,
} from '../../../../mobile-core/src/features/headman-subjects/subject-edit-intent';
import { createSubjectSaveFlow } from '../../../../mobile-core/src/features/headman-subjects/subject-save-flow';
import HeadmanPage from './HeadmanPage.vue';
import { assertReplacementWire, replacementDraftInput } from './subject-replacement-state';
import SubjectDeletion from './SubjectDeletion.vue';
import WebSelect from '../../shared/WebSelect.vue';
import TeacherAssignmentPanel from './TeacherAssignmentPanel.vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import './headman-management.pcss';
const feature = useHeadmanFeature(),
  session = webSession.snapshot.value!;
const loading = ref(false),
  busy = ref(false),
  error = ref<string | null>(null),
  editor = ref(false),
  denied = ref(false);
const subjects = shallowRef<readonly HeadmanSubject[]>([]),
  semesters = shallowRef<readonly HeadmanSemester[]>([]),
  teachers = shallowRef<readonly HeadmanTeacher[]>([]),
  progress = shallowRef<HeadmanSemesterProgress | null>(null);
const semesterId = ref(0),
  selected = shallowRef<HeadmanSubject | null>(null),
  deleting = ref(0);
const semester = computed(() => semesters.value.find((item) => item.id === semesterId.value));
const draft = reactive({
  name: '',
  types: ['LECTURE'] as HeadmanSubjectType[],
  initial: [] as HeadmanSubjectInitialAssignment[],
});
const enabledTypes = computed(() => (['LECTURE', 'PRACTICE', 'LAB'] as const).filter(type => draft.types.includes(type)));
const editorRevision = ref(0);
let assignmentOperation = false;
const intent = shallowRef<SubjectEditIntent | null>(null),
  selectedRecoveryId = ref(0);
interface Replacement {
  assignmentId: number;
  subjectId: number;
  input: AssignmentReplacementInput;
  receipt: AssignmentReplacementResponse | null;
}
const replacement = shallowRef<Replacement | null>(null);
const storageKey = subjectEditStorageKey(session.userId, feature.groupId),
  replacementKey = `${storageKey}:web-replacement`;
const writable = computed(() => feature.writable() && !!semester.value?.active && !denied.value);
const locked = computed(() => loading.value || busy.value || !!intent.value || !!replacement.value);
let alive = true;
function rememberTeachers(values: readonly HeadmanTeacher[]) {
  teachers.value = [...new Map([...teachers.value, ...values].map(teacher => [teacher.id, teacher])).values()];
}
function teacherName(id: number) {
  return teachers.value.find((teacher) => teacher.id === id)?.fullName ?? `Преподаватель №${id}`;
}
function persist(next: SubjectEditIntent | null) {
  try {
    feature.assertCurrent();
    if (next) {
      const checked = parseSubjectEditIntent(JSON.stringify(next), session.userId, feature.groupId);
      if (!checked) throw new Error('Состав изменения предмета не прошёл проверку.');
      sessionStorage.setItem(storageKey, JSON.stringify(checked));
      intent.value = checked;
    } else {
      sessionStorage.removeItem(storageKey);
      intent.value = null;
    }
    return true;
  } catch (cause) {
    error.value = message(cause);
    return false;
  }
}
async function read(): Promise<boolean> {
  const values = await feature.subjects.listSubjects(feature.groupId);
  feature.assertCurrent();
  subjects.value = values;
  const ids = [...new Set(values.flatMap((subject) => subject.teacherIds))];
  const names: HeadmanTeacher[] = [];
  for (let offset = 0; offset < ids.length; offset += 100) {
    names.push(...(await feature.subjects.resolveTeachers(ids.slice(offset, offset + 100))));
    feature.assertCurrent();
  }
  teachers.value = [
    ...new Map([...teachers.value, ...names].map((teacher) => [teacher.id, teacher])).values(),
  ];
  if (semester.value) {
    const result = await feature.subjects.semesterProgress(feature.groupId, semester.value);
    feature.assertCurrent();
    progress.value = result;
  }
  if (selected.value)
    selected.value = values.find((subject) => subject.id === selected.value?.id) ?? null;
  return true;
}
const flow = createSubjectSaveFlow({
  api: () => feature.subjects,
  actor: () => session.userId,
  group: () => feature.groupId,
  available: () => writable.value,
  disposed: () => !alive,
  subjects: () => subjects.value,
  intent: () => intent.value,
  persist,
  read,
  busy: (value) => {
    busy.value = value;
  },
  error: (value) => {
    error.value = value;
  },
  denied: (value) => {
    denied.value = value;
  },
  completed: () => {
    if (!assignmentOperation) editor.value = false;
    assignmentOperation = false;
  },
  failed: () => undefined,
});
async function load() {
  loading.value = true;
  try {
    await feature.initialize();
    if (!feature.isHeadman) throw new Error('Управление предметами доступно старосте.');
    if (!semesters.value.length) {
      const periods = await feature.subjects.listSemesters();
      feature.assertCurrent();
      semesters.value = periods;
      semesterId.value = periods.find((item) => item.active)?.id ?? periods[0]?.id ?? 0;
      const raw = sessionStorage.getItem(storageKey);
      if (raw) {
        intent.value = parseSubjectEditIntent(raw, session.userId, feature.groupId);
        if (!intent.value) throw new Error('Незавершённое изменение предмета повреждено.');
      }
      const stored = sessionStorage.getItem(replacementKey);
      if (stored) {
        const restored = JSON.parse(stored) as Replacement;
        try {
          assertReplacementWire(restored.input, restored.assignmentId);
          replacement.value = restored;
        } catch (cause) {
          if (restored.receipt) {
            replacement.value = restored;
            throw cause;
          }
          sessionStorage.removeItem(replacementKey);
          error.value =
            'Неотправленная замена имела некорректные поля. Выбери назначение и дату заново.';
        }
      }
    }
    await read();
  } catch (cause) {
    error.value = message(cause);
  } finally {
    loading.value = false;
  }
}
function open(subject: HeadmanSubject | null = null) {
  if (locked.value || !writable.value) return;
  editorRevision.value++;
  assignmentOperation = false;
  selected.value = subject;
  editor.value = true;
  draft.name = subject?.name ?? '';
  draft.types = [...(subject?.lessonTypes ?? ['LECTURE'])];
  draft.initial = [];
  error.value = null;
}
function validDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(`${value}T12:00:00Z`)) && new Date(`${value}T12:00:00Z`).toISOString().slice(0, 10) === value;
}
function assignment(teacher: HeadmanTeacher, value: HeadmanSubjectInitialAssignment): HeadmanSubjectInitialAssignment {
  const until = value.validUntilExclusive ?? null;
  if (!semester.value || value.semesterId !== semester.value.id || !draft.types.includes(value.lessonType) || value.teacherId !== teacher.id || !teachers.value.some(item => item.id === teacher.id)) throw new Error('Выбери преподавателя и тип занятия.');
  if (!validDate(value.validFrom) || value.validFrom < (today() > semester.value.dateFrom ? today() : semester.value.dateFrom) || value.validFrom > semester.value.dateTo || (until !== null && (!validDate(until) || until <= value.validFrom || until > addDays(semester.value.dateTo, 1)))) throw new Error('Проверь период назначения в семестре.');
  return { ...value, validUntilExclusive: until };
}
function removeInitial(value: HeadmanSubjectInitialAssignment) {
  if (locked.value || !writable.value) return;
  draft.initial = draft.initial.filter(item => JSON.stringify(item) !== JSON.stringify(value));
}
watch(() => [...draft.types], types => { draft.initial = draft.initial.filter(item => types.includes(item.lessonType)); }, { flush: 'sync' });
function plan(
  subject: HeadmanSubject | null,
  assignments: readonly HeadmanSubjectInitialAssignment[] = [],
  step = 0,
): SubjectEditIntent {
  return {
    version: 1,
    actorUserId: session.userId,
    groupId: feature.groupId,
    editId: crypto.randomUUID(),
    subjectId: subject?.id ?? null,
    createdId: null,
    name: subject?.name ?? draft.name,
    lessonTypes: subject?.lessonTypes ?? draft.types,
    assignments,
    removals: [],
    pendingAdoptions: [],
    step,
    unknown: false,
  };
}
async function save() {
  if (locked.value || !writable.value) return;
  if (!draft.name.trim() || !draft.types.length) {
    error.value = 'Укажи название и хотя бы один тип занятия.';
    return;
  }
  assignmentOperation = false;
  const value = plan(selected.value, selected.value ? [] : draft.initial.filter(item => draft.types.includes(item.lessonType)));
  if (persist({ ...value, name: draft.name, lessonTypes: [...draft.types] })) await flow.send(true);
}
async function addTeacher(teacher: HeadmanTeacher, input: HeadmanSubjectInitialAssignment) {
  if (locked.value || !writable.value || !editor.value) return;
  try {
    const value = assignment(teacher, input);
    if (!selected.value) {
      if (!draft.initial.some(item => JSON.stringify(item) === JSON.stringify(value))) draft.initial.push(value);
      return;
    }
    if (!selected.value.lessonTypes.includes(value.lessonType)) throw new Error('Сначала сохрани новый тип занятия в предмете.');
    if (persist(plan(selected.value, [value], 1))) { assignmentOperation = true; await flow.send(true); }
  } catch (cause) { error.value = message(cause); }
}
function ownedAssignment(value: HeadmanSubjectAssignment) {
  return selected.value?.assignments.find(item => item.id === value.id && item.teacherId === value.teacherId && item.lessonType === value.lessonType && item.semesterId === semesterId.value && draft.types.includes(item.lessonType));
}
async function removeTeacher(value: HeadmanSubjectAssignment, from: string) {
  if (!selected.value || locked.value || !writable.value || !editor.value) return;
  const target = ownedAssignment(value);
  if (!target || !semester.value || !validDate(from) || from < semester.value.dateFrom || from > semester.value.dateTo) { error.value = 'Проверь назначение и дату завершения.'; return; }
  if (!window.confirm(`Завершить назначение «${teacherName(target.teacherId)}» с ${dateLabel(from)}? Прошедшая история сохранится.`)) return;
  const current = plan(selected.value, [], 1);
  if (persist({ ...current, removals: [{ subjectId: selected.value.id, teacherId: target.teacherId, assignmentId: target.id, validUntilExclusive: from, requestKey: crypto.randomUUID() }] })) { assignmentOperation = true; await flow.send(true); }
}
function replaceTeacher(value: HeadmanSubjectAssignment, teacher: HeadmanTeacher, from: string) {
  const target = ownedAssignment(value);
  if (target) void replace(false, { assignment: target, teacher, from });
}
function persistReplacement(value: Replacement | null) {
  feature.assertCurrent();
  if (value) sessionStorage.setItem(replacementKey, JSON.stringify(value));
  else sessionStorage.removeItem(replacementKey);
  replacement.value = value;
}
async function replace(recover = false, selection?: { assignment: HeadmanSubjectAssignment; teacher: HeadmanTeacher; from: string }) {
  if (loading.value || busy.value || !writable.value || intent.value) return;
  let pending = replacement.value;
  if (!pending) {
    if (!selection || !editor.value || !ownedAssignment(selection.assignment)) return;
    let input: AssignmentReplacementInput;
    try {
      const payload = replacementDraftInput(
        selected.value,
        semester.value,
        feature.groupId,
        selection.assignment.id,
        selection.teacher.id,
        teachers.value.map((teacher) => teacher.id),
        selection.from,
        today(),
      );
      input = { ...payload, requestKey: crypto.randomUUID() };
      assertReplacementWire(input, selection.assignment.id);
    } catch (cause) {
      error.value = message(cause);
      return;
    }
    if (!selected.value) return;
    pending = {
      subjectId: selected.value.id,
      assignmentId: selection.assignment.id,
      input,
      receipt: null,
    };
    if (
      !window.confirm(
        `Заменить преподавателя с ${dateLabel(selection.from)}? Будущие пары перейдут новому назначению, прошедшая история сохранится.`,
      )
    )
      return;
    persistReplacement(pending);
  }
  busy.value = true;
  try {
    if (recover) await read();
    const receipt = pending.receipt
      ? await feature.subjects.getReplacementStatus(pending.receipt.operationId)
      : await feature.subjects.replaceAssignment(pending.assignmentId, pending.input);
    feature.assertCurrent();
    if (
      receipt.sourceAssignmentId !== pending.assignmentId ||
      receipt.targetTeacherId !== Number(pending.input.replacementTeacherId) ||
      receipt.subjectId !== pending.subjectId ||
      receipt.groupId !== feature.groupId
    )
      throw new Error('Сервер подтвердил другую замену.');
    persistReplacement({ ...pending, receipt });
    if (receipt.state === 'COMMITTED') {
      persistReplacement(null);
      await read();
      error.value = null;
    } else error.value = 'Замена принята, но ещё не завершена. Проверь исходную операцию.';
  } catch (cause) {
    if (
      !recover &&
      cause instanceof HeadmanSubjectsApiError &&
      [400, 403, 409, 422].includes(cause.response.status)
    )
      persistReplacement(null);
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
onBeforeUnmount(() => {
  alive = false;
  flow.reset();
});
useWebLiveRefresh(load, {
  blocked: () => loading.value || locked.value || editor.value || !!deleting.value,
});
</script>
<template>
  <HeadmanPage
    title="Предметы"
    class="headman-management"
    hide-title
    :loading="loading"
    :error="error"
    @reload="load"
>
    <div class="headman-management-toolbar headman-subject-toolbar"><WebSelect v-model="semesterId" label="Семестр" :options="semesters.map(period => ({ value: period.id, label: period.name + (period.active ? '' : ' · архив') }))" :disabled="locked || editor || !!deleting" @change="load" /><button type="button" class="web-button web-button-primary" :disabled="locked || !writable" @click="open()">Добавить предмет</button></div>
    <section
      v-if="intent"
      class="web-notice web-stack"
      ><p
        >Сохранено незавершённое изменение «{{ intent.name }}». Подтверждено шагов:
        {{ intent.step }}.</p
      ><template v-if="intent.subjectId === null && intent.createdId === null && intent.unknown"
        ><WebSelect v-model="selectedRecoveryId" label="Проверить конкретный созданный предмет" :options="[{ value: 0, label: 'Выбери результат на сервере' }, ...subjects.map(subject => ({ value: subject.id, label: subject.name + ' · №' + subject.id }))]" :disabled="busy" /><button
          class="web-button"
          :disabled="busy || !selectedRecoveryId"
          @click="flow.reconcile(selectedRecoveryId, true)"
          >Подтвердить выбранный результат</button
        ></template
      ><button
        class="web-button"
        :disabled="busy"
        @click="flow.reconcile()"
        >Проверить состояние</button
      ><button
        v-if="flow.canContinue()"
        class="web-button"
        :disabled="busy"
        @click="flow.send()"
        >Продолжить исходное сохранение</button
      ><button
        v-if="intent.rejected"
        class="web-button"
        :disabled="busy"
        @click="flow.cancelRejected()"
        >Отменить отклонённую часть</button
      ></section
    ><p
      v-if="replacement"
      class="web-notice"
      >Замена преподавателя сохранена.
      <button
        class="web-button"
        :disabled="busy"
        @click="replace(true)"
        >Проверить замену</button
      ></p
    >
    <div class="headman-management-scroll" tabindex="0" role="region" aria-label="Предметы группы"><table class="headman-management-table headman-subjects-table"><colgroup><col /><col /><col /><col /><col class="headman-subject-progress-column" /><col class="headman-subject-action" /><col class="headman-subject-action" /></colgroup><thead><tr><th scope="col">№</th><th scope="col">Предмет</th><th scope="col">Тип занятия</th><th scope="col">Преподаватели</th><th scope="col">Пройдено / всего</th><th scope="colgroup" colspan="2">Действия</th></tr></thead><tbody>
      <template v-for="(subject, index) in subjects" :key="subject.id"><tr v-for="(type, typeIndex) in subject.lessonTypes" :key="type" :class="{ 'is-selected': editor && selected?.id === subject.id }"><td v-if="typeIndex === 0" :rowspan="subject.lessonTypes.length">{{ index + 1 }}</td><th v-if="typeIndex === 0" scope="rowgroup" :rowspan="subject.lessonTypes.length">{{ subject.name }}</th><td>{{ lessonTypes[type] }}</td><td>{{ subject.assignments.filter(assignment => assignment.semesterId === semesterId && assignment.lessonType === type && (!assignment.validUntilExclusive || assignment.validUntilExclusive > today())).map(assignment => teacherName(assignment.teacherId)).join(' · ') || 'Нет назначения' }}</td>
        <td class="headman-subject-progress-cell"><div v-if="progress?.items.some(item => item.subjectId === subject.id && item.lessonType === type)" class="headman-subject-progress"><div><span>{{ progress.items.find(item => item.subjectId === subject.id && item.lessonType === type)!.done }}</span><small>{{ progress.items.find(item => item.subjectId === subject.id && item.lessonType === type)!.total }}</small></div><progress v-if="progress.items.find(item => item.subjectId === subject.id && item.lessonType === type)!.total > 0" :value="progress.items.find(item => item.subjectId === subject.id && item.lessonType === type)!.done" :max="progress.items.find(item => item.subjectId === subject.id && item.lessonType === type)!.total" :aria-label="`${subject.name}, ${lessonTypes[type]}: пройдено ${progress.items.find(item => item.subjectId === subject.id && item.lessonType === type)!.done} из ${progress.items.find(item => item.subjectId === subject.id && item.lessonType === type)!.total}`" /></div><span v-else class="web-caption">Нет данных</span></td>
        <td v-if="typeIndex === 0" :rowspan="subject.lessonTypes.length" class="headman-subject-action"><button type="button" class="web-button web-icon-button" :aria-label="`Изменить предмет ${subject.name}`" :title="`Изменить ${subject.name}`" :disabled="locked || !writable" @click="open(subject)"><MobileIcon name="edit" /></button></td><td v-if="typeIndex === 0" :rowspan="subject.lessonTypes.length" class="headman-subject-action"><button type="button" class="web-button web-icon-button" :aria-label="`Удалить предмет ${subject.name}`" :title="`Удалить ${subject.name}`" :disabled="locked || !writable" @click="deleting = subject.id; editor = false"><MobileIcon name="trash" /></button></td>
      </tr></template>
    </tbody></table></div><p v-if="!loading && !error && !subjects.length" class="web-caption">Предметов пока нет.</p>
    <section v-if="editor && semester" class="headman-panel headman-subject-editor web-stack" aria-label="Редактор предмета">
      <h2>{{ selected ? 'Изменить предмет' : 'Добавить предмет' }}</h2>
      <p v-if="!selected" class="web-caption">Добавленные назначения сохранятся вместе с предметом.</p>
      <fieldset :disabled="locked" class="headman-subject-top"><label class="web-field">Название предмета<input v-model="draft.name" required maxlength="255" @keydown.enter.prevent /></label><fieldset><legend>Типы занятий</legend><div class="headman-subject-types"><label v-for="(label, code) in lessonTypes" :key="code" class="headman-subject-type"><input v-model="draft.types" type="checkbox" :value="code" /><span>{{ label }}</span></label></div></fieldset></fieldset>
      <div class="headman-subject-panels" :class="`has-${draft.types.length}`">
        <TeacherAssignmentPanel v-for="type in enabledTypes" :key="`${editorRevision}:${type}`" :feature="feature" :semester="semester" :type="type" :label="lessonTypes[type] ?? type" :teachers="teachers" :assignments="selected?.assignments.filter(item => item.semesterId === semesterId && item.lessonType === type) ?? []" :staged="draft.initial.filter(item => item.lessonType === type)" :existing="!!selected" :locked="locked || !writable" :assignment-enabled="!selected || selected.lessonTypes.includes(type)" @remember="rememberTeachers" @add="addTeacher" @remove="removeTeacher" @replace="replaceTeacher" @unstage="removeInitial" />
      </div>
      <div class="headman-actions headman-subject-form-actions"><button type="button" class="web-button" :disabled="locked" @click="editor = false">Отмена</button><button type="button" class="web-button web-button-primary" :disabled="locked || !writable" @click="save">Сохранить предмет</button></div>
    </section>
    <SubjectDeletion
      v-if="deleting"
      :key="deleting"
      :feature="feature"
      :subject-id="deleting"
      :semester-id="semesterId"
      @completed="
        deleting = 0;
        load();
      "
      @close="deleting = 0"
    />
  </HeadmanPage>
</template>
