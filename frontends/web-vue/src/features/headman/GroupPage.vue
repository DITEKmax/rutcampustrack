<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef } from 'vue';
import { message, webSession } from '../../auth/session';
import { HeadmanGroupApiError } from '../../../../mobile-core/src/features/headman-group/headman-group-client';
import { useHeadmanFeature, dateLabel } from './context';
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
import StudentDetail from './StudentDetail.vue';
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
  selectedId = ref(0),
  selectedTab = ref<'rights' | 'access' | 'budget' | 'statistics'>('rights');
const selected = computed(() => members.value.find((member) => member.id === selectedId.value));
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
async function confirmCredential(action: 'generate' | 'link') {
  const value = credential.value;
  if (!value || !credentials.available.value || !selected.value) return;
  credentialConfirmation.value = {
    studentId: selected.value.id,
    action,
    version: value.credentialsVersion,
  };
  await nextTick();
  credentialDialog.value?.showModal();
}
async function commitCredential() {
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
const budget = shallowRef<HeadmanLateCheckinBudget | null>(null),
  increase = ref(1),
  budgetIntent = shallowRef<(HeadmanBudgetIncrease & { studentId: number }) | null>(null);
const rosterFormats = shallowRef<readonly HeadmanRosterFormat[]>([]),
  format = ref<ReportDownloadFormat>('xlsx');
const budgetStorage = `rct:web:headman:budget:${webSession.identity.value}`;
let revision = 0;
const credentials = useHeadmanCredentials(() => ({
  api: feature.group,
  groupId: feature.groupId,
  actorUserId: webSession.snapshot.value?.userId ?? null,
  enabled:
    feature.writable() &&
    credentialEligible.value &&
    !!selected.value &&
    selectedTab.value === 'access',
  memberIds: selected.value ? [selected.value.id] : [],
}));
const credential = computed(() => credentials.values.value[selectedId.value]);
const semesterId = computed(() => semesters.value.find((semester) => semester.active)?.id);
async function load() {
  if (busy.value) return;
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
    revision++;
    members.value = roster;
    assistants.value = helpers;
    options.value = catalog;
    semesters.value = periods;
    rosterFormats.value = formats;
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
      selectedId.value = value.studentId;
      selectedTab.value = 'budget';
      await readBudget();
    }
    uncertain.value = false;
    if (selected.value) draft.value = [...(assistant.value?.permissions ?? [])];
  } catch (cause) {
    error.value = message(cause);
  } finally {
    loading.value = false;
  }
}
function choose(member: HeadmanGroupMember) {
  if (busy.value || uncertain.value || budgetIntent.value) return;
  selectedId.value = member.id;
  selectedTab.value = 'rights';
  draft.value = [...(assistant.value?.permissions ?? [])];
  budget.value = null;
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
  if (!current || !rightsWritable.value || busy.value || uncertain.value) return;
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
  if (!current || !rightsWritable.value) {
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
async function readBudget() {
  if (!selected.value) return;
  busy.value = true;
  try {
    const value = await feature.group.getLateCheckinBudget(selected.value.id);
    feature.assertCurrent();
    budget.value = value;
    selectedTab.value = 'budget';
  } catch (cause) {
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
async function increaseBudget(recover = false) {
  if (!feature.writable() || busy.value || !selected.value || !budget.value) return;
  if (!recover && budgetIntent.value) return;
  const pending = budgetIntent.value ?? {
    studentId: selected.value.id,
    semesterId: budget.value.semesterId,
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
  try {
    if (recover) {
      const actual = await feature.group.getLateCheckinBudget(pending.studentId);
      feature.assertCurrent();
      budget.value = actual;
      if (actual.semesterId !== pending.semesterId)
        throw new Error('Семестр изменился. Предыдущую команду нельзя повторить.');
      // An unrelated increase proves no result for our command. The original key below
      // retrieves the immutable receipt validated by StudentRequestService.budgetReplay.
    }
    const result = await feature.group.increaseLateCheckinBudget(pending.studentId, pending);
    feature.assertCurrent();
    budget.value = result;
    budgetIntent.value = null;
    sessionStorage.removeItem(budgetStorage);
    const latest = await feature.group.getLateCheckinBudget(pending.studentId);
    feature.assertCurrent();
    budget.value = latest;
    error.value = null;
  } catch (cause) {
    error.value = `${message(cause)}. Сохранена исходная команда увеличения бюджета.`;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <HeadmanPage
    title="Группа"
    :loading="loading"
    :error="error"
    @reload="load"
    ><div class="headman-toolbar"
      ><label class="web-field"
        >Найти студента<input
          v-model="search"
          type="search" /></label
      ><label class="web-field"
        >Формат состава<select v-model="format"
          ><option
            v-for="item in rosterFormats"
            :key="item.code"
            :value="item.code"
            >{{ item.label }}</option
          ></select
        ></label
      ><ReportExport
        :transport="feature.transport"
        scope="roster"
        :report="{ kind: 'HEADMAN_GROUP_COMPOSITION', headmanGroupComposition: { format } }"
        :disabled="loading"
        label="Скачать состав группы"
    /></div>
    <div class="headman-table-scroll"
      ><table class="headman-table"
        ><thead
          ><tr
            ><th>Студент</th><th>Логин</th><th>Статус</th><th>Полномочия</th><th>Действия</th></tr
          ></thead
        ><tbody
          ><tr
            v-for="member in visible"
            :key="member.id"
            ><th scope="row">{{ member.fullName }}</th
            ><td>{{ member.login }}</td
            ><td>{{ member.status }}</td
            ><td>{{
              assistants
                .find((item) => item.studentId === member.id && item.active)
                ?.permissions.map(
                  (code) => options.find((option) => option.code === code)?.label ?? code,
                )
                .join(' · ') || 'Без прав помощника'
            }}</td
            ><td
              ><button
                class="web-button"
                :disabled="busy || uncertain || !!budgetIntent"
                @click="choose(member)"
                >Подробнее</button
              ></td
            ></tr
          ></tbody
        ></table
      ></div
    ><p v-if="!loading && !visible.length">Студентов по этому запросу нет.</p>
    <section
      v-if="selected"
      class="headman-panel web-stack"
      ><div class="headman-heading"
        ><h2>{{ selected.fullName }}</h2
        ><button
          class="web-button"
          :disabled="busy || uncertain || !!budgetIntent"
          @click="
            selectedId = 0;
            credentials.clear();
          "
          >Закрыть</button
        ></div
      ><div class="headman-actions"
        ><button
          class="web-button"
          :disabled="busy || !!budgetIntent"
          @click="selectedTab = 'rights'"
          >Права помощника</button
        ><button
          class="web-button"
          :disabled="busy || !!budgetIntent || !feature.writable() || !credentialEligible"
          @click="selectedTab = 'access'"
          >Доступ</button
        ><button
          class="web-button"
          :disabled="busy || !!budgetIntent"
          @click="readBudget"
          >Бюджет заявок</button
        ><button
          class="web-button"
          :disabled="busy || !!budgetIntent"
          @click="selectedTab = 'statistics'"
          >Статистика</button
        ></div
      >
      <form
        v-if="selectedTab === 'rights'"
        class="web-stack"
        @submit.prevent="startConfirmation"
        ><fieldset :disabled="busy || uncertain || !rightsWritable"
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
          :disabled="busy || uncertain || !rightsWritable"
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
        v-if="selectedTab === 'access'"
        class="web-stack"
        ><p v-if="credentials.loading.value">Проверка доступа…</p
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
            :disabled="!credentials.available.value"
            @click="confirmCredential('generate')"
            >Сгенерировать новый пароль</button
          ><button
            class="web-button"
            :disabled="!credentials.available.value"
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
          :disabled="credentials.busy.value || credentials.loading.value"
          @click="credentials.refresh"
          >Проверить данные доступа</button
        ></div
      >
      <form
        v-if="selectedTab === 'budget' && budget"
        class="web-stack"
        @submit.prevent="increaseBudget()"
        ><p
          >Семестр №{{ budget.semesterId }} · использовано {{ budget.used }} из {{ budget.limit }} ·
          осталось {{ budget.remaining }}</p
        ><template v-if="!budgetIntent"
          ><label class="web-field"
            >Увеличить лимит на<input
              v-model.number="increase"
              type="number"
              min="1"
              max="2147483647"
              required /></label
          ><button
            class="web-button"
            :disabled="busy || !feature.writable()"
            >Увеличить бюджет</button
          ></template
        ><button
          v-else
          type="button"
          class="web-button"
          :disabled="busy"
          @click="increaseBudget(true)"
          >Проверить сохранённое увеличение</button
        ></form
      >
      <StudentDetail
        v-if="selectedTab === 'statistics'"
        :feature="feature"
        :student-id="selected.id"
        :semester-id="semesterId"
      />
    </section>
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
