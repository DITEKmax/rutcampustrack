<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import type { AdminMapBuildingResponse, AdminMapDeletionPreview, AdminMapDeletionTarget, AdminMapFloorResponse } from '../../api/types'
import type { AdminAccessProfile } from '../admin-access/admin-access-client'
import { isAdminPasswordProofFailure } from '../../shared/admin-authority'
import { AdminMapClient, MapApiError } from '../../api/map-client'
import { adminMapSelectionKey, captureAdminMapActionTarget, captureAdminMapUploadTarget, matchesAdminMapPreview } from './admin-map-state'
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
const buildingCode = ref<number | string>('')
const floorCode = ref<number | string>('')
const fieldErrors = ref<{ building?: string; floor?: string; buildingEdit?: string; floorEdit?: string; password?: string }>({})
const pendingCreate = ref<{ buildingCode: string; floorCode: string; buildingId: string | undefined; unknown: boolean } | null>(null)
const warning = ref<string | null>(null)
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

watch(() => [props.client, props.client.currentGeneration(), props.authority?.userId, props.authority?.assigned, props.authority?.mode, props.authority?.version, props.authority?.permissions.join('|')] as const, () => {
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
  error.value = notice.value = warning.value = null
  pendingCreate.value = null
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
    reconcileCreation(nextBuildings)
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

async function addFloor(): Promise<void> {
  const requestedBuilding = normalizePositiveInteger(buildingCode.value)
  const requestedFloor = normalizePositiveInteger(floorCode.value)
  fieldErrors.value = {
    ...(!requestedBuilding ? { building: 'Укажи положительный номер корпуса.' } : {}),
    ...(!requestedFloor ? { floor: 'Укажи положительный номер этажа.' } : {}),
  }
  if (!requestedBuilding || !requestedFloor || pendingCreate.value?.unknown) return
  if (buildings.value.some(building => building.code === requestedBuilding && building.floors.some(floor => floor.code === requestedFloor))) {
    fieldErrors.value = { floor: 'Такой этаж уже есть в корпусе. Выбери его ниже.' }
    return
  }
  const client = props.client
  const startingSelectionRevision = selectionRevision
  await runSave(async (isCurrent) => {
    const current = () => isCurrent() && selectionRevision === startingSelectionRevision
    const intent = { buildingCode: requestedBuilding, floorCode: requestedFloor, unknown: false,
      buildingId: buildings.value.find(building => building.code === requestedBuilding)?.id
        ?? (pendingCreate.value?.buildingCode === requestedBuilding ? pendingCreate.value.buildingId : undefined) }
    pendingCreate.value = intent
    try {
      if (!intent.buildingId) {
        const building = await client.createBuilding(requestedBuilding)
        if (!current()) return
        intent.buildingId = building.id
        buildings.value = [...buildings.value.filter(item => item.id !== building.id), building]
        pendingCreate.value = { ...intent }
        notice.value = `Корпус ${requestedBuilding} создан.`
      }
      if (!current()) return
      const floor = await client.createFloor(intent.buildingId, requestedFloor)
      if (!current()) return
      pendingCreate.value = null
      warning.value = null
      floorCode.value = ''
      notice.value = `Этаж ${requestedFloor} добавлен в корпус ${requestedBuilding}.`
      await refreshInventory(true)
      if (!current() || !inventoryReady.value) return
      selectedBuildingId.value = intent.buildingId
      selectedFloorId.value = floor.id
    } catch (cause) {
      if (!current()) return
      if (cause instanceof StaleSessionGenerationError) { emit('owner-error', cause); return }
      const unknown = !(cause instanceof MapApiError) || cause.response.status >= 500
      pendingCreate.value = { ...intent, unknown }
      warning.value = unknown
        ? `${intent.buildingId ? `Корпус ${requestedBuilding} сохранён. ` : ''}Исход создания ${intent.buildingId ? 'этажа' : 'корпуса'} неизвестен. Проверь список с сервера перед повторной попыткой.`
        : intent.buildingId ? `Корпус ${requestedBuilding} сохранён. Этаж ${requestedFloor} не добавлен; исправь данные и повтори только добавление этажа.` : null
      if (!unknown) {
        const message = mapErrorMessage(cause, 'Этаж не добавлен. Проверь номер.')
        if (cause instanceof MapApiError && [400, 409].includes(cause.response.status)) {
          fieldErrors.value = { [intent.buildingId ? 'floor' : 'building']: message }
        } else error.value = message
      }
    }
  })
}

function reconcileCreation(inventory: readonly AdminMapBuildingResponse[]): void {
  const pending = pendingCreate.value
  if (!pending) return
  const building = inventory.find(item => item.code === pending.buildingCode)
  if (building?.floors.some(item => item.code === pending.floorCode)) {
    pendingCreate.value = null
    warning.value = null
    notice.value = `Список подтверждает: корпус ${pending.buildingCode}, этаж ${pending.floorCode} сохранены.`
  } else if (pending.unknown) {
    pendingCreate.value = { ...pending, ...(building ? { buildingId: building.id } : {}), unknown: false }
    warning.value = `${building ? `Корпус ${pending.buildingCode} есть в реестре. ` : ''}Этаж ${pending.floorCode} не найден. Можно явно повторить добавление; существующий корпус будет использован.`
  }
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
  const client = props.client
  await runSave(async (isCurrent) => {
    await client.uploadVersion(target.floorId, {
      ...(target.png ? { png: target.png } : {}),
      ...(target.svg ? { svg: target.svg } : {}),
    })
    if (!isCurrent()) return
    if (draftKey.value === target.key) clearFileDraft(false)
    notice.value = `Новая версия: ${target.buildingLabel}, ${target.floorLabel}.`
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
  if (cause.response.status === 400) return 'Проверь номер и пароль: сервер отклонил данные.'
  return fallback
}

function cancelDeletion(): void {
  deletion.value = null
  password.value = ''
  delete fieldErrors.value.password
}

async function editInventory(type: AdminMapDeletionTarget): Promise<void> {
  const target = captureAdminMapActionTarget(buildings.value, type, selectedBuildingId.value, selectedFloorId.value)
  const code = normalizePositiveInteger(type === 'BUILDING' ? editBuildingCode.value : editFloorCode.value)
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
  if (!canDelete.value) return
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
        class="admin-map-card"
        aria-labelledby="admin-map-floor-form"
      >
        <h2 id="admin-map-floor-form">
          Добавить этаж
        </h2>
        <div class="admin-map-fields">
          <label class="admin-map-field"><span>Номер корпуса</span><input
            v-model="buildingCode"
            data-field="building-code"
            :disabled="controlsLocked || pendingCreate?.unknown"
            :aria-invalid="Boolean(fieldErrors.building)"
            inputmode="numeric"
            type="text"
          ><small
            v-if="fieldErrors.building"
            class="admin-map-field-error"
            role="alert"
          >{{ fieldErrors.building }}</small></label>
          <label class="admin-map-field"><span>Номер этажа</span><input
            v-model="floorCode"
            data-field="floor-code"
            :disabled="controlsLocked || pendingCreate?.unknown"
            :aria-invalid="Boolean(fieldErrors.floor)"
            inputmode="numeric"
            type="text"
          ><small
            v-if="fieldErrors.floor"
            class="admin-map-field-error"
            role="alert"
          >{{ fieldErrors.floor }}</small></label>
        </div>
        <p class="admin-map-help">
          Если корпуса ещё нет, он будет создан вместе с этажом.
        </p>
        <button
          type="button"
          :disabled="controlsLocked || pendingCreate?.unknown"
          @click="addFloor"
        >
          {{ saving ? 'Сохраняем…' : 'Добавить этаж' }}
        </button>
      </section>
      <section
        class="admin-map-card"
        aria-labelledby="admin-map-upload-form"
      >
        <h2 id="admin-map-upload-form">
          Новая версия схемы
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
          v-if="!floors.length"
          class="admin-map-help"
        >
          Добавь этаж, чтобы загрузить схему.
        </p>
        <p class="admin-map-help">
          SVG открывается в карте, PNG можно скачать. Можно заменить один формат, сохранив второй.
        </p>
        <label
          class="admin-map-file"
          :class="{ 'admin-map-file--disabled': !selectedFloor || controlsLocked }"
        ><span>SVG для просмотра</span><strong>{{ svg?.name ?? 'Выбрать файл SVG' }}</strong><small>Нажми или перетащи файл сюда</small><input
          ref="svgInput"
          accept="image/svg+xml,.svg"
          :disabled="!selectedFloor || controlsLocked"
          type="file"
          @change="chooseSvg"
        ></label>
        <label
          class="admin-map-file"
          :class="{ 'admin-map-file--disabled': !selectedFloor || controlsLocked }"
        ><span>PNG для скачивания</span><strong>{{ png?.name ?? 'Выбрать файл PNG' }}</strong><small>Нажми или перетащи файл сюда</small><input
          ref="pngInput"
          accept="image/png,.png"
          :disabled="!selectedFloor || controlsLocked"
          type="file"
          @change="choosePng"
        ></label>
        <button
          type="button"
          :disabled="controlsLocked || !selectedFloorId || (!png && !svg)"
          @click="upload"
        >
          Опубликовать версию
        </button>
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
          :disabled="controlsLocked || !selectedBuilding"
          type="button"
          @click="editInventory('BUILDING')"
        >
          Сохранить корпус
        </button>
        <button
          v-if="canDelete"
          class="admin-map-danger"
          :disabled="controlsLocked || !selectedBuilding"
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
          :disabled="controlsLocked || !selectedFloor"
          type="button"
          @click="editInventory('FLOOR')"
        >
          Сохранить этаж
        </button>
        <button
          v-if="canDelete"
          class="admin-map-danger"
          :disabled="controlsLocked || !selectedFloor"
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
