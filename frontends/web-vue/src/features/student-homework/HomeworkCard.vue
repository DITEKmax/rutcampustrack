<script setup lang="ts">
import { computed } from 'vue';
import type { StudentHomeworkItem } from '../../../../mobile-core/src/api/types';
import { validateHomeworkLink } from '../../../../mobile-core/src/domain/homework';
import LinkifiedText from '../../shared/LinkifiedText.vue';

const props = defineProps<{
  item: StudentHomeworkItem;
  pending: boolean;
  readOnly: boolean;
  error?: string | undefined;
  uncertain: boolean;
}>();
defineEmits<{ complete: [completed: boolean]; reconcile: [] }>();
const href = computed(() =>
  props.item.link && validateHomeworkLink(props.item.link).supported ? props.item.link : null,
);
const domain = computed(() => (href.value ? new URL(href.value).host : ''));
</script>

<template>
  <article
    class="web-homework-card"
    :class="{ 'is-completed': item.completed, 'is-archived': item.archived }"
    :aria-busy="pending"
  >
    <header class="web-homework-heading">
      <div>
        <h3>{{ item.title || item.subject.name }}</h3><p class="web-caption">
          {{ item.subject.name }} ·
          <time :datetime="item.lessonDate">{{
            new Intl.DateTimeFormat('ru', { day: 'numeric', month: 'long' }).format(
              new Date(`${item.lessonDate}T12:00:00Z`),
            )
          }}</time><span v-if="item.lessonNumber"> · {{ item.lessonNumber }} пара</span>
        </p>
      </div>
      <span
        v-if="item.archived"
        class="web-caption"
      >В архиве</span>
      <label
        v-else
        class="web-completion"
      ><input
        type="checkbox"
        :checked="item.completed"
        :disabled="pending || readOnly || uncertain"
        :aria-label="`Выполнено: ${item.title || item.subject.name}`"
        @change="$emit('complete', ($event.target as HTMLInputElement).checked)"
      ><span>Выполнено</span></label>
    </header>
    <div class="web-homework-description">
      <LinkifiedText :text="item.description" />
    </div>
    <a
      v-if="href"
      class="web-homework-link"
      :href="href"
      target="_blank"
      rel="noopener noreferrer"
    >{{ domain }}
      <img
        src="../../../../mobile-core/src/assets/homework-external-link.svg"
        alt="(в новой вкладке)"
      ></a>
    <div
      v-if="error"
      class="web-error"
      role="alert"
    >
      <p>{{ error }}</p><button
        v-if="uncertain"
        class="web-button"
        @click="$emit('reconcile')"
      >
        Проверить состояние
      </button>
    </div>
  </article>
</template>
