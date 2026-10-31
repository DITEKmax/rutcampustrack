<script setup lang="ts">
import { computed, nextTick, ref, shallowRef, watch } from 'vue';
import type { StudentStatisticsRankingResponse } from '../../../../mobile-core/src/api/types';
import { StudentApiError } from '../../../../mobile-core/src/api/student-client';
import { studentError, type StudentScope } from './student-state';
import { percent } from './student-format';
import { useStudentAutoPage } from './use-student-auto-page';
import StudentNotice from './StudentNotice.vue';
const props = defineProps<{ scope: StudentScope }>();
const page = shallowRef<StudentStatisticsRankingResponse | null>(null),
  loading = ref(false),
  error = ref('');
const firstPage = ref(0),
  earlierPages = ref(false),
  list = ref<HTMLElement | null>(null);
let revision = 0;
async function load(direction: 'replace' | 'next' | 'previous' = 'replace') {
  const semester = props.scope.session.value?.semester;
  if (!semester || (loading.value && direction !== 'replace')) return;
  const previous = page.value;
  const requestedPage = previous
    ? direction === 'next'
      ? previous.page + 1
      : direction === 'previous'
        ? firstPage.value - 1
        : null
    : null;
  if (requestedPage !== null && requestedPage < 0) return;
  const run = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const result = await props.scope.api.getStatisticsRanking(semester.id, requestedPage);
    props.scope.transport.assertCurrent();
    if (!props.scope.current() || run !== revision) return;
    if (direction !== 'replace' && previous) {
      if (
        result.page !== requestedPage ||
        result.total !== previous.total ||
        result.rows.some((r) => previous.rows.some((old) => old.id === r.id))
      )
        throw Error('Рейтинг изменился. Обнови список.');
      if (direction === 'previous') {
        page.value = { ...previous, rows: [...result.rows, ...previous.rows] };
        firstPage.value = result.page;
      } else page.value = { ...result, rows: [...previous.rows, ...result.rows] };
    } else {
      page.value = result;
      firstPage.value = result.page;
      earlierPages.value = result.page > 0;
    }
  } catch (cause) {
    if (props.scope.current() && run === revision) {
      if (cause instanceof StudentApiError && [401, 403, 404].includes(cause.response.status))
        page.value = null;
      error.value = studentError(cause);
    }
  } finally {
    if (props.scope.current() && run === revision) loading.value = false;
  }
}
watch(
  props.scope.contextKey,
  () => {
    revision++;
    page.value = null;
    loading.value = false;
  },
  { flush: 'sync' },
);
async function ownPosition() {
  await load();
  await nextTick();
  list.value?.querySelector<HTMLElement>('.is-self')?.scrollIntoView({ block: 'nearest' });
}
watch(
  () => props.scope.refreshed.value,
  () => load(),
  { immediate: true },
);
const hasMore = computed(() =>
  Boolean(page.value && (page.value.page + 1) * page.value.size < page.value.total),
);
const canAppend = computed(() => hasMore.value && !loading.value && !error.value);
const { edge, supported } = useStudentAutoPage(canAppend, () => load('next'));
</script>
<template>
  <div class="web-stack student-ranking">
    <StudentNotice
      :loading="loading"
      :error="error"
      @retry="load()"
    />
    <template v-if="page">
      <p v-if="!page.available">
        Рейтинг пока недоступен: нет учитываемых пар.
      </p><p v-else-if="page.ownPosition === null">
        Твоя позиция пока не определена.
      </p><p v-else>
        Твоя позиция: {{ page.ownPosition }} из {{ page.total }}
      </p><p
        v-if="!page.rows.length"
        class="web-caption"
      >
        Участников с результатом пока нет.
      </p><div
        v-if="earlierPages"
        class="student-actions"
      >
        <button
          class="web-button"
          :disabled="loading || firstPage <= 0"
          @click="load('previous')"
        >
          Предыдущие участники
        </button><button
          class="web-button"
          :disabled="loading"
          @click="ownPosition"
        >
          К моей позиции
        </button>
      </div><ol
        ref="list"
        class="student-ranking-list"
      >
        <li
          v-for="row in page.rows"
          :key="row.id"
          :class="{ 'is-self': row.isSelf }"
        >
          <span>{{ row.position ?? '—' }}. {{ row.name }}{{ row.isSelf ? ' · Ты' : '' }}</span><strong>{{ percent(row.percentage) }}</strong>
        </li>
      </ol>
    </template>
    <span
      ref="edge"
      class="student-auto-edge"
      aria-hidden="true"
    />
    <button
      v-if="hasMore && !supported"
      class="web-button"
      :disabled="loading"
      @click="load('next')"
    >
      Следующие участники
    </button>
  </div>
</template>
