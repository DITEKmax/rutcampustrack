<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import type { AdminMapBuildingResponse, AdminMapDeletionPreview, AdminMapDeletionTarget, AdminMapFloorResponse } from '../../api/types'
import { AdminMapClient, MapApiError } from '../../api/map-client'
import { adminMapSelectionKey, captureAdminMapActionTarget, captureAdminMapUploadTarget, matchesAdminMapPreview } from './admin-map-state'
import type { AdminMapActionTarget } from './admin-map-state'
import './admin-map-screen.pcss'

const props = withDefaults(defineProps<{
  client: AdminMapClient
  theme?: 'dark' | 'light'
}>(), {
  theme: 'dark',
})

const buildings = ref<readonly AdminMapBuildingResponse[]>([])
const selectedBuildingId = ref<string | null>(null)
const selectedFloorId = ref<string | null>(null)
const buildingCode = ref<number | string>('')
const buildingLabel = ref('')
const floorCode = ref<number | string>('')
const floorLabel = ref('')
const png = ref<File | undefined>()
const svg = ref<File | undefined>()
const pngInput = ref<HTMLInputElement | null>(null)
const svgInput = ref<HTMLInputElement | null>(null)
const draftKey = ref<string | null>(null)
const loading = ref(true)
const initialLoadComplete = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const inventoryReady = ref(false)
const editBuildingCode = ref('')
const editBuildingLabel = ref('')
const editFloorCode = ref('')
const editFloorLabel = ref('')
const password = ref('')
const deletion = shallowRef<{
  target: AdminMapActionTarget
  preview: Readonly<AdminMapDeletionPreview>
  client: AdminMapClient
  generation: number | undefined
  revision: number
} | null>(null)
const controlsLocked = computed(() => saving.value || loading.value || !inventoryReady.value || Boolean(deletion.value))

const selectedBuilding = computed(() => buildings.value.find((item) => item.id === selectedBuildingId.value) ?? null)
const floors = computed<readonly AdminMapFloorResponse[]>(() => selectedBuilding.value?.floors ?? [])
const selectedFloor = computed(() => floors.value.find((item) => item.id === selectedFloorId.value) ?? null)
const floorDemandRows = computed(() => buildings.value.flatMap((building) => building.floors.map((floor) => ({
  buildingLabel: building.label,
  floor,
}))))

let componentMounted = false
let saveSequence = 0
let refreshSequence = 0
let selectionRevision = 0
let refreshController: AbortController | undefined

onMounted(() => {
  componentMounted = true
  void refresh()
})

onUnmounted(() => {
  componentMounted = false
  saveSequence += 1
  refreshSequence += 1
  refreshController?.abort()
  cancelDeletion()
})

watch(selectedBuildingId, () => {
  selectionRevision += 1
  cancelDeletion()
  if (!floors.value.some((floor) => floor.id === selectedFloorId.value)) {
    selectedFloorId.value = floors.value[0]?.id ?? null
  }
  clearFileDraft(true)
}, { flush: 'sync' })

watch(selectedFloorId, () => {
  selectionRevision += 1
  cancelDeletion()
  clearFileDraft(true)
}, { flush: 'sync' })

watch(selectedBuilding, (building) => {
  editBuildingCode.value = building?.code ?? ''
  editBuildingLabel.value = building?.label ?? ''
}, { flush: 'sync' })
watch(selectedFloor, (floor) => {
  editFloorCode.value = floor?.code ?? ''
  editFloorLabel.value = floor?.label ?? ''
}, { flush: 'sync' })

watch(() => [props.client, props.client.currentGeneration()] as const, () => {
  saveSequence += 1
  refreshSequence += 1
  refreshController?.abort()
  saving.value = false
  cancelDeletion()
  clearFileDraft(false)
  buildings.value = []
  selectedBuildingId.value = null
  selectedFloorId.value = null
  buildingCode.value = floorCode.value = ''
  buildingLabel.value = floorLabel.value = ''
  error.value = notice.value = null
  inventoryReady.value = false
  if (componentMounted) void refresh()
}, { flush: 'sync' })

