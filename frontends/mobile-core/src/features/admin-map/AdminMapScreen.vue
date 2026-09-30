<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { AdminMapBuildingResponse, AdminMapFloorResponse } from '../../api/types'
import { AdminMapClient } from '../../api/map-client'
import { adminMapSelectionKey, captureAdminMapUploadTarget } from './admin-map-state'
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
})

watch(selectedBuildingId, () => {
  selectionRevision += 1
  if (!floors.value.some((floor) => floor.id === selectedFloorId.value)) {
    selectedFloorId.value = floors.value[0]?.id ?? null
  }
  clearFileDraft(true)
}, { flush: 'sync' })

watch(selectedFloorId, () => {
  selectionRevision += 1
  clearFileDraft(true)
}, { flush: 'sync' })

async function refresh(): Promise<void> {
  const requestId = ++refreshSequence
  refreshController?.abort()
  const controller = new AbortController()
  refreshController = controller
  loading.value = true
  error.value = null
  try {
    const nextBuildings = await props.client.listBuildings(controller.signal)
    if (!isCurrentRefresh(requestId)) return
    buildings.value = nextBuildings
    if (!selectedBuildingId.value || !buildings.value.some((item) => item.id === selectedBuildingId.value)) {
      selectedBuildingId.value = buildings.value[0]?.id ?? null
    }
    const firstFloor = floors.value[0]
    if (!selectedFloorId.value || !floors.value.some((item) => item.id === selectedFloorId.value)) {
      selectedFloorId.value = firstFloor?.id ?? null
    }
  } catch {
    if (isCurrentRefresh(requestId) && !controller.signal.aborted) {
      error.value = 'Реестр карт не удалось загрузить.'
    }
  } finally {
    if (isCurrentRefresh(requestId)) {
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
  if (saving.value) return
  const requestId = ++saveSequence
  const isCurrent = () => componentMounted && requestId === saveSequence
  saving.value = true
  error.value = null
  notice.value = null
  try {
    await action(isCurrent)
  } catch {
    if (isCurrent()) error.value = failureMessage
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
          :disabled="saving"
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
            :disabled="!buildings.length"
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
          :disabled="saving || !selectedBuildingId"
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
            :disabled="!floors.length"
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
          :disabled="!selectedFloor || saving"
          type="file"
          @change="chooseSvg"
        ></label>
        <label class="admin-map-file"><span>PNG для скачивания</span><input
          ref="pngInput"
          accept="image/png,.png"
          :disabled="!selectedFloor || saving"
          type="file"
          @change="choosePng"
        ></label>
        <button
          type="button"
          :disabled="saving || !selectedFloorId"
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
