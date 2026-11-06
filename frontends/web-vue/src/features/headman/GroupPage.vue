<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import { message, webSession } from '../../auth/session';
import { HeadmanGroupApiError } from '../../../../mobile-core/src/features/headman-group/headman-group-client';
import { useHeadmanFeature } from './context';
import type {
  HeadmanAssistant,
  HeadmanAssistantPermission,
  HeadmanGroupMember,
  HeadmanPermissionOption,
  HeadmanLateCheckinBudget,
  HeadmanBudgetIncrease,
  HeadmanRosterFormat,
} from '../../../../mobile-core/src/features/headman-group/headman-group-client';
import { useHeadmanCredentials } from '../../../../mobile-core/src/features/headman-group/use-headman-credentials';
import {
  PermissionConfirmationFlow,
  type PermissionConfirmationState,
  type PermissionConfirmationContext,
} from '../../../../mobile-core/src/features/headman-group/permission-confirmation';
import type { HeadmanSemester } from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client';
import type { ReportDownloadFormat } from '../../../../mobile-core/src/shared/report-download-client';
import HeadmanPage from './HeadmanPage.vue';
import ReportExport from './ReportExport.vue';
import WebSelect from '../../shared/WebSelect.vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import './headman-management.pcss';
const feature = useHeadmanFeature();
const members = shallowRef<readonly HeadmanGroupMember[]>([]),
  assistants = shallowRef<readonly HeadmanAssistant[]>([]),
  options = shallowRef<readonly HeadmanPermissionOption[]>([]),
  semesters = shallowRef<readonly HeadmanSemester[]>([]);
const loading = ref(false),
  busy = ref(false),
  error = ref<string | null>(null),
  uncertain = ref(false),
  search = ref(''),
  selectedId = ref(0);
const selected = computed(() => members.value.find((member) => member.id === selectedId.value));
function roleLabel(member: HeadmanGroupMember) {
  if (member.roles?.some(grant => grant.role === 'HEADMAN' && grant.status === 'ACTIVE' && grant.groupId === feature.groupId)) return 'Староста';
  return assistants.value.some(item => item.studentId === member.id && item.active) ? 'Помощник старосты' : 'Студент';
}
function statusLabel(status: string) { return ({ ACTIVE: 'Активен', ARCHIVED: 'В архиве', BLOCKED: 'Заблокирован', PENDING: 'Ожидает активации' } as Readonly<Record<string, string>>)[status] ?? 'Статус недоступен'; }
const assistant = computed(() =>
  assistants.value.find((item) => item.studentId === selectedId.value && item.active),
);
const visible = computed(() =>
  members.value.filter((member) =>
    `${member.fullName} ${member.login}`
      .toLocaleLowerCase('ru-RU')
      .includes(search.value.toLocaleLowerCase('ru-RU')),
  ),
);
const rightsWritable = computed(
  () =>
    feature.writable() &&
    selected.value?.status === 'ACTIVE' &&
    !selected.value.roles?.some(
      (grant) =>
        grant.role === 'HEADMAN' && grant.status === 'ACTIVE' && grant.groupId === feature.groupId,
    ) &&
    String(selectedId.value) !== webSession.snapshot.value?.userId,
);
const credentialEligible = computed(
  () =>
    !selected.value?.roles?.some((grant) => grant.role === 'ADMIN' && grant.status === 'ACTIVE'),
);
const credentialConfirmation = shallowRef<{
  studentId: number;
  action: 'generate' | 'link';
  version: string;
} | null>(null);
const credentialDialog = ref<HTMLDialogElement | null>(null);
function refreshCredential() {
  if (loading.value || busy.value || budgetIntent.value || confirmation.value || credentialConfirmation.value || credentials.busy.value || credentials.loading.value || !feature.writable() || !credentialEligible.value) return;
  void credentials.refresh();
}
async function confirmCredential(action: 'generate' | 'link') {
  const value = credential.value;
  if (!value || !credentials.available.value || !selected.value || managementLocked.value) return;
  credentialConfirmation.value = {
    studentId: selected.value.id,
    action,
    version: value.credentialsVersion,
  };
  await nextTick();
  credentialDialog.value?.showModal();
}
async function commitCredential() {
  if (loading.value || busy.value || uncertain.value || budgetIntent.value || confirmation.value || credentials.busy.value || credentials.uncertain.value) return;
  const pending = credentialConfirmation.value;
  credentialConfirmation.value = null;
  credentialDialog.value?.close();
  if (pending && pending.studentId === selectedId.value)
    await credentials.perform(pending.studentId, pending.action, pending.version);
}
const draft = ref<HeadmanAssistantPermission[]>([]),
  confirmation = shallowRef<PermissionConfirmationState | null>(null),
  flow = new PermissionConfirmationFlow(),
  dialog = ref<HTMLDialogElement | null>(null);
