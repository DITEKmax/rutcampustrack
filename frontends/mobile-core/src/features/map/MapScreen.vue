<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { MapBuilding, MapFloor, MapManifest, MapPlan } from '../../api/types'
import { CampusMapClient } from '../../api/map-client'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import MapSelect from './MapSelect.vue'
import './map-screen.pcss'

const props = withDefaults(defineProps<{
  client: CampusMapClient
  theme?: 'dark' | 'light'
  onBack?: (() => void | Promise<void>) | undefined
  offline?: boolean
}>(), {
  theme: 'dark',
  onBack: undefined,
  offline: false,
})

const manifest = ref<MapManifest | null>(null)
const loading = ref(true)
const loadingAsset = ref(false)
const error = ref<string | null>(null)
const selectedBuildingId = ref<string | null>(null)
const selectedFloorId = ref<string | null>(null)
const currentPlan = ref<MapPlan | null>(null)
const currentPlanFloorKey = ref<string | null>(null)
const svgUrl = ref<string | null>(null)
const pngUrl = ref<string | null>(null)
const usageError = ref<string | null>(null)
const svgAssetRequest = ref(0)
let assetRequest = 0
let lifecycleGeneration = 0
let requestController: AbortController | null = null
let disposed = false
let openIntentKey: string | null = null
let openIntentId: string | null = null
let openIntentGeneration: number | undefined
let openRecorded = false
let openRecording = false

const buildings = computed<readonly MapBuilding[]>(() => manifest.value?.buildings ?? [])
const selectedBuilding = computed(() => buildings.value.find((item) => item.id === selectedBuildingId.value) ?? null)
const floors = computed<readonly MapFloor[]>(() => selectedBuilding.value?.floors ?? [])
const selectedFloor = computed(() => floors.value.find((item) => item.id === selectedFloorId.value) ?? null)
const selectedPlan = computed<MapPlan | null>(() => {
  const building = selectedBuilding.value
  const floor = selectedFloor.value
  if (!building || !floor || currentPlanFloorKey.value !== `${building.id}:${floor.id}`) return null
  return currentPlan.value
})
const previewUrl = computed(() => svgUrl.value ?? pngUrl.value)
const unavailableFormats = computed(() => {
  const plan = selectedPlan.value
  return plan ? (['svg', 'png'] as const).filter((format) => plan[format].state !== 'ready') : []
})

onMounted(() => void loadManifest())
onBeforeUnmount(() => {
  disposed = true
  lifecycleGeneration += 1
  assetRequest += 1
  requestController?.abort()
  requestController = null
  invalidateOpenIntent()
  revokeAsset('svg')
  revokeAsset('png')
})

watch(selectedBuildingId, () => {
  const firstFloor = floors.value[0]
  selectedFloorId.value = firstFloor?.id ?? null
})

watch([selectedBuildingId, selectedFloorId], () => void loadCurrentFloorPlan())

async function loadManifest(): Promise<void> {
  const requestGeneration = lifecycleGeneration
  loading.value = true
  error.value = null
  try {
    const response = await props.client.getManifest()
    if (disposed || requestGeneration !== lifecycleGeneration) return
    manifest.value = response.data
    const firstBuilding = response.data?.buildings[0]
    selectedBuildingId.value = firstBuilding?.id ?? null
    selectedFloorId.value = firstBuilding?.floors[0]?.id ?? null
  } catch {
    if (!disposed && requestGeneration === lifecycleGeneration) {
      error.value = 'Карту не удалось загрузить. Попробуй обновить раздел.'
    }
  } finally {
    if (!disposed && requestGeneration === lifecycleGeneration) loading.value = false
  }
}

