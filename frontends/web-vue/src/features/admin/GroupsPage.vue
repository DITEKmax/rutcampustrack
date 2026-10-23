<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue';
import { onBeforeRouteLeave, RouterLink } from 'vue-router';
import { useWebLiveRefresh } from '../../app/live';
import {
  AdminGroupsClient,
  AdminGroupsApiError,
  adminGroupProgramTypes,
  type AdminGroup,
  type AdminGroupsPage,
  type AdminGroupStatus,
  type AdminGroupStudyMode,
  type AdminGroupEducationLevel,
  type AdminGroupSort,
  type HeadmanRoster,
  type HeadmanAssignmentPreview,
  type PromotionSummary,
} from '../../../../mobile-core/src/features/admin-groups/admin-groups-client';
import { useAdmin } from './use-admin';
import AdminState from './AdminState.vue';
import ExportControl from './ExportControl.vue';
import './admin.pcss';
const admin = useAdmin();
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
const selected = shallowRef<AdminGroup | null>(null);
const creating = ref(false),
  alphabeticCode = ref(''),
  numericCode = ref(''),
  duration = ref(4),
  name = ref('');
const pending = ref(false),
  unknownWrite = ref(false),
  confirmArchive = ref(false);
const roster = shallowRef<HeadmanRoster | null>(null),
  headmanPreview = shallowRef<HeadmanAssignmentPreview | null>(null);
const candidateId = ref<number>(),
  promotion = shallowRef<PromotionSummary | null>(null);
const canManage = computed(() => admin.can('GROUPS_MANAGE'));
const locked = computed(() => busy.value || pending.value || unknownWrite.value);
admin.guardUnfinished(() => locked.value);
const exportSelector = computed(() => {
  const [field, direction] = sort.value.split(',');
  return { ...applied.value, sorts: [{ field: field!, descending: direction === 'desc' }] };
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
  const value = await client.listGroups({ ...applied.value, page: number, size: 30 }, admin.signal);
  admin.assertCurrent();
  page.value = value;
}
function apply() {
  applied.value = query();
  selected.value = null;
  roster.value = null;
  promotion.value = null;
  void admin.run(() => load());
}
function open(group: AdminGroup) {
  selected.value = group;
  creating.value = false;
  name.value = group.name;
  duration.value = group.trainingDurationYears ?? 4;
  roster.value = null;
  headmanPreview.value = null;
  confirmArchive.value = false;
  promotion.value = null;
}
function beginCreate() {
  selected.value = null;
  creating.value = true;
  alphabeticCode.value = '';
  numericCode.value = '';
  duration.value = 4;
}
async function save() {
  await admin.run(async () => {
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
      } else if (selected.value)
        await client.renameGroup(selected.value, name.value, duration.value, admin.signal);
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
onBeforeRouteLeave(() => {
  if (locked.value) {
    error.value = 'Сначала проверь результат операции с группой.';
    return false;
  }
});
onMounted(() => admin.init('GROUPS_VIEW', () => load()));
useWebLiveRefresh(
  () =>
    admin.refresh(async () => {
      await admin.requirePermission('GROUPS_VIEW');
      await load(page.value?.number ?? 0);
    }),
  { blocked: () => locked.value || !!selected.value || creating.value || !!promotion.value },
);
</script>
<template>
  <section
    class="admin-page"
    :aria-busy="busy"
  >
    <header class="admin-heading">
      <div>
        <h1>Группы</h1><p
          v-if="page"
          class="web-caption"
        >
          Активные: {{ page.activeCount }} · Черновики: {{ page.draftCount }} · Архив:
          {{ page.archivedCount }}
        </p>
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
      v-if="admin.can('GROUPS_VIEW')"
      class="admin-toolbar"
      @submit.prevent="apply"
    >
      <label class="web-field">Поиск<input
        v-model="search"
        type="search"
        placeholder="Код группы"
      ></label><label class="web-field">Состояние<select v-model="status"><option value="ACTIVE">Активные</option><option value="DRAFT">Черновики</option><option value="ARCHIVED">Архивные</option></select></label><label class="web-field">Курс<select v-model="course"><option value="">Все курсы</option><option
        v-for="n in 5"
        :key="n"
        :value="n"
      >{{ n }}</option></select></label><label class="web-field">Форма обучения<select v-model="studyMode"><option value="">Все формы</option><option value="FULL_TIME">Очная</option><option value="PART_TIME">Очно-заочная</option><option value="EXTRAMURAL">Заочная</option></select></label><label class="web-field">Образование<select v-model="educationLevel"><option value="">Все уровни</option><option value="BACHELOR">Бакалавриат</option><option value="SPECIALIST">Специалитет</option><option value="MASTER">Магистратура</option></select></label><label class="web-field">Сортировка<select v-model="sort"><option value="name,asc">По коду А–Я</option><option value="name,desc">По коду Я–А</option><option value="currentCourse,asc">По курсу</option><option value="programType,asc">По программе</option></select></label><button
        class="web-button"
        :disabled="locked"
      >
        Найти
      </button>
    </form>
    <template v-if="page && admin.can('GROUPS_VIEW')">
      <div
        v-if="page.items.length"
        class="admin-table-wrap"
      >
        <table class="admin-table">
          <caption class="web-sr-only">
            Реестр групп
          </caption><thead>
            <tr>
              <th scope="col">
                Группа
              </th><th scope="col">
                Курс / срок
              </th><th scope="col">
                Программа
              </th><th scope="col">
                Студенты
              </th><th scope="col">
                Староста
              </th><th scope="col">
                Действия
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="group in page.items"
              :key="group.id"
            >
              <td>
                {{ group.name
                }}<span
                  v-if="group.draftReason"
                  class="admin-subtext"
                >{{ group.draftReason }}</span>
              </td><td>
                {{ group.currentCourse ?? '—' }} /
                {{ group.trainingDurationYears ?? 'Не указан' }}
              </td><td>
                {{
                  adminGroupProgramTypes.find((p) => p.digit === group.programTypeDigit)?.label ??
                    'Не указана'
                }}
              </td><td>{{ group.studentCount }}</td><td>{{ group.headmanFio ?? 'Не назначен' }}</td><td>
                <button
                  class="web-button"
                  :disabled="locked"
                  :aria-label="`Открыть группу ${group.name}`"
                  @click="open(group)"
                >
                  Открыть
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div><p
        v-else
        role="status"
      >
        Группы не найдены.
      </p><div class="admin-pager">
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
        />
      </div>
    </template>
    <section
      v-if="creating || selected"
      class="admin-panel"
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
            v-if="canManage && selected.status === 'ACTIVE'"
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
          <h3>Староста группы</h3><label class="web-field">Студент<select
            v-model="candidateId"
            :disabled="locked"
            @change="headmanPreview = null"
          ><option
            :value="undefined"
            disabled
          >Выбери студента</option><option
            v-for="candidate in roster.candidates"
            :key="candidate.id"
            :value="candidate.id"
          >{{ candidate.fio }}{{ candidate.current ? ' — текущий староста' : '' }}</option></select></label><p v-if="!roster.candidates.length">
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
        <div
          v-if="admin.can('DATA_DELETE')"
          class="web-notice"
        >
          <h3>Окончательное удаление группы</h3><p>
            Недоступно: серверный контракт точного предпросмотра, подтверждения паролем и сохранения
            учётных записей ещё не готов. Архивация доступна выше.
          </p><button
            class="web-button web-button-danger"
            disabled
          >
            Удалить окончательно
          </button>
        </div>
      </template>
    </section>
    <section
      v-if="promotion"
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
