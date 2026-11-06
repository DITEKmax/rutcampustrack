<script setup lang="ts">
import { computed } from 'vue';
import type { CheckinArrivalGeometry } from './checkin-impulse';
import { measuredRem } from './checkin-impulse-effects';
import './checkin-impulse.pcss';

const props = defineProps<{
  arrival: { phase: 'covering' | 'covered' | 'revealing'; geometry: CheckinArrivalGeometry };
}>();

const discStyle = computed(() => {
  const { x, y, radius, startScale } = props.arrival.geometry;
  return {
    '--impulse-arrival-x': measuredRem(x),
    '--impulse-arrival-y': measuredRem(y),
    '--impulse-arrival-radius': measuredRem(radius),
    '--impulse-arrival-start': startScale,
  };
});
</script>

<template>
  <div
    class="checkin-arrival"
    data-checkin-arrival
    :data-phase="arrival.phase"
    aria-hidden="true"
  >
    <div
      class="checkin-arrival__disc"
      :style="discStyle"
    />
  </div>
</template>
