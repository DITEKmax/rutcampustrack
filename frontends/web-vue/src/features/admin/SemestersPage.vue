<script setup lang="ts">
import { useWebLiveRefresh } from '../../app/live';
import { computed, onMounted, ref, shallowRef } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import {
  AdminSemesterClient,
  AdminSemesterApiError,
  type AdminSemester,
  type AdminSemesterType,
  type AdminSemesterArchiveOperation,
  type AdminSemesterArchiveStatus,
  type AdminSemesterDeletionPreview,
  type AdminSemesterDeletionOperation,
  type AdminSemesterDeletionCounts,
} from '../../../../mobile-core/src/features/admin-semester/admin-semester-client';
import { useAdmin, dateLabel } from './use-admin';
import AdminState from './AdminState.vue';
import './admin.pcss';
const admin = useAdmin();
const { loading, busy, error, notice } = admin;
const client = new AdminSemesterClient(admin.options);
const semesters = shallowRef<readonly AdminSemester[]>([]);
const selected = shallowRef<AdminSemester | null>(null);
const editing = ref(false),
  name = ref(''),
  dateFrom = ref(''),
  dateTo = ref(''),
  semesterType = ref<AdminSemesterType>('AUTUMN'),
  academicYear = ref(new Date().getFullYear());
const filter = ref<'ALL' | 'CURRENT' | 'ARCHIVED'>('ALL');
const archiveStatus = shallowRef<AdminSemesterArchiveStatus | null>(null);
const archiveConfirm = ref(false),
  activateConfirm = ref(false);
const deletionPreview = shallowRef<AdminSemesterDeletionPreview | null>(null),
  deletionOperation = shallowRef<AdminSemesterDeletionOperation | null>(null),
  archiveOperation = shallowRef<AdminSemesterArchiveOperation | null>(null);
