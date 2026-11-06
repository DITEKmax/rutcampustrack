<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue';
import { RouterLink, useRoute, useRouter, onBeforeRouteUpdate } from 'vue-router';
import { useWebLiveRefresh } from '../../app/live';
import {
  AdminGroupsClient,
  type AdminGroup,
} from '../../../../mobile-core/src/features/admin-groups/admin-groups-client';
import {
  AdminUsersClient,
  type AdminUsersPage,
} from '../../../../mobile-core/src/features/admin-users/admin-users-client';
import { useAdmin, roleLabels, statusLabels } from './use-admin';
import {
  readUserRegistryQuery,
  userRegistryLocation,
  userRegistryExportSelector,
  type UserRegistryFilters,
} from './user-registry-query';
import AdminState from './AdminState.vue';
import UserRegistryTable from './UserRegistryTable.vue';
import ExportControl from './ExportControl.vue';
import './admin.pcss';

const admin = useAdmin();
const { loading, busy, error, notice } = admin;
const route = useRoute(),
  router = useRouter();
const groups = new AdminGroupsClient(admin.options),
  users = new AdminUsersClient(admin.options);
const group = shallowRef<AdminGroup | null>(null),
  page = shallowRef<AdminUsersPage | null>(null);
const initial = readUserRegistryQuery(route.query);
const filters = ref(initial.filters);
const applied = shallowRef(initial.filters);
const loadedFilters = shallowRef<UserRegistryFilters | null>(null);
const reportConfirmed = ref(false);
const exportSelector = computed(() => userRegistryExportSelector(loadedFilters.value ?? applied.value));
const reportMatches = computed(() =>
  reportConfirmed.value && !!page.value && JSON.stringify(loadedFilters.value) === JSON.stringify(applied.value),
);
let navigating = false,
  revision = 0;
