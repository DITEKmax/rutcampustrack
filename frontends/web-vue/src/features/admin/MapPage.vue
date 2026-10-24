<script setup lang="ts">
import { useWebLiveRefresh } from '../../app/live';
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { AdminMapClient, MapApiError } from '../../../../mobile-core/src/api/map-client';
import type {
  AdminMapBuildingResponse,
  AdminMapFloorResponse,
  AdminMapPlan,
  AdminMapPlanDeletionPreview,
  MapFormat,
} from '../../../../mobile-core/src/api/types';
import { webSession } from '../../auth/session';
import { useAdmin } from './use-admin';
import AdminState from './AdminState.vue';
import './admin.pcss';
const admin = useAdmin();
const { loading, busy, error, notice } = admin;
const generation = webSession.generation.value;
const client = new AdminMapClient({
  ...admin.options,
  currentGeneration: () => {
    admin.assertCurrent();
    return generation;
  },
});
const inventory = shallowRef<readonly AdminMapBuildingResponse[]>([]);
const selectedBuilding = ref('');
const selected = shallowRef<{
  building: AdminMapBuildingResponse;
  floor: AdminMapFloorResponse;
} | null>(null);
const uploading = ref(false),
  buildingCode = ref(''),
  floorCode = ref(''),
  label = ref('');
const png = shallowRef<File | null>(null),
  svg = shallowRef<File | null>(null);
const fileEpoch = ref(0);
const uploadConfirmation = ref(false);
const editKind = ref<'building' | 'floor' | null>(null),
  editCode = ref(''),
  editLabel = ref('');
const plan = shallowRef<AdminMapPlan | null>(null),
  version = ref('');
const deletionPreview = shallowRef<AdminMapPlanDeletionPreview | null>(null),
  deleteFormat = ref<MapFormat | ''>(''),
  password = ref('');
type UnknownPublication = {
  buildingCode: string;
  floorCode: string;
  pngHash: string | null;
  svgHash: string | null;
};
type UnknownDeletion = {
  buildingCode: string;
  floorCode: string;
  operationId: string;
  previewDigest: string;
  format?: MapFormat;
};
const unknownPublication = shallowRef<UnknownPublication | null>(null),
  unknownDeletion = shallowRef<UnknownDeletion | null>(null);
