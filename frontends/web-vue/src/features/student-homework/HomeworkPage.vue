<script setup lang="ts">
import { computed } from 'vue';
import { formatHomeworkDate } from '../../../../mobile-core/src/domain/homework';
import { webSession } from '../../auth/session';
import HomeworkCard from './HomeworkCard.vue';
import { useWebHomework } from './use-homework';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import previousIcon from '../../../../mobile-core/src/assets/headman-homework-previous.svg';
import nextIcon from '../../../../mobile-core/src/assets/headman-homework-next.svg';

const h = useWebHomework();
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
</script>

<template>
  <section
    class="web-homework web-stack"
    data-screen-id="WEB-STUDENT-HOMEWORK"
    @pointerenter="h.pointerWithin.value = true"
    @pointerleave="h.pointerWithin.value = false"
    @focusin="h.focusWithin.value = true"
    @focusout="
      h.focusWithin.value = Boolean(
        ($event.relatedTarget as Node | null) && $el.contains($event.relatedTarget),
      )
    "
  >
    <h1>Домашнее задание</h1>
    <div class="web-period-toolbar">
      <div
        class="web-period-modes"
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
      <div class="web-date-pager">
        <button
          class="web-button web-icon-button"
          aria-label="Предыдущий период"
          :disabled="!h.canStep(-1) || h.loading.value"
          @click="h.step(-1)"
        >
          <SvgIcon :src="previousIcon" />
        </button>
        <span
          class="web-period-label"
          role="status"
        >{{ periodLabel }}</span>
        <button
          class="web-button web-icon-button"
          aria-label="Следующий период"
          :disabled="!h.canStep(1) || h.loading.value"
          @click="h.step(1)"
        >
          <SvgIcon :src="nextIcon" />
        </button>
        <button
          class="web-button"
          @click="h.reset"
        >
          Сегодня
        </button>
      </div>
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
            class="web-caption"
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
            <span>{{ day.number }}</span><small v-if="day.count">{{ day.count }}</small>
          </button>
        </div>
      </aside>
      <div class="web-stack">
        <section
          v-for="group in h.groups.value"
          :key="group.key"
          class="web-stack"
        >
          <h2>{{ group.label }}</h2>
          <p
            v-if="!group.items.length"
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
        </section>
      </div>
    </div>
  </section>
</template>
