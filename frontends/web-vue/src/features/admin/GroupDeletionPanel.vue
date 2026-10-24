<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import {
  AdminGroupDeletionUnknownError,
  AdminGroupsApiError,
  type AdminGroup,
  type AdminGroupDeletionOperation,
  type AdminGroupsClient,
} from '../../../../mobile-core/src/features/admin-groups/admin-groups-client';
import { webSession } from '../../auth/session';
import ConfirmDialog from '../../shared/ConfirmDialog.vue';
import type { useAdmin } from './use-admin';
import {
  createGroupDeletionFlow,
  restoreGroupDeletion,
  type GroupDeletionState,
} from './group-deletion-state';

const props = defineProps<{
  group: AdminGroup | null;
  admin: ReturnType<typeof useAdmin>;
  client: AdminGroupsClient;
  blocked: boolean;
  onSettled(operation: AdminGroupDeletionOperation): Promise<void>;
}>();
const emit = defineEmits<{ locked: [value: boolean] }>();
const busy = props.admin.busy;
const userId = webSession.snapshot.value!.userId;
const storageKey = `rct:web:group-deletion:v1:${userId}`;
const state = shallowRef<GroupDeletionState>({ preview: null, reference: null, operation: null });
const password = ref('');
const failure = ref('');
const canDelete = computed(
  () => props.admin.can('GROUPS_MANAGE') && props.admin.can('DATA_DELETE'),
);
const flow = createGroupDeletionFlow({
  userId,
  client: props.client,
  assertCurrent: props.admin.assertCurrent,
  saved: readSaved(),
  persist(reference) {
    props.admin.assertCurrent();
    try {
      if (reference) sessionStorage.setItem(storageKey, JSON.stringify(reference));
      else sessionStorage.removeItem(storageKey);
    } catch {
      // The mounted page still retains the original intent if browser storage is unavailable.
    }
  },
  changed(value) {
    state.value = value;
  },
});
state.value = flow.state;
watch(
  () => !!state.value.reference || !!state.value.preview,
  (value) => emit('locked', value),
  {
    immediate: true,
    flush: 'sync',
  },
);
watch(
  canDelete,
  (allowed) => {
    if (!allowed) {
      password.value = '';
      failure.value = '';
      flow.clearPrivate();
    }
  },
  { flush: 'sync' },
);
watch(
  () => props.group?.id,
  () => {
    password.value = '';
    failure.value = '';
    if (!state.value.reference) flow.clearPrivate();
  },
);
onBeforeUnmount(() => {
  password.value = '';
});

