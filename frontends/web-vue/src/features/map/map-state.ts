import { computed, ref, shallowRef } from 'vue'
import { CampusMapClient, MapApiError } from '../../../../mobile-core/src/api/map-client'
import type { MapFormat, MapManifest, MapPlan } from '../../../../mobile-core/src/api/types'

type Asset = { url: string; blob: Blob }

/** One admitted page owns its floor reads and every temporary asset URL. */
export function createMapState(
  client: CampusMapClient,
  assertCurrent: () => void,
  urls: Pick<typeof URL, 'createObjectURL' | 'revokeObjectURL'> = URL,
) {
  const manifest = shallowRef<MapManifest | null>(null)
  const buildingId = ref('')
  const floorId = ref('')
  const plan = shallowRef<MapPlan | null>(null)
  const assets = shallowRef<Partial<Record<MapFormat, Asset>>>({})
  const assetErrors = ref<Partial<Record<MapFormat, string>>>({})
  const loading = ref(false)
  const floorLoading = ref(false)
  const error = ref('')
  const usageError = ref('')
  const buildings = computed(() => manifest.value?.buildings ?? [])
  const building = computed(() => buildings.value.find((item) => item.id === buildingId.value))
  const floors = computed(() => building.value?.floors ?? [])
  const floor = computed(() => floors.value.find((item) => item.id === floorId.value))
  const preview = computed(() => assets.value.svg ?? assets.value.png)
  let disposed = false
  let manifestRead = 0
  let floorRead = 0
  let manifestController: AbortController | undefined
  let floorController: AbortController | undefined
  let openIntent: string | null = null
  let openStarted = false

  function current(sequence: number) {
    if (disposed || sequence !== floorRead) return false
    try {
      assertCurrent()
      return true
    } catch {
      return false
    }
  }
  function clearAssets() {
    for (const asset of Object.values(assets.value)) urls.revokeObjectURL(asset.url)
    assets.value = {}
  }
  function message(cause: unknown) {
    return cause instanceof MapApiError && cause.response.status === 403
      ? 'Нет доступа к схеме в текущей роли.'
      : 'Не удалось загрузить карту. Проверь подключение и повтори.'
  }
  async function loadFloor(preserveCurrent = false) {
    const sequence = ++floorRead
    floorController?.abort()
    floorController = new AbortController()
    const previousPlan = plan.value
    const preserve =
      preserveCurrent &&
      previousPlan?.buildingId === buildingId.value &&
      previousPlan.floorId === floorId.value
    if (!preserve) {
      clearAssets()
      plan.value = null
    }
    error.value = ''
    if (!preserve) {
      usageError.value = ''
      assetErrors.value = {}
      openIntent = null
      openStarted = false
    }
    const selectedBuilding = building.value
    const selectedFloor = floor.value
    if (!selectedBuilding || !selectedFloor) {
      floorLoading.value = false
      return
    }
    floorLoading.value = true
    const signal = floorController.signal
    try {
      const value = await client.getFloorPlan(selectedBuilding.id, selectedFloor.id, signal)
      if (!current(sequence)) return
      const samePlan = preserve && JSON.stringify(value) === JSON.stringify(previousPlan)
      if (!samePlan) {
        clearAssets()
        assetErrors.value = {}
        usageError.value = ''
        openStarted = false
      }
      plan.value = value
      if (!value) return
      if (!samePlan) openIntent = globalThis.crypto?.randomUUID() ?? null
      await Promise.all(
        (['svg', 'png'] as const).map(async (format) => {
          const slot = value[format]
          if (slot.state !== 'ready' || !slot.id || assets.value[format]) return
          const errors = { ...assetErrors.value }
          delete errors[format]
          assetErrors.value = errors
          try {
            const blob = await client.downloadAsset(
              selectedBuilding.id,
              selectedFloor.id,
              value.version,
              format,
              slot.id,
              signal,
            )
            if (!current(sequence)) return
            assets.value = { ...assets.value, [format]: { blob, url: urls.createObjectURL(blob) } }
          } catch (cause) {
            if (current(sequence) && !signal.aborted)
              assetErrors.value = { ...assetErrors.value, [format]: message(cause) }
          }
        }),
      )
    } catch (cause) {
      if (current(sequence) && !signal.aborted) error.value = message(cause)
    } finally {
      if (current(sequence)) floorLoading.value = false
    }
  }
  async function load() {
    const sequence = ++manifestRead
    manifestController?.abort()
    manifestController = new AbortController()
    loading.value = !manifest.value
    error.value = ''
    try {
      const response = await client.getManifest(undefined, manifestController.signal)
      if (disposed || sequence !== manifestRead) return
      assertCurrent()
      manifest.value = response.data
      buildingId.value =
        buildings.value.find((item) => item.id === buildingId.value)?.id ??
        buildings.value[0]?.id ??
        ''
      floorId.value =
        floors.value.find((item) => item.id === floorId.value)?.id ?? floors.value[0]?.id ?? ''
      await loadFloor(true)
    } catch (cause) {
      if (!disposed && sequence === manifestRead && !manifestController.signal.aborted) {
        try {
          assertCurrent()
          error.value = message(cause)
        } catch {
          /* Obsolete authority has no UI continuation. */
        }
      }
    } finally {
      if (!disposed && sequence === manifestRead) loading.value = false
    }
  }
  async function selectBuilding(id: string) {
    buildingId.value = id
    floorId.value = floors.value[0]?.id ?? ''
    await loadFloor()
  }
  async function selectFloor(id: string) {
    floorId.value = id
    await loadFloor()
  }
  function guardAsset(format: MapFormat, url?: string) {
    assertCurrent()
    return (
      !disposed &&
      assets.value[format] !== undefined &&
      (!url || assets.value[format]?.url === url) &&
      plan.value?.[format].state === 'ready'
    )
  }
  /** Count a visible decoded preview once; an uncertain write is never replayed. */
  async function recordOpen(url: string) {
    if (!preview.value || preview.value.url !== url || !openIntent || openStarted) return
    const sequence = floorRead
    const selectedBuilding = building.value
    const selectedFloor = floor.value
    if (!selectedBuilding || !selectedFloor || !current(sequence)) return
    openStarted = true
    try {
      await client.recordFloorOpen(
        selectedBuilding.id,
        selectedFloor.id,
        openIntent,
        floorController?.signal,
      )
      if (current(sequence)) usageError.value = ''
    } catch {
      if (current(sequence))
        usageError.value = 'Схема доступна, но сохранение просмотра не подтверждено.'
    }
  }
  function dispose() {
    disposed = true
    manifestRead++
    floorRead++
    manifestController?.abort()
    floorController?.abort()
    clearAssets()
  }
  return {
    manifest,
    buildings,
    building,
    floors,
    floor,
    buildingId,
    floorId,
    plan,
    assets,
    preview,
    loading,
    floorLoading,
    error,
    usageError,
    assetErrors,
    load,
    loadFloor,
    selectBuilding,
    selectFloor,
    guardAsset,
    recordOpen,
    dispose,
  }
}