async function loadCurrentFloorPlan(): Promise<void> {
  const floor = selectedFloor.value
  const building = selectedBuilding.value
  const requestId = ++assetRequest
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  const floorKey = floor && building ? `${building.id}:${floor.id}` : null
  currentPlan.value = null
  currentPlanFloorKey.value = floorKey
  revokeAsset('svg')
  revokeAsset('png')
  invalidateOpenIntent()
  error.value = null
  if (!floor || !building) {
    loadingAsset.value = false
    return
  }

  loadingAsset.value = true
  let planResolved = false
  try {
    const plan = await props.client.getFloorPlan(building.id, floor.id, controller.signal)
    if (!isCurrentFloorRequest(requestId, floorKey)) return
    currentPlan.value = plan
    planResolved = true
    if (!plan) return

    const currentOpenKey = `${building.id}:${floor.id}:${plan.version}`
    const currentGeneration = props.client.currentGeneration()
    if (openIntentKey !== currentOpenKey
      || openIntentGeneration !== currentGeneration
      || !openIntentId) {
      openIntentKey = currentOpenKey
      openIntentGeneration = currentGeneration
      openIntentId = globalThis.crypto?.randomUUID?.() ?? null
      openRecorded = false
      openRecording = false
    }
    if (!openRecorded) openRecording = false
    usageError.value = openIntentId
      ? null
      : 'Просмотр доступен, но статистику открытия сохранить не удалось.'

    const downloads: Promise<void>[] = []
    if (plan.svg.state === 'ready' && plan.svg.id) {
      downloads.push(props.client.downloadAsset(building.id, floor.id, plan.version, 'svg', plan.svg.id, controller.signal)
        .then((blob) => {
          if (isCurrentFloorRequest(requestId, floorKey)) {
            svgUrl.value = URL.createObjectURL(blob)
            svgAssetRequest.value = requestId
          }
        }))
    }
    if (plan.png.state === 'ready' && plan.png.id) {
      downloads.push(props.client.downloadAsset(building.id, floor.id, plan.version, 'png', plan.png.id, controller.signal)
        .then((blob) => {
          if (isCurrentFloorRequest(requestId, floorKey)) {
            pngUrl.value = URL.createObjectURL(blob)
            svgAssetRequest.value = requestId
          }
        }))
    }
    await Promise.all(downloads)
  } catch (cause) {
    if (isCurrentFloorRequest(requestId, floorKey) && !isAbortError(cause)) {
      error.value = planResolved
        ? 'Схему не удалось открыть. Попробуй позже.'
        : 'Актуальную схему выбранного этажа не удалось загрузить. Попробуй обновить раздел.'
    }
  } finally {
    if (isCurrentFloorRequest(requestId, floorKey)) loadingAsset.value = false
  }
}

async function retryMap(): Promise<void> {
  if (selectedBuilding.value && selectedFloor.value) await loadCurrentFloorPlan()
  else await loadManifest()
}

function isCurrentFloorRequest(requestId: number, floorKey: string | null): boolean {
  const building = selectedBuilding.value
  const floor = selectedFloor.value
  return !disposed
    && requestId === assetRequest
    && floorKey !== null
    && building !== null
    && floor !== null
    && floorKey === `${building.id}:${floor.id}`
}

function handleSvgLoaded(): void {
  const requestId = svgAssetRequest.value
  const intentId = openIntentId
  const intentKey = openIntentKey
  const generation = openIntentGeneration
  const building = selectedBuilding.value
  const floor = selectedFloor.value
  if (disposed || requestId === 0 || requestId !== assetRequest
      || !intentId || !intentKey || !building || !floor
      || openRecorded || openRecording
      || !props.client.isCurrentGeneration(generation)) return

  openRecording = true
  void props.client.recordFloorOpen(building.id, floor.id, intentId, requestController?.signal)
    .then(() => {
      if (requestId === assetRequest
          && intentKey === openIntentKey
          && intentId === openIntentId
          && props.client.isCurrentGeneration(generation)) {
        openRecorded = true
        usageError.value = null
      }
    })
    .catch((cause: unknown) => {
      if (requestId === assetRequest
          && !disposed
          && !isAbortError(cause)
          && props.client.isCurrentGeneration(generation)) {
        usageError.value = 'Просмотр доступен, но статистику открытия сохранить не удалось.'
      }
    })
    .finally(() => {
      if (requestId === assetRequest && intentKey === openIntentKey && intentId === openIntentId) {
        openRecording = false
      }
    })
}

function invalidateOpenIntent(): void {
  openIntentKey = null
  openIntentId = null
  openIntentGeneration = undefined
  openRecorded = false
  openRecording = false
  usageError.value = null
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === 'AbortError'
}

function revokeAsset(kind: 'svg' | 'png'): void {
  const value = kind === 'svg' ? svgUrl.value : pngUrl.value
  if (value) URL.revokeObjectURL(value)
  if (kind === 'svg') svgUrl.value = null
  else pngUrl.value = null
}

