<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { MapBuilding, MapFloor, MapManifest, MapPlan } from '../../api/types'
import { CampusMapClient } from '../../api/map-client'
import './map-screen.pcss'

const props = withDefaults(defineProps<{
  client: CampusMapClient
  theme?: 'dark' | 'light'
}>(), {
  theme: 'dark',
})

const manifest = ref<MapManifest | null>(null)
const loading = ref(true)
const loadingAsset = ref(false)
const error = ref<string | null>(null)
const selectedBuildingId = ref<string | null>(null)
const selectedFloorId = ref<string | null>(null)
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
const selectedPlan = computed<MapPlan | null>(() => selectedFloor.value?.plan ?? null)
const svgMissing = computed(() => selectedPlan.value?.svg.state !== 'ready')
const pngMissing = computed(() => selectedPlan.value?.png.state !== 'ready')

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

watch(selectedFloor, () => void loadAssets())

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

async function loadAssets(): Promise<void> {
  const plan = selectedPlan.value
  const floor = selectedFloor.value
  const building = selectedBuilding.value
  const requestId = ++assetRequest
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  revokeAsset('svg')
  revokeAsset('png')
  if (!plan || !floor || !building) {
    loadingAsset.value = false
    invalidateOpenIntent()
    return
  }

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

  loadingAsset.value = true
  error.value = null
  try {
    const downloads: Promise<void>[] = []
    if (plan.svg.state === 'ready' && plan.svg.id) {
      downloads.push(props.client.downloadAsset(building.id, floor.id, plan.version, 'svg', plan.svg.id)
        .then((blob) => {
          if (requestId === assetRequest && !disposed) {
            svgUrl.value = URL.createObjectURL(blob)
            svgAssetRequest.value = requestId
          }
        }))
    }
    if (plan.png.state === 'ready' && plan.png.id) {
      downloads.push(props.client.downloadAsset(building.id, floor.id, plan.version, 'png', plan.png.id)
        .then((blob) => {
          if (requestId === assetRequest && !disposed) pngUrl.value = URL.createObjectURL(blob)
        }))
    }
    await Promise.all(downloads)
  } catch (cause) {
    if (requestId === assetRequest && !disposed && !isAbortError(cause)) {
      error.value = 'Схему не удалось открыть. Попробуй позже.'
    }
  } finally {
    if (requestId === assetRequest && !disposed) loadingAsset.value = false
  }
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
      <p class="map-screen__eyebrow">
        Карта кампуса
      </p>
      <h1 id="map-screen-title">
        Схема этажа
      </h1>
    </header>

    <p
      v-if="loading"
      class="map-state"
      role="status"
    >
      Загружаем список корпусов…
    </p>
    <div
      v-else
      class="map-screen__content"
    >
      <p
        v-if="error"
        class="map-state map-state--error"
        role="alert"
      >
        {{ error }}
      </p>
      <p
        v-else-if="!buildings.length"
        class="map-state"
        role="status"
      >
        Схемы пока нет.
      </p>

      <template v-else>
        <label class="map-field">
          <span>Корпус</span>
          <select v-model="selectedBuildingId">
            <option
              v-for="building in buildings"
              :key="building.id"
              :value="building.id"
            >
              {{ building.label }}
            </option>
          </select>
        </label>
        <label class="map-field">
          <span>Этаж</span>
          <select
            v-model="selectedFloorId"
            :disabled="!floors.length"
          >
            <option
              v-for="floor in floors"
              :key="floor.id"
              :value="floor.id"
            >
              {{ floor.label }}
            </option>
          </select>
        </label>

        <section
          v-if="selectedFloor && selectedPlan"
          class="map-card"
          aria-live="polite"
        >
          <div class="map-card__heading">
            <div>
              <h2>{{ selectedPlan.label }}</h2>
              <p>Версия {{ selectedPlan.version }}</p>
            </div>
            <span
              v-if="loadingAsset"
              class="map-card__status"
            >Обновляем…</span>
          </div>

          <div
            v-if="svgUrl"
            class="map-preview"
          >
            <img
              :key="svgUrl"
              :src="svgUrl"
              alt="Схема выбранного этажа"
              @load="handleSvgLoaded"
            >
          </div>
          <p
            v-else
            class="map-state map-state--compact"
          >
            {{ formatMessage(selectedPlan.svg.state, 'SVG') }}. Просмотр недоступен.
          </p>

          <a
            v-if="pngUrl"
            class="map-download"
            :href="pngUrl"
            download="campus-map.png"
          >Скачать PNG</a>
          <p
            v-else
            class="map-state map-state--compact"
          >
            {{ formatMessage(selectedPlan.png.state, 'PNG') }}. Скачать файл нельзя.
          </p>

          <p
            v-if="svgMissing || pngMissing"
            class="map-card__hint"
          >
            Доступные форматы показаны отдельно: публикация одного файла не скрывает другой.
          </p>
          <p
            v-if="usageError"
            class="map-state map-state--compact"
            role="status"
          >
            {{ usageError }}
          </p>
        </section>
        <p
          v-else
          class="map-state"
          role="status"
        >
          Для выбранного этажа схема ещё не опубликована.
        </p>
      </template>
    </div>
  </main>
</template>