type BudgetState = { value: HeadmanLateCheckinBudget | null; loading: boolean; error: string | null };
const budgets = shallowRef<Readonly<Record<number, BudgetState>>>({});
const budget = computed(() => budgets.value[selectedId.value]?.value ?? null);
const increase = ref(1),
  budgetIntent = shallowRef<(HeadmanBudgetIncrease & { studentId: number }) | null>(null);
const rosterFormats = shallowRef<readonly HeadmanRosterFormat[]>([]),
  format = ref<ReportDownloadFormat>('xlsx');
const budgetStorage = `rct:web:headman:budget:${webSession.identity.value}`;
let revision = 0, loadRun = 0, budgetEpoch = 0, budgetSemester = 0, activeBudgetReads = 0;
let alive = true;
const budgetTokens = new Map<number, number>();
const budgetReads = new Map<number, Promise<void>>();
const budgetQueue: Array<() => void> = [];
const credentials = useHeadmanCredentials(() => ({
  api: feature.group,
  groupId: feature.groupId,
  actorUserId: webSession.snapshot.value?.userId ?? null,
  enabled:
    feature.writable() &&
    credentialEligible.value &&
    !!selected.value,
  memberIds: selected.value ? [selected.value.id] : [],
  detailStudentId: selected.value?.id ?? null,
}));
const credential = computed(() => credentials.values.value[selectedId.value]);
const semesterId = computed(() => semesters.value.find((semester) => semester.active)?.id);
const credentialLocked = computed(() => credentials.busy.value || credentials.uncertain.value || !!credentialConfirmation.value);
const managementLocked = computed(() => loading.value || busy.value || uncertain.value || !!budgetIntent.value || credentialLocked.value || !!confirmation.value);
async function load() {
  if (busy.value || credentials.busy.value || credentialConfirmation.value || confirmation.value) return;
  const currentRun = ++loadRun;
  loading.value = true;
  error.value = null;
  try {
    await feature.initialize();
    if (!feature.isHeadman) throw new Error('Управление группой доступно старосте.');
    const [roster, helpers, catalog, periods, formats] = await Promise.all([
      feature.group.listMembers(feature.groupId),
      feature.group.listAssistants(feature.groupId),
      feature.group.listPermissionCatalog(),
      feature.subjects.listSemesters(),
      feature.group.listRosterFormats(),
    ]);
    feature.assertCurrent();
    if (!alive || currentRun !== loadRun) return;
    revision++;
    members.value = roster;
    assistants.value = helpers;
    options.value = catalog;
    semesters.value = periods;
    rosterFormats.value = formats;
    if ((semesterId.value ?? 0) !== budgetSemester) {
      budgetSemester = semesterId.value ?? 0;
      budgetEpoch++;
      budgets.value = {};
      budgetReads.clear();
    }
    const savedBudget = sessionStorage.getItem(budgetStorage);
    if (savedBudget && !budgetIntent.value) {
      const value: unknown = JSON.parse(savedBudget);
      if (
        !value ||
        typeof value !== 'object' ||
        !('studentId' in value) ||
        typeof value.studentId !== 'number' ||
        !members.value.some((member) => member.id === value.studentId) ||
        !('semesterId' in value) ||
        typeof value.semesterId !== 'number' ||
        !('increase' in value) ||
        typeof value.increase !== 'number' ||
        !Number.isSafeInteger(value.increase) ||
        value.increase <= 0 ||
        !('requestKey' in value) ||
        typeof value.requestKey !== 'string'
      )
        throw new Error('Не удалось восстановить исходное увеличение бюджета.');
      budgetIntent.value = value as HeadmanBudgetIncrease & { studentId: number };
      credentials.clear();
      selectedId.value = value.studentId;
      await readBudget(value.studentId, true);
    }
    if (!alive || currentRun !== loadRun) return;
    for (const member of roster) void readBudget(member.id, true);
    uncertain.value = false;
    if (selected.value) draft.value = [...(assistant.value?.permissions ?? [])];
  } catch (cause) {
    if (alive && currentRun === loadRun) error.value = message(cause);
  } finally {
    if (alive && currentRun === loadRun) loading.value = false;
  }
}
function choose(member: HeadmanGroupMember) {
  if (managementLocked.value) return;
  closeConfirmation();
  credentials.clear();
  selectedId.value = member.id;
  draft.value = [...(assistant.value?.permissions ?? [])];
  void readBudget(member.id, true);
}
function closeMember() {
  if (managementLocked.value) return;
  closeConfirmation();
  selectedId.value = 0;
  credentials.clear();
}
function context(): PermissionConfirmationContext | null {
  if (!selected.value) return null;
  return {
    ownerRevision: revision,
    actorUserId: webSession.snapshot.value?.userId ?? null,
    groupId: feature.groupId,
    memberId: selected.value.id,
    memberName: selected.value.fullName,
    assistantId: assistant.value?.id ?? null,
    helperIdentity: JSON.stringify(assistant.value ?? null),
    before: assistant.value?.permissions ?? [],
    after: draft.value,
  };
}
async function startConfirmation() {
  const current = context();
  if (!current || !rightsWritable.value || managementLocked.value) return;
  confirmation.value = flow.start(current, options.value);
  if (!confirmation.value) return;
  await nextTick();
  dialog.value?.showModal();
}
function closeConfirmation() {
  flow.cancel();
  confirmation.value = null;
  dialog.value?.close();
}
async function confirm() {
  const current = context();
  if (!current || !rightsWritable.value || loading.value || busy.value || budgetIntent.value || credentialLocked.value) {
    closeConfirmation();
    return;
  }
  const step = flow.confirm(current);
  if (step.kind === 'next') {
    confirmation.value = step.state;
    return;
  }
  closeConfirmation();
  if (step.kind !== 'commit') {
    error.value = 'Состав или права изменились. Проверь их заново.';
    return;
  }
  const frozen = step.snapshot;
  busy.value = true;
  error.value = null;
  let conflict = false;
  try {
    feature.assertCurrent();
    if (frozen.assistantId)
      await feature.group.updatePermissions(frozen.assistantId, frozen.after, frozen.before);
    else await feature.group.assignAssistant(feature.groupId, frozen.memberId, frozen.after);
    feature.assertCurrent();
  } catch (cause) {
    conflict = cause instanceof HeadmanGroupApiError && cause.response.status === 409;
    uncertain.value = true;
    error.value = conflict
      ? 'Права помощника уже изменились. Черновик сохранён; проверь актуальные права и подтверди свой выбор заново.'
      : `${message(cause)}. Проверь текущие права; повторная запись не отправляется.`;
  } finally {
    // Reading authority resolves the result; it never repeats assign/update/revoke.
    try {
      const latest = await feature.group.listAssistants(feature.groupId);
      feature.assertCurrent();
      assistants.value = latest;
      const actual =
        latest.find((item) => item.studentId === frozen.memberId && item.active)?.permissions ?? [];
      if (
        !conflict &&
        actual.length === frozen.after.length &&
        actual.every((code) => frozen.after.includes(code))
      )
        error.value = null;
      if (conflict) {
        const labels = actual.map(
          (code) => options.value.find((option) => option.code === code)?.label ?? code,
        );
        error.value += ` Актуальные права: ${labels.join(', ') || 'нет'}.`;
      } else draft.value = [...actual];
      uncertain.value = false;
      revision++;
    } catch {
      uncertain.value = true;
    } finally {
      busy.value = false;
    }
  }
}
function budgetState(studentId: number) {
  return budgets.value[studentId];
}
function pumpBudgets() {
  while (alive && activeBudgetReads < 3 && budgetQueue.length) budgetQueue.shift()!();
}
function storeBudget(value: HeadmanLateCheckinBudget) {
  budgetTokens.set(value.studentId, (budgetTokens.get(value.studentId) ?? 0) + 1);
  budgetReads.delete(value.studentId);
  budgets.value = { ...budgets.value, [value.studentId]: { value, loading: false, error: null } };
}
function readBudget(studentId: number, force = false): Promise<void> {
  if (!alive || !members.value.some(member => member.id === studentId)) return Promise.resolve();
  const existing = budgetReads.get(studentId);
  if (existing) return existing;
  if (!force && budgets.value[studentId]?.value) return Promise.resolve();
  const epoch = budgetEpoch, semester = budgetSemester;
  const token = (budgetTokens.get(studentId) ?? 0) + 1;
  budgetTokens.set(studentId, token);
  budgets.value = { ...budgets.value, [studentId]: { value: budgets.value[studentId]?.value ?? null, loading: true, error: null } };
  const current = () => alive && epoch === budgetEpoch && token === budgetTokens.get(studentId) && members.value.some(member => member.id === studentId);
  let finish!: () => void;
  const promise = new Promise<void>(resolve => { finish = resolve; });
  budgetReads.set(studentId, promise);
  budgetQueue.push(() => {
    if (!current()) { if (budgetReads.get(studentId) === promise) budgetReads.delete(studentId); finish(); return; }
    activeBudgetReads++;
    void (async () => {
      try {
        const value = await feature.group.getLateCheckinBudget(studentId);
        feature.assertCurrent();
        if (!current()) return;
        if (value.studentId !== studentId || (semester && value.semesterId !== semester)) throw new Error('Бюджет не подтверждён для текущего студента и семестра.');
        budgets.value = { ...budgets.value, [studentId]: { value, loading: false, error: null } };
      } catch (cause) {
        if (current()) budgets.value = { ...budgets.value, [studentId]: { value: budgets.value[studentId]?.value ?? null, loading: false, error: message(cause) } };
      } finally {
        activeBudgetReads--;
        if (budgetReads.get(studentId) === promise) budgetReads.delete(studentId);
        finish();
        pumpBudgets();
      }
    })();
  });
  pumpBudgets();
  return promise;
}
async function increaseBudget(recover = false) {
  if (!feature.writable() || loading.value || busy.value || uncertain.value || credentialLocked.value || confirmation.value || !selected.value) return;
  if (recover ? !budgetIntent.value : !!budgetIntent.value || !budget.value) return;
  const pending = budgetIntent.value ?? {
    studentId: selected.value.id,
    semesterId: budget.value!.semesterId,
    increase: increase.value,
    requestKey: crypto.randomUUID(),
  };
  try {
    sessionStorage.setItem(budgetStorage, JSON.stringify(pending));
    budgetIntent.value = pending;
  } catch {
    error.value = 'Не удалось сохранить исходный запрос. Увеличение не отправлено.';
    return;
  }
  busy.value = true;
  let confirmed = false;
  try {
    if (recover) {
      await readBudget(pending.studentId, true);
      feature.assertCurrent();
      const actual = budgets.value[pending.studentId]?.value;
      if (!actual || budgets.value[pending.studentId]?.error) throw new Error('Не удалось подтвердить текущий бюджет.');
      if (actual.semesterId !== pending.semesterId)
        throw new Error('Семестр изменился. Предыдущую команду нельзя повторить.');
      // An unrelated increase proves no result for our command. The original key below
      // retrieves the immutable receipt validated by StudentRequestService.budgetReplay.
    }
    const result = await feature.group.increaseLateCheckinBudget(pending.studentId, pending);
    feature.assertCurrent();
    storeBudget(result);
    confirmed = true;
    budgetIntent.value = null;
    sessionStorage.removeItem(budgetStorage);
    await readBudget(pending.studentId, true);
    feature.assertCurrent();
    if (budgets.value[pending.studentId]?.error) throw new Error('Не удалось обновить показанный бюджет.');
    error.value = null;
  } catch (cause) {
    error.value = confirmed ? `Увеличение подтверждено. ${message(cause)}` : `${message(cause)}. Сохранена исходная команда увеличения бюджета.`;
  } finally {
    busy.value = false;
  }
}
onBeforeUnmount(() => { alive = false; loadRun++; budgetEpoch++; credentials.clear(); while (budgetQueue.length) budgetQueue.shift()!(); });
onMounted(load);
</script>
<template>
  <HeadmanPage
    title="Группа"
    class="headman-management"
    hide-title
    :loading="loading"
    :error="error"
    @reload="load"
