<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import type { AdminMapBuildingResponse } from '../../api/types';
import { AdminMapClient } from '../../api/map-client';
import type { AdminAccessProfile } from '../admin-access/admin-access-client';
import AdminNotice from '../../shared/components/AdminNotice.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import './admin-map-screen.pcss';

const props = withDefaults(
  defineProps<{
    client: AdminMapClient;
    authority: AdminAccessProfile | null;
    theme?: 'dark' | 'light';
    showBack?: boolean;
  }>(),
  { theme: 'dark', showBack: true },
);
const emit = defineEmits<{ back: []; 'owner-error': [cause: unknown] }>();
const inventory = ref<readonly AdminMapBuildingResponse[]>([]);
const buildingId = ref('all');
const loading = ref(true);
const error = ref(false);
const canView = computed(
  () =>
    props.authority?.assigned === true &&
    (props.authority.mode === 'FULL' || props.authority.mode === 'LIMITED') &&
    props.authority.permissions.includes('CAMPUS_MAP_MANAGE'),
);
const allRows = computed(() =>
  inventory.value.flatMap((building) => building.floors.map((floor) => ({ building, floor }))),
);
const rows = computed(() =>
  allRows.value.filter((row) => buildingId.value === 'all' || row.building.id === buildingId.value),
);
const total = computed(() => rows.value.reduce((sum, row) => sum + row.floor.openCount, 0));
const maximum = computed(() => Math.max(1, ...rows.value.map((row) => row.floor.openCount)));
let mounted = false;
let sequence = 0;
let controller: AbortController | undefined;
onMounted(() => {
  mounted = true;
  void load();
});
onUnmounted(() => {
  mounted = false;
  sequence++;
  controller?.abort();
});
watch(
  () =>
    [
      props.client,
      props.client.currentGeneration(),
      props.authority?.userId,
      props.authority?.assigned,
      props.authority?.mode,
      props.authority?.version,
      props.authority?.permissions.join('|'),
    ] as const,
  () => {
    sequence++;
    controller?.abort();
    inventory.value = [];
    buildingId.value = 'all';
    error.value = false;
    if (mounted) void load();
  },
  { flush: 'sync' },
);
async function load(): Promise<void> {
  if (!canView.value) {
    loading.value = false;
    return;
  }
  const client = props.client;
  const generation = client.currentGeneration();
  const request = ++sequence;
  const current = () =>
    mounted &&
    canView.value &&
    request === sequence &&
    props.client === client &&
    client.isCurrentGeneration(generation);
  controller?.abort();
  controller = new AbortController();
  const signal = controller.signal;
  loading.value = true;
  error.value = false;
  try {
    const result = await client.listBuildings(signal);
    if (current()) inventory.value = result;
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) emit('owner-error', cause);
    else if (current() && !signal.aborted) error.value = true;
  } finally {
    if (current()) loading.value = false;
  }
}
</script>

<template>
  <main
    class="admin-map-screen admin-map-statistics"
    :data-theme="theme"
    aria-labelledby="admin-map-statistics-title"
    :aria-busy="loading"
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
      </button><h1 id="admin-map-statistics-title">
        Статистика карты
      </h1>
    </header>
    <AdminNotice
      v-if="!canView"
      tone="warning"
      title="Статистика недоступна"
    >
      Проверь права активного администратора.
    </AdminNotice>
    <p
      v-else-if="loading"
      class="admin-map-state"
      role="status"
    >
      Загружаем статистику…
    </p>
    <AdminNotice
      v-else-if="error"
      tone="danger"
      title="Не удалось загрузить статистику"
    >
      Попробуй получить актуальные данные ещё раз.<template #actions>
        <button
          type="button"
          @click="load"
        >
          Повторить
        </button>
      </template>
    </AdminNotice>
    <div
      v-else-if="!allRows.length"
      class="admin-map-empty"
    >
      <MobileIcon name="room" /><h2>Этажей пока нет</h2><p>Открытия схем появятся здесь после добавления этажей.</p>
    </div>
    <div
      v-else
      class="admin-map-screen__content"
    >
      <section
        class="admin-map-card admin-map-statistics__hero"
        aria-label="Открытия схем"
      >
        <MobileIcon name="attendance-graph" /><p>Открытия схем</p><strong>{{ total.toLocaleString('ru-RU') }}</strong><span>За всё время</span>
      </section>
      <label class="admin-map-field"><span>Корпус</span><span class="admin-map-select"><select v-model="buildingId"><option value="all">Все корпуса</option><option
        v-for="building in inventory"
        :key="building.id"
        :value="building.id"
      >Корпус {{ building.code }}</option></select><MobileIcon name="chevron-down" /></span></label>
      <section
        class="admin-map-card"
        aria-labelledby="admin-map-statistics-floors"
      >
        <h2 id="admin-map-statistics-floors">
          По этажам
        </h2>
        <ul
          class="admin-map-demand"
          aria-label="Количество открытий схем по этажам"
        >
          <li
            v-for="row in rows"
            :key="row.floor.id"
          >
            <div>
              <span>Корпус {{ row.building.code }} · этаж {{ row.floor.code }}</span><strong>{{ row.floor.openCount.toLocaleString('ru-RU') }}</strong>
            </div><progress
              :value="row.floor.openCount"
              :max="maximum"
              :aria-label="`Корпус ${row.building.code}, этаж ${row.floor.code}: ${row.floor.openCount} открытий`"
            />
          </li>
        </ul>
        <p
          v-if="!rows.length"
          class="admin-map-help"
        >
          В этом корпусе пока нет этажей.
        </p>
      </section>
    </div>
  </main>
</template>
