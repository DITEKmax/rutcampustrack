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
let assetRequest = 0

const buildings = computed<readonly MapBuilding[]>(() => manifest.value?.buildings ?? [])
const selectedBuilding = computed(() => buildings.value.find((item) => item.id === selectedBuildingId.value) ?? null)
const floors = computed<readonly MapFloor[]>(() => selectedBuilding.value?.floors ?? [])
const selectedFloor = computed(() => floors.value.find((item) => item.id === selectedFloorId.value) ?? null)
const selectedPlan = computed<MapPlan | null>(() => selectedFloor.value?.plan ?? null)
const svgMissing = computed(() => selectedPlan.value?.svg.state !== 'ready')
const pngMissing = computed(() => selectedPlan.value?.png.state !== 'ready')

onMounted(() => void loadManifest())
onBeforeUnmount(() => {
  revokeAsset('svg')
  revokeAsset('png')
})

watch(selectedBuildingId, () => {
  const firstFloor = floors.value[0]
  selectedFloorId.value = firstFloor?.id ?? null
})

watch(selectedFloor, () => void loadAssets())

async function loadManifest(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const response = await props.client.getManifest()
    manifest.value = response.data
    const firstBuilding = response.data?.buildings[0]
    selectedBuildingId.value = firstBuilding?.id ?? null
    selectedFloorId.value = firstBuilding?.floors[0]?.id ?? null
  } catch {
    error.value = 'Карту не удалось загрузить. Попробуй обновить раздел.'
  } finally {
    loading.value = false
  }
}

async function loadAssets(): Promise<void> {
  const plan = selectedPlan.value
  const floor = selectedFloor.value
  const building = selectedBuilding.value
  const requestId = ++assetRequest
  revokeAsset('svg')
  revokeAsset('png')
  if (!plan || !floor || !building) return

  loadingAsset.value = true
  error.value = null
  try {
    const downloads: Promise<void>[] = []
    if (plan.svg.state === 'ready' && plan.svg.id) {
      downloads.push(props.client.downloadAsset(building.id, floor.id, plan.version, 'svg', plan.svg.id)
        .then((blob) => {
          if (requestId === assetRequest) svgUrl.value = URL.createObjectURL(blob)
        }))
    }
    if (plan.png.state === 'ready' && plan.png.id) {
      downloads.push(props.client.downloadAsset(building.id, floor.id, plan.version, 'png', plan.png.id)
        .then((blob) => {
          if (requestId === assetRequest) pngUrl.value = URL.createObjectURL(blob)
        }))
    }
    await Promise.all(downloads)
  } catch {
    if (requestId === assetRequest) error.value = 'Схему не удалось открыть. Попробуй позже.'
  } finally {
    if (requestId === assetRequest) loadingAsset.value = false
  }
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
              :src="svgUrl"
              alt="Схема выбранного этажа"
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