const locked = computed(() => busy.value || !!unknownPublication.value || !!unknownDeletion.value);
admin.guardUnfinished(() => locked.value);
const rows = computed(() =>
  inventory.value
    .filter((b) => !selectedBuilding.value || b.id === selectedBuilding.value)
    .flatMap((building) => building.floors.map((floor) => ({ building, floor }))),
);
const statuses = {
  absent: 'Не загружен',
  processing: 'Обрабатывается',
  ready: 'Готов',
  failed: 'Ошибка обработки',
};
const objectUrls = new Set<string>();
// Current server command only clears the pointer; all-version/file final deletion is not ready.
const permanentDeletionAvailable = false;
async function load() {
  const value = await client.listBuildings(admin.signal);
  admin.assertCurrent();
  inventory.value = value;
  if (selectedBuilding.value && !value.some((b) => b.id === selectedBuilding.value))
    selectedBuilding.value = '';
}
function resetFiles() {
  png.value = null;
  svg.value = null;
  fileEpoch.value++;
}
function beginUpload(row?: { building: AdminMapBuildingResponse; floor: AdminMapFloorResponse }) {
  selected.value = row ?? null;
  buildingCode.value = row?.building.code ?? '';
  floorCode.value = row?.floor.code ?? '';
  label.value = row?.floor.currentPlan?.label ?? '';
  uploading.value = true;
  uploadConfirmation.value = false;
  deletionPreview.value = null;
  resetFiles();
}
function open(row: { building: AdminMapBuildingResponse; floor: AdminMapFloorResponse }) {
  selected.value = row;
  plan.value = row.floor.currentPlan;
  version.value = row.floor.currentPlan?.version ?? '';
  uploading.value = false;
  editKind.value = null;
  deletionPreview.value = null;
  resetFiles();
}
function chooseFile(event: Event, format: MapFormat) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null;
  if (format === 'png') png.value = file;
  else svg.value = file;
  uploadConfirmation.value = false;
}
async function hash(file: File | null): Promise<string | null> {
  if (!file) return null;
  const bytes = await file.arrayBuffer();
  admin.assertCurrent();
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  admin.assertCurrent();
  return [...new Uint8Array(digest)].map((v) => v.toString(16).padStart(2, '0')).join('');
}
async function publish() {
  await admin.run(async () => {
    const files = [png.value, svg.value].filter((file): file is File => !!file);
    if (!files.length) throw new Error('Выбери PNG или SVG.');
    if (files.some((file) => !file.size || file.size > 10 * 1024 * 1024))
      throw new Error('Каждый файл должен быть от 1 байта до 10 МиБ.');
    const target = await client.resolvePlanTarget(
      buildingCode.value,
      floorCode.value,
      admin.signal,
    );
    admin.assertCurrent();
    if (target.status === 'found' && target.floor?.currentPlan && !uploadConfirmation.value) {
      uploadConfirmation.value = true;
      return;
    }
    const reference: UnknownPublication = {
      buildingCode: buildingCode.value,
      floorCode: floorCode.value,
      pngHash: await hash(png.value),
      svgHash: await hash(svg.value),
    };
    admin.assertCurrent();
    try {
      const value = await client.uploadPlan({
        buildingCode: reference.buildingCode,
        floorCode: reference.floorCode,
        ...(label.value ? { label: label.value } : {}),
        ...(png.value ? { png: png.value } : {}),
        ...(svg.value ? { svg: svg.value } : {}),
      });
      admin.assertCurrent();
      plan.value = value.plan;
      uploading.value = false;
      uploadConfirmation.value = false;
      resetFiles();
      await load();
      notice.value =
        value.status === 'created'
          ? 'Схема опубликована.'
          : 'Схема заменена. Предыдущая версия сохранена.';
    } catch (cause) {
      admin.assertCurrent();
      if (!(cause instanceof MapApiError) || cause.response.status >= 500)
        unknownPublication.value = reference;
      throw cause;
    }
  }, 'CAMPUS_MAP_MANAGE');
}
async function inspectPublication() {
  await admin.run(async () => {
    const reference = unknownPublication.value;
    if (!reference) return;
    const target = await client.resolvePlanTarget(
      reference.buildingCode,
      reference.floorCode,
      admin.signal,
    );
    admin.assertCurrent();
    const current = target.floor?.currentPlan;
    if (
      current &&
      (!reference.pngHash || current.png.sha256 === reference.pngHash) &&
      (!reference.svgHash || current.svg.sha256 === reference.svgHash)
    ) {
      unknownPublication.value = null;
      plan.value = current;
      uploading.value = false;
      resetFiles();
      await load();
      notice.value =
        'Текущее содержимое схемы совпадает с выбранными файлами. Повторной загрузки не было.';
    } else
      notice.value = 'Публикация пока не подтверждена. Автоматической повторной загрузки не будет.';
  });
}
function beginEdit(kind: 'building' | 'floor') {
  if (!selected.value) return;
  editKind.value = kind;
  const target = kind === 'building' ? selected.value.building : selected.value.floor;
  editCode.value = target.code;
  editLabel.value = target.label;
}
async function saveLabels() {
  await admin.run(async () => {
    if (!selected.value || !editKind.value) return;
    if (editKind.value === 'building')
      await client.updateBuilding(selected.value.building.id, editCode.value, editLabel.value);
    else await client.updateFloor(selected.value.floor.id, editCode.value, editLabel.value);
    admin.assertCurrent();
    editKind.value = null;
    selected.value = null;
    await load();
    notice.value = 'Подписи обновлены.';
  }, 'CAMPUS_MAP_MANAGE');
}
async function readVersion() {
  await admin.run(async () => {
    if (!selected.value) return;
    const value = await client.getVersion(selected.value.floor.id, version.value, admin.signal);
    admin.assertCurrent();
    plan.value = value;
  }, 'CAMPUS_MAP_MANAGE');
}
async function download(format: MapFormat) {
  await admin.run(async () => {
    const current = plan.value;
    const slot = current?.[format];
    if (!current || !slot?.id || slot.state !== 'ready') return;
    const blob = await client.downloadAsset(current.floorId, current.version, format, slot.id);
    admin.assertCurrent();
    if (!blob.size || blob.type.split(';')[0] !== slot.contentType)
      throw new Error('Файл схемы не прошёл проверку ответа.');
    const url = URL.createObjectURL(blob);
    objectUrls.add(url);
    const link = document.createElement('a');
    link.href = url;
    link.download = `scheme-${current.version}.${format}`;
    link.rel = 'noopener';
    document.body.appendChild(link);
    link.click();
    link.remove();
    notice.value = 'Файл получен. Скачивание передано браузеру.';
  }, 'CAMPUS_MAP_MANAGE');
}
async function previewDeletion() {
  await admin.run(async () => {
    if (!selected.value) return;
    const value = await client.planDeletionPreview(
      selected.value.building.code,
      selected.value.floor.code,
      deleteFormat.value || undefined,
      admin.signal,
    );
    admin.assertCurrent();
    deletionPreview.value = value;
    password.value = '';
  }, 'DATA_DELETE');
}
async function removePlan() {
  await admin.run(async () => {
    if (!permanentDeletionAvailable)
      throw new Error(
        'Окончательное удаление всех версий и файлов пока не поддерживается сервером.',
      );
    const p = deletionPreview.value;
    if (!p || !admin.can('CAMPUS_MAP_MANAGE')) throw new Error('Нужны права управления картой.');
    const reference: UnknownDeletion = {
      buildingCode: p.buildingCode,
      floorCode: p.floorCode,
      previewDigest: p.previewDigest,
      operationId: crypto.randomUUID(),
      ...(p.format ? { format: p.format } : {}),
    };
    try {
      await client.deletePlanTarget({ ...reference, password: password.value });
      admin.assertCurrent();
      deletionPreview.value = null;
      selected.value = null;
      plan.value = null;
      await load();
      notice.value = 'Выбранные файлы схемы удалены.';
    } catch (cause) {
      admin.assertCurrent();
      if (!(cause instanceof MapApiError) || cause.response.status >= 500)
        unknownDeletion.value = reference;
      throw cause;
    } finally {
      password.value = '';
    }
  }, 'DATA_DELETE');
}
async function inspectDeletion() {
  await admin.run(async () => {
    const reference = unknownDeletion.value;
    if (!reference) return;
    const current = await client.resolvePlanTarget(
      reference.buildingCode,
      reference.floorCode,
      admin.signal,
    );
    admin.assertCurrent();
    const p = current.floor?.currentPlan;
    const absent =
      !p ||
      (reference.format
        ? p[reference.format].state === 'absent'
        : p.png.state === 'absent' && p.svg.state === 'absent');
    if (absent) {
      unknownDeletion.value = null;
      deletionPreview.value = null;
      selected.value = null;
      plan.value = null;
      await load();
      notice.value = 'Выбранные файлы сейчас отсутствуют. Повторного удаления не было.';
    } else
      notice.value =
        'Файлы пока существуют. Результат операции не подтверждён; повторное удаление заблокировано.';
  });
}
onBeforeRouteLeave(() => {
  if (locked.value) {
    error.value = 'Сначала проверь результат публикации или удаления.';
    return false;
  }
});
onBeforeUnmount(() => {
  objectUrls.forEach((url) => URL.revokeObjectURL(url));
  resetFiles();
  password.value = '';
});
onMounted(() => admin.init('CAMPUS_MAP_MANAGE', load));
useWebLiveRefresh(
  () =>
    admin.refresh(async () => {
      await admin.requirePermission('CAMPUS_MAP_MANAGE');
      await load();
    }),
  { blocked: () => locked.value || uploading.value || !!selected.value },
);
</script>
<template>
  <section
    class="admin-page"
    :aria-busy="busy"
  >
    <header class="admin-heading">
      <h1>Карта — схемы корпусов</h1><button
        v-if="admin.can('CAMPUS_MAP_MANAGE')"
        class="web-button web-button-primary"
        :disabled="locked"
        @click="beginUpload()"
      >
        Добавить схему
      </button>
    </header><AdminState
      :loading="loading"
      :busy="busy"
      :error="error"
      :notice="notice"
      @retry="admin.run(load)"
    />
    <template v-if="admin.can('CAMPUS_MAP_MANAGE')">
      <form
        v-if="uploading"
        class="admin-panel admin-form"
        @submit.prevent="publish"
      >
        <h2>{{ selected ? 'Заменить файлы схемы' : 'Новая схема этажа' }}</h2><p class="web-caption">
          PNG — для скачивания, SVG — для просмотра. Можно загрузить один формат: второй останется
          недоступен. Каждый файл — до 10 МиБ; SVG проверяется сервером.
        </p><fieldset
          class="admin-form-grid"
          :disabled="locked"
        >
          <legend class="web-sr-only">
            Новая схема
          </legend><label class="web-field">Номер корпуса<input
            v-model="buildingCode"
            required
            pattern="[1-9][0-9]{0,63}"
            inputmode="numeric"
            :readonly="!!selected"
            @input="uploadConfirmation = false"
          ></label><label class="web-field">Номер этажа<input
            v-model="floorCode"
            required
            pattern="[1-9][0-9]{0,63}"
            inputmode="numeric"
            :readonly="!!selected"
            @input="uploadConfirmation = false"
          ></label><label class="web-field">Название схемы<input
            v-model="label"
            maxlength="255"
          ></label><label
            :key="`png-${fileEpoch}`"
            class="web-field"
          >Файл PNG<input
            type="file"
            accept="image/png,.png"
            @change="chooseFile($event, 'png')"
          ></label><label
            :key="`svg-${fileEpoch}`"
            class="web-field"
          >Файл SVG<input
            type="file"
            accept="image/svg+xml,.svg"
            @change="chooseFile($event, 'svg')"
          ></label>
        </fieldset><div
          v-if="uploadConfirmation"
          class="web-notice"
        >
          <h3>На этом этаже уже есть схема</h3><p>
            Выбранные форматы заменят текущие файлы. Новую схему увидят все роли. Предыдущая версия
            сохранится.
          </p>
        </div><div class="admin-actions">
          <button
            type="button"
            class="web-button"
            :disabled="locked"
            @click="
              uploading = false;
              resetFiles();
            "
          >
            Отмена
          </button><button
            class="web-button web-button-primary"
            :disabled="locked || (!png && !svg)"
          >
            {{ uploadConfirmation ? 'Подтвердить замену' : 'Опубликовать схему' }}
          </button>
        </div>
      </form>
      <label class="web-field">Корпус<select v-model="selectedBuilding"><option value="">Все корпуса</option><option
        v-for="building in inventory"
        :key="building.id"
        :value="building.id"
      >{{ building.label }} · {{ building.code }}</option></select></label><div
        v-if="rows.length"
        class="admin-table-wrap"
        role="region"
        aria-label="Схемы корпусов и этажей"
        tabindex="0"
      >
        <table class="admin-table">
          <caption class="web-sr-only">
            Схемы этажей и просмотры за всё время
          </caption><thead>
            <tr>
              <th scope="col">
                Корпус
              </th><th scope="col">
                Этаж
              </th><th scope="col">
                PNG
              </th><th scope="col">
                SVG
              </th><th scope="col">
                Просмотры за всё время
              </th><th scope="col">
                Действия
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="row in rows"
              :key="row.floor.id"
            >
              <td>
                {{ row.building.label
                }}<span class="admin-subtext">{{ row.building.code }}</span>
              </td><td>
                {{ row.floor.label }}<span class="admin-subtext">{{ row.floor.code }}</span>
              </td><td>
                {{ statuses[row.floor.currentPlan?.png.state ?? 'absent']
                }}<span
                  v-if="row.floor.currentPlan?.png.state !== 'ready'"
                  class="admin-subtext"
                >Скачивание PNG недоступно</span>
              </td><td>
                {{ statuses[row.floor.currentPlan?.svg.state ?? 'absent']
                }}<span
                  v-if="row.floor.currentPlan?.svg.state !== 'ready'"
                  class="admin-subtext"
                >Просмотр схемы недоступен</span>
              </td><td>{{ row.floor.openCount.toLocaleString('ru-RU') }}</td><td>
                <button
                  class="web-button"
                  :disabled="locked"
                  :aria-label="`Открыть корпус ${row.building.code}, этаж ${row.floor.code}`"
                  @click="open(row)"
                >
                  Открыть
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div><p
        v-else-if="!loading"
        role="status"
      >
        Схем пока нет. Добавь первую схему, чтобы карта стала доступна пользователям.
      </p>
      <section
        v-if="selected && !uploading"
        class="admin-panel"
      >
        <header class="admin-heading">
          <h2>Корпус {{ selected.building.code }} · этаж {{ selected.floor.code }}</h2><button
            class="web-button"
            :disabled="locked"
            @click="
              selected = null;
              plan = null;
            "
          >
            Закрыть
          </button>
        </header><div class="admin-actions">
          <button
            class="web-button"
            :disabled="locked"
            @click="beginUpload(selected!)"
          >
            Заменить файлы
          </button><button
            class="web-button"
            :disabled="locked"
            @click="beginEdit('building')"
          >
            Изменить корпус
          </button><button
            class="web-button"
            :disabled="locked"
            @click="beginEdit('floor')"
          >
            Изменить этаж
          </button>
        </div>
        <form
          v-if="editKind"
          class="admin-form web-stack"
          @submit.prevent="saveLabels"
        >
          <h3>{{ editKind === 'building' ? 'Корпус' : 'Этаж' }}</h3><label class="web-field">Номер<input
            v-model="editCode"
            pattern="[1-9][0-9]{0,63}"
            required
            :disabled="locked"
          ></label><label class="web-field">Подпись<input
            v-model="editLabel"
            maxlength="255"
            required
            :disabled="locked"
          ></label><div class="admin-actions">
            <button
              type="button"
              class="web-button"
              :disabled="locked"
              @click="editKind = null"
            >
              Отмена
            </button><button
              class="web-button"
              :disabled="locked"
            >
              Сохранить подписи
            </button>
          </div>
        </form>
        <section class="web-stack">
          <h3>Версии схемы</h3><form
            class="admin-actions"
            @submit.prevent="readVersion"
          >
            <label class="web-field">Номер версии<input
              v-model="version"
              required
              pattern="[1-9][0-9]*"
              inputmode="numeric"
            ></label><button
              class="web-button"
              :disabled="locked || !version"
            >
              Открыть версию
            </button>
          </form><dl
            v-if="plan"
            class="admin-pairs"
          >
            <dt>Версия схемы</dt><dd>{{ plan.version }}</dd><dt>Редакция каталога</dt><dd>{{ plan.catalogRevision }}</dd><dt>PNG</dt><dd>
              {{ statuses[plan.png.state] }} · {{ plan.png.bytes.toLocaleString('ru-RU') }} байт
              <button
                v-if="plan.png.state === 'ready'"
                class="web-button"
                :disabled="locked"
                @click="download('png')"
              >
                Скачать PNG
              </button>
            </dd><dt>SVG</dt><dd>
              {{ statuses[plan.svg.state] }} · {{ plan.svg.bytes.toLocaleString('ru-RU') }} байт
              <button
                v-if="plan.svg.state === 'ready'"
                class="web-button"
                :disabled="locked"
                @click="download('svg')"
              >
                Скачать SVG
              </button>
            </dd>
          </dl><p v-else>
            У этажа пока нет опубликованной схемы.
          </p>
        </section>
        <section
          v-if="admin.can('DATA_DELETE')"
          class="admin-form web-stack"
        >
          <h3>Окончательное удаление</h3><p
            v-if="!permanentDeletionAvailable"
            class="web-notice"
          >
            Окончательное удаление недоступно: сервер пока не удаляет все версии и файлы схемы.
            Предпросмотр показывает только текущие файлы.
          </p><label class="web-field">Удалить<select
            v-model="deleteFormat"
            :disabled="locked"
            @change="deletionPreview = null"
          ><option value="">Оба формата схемы</option><option value="png">Только PNG</option><option value="svg">Только SVG</option></select></label><button
            class="web-button web-button-danger"
            :disabled="locked || !selected.floor.currentPlan"
            @click="previewDeletion"
          >
            Показать последствия удаления
          </button><form
            v-if="deletionPreview"
            class="admin-panel"
            @submit.prevent="removePlan"
          >
            <h3 class="admin-danger">
              Удалить {{ deletionPreview.label }}?
            </h3><p>
              Файлов: {{ deletionPreview.files }} ·
              {{ deletionPreview.bytes.toLocaleString('ru-RU') }} байт. Выбранные форматы исчезнут
              из доступных схем у всех ролей. Восстановление удалённых файлов недоступно.
            </p><label class="web-field">Твой пароль<input
              v-model="password"
              type="password"
              autocomplete="current-password"
              required
              :disabled="busy || !permanentDeletionAvailable"
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
                :disabled="locked || !permanentDeletionAvailable"
              >
                Подтвердить удаление
              </button>
            </div>
          </form>
        </section>
      </section>
    </template>
    <div
      v-if="unknownPublication || unknownDeletion"
      class="web-notice"
      role="status"
    >
      <p>Результат изменения неизвестен. Повторная запись заблокирована.</p><button
        v-if="unknownPublication"
        class="web-button"
        :disabled="busy"
        @click="inspectPublication"
      >
        Проверить опубликованные файлы
      </button><button
        v-if="unknownDeletion"
        class="web-button"
        :disabled="busy"
        @click="inspectDeletion"
      >
        Проверить результат удаления
      </button>
    </div>
  </section>
</template>
