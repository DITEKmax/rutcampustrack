<script setup lang="ts">
import { useRoute } from 'vue-router';
import { computed, nextTick, watch } from 'vue';
import { formatHomeworkDate } from '../../../../mobile-core/src/domain/homework';
import { webSession } from '../../auth/session';
import HomeworkCard from './HomeworkCard.vue';
import { useWebHomework } from './use-homework';
import StudentPeriodPager from '../student/StudentPeriodPager.vue';
import '../student/student.pcss';
import EmptyHomeworkIllustration from '../../../../shared/illustrations/EmptyHomeworkIllustration.vue';

const h = useWebHomework();
const route = useRoute();
let focusedHash = '';
watch(() => [h.loading.value, h.feed.value, route.hash], async () => {
  if (h.loading.value || !/^#homework-[1-9][0-9]*$/.test(route.hash) || route.hash === focusedHash) return;
  await nextTick();
  const target = document.getElementById(route.hash.slice(1));
  if (target) { focusedHash = route.hash; target.scrollIntoView({ block: 'center' }); target.querySelector('h3')?.focus(); }
});
const modes = [
  { value: 'day', label: 'День' },
  { value: 'week', label: 'Неделя' },
  { value: 'month', label: 'Месяц' },
] as const;
const periodLabel = computed(() => {
  if (!h.date.value || !h.feed.value) return '';
  if (h.mode.value === 'month')
    return new Intl.DateTimeFormat('ru', { month: 'long', year: 'numeric' }).format(
      new Date(`${h.date.value}T12:00:00Z`),
    );
  if (h.mode.value === 'week' && h.range.value)
    return `${formatHomeworkDate(h.range.value.from, h.feed.value.serverNow)} — ${formatHomeworkDate(h.range.value.to, h.feed.value.serverNow)}`;
  return formatHomeworkDate(h.date.value, h.feed.value.serverNow);
});
const emptyWeek = computed(() => h.mode.value === 'week' && !h.loading.value && !h.error.value &&
  h.groups.value.length > 0 && h.groups.value.every((group) => !group.items.length));
</script>

<template>
  <section
    class="web-homework web-stack"
    data-screen-id="WEB-STUDENT-HOMEWORK"
  >
    <h1>Домашнее задание</h1>
    <div class="web-period-toolbar">
      <div
        class="student-actions"
        role="group"
        aria-label="Период"
      >
        <button
          v-for="mode in modes"
          :key="mode.value"
          class="web-button"
          :class="{ 'web-button-selected': mode.value === h.mode.value }"
          :aria-pressed="mode.value === h.mode.value"
          @click="h.mode.value = mode.value"
        >
          {{ mode.label }}
        </button>
      </div>
      <StudentPeriodPager
        class="student-homework-period"
        :label="periodLabel"
        unit="период заданий"
        :previous-disabled="!h.canStep(-1)"
        :next-disabled="!h.canStep(1)"
        :busy="h.loading.value"
        @step="h.step"
        @reset="h.reset"
      />
    </div>
    <div
      v-if="h.error.value"
      class="web-notice web-stack"
      role="alert"
    >
      <h2>{{ h.forbidden.value ? 'Нет доступа к заданиям' : 'Не удалось обновить задания' }}</h2><p>{{ h.error.value }}</p><button
        class="web-button"
        @click="h.load()"
      >
        Повторить
      </button>
    </div>
    <div
      v-if="h.loading.value && !h.feed.value"
      class="web-stack"
      aria-busy="true"
    >
      <span
        class="web-sr-only"
        role="status"
      >Загрузка заданий</span><div
        v-for="index in 3"
        :key="index"
        class="web-skeleton"
      />
    </div>
    <div
      v-else-if="h.feed.value"
      class="web-homework-layout"
      :class="{ 'has-calendar': h.mode.value === 'month' }"
      :aria-busy="h.loading.value"
    >
      <aside
        v-if="h.mode.value === 'month'"
        class="web-calendar web-stack"
        aria-label="Календарь домашних заданий"
      >
        <h2>Календарь месяца</h2>
        <div class="web-calendar-grid">
          <span
            v-for="day in ['ПН', 'ВТ', 'СР', 'ЧТ', 'ПТ', 'СБ', 'ВС']"
            :key="day"
            class="web-caption web-calendar-weekday"
          >{{ day }}</span>
          <button
            v-for="day in h.calendar.value"
            :key="day.date"
            class="web-calendar-day"
            :class="{
              'is-selected': day.date === h.selected.value,
              'is-today': day.date === h.today.value,
            }"
            :disabled="
              !day.inMonth ||
                day.date < h.feed.value.semester.dateFrom ||
                day.date > h.feed.value.semester.dateTo
            "
            :aria-label="`${day.date}${day.count ? `, заданий: ${day.count}` : ''}`"
            :aria-pressed="day.date === h.selected.value"
            :aria-current="day.date === h.today.value ? 'date' : undefined"
            @click="h.selected.value = day.date"
          >
            <span>{{ day.number }}</span><small :class="{ 'is-empty': !day.count }" :aria-hidden="!day.count">{{ day.count || '' }}</small>
          </button>
        </div>
      </aside>
      <div class="web-stack web-homework-groups" :class="{ 'is-week': h.mode.value === 'week' }">
        <section v-if="emptyWeek" class="web-stack">
          <h2>{{ periodLabel }}</h2>
          <div class="web-homework-empty"><EmptyHomeworkIllustration /><p role="status">Заданий нет</p></div>
        </section>
        <template v-else><section
          v-for="group in h.groups.value"
          :key="group.key"
          class="web-stack web-homework-day"
        >
          <h2>{{ group.label }}</h2>
          <div v-if="!group.items.length && h.mode.value !== 'week' && !h.loading.value && !h.error.value" class="web-homework-empty">
            <EmptyHomeworkIllustration /><p role="status">Заданий нет</p>
          </div>
          <p
            v-else-if="!group.items.length && h.mode.value === 'week' && !h.loading.value && !h.error.value"
            class="web-caption"
          >
            Заданий нет
          </p>
          <HomeworkCard
            v-for="item in group.items"
            :key="item.id"
            :item="item"
            :pending="Boolean(h.pending[item.id])"
            :read-only="Boolean(webSession.snapshot.value?.readOnly)"
            :error="h.itemErrors[item.id]"
            :uncertain="Boolean(h.uncertain[item.id])"
            @complete="h.complete(item, $event)"
            @reconcile="h.reconcile(item.id)"
          />
        </section></template>
      </div>
    </div>
  </section>
</template>