const labels: Record<string, string> = {
  accountsAffected: 'Аккаунты с доступом к группе (сохранятся)',
  memberships: 'Участие студентов в группе',
  headmanAssignments: 'Назначения старосты',
  assistantAssignments: 'Полномочия помощников',
  authGrants: 'Права доступа к группе',
  academicRows: 'Учебные записи',
  scheduleRows: 'Записи расписания',
  attendanceRows: 'Записи посещаемости',
  groups: 'Группа',
  academic_role_eligibility: 'Назначения участников',
  student_group_history: 'История состава',
  group_history_coverage: 'Периоды сохранённого состава',
  headman_assistants: 'Назначения помощников',
  subjects: 'Предметы',
  subject_lesson_types: 'Типы занятий предметов',
  teacher_subject_groups: 'Назначения преподавателей в группе',
  assignments: 'Учебные назначения',
  attendance_thresholds: 'Настройки посещаемости',
  homeworks: 'Домашние задания',
  homework_weekly_digest_runs: 'Сводки домашних заданий',
  homework_completions: 'Выполнение домашних заданий',
  homework_edit_history: 'История правок домашних заданий',
  homework_edit_receipts: 'Подтверждения правок домашних заданий',
  homework_edit_operations: 'Операции правок домашних заданий',
  homework_edit_archive_admissions: 'Подтверждения архивных правок',
  homework_binding_archives: 'Архивные связи домашних заданий',
  homework_binding_transfer_markers: 'Метки переноса домашних заданий',
  homework_binding_transfer_history: 'История переноса домашних заданий',
  schedule_items: 'Шаблоны расписания',
  schedule_one_off_lessons: 'Разовые занятия',
  lesson_occurrences: 'События занятий',
  lessons: 'Занятия',
  lesson_homework_bindings: 'Связи занятий и домашних заданий',
  schedule_assignment_fences: 'Закрепления учебных назначений',
  lesson_lifecycle_entries: 'История изменений занятий',
  attendances: 'Отметки посещаемости',
  late_checkin_requests: 'Заявки на опоздание',
  excuse_tickets: 'Заявки на уважительную причину',
  request_attachments: 'Вложения заявок',
  lesson_actions: 'Действия на занятиях',
  student_checkin_pairs: 'Связи отметок студентов',
  student_checkin_receipts: 'Подтверждения отметок студентов',
  student_request_receipts: 'Подтверждения заявок студентов',
  lesson_cancellation_markers: 'Метки отмены занятий',
};
const summaryKeys = [
  'accountsAffected',
  'memberships',
  'headmanAssignments',
  'assistantAssignments',
  'authGrants',
  'academicRows',
  'scheduleRows',
  'attendanceRows',
];
const details = computed(() =>
  Object.entries(state.value.preview?.counts ?? {}).filter(([key]) => !summaryKeys.includes(key)),
);
const phases = {
  PREPARING: 'Подготовка удаления',
  COMMITTING: 'Удаление данных',
  RELEASING: 'Завершение операции',
  COMPLETED: 'Удаление завершено',
  CANCELLED: 'Удаление отменено',
};
function resourceLabel(key: string) {
  const resource = key.includes('.') ? key.slice(key.indexOf('.') + 1) : key;
  return labels[resource] ?? 'Дополнительные записи';
}
function readSaved() {
  try {
    return restoreGroupDeletion(JSON.parse(sessionStorage.getItem(storageKey) ?? 'null'), userId);
  } catch {
    return null;
  }
}
async function authorize() {
  await props.admin.refreshAuthority();
  props.admin.assertCurrent();
  if (!canDelete.value)
    throw new Error('Для удаления нужны права управления группами и удаления данных.');
}
function showFailure(cause: unknown) {
  if (
    state.value.operation?.phase === 'COMPLETED' ||
    state.value.operation?.phase === 'CANCELLED'
  ) {
    failure.value = 'Результат операции подтверждён. Не удалось обновить реестр — обнови данные.';
    return;
  }
  const status = cause instanceof AdminGroupsApiError ? cause.response.status : null;
  if (state.value.reference) {
    failure.value =
      cause instanceof AdminGroupDeletionUnknownError
        ? ''
        : status === 403
          ? 'Нет доступа к состоянию операции. Проверь текущие права.'
          : status === 401
            ? 'Подтверди вход, затем проверь сохранённую операцию.'
            : status === 404
              ? 'Сервер пока не нашёл исходную операцию. Повтори проверку позже.'
              : 'Не удалось проверить состояние операции. Повтори проверку позже.';
    return;
  }
  failure.value =
    status === 409
      ? 'Данные изменились или ключ операции занят. Получи новый предпросмотр.'
      : status === 403
        ? 'Пароль не подтверждён или право удаления отозвано.'
        : status === 401
          ? 'Подтверди вход и проверь исходную операцию.'
          : status === 429
            ? 'Слишком много запросов. Повтори проверку позже.'
            : status === 400
              ? 'Не удалось подтвердить данные удаления. Получи новый предпросмотр.'
              : 'Не удалось получить предпросмотр. Повтори проверку.';
}
async function prepare() {
  if (props.blocked || state.value.reference || !props.group) return;
  password.value = '';
  failure.value = '';
  await props.admin.run(async () => {
    try {
      await authorize();
      const preview = await props.client.previewGroupDeletion(
        String(props.group!.id),
        props.admin.signal,
      );
      props.admin.assertCurrent();
      flow.prepare(preview);
    } catch (cause) {
      showFailure(cause);
      throw cause;
    }
  });
}
function cancel() {
  if (busy.value || state.value.reference) return;
  password.value = '';
  failure.value = '';
  flow.clearPrivate();
}
async function settle(operation: AdminGroupDeletionOperation) {
  if (operation.phase === 'COMPLETED' || operation.phase === 'CANCELLED')
    await props.onSettled(operation);
}
async function submit() {
  if (busy.value || !state.value.preview || state.value.reference) return;
  if (!password.value.trim() || password.value.length > 256) {
    failure.value = 'Введи свой текущий пароль (до 256 символов).';
    return;
  }
  failure.value = '';
  await props.admin.run(async () => {
    try {
      await authorize();
      const proof = password.value;
      password.value = '';
      await settle(await flow.submit(proof, props.admin.signal));
    } catch (cause) {
      showFailure(cause);
      throw cause;
    } finally {
      password.value = '';
    }
  });
}
async function recover() {
  if (busy.value || !state.value.reference) return;
  failure.value = '';
  password.value = '';
  await props.admin.run(async () => {
    try {
      await authorize();
      await settle(await flow.recover(props.admin.signal));
    } catch (cause) {
      showFailure(cause);
      throw cause;
    }
  });
}
</script>

