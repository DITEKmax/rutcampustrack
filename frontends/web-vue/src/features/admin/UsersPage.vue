<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router';
import { useWebLiveRefresh } from '../../app/live';
import {
  AdminUsersClient,
  type AdminUser,
  type AdminUsersPage,
  type AdminRoleStatus,
  type AdminUserArchivePreview,
  type CreateAdminUserInput,
} from '../../../../mobile-core/src/features/admin-users/admin-users-client';
import type {
  AdminAccessCatalog,
  AdminAccessMode,
} from '../../../../mobile-core/src/features/admin-access/admin-access-client';
import { useAdmin, bindAdminRecovery, roleLabels, statusLabels } from './use-admin';
import AdminState from './AdminState.vue';
import GroupPicker from './GroupPicker.vue';
import GroupFilter from './GroupFilter.vue';
import UserRegistryTable from './UserRegistryTable.vue';
import PermissionFields from './PermissionFields.vue';
import UserAccess from './UserAccess.vue';
import UserCredentials from './UserCredentials.vue';
import ExportControl from './ExportControl.vue';
import {
  readUserRegistryQuery,
  userRegistryLocation,
  userRegistryExportSelector,
  type UserRegistryFilters,
} from './user-registry-query';
import './admin.pcss';

const admin = useAdmin();
const { loading, busy, error, notice, full } = admin;
const users = new AdminUsersClient(admin.options);
bindAdminRecovery(admin.options, 'users', users);
const page = shallowRef<AdminUsersPage | null>(null);
const selected = shallowRef<AdminUser | null>(null);
const route = useRoute(),
  router = useRouter();
const initialQuery = readUserRegistryQuery(route.query);
const search = ref(initialQuery.filters.search);
const role = ref(initialQuery.filters.role);
const roleStatus = ref(initialQuery.filters.roleStatus);
const accountStatus = ref(initialQuery.filters.status);
const filterGroupId = ref(initialQuery.filters.groupId);
const sortDirection = ref(initialQuery.filters.sortDirection);
const applied = shallowRef<UserRegistryFilters>(initialQuery.filters);
let navigating = false;
const creating = ref(false);
const editor = ref<HTMLElement | null>(null);
const lastName = ref(''),
  firstName = ref(''),
  middleName = ref(''),
  employeeNumber = ref(''),
  telegramId = ref('');
const createRole = ref<'STUDENT' | 'TEACHER' | 'ADMIN'>('STUDENT');
const groupId = ref<number>();
const catalog = shallowRef<AdminAccessCatalog | null>(null);
const accessMode = ref<AdminAccessMode>('LIMITED'),
  permissions = ref<string[]>([]);
const pending = ref(Boolean(users.failedIdentityOperation)),
  accessLocked = ref(false),
  credentialLocked = ref(false);
const childLocked = computed(() => accessLocked.value || credentialLocked.value);
const roleToUpdate = ref<'STUDENT' | 'TEACHER' | 'ADMIN'>('STUDENT');
const targetStatus = ref<AdminRoleStatus>('ACTIVE');
const targetGroupId = ref<number>(),
  transferGroupId = ref<number>(),
  transferReason = ref('');
const archive = shallowRef<AdminUserArchivePreview | null>(null);
const password = ref('');
const archiveRecovery = ref(false);
const initialReceipt = shallowRef<{ login: string; password: string } | null>(null);
let loadRevision = 0;
const locked = computed(() => busy.value || pending.value || childLocked.value);
admin.guardUnfinished(() => locked.value);
const canManage = computed(() => admin.can('USERS_MANAGE'));
const canArchive = computed(() => admin.can('USERS_ARCHIVE'));
const activeGrant = computed(() =>
  selected.value?.roles.find((r) => r.role === roleToUpdate.value),
);
const applicableStatuses = computed(
  () => activeGrant.value?.applicableStatuses ?? (['ACTIVE'] as const),
);
const exportSelector = computed(() => userRegistryExportSelector(applied.value));