function formatMessage(state: string | undefined, format: 'SVG' | 'PNG'): string {
  if (state === 'absent') return `${format} пока не загружен`
  if (state === 'processing') return `${format} ещё обрабатывается`
  if (state === 'failed') return `${format} не прошёл проверку`
  return `${format} недоступен`
}
</script>

<template>
  <main
    class="map-screen"
    :data-theme="theme"
    aria-labelledby="map-screen-title"
  >
    <header class="map-screen__header">
      <button
        v-if="onBack"
        type="button"
        class="map-screen__back"
        aria-label="Назад"
        @click="onBack"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="map-screen-title">
        Карта кампуса
      </h1>
    </header>
    <div class="map-screen__content">
      <StudentWarningBlock
        v-if="offline"
        title="Нет подключения"
        message="Карта кампуса доступна онлайн. Подключись к интернету и обнови раздел."
      />
      <p
        v-if="loading"
        class="map-state"
        role="status"
      >
        Загружаем список корпусов…
      </p>
      <template v-else>
        <StudentWarningBlock
          v-if="error"
          severity="error"
          title="Карта не загружена"
          :message="error"
          action-label="Повторить"
          :action-disabled="offline"
          @action="retryMap"
        />
        <template v-if="buildings.length">
          <label
            class="map-field"
            for="map-building"
          ><span>Корпус</span></label>
          <MapSelect
            id="map-building"
            v-model="selectedBuildingId"
            :options="buildings"
          />
          <label
            class="map-field"
            for="map-floor"
          ><span>Этаж</span></label>
          <MapSelect
            id="map-floor"
            v-model="selectedFloorId"
            :options="floors"
            :disabled="!floors.length"
          />
          <p
            v-if="loadingAsset && selectedFloor && !selectedPlan"
            class="map-state"
            role="status"
          >
            Загружаем актуальную схему этажа…
          </p>
          <section
            v-else-if="selectedFloor && selectedPlan"
            class="map-card"
            aria-live="polite"
          >
            <div class="map-card__heading">
              <div><h2>{{ selectedPlan.label }}</h2><p>Версия {{ selectedPlan.version }}</p></div>
              <span
                v-if="loadingAsset"
                class="map-card__status"
              >Загружаем…</span>
            </div>
            <div
              v-if="previewUrl"
              class="map-preview"
            >
              <img
                :key="previewUrl"
                :src="previewUrl"
                alt="Схема выбранного этажа"
                @load="handleSvgLoaded"
              >
            </div>
            <div
              v-else-if="!loadingAsset && !error"
              class="map-empty"
              role="status"
            >
              <p>Для этого этажа схема не загружена</p><p>Выбери другой этаж</p>
            </div>
            <div
              v-if="svgUrl || pngUrl"
              class="map-actions"
            >
              <a
                v-if="previewUrl"
                class="map-open"
                :href="previewUrl"
                target="_blank"
                rel="noopener"
              >Открыть схему</a>
              <a
                v-if="pngUrl"
                class="map-download"
                :href="pngUrl"
                download="campus-map.png"
              >Скачать PNG</a>
              <a
                v-if="svgUrl"
                class="map-open"
                :href="svgUrl"
                download="campus-map.svg"
              >Скачать SVG</a>
            </div>
            <StudentWarningBlock
              v-for="format in unavailableFormats"
              :key="format"
              :severity="selectedPlan[format].state === 'failed' ? 'error' : 'warning'"
              :title="formatMessage(selectedPlan[format].state, format === 'svg' ? 'SVG' : 'PNG')"
              message="Выбери другой этаж или воспользуйся доступным форматом схемы."
            />
            <StudentWarningBlock
              v-if="usageError"
              title="Открытие не сохранено"
              :message="usageError"
            />
          </section>
          <div
            v-else-if="!error && !loadingAsset"
            class="map-empty"
            role="status"
          >
            <p>{{ floors.length ? 'Для этого этажа схема не загружена' : 'В этом корпусе нет доступных этажей' }}</p><p>{{ floors.length ? 'Выбери другой этаж' : 'Выбери другой корпус' }}</p>
          </div>
        </template>
        <div
          v-else-if="!error"
          class="map-empty"
          role="status"
        >
          <p>Схемы кампуса пока нет</p><p>Корпуса появятся после публикации схем</p>
        </div>
      </template>
    </div>
  </main>
</template>
