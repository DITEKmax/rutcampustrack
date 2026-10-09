<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import type { AdminMapBuildingResponse, AdminMapDeletionPreview, AdminMapDeletionTarget, AdminMapFloorResponse } from '../../api/types'
import type { AdminAccessProfile } from '../admin-access/admin-access-client'
import { isAdminPasswordProofFailure } from '../../shared/admin-authority'
import { AdminMapClient, MapApiError } from '../../api/map-client'
import { captureAdminMapActionTarget, matchesAdminMapPreview, normalizeAdminMapCode } from './admin-map-state'
import type { AdminMapActionTarget } from './admin-map-state'
import AdminNotice from '../../shared/components/AdminNotice.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import './admin-map-screen.pcss'

const props = withDefaults(defineProps<{
  client: AdminMapClient
  authority: AdminAccessProfile | null
  theme?: 'dark' | 'light'
  showBack?: boolean
}>(), {
  theme: 'dark',
  showBack: true,
})
const emit = defineEmits<{ back: []; 'owner-error': [cause: unknown] }>()

const buildings = ref<readonly AdminMapBuildingResponse[]>([])
const selectedBuildingId = ref<string | null>(null)
const selectedFloorId = ref<string | null>(null)
const buildingCode = ref('')
const floorCode = ref('')
const fieldErrors = ref<{ building?: string; floor?: string; buildingEdit?: string; floorEdit?: string; password?: string; files?: string }>({})
const formOpen = ref(false)
const pendingUpload = ref<{ buildingCode: string; floorCode: string; priorVersion: string | null; reconciled: boolean } | null>(null)
const assetUrls = ref<{ svg: string | null; png: string | null }>({ svg: null, png: null })
const assetLoading = ref(false)
const assetError = ref<string | null>(null)
const warning = ref<string | null>(null)
const png = ref<File | undefined>()
const svg = ref<File | undefined>()
const pngInput = ref<HTMLInputElement | null>(null)
const svgInput = ref<HTMLInputElement | null>(null)
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
const canManage = computed(() => props.authority?.assigned === true && (props.authority.mode === 'FULL' || props.authority.mode === 'LIMITED') && props.authority.permissions.includes('CAMPUS_MAP_MANAGE'))
const canDelete = computed(() => canManage.value && props.authority?.permissions.includes('DATA_DELETE') === true)
const controlsLocked = computed(() => !canManage.value || saving.value || loading.value || !inventoryReady.value || Boolean(deletion.value))

const selectedBuilding = computed(() => buildings.value.find((item) => item.id === selectedBuildingId.value) ?? null)
const floors = computed<readonly AdminMapFloorResponse[]>(() => selectedBuilding.value?.floors ?? [])
const selectedFloor = computed(() => floors.value.find((item) => item.id === selectedFloorId.value) ?? null)

let componentMounted = false
let saveSequence = 0
let refreshSequence = 0
let selectionRevision = 0
let refreshController: AbortController | undefined
let assetSequence = 0

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
  clearAssets()
})

watch(selectedBuildingId, () => {
  selectionRevision += 1
  cancelDeletion()
  if (!floors.value.some((floor) => floor.id === selectedFloorId.value)) {
    selectedFloorId.value = floors.value[0]?.id ?? null
  }
  clearAssets()
}, { flush: 'sync' })

watch(selectedFloorId, () => {
  selectionRevision += 1
  cancelDeletion()
  clearAssets()
}, { flush: 'sync' })

watch(selectedBuilding, (building) => {
  editBuildingCode.value = building?.code ?? ''
  editBuildingLabel.value = building?.label ?? ''
}, { flush: 'sync' })
watch(selectedFloor, (floor) => {
  editFloorCode.value = floor?.code ?? ''
  editFloorLabel.value = floor?.label ?? ''
}, { flush: 'sync' })

watch(() => [props.client, props.client.currentGeneration(), props.authority?.userId, props.authority?.assigned, props.authority?.mode, props.authority?.version, props.authority?.permissions.join('|')] as const, () => {
  saveSequence += 1
  refreshSequence += 1
  refreshController?.abort()
  saving.value = false
  cancelDeletion()
  clearFileDraft()
  buildings.value = []
  selectedBuildingId.value = null
  selectedFloorId.value = null
  buildingCode.value = floorCode.value = ''
  error.value = notice.value = warning.value = null
  pendingUpload.value = null
  formOpen.value = false
  fieldErrors.value = {}
  inventoryReady.value = false
  if (componentMounted && canManage.value) void refresh()
}, { flush: 'sync' })

