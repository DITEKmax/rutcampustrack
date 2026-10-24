<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import type { StudentHomeworkItem } from '../../../../mobile-core/src/api/types';
import { validateHomeworkLink } from '../../../../mobile-core/src/domain/homework';
import LinkifiedText from '../../shared/LinkifiedText.vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import externalLinkIcon from '../../../../mobile-core/src/assets/homework-external-link.svg';

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
const description = ref<HTMLElement | null>(null);
const expanded = ref(false);
const canExpand = ref(false);
let observer: ResizeObserver | undefined;
const measureDescription = () => {
  if (!expanded.value && description.value) canExpand.value = description.value.scrollHeight > description.value.clientHeight + 1;
};
onMounted(() => {
  observer = new ResizeObserver(measureDescription);
  if (description.value) observer.observe(description.value);
  measureDescription();
});
onUnmounted(() => observer?.disconnect());
watch(() => props.item.description, async () => {
  expanded.value = false;
  await nextTick();
  measureDescription();
});
</script>

<template>
  <article
    :id="`homework-${item.id}`"
    class="web-homework-card"
    :class="{ 'is-completed': item.completed, 'is-archived': item.archived }"
    :aria-busy="pending"
  >
    <header class="web-homework-heading">
      <div>
        <h3 tabindex="-1">
          {{ item.title || item.subject.name }}
        </h3><p class="web-caption">
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
    <div
      ref="description"
      class="web-homework-description"
      :class="{ 'is-expanded': expanded }"
    >
      <LinkifiedText :text="item.description" />
    </div>
    <button
      v-if="canExpand || expanded"
      class="web-button web-description-toggle"
      :aria-expanded="expanded"
      :aria-label="`${expanded ? 'Свернуть' : 'Развернуть'} описание задания ${item.title || item.subject.name}`"
      @click="expanded = !expanded"
    >
      {{ expanded ? 'Свернуть' : 'Полное задание' }}
    </button>
    <a
      v-if="href"
      class="web-homework-link"
      :href="href"
      target="_blank"
      rel="noopener noreferrer"
    >{{ domain }}
      <SvgIcon :src="externalLinkIcon" /><span class="web-sr-only"> (в новой вкладке)</span></a>
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
