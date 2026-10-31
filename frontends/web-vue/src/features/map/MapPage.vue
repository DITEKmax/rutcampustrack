<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, useId, watch } from 'vue';
import type { MapFormat } from '../../../../mobile-core/src/api/types';
import { useWebMap } from './use-map';
import './map.pcss';

const map = useWebMap();
const id = useId();
const viewport = ref<HTMLElement | null>(null);
const zoom = ref(1);
const imageReady = ref(false);
const imageError = ref(false);
const label = computed(() => `${map.building.value?.label ?? ''}, ${map.floor.value?.label ?? ''}`);
const formats = ['png', 'svg'] as const;
const ready = computed(() => imageReady.value && !imageError.value);
let revision = 0;
let drag: { pointer: number; x: number; y: number; left: number; top: number } | null = null;
function stopDrag() {
  if (drag && viewport.value?.hasPointerCapture(drag.pointer))
    viewport.value.releasePointerCapture(drag.pointer);
  drag = null;
}
function reset() {
  revision++;
  stopDrag();
  zoom.value = 1;
  viewport.value?.scrollTo(0, 0);
}
watch(
  () => map.preview.value?.url,
  () => {
    imageReady.value = false;
    imageError.value = false;
    reset();
  },
  { flush: 'sync' },
);
onBeforeUnmount(stopDrag);
function zoomBy(delta: number) {
  const area = viewport.value;
  if (!area || !ready.value) return;
  const scale = Math.max(1, Math.min(4, zoom.value + delta));
  if (scale === zoom.value) return;
  stopDrag();
  const centerX = (area.scrollLeft + area.clientWidth / 2) / zoom.value;
  const centerY = (area.scrollTop + area.clientHeight / 2) / zoom.value;
  const currentRevision = revision;
  zoom.value = scale;
  void nextTick(() => {
    if (currentRevision !== revision || viewport.value !== area || zoom.value !== scale) return;
    area.scrollTo(centerX * scale - area.clientWidth / 2, centerY * scale - area.clientHeight / 2);
  });
}
function onKey(event: KeyboardEvent) {
  const area = viewport.value;
  if (!area || !ready.value || event.ctrlKey || event.metaKey || event.altKey) return;
  if (event.key === '+' || event.key === '=') zoomBy(0.25);
  else if (event.key === '-' || event.key === '−') zoomBy(-0.25);
  else if (event.key === '0') reset();
  else if (
    zoom.value > 1 &&
    ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)
  ) {
    const dx = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0;
    const dy = event.key === 'ArrowUp' ? -1 : event.key === 'ArrowDown' ? 1 : 0;
    area.scrollBy((dx * area.clientWidth) / 8, (dy * area.clientHeight) / 8);
  } else return;
  event.preventDefault();
}
function onWheel(event: WheelEvent) {
  if (!ready.value || (!event.ctrlKey && !event.metaKey) || !event.deltaY) return;
  event.preventDefault();
  zoomBy(event.deltaY < 0 ? 0.25 : -0.25);
}
function startDrag(event: PointerEvent) {
  const area = viewport.value;
  if (
    !area ||
    !ready.value ||
    zoom.value === 1 ||
    event.pointerType !== 'mouse' ||
    event.button !== 0
  )
    return;
  drag = {
    pointer: event.pointerId,
    x: event.clientX,
    y: event.clientY,
    left: area.scrollLeft,
    top: area.scrollTop,
  };
  area.setPointerCapture(event.pointerId);
  event.preventDefault();
}
function moveDrag(event: PointerEvent) {
  if (!drag || drag.pointer !== event.pointerId) return;
  viewport.value?.scrollTo(drag.left + drag.x - event.clientX, drag.top + drag.y - event.clientY);
  event.preventDefault();
}
function loaded(event: Event) {
  const image = event.currentTarget as HTMLImageElement;
  if (image.src !== map.preview.value?.url || !image.naturalWidth || !image.naturalHeight) return;
  imageReady.value = true;
  void map.recordOpen(image.src);
}
function filename(format: MapFormat) {
  return `campus-map-${label.value.replace(/[^\p{L}\p{N}_-]+/gu, '-').slice(0, 100)}.${format}`;
}
function guardDownload(event: Event, format: MapFormat) {
  try {
    if (map.guardAsset(format, (event.currentTarget as HTMLAnchorElement).href)) return;
  } catch {
    /* The admitted page no longer owns the file. */
  }
  event.preventDefault();
}
function formatStatus(format: MapFormat) {
  const state = map.plan.value?.[format].state;
  const name = format.toUpperCase();
  if (state === 'processing') return `${name} ещё обрабатывается.`;
  if (state === 'failed') return `${name} не прошёл проверку.`;
  if (state === 'absent') return `${name} пока не загружен.`;
  return `${name} недоступен.`;
}
</script>
<template>
  <section
    class="web-map web-stack"
    data-screen-id="WEB-SHARED-MAP"
  >
    <h1 class="web-sr-only">
      Карта кампуса
    </h1>
    <p
      v-if="map.loading.value"
      role="status"
    >
      Загружаем список корпусов…
    </p>
    <p
      v-if="map.error.value"
      class="web-error"
      role="alert"
    >
      {{ map.error.value }}
      <button
        class="web-button"
        @click="map.load"
      >
        Повторить
      </button>
    </p>
    <template v-if="map.buildings.value.length">
      <div class="web-map-pickers">
        <div class="web-field">
          <label :for="`${id}-building`">Корпус</label>
          <select
            :id="`${id}-building`"
            :value="map.buildingId.value"
            @change="map.selectBuilding(($event.target as HTMLSelectElement).value)"
          >
            <option
              v-for="building in map.buildings.value"
              :key="building.id"
              :value="building.id"
            >
              {{ building.label }}
            </option>
          </select>
        </div>
        <div class="web-field">
          <label :for="`${id}-floor`">Этаж</label>
          <select
            :id="`${id}-floor`"
            :value="map.floorId.value"
            :disabled="!map.floors.value.length"
            @change="map.selectFloor(($event.target as HTMLSelectElement).value)"
          >
            <option
              v-for="floor in map.floors.value"
              :key="floor.id"
              :value="floor.id"
            >
              {{ floor.label }}
            </option>
          </select>
        </div>
      </div>
      <p
        class="web-sr-only"
        role="status"
      >
        {{ label }}. Доступно этажей: {{ map.floors.value.length }}.
      </p>
      <div
        class="web-map-viewer"
        :aria-busy="map.floorLoading.value"
      >
        <p
          v-if="map.floorLoading.value && !map.preview.value"
          class="web-map-state"
          role="status"
        >
          Загружаем схему этажа…
        </p>
        <template v-if="map.preview.value">
          <div
            ref="viewport"
            class="web-map-viewport"
            :class="{ 'web-map-zoomed': zoom > 1 }"
            role="region"
            tabindex="0"
            :aria-label="`Схема: ${label}`"
            :aria-describedby="`${id}-help`"
            @keydown="onKey"
            @wheel="onWheel"
            @pointerdown="startDrag"
            @pointermove="moveDrag"
            @pointerup="stopDrag"
            @pointercancel="stopDrag"
            @lostpointercapture="stopDrag"
          >
            <div
              class="web-map-canvas"
              :style="{ '--web-map-zoom': zoom }"
            >
              <img
                :key="map.preview.value.url"
                :src="map.preview.value.url"
                :alt="map.plan.value?.label || `Схема: ${label}`"
                :draggable="false"
                @load="loaded"
                @error="imageError = true"
              >
            </div>
          </div>
          <div
            class="web-map-controls"
            role="group"
            aria-label="Масштаб схемы"
          >
            <button
              class="web-button"
              aria-label="Увеличить масштаб схемы"
              :disabled="!ready || zoom === 4"
              @click="zoomBy(0.25)"
            >
              +
            </button>
            <button
              class="web-button"
              aria-label="Уменьшить масштаб схемы"
              :disabled="!ready || zoom === 1"
              @click="zoomBy(-0.25)"
            >
              −
            </button>
            <button
              class="web-button"
              aria-label="Сбросить масштаб схемы"
              :disabled="!ready || zoom === 1"
              @click="reset"
            >
              ↺
            </button>
          </div>
          <p
            class="web-map-scale"
            role="status"
          >
            {{ Math.round(zoom * 100) }}%
          </p>
        </template>
        <p
          v-else-if="!map.floorLoading.value && !map.error.value"
          class="web-map-state"
          role="status"
        >
          {{
            map.floors.value.length
              ? 'Для этого этажа схема пока не опубликована.'
              : 'В этом корпусе нет доступных этажей.'
          }}
        </p>
      </div>
      <p
        :id="`${id}-help`"
        class="web-sr-only"
      >
        Плюс и минус меняют масштаб, ноль сбрасывает его. После увеличения перемещай схему
        стрелками или прокруткой.
      </p>
      <p
        v-if="imageError"
        class="web-error"
        role="alert"
      >
        Браузер не смог открыть изображение.
        <button
          class="web-button"
          @click="map.loadFloor()"
        >
          Повторить
        </button>
      </p>
      <template v-if="map.plan.value">
        <div class="web-map-downloads">
          <template
            v-for="format in formats"
            :key="format"
          >
            <a
              v-if="map.assets.value[format]"
              :href="map.assets.value[format]!.url"
              :download="filename(format)"
              @click="guardDownload($event, format)"
            >Скачать схему: {{ label }}, {{ format.toUpperCase() }}</a>
            <p
              v-else-if="!map.floorLoading.value"
              :class="map.assetErrors.value[format] ? 'web-error' : 'web-muted'"
            >
              {{ map.assetErrors.value[format] || formatStatus(format) }}
            </p>
          </template>
        </div>
        <button
          v-if="Object.keys(map.assetErrors.value).length"
          class="web-button"
          @click="map.loadFloor()"
        >
          Загрузить файлы повторно
        </button>
      </template>
      <p
        v-if="map.usageError.value"
        class="web-notice"
        role="status"
      >
        {{ map.usageError.value }}
      </p>
    </template>
    <p
      v-else-if="!map.loading.value && !map.error.value"
      role="status"
    >
      Схемы кампуса пока нет. Корпуса появятся после публикации.
    </p>
  </section>
</template>