async function refresh(): Promise<void> {
  await refreshInventory(false)
}

async function refreshInventory(preserveSelection: boolean): Promise<void> {
  if (!canManage.value) { loading.value = false; return }
  const client = props.client
  const generation = client.currentGeneration()
  const requestId = ++refreshSequence
  const current = () => canManage.value && isCurrentRefresh(requestId) && props.client === client && client.isCurrentGeneration(generation)
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
    reconcileUpload(nextBuildings)
    if (!preserveSelection && (!selectedBuildingId.value || !buildings.value.some((item) => item.id === selectedBuildingId.value))) {
      selectedBuildingId.value = buildings.value[0]?.id ?? null
    }
    const firstFloor = floors.value[0]
    if (!preserveSelection && (!selectedFloorId.value || !floors.value.some((item) => item.id === selectedFloorId.value))) {
      selectedFloorId.value = firstFloor?.id ?? null
    }
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) emit('owner-error', cause)
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

function openForm(): void {
  if (controlsLocked.value || pendingUpload.value) return
  formOpen.value = !formOpen.value
}

async function upload(): Promise<void> {
  const requestedBuilding = normalizeAdminMapCode(buildingCode.value)
  const requestedFloor = normalizeAdminMapCode(floorCode.value)
  fieldErrors.value = {
    ...(!requestedBuilding ? { building: 'Введи от 1 до 64 цифр, начиная с 1–9, без пробелов.' } : {}),
    ...(!requestedFloor ? { floor: 'Введи от 1 до 64 цифр, начиная с 1–9, без пробелов.' } : {}),
    ...(!png.value && !svg.value ? { files: 'Приложи хотя бы один файл: PNG или SVG.' } : {}),
  }
  if (!requestedBuilding || !requestedFloor || fieldErrors.value.files || pendingUpload.value) return
  const target = Object.freeze({ buildingCode: requestedBuilding, floorCode: requestedFloor,
    ...(png.value ? { png: png.value } : {}), ...(svg.value ? { svg: svg.value } : {}) })
  const oldFloor = buildings.value.find(item => item.code === target.buildingCode)?.floors.find(item => item.code === target.floorCode)
  const client = props.client
  await runSave(async (isCurrent) => {
    try {
      const result = await client.uploadPlan(target)
      if (!isCurrent()) return
      clearFileDraft()
      formOpen.value = false
      buildingCode.value = floorCode.value = ''
      warning.value = null
      notice.value = result.status === 'replaced'
        ? `Схема заменена: корпус ${target.buildingCode}, этаж ${target.floorCode}.`
        : result.buildingCreated
          ? `Корпус ${target.buildingCode} создан, этаж ${target.floorCode} и схема добавлены.`
          : `Схема добавлена: корпус ${target.buildingCode}, этаж ${target.floorCode}.`
      await refreshInventory(true)
      if (!isCurrent() || !inventoryReady.value) return
      selectedBuildingId.value = result.plan.buildingId
      selectedFloorId.value = result.plan.floorId
      clearAssets()
    } catch (cause) {
      if (!isCurrent()) return
      if (cause instanceof StaleSessionGenerationError) { emit('owner-error', cause); return }
      const unknown = !(cause instanceof MapApiError) || cause.response.status >= 500
      if (unknown) {
        pendingUpload.value = { buildingCode: target.buildingCode, floorCode: target.floorCode,
          priorVersion: oldFloor?.currentPlan?.version ?? null, reconciled: false }
        warning.value = 'Исход загрузки неизвестен: подтверждение сервера не получено. Файлы сохранены. Проверь текущую схему перед новой загрузкой.'
      } else error.value = mapErrorMessage(cause, 'Схема не сохранена. Проверь файлы и повтори загрузку.')
    }
  })
}

