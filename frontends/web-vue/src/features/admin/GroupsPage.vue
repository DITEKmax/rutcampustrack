<script setup lang="ts">
import WebSelect from '../../shared/WebSelect.vue';
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import { onBeforeRouteLeave, RouterLink } from 'vue-router';
import { useWebLiveRefresh } from '../../app/live';
import { webSession } from '../../auth/session';
import {
  AdminGroupsClient,
  AdminGroupsApiError,
  type AdminGroup,
  type AdminGroupsPage,
  type AdminGroupStatus,
  type AdminGroupStudyMode,
  type AdminGroupEducationLevel,
  type AdminGroupSort,
  type HeadmanRoster,
  type HeadmanAssignmentPreview,
  type PromotionSummary,
  type AdminGroupDeletionOperation,
} from '../../../../mobile-core/src/features/admin-groups/admin-groups-client';
import { useAdmin } from './use-admin';
import AdminState from './AdminState.vue';
import ExportControl from './ExportControl.vue';
import GroupDeletionPanel from './GroupDeletionPanel.vue';
import GroupRegistryTable from './GroupRegistryTable.vue';
import './admin.pcss';
const admin = useAdmin();
const owner = webSession.identity.value;
const currentOwner = computed(
  () =>
    webSession.phase.value === 'ready' &&
    webSession.identity.value === owner &&
    webSession.snapshot.value?.activeRole === 'ADMIN',
);
const { loading, busy, error, notice } = admin;
const client = new AdminGroupsClient(admin.options);
const page = shallowRef<AdminGroupsPage | null>(null);
const status = ref<AdminGroupStatus>('ACTIVE'),
  search = ref(''),
  course = ref<number | ''>(''),
  studyMode = ref<AdminGroupStudyMode | ''>(''),
  educationLevel = ref<AdminGroupEducationLevel | ''>(''),
  sort = ref<AdminGroupSort>('name,asc');
const applied = shallowRef(query());
const loadedQueryKey = ref('');
const filterPending = ref(false);
let filterTimer: ReturnType<typeof setTimeout> | null = null;
let queryQueued = false, listRevision = 0, alive = true;
function cancelFilterTimer() {
  if (filterTimer !== null) clearTimeout(filterTimer);
  filterTimer = null;
}
const filtersDirty = computed(() => JSON.stringify(query()) !== JSON.stringify(applied.value));
const selected = shallowRef<AdminGroup | null>(null);
const registryItems = computed(() => {
  const items = page.value?.items ?? [];
  const current = selected.value;
  if (!current || creating.value) return items;
  return items.some(group => group.id === current.id)
    ? items.map(group => group.id === current.id ? current : group)
    : [...items, current];
});
const pinnedGroupId = computed(() => selected.value && !page.value?.items.some(group => group.id === selected.value?.id) ? selected.value.id : undefined);
const creating = ref(false),
  alphabeticCode = ref(''),
  numericCode = ref(''),
  duration = ref<number | ''>(4),
  name = ref('');
const pending = ref(false),
  unknownWrite = ref(false),
  confirmArchive = ref(false);
const roster = shallowRef<HeadmanRoster | null>(null),
  headmanPreview = shallowRef<HeadmanAssignmentPreview | null>(null);
const candidateId = ref<number>(),
  promotion = shallowRef<PromotionSummary | null>(null);