async function refresh(): Promise<void> {
  const client = props.client
  const generation = client.currentGeneration()
  const requestId = ++refreshSequence
  const current = () => isCurrentRefresh(requestId) && props.client === client && client.isCurrentGeneration(generation)
  refreshController?.abort()
  const controller = new AbortController()
  refreshController = controller
  loading.value = true
  inventoryReady.value = false
  error.value = null
  try {
    const nextBuildings = await client.listBuildings(controller.signal)
    if (!current()) return
    buildings.value = nextBuildings
    inventoryReady.value = true
    if (!selectedBuildingId.value || !buildings.value.some((item) => item.id === selectedBuildingId.value)) {
      selectedBuildingId.value = buildings.value[0]?.id ?? null
    }
    const firstFloor = floors.value[0]
    if (!selectedFloorId.value || !floors.value.some((item) => item.id === selectedFloorId.value)) {
      selectedFloorId.value = firstFloor?.id ?? null
    }
  } catch (cause) {
    if (current() && !controller.signal.aborted) {
      error.value = mapErrorMessage(cause, 'Реестр карт не удалось загрузить. Обнови список перед следующей операцией.')
    }
  } finally {
    if (current()) {
      loading.value = false
      initialLoadComplete.value = true
      if (refreshController === controller) refreshController = undefined
    }
  }
}

function isCurrentRefresh(requestId: number): boolean {
  return componentMounted && requestId === refreshSequence
}

async function addBuilding(): Promise<void> {
  const code = normalizePositiveInteger(buildingCode.value)
  if (!code) {
    error.value = 'Номер корпуса должен быть положительным числом.'
    return
  }
  await runSave(async (isCurrent) => {
    await props.client.createBuilding(code, buildingLabel.value.trim() || undefined)
    if (!isCurrent()) return
    buildingCode.value = ''
    buildingLabel.value = ''
    notice.value = 'Корпус добавлен.'
    await refresh()
  })
}

async function addFloor(): Promise<void> {
  const code = normalizePositiveInteger(floorCode.value)
  const building = selectedBuilding.value
  if (!building || !code) {
    error.value = 'Выбери корпус и укажи положительный номер этажа.'
    return
  }
  const buildingId = building.id
  const startingSelectionRevision = selectionRevision
  await runSave(async (isCurrent) => {
    const createdFloor = await props.client.createFloor(buildingId, code, floorLabel.value.trim() || undefined)
    if (!isCurrent()) return
    floorCode.value = ''
    floorLabel.value = ''
    if (selectedBuildingId.value === buildingId && selectionRevision === startingSelectionRevision) {
      selectedFloorId.value = createdFloor.id
    }
    notice.value = `Этаж ${createdFloor.label} добавлен в корпус ${building.label}.`
    await refresh()
  }, `Не удалось добавить этаж в корпус ${building.label}. Проверь номер этажа.`)
}

function normalizePositiveInteger(value: number | string): string | null {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value > 0 ? String(value) : null
  }

  const trimmed = value.trim()
  if (!/^[1-9][0-9]*$/.test(trimmed)) {
    return null
  }

  const parsed = Number(trimmed)
  return Number.isSafeInteger(parsed) ? String(parsed) : null
}

function choosePng(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const key = adminMapSelectionKey(selectedBuildingId.value, selectedFloor.value?.id ?? null)
  if (!file || !key) return
  draftKey.value = key
  png.value = file
}

function chooseSvg(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const key = adminMapSelectionKey(selectedBuildingId.value, selectedFloor.value?.id ?? null)
  if (!file || !key) return
  draftKey.value = key
  svg.value = file
}

function clearFileDraft(showNotice: boolean): void {
  const hadFiles = Boolean(png.value || svg.value)
  png.value = undefined
  svg.value = undefined
  draftKey.value = null
  if (pngInput.value) pngInput.value.value = ''
  if (svgInput.value) svgInput.value.value = ''
  if (showNotice && hadFiles && componentMounted) {
    error.value = null
    notice.value = 'Выбранный этаж изменился. Файлы сняты; выбери их заново для нужного этажа.'
  }
}

