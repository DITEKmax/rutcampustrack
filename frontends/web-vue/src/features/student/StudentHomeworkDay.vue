<script setup lang="ts">
import { computed } from 'vue';
import { useWebHomework } from '../student-homework/use-homework';
import HomeworkCard from '../student-homework/HomeworkCard.vue';
import { formatHomeworkDate } from '../../../../mobile-core/src/domain/homework';
import { webSession } from '../../auth/session';
import StudentNotice from './StudentNotice.vue';
import StudentPeriodPager from './StudentPeriodPager.vue';
const h = useWebHomework();
const label = computed(() =>
  h.feed.value && h.date.value ? formatHomeworkDate(h.date.value, h.feed.value.serverNow) : 'День',
);
</script>
<template>
  <section
    class="web-stack"
    aria-labelledby="student-homework-title"
  >
    <h2 id="student-homework-title">
      Домашнее задание
    </h2>
    <StudentPeriodPager
      :label="label"
      unit="день заданий"
      :previous-disabled="!h.canStep(-1)"
      :next-disabled="!h.canStep(1)"
      :busy="h.loading.value"
      @step="h.step"
      @reset="h.reset"
    />
    <StudentNotice
      :loading="h.loading.value"
      :error="h.error.value ?? ''"
      @retry="h.load()"
    />
    <template
      v-for="group in h.groups.value"
      :key="group.key"
    >
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
    </template>
    <RouterLink
      class="web-button"
      :to="{ name: 'student-homework' }"
    >
      Все домашние задания
    </RouterLink>
  </section>
</template>