async function load(number = 0) {
  const own = ++loadRevision;
  const value = await users.listUsers({ ...applied.value, page: number, size: 30 }, admin.signal);
  admin.assertCurrent();
  if (own === loadRevision) page.value = value;
}
function draftQuery(): UserRegistryFilters {
  return {
    search: search.value,
    role: role.value,
    roleStatus: role.value ? roleStatus.value : '',
    status: accountStatus.value,
    ...(filterGroupId.value ? { groupId: filterGroupId.value } : {}),
    sortDirection: sortDirection.value,
  };
}
function clearSelection() {
  selected.value = null;
  creating.value = false;
  archive.value = null;
  initialReceipt.value = null;
}
async function navigate(number = 0, filters = applied.value) {
  if (locked.value) return;
  clearSelection();
  navigating = true;
  try {
    const failure = await router.replace({ query: userRegistryLocation(filters, number) });
    admin.assertCurrent();
    if (failure) return;
    applied.value = filters;
  } catch (cause) {
    try {
      admin.assertCurrent();
      error.value = cause instanceof Error ? cause.message : 'Не удалось применить фильтры.';
    } catch {
      /* The admitted session has ended. */
    }
    return;
  } finally {
    navigating = false;
  }
  await admin.run(() => load(number));
}
function apply() {
  void navigate(0, draftQuery());
}
watch(
  () => route.fullPath,
  () => {
    if (navigating) return;
    const current = readUserRegistryQuery(route.query);
    search.value = current.filters.search;
    role.value = current.filters.role;
    roleStatus.value = current.filters.roleStatus;
    accountStatus.value = current.filters.status;
    filterGroupId.value = current.filters.groupId;
    sortDirection.value = current.filters.sortDirection;
    applied.value = current.filters;
    clearSelection();
    void admin.run(() => load(current.page));
  },
);
async function focusEditor() {
  await nextTick();
  admin.assertCurrent();
  editor.value?.focus();
  editor.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
async function open(user: AdminUser) {
  await admin.run(async () => {
    const value = await users.getUser(user.id, admin.signal);
    admin.assertCurrent();
    selected.value = value;
    creating.value = false;
    archive.value = null;
    lastName.value = value.lastName;
    firstName.value = value.firstName;
    middleName.value = value.middleName ?? '';
    employeeNumber.value = value.employeeNumber ?? '';
    telegramId.value = value.telegramId?.toString() ?? '';
    roleToUpdate.value = value.role;
    targetStatus.value = value.roles.find((r) => r.role === value.role)?.status ?? 'ACTIVE';
    targetGroupId.value = value.groupId ?? undefined;
    await focusEditor();
  });
}
async function beginCreate() {
  selected.value = null;
  creating.value = true;
  archive.value = null;
  lastName.value = '';
  firstName.value = '';
  middleName.value = '';
  employeeNumber.value = '';
  telegramId.value = '';
  groupId.value = undefined;
  if (full.value)
    await admin.run(async () => {
      const value = await admin.access.getCatalog();
      admin.assertCurrent();
      catalog.value = value;
    });
  await focusEditor();
}
async function identity(task: () => Promise<void>) {
  try {
    await task();
    admin.assertCurrent();
    users.acknowledgeIdentityOperation();
  } finally {
    pending.value = users.hasPendingIdentityOperation || Boolean(users.failedIdentityOperation);
  }
}
async function save() {
  await admin.run(
    async () =>
      identity(async () => {
        let user: AdminUser;
        if (creating.value) {
          const input: CreateAdminUserInput = {
            lastName: lastName.value,
            firstName: firstName.value,
            middleName: middleName.value,
            role: createRole.value,
            ...(createRole.value === 'STUDENT' && groupId.value ? { groupId: groupId.value } : {}),
            ...(createRole.value === 'TEACHER' ? { employeeNumber: employeeNumber.value } : {}),
            ...(telegramId.value ? { telegramId: Number(telegramId.value) } : {}),
            ...(createRole.value === 'ADMIN'
              ? {
                  adminAccess: {
                    mode: accessMode.value,
                    permissions: accessMode.value === 'FULL' ? [] : permissions.value,
                  },
                }
              : {}),
          };
          user = await users.createUser(input, admin.signal);
          admin.assertCurrent();
          if (user.initialPassword)
            initialReceipt.value = { login: user.login, password: user.initialPassword };
        } else if (selected.value) {
          user = await users.updateProfile(
            selected.value.id,
            {
              lastName: lastName.value,
              firstName: firstName.value,
              middleName: middleName.value,
              employeeNumber: employeeNumber.value,
              ...(telegramId.value ? { telegramId: Number(telegramId.value) } : {}),
            },
            admin.signal,
          );
        } else return;
        admin.assertCurrent();
        selected.value = user;
        creating.value = false;
        await load(page.value?.number ?? 0);
        notice.value = 'Учётная запись сохранена.';
      }),
    'USERS_MANAGE',
  );
}
async function updateRole() {
  await admin.run(
    async () =>
      identity(async () => {
        if (!selected.value) return;
        const value = await users.updateRole(
          selected.value.id,
          roleToUpdate.value,
          {
            status: targetStatus.value,
            ...(roleToUpdate.value === 'STUDENT' && targetGroupId.value
              ? { groupId: targetGroupId.value }
              : {}),
            ...(roleToUpdate.value === 'TEACHER' ? { employeeNumber: employeeNumber.value } : {}),
            ...(telegramId.value ? { telegramId: Number(telegramId.value) } : {}),
          },
          admin.signal,
        );
        admin.assertCurrent();
        selected.value = value;
        await load(page.value?.number ?? 0);
        notice.value = 'Роль обновлена.';
      }),
    'USERS_MANAGE',
  );
}
async function transfer() {
  await admin.run(
    async () =>
      identity(async () => {
        if (!selected.value || !transferGroupId.value) return;
        const value = await users.transferStudent(
          selected.value.id,
          { newGroupId: transferGroupId.value, reason: transferReason.value },
          admin.signal,
        );
        admin.assertCurrent();
        selected.value = value;
        await load(page.value?.number ?? 0);
        notice.value = 'Студент переведён. Предыдущая история сохранена.';
      }),
    'USERS_MANAGE',
  );
}
async function previewArchive() {
  await admin.run(async () => {
    if (!selected.value) return;
    const value = await users.previewArchive(selected.value.id, admin.signal);
    admin.assertCurrent();
    archive.value = value;
  }, 'USERS_ARCHIVE');
}
async function archiveUser() {
  await admin.run(
    async () =>
      identity(async () => {
        if (!selected.value || !archive.value) return;
        const id = selected.value.id;
        try {
          if (archiveRecovery.value)
            await users.reconcileIdentityOperation(
              {
                archivePreviewDigest: archive.value.previewDigest,
                ...(password.value ? { password: password.value } : {}),
              },
              admin.signal,
            );
          else
            await users.archiveUser(
              id,
              {
                operationId: crypto.randomUUID(),
                previewDigest: archive.value.previewDigest,
                ...(password.value ? { password: password.value } : {}),
              },
              admin.signal,
            );
        } finally {
          password.value = '';
        }
        admin.assertCurrent();
        archiveRecovery.value = false;
        archive.value = null;
        selected.value = await users.getUser(id, admin.signal);
        admin.assertCurrent();
        await load();
        notice.value = 'Пользователь архивирован.';
      }),
    'USERS_ARCHIVE',
  );
}
async function restore() {
  await admin.run(
    async () =>
      identity(async () => {
        if (!selected.value) return;
        const id = selected.value.id;
        await users.restoreUser(id, crypto.randomUUID(), admin.signal);
        admin.assertCurrent();
        selected.value = await users.getUser(id, admin.signal);
        admin.assertCurrent();
        await load();
        notice.value = 'Учётная запись восстановлена без автоматического возврата прежних прав.';
      }),
    'USERS_ARCHIVE',
  );
}
async function resume() {
  await admin.run(async () =>
    identity(async () => {
      const result = await users.resumeIdentityOperation();
      admin.assertCurrent();
      if (result.user) {
        selected.value = result.user;
        creating.value = false;
      }
      await load();
      notice.value = 'Результат операции подтверждён.';
    }),
  );
}
async function recover() {
  await admin.run(async () => {
    const failed = await users.confirmIdentityRecovery();
    admin.assertCurrent();
    if (!failed) throw new Error('Операция пока не готова к восстановлению.');
    if (failed.kind === 'ARCHIVE') {
      archive.value = await users.previewIdentityRecovery(admin.signal);
      admin.assertCurrent();
      archiveRecovery.value = true;
      pending.value = true;
      notice.value = 'Обнови подтверждение архивации и восстанови операцию.';
      return;
    }
    await users.reconcileIdentityOperation(undefined, admin.signal);
    admin.assertCurrent();
    await users.resumeIdentityOperation();
    admin.assertCurrent();
    users.acknowledgeIdentityOperation();
    pending.value = false;
    await load();
    notice.value = 'Операция восстановлена.';
  });
}
async function reconcileStoredArchive() {
  await admin.run(
    async () =>
      identity(async () => {
        const preview = archive.value;
        if (!archiveRecovery.value || !preview) return;
        try {
          await users.reconcileIdentityOperation(
            {
              archivePreviewDigest: preview.previewDigest,
              ...(password.value ? { password: password.value } : {}),
            },
            admin.signal,
          );
        } finally {
          password.value = '';
        }
        admin.assertCurrent();
        await users.resumeIdentityOperation();
        admin.assertCurrent();
        archiveRecovery.value = false;
        archive.value = null;
        await load();
        notice.value = 'Архивация восстановлена и подтверждена.';
      }),
    'USERS_ARCHIVE',
  );
}
function chooseRole() {
  targetStatus.value = activeGrant.value?.status ?? 'ACTIVE';
}
function close() {
  if (!locked.value) {
    selected.value = null;
    creating.value = false;
    archive.value = null;
    initialReceipt.value = null;
  }
}
onBeforeRouteLeave(() => {
  if (locked.value) {
    error.value = 'Сначала дождись завершения и проверь результат операции.';
    return false;
  }
});
onBeforeRouteUpdate(() => !locked.value);
onMounted(() => admin.init('USERS_VIEW', () => load(initialQuery.page)));
useWebLiveRefresh(
  () =>
    admin.refresh(async () => {
      await admin.requirePermission('USERS_VIEW');
      await load(page.value?.number ?? 0);
    }),
  { blocked: () => locked.value || !!selected.value || creating.value },
);
</script>
<template>
  <section
    class="admin-page"
    :aria-busy="busy"
  >
    <header class="admin-heading">
      <div>
        <h1>Пользователи</h1><p
          v-if="page"
          class="web-caption"
        >
          {{ page.totalElements.toLocaleString('ru-RU') }} учётных записей
        </p>
      </div><button
        v-if="canManage"
        class="web-button web-button-primary"
        :disabled="locked"
        @click="beginCreate"
      >
        Создать пользователя
      </button>
    </header>
    <AdminState
      :loading="loading"
      :busy="busy"
      :error="error"
      :notice="notice"
      @retry="admin.run(() => load())"
    />
    <GroupFilter
      v-if="admin.can('USERS_VIEW')"
      v-model="filterGroupId"
      :client="users"
      :assert-current="admin.assertCurrent"
      :disabled="locked"
    />
    <form
      v-if="admin.can('USERS_VIEW')"
      class="admin-toolbar"
      @submit.prevent="apply"
    >
      <label class="web-field">Поиск<input
        v-model="search"
        type="search"
        placeholder="ФИО, логин или Telegram ID"
      ></label><label class="web-field">Роль<select
        v-model="role"
        @change="roleStatus = ''"
      ><option value="">Все роли</option><option
        v-for="(label, value) in roleLabels"
        :key="value"
        :value="value"
      >{{ label }}</option></select></label><label class="web-field">Статус роли<select
        v-model="roleStatus"
        :disabled="!role"
      ><option value="">Все статусы</option><option
        v-for="(label, value) in statusLabels"
        :key="value"
        :value="value"
      >{{ label }}</option></select></label><label class="web-field">Учётная запись<select v-model="accountStatus"><option value="">{{
        roleStatus === 'ARCHIVED' ? 'Все состояния записи' : 'Без архивных записей'
      }}</option><option value="ACTIVE">Активные</option><option value="EXPELLED">Отчисленные</option><option value="SUSPENDED">Приостановленные</option><option value="ARCHIVED">Архивные</option></select></label><label class="web-field">ФИО<select v-model="sortDirection"><option value="asc">От А до Я</option><option value="desc">От Я до А</option></select></label><button
        class="web-button"
        :disabled="locked"
      >
        Найти
      </button>
    </form>
    <template v-if="page && admin.can('USERS_VIEW')">
      <UserRegistryTable
        v-if="page.items.length"
        :items="page.items"
        :disabled="locked"
        @open="open"
      /><p
        v-else
        role="status"
      >
        Пользователи не найдены. Измени поиск или фильтры.
      </p>
      <div class="admin-pager">
        <div class="admin-actions">
          <button
            class="web-button"
            :disabled="locked || page.number === 0"
            @click="navigate(page!.number - 1)"
          >
            Назад
          </button><span v-if="page.totalPages">{{ page.number + 1 }} / {{ page.totalPages }}</span><button
            class="web-button"
            :disabled="locked || page.number + 1 >= page.totalPages"
            @click="navigate(page!.number + 1)"
          >
            Дальше
          </button>
        </div><ExportControl
          kind="users"
          :selector="exportSelector"
        />
      </div>
    </template>
    <section
      v-if="(creating || selected) && admin.can('USERS_VIEW')"
      ref="editor"
      class="admin-panel"
      :inert="busy || (pending && !archiveRecovery)"
      tabindex="-1"
      :aria-label="creating ? 'Создание пользователя' : selected!.fullName"
    >
      <header class="admin-heading">
        <h2>{{ creating ? 'Новый пользователь' : selected!.fullName }}</h2><button
          class="web-button"
          :disabled="locked"
          @click="close"
        >
          Закрыть
        </button>
      </header>
      <div
        v-if="initialReceipt"
        class="web-notice"
        role="status"
      >
        <h3>Начальный доступ</h3><p>
          Логин: <output class="admin-secret">{{ initialReceipt.login }}</output>
        </p><p>
          Пароль: <output class="admin-secret">{{ initialReceipt.password }}</output>
        </p><p class="web-caption">
          Передай эти данные пользователю. Пароль не сохраняется в браузере.
        </p><button
          class="web-button"
          @click="initialReceipt = null"
        >
          Данные сохранены
        </button>
      </div>
      <dl
        v-if="selected"
        class="admin-pairs"
      >
        <dt>Логин</dt><dd>{{ selected.login }}</dd><dt>Состояние</dt><dd>{{ statusLabels[selected.status] }}</dd>
      </dl>
      <form
        v-if="canManage"
        class="admin-form web-stack"
        @submit.prevent="save"
      >
        <fieldset
          class="admin-form-grid"
          :disabled="locked"
        >
          <legend class="web-sr-only">
            Данные пользователя
          </legend><label
            v-if="creating"
            class="web-field"
          >Роль<select v-model="createRole"><option value="STUDENT">Студент</option><option value="TEACHER">Преподаватель</option><option
            v-if="full"
            value="ADMIN"
          >Администратор</option></select></label><label class="web-field">Фамилия<input
            v-model="lastName"
            required
            maxlength="128"
          ></label><label class="web-field">Имя<input
            v-model="firstName"
            required
            maxlength="128"
          ></label><label class="web-field">Отчество<input
            v-model="middleName"
            maxlength="128"
          ></label><label
            v-if="!creating || createRole === 'TEACHER'"
            class="web-field"
          >Табельный номер<input
            v-model="employeeNumber"
            :required="creating && createRole === 'TEACHER'"
          ></label><label class="web-field">Telegram ID<input
            v-model="telegramId"
            inputmode="numeric"
            pattern="[1-9][0-9]*"
            :required="creating && createRole === 'STUDENT'"
          ></label>
        </fieldset>
        <GroupPicker
          v-if="creating && createRole === 'STUDENT'"
          v-model="groupId"
          :client="users"
          :assert-current="admin.assertCurrent"
          label="Учебная группа"
          :disabled="locked"
        />
        <PermissionFields
          v-if="creating && createRole === 'ADMIN' && catalog"
          v-model:mode="accessMode"
          v-model:permissions="permissions"
          :catalog="catalog"
          :disabled="locked"
        />
        <button
          class="web-button web-button-primary"
          :disabled="
            locked ||
              (creating && createRole === 'STUDENT' && !groupId) ||
              (creating && createRole === 'ADMIN' && accessMode === 'LIMITED' && !permissions.length)
          "
        >
          {{ creating ? 'Создать учётную запись' : 'Сохранить данные' }}
        </button>
      </form>
      <template v-if="selected && !creating">
        <section
          v-if="canManage"
          class="admin-panel admin-form"
        >
          <h3>Текущие роли</h3><form
            class="web-stack"
            @submit.prevent="updateRole"
          >
            <label class="web-field">Роль<select
              v-model="roleToUpdate"
              :disabled="locked"
              @change="chooseRole"
            ><option value="STUDENT">Студент</option><option value="TEACHER">Преподаватель</option><option
              v-if="full"
              value="ADMIN"
            >Администратор</option></select></label><p
              v-if="activeGrant?.blockedReason"
              class="admin-warning"
            >
              {{ activeGrant.blockedReason }}
            </p><label class="web-field">Статус<select
              v-model="targetStatus"
              :disabled="locked"
            ><option
              v-for="status in applicableStatuses"
              :key="status"
              :value="status"
            >{{ statusLabels[status] }}</option></select></label><GroupPicker
              v-if="roleToUpdate === 'STUDENT' && !activeGrant"
              v-model="targetGroupId"
              :client="users"
              :assert-current="admin.assertCurrent"
              label="Группа для новой роли"
              :disabled="locked"
            /><button
              class="web-button"
              :disabled="locked || activeGrant?.canUpdate === false"
            >
              {{ activeGrant ? 'Обновить роль' : 'Добавить роль' }}
            </button>
          </form>
        </section>
        <form
          v-if="
            canManage && selected.roles.some((r) => r.role === 'STUDENT' && r.status === 'ACTIVE')
          "
          class="admin-panel admin-form"
          @submit.prevent="transfer"
        >
          <h3>Перевод в другую группу</h3><p class="web-caption">
            История в предыдущей группе сохранится. Права старосты и помощника не переносятся.
          </p><GroupPicker
            v-model="transferGroupId"
            :client="users"
            :assert-current="admin.assertCurrent"
            label="Новая группа"
            :disabled="locked"
          /><label class="web-field">Причина<input
            v-model="transferReason"
            required
            maxlength="500"
          ></label><button
            class="web-button"
            :disabled="locked || !transferGroupId"
          >
            Перевести студента
          </button>
        </form>
        <UserAccess
          v-if="full"
          :key="`access-${selected.id}`"
          :user-id="selected.id"
          :inert="credentialLocked"
          @locked="accessLocked = $event"
          @saved="admin.run(() => load(page?.number ?? 0))"
        />
        <UserCredentials
          v-if="admin.can('USER_CREDENTIALS_MANAGE')"
          :key="`credentials-${selected.id}`"
          :user="selected"
          :inert="accessLocked"
          @locked="credentialLocked = $event"
        />
        <div
          v-if="canArchive"
          class="admin-actions"
        >
          <button
            v-if="selected.status !== 'ARCHIVED'"
            class="web-button web-button-danger"
            :disabled="locked"
            @click="previewArchive"
          >
            Архивировать пользователя
          </button><button
            v-else
            class="web-button"
            :disabled="locked"
            @click="restore"
          >
            Восстановить без прежних прав
          </button>
        </div>
        <form
          v-if="archive && !archiveRecovery"
          class="admin-panel admin-form"
          @submit.prevent="archiveUser"
        >
          <h3>Архивировать {{ selected.fullName }}?</h3><p>
            Связанных групп: {{ archive.linkedGroupCount }}. Отметок в истории:
            {{ archive.attendanceMarksCount }}. Групп со старостой:
            {{ archive.activeHeadmanGroupCount }}. Назначений единственного преподавателя:
            {{ archive.soleTeacherAssignmentCount }}.
          </p><p>Активные права и сессии будут отозваны. История сохранится.</p><label
            v-if="archive.requiresPassword"
            class="web-field"
          >Твой пароль<input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
          ></label><div class="admin-actions">
            <button
              type="button"
              class="web-button"
              :disabled="locked"
              @click="archive = null"
            >
              Отмена
            </button><button
              class="web-button web-button-danger"
              :disabled="busy || childLocked"
            >
              Подтвердить архивацию
            </button>
          </div>
        </form>
      </template>
    </section>
    <form
      v-if="archiveRecovery && archive && canArchive"
      class="admin-panel admin-form"
      @submit.prevent="reconcileStoredArchive"
    >
      <h2>Восстановить архивацию пользователя {{ archive.userId }}?</h2><p>
        Связанных групп: {{ archive.linkedGroupCount }}. Отметок:
        {{ archive.attendanceMarksCount }}. Текущие роли и сессии будут отозваны, история
        сохранится.
      </p><label
        v-if="archive.requiresPassword"
        class="web-field"
      >Твой пароль<input
        v-model="password"
        type="password"
        autocomplete="current-password"
        required
        :disabled="busy"
      ></label><button
        class="web-button web-button-danger"
        :disabled="busy"
      >
        Подтвердить восстановление архивации
      </button>
    </form>
    <div
      v-if="pending"
      class="web-notice"
      role="status"
    >
      <p>Операция ещё не подтверждена. Новые изменения заблокированы.</p><button
        class="web-button"
        :disabled="busy"
        @click="resume"
      >
        Проверить результат
      </button><button
        v-if="users.failedIdentityOperation"
        class="web-button"
        :disabled="busy"
        @click="recover"
      >
        Восстановить остановленную операцию
      </button>
    </div>
  </section>
</template>