const password = ref('');
type DurableReference = {
  kind: 'ARCHIVE' | 'RESTORE' | 'DELETE';
  semesterId: number;
  operationId: string;
  previewDigest?: string;
};
const durable = shallowRef<DurableReference | null>(null);
const uncertain = ref(false);
const canManage = computed(() => admin.can('SEMESTERS_MANAGE'));
const locked = computed(() => busy.value || durable.value !== null || uncertain.value);
const visible = computed(() =>
  semesters.value.filter(
    (s) => filter.value === 'ALL' || (filter.value === 'ARCHIVED' ? s.archived : !s.archived),
  ),
);
const scope = admin.options.sessionScopeFor();
const storageKey = scope ? `rct:web:semester-operation:v1:${scope}` : null;
const countLabels: Record<keyof AdminSemesterDeletionCounts, string> = {
  scheduleTemplates: 'Шаблоны расписания',
  oneOffLessons: 'Разовые занятия',
  lessons: 'Занятия',
  assignments: 'Назначения',
  homeworks: 'Домашние задания',
  attendanceMarks: 'Отметки посещаемости',
  studentRequests: 'Заявки студентов',
};
const deletionPhases = {
  PREPARING: 'Подготовка удаления',
  RELEASING: 'Завершение блокировок',
  DELETING: 'Удаление данных',
  COMPLETED: 'Удаление завершено',
  CANCELLED: 'Удаление отменено',
};
function persist(value: DurableReference | null) {
  durable.value = value;
  try {
    if (storageKey) {
      if (value) sessionStorage.setItem(storageKey, JSON.stringify(value));
      else sessionStorage.removeItem(storageKey);
    }
  } catch {
    /* The reference remains in this mounted page. */
  }
}
function restoreReference() {
  try {
    const raw: unknown = JSON.parse(
      storageKey ? (sessionStorage.getItem(storageKey) ?? 'null') : 'null',
    );
    if (raw && typeof raw === 'object') {
      const v = raw as Record<string, unknown>;
      if (
        ['ARCHIVE', 'RESTORE', 'DELETE'].includes(String(v.kind)) &&
        Number.isSafeInteger(v.semesterId) &&
        Number(v.semesterId) > 0 &&
        typeof v.operationId === 'string' &&
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v.operationId) &&
        (v.previewDigest === undefined ||
          (typeof v.previewDigest === 'string' && /^[0-9a-f]{64}$/.test(v.previewDigest)))
      )
        durable.value = v as DurableReference;
    }
  } catch {
    /* Ignore malformed local hints; the server remains authoritative. */
  }
}
async function load() {
  const value = await client.listSemesters(admin.signal);
  admin.assertCurrent();
  semesters.value = value;
}
async function open(semester: AdminSemester) {
  await admin.run(async () => {
    const [value, status] = await Promise.all([
      client.getSemester(semester.id, admin.signal),
      client.getArchiveStatus(semester.id, admin.signal),
    ]);
    admin.assertCurrent();
    selected.value = value;
    archiveStatus.value = status;
    editing.value = false;
    archiveConfirm.value = false;
    activateConfirm.value = false;
    deletionPreview.value = null;
  });
}
function edit(semester?: AdminSemester) {
  selected.value = semester ?? null;
  editing.value = true;
  name.value = semester?.name ?? '';
  dateFrom.value = semester?.dateFrom ?? '';
  dateTo.value = semester?.dateTo ?? '';
  semesterType.value = semester?.semesterType ?? 'AUTUMN';
  academicYear.value = semester?.academicYear ?? new Date().getFullYear();
  deletionPreview.value = null;
}
async function save() {
  await admin.run(async () => {
    const overlap = await client.checkOverlap(
      dateFrom.value,
      dateTo.value,
      selected.value?.id,
      admin.signal,
    );
    admin.assertCurrent();
    if (overlap.overlaps)
      throw new Error(
        `Даты пересекаются с семестром «${overlap.conflictingName ?? 'без названия'}».`,
      );
    const input = {
      name: name.value,
      dateFrom: dateFrom.value,
      dateTo: dateTo.value,
      semesterType: semesterType.value,
      academicYear: academicYear.value,
    };
    try {
      const value = selected.value
        ? await client.updateSemester(selected.value.id, input)
        : await client.createSemester(input);
      admin.assertCurrent();
      selected.value = value;
      editing.value = false;
      await load();
      notice.value = 'Семестр сохранён.';
    } catch (cause) {
      admin.assertCurrent();
      if (!hasDefiniteRejection(cause)) uncertain.value = true;
      throw cause;
    }
  }, 'SEMESTERS_MANAGE');
}
function hasDefiniteRejection(cause: unknown) {
  return Boolean(
    cause &&
      typeof cause === 'object' &&
      'response' in cause &&
      cause.response instanceof Response &&
      cause.response.status >= 400 &&
      cause.response.status < 500,
  );
}
async function inspectSave() {
  await admin.run(async () => {
    await load();
    admin.assertCurrent();
    const found = semesters.value.find(
      (s) =>
        s.name === name.value &&
        s.dateFrom === dateFrom.value &&
        s.dateTo === dateTo.value &&
        (!selected.value || selected.value.id === s.id),
    );
    if (found) {
      selected.value = found;
      editing.value = false;
      uncertain.value = false;
      notice.value = 'Текущие данные семестра подтверждены. Повторная запись не отправлялась.';
    } else notice.value = 'Сохранение пока не подтверждено. Повторная запись заблокирована.';
  });
}
async function activate() {
  await admin.run(async () => {
    if (!selected.value) return;
    const id = selected.value.id;
    try {
      const value = await client.activateSemester(id);
      admin.assertCurrent();
      selected.value = value;
      activateConfirm.value = false;
      await load();
      notice.value = value.activationScheduled
        ? 'Активация назначена сервером.'
        : 'Активный семестр изменён.';
    } catch (cause) {
      admin.assertCurrent();
      await load();
      const current = semesters.value.find((s) => s.id === id);
      if (current?.active || current?.activationScheduled) {
        selected.value = current;
        activateConfirm.value = false;
        notice.value = 'Текущее состояние активации подтверждено.';
      } else throw cause;
    }
  }, 'SEMESTERS_MANAGE');
}
async function changeArchive() {
  await admin.run(async () => {
    if (!selected.value) return;
    const reference: DurableReference = {
      kind: selected.value.archived ? 'RESTORE' : 'ARCHIVE',
      semesterId: selected.value.id,
      operationId: crypto.randomUUID(),
    };
    persist(reference);
    archiveConfirm.value = false;
    try {
      const value =
        reference.kind === 'RESTORE'
          ? await client.restoreSemester(reference.semesterId, reference.operationId, admin.signal)
          : await client.archiveSemester(reference.semesterId, reference.operationId, admin.signal);
      admin.assertCurrent();
      archiveOperation.value = value;
      await finishArchive(value);
    } catch (cause) {
      admin.assertCurrent();
      if (cause instanceof AdminSemesterApiError && cause.archiveOperation) {
        archiveOperation.value = cause.archiveOperation;
        persist({ ...reference, operationId: cause.archiveOperation.operationId });
        await finishArchive(cause.archiveOperation);
      } else if (hasDefiniteRejection(cause)) persist(null);
      throw cause;
    }
  }, 'SEMESTERS_MANAGE');
}
async function finishArchive(value: AdminSemesterArchiveOperation) {
  if (
    value.operationState === 'COMPLETED' &&
    !value.releasePending &&
    value.transition === 'NONE'
  ) {
    persist(null);
    await load();
    selected.value = semesters.value.find((s) => s.id === value.semesterId) ?? null;
    notice.value = value.action === 'RESTORE' ? 'Семестр восстановлен.' : 'Семестр архивирован.';
  } else
    notice.value =
      value.blockingReason ??
      (value.operationState === 'ERROR'
        ? 'Операция остановлена. Проверь состояние и возможность продолжения.'
        : 'Сервер завершает изменение семестра. Проверь результат.');
}
async function previewDelete() {
  await admin.run(async () => {
    if (!selected.value) return;
    const value = await client.getSemesterDeletionPreview(selected.value.id, admin.signal);
    admin.assertCurrent();
    deletionPreview.value = value;
    password.value = '';
  }, 'DATA_DELETE');
}
async function deleteSemester() {
  await admin.run(async () => {
    const preview = deletionPreview.value;
    if (!preview || !admin.can('SEMESTERS_MANAGE'))
      throw new Error('Нужны права управления семестрами.');
    const reference: DurableReference = {
      kind: 'DELETE',
      semesterId: preview.semesterId,
      operationId: crypto.randomUUID(),
      previewDigest: preview.previewDigest,
    };
    persist(reference);
    try {
      const value = await client.deleteSemester(
        preview.semesterId,
        { previewDigest: preview.previewDigest, password: password.value },
        reference.operationId,
        admin.signal,
      );
      admin.assertCurrent();
      deletionOperation.value = value;
      deletionPreview.value = null;
      await finishDelete(value);
    } catch (cause) {
      admin.assertCurrent();
      if (cause instanceof AdminSemesterApiError && cause.deletionOperation) {
        deletionOperation.value = cause.deletionOperation;
        persist({ ...reference, operationId: cause.deletionOperation.operationId });
        await finishDelete(cause.deletionOperation);
      } else if (hasDefiniteRejection(cause)) persist(null);
      throw cause;
    } finally {
      password.value = '';
    }
  }, 'DATA_DELETE');
}
async function finishDelete(value: AdminSemesterDeletionOperation) {
  if (value.phase === 'COMPLETED') {
    persist(null);
    selected.value = null;
    await load();
    notice.value = 'Семестр и перечисленные данные удалены.';
  } else if (value.phase === 'CANCELLED') {
    persist(null);
    deletionPreview.value = value.refreshedPreview;
    await load();
    notice.value = value.reason ?? 'Удаление отменено. Проверь обновлённый предпросмотр.';
  } else
    notice.value =
      value.blockingReason ?? value.reason ?? 'Удаление выполняется. Проверь состояние операции.';
}
async function inspectOperation() {
  await admin.run(async () => {
    const current = durable.value;
    if (!current) return;
    if (current.kind === 'DELETE') {
      const value = await client.getSemesterDeletionOperation(current.operationId, admin.signal);
      admin.assertCurrent();
      deletionOperation.value = value;
      await finishDelete(value);
    } else {
      const value = await client.getArchiveOperation(current.operationId, admin.signal);
      admin.assertCurrent();
      archiveOperation.value = value;
      await finishArchive(value);
    }
  });
}
async function continueArchive() {
  await admin.run(async () => {
    const reference = durable.value;
    if (!reference || reference.kind === 'DELETE' || !archiveOperation.value?.retryable) return;
    const value =
      reference.kind === 'RESTORE'
        ? await client.restoreSemester(reference.semesterId, reference.operationId, admin.signal)
        : await client.archiveSemester(reference.semesterId, reference.operationId, admin.signal);
    admin.assertCurrent();
    archiveOperation.value = value;
    await finishArchive(value);
  }, 'SEMESTERS_MANAGE');
}
async function continueDelete() {
  await admin.run(async () => {
    const reference = durable.value;
    if (
      !reference ||
      reference.kind !== 'DELETE' ||
      !reference.previewDigest ||
      !deletionOperation.value?.retryable ||
      !admin.can('SEMESTERS_MANAGE')
    )
      return;
    try {
      const value = await client.deleteSemester(
        reference.semesterId,
        { previewDigest: reference.previewDigest, password: password.value },
        reference.operationId,
        admin.signal,
      );
      admin.assertCurrent();
      deletionOperation.value = value;
      await finishDelete(value);
    } finally {
      password.value = '';
    }
  }, 'DATA_DELETE');
}
onBeforeRouteLeave(() => {
  if (busy.value || uncertain.value) {
    error.value = 'Сначала проверь результат сохранения.';
    return false;
  }
});
onMounted(() => {
  restoreReference();
  void admin.init('SEMESTERS_VIEW', load).then(() => {
    if (durable.value) return inspectOperation();
  });
});
useWebLiveRefresh(
  async () => {
    await admin.run(async () => {
      await admin.requirePermission('SEMESTERS_VIEW');
      await load();
    });
    if (durable.value) await inspectOperation();
  },
  { blocked: () => busy.value || editing.value || (!durable.value && !!selected.value) },
);
</script>
<template>
  <section
    class="admin-page"
    :aria-busy="busy"
  >
    <header class="admin-heading">
      <div>
        <h1>Семестры</h1><p class="web-caption">
          {{ semesters.find((s) => s.active)?.name ?? 'Нет активного семестра' }}
        </p>
      </div><button
        v-if="canManage"
        class="web-button web-button-primary"
        :disabled="locked"
        @click="edit()"
      >
        Добавить семестр
      </button>
    </header><AdminState
      :loading="loading"
      :busy="busy"
      :error="error"
      :notice="notice"
      @retry="admin.run(load)"
    />
    <label
      v-if="admin.can('SEMESTERS_VIEW')"
      class="web-field"
    >Показывать<select v-model="filter"><option value="ALL">Все семестры</option><option value="CURRENT">Текущие</option><option value="ARCHIVED">Архивные</option></select></label>
    <div
      v-if="visible.length && admin.can('SEMESTERS_VIEW')"
      class="admin-table-wrap"
    >
      <table class="admin-table">
        <caption class="web-sr-only">
          Учебные семестры
        </caption><thead>
          <tr>
            <th scope="col">
              Название
            </th><th scope="col">
              Начало
            </th><th scope="col">
              Конец
            </th><th scope="col">
              Состояние
            </th><th scope="col">
              Действия
            </th>
          </tr>
        </thead><tbody>
          <tr
            v-for="semester in visible"
            :key="semester.id"
          >
            <td>{{ semester.name }}</td><td>{{ dateLabel(semester.dateFrom) }}</td><td>{{ dateLabel(semester.dateTo) }}</td><td>
              {{
                semester.transition === 'ARCHIVING'
                  ? 'Архивация'
                  : semester.transition === 'RESTORING'
                    ? 'Восстановление'
                    : semester.releasePending
                      ? 'Завершение операции'
                      : semester.archived
                        ? 'Архивный'
                        : semester.active
                          ? 'Активный'
                          : semester.activationScheduled
                            ? 'Активация назначена'
                            : 'Неактивный'
              }}
            </td><td>
              <button
                class="web-button"
                :disabled="locked"
                @click="open(semester)"
              >
                Открыть
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div><p
      v-else-if="!loading && admin.can('SEMESTERS_VIEW')"
      role="status"
    >
      В этом разделе нет семестров.
    </p>
    <section
      v-if="editing"
      class="admin-panel admin-form"
    >
      <h2>{{ selected ? 'Изменить семестр' : 'Новый семестр' }}</h2><form
        class="web-stack"
        @submit.prevent="save"
      >
        <fieldset
          class="admin-form-grid"
          :disabled="locked"
        >
          <legend class="web-sr-only">
            Параметры семестра
          </legend><label class="web-field">Название<input
            v-model="name"
            required
            maxlength="128"
          ></label><label class="web-field">Тип<select v-model="semesterType"><option value="AUTUMN">Осенний</option><option value="SPRING">Весенний</option></select></label><label class="web-field">Первый год учебного года<input
            v-model.number="academicYear"
            type="number"
            min="2000"
            max="2200"
            required
          ></label><label class="web-field">Начало<input
            v-model="dateFrom"
            type="date"
            required
            :disabled="selected?.archived"
          ></label><label class="web-field">Конец<input
            v-model="dateTo"
            type="date"
            required
            :min="dateFrom"
            :disabled="selected?.archived"
          ></label>
        </fieldset><p
          v-if="selected?.archived"
          class="web-caption"
        >
          У архивного семестра даты защищены от изменения.
        </p><div class="admin-actions">
          <button
            type="button"
            class="web-button"
            :disabled="locked"
            @click="editing = false"
          >
            Отмена
          </button><button
            class="web-button web-button-primary"
            :disabled="locked"
          >
            Сохранить семестр
          </button>
        </div>
      </form>
    </section>
    <section
      v-else-if="selected"
      class="admin-panel"
    >
      <header class="admin-heading">
        <h2>{{ selected.name }}</h2><button
          class="web-button"
          :disabled="locked"
          @click="selected = null"
        >
          Закрыть
        </button>
      </header><p>{{ dateLabel(selected.dateFrom) }} — {{ dateLabel(selected.dateTo) }}</p><p
        v-if="selected.isWriteBlocked || archiveStatus?.releasePending"
        class="web-notice"
      >
        Данные семестра защищены на время серверной операции.
      </p><div
        v-if="canManage"
        class="admin-actions"
      >
        <button
          class="web-button"
          :disabled="locked || selected.isWriteBlocked"
          @click="edit(selected!)"
        >
          Изменить
        </button><button
          v-if="!selected.active && !selected.archived"
          class="web-button"
          :disabled="locked || selected.isWriteBlocked"
          @click="activateConfirm = true"
        >
          Активировать
        </button><button
          class="web-button"
          :disabled="locked || selected.isWriteBlocked"
          @click="archiveConfirm = true"
        >
          {{ selected.archived ? 'Восстановить' : 'Архивировать' }}
        </button><button
          v-if="admin.can('DATA_DELETE')"
          class="web-button web-button-danger"
          :disabled="locked"
          @click="previewDelete"
        >
          Удалить окончательно
        </button>
      </div>
      <div
        v-if="activateConfirm"
        class="web-notice"
      >
        <h3>Активировать {{ selected.name }}?</h3><p>Предыдущий активный семестр станет неактивным. Сервер проверит допустимость дат.</p><div class="admin-actions">
          <button
            class="web-button"
            :disabled="busy"
            @click="activateConfirm = false"
          >
            Отмена
          </button><button
            class="web-button web-button-primary"
            :disabled="locked"
            @click="activate"
          >
            Подтвердить активацию
          </button>
        </div>
      </div>
      <div
        v-if="archiveConfirm"
        class="web-notice"
      >
        <h3>{{ selected.archived ? 'Восстановить' : 'Архивировать' }} семестр?</h3><p>
          {{
            selected.archived
              ? 'Сервер согласованно восстановит учебные данные. Семестр не станет активным автоматически.'
              : 'Учебные данные сохранятся в архиве и будут недоступны для изменения.'
          }}
        </p><div class="admin-actions">
          <button
            class="web-button"
            :disabled="busy"
            @click="archiveConfirm = false"
          >
            Отмена
          </button><button
            class="web-button"
            :disabled="locked"
            @click="changeArchive"
          >
            Подтвердить
          </button>
        </div>
      </div>
      <form
        v-if="deletionPreview"
        class="admin-panel admin-form"
        @submit.prevent="deleteSemester"
      >
        <h3 class="admin-danger">
          Удалить {{ deletionPreview.semesterName }} без возможности восстановления?
        </h3><p>Будут окончательно удалены:</p><dl class="admin-pairs">
          <template
            v-for="(count, key) in deletionPreview.counts"
            :key="key"
          >
            <dt>{{ countLabels[key] }}</dt><dd>{{ count }}</dd>
          </template>
        </dl><label class="web-field">Твой пароль<input
          v-model="password"
          type="password"
          autocomplete="current-password"
          required
          :disabled="busy"
        ></label><div class="admin-actions">
          <button
            type="button"
            class="web-button"
            :disabled="locked"
            @click="deletionPreview = null"
          >
            Отмена
          </button><button
            class="web-button web-button-danger"
            :disabled="locked"
          >
            Подтвердить окончательное удаление
          </button>
        </div>
      </form>
    </section>
    <section
      v-if="durable"
      class="web-notice"
      role="status"
    >
      <h2>{{ durable.kind === 'DELETE' ? 'Удаление семестра' : 'Изменение архива семестра' }}</h2><p>Операция {{ durable.operationId }} · семестр {{ durable.semesterId }}.</p><p v-if="archiveOperation">
        {{
          archiveOperation.operationState === 'PENDING'
            ? 'Выполняется'
            : archiveOperation.operationState === 'ERROR'
              ? 'Остановлена'
              : 'Завершается'
        }}
        · {{ archiveOperation.blockingReason }}
      </p><p v-if="deletionOperation">
        {{ deletionPhases[deletionOperation.phase] }} ·
        {{ deletionOperation.blockingReason ?? deletionOperation.reason }}
      </p><div class="admin-actions">
        <button
          class="web-button"
          :disabled="busy"
          @click="inspectOperation"
        >
          Проверить состояние
        </button><button
          v-if="archiveOperation?.retryable && durable.kind !== 'DELETE'"
          class="web-button"
          :disabled="busy"
          @click="continueArchive"
        >
          Продолжить эту операцию
        </button>
      </div>
      <form
        v-if="deletionOperation?.retryable && durable.kind === 'DELETE' && durable.previewDigest"
        class="admin-form web-stack"
        @submit.prevent="continueDelete"
      >
        <p>Продолжение использует тот же номер операции и исходный предпросмотр.</p><label class="web-field">Твой пароль<input
          v-model="password"
          type="password"
          autocomplete="current-password"
          required
          :disabled="busy"
        ></label><button
          class="web-button web-button-danger"
          :disabled="busy"
        >
          Продолжить это удаление
        </button>
      </form>
    </section>
    <div
      v-if="uncertain"
      class="web-notice"
    >
      <p>Результат сохранения неизвестен. Повторная запись заблокирована.</p><button
        class="web-button"
        :disabled="busy"
        @click="inspectSave"
      >
        Проверить текущие данные
      </button>
    </div>
  </section>
</template>
