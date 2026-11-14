<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';

const tools = [
  'Java 21',
  'Spring Boot',
  'Vue 3',
  'TypeScript',
  'PostgreSQL',
  'MongoDB',
  'Redis',
  'RabbitMQ',
  'gRPC',
  'Docker',
];
const region = ref<HTMLElement | null>(null);
const rail = ref<HTMLElement | null>(null);
const original = ref<HTMLElement | null>(null);
const running = ref(
  typeof Element.prototype.animate === 'function' &&
    !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
);
const copies = ref(running.value ? 4 : 0);
const pixelsPerSecond = 38;
let animation: Animation | null = null;
let duration = 0;
let distance = 0;
let resize: ResizeObserver | undefined;
let visibility: IntersectionObserver | undefined;
let motion: MediaQueryList | undefined;
let visible = true;
let hovered = false;
let disposed = false;

function updatePlayback() {
  if (visible && !hovered) animation?.play();
  else animation?.pause();
}
function measure() {
  if (!running.value || !region.value || !rail.value || !original.value) return;
  const width = original.value.getBoundingClientRect().width;
  if (width <= 0) return;
  copies.value = Math.ceil(region.value.clientWidth / width) + 1;
  if (animation && distance === width) return;

  const phase =
    animation && typeof animation.currentTime === 'number'
      ? (animation.currentTime % duration) / duration
      : 0;
  animation?.cancel();
  distance = width;
  duration = (width / pixelsPerSecond) * 1000;
  animation = rail.value.animate(
    [{ transform: 'translateX(0)' }, { transform: `translateX(-${width}px)` }],
    { duration, iterations: Infinity, easing: 'linear' },
  );
  animation.currentTime = phase * duration;
  updatePlayback();
}
async function setMotion() {
  running.value = !motion?.matches && typeof rail.value?.animate === 'function';
  if (!running.value) {
    animation?.cancel();
    animation = null;
    copies.value = 0;
    return;
  }
  await nextTick();
  if (!disposed) measure();
}
function onEnter(event: PointerEvent) {
  if (event.pointerType === 'touch') return;
  hovered = true;
  updatePlayback();
}
function onLeave() {
  hovered = false;
  updatePlayback();
}
onMounted(() => {
  motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  motion.addEventListener('change', setMotion);
  if (region.value && original.value && 'ResizeObserver' in window) {
    resize = new ResizeObserver(measure);
    resize.observe(region.value);
    resize.observe(original.value);
  } else {
    window.addEventListener('resize', measure);
  }
  if (region.value && 'IntersectionObserver' in window) {
    visibility = new IntersectionObserver(([entry]) => {
      if (!entry) return;
      visible = entry.isIntersecting;
      updatePlayback();
    });
    visibility.observe(region.value);
  }
  void setMotion();
});
onBeforeUnmount(() => {
  disposed = true;
  animation?.cancel();
  resize?.disconnect();
  visibility?.disconnect();
  motion?.removeEventListener('change', setMotion);
  window.removeEventListener('resize', measure);
});
</script>

<template>
  <div
    ref="region"
    class="hero-stack"
    :data-marquee-running="running ? '' : undefined"
    @pointerenter.passive="onEnter"
    @pointerleave.passive="onLeave"
  >
    <div
      ref="rail"
      class="hero-stack__rail"
    >
      <ul
        ref="original"
        role="list"
        aria-label="Технологии проекта"
      >
        <li
          v-for="tool in tools"
          :key="tool"
          >{{ tool }}</li
        >
      </ul>
      <ul
        v-for="copy in copies"
        :key="copy"
        aria-hidden="true"
      >
        <li
          v-for="tool in tools"
          :key="tool"
          >{{ tool }}</li
        >
      </ul>
    </div>
  </div>
</template>