function reconcileUpload(inventory: readonly AdminMapBuildingResponse[]): void {
  const pending = pendingUpload.value
  if (!pending) return
  const building = inventory.find(item => item.code === pending.buildingCode)
  const floor = building?.floors.find(item => item.code === pending.floorCode)
  pendingUpload.value = { ...pending, reconciled: true }
  warning.value = floor?.currentPlan && floor.currentPlan.version !== pending.priorVersion
    ? 'На сервере появилась новая версия схемы. Проверь её ниже: исход именно твоей загрузки не подтверждён. Повторной отправки не было.'
    : 'Список проверен: новая версия схемы не подтверждена. Повторной отправки не было. Перед новой загрузкой проверь текущую схему.'
  if (building && floor) { selectedBuildingId.value = building.id; selectedFloorId.value = floor.id }
}

function finishReconciliation(): void {
  if (!pendingUpload.value?.reconciled || saving.value || loading.value) return
  pendingUpload.value = null
  warning.value = null
  clearFileDraft()
  buildingCode.value = floorCode.value = ''
  formOpen.value = false
}

function choosePng(event: Event): void { png.value = (event.target as HTMLInputElement).files?.[0] }
function chooseSvg(event: Event): void { svg.value = (event.target as HTMLInputElement).files?.[0] }
function clearFileDraft(): void {
  png.value = undefined
  svg.value = undefined
  if (pngInput.value) pngInput.value.value = ''
  if (svgInput.value) svgInput.value.value = ''
}

function clearAssets(): void {
  assetSequence += 1
  for (const url of Object.values(assetUrls.value)) if (url) URL.revokeObjectURL(url)
  assetUrls.value = { svg: null, png: null }
  assetLoading.value = false
  assetError.value = null
}

async function loadAssets(): Promise<void> {
  clearAssets()
  const floor = selectedFloor.value
  const plan = floor?.currentPlan
  if (!floor || !plan || !canManage.value) return
  const client = props.client
  const generation = client.currentGeneration()
  const sequence = assetSequence
  const revision = selectionRevision
  const current = () => componentMounted && canManage.value && props.client === client
    && client.isCurrentGeneration(generation) && sequence === assetSequence && revision === selectionRevision
  assetLoading.value = true
  try {
    await Promise.all((['svg', 'png'] as const).map(async format => {
      const slot = plan[format]
      if (slot.state !== 'ready' || !slot.id) return
      const blob = await client.downloadAsset(floor.id, plan.version, format, slot.id)
      if (!current()) return
      const expectedType = format === 'png' ? 'image/png' : 'image/svg+xml'
      if (!blob.size || blob.type.split(';', 1)[0]?.toLowerCase() !== expectedType) throw new Error('Invalid map asset')
      assetUrls.value = { ...assetUrls.value, [format]: URL.createObjectURL(blob) }
    }))
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) emit('owner-error', cause)
    else if (current()) assetError.value = 'Не удалось открыть схему. Попробуй ещё раз.'
  } finally { if (current()) assetLoading.value = false }
}

async function runSave(
  action: (isCurrent: () => boolean) => Promise<void>,
  failureMessage = 'Изменение не сохранено. Проверь данные и формат файлов.',
): Promise<void> {
  if (controlsLocked.value) return
  const client = props.client
  const generation = client.currentGeneration()
  const requestId = ++saveSequence
  const isCurrent = () => canManage.value && componentMounted && requestId === saveSequence
    && props.client === client && client.isCurrentGeneration(generation)
  saving.value = true
  error.value = null
  notice.value = null
  try {
    await action(isCurrent)
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) emit('owner-error', cause)
    else if (isCurrent()) error.value = mapErrorMessage(cause, failureMessage)
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
  if (cause.response.status === 400) return cause.problem?.detail || 'Сервер отклонил данные. Проверь номера и файлы.'
  return fallback
}

function cancelDeletion(): void {
  deletion.value = null
  password.value = ''
  delete fieldErrors.value.password
}