>
    <div class="headman-management-toolbar headman-group-toolbar"><label class="web-field">Найти студента<input v-model="search" type="search" /></label><WebSelect v-model="format" label="Формат состава" :options="rosterFormats.map(item => ({ value: item.code, label: item.label }))" /><ReportExport class="headman-group-export" :transport="feature.transport" scope="roster" :report="{ kind: 'HEADMAN_GROUP_COMPOSITION', headmanGroupComposition: { format } }" :disabled="loading" label="Скачать состав группы" /></div>
    <div class="headman-management-scroll" role="region" aria-label="Состав группы" tabindex="0"><table class="headman-management-table headman-members-table"><thead><tr><th scope="col">№</th><th scope="col">ФИО</th><th scope="col">Логин</th><th scope="col">Статус</th><th scope="col">Дополнительные права</th><th scope="col">Бюджет заявок</th><th scope="col">Действия</th></tr></thead><tbody>
      <template v-for="(member, index) in visible" :key="member.id"><tr :class="{ 'is-selected': selectedId === member.id }"><td>{{ index + 1 }}</td><th scope="row"><span>{{ member.fullName }}</span><small>{{ roleLabel(member) }}</small></th><td><span class="headman-member-login">{{ member.login }}</span></td><td>{{ statusLabel(member.status) }}</td><td><ul class="headman-member-rights"><li v-for="code in assistants.find(item => item.studentId === member.id && item.active)?.permissions ?? []" :key="code">{{ options.find(option => option.code === code)?.label ?? 'Неизвестное право' }}</li></ul><span v-if="!assistants.find(item => item.studentId === member.id && item.active)?.permissions.length" class="web-caption">—</span></td><td><span v-if="budgetState(member.id)?.error" class="web-error">{{ budgetState(member.id)?.error }}</span><span v-else-if="budgetState(member.id)?.loading" class="web-caption" role="status">Загрузка…</span><span v-else-if="budgetState(member.id)?.value" :aria-label="`Осталось заявок: ${budgetState(member.id)?.value?.remaining} из ${budgetState(member.id)?.value?.limit}`">{{ budgetState(member.id)?.value?.remaining }} / {{ budgetState(member.id)?.value?.limit }}</span><span v-else class="web-caption">Недоступен</span></td><td><button type="button" class="web-button web-icon-button" :aria-label="`Настройки: ${member.fullName}`" :aria-expanded="selectedId === member.id" :disabled="managementLocked" @click="choose(member)"><MobileIcon name="settings" /></button></td></tr>
      <tr v-if="selectedId === member.id" class="headman-member-reveal"><td colspan="7">    <section
      v-if="selected"
      class="headman-member-detail web-stack"
      :aria-label="`Настройки: ${selected.fullName}`"
      ><button type="button" class="web-button web-icon-button headman-member-close" aria-label="Закрыть настройки студента" :disabled="managementLocked" @click="closeMember"><MobileIcon name="close" /></button>
      <div class="headman-member-panels">
      <form
        class="web-stack headman-member-panel"
        @submit.prevent="startConfirmation"
        ><fieldset :disabled="managementLocked || !rightsWritable"
          ><legend>Права помощника</legend
          ><div class="headman-list"
            ><label
              v-for="option in options"
              :key="option.code"
              ><input
                v-model="draft"
                type="checkbox"
                :value="option.code"
              />
              {{ option.label }}</label
            ></div
          ></fieldset
        ><button
          class="web-button"
          :disabled="managementLocked || !rightsWritable"
          >Сохранить права</button
        ><p
          v-if="uncertain"
          class="web-notice"
          >Состояние прав неизвестно.
          <button
            class="web-button"
            :disabled="busy"
            @click="load"
            >Проверить права</button
          ></p
        ></form
      >
      <div
        class="web-stack headman-member-panel"
        ><h3>Доступ</h3><p v-if="!feature.writable() || !credentialEligible" class="web-caption">Доступ к реквизитам недоступен.</p><p v-if="credentials.loading.value">Проверка доступа…</p
        ><p
          v-if="credentials.error.value"
          class="web-error"
          role="alert"
          >{{ credentials.error.value }}</p
        ><template v-if="credential"
          ><p v-if="credential.password"
            >Доступный пароль: <output>{{ credential.password }}</output></p
          ><p v-else>{{
            credential.state === 'STUDENT_CHANGED'
              ? 'Студент изменил пароль. Старый пароль недоступен.'
              : 'Пароль недоступен.'
          }}</p
          ><p class="web-caption">Новый пароль заменит текущий доступ студента.</p
          ><button
            class="web-button"
            :disabled="!credentials.available.value || managementLocked"
            @click="confirmCredential('generate')"
            >Сгенерировать новый пароль</button
          ><button
            class="web-button"
            :disabled="!credentials.available.value || managementLocked"
            @click="confirmCredential('link')"
            >Выдать ссылку сброса</button
          ></template
        ><p v-if="credentials.link.value"
          ><a
            :href="credentials.link.value.url"
            rel="noreferrer"
            >Ссылка восстановления</a
          >
          · действует до {{ new Date(credentials.link.value.expiresAt).toLocaleString('ru-RU') }}</p
        ><button
          v-if="credentials.uncertain.value || !credential"
          class="web-button"
          :disabled="loading || busy || !!budgetIntent || !!confirmation || !!credentialConfirmation || credentials.busy.value || credentials.loading.value || !feature.writable() || !credentialEligible"
          @click="refreshCredential"
          >Проверить данные доступа</button
        ></div
      >
      <form
        class="web-stack headman-member-panel"
        @submit.prevent="increaseBudget()"
        ><h3>Бюджет заявок</h3><p v-if="budgetState(selected.id)?.loading" class="web-caption" role="status">Загрузка бюджета…</p><p v-else-if="budgetState(selected.id)?.error" class="web-error">{{ budgetState(selected.id)?.error }}</p><p v-else-if="budget" :aria-label="`Осталось заявок: ${budget.remaining} из ${budget.limit}`">{{ budget.remaining }} / {{ budget.limit }}</p><p v-else class="web-caption">Бюджет недоступен.</p><template v-if="budget && !budgetIntent"
          ><label class="web-field"
            >Увеличить лимит на<input
              v-model.number="increase"
              :disabled="managementLocked"
              type="number"
              min="1"
              max="2147483647"
              required /></label
          ><button
            class="web-button"
            :disabled="managementLocked || !feature.writable()"
            >Увеличить бюджет</button
          ></template
        ><button
          v-else-if="budgetIntent"
          type="button"
          class="web-button"
          :disabled="busy || credentialLocked || !!confirmation"
          @click="increaseBudget(true)"
          >Проверить сохранённое увеличение</button
        ></form
      >
      </div>
    </section>