async function upload(): Promise<void> {
  const target = captureAdminMapUploadTarget(
    buildings.value,
    selectedBuildingId.value,
    selectedFloorId.value,
    draftKey.value,
    { ...(png.value ? { png: png.value } : {}), ...(svg.value ? { svg: svg.value } : {}) },
  )
  if (!target) {
    error.value = 'Выбери этаж и приложи PNG или SVG.'
    return
  }
  await runSave(async (isCurrent) => {
    await props.client.uploadVersion(target.floorId, {
      ...(target.png ? { png: target.png } : {}),
      ...(target.svg ? { svg: target.svg } : {}),
    })
    if (!isCurrent()) return
    if (draftKey.value === target.key) clearFileDraft(false)
    notice.value = `Новая версия корпуса ${target.buildingLabel}, этаж ${target.floorLabel} опубликована.`
    await refresh()
  }, `Не удалось опубликовать версию корпуса ${target.buildingLabel}, этаж ${target.floorLabel}. Проверь файлы.`)
}

async function runSave(
  action: (isCurrent: () => boolean) => Promise<void>,
  failureMessage = 'Изменение не сохранено. Проверь данные и формат файлов.',
): Promise<void> {
  if (controlsLocked.value) return
  const client = props.client
  const generation = client.currentGeneration()
  const requestId = ++saveSequence
  const isCurrent = () => componentMounted && requestId === saveSequence
    && props.client === client && client.isCurrentGeneration(generation)
  saving.value = true
  error.value = null
  notice.value = null
  try {
    await action(isCurrent)
  } catch (cause) {
    if (isCurrent()) error.value = mapErrorMessage(cause, failureMessage)
  } finally {
    if (isCurrent()) saving.value = false
  }
}

function mapErrorMessage(cause: unknown, fallback: string): string {
  if (!(cause instanceof MapApiError)) return fallback
  if (cause.response.status === 403) return 'Недостаточно прав или пароль не подтверждён. Проверь активную сессию администратора.'
  if (cause.response.status === 401) return 'Сессия истекла. Войди снова; удаление автоматически не повторяется.'
  if (cause.response.status === 404) return 'Корпус или этаж уже недоступен. Обнови список.'
  if (cause.response.status === 409) return cause.problem?.detail || 'Данные изменились или номер уже занят. Обнови список и проверь данные.'
  if (cause.response.status === 400) return 'Проверь номер, название и пароль: сервер отклонил данные.'
  return fallback
}

function cancelDeletion(): void {
  deletion.value = null
  password.value = ''
}

async function editInventory(type: AdminMapDeletionTarget): Promise<void> {
  const target = captureAdminMapActionTarget(buildings.value, type, selectedBuildingId.value, selectedFloorId.value)
  const code = normalizePositiveInteger(type === 'BUILDING' ? editBuildingCode.value : editFloorCode.value)
  const label = (type === 'BUILDING' ? editBuildingLabel.value : editFloorLabel.value).trim()
  if (!target || !code || label.length > 255) {
    error.value = 'Выбери корпус или этаж, укажи положительный номер и название не длиннее 255 символов.'
    return
  }
  const client = props.client
  await runSave(async (isCurrent) => {
    if (type === 'BUILDING') await client.updateBuilding(target.id, code, label)
    else await client.updateFloor(target.id, code, label)
    if (!isCurrent()) return
    notice.value = 'Изменение сохранено. Обновляем список.'
    await refresh()
  })
}

async function previewDeletion(type: AdminMapDeletionTarget): Promise<void> {
  const target = captureAdminMapActionTarget(buildings.value, type, selectedBuildingId.value, selectedFloorId.value)
  if (!target) return
  const client = props.client
  const generation = client.currentGeneration()
  const revision = selectionRevision
  await runSave(async (isCurrent) => {
    const preview = await client.deletionPreview(target.type, target.id)
    if (!isCurrent() || revision !== selectionRevision) return
    if (!matchesAdminMapPreview(target, preview)) throw new Error('Preview target mismatch')
    deletion.value = Object.freeze({ target, preview: Object.freeze(preview), client, generation, revision })
  }, 'Не удалось получить последствия удаления. Обнови список и запроси их заново.')
}