const candidateValue = computed({ get: () => candidateId.value ?? '' as const, set: (value: number | '') => { candidateId.value = value === '' ? undefined : value; } });
const canView = computed(() => currentOwner.value && admin.can('GROUPS_VIEW'));
const canManage = computed(() => canView.value && admin.can('GROUPS_MANAGE'));
const deletionLocked = ref(false);
const locked = computed(
  () => busy.value || pending.value || unknownWrite.value || deletionLocked.value,
);
// A durable recovery key blocks writes, never retirement of its admitted authority.
const guardNavigation = computed(() => canView.value && locked.value);
admin.guardUnfinished(() => guardNavigation.value);
watch(
  () => [canView.value, canManage.value],
  ([canView, manage]) => {
    if (!canView) {
      cancelFilterTimer();
      queryQueued = false;
      filterPending.value = false;
      listRevision++;
      page.value = null;
      selected.value = null;
    }
    if (!canView || !manage) {
      creating.value = false;
      roster.value = null;
      headmanPreview.value = null;
      promotion.value = null;
      confirmArchive.value = false;
    }
  },
  { flush: 'sync' },
);
const exportSelector = computed(() => {
  const { sort: appliedSort, ...filters } = applied.value;
  const [field, direction] = appliedSort[0]!.split(',');
  return { ...filters, sorts: [{ field: field!, descending: direction === 'desc' }] };
});
function query() {
  return {
    status: status.value,
    search: search.value,
    ...(course.value ? { course: course.value } : {}),
    ...(studyMode.value ? { studyMode: studyMode.value } : {}),
    ...(educationLevel.value ? { educationLevel: educationLevel.value } : {}),
    sort: [sort.value],
  };
}
async function load(number = 0) {
  const own = ++listRevision, key = JSON.stringify(applied.value);
  const value = await client.listGroups({ ...applied.value, page: number, size: 30 }, admin.signal);
  admin.assertCurrent();
  if (!alive || own !== listRevision || key !== JSON.stringify(applied.value) || !canView.value) return;
  page.value = value;
  loadedQueryKey.value = key;
}
async function apply() {
  cancelFilterTimer();
  queryQueued = true;
  if (!alive || !canView.value || locked.value) return;
  queryQueued = false;
  filterPending.value = false;
  applied.value = query();
  selected.value = null;
  creating.value = false;
  roster.value = null;
  headmanPreview.value = null;
  promotion.value = null;
  confirmArchive.value = false;
  page.value = null;
  loadedQueryKey.value = '';
  await admin.refresh(() => load());
}
function scheduleApply() {
  queryQueued = true;
  filterPending.value = true;
  listRevision++;
  cancelFilterTimer();
  filterTimer = setTimeout(() => { filterTimer = null; void apply(); }, 250);
}
function resetFilters() {
  search.value = ''; status.value = 'ACTIVE'; course.value = '';
  studyMode.value = ''; educationLevel.value = ''; sort.value = 'name,asc';
  void apply();
}
watch(() => JSON.stringify(query()), scheduleApply, { flush: 'sync' });
watch(() => !locked.value && canView.value, ready => {
  if (ready && queryQueued && filterTimer === null) void apply();
});
onBeforeUnmount(() => { alive = false; listRevision++; cancelFilterTimer(); });
function open(group: AdminGroup) {
  if (locked.value) return;
  selected.value = group;
  creating.value = false;
  name.value = group.name;
  duration.value = group.trainingDurationYears ?? '';
  roster.value = null;
  headmanPreview.value = null;
  confirmArchive.value = false;
  promotion.value = null;
}
function beginCreate() {
  if (locked.value) return;
  selected.value = null;
  creating.value = true;
  alphabeticCode.value = '';
  numericCode.value = '';
  duration.value = 4;
}
async function save() {
  if (deletionLocked.value) return;
  await admin.run(async () => {
    if (
      duration.value === '' ||
      !Number.isInteger(duration.value) ||
      duration.value < 1 ||
      duration.value > 5
    )
      throw new RangeError('Укажи срок обучения от 1 до 5 лет.');
    let acknowledged = false;
    try {
      if (creating.value) {
        const value = await client.createGroup(
          {
            alphabeticCode: alphabeticCode.value,
            numericCode: numericCode.value,
            trainingDurationYears: duration.value,
          },
          admin.signal,
        );
        admin.assertCurrent();
        selected.value = value;
        creating.value = false;
        name.value = value.name;
      } else if (selected.value) {
        const id = selected.value.id;
        await client.renameGroup(selected.value, name.value, duration.value, admin.signal);
        admin.assertCurrent();
        acknowledged = true;
        selected.value = null;
        const confirmed = await client.resolveNotificationGroup(id);
        admin.assertCurrent();
        if (!confirmed) throw new Error('Сохранённая группа не найдена. Обнови реестр.');
        selected.value = confirmed;
        name.value = confirmed.name;
        duration.value = confirmed.trainingDurationYears ?? '';
      }
      admin.assertCurrent();
      acknowledged = true;
      await load();
      notice.value = 'Группа сохранена.';
    } catch (cause) {
      admin.assertCurrent();
      if (
        !acknowledged &&
        !(cause instanceof RangeError) &&
        (!(cause instanceof AdminGroupsApiError) || cause.response.status >= 500)
      )
        unknownWrite.value = true;
      throw cause;
    }
  }, 'GROUPS_MANAGE');
}
async function inspectWrite() {
  await admin.run(async () => {
    const expected = creating.value
      ? `${alphabeticCode.value.trim().toUpperCase()}-${numericCode.value.trim()}`
      : name.value.trim().toUpperCase();
    const result = await client.listGroups(
      { status: creating.value ? 'DRAFT' : 'ACTIVE', search: expected, size: 100 },
      admin.signal,
    );
    admin.assertCurrent();
    const found = result.items.find(
      (g) =>
        g.name === expected &&
        g.trainingDurationYears === duration.value &&
        (!selected.value || selected.value.id === g.id),
    );
    if (found) {
      unknownWrite.value = false;
      selected.value = found;
      creating.value = false;
      notice.value = 'Текущие данные группы подтверждены. Новая запись не отправлялась.';
      await load();
    } else
      notice.value =
        'Изменение пока не подтверждено. Повторная запись заблокирована; обнови результат позже.';
  });
}
async function identity(task: () => Promise<void>) {
  try {
    await task();
    admin.assertCurrent();
    client.acknowledgeIdentityOperation();
  } finally {
    pending.value = client.hasPendingIdentityOperation || Boolean(client.failedGroupOperationId);
  }
}
async function archiveOrRestore() {
  await admin.run(
    async () =>
      identity(async () => {
        if (!selected.value) return;
        if (selected.value.status === 'ARCHIVED')
          await client.restoreGroup(selected.value.id, admin.signal);
        else await client.archiveGroup(selected.value.id, selected.value.name, admin.signal);
        admin.assertCurrent();
        selected.value = null;
        confirmArchive.value = false;
        await load();
        notice.value = 'Состояние группы обновлено. История сохранена.';
      }),
    'GROUPS_MANAGE',
  );
}
async function loadRoster() {
  await admin.run(async () => {
    if (!selected.value) return;
    const value = await client.getHeadmanRoster(selected.value.id, admin.signal);
    admin.assertCurrent();
    roster.value = value;
    candidateId.value = value.currentHeadmanId ?? undefined;
  }, 'GROUPS_MANAGE');
}
async function previewHeadman() {
  await admin.run(async () => {
    if (!selected.value || !candidateId.value) return;
    const value = await client.previewHeadman(selected.value.id, candidateId.value, admin.signal);
    admin.assertCurrent();
    headmanPreview.value = value;
  }, 'GROUPS_MANAGE');
}
async function assign() {
  await admin.run(
    async () =>
      identity(async () => {
        const p = headmanPreview.value;
        if (!selected.value || !p) return;
        const result = await client.assignHeadman(
          selected.value.id,
          { studentId: p.candidateId, expectedHeadmanId: p.currentHeadmanId },
          admin.signal,
        );
        admin.assertCurrent();
        headmanPreview.value = null;
        roster.value = null;
        selected.value = null;
        await load();
        notice.value = `${result.headmanFio} — староста группы. Снято полномочий помощников: ${result.assistantsRevoked}.`;
      }),
    'GROUPS_MANAGE',
  );
}
async function previewPromotion(groupId?: number) {
  await admin.run(async () => {
    const value = await client.previewPromotion(groupId, admin.signal);
    admin.assertCurrent();
    promotion.value = value;
  }, 'GROUPS_MANAGE');
}
async function executePromotion() {
  await admin.run(
    async () =>
      identity(async () => {
        const p = promotion.value;
        if (!p) return;
        const result = await client.executePromotion(
          {
            cycleSemesterId: p.cycleSemesterId,
            previewVersion: p.previewVersion,
            groupId: p.groupId,
          },
          admin.signal,
        );
        admin.assertCurrent();
        promotion.value = result;
        selected.value = null;
        await load();
        notice.value = `Переведено групп: ${result.promoteCount}. Архивировано: ${result.archiveCount}.`;
      }),
    'GROUPS_MANAGE',
  );
}
async function resume() {
  await admin.run(async () =>
    identity(async () => {
      if (client.failedGroupOperationId && !client.hasPendingIdentityOperation) {
        const failed = await client.confirmGroupRecovery();
        admin.assertCurrent();
        if (!failed) throw new Error('Операция пока не готова к восстановлению.');
        await client.reconcileGroupOperation(failed, admin.signal);
        admin.assertCurrent();
      }
      await client.resumeIdentityOperation();
      admin.assertCurrent();
      selected.value = null;
      await load();
      notice.value = 'Результат операции подтверждён.';
    }),
  );
}
async function finishDeletion(operation: AdminGroupDeletionOperation) {
  admin.assertCurrent();
  selected.value = null;
  roster.value = null;
  headmanPreview.value = null;
  promotion.value = null;
  confirmArchive.value = false;
  notice.value =
    operation.phase === 'COMPLETED'
      ? 'Группа и её данные удалены. Аккаунты и другие назначения сохранены.'
      : operation.failure === 'NOT_ACCEPTED'
        ? 'Запрос не был принят. Данные группы сохранены, можно получить новый предпросмотр.'
        : 'Удаление отменено. Получи новый предпросмотр перед следующей попыткой.';
  // Clear the private view before a refresh that may itself fail after completion.
  page.value = null;
  await load();
}
onBeforeRouteLeave(() => {
  if (guardNavigation.value) {
    error.value = 'Сначала проверь результат операции с группой.';
    return false;
  }
});
onMounted(() => {
  if (currentOwner.value) void admin.init('GROUPS_VIEW', () => load());
});
const refreshDeferred = ref(false);
const refreshAvailable = computed(
  () =>
    !pending.value &&
    !unknownWrite.value &&
    !deletionLocked.value &&
    !selected.value &&
    !creating.value &&
    !promotion.value,
);
const canRefresh = computed(() => !busy.value && refreshAvailable.value);
async function refreshGroups() {
  if (!refreshDeferred.value || !refreshAvailable.value || !admin.can('GROUPS_VIEW')) return;
  refreshDeferred.value = false;
  await load(page.value?.number ?? 0);
}
watch(canRefresh, (ready) => {
  if (ready && refreshDeferred.value) void admin.refresh(refreshGroups);
});
useWebLiveRefresh(
  () =>
    admin.refresh(async () => {
      await admin.requirePermission('GROUPS_VIEW');
      refreshDeferred.value = true;
      await refreshGroups();
    }),
  // Privilege invalidation must run even while a preview or an unknown operation is open.
  { blocked: busy },
);
</script>
<template>
  <section
    class="admin-page"
    :aria-busy="busy"
  >
    <header class="admin-heading">
      <div>
        <h1>Группы</h1>
      </div><div
        v-if="canManage"
        class="admin-actions"
      >
        <button
          class="web-button web-button-primary"
          :disabled="locked"
          @click="beginCreate"
        >
          Создать группу
        </button><button
          class="web-button"
          :disabled="locked"
          @click="previewPromotion()"
        >
          Перевести группы на следующий курс
        </button>
      </div>
    </header>
    <AdminState
      :loading="loading"
      :busy="busy"
      :error="error"
      :notice="notice"
      @retry="admin.run(() => load())"
    />
    <form
      v-if="canView"
      class="admin-toolbar"
      @submit.prevent="apply"
    >
      <label class="web-field">Поиск<input
        v-model="search"
        type="search"
        placeholder="Код группы"
        :disabled="pending || unknownWrite || deletionLocked"
      ></label><WebSelect v-model="status" label="Состояние" :options="[{ value: 'ACTIVE', label: 'Активные' }, { value: 'DRAFT', label: 'Черновики' }, { value: 'ARCHIVED', label: 'Архивные' }]" :disabled="pending || unknownWrite || deletionLocked" /><WebSelect v-model="course" label="Курс" :options="[{ value: '' as const, label: 'Все курсы' }, ...[1, 2, 3, 4, 5].map(value => ({ value, label: String(value) }))]" :disabled="pending || unknownWrite || deletionLocked" /><WebSelect v-model="studyMode" label="Форма обучения" :options="[{ value: '', label: 'Все формы' }, { value: 'FULL_TIME', label: 'Очная' }, { value: 'PART_TIME', label: 'Очно-заочная' }, { value: 'EXTRAMURAL', label: 'Заочная' }]" :disabled="pending || unknownWrite || deletionLocked" /><WebSelect v-model="educationLevel" label="Образование" :options="[{ value: '', label: 'Все уровни' }, { value: 'BACHELOR', label: 'Бакалавриат' }, { value: 'SPECIALIST', label: 'Специалитет' }, { value: 'MASTER', label: 'Магистратура' }]" :disabled="pending || unknownWrite || deletionLocked" /><WebSelect v-model="sort" label="Сортировка" :options="[{ value: 'name,asc', label: 'По коду А–Я' }, { value: 'name,desc', label: 'По коду Я–А' }, { value: 'currentCourse,asc', label: 'По курсу' }, { value: 'programType,asc', label: 'По программе' }]" :disabled="pending || unknownWrite || deletionLocked" /><button
        type="button"
        class="web-button web-icon-button"
        aria-label="Сбросить поиск и фильтры групп"
        title="Сбросить поиск и фильтры"
        :disabled="pending || unknownWrite || deletionLocked"
        @click="resetFilters"
      ><MobileIcon name="close" /></button>
    </form>
    <template v-if="canView">
      <GroupRegistryTable
        :items="registryItems"
        :selected-id="selected?.id"
        :pinned-id="pinnedGroupId"
        :creating="creating"
        :disabled="locked"
        @open="open"
      >
        <template #editor>    <section
      v-if="(creating || selected) && canView"
      class="admin-panel admin-inline-editor"
    >
      <header class="admin-heading">
        <h2>{{ creating ? 'Новая группа' : selected!.name }}</h2><button
          class="web-button"
          :disabled="locked"
          @click="
            selected = null;
            creating = false;
          "
        >
          Закрыть
        </button>
      </header>
      <form
        v-if="canManage && (creating || selected?.status === 'ACTIVE')"
        class="admin-form web-stack"
        @submit.prevent="save"
      >
        <template v-if="creating">
          <label class="web-field">Буквенный код<input
            v-model="alphabeticCode"
            required
            pattern="[А-Яа-яЁё]{2,4}"
            maxlength="4"
            :disabled="locked"
          ></label><label class="web-field">Цифровой код<input
            v-model="numericCode"
            required
            pattern="[1-5][1-9][0-9]"
            maxlength="3"
            :disabled="locked"
          ></label>
        </template><label
          v-else
          class="web-field"
        >Код группы<input
          v-model="name"
          required
          :disabled="locked"
        ></label><label class="web-field">Срок обучения, лет<input
          v-model.number="duration"
          type="number"
          min="1"
          max="5"
          required
          :disabled="locked"
        ></label><button
          class="web-button web-button-primary"
          :disabled="locked"
        >
          Сохранить группу
        </button>
      </form>
      <template v-if="selected">
        <dl class="admin-pairs">
          <dt>Студентов</dt><dd>{{ selected.studentCount }}</dd><dt>Староста</dt><dd>{{ selected.headmanFio ?? 'Не назначен' }}</dd>
        </dl><div class="admin-actions">
          <button
            v-if="canManage && selected.status !== 'ARCHIVED'"
            class="web-button"
            :disabled="locked"
            @click="loadRoster"
          >
            Назначить или заменить старосту
          </button><button
            v-if="canManage && selected.status !== 'ARCHIVED'"
            class="web-button"
            :disabled="locked"
            @click="previewPromotion(selected!.id)"
          >
            Предпросмотр перевода
          </button><button
            v-if="canManage && selected.status !== 'ARCHIVED'"
            class="web-button web-button-danger"
            :disabled="locked"
            @click="confirmArchive = true"
          >
            Архивировать
          </button><button
            v-if="canManage && selected.status === 'ARCHIVED'"
            class="web-button"
            :disabled="locked"
            @click="archiveOrRestore"
          >
            Восстановить
          </button><RouterLink
            v-if="selected.status === 'ARCHIVED' && admin.can('USERS_VIEW')"
            class="web-button"
            :to="`/admin/groups/${selected.id}/history`"
          >
            Сохранённый состав
          </RouterLink>
        </div>
        <div
          v-if="confirmArchive"
          class="web-notice"
        >
          <h3>Архивировать {{ selected.name }}?</h3><p>
            Группа станет архивной, текущие полномочия будут отозваны. Учётные записи и история
            сохранятся.
          </p><div class="admin-actions">
            <button
              class="web-button"
              :disabled="locked"
              @click="confirmArchive = false"
            >
              Отмена
            </button><button
              class="web-button web-button-danger"
              :disabled="locked"
              @click="archiveOrRestore"
            >
              Подтвердить архивацию
            </button>
          </div>
        </div>
        <section
          v-if="roster"
          class="admin-panel admin-form"
        >
          <h3>Староста группы</h3><WebSelect v-model="candidateValue" label="Студент" :options="[{ value: '' as const, label: 'Выбери студента', disabled: true }, ...roster.candidates.map(candidate => ({ value: candidate.id, label: candidate.fio + (candidate.current ? ' — текущий староста' : '') }))]" :disabled="locked" @change="headmanPreview = null" /><p v-if="!roster.candidates.length">
            В группе нет подходящих кандидатов.
          </p><button
            class="web-button"
            :disabled="locked || !candidateId"
            @click="previewHeadman"
          >
            Показать последствия
          </button><div
            v-if="headmanPreview"
            class="web-notice"
          >
            <p>
              Новый староста: {{ headmanPreview.candidateFio }}. Текущий:
              {{ headmanPreview.currentHeadmanFio ?? 'не назначен' }}.
            </p><p>
              Помощников потеряют полномочия: {{ headmanPreview.assistantsToRevoke }}.<template
                v-if="headmanPreview.activatesDraft"
              >
                Группа станет активной.
              </template>
            </p><button
              class="web-button web-button-primary"
              :disabled="locked || headmanPreview.sameHeadman"
              @click="assign"
            >
              Подтвердить назначение
            </button>
          </div>
        </section>
      </template>
    </section>