<template>
  <section
    v-if="canDelete && group && !state.reference"
    class="web-stack"
  >
    <h3>Окончательное удаление</h3>
    <button
      class="web-button web-button-danger"
      :disabled="busy || blocked"
      @click="prepare"
    >
      Показать последствия удаления
    </button>
  </section>
  <p
    v-if="failure && !state.preview"
    class="web-notice admin-feedback-error"
    role="alert"
  >
    {{ failure }}
  </p>
  <ConfirmDialog
    :open="!!state.preview && canDelete"
    :title="`Удалить ${state.preview?.groupName ?? ''} окончательно?`"
    confirm-label="Удалить окончательно"
    :busy="busy"
    danger
    @confirm="submit"
    @cancel="cancel"
  >
    <div
      v-if="state.preview"
      class="web-stack"
    >
      <p>
        Данные группы будут удалены без восстановления. Участие и полномочия в этой группе
        прекратятся, включая доступ открытых сессий.
      </p>
      <p>Аккаунты, пароли, другие роли и назначения сохранятся.</p>
      <dl class="admin-pairs">
        <template
          v-for="key in summaryKeys"
          :key="key"
        >
          <dt>{{ labels[key] }}</dt><dd>{{ state.preview.counts[key]!.toLocaleString('ru-RU') }}</dd>
        </template>
      </dl>
      <details v-if="details.length">
        <summary>Состав удаляемых данных</summary>
        <dl class="admin-pairs">
          <template
            v-for="[key, count] in details"
            :key="key"
          >
            <dt>{{ resourceLabel(key) }}</dt><dd>{{ count.toLocaleString('ru-RU') }}</dd>
          </template>
        </dl>
      </details>
      <form
        class="web-stack"
        @submit.prevent="submit"
      >
        <label class="web-field">Твой текущий пароль
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="256"
            :disabled="busy"
          >
        </label>
      </form>
      <p
        v-if="failure"
        class="web-notice admin-feedback-error"
        role="alert"
      >
        {{ failure }}
      </p>
    </div>
  </ConfirmDialog>
  <section
    v-if="state.reference"
    class="web-notice web-stack"
    role="status"
    :aria-busy="busy"
  >
    <h3>Удаление группы №{{ state.reference.groupId }}</h3>
    <p>
      {{
        state.operation ? phases[state.operation.phase] : 'Результат удаления пока неизвестен'
      }}.
    </p>
    <p v-if="state.operation?.failure">
      Завершение ожидает восстановления сервиса. Повтори проверку позже.
    </p>
    <p>Новые изменения заблокированы до подтверждения результата.</p>
    <button
      v-if="canDelete"
      class="web-button"
      :disabled="busy"
      @click="recover"
    >
      {{ busy ? 'Проверяем…' : 'Проверить исходную операцию' }}
    </button>
    <p v-else>
      Право удаления недоступно. Исходная операция сохранена для проверки после восстановления
      доступа.
    </p>
  </section>
</template>