async function confirmDeletion(): Promise<void> {
  const context = deletion.value
  if (!context || saving.value || !password.value || password.value.length > 256) return
  const { client, generation, revision, target, preview } = context
  if (props.client !== client || !client.isCurrentGeneration(generation) || revision !== selectionRevision
    || !matchesAdminMapPreview(target, preview)) {
    cancelDeletion()
    return
  }
  const operationId = crypto.randomUUID()
  const secret = password.value
  password.value = ''
  const requestId = ++saveSequence
  const isCurrent = () => componentMounted && requestId === saveSequence
    && props.client === client && client.isCurrentGeneration(generation)
  saving.value = true
  error.value = notice.value = null
  try {
    const result = await client.deleteInventory(target.type, target.id, {
      operationId, previewDigest: preview.previewDigest, password: secret,
    })
    if (!isCurrent()) return
    if (result.operationId !== operationId || result.targetId !== target.id
      || result.targetType !== target.type || result.status !== 'COMPLETED') throw new Error('Deletion result mismatch')
    cancelDeletion()
    notice.value = `${target.label}: окончательное удаление подтверждено сервером. Обновляем список.`
    await refresh()
    if (isCurrent() && inventoryReady.value && buildings.value.some((building) => target.type === 'BUILDING'
      ? building.id === target.id : building.floors.some((floor) => floor.id === target.id))) {
      error.value = 'Сервер подтвердил удаление, но объект ещё присутствует в реестре. Обнови список; запрос удаления не повторяется.'
    }
  } catch (cause) {
    if (!isCurrent()) return
    cancelDeletion()
    const uncertain = !(cause instanceof MapApiError) || cause.response.status >= 500
    const message = uncertain
      ? 'Исход удаления неизвестен: подтверждение сервера не получено. Запрос не повторяется. Проверь актуальный список перед новой операцией.'
      : mapErrorMessage(cause, 'Сервер отклонил удаление. Запроси последствия заново перед повторной попыткой.')
    await refresh()
    if (isCurrent()) error.value = `${target.label}: ${message}${inventoryReady.value ? ' Список обновлён с сервера.' : ' Список обновить не удалось.'}`
  } finally {
    if (isCurrent()) saving.value = false
  }
}

function periodLabel(period: string): string {
  return period === 'all_time' ? 'за всё время' : period
}

function stateLabel(state: string): string {
  return {
    absent: 'не загружен',
    processing: 'обрабатывается',
    ready: 'готов',
    failed: 'ошибка проверки',
  }[state] ?? state
}

function planAvailabilityMessage(svgState: string, pngState: string): string {
  const consequences: string[] = []
  if (svgState !== 'ready') consequences.push('этаж нельзя открыть в карте')
  if (pngState !== 'ready') consequences.push('PNG нельзя скачать')
  return consequences.length
    ? `Сейчас недоступно: ${consequences.join('; ')}.`
    : 'Этаж можно открыть в карте, PNG доступен для скачивания.'
}
</script>

