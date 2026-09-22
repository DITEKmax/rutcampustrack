<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { AdminMapBuildingResponse, AdminMapFloorResponse } from '../../api/types'
import { AdminMapClient } from '../../api/map-client'
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
const buildingCode = ref('')
const buildingLabel = ref('')
const floorCode = ref('')
const floorLabel = ref('')
const png = ref<File | undefined>()
const svg = ref<File | undefined>()
const loading = ref(true)
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

onMounted(() => void refresh())

async function refresh(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    buildings.value = await props.client.listBuildings()
    if (!selectedBuildingId.value || !buildings.value.some((item) => item.id === selectedBuildingId.value)) {
      selectedBuildingId.value = buildings.value[0]?.id ?? null
    }
    const firstFloor = floors.value[0]
    if (!selectedFloorId.value || !floors.value.some((item) => item.id === selectedFloorId.value)) {
      selectedFloorId.value = firstFloor?.id ?? null
    }
  } catch {
    error.value = 'Реестр карт не удалось загрузить.'
  } finally {
    loading.value = false
  }
}

async function addBuilding(): Promise<void> {
  if (!/^[1-9][0-9]*$/.test(buildingCode.value.trim())) {
    error.value = 'Номер корпуса должен быть положительным числом.'
    return
  }
  await runSave(async () => {
    await props.client.createBuilding(buildingCode.value.trim(), buildingLabel.value.trim() || undefined)
    buildingCode.value = ''
    buildingLabel.value = ''
    notice.value = 'Корпус добавлен.'
    await refresh()
  })
}

async function addFloor(): Promise<void> {
  if (!selectedBuildingId.value || !/^[1-9][0-9]*$/.test(floorCode.value.trim())) {
    error.value = 'Выбери корпус и укажи положительный номер этажа.'
    return
  }
  await runSave(async () => {
    await props.client.createFloor(selectedBuildingId.value!, floorCode.value.trim(), floorLabel.value.trim() || undefined)
    floorCode.value = ''
    floorLabel.value = ''
    notice.value = 'Этаж добавлен.'
    await refresh()
  })
}

function choosePng(event: Event): void {
  png.value = (event.target as HTMLInputElement).files?.[0]
}

function chooseSvg(event: Event): void {
  svg.value = (event.target as HTMLInputElement).files?.[0]
}

async function upload(): Promise<void> {
  if (!selectedFloorId.value || (!png.value && !svg.value)) {
    error.value = 'Выбери этаж и приложи PNG или SVG.'
    return
  }
  await runSave(async () => {
    await props.client.uploadVersion(selectedFloorId.value!, {
      ...(png.value ? { png: png.value } : {}),
      ...(svg.value ? { svg: svg.value } : {}),
    })
    png.value = undefined
    svg.value = undefined
    notice.value = 'Новая версия опубликована. Отсутствующий формат сохранён как отдельное состояние.'
    await refresh()
  })
}

async function runSave(action: () => Promise<void>): Promise<void> {
  saving.value = true
  error.value = null
  notice.value = null
  try {
    await action()
  } catch {
    error.value = 'Изменение не сохранено. Проверь данные и формат файлов.'
  } finally {
    saving.value = false
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
      v-if="loading"
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
        <label class="admin-map-field"><span>Этаж</span>
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
          accept="image/svg+xml,.svg"
          type="file"
          @change="chooseSvg"
        ></label>
        <label class="admin-map-file"><span>PNG для скачивания</span><input
          accept="image/png,.png"
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
        <ul class="admin-map-demand" aria-label="Количество открытий схем по этажам">
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