function targetId() {
  const value = String(route.params.id ?? '');
  if (!/^[1-9][0-9]*$/.test(value) || !Number.isSafeInteger(Number(value)))
    throw new Error('Некорректная ссылка на группу.');
  return Number(value);
}
function clearReport() {
  group.value = null;
  page.value = null;
  loadedFilters.value = null;
}
async function load(number = 0, resolve = false) {
  reportConfirmed.value = false;
  const own = ++revision;
  let id: number;
  try {
    id = targetId();
  } catch (cause) {
    clearReport();
    throw cause;
  }
  if (resolve || group.value?.id !== id) {
    if (group.value?.id !== id) clearReport();
    const value = await groups.resolveNotificationGroup(id);
    admin.assertCurrent();
    if (own !== revision) return;
    if (!value || value.id !== id) {
      clearReport();
      throw new Error('Группа не найдена в доступном реестре.');
    }
    group.value = value;
  }
  const requested = { ...applied.value, groupId: id };
  applied.value = requested;
  const value = await users.listUsers({ ...requested, page: number, size: 30 }, admin.signal);
  admin.assertCurrent();
  if (own === revision) {
    page.value = value;
    loadedFilters.value = requested;
    reportConfirmed.value = true;
  }
}
async function navigate(number = 0) {
  if (busy.value) return;
  const id = targetId();
  const next = { ...filters.value, groupId: id };
  const query = userRegistryLocation(next, number);
  delete query.groupId;
  navigating = true;
  try {
    const failure = await router.replace({ query });
    admin.assertCurrent();
    if (failure) return;
    applied.value = next;
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
async function refresh() {
  reportConfirmed.value = false;
  await admin.requirePermission('GROUPS_VIEW');
  await admin.requirePermission('USERS_VIEW');
  await load(page.value?.number ?? 0, true);
}
watch(
  () => route.fullPath,
  () => {
    if (navigating) return;
    reportConfirmed.value = false;
    if (group.value && String(route.params.id ?? '') !== String(group.value.id)) clearReport();
    const current = readUserRegistryQuery(route.query);
    filters.value = current.filters;
    applied.value = current.filters;
    void admin.run(async () => {
      await admin.requirePermission('GROUPS_VIEW');
      await admin.requirePermission('USERS_VIEW');
      await load(current.page, true);
    });
  },
);
onBeforeRouteUpdate(() => !busy.value);
onMounted(() =>
  admin.init('GROUPS_VIEW', async () => {
    await admin.requirePermission('USERS_VIEW');
    await load(initial.page, true);
  }),
);
useWebLiveRefresh(() => admin.refresh(refresh), { blocked: busy });
</script>
<template>
  <section
    class="admin-page"
    :aria-busy="busy"
  >
    <header class="admin-heading">
      <div>
        <h1>Сохранённые связи группы{{ group ? ` ${group.name}` : '' }}</h1>
        <p class="web-caption">
          Текущие и сохранённые роли, связанные с этой группой. После перевода отображается новая
          группа; состав на прошлую дату здесь не восстанавливается.
        </p>
      </div><RouterLink
        class="web-button"
        :to="{ path: '/admin/groups', query: group ? { groupId: String(group.id), status: group.status } : {} }"
      >
        К группам
      </RouterLink>
    </header>
    <AdminState
      :loading="loading"
      :busy="busy"
      :error="error"
      :notice="notice"
      @retry="admin.run(refresh)"
    />
    <template v-if="group && admin.can('USERS_VIEW') && admin.can('GROUPS_VIEW')">
      <form
        class="admin-toolbar"
        @submit.prevent="navigate()"
      >
        <label class="web-field admin-search">Поиск<input
          v-model="filters.search"
          type="search"
          placeholder="ФИО или логин"
        ></label>
        <label class="web-field">Роль<select
          v-model="filters.role"
          @change="filters.roleStatus = ''"
        ><option value="">Все роли</option><option
          v-for="(label, code) in roleLabels"
          :key="code"
          :value="code"
        >{{ label }}</option></select></label>
        <label class="web-field">Статус роли<select
          v-model="filters.roleStatus"
          :disabled="!filters.role"
        ><option value="">Все статусы роли</option><option
          v-for="(label, code) in statusLabels"
          :key="code"
          :value="code"
        >{{ label }}</option></select></label>
        <label class="web-field">Учётная запись<select v-model="filters.status"><option value="">{{
          filters.roleStatus === 'ARCHIVED' ? 'Все состояния записи' : 'Без архивных записей'
        }}</option><option value="ACTIVE">Активные</option><option value="EXPELLED">Отчисленные</option><option value="SUSPENDED">Приостановленные</option><option value="ARCHIVED">Архивные</option></select></label>
        <label class="web-field">ФИО<select v-model="filters.sortDirection"><option value="asc">От А до Я</option><option value="desc">От Я до А</option></select></label>
        <button
          class="web-button"
          :disabled="busy"
        >
          Найти
        </button>
      </form>
      <section class="admin-panel">
        <h2>Связи группы</h2>
        <p class="web-caption">
          {{ group.status === 'ARCHIVED' ? 'Архивная' : group.status === 'DRAFT' ? 'Черновая' : 'Активная' }}
          группа. Просмотр без изменения прав.
        </p>
        <p
          v-if="page && !reportMatches"
          class="web-caption"
          role="status"
        >
          Показан последний успешно загруженный результат.
        </p>
        <template v-if="page">
          <UserRegistryTable
            v-if="page.items.length"
            :items="page.items"
            read-only
          /><p
            v-else
            role="status"
          >
            Для выбранных фильтров нет сохранённых связей.
          </p>
          <div class="admin-pager">
            <div class="admin-actions">
              <button
                class="web-button"
                :disabled="busy || page.number === 0"
                @click="navigate(page!.number - 1)"
              >
                Назад
              </button><span v-if="page.totalPages">{{ page.number + 1 }} / {{ page.totalPages }}</span><button
                class="web-button"
                :disabled="busy || page.number + 1 >= page.totalPages"
                @click="navigate(page!.number + 1)"
              >
                Дальше
              </button>
            </div>
          </div>
        </template>
      </section>
    </template>
    <section
      v-if="admin.can('USERS_VIEW') && admin.can('GROUPS_VIEW')"
      class="admin-panel"
    >
      <h2>Выгрузка связей</h2>
      <ExportControl
        kind="users"
        :selector="exportSelector"
        :disabled="busy || !group || !reportMatches"
      />
    </section>
  </section>
</template>