<template>
  <main
    class="admin-map-screen"
    :data-theme="theme"
    aria-labelledby="admin-map-title"
  >
    <header class="admin-map-screen__header">
      <p>Администрирование карты</p>
      <h1 id="admin-map-title">
        Корпуса и этажи
      </h1>
    </header>

    <p
      v-if="loading && !initialLoadComplete"
      class="admin-map-state"
      role="status"
    >
      Загружаем реестр…
    </p>
    <div
      v-else
      class="admin-map-screen__content"
    >
      <p
        v-if="loading"
        class="admin-map-state"
        role="status"
      >
        Обновляем реестр…
      </p>
      <p
        v-if="error"
        class="admin-map-state admin-map-state--error"
        role="alert"
      >
        {{ error }}
      </p>
      <p
        v-if="notice"
        class="admin-map-state admin-map-state--success"
        role="status"
      >
        {{ notice }}
      </p>

      <section
        class="admin-map-card"
        aria-labelledby="admin-map-building-form"
      >
        <h2 id="admin-map-building-form">
          Добавить корпус
        </h2>
        <div class="admin-map-fields">
          <label><span>Номер</span><input
            v-model="buildingCode"
            inputmode="numeric"
            min="1"
            type="number"
          ></label>
          <label><span>Название</span><input
            v-model="buildingLabel"
            type="text"
            maxlength="120"
          ></label>
        </div>
        <button
          type="button"
          :disabled="controlsLocked"
          @click="addBuilding"
        >
          Добавить корпус
        </button>
      </section>

      <section
        class="admin-map-card"
        aria-labelledby="admin-map-floor-form"
      >
        <h2 id="admin-map-floor-form">
          Добавить этаж
        </h2>
        <label class="admin-map-field"><span>Корпус</span>
          <select
            v-model="selectedBuildingId"
            :disabled="controlsLocked || !buildings.length"
          >
            <option
              v-for="building in buildings"
              :key="building.id"
              :value="building.id"
            >{{ building.label }}</option>
          </select>
        </label>
        <div class="admin-map-fields">
          <label><span>Номер этажа</span><input
            v-model="floorCode"
            inputmode="numeric"
            min="1"
            type="number"
          ></label>
          <label><span>Название</span><input
            v-model="floorLabel"
            type="text"
            maxlength="120"
          ></label>
        </div>
        <button
          type="button"
          :disabled="controlsLocked || !selectedBuildingId"
          @click="addFloor"
        >
          Добавить этаж
        </button>
      </section>

      <section
        class="admin-map-card"
        aria-labelledby="admin-map-upload-form"
      >
        <h2 id="admin-map-upload-form">
          Новая версия схемы
        </h2>
        <label class="admin-map-field"><span>Этаж · {{ selectedBuilding?.label ?? 'корпус не выбран' }}</span>
          <select
            v-model="selectedFloorId"
            :disabled="controlsLocked || !floors.length"
          >
            <option
              v-for="floor in floors"
              :key="floor.id"
              :value="floor.id"
            >{{ floor.label }}</option>
          </select>
        </label>
        <p class="admin-map-help">
          Можно загрузить один формат. Второй сохранит предыдущую готовую версию, а при первом выпуске останется «не загружен».
        </p>
        <label class="admin-map-file"><span>SVG для просмотра</span><input
          ref="svgInput"
          accept="image/svg+xml,.svg"
          :disabled="!selectedFloor || controlsLocked"
          type="file"
          @change="chooseSvg"
        ></label>
        <label class="admin-map-file"><span>PNG для скачивания</span><input
          ref="pngInput"
          accept="image/png,.png"
          :disabled="!selectedFloor || controlsLocked"
          type="file"
          @change="choosePng"
        ></label>
        <button
          type="button"
          :disabled="controlsLocked || !selectedFloorId"
          @click="upload"
        >
          Опубликовать версию
        </button>
        <dl
          v-if="selectedFloor?.currentPlan"
          class="admin-map-slots"
        >
          <div><dt>SVG</dt><dd>{{ stateLabel(selectedFloor.currentPlan.svg.state) }}</dd></div>
          <div><dt>PNG</dt><dd>{{ stateLabel(selectedFloor.currentPlan.png.state) }}</dd></div>
        </dl>
        <p
          v-if="selectedFloor?.currentPlan"
          class="admin-map-help"
          role="status"
        >
          {{ planAvailabilityMessage(selectedFloor.currentPlan.svg.state, selectedFloor.currentPlan.png.state) }}
        </p>
      </section>

      <section
        class="admin-map-card"
        aria-labelledby="admin-map-edit-title"
      >
        <h2 id="admin-map-edit-title">
          Изменить выбранные корпус и этаж
        </h2>
        <p class="admin-map-help">
          Корпус: {{ selectedBuilding?.label ?? 'не выбран' }} · этаж: {{ selectedFloor?.label ?? 'не выбран' }}
        </p>
        <div
          v-if="selectedBuilding"
          class="admin-map-fields"
        >
          <label><span>Номер корпуса</span><input
            v-model="editBuildingCode"
            :disabled="controlsLocked"
            inputmode="numeric"
            type="text"
          ></label>
          <label><span>Название корпуса</span><input
            v-model="editBuildingLabel"
            :disabled="controlsLocked"
            type="text"
            maxlength="255"
          ></label>
        </div>
        <button
          :disabled="controlsLocked || !selectedBuilding"
          type="button"
          @click="editInventory('BUILDING')"
        >
          Сохранить корпус
        </button>
        <button
          class="admin-map-danger"
          :disabled="controlsLocked || !selectedBuilding"
          type="button"
          @click="previewDeletion('BUILDING')"
        >
          Удалить пустой корпус…
        </button>
        <div
          v-if="selectedFloor"
          class="admin-map-fields"
        >
          <label><span>Номер этажа</span><input
            v-model="editFloorCode"
            :disabled="controlsLocked"
            inputmode="numeric"
            type="text"
          ></label>
          <label><span>Название этажа</span><input
            v-model="editFloorLabel"
            :disabled="controlsLocked"
            type="text"
            maxlength="255"
          ></label>
        </div>
        <button
          :disabled="controlsLocked || !selectedFloor"
          type="button"
          @click="editInventory('FLOOR')"
        >
          Сохранить этаж
        </button>
        <button
          class="admin-map-danger"
          :disabled="controlsLocked || !selectedFloor"
          type="button"
          @click="previewDeletion('FLOOR')"
        >
          Удалить этаж со всеми версиями…
        </button>
      </section>

      <section
        v-if="deletion"
        class="admin-map-card"
        role="dialog"
        aria-labelledby="admin-map-delete-title"
        aria-describedby="admin-map-delete-help"
      >
        <h2 id="admin-map-delete-title">
          Окончательно удалить {{ deletion.preview.label }}?
        </h2>
        <p class="admin-map-help">
          Выбранный объект: {{ deletion.target.label }}
        </p>
        <p
          id="admin-map-delete-help"
          class="admin-map-help"
        >
          {{ deletion.target.type === 'FLOOR' ? 'Этаж и все версии его схем исчезнут у студентов, старост и преподавателей.' : 'Пустой корпус исчезнет из реестра.' }} Восстановление через интерфейс невозможно.
        </p>
        <dl class="admin-map-slots">
          <div><dt>Версий схем</dt><dd>{{ deletion.preview.versions }}</dd></div>
          <div><dt>Файлов PNG/SVG</dt><dd>{{ deletion.preview.assets }}</dd></div>
          <div><dt>Объём, байт</dt><dd>{{ deletion.preview.bytes }}</dd></div>
          <div><dt>Открытий</dt><dd>{{ deletion.preview.openCount }}</dd></div>
          <div><dt>Этажей останется в корпусе</dt><dd>{{ deletion.preview.remainingFloors }}</dd></div>
        </dl>
        <label class="admin-map-field"><span>Пароль текущего администратора</span><input
          v-model="password"
          :disabled="saving"
          type="password"
          autocomplete="current-password"
          maxlength="256"
        ></label>
        <button
          class="admin-map-danger"
          :disabled="saving || !password"
          type="button"
          @click="confirmDeletion"
        >
          {{ saving ? 'Удаляем…' : 'Подтвердить окончательное удаление' }}
        </button>
        <button
          :disabled="saving"
          type="button"
          @click="cancelDeletion"
        >
          Отмена
        </button>
      </section>
      <button
        class="admin-map-refresh"
        :disabled="saving || loading || Boolean(deletion)"
        type="button"
        @click="refresh"
      >
        Обновить список с сервера
      </button>

      <section
        class="admin-map-card"
        aria-labelledby="admin-map-demand-title"
      >
        <h2 id="admin-map-demand-title">
          Открытия этажей
        </h2>
        <p class="admin-map-help">
          Сколько раз пользователи открывали схему, за всё время.
        </p>
        <ul
          class="admin-map-demand"
          aria-label="Количество открытий схем по этажам"
        >
          <li
            v-for="row in floorDemandRows"
            :key="row.floor.id"
          >
            <span>
              {{ row.buildingLabel }} · {{ row.floor.label }}
              <small>{{ periodLabel(row.floor.openCountPeriod) }}</small>
            </span>
            <strong>{{ row.floor.openCount }}</strong>
          </li>
        </ul>
        <p
          v-if="!floorDemandRows.length"
          class="admin-map-help"
        >
          Этажей пока нет.
        </p>
      </section>
    </div>
  </main>
</template>
