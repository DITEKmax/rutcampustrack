<script setup lang="ts">
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import previous from '../../../../mobile-core/src/assets/headman-homework-previous.svg';
import next from '../../../../mobile-core/src/assets/headman-homework-next.svg';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
defineProps<{
  label: string;
  previousDisabled?: boolean;
  nextDisabled?: boolean;
  busy?: boolean;
  unit: string;
}>();
defineEmits<{ step: [direction: -1 | 1]; reset: [] }>();
</script>
<template>
  <div class="student-period-context">
    <button
      class="web-button web-icon-button student-period-arrow"
      :aria-label="`Предыдущий ${unit}`"
      :disabled="previousDisabled || busy"
      @click="$emit('step', -1)"
    >
      <SvgIcon :src="previous" />
    </button>
    <div class="student-period-label"><slot><span role="status">{{ label }}</span></slot></div>
    <button
      class="web-button web-icon-button student-period-arrow"
      :aria-label="`Следующий ${unit}`"
      :disabled="nextDisabled || busy"
      @click="$emit('step', 1)"
    >
      <SvgIcon :src="next" />
    </button>
    <button
      class="web-button web-icon-button student-period-reset"
      aria-label="К текущему периоду"
      title="К текущему периоду"
      :disabled="busy"
      @click="$emit('reset')"
    >
      <MobileIcon name="retry" />
    </button>
  </div>
</template>
