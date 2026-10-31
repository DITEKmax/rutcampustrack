<script setup lang="ts">
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import previous from '../../../../mobile-core/src/assets/headman-homework-previous.svg';
import next from '../../../../mobile-core/src/assets/headman-homework-next.svg';
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
      class="web-button web-icon-button"
      :aria-label="`Предыдущий ${unit}`"
      :disabled="previousDisabled || busy"
      @click="$emit('step', -1)"
    >
      <SvgIcon :src="previous" />
    </button>
    <span role="status">{{ label }}</span>
    <button
      class="web-button web-icon-button"
      :aria-label="`Следующий ${unit}`"
      :disabled="nextDisabled || busy"
      @click="$emit('step', 1)"
    >
      <SvgIcon :src="next" />
    </button>
    <button
      class="web-button"
      :disabled="busy"
      @click="$emit('reset')"
    >
      Текущий период
    </button>
  </div>
</template>