</td></tr></template>
    </tbody></table></div><p v-if="!loading && !error && !visible.length" class="web-caption">Студентов по этому запросу нет.</p>
    <dialog
      ref="credentialDialog"
      class="headman-dialog"
      @cancel="credentialConfirmation = null"
      ><div
        v-if="credentialConfirmation"
        class="web-stack"
        ><h2>{{
          credentialConfirmation.action === 'generate'
            ? 'Сбросить пароль?'
            : 'Выдать ссылку сброса?'
        }}</h2
        ><p>{{ selected?.fullName }}</p
        ><p>{{
          credentialConfirmation.action === 'generate'
            ? 'Будет создан новый пароль. Текущие сеансы студента завершатся. Пароль останется доступен до смены студентом.'
            : 'Предыдущая ссылка перестанет действовать. Текущий пароль и сеансы сохранятся до использования новой ссылки.'
        }}</p
        ><div class="headman-actions"
          ><button
            class="web-button"
            @click="
              credentialConfirmation = null;
              credentialDialog?.close();
            "
            >Отмена</button
          ><button
            class="web-button web-button-primary"
            @click="commitCredential"
            >Подтвердить</button
          ></div
        ></div
      ></dialog
    >
    <dialog
      ref="dialog"
      class="headman-dialog"
      @cancel="closeConfirmation"
      ><div
        v-if="confirmation"
        class="web-stack"
        ><h2>{{ confirmation.step.kind === 'revoke' ? 'Отозвать права?' : 'Выдать права?' }}</h2
        ><p>{{ confirmation.snapshot.memberName }}</p
        ><ul
          ><li
            v-for="right in confirmation.step.rights"
            :key="right.code"
            >{{ right.label }}</li
          ></ul
        ><div class="headman-actions"
          ><button
            class="web-button"
            @click="closeConfirmation"
            >Отмена</button
          ><button
            class="web-button web-button-primary"
            @click="confirm"
            >Подтвердить</button
          ></div
        ></div
      ></dialog
    >
  </HeadmanPage>
</template>