</template>
      </GroupRegistryTable><p
        v-if="page && !registryItems.length"
        role="status"
      >
        Группы не найдены.
      </p><div v-if="page" class="admin-pager">
        <div class="admin-actions">
          <button
            class="web-button"
            :disabled="locked || page.number === 0"
            @click="admin.run(() => load(page!.number - 1))"
          >
            Назад
          </button><span v-if="page.totalPages">{{ page.number + 1 }} / {{ page.totalPages }}</span><button
            class="web-button"
            :disabled="locked || page.number + 1 >= page.totalPages"
            @click="admin.run(() => load(page!.number + 1))"
          >
            Дальше
          </button>
        </div><ExportControl
          kind="groups"
          :selector="exportSelector"
          :disabled="busy || filterPending || filtersDirty || loadedQueryKey !== JSON.stringify(applied)"
        />
      </div>
    </template>
    <GroupDeletionPanel
      :group="canView ? selected : null"
      :admin="admin"
      :client="client"
      :blocked="pending || unknownWrite"
      :on-settled="finishDeletion"
      @locked="deletionLocked = $event"
    />
    <section
      v-if="promotion && canManage"
      class="admin-panel"
    >
      <header class="admin-heading">
        <h2>{{ promotion.groupId ? 'Перевод выбранной группы' : 'Перевод групп' }}</h2><button
          class="web-button"
          :disabled="locked"
          @click="promotion = null"
        >
          Закрыть
        </button>
      </header><p>
        Цикл заканчивается {{ promotion.cycleDateTo }}. Будут переведены:
        {{ promotion.promoteCount }} групп / {{ promotion.promotedStudentCount }} студентов. Будут
        архивированы: {{ promotion.archiveCount }} групп /
        {{ promotion.archivedStudentCount }} студентов.
      </p><div class="admin-form-grid">
        <section>
          <h3>Следующий курс</h3><ul>
            <li
              v-for="item in promotion.toPromote"
              :key="item.id"
            >
              {{ item.from }} → {{ item.to }} · {{ item.studentCount }} студентов
            </li>
          </ul>
        </section><section>
          <h3>Завершение обучения</h3><ul>
            <li
              v-for="item in promotion.toArchive"
              :key="item.id"
            >
              {{ item.from }} · {{ item.studentCount }} студентов
            </li>
          </ul>
        </section>
      </div><p v-if="promotion.skipped.length">
        Пропущено: {{ promotion.skippedGroupCount }} групп. Уже обработанные группы не переводятся
        повторно.
      </p><ul
        v-if="promotion.conflicts.length"
        class="admin-danger"
      >
        <li
          v-for="(conflict, index) in promotion.conflicts"
          :key="index"
        >
          {{ conflict.message }}
        </li>
      </ul><button
        v-if="!promotion.executed"
        class="web-button web-button-primary"
        :disabled="
          locked ||
            promotion.conflictCount > 0 ||
            promotion.promoteCount + promotion.archiveCount === 0
        "
        @click="executePromotion"
      >
        Подтвердить перевод и архивацию
      </button>
    </section>
    <div
      v-if="pending || unknownWrite"
      class="web-notice"
      role="status"
    >
      <p>Результат изменения пока не подтверждён. Новые записи заблокированы.</p><button
        v-if="pending"
        class="web-button"
        :disabled="busy"
        @click="resume"
      >
        Проверить результат операции
      </button><button
        v-if="unknownWrite"
        class="web-button"
        :disabled="busy"
        @click="inspectWrite"
      >
        Сверить текущие данные группы
      </button>
    </div>
  </section>
</template>
