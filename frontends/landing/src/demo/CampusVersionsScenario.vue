<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import LocalScenarioPanel from './LocalScenarioPanel.vue'

type Floor = '1' | '2'
type Version = '1' | '2'
const floor = ref<Floor>('2')
const latest = ref<Version>('1')
const opened = ref<Version | null>('1')
const revision = ref(12)
const name = ref('Этаж 2')
const status = ref<HTMLElement | null>(null)
const unchanged = computed(() => floor.value === '2' && opened.value === '1' && revision.value === 12)
const pngSha = '11'.repeat(32)
const svg = { id: '33', state: 'ready', sha256: '22'.repeat(32) }
function chooseFloor(value: Floor) {
  floor.value = value
  opened.value = value === '2' ? latest.value : null
}
async function focusStatus() {
  await nextTick()
  status.value?.focus({ preventScroll: true })
}
async function publish() {
  if (floor.value !== '2' || latest.value !== '1') return
  latest.value = '2'
  opened.value = '2'
  revision.value++
  await focusStatus()
}
async function openVersion(version: Version) {
  opened.value = version
  await focusStatus()
}
async function rename() {
  if (floor.value !== '2' || name.value !== 'Этаж 2') return
  name.value = 'Учебный этаж'
  revision.value++
  await focusStatus()
}
function reset() {
  floor.value = '2'
  latest.value = '1'
  opened.value = '1'
  revision.value = 12
  name.value = 'Этаж 2'
}
const state = computed(() => ({
  catalogRevision: String(revision.value),
  buildingId: '1',
  floor: { id: floor.value, label: floor.value === '2' ? name.value : 'Этаж 1' },
  currentVersion: floor.value === '2' ? latest.value : null,
  openedPlan: opened.value ? {
    version: opened.value,
    png: { id: opened.value === '1' ? '31' : '32', state: 'ready', sha256: pngSha },
    svg: opened.value === '2' ? svg : { id: null, state: 'absent', sha256: null },
  } : null,
  savedVersions: floor.value === '1' ? [] : latest.value === '2' ? ['1', '2'] : ['1'],
}))
</script>

<template>
  <LocalScenarioPanel id="demo-campus-versions" title="Версии плана этажа"
    :json="state" :changed="!unchanged" @reset="reset">
    <p>Корпус 1. В PWA открывается актуальная версия. В панели администратора можно выбрать сохранённую.</p>
    <div class="local-scene-controls" role="group" aria-label="Этаж">
      <button type="button" :aria-pressed="floor === '1'" @click="chooseFloor('1')">Этаж 1</button>
      <button type="button" :aria-pressed="floor === '2'" @click="chooseFloor('2')">{{ name }}</button>
    </div>
    <div v-if="opened" class="map-preview" :data-version="opened" role="img"
      :aria-label="opened === '1' ? 'План v1: аудитории 201 и 202' : 'План v2: добавлен проход к аудитории 203'">
      <span>201</span><span class="map-preview__hall" aria-hidden="true"></span><span>202</span>
      <span v-if="opened === '2'" class="map-preview__added">203</span>
    </div>
    <p v-else>У этажа ещё нет опубликованного плана.</p>
    <p ref="status" role="status" tabindex="-1">
      Ревизия {{ revision }} · {{ floor === '2' ? 'текущий v' + latest + ', открыт v' + opened : 'план отсутствует' }}
    </p>
    <div class="local-scene-controls">
      <button type="button" :disabled="floor !== '2' || latest === '2'" @click="publish">Опубликовать v2 · SVG</button>
      <button type="button" :disabled="floor !== '2' || opened === latest" @click="openVersion(latest)">Открыть текущий</button>
      <button type="button" :disabled="floor !== '2' || latest === '1' || opened === '1'" @click="openVersion('1')">Открыть сохранённую v1</button>
      <button type="button" :disabled="floor !== '2' || name !== 'Этаж 2'" @click="rename">Переименовать этаж</button>
    </div>
    <p v-if="floor === '2' && latest === '2'">В v2 добавлен SVG. Копия PNG сохранила контрольную сумму.</p>
  </LocalScenarioPanel>
</template>

<style scoped>
.map-preview {
  width: min(100%, 19rem);
  min-height: 5.7rem;
  display: grid;
  grid-template-columns: 1fr .45fr 1fr;
  gap: .26rem;
  padding: .4rem;
  border: 1px solid var(--story-line);
  border-radius: .4rem;
  background: var(--landing-bg);
}
.map-preview span {
  padding: .25rem;
  display: grid;
  place-items: center;
  border: 1px solid var(--story-line);
  color: var(--story-text);
  background: color-mix(in srgb, var(--landing-accent) 12%, transparent);
}
.map-preview .map-preview__hall { background: transparent; border-color: transparent; }
.map-preview[data-version='2'] .map-preview__added { grid-column: 2 / 4; border-color: var(--landing-accent); }
</style>
