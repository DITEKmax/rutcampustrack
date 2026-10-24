<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue';
import type { StudentStatisticsRankingResponse } from '../../../../mobile-core/src/api/types';
import { studentError, type StudentScope } from './student-state';
import { percent } from './student-format';
import { useStudentAutoPage } from './use-student-auto-page';
import StudentNotice from './StudentNotice.vue';
const props = defineProps<{ scope: StudentScope }>();
const page = shallowRef<StudentStatisticsRankingResponse | null>(null),
  loading = ref(false),
  error = ref('');
let revision = 0;
async function load(append = false) {
  const semester = props.scope.session.value?.semester;
  if (!semester || loading.value) return;
  const previous = page.value;
  const run = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const result = await props.scope.api.getStatisticsRanking(
      semester.id,
      append && previous ? previous.page + 1 : null,
    );
    props.scope.transport.assertCurrent();
    if (!props.scope.current() || run !== revision) return;
    if (append && previous) {
      if (
        result.page !== previous.page + 1 ||
        result.total !== previous.total ||
        result.rows.some((r) => previous.rows.some((old) => old.id === r.id))
      )
        throw Error('Рейтинг изменился. Обнови список.');
      page.value = { ...result, rows: [...previous.rows, ...result.rows] };
    } else page.value = result;
  } catch (cause) {
    if (props.scope.current() && run === revision) error.value = studentError(cause);
  } finally {
    if (props.scope.current() && run === revision) loading.value = false;
  }
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
const { edge, supported } = useStudentAutoPage(canAppend, () => load(true));
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
      </p><ol class="student-ranking-list">
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
      @click="load(true)"
    >
      Следующие участники
    </button>
  </div>
</template>