async function editInventory(type: AdminMapDeletionTarget): Promise<void> {
  if (pendingUpload.value) return
  const target = captureAdminMapActionTarget(buildings.value, type, selectedBuildingId.value, selectedFloorId.value)
  const code = normalizeAdminMapCode(type === 'BUILDING' ? editBuildingCode.value : editFloorCode.value)
  const label = (type === 'BUILDING' ? editBuildingLabel.value : editFloorLabel.value).trim()
  if (!target || !code) {
    fieldErrors.value = { [type === 'BUILDING' ? 'buildingEdit' : 'floorEdit']: 'Укажи положительный номер.' }
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
  if (!canDelete.value || pendingUpload.value) return
  const target = captureAdminMapActionTarget(buildings.value, type, selectedBuildingId.value, selectedFloorId.value)
  if (!target) return
  const client = props.client
  const generation = client.currentGeneration()
  const revision = selectionRevision
  await runSave(async (isCurrent) => {
    const preview = await client.deletionPreview(target.type, target.id)
    if (!isCurrent() || !canDelete.value || revision !== selectionRevision) return
    if (!matchesAdminMapPreview(target, preview)) throw new Error('Preview target mismatch')
    deletion.value = Object.freeze({ target, preview: Object.freeze(preview), client, generation, revision })
  }, 'Не удалось получить последствия удаления. Обнови список и запроси их заново.')
}

async function confirmDeletion(): Promise<void> {
  if (!canDelete.value) { cancelDeletion(); return }
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
  const isCurrent = () => canDelete.value && componentMounted && requestId === saveSequence
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
    if (cause instanceof MapApiError && cause.response.status === 403 && isAdminPasswordProofFailure(cause.problem)) {
      fieldErrors.value.password = 'Пароль не подошёл. Попробуй ещё раз.'
      return
    }
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
  if (svgState !== 'ready' && pngState !== 'ready') consequences.push('схему нельзя открыть')
  if (pngState !== 'ready') consequences.push('PNG нельзя скачать')
  return consequences.length
    ? `Сейчас недоступно: ${consequences.join('; ')}.`
    : 'Схема доступна для просмотра, PNG можно скачать.'
}
</script>

<template>
  <main
    class="admin-map-screen"
    :data-theme="theme"
    aria-labelledby="admin-map-title"
  >
    <header class="admin-map-screen__header">
      <button
        v-if="showBack"
        class="admin-map-back"
        type="button"
        aria-label="Назад"
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button><h1 id="admin-map-title">
        Корпуса и этажи
      </h1>
      <button
        v-if="canManage"
        class="admin-map-add"
        type="button"
        aria-label="Добавить схему"
        :aria-expanded="formOpen"
        aria-controls="admin-map-upload-form"
        :disabled="controlsLocked || Boolean(pendingUpload)"
        @click="openForm"
      >
        <MobileIcon name="plus" />
      </button>
    </header>
    <AdminNotice
      v-if="!canManage"
      tone="warning"
      title="Управление картой недоступно"
    >
      Проверь права активного администратора.
    </AdminNotice>
    <p
      v-else-if="loading && !initialLoadComplete"
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
      <AdminNotice
        v-if="error"
        tone="danger"
        title="Изменение не выполнено"
      >
        {{ error }}
      </AdminNotice>
      <AdminNotice
        v-if="warning"
        tone="warning"
        title="Проверь результат"
      >
        {{ warning }}<template #actions>
          <button
            type="button"
            :disabled="saving || loading"
            @click="refresh"
          >
            Проверить список с сервера
          </button>
          <button
            v-if="pendingUpload?.reconciled"
            type="button"
            :disabled="saving || loading"
            @click="finishReconciliation"
          >
            Завершить проверку, очистить форму
          </button>
        </template>
      </AdminNotice>
      <AdminNotice
        v-if="notice"
        tone="success"
        title="Изменение сохранено"
      >
        {{ notice }}
      </AdminNotice>
      <section
        v-if="formOpen"
        id="admin-map-upload-form"
        class="admin-map-card"
        aria-labelledby="admin-map-form-title"
      >
        <h2 id="admin-map-form-title">
          Добавить или заменить схему
        </h2>
        <div class="admin-map-fields">
          <label class="admin-map-field"><span>Номер корпуса</span><input
            v-model="buildingCode"
            data-field="building-code"
            :disabled="controlsLocked || Boolean(pendingUpload)"
            :aria-invalid="Boolean(fieldErrors.building)"
            inputmode="numeric"
            type="text"
            maxlength="64"
          ><small
            v-if="fieldErrors.building"
            class="admin-map-field-error"
            role="alert"
          >{{ fieldErrors.building }}</small></label>
          <label class="admin-map-field"><span>Номер этажа</span><input
            v-model="floorCode"
            data-field="floor-code"
            :disabled="controlsLocked || Boolean(pendingUpload)"
            :aria-invalid="Boolean(fieldErrors.floor)"
            inputmode="numeric"
            type="text"
            maxlength="64"
          ><small
            v-if="fieldErrors.floor"
            class="admin-map-field-error"
            role="alert"
          >{{ fieldErrors.floor }}</small></label>
        </div>
        <p class="admin-map-help">
          Приложи PNG или SVG. При замене второй формат сохраняется.
        </p>
        <label
          class="admin-map-file"
          :class="{ 'admin-map-file--disabled': controlsLocked || Boolean(pendingUpload) }"
        ><span>SVG для просмотра</span><strong>{{ svg?.name ?? 'Выбрать файл SVG' }}</strong><small>Нажми, чтобы выбрать файл</small><input
          ref="svgInput"
          accept="image/svg+xml,.svg"
          :disabled="controlsLocked || Boolean(pendingUpload)"
          type="file"
          @change="chooseSvg"
        ></label>
        <label
          class="admin-map-file"
          :class="{ 'admin-map-file--disabled': controlsLocked || Boolean(pendingUpload) }"
        ><span>PNG для скачивания</span><strong>{{ png?.name ?? 'Выбрать файл PNG' }}</strong><small>Нажми, чтобы выбрать файл</small><input
          ref="pngInput"
          accept="image/png,.png"
          :disabled="controlsLocked || Boolean(pendingUpload)"
          type="file"
          @change="choosePng"
        ></label>
        <small
          v-if="fieldErrors.files"
          class="admin-map-field-error"
          role="alert"
        >{{ fieldErrors.files }}</small>
        <button
          type="button"
          :disabled="controlsLocked || Boolean(pendingUpload)"
          @click="upload"
        >
          {{ saving ? 'Сохраняем…' : 'Сохранить схему' }}
        </button>
      </section>
      <section
        class="admin-map-card"
        aria-labelledby="admin-map-browse-title"
      >
        <h2 id="admin-map-browse-title">
          Схемы этажей
        </h2>
        <div class="admin-map-fields">
          <label class="admin-map-field"><span>Корпус</span><span class="admin-map-select"><select
            v-model="selectedBuildingId"
            :disabled="controlsLocked || !buildings.length"
          ><option
            v-for="building in buildings"
            :key="building.id"
            :value="building.id"
          >{{ building.code }}</option></select><MobileIcon name="chevron-down" /></span></label>
          <label class="admin-map-field"><span>Этаж</span><span class="admin-map-select"><select
            v-model="selectedFloorId"
            :disabled="controlsLocked || !floors.length"
          ><option
            v-for="floor in floors"
            :key="floor.id"
            :value="floor.id"
          >{{ floor.code }}</option></select><MobileIcon name="chevron-down" /></span></label>
        </div>
        <p
          v-if="!selectedFloor"
          class="admin-map-help"
        >
          Этажей пока нет. Добавь схему через плюс вверху.
        </p>
        <p
          v-else-if="!selectedFloor.currentPlan"
          class="admin-map-help"
        >
          У этого этажа пока нет схемы.
        </p>
        <dl
          v-if="selectedFloor?.currentPlan"
          class="admin-map-slots"
        >
          <div><dt>SVG</dt><dd>{{ stateLabel(selectedFloor.currentPlan.svg.state) }}</dd></div><div><dt>PNG</dt><dd>{{ stateLabel(selectedFloor.currentPlan.png.state) }}</dd></div>
        </dl>
        <p
          v-if="selectedFloor?.currentPlan"
          class="admin-map-help"
          role="status"
        >
          {{ planAvailabilityMessage(selectedFloor.currentPlan.svg.state, selectedFloor.currentPlan.png.state) }}
        </p>
        <button
          v-if="selectedFloor?.currentPlan"
          type="button"
          :disabled="controlsLocked || assetLoading"
          @click="loadAssets"
        >
          {{ assetLoading ? 'Открываем…' : 'Открыть схему' }}
        </button>
        <p
          v-if="assetError"
          class="admin-map-field-error"
          role="alert"
        >
          {{ assetError }}
        </p>
        <img
          v-if="assetUrls.svg || assetUrls.png"
          class="admin-map-preview"
          :src="assetUrls.svg || assetUrls.png || undefined"
          :alt="`Схема: корпус ${selectedBuilding?.code}, этаж ${selectedFloor?.code}`"
        >
        <a
          v-if="assetUrls.png"
          class="admin-map-download"
          :href="assetUrls.png"
          :download="`корпус-${selectedBuilding?.code}-этаж-${selectedFloor?.code}.png`"
        >Скачать PNG: корпус {{ selectedBuilding?.code }}, этаж {{ selectedFloor?.code }}</a>
      </section>
      <section
        class="admin-map-card"
        aria-labelledby="admin-map-edit-title"
      >
        <h2 id="admin-map-edit-title">
          Изменить номера
        </h2>
        <p class="admin-map-help">
          Корпус {{ selectedBuilding?.code ?? 'не выбран' }} · этаж {{ selectedFloor?.code ?? 'не выбран' }}
        </p>
        <label
          v-if="selectedBuilding"
          class="admin-map-field"
        ><span>Номер корпуса</span><input
          v-model="editBuildingCode"
          :disabled="controlsLocked"
          inputmode="numeric"
          type="text"
          :aria-invalid="Boolean(fieldErrors.buildingEdit)"
        ><small
          v-if="fieldErrors.buildingEdit"
          class="admin-map-field-error"
          role="alert"
        >{{ fieldErrors.buildingEdit }}</small></label>
        <button
          :disabled="controlsLocked || Boolean(pendingUpload) || !selectedBuilding"
          type="button"
          @click="editInventory('BUILDING')"
        >
          Сохранить корпус
        </button>
        <button
          v-if="canDelete"
          class="admin-map-danger"
          :disabled="controlsLocked || Boolean(pendingUpload) || !selectedBuilding"
          type="button"
          data-action="delete"
          @click="previewDeletion('BUILDING')"
        >
          Удалить пустой корпус…
        </button>
        <label
          v-if="selectedFloor"
          class="admin-map-field"
        ><span>Номер этажа</span><input
          v-model="editFloorCode"
          :disabled="controlsLocked"
          inputmode="numeric"
          type="text"
          :aria-invalid="Boolean(fieldErrors.floorEdit)"
        ><small
          v-if="fieldErrors.floorEdit"
          class="admin-map-field-error"
          role="alert"
        >{{ fieldErrors.floorEdit }}</small></label>
        <button
          :disabled="controlsLocked || Boolean(pendingUpload) || !selectedFloor"
          type="button"
          @click="editInventory('FLOOR')"
        >
          Сохранить этаж
        </button>
        <button
          v-if="canDelete"
          class="admin-map-danger"
          :disabled="controlsLocked || Boolean(pendingUpload) || !selectedFloor"
          type="button"
          data-action="delete"
          @click="previewDeletion('FLOOR')"
        >
          Удалить этаж со всеми версиями…
        </button>
      </section>
      <section
        v-if="deletion && canDelete"
        class="admin-map-card"
        role="dialog"
        aria-labelledby="admin-map-delete-title"
        aria-describedby="admin-map-delete-help"
      >
        <h2 id="admin-map-delete-title">
          Окончательно удалить {{ deletion.target.type === 'BUILDING' ? 'корпус ' + selectedBuilding?.code : 'этаж ' + selectedFloor?.code }}?
        </h2>
        <p
          id="admin-map-delete-help"
          class="admin-map-help"
        >
          {{ deletion.target.type === 'FLOOR' ? 'Этаж и все версии его схем исчезнут у студентов, старост и преподавателей.' : 'Пустой корпус исчезнет из реестра.' }} Восстановление через интерфейс невозможно.
        </p>
        <dl class="admin-map-slots">
          <div><dt>Версий схем</dt><dd>{{ deletion.preview.versions }}</dd></div><div><dt>Файлов PNG/SVG</dt><dd>{{ deletion.preview.assets }}</dd></div><div><dt>Объём, байт</dt><dd>{{ deletion.preview.bytes }}</dd></div><div><dt>Открытий</dt><dd>{{ deletion.preview.openCount }}</dd></div><div><dt>Этажей останется</dt><dd>{{ deletion.preview.remainingFloors }}</dd></div>
        </dl>
        <label class="admin-map-field"><span>Пароль текущего администратора</span><input
          v-model="password"
          :disabled="saving"
          type="password"
          autocomplete="current-password"
          maxlength="256"
          :aria-invalid="Boolean(fieldErrors.password)"
        ><small
          v-if="fieldErrors.password"
          class="admin-map-field-error"
          role="alert"
        >{{ fieldErrors.password }}</small></label>
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
          data-action="cancel"
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
    </div>
  </main>
</template>
