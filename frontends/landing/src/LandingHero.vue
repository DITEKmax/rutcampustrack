<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import LandingIcon from './art/LandingIcon.vue';
import HeroParticles from './art/HeroParticles.vue';
import HeroOrbit from './art/HeroOrbit.vue';
import HeroStack from './art/HeroStack.vue';

const hero = ref<HTMLElement | null>(null);
const orbit = ref<HTMLElement | null>(null);
const entering = ref(true);
const motionPaused = ref(false);
let visibility: IntersectionObserver | undefined;
let motion: MediaQueryList | undefined;
let frame = 0;
let pointer: { x: number; y: number } | null = null;

function followOrbit(event: PointerEvent) {
  if (!event.isPrimary || event.pointerType === 'touch' || motion?.matches || motionPaused.value)
    return;
  pointer = { x: event.clientX, y: event.clientY };
  if (!frame) frame = requestAnimationFrame(moveOrbit);
}

function moveOrbit() {
  frame = 0;
  if (!hero.value || !orbit.value || !pointer) return;
  const box = hero.value.getBoundingClientRect();
  if (!box.width || !box.height) return;
  const x = (2 * (pointer.x - box.left)) / box.width - 1;
  const y = (2 * (pointer.y - box.top)) / box.height - 1;
  orbit.value.style.setProperty('--hero-orbit-pointer-x', `${(x * 0.5).toFixed(3)}rem`);
  orbit.value.style.setProperty('--hero-orbit-pointer-y', `${(y * 0.33).toFixed(3)}rem`);
  orbit.value.style.setProperty(
    '--hero-orbit-pointer-angle',
    `${(x * 1.55 - y * 0.35).toFixed(2)}deg`,
  );
}

function settleOrbit() {
  pointer = null;
  if (frame) cancelAnimationFrame(frame);
  frame = 0;
  orbit.value?.style.removeProperty('--hero-orbit-pointer-x');
  orbit.value?.style.removeProperty('--hero-orbit-pointer-y');
  orbit.value?.style.removeProperty('--hero-orbit-pointer-angle');
}

function finishEntrance(event: AnimationEvent) {
  if (event.animationName === 'hero-scene-enter') entering.value = false;
}

function syncMotionPreference() {
  if (!motion?.matches) return;
  entering.value = false;
  settleOrbit();
}

onMounted(() => {
  motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  motion.addEventListener('change', syncMotionPreference);
  syncMotionPreference();
  window.addEventListener('blur', settleOrbit);

  if (hero.value && 'IntersectionObserver' in window) {
    visibility = new IntersectionObserver(([entry]) => {
      if (!entry) return;
      motionPaused.value = !entry.isIntersecting;
      if (motionPaused.value) settleOrbit();
    });
    visibility.observe(hero.value);
  }
});

onBeforeUnmount(() => {
  visibility?.disconnect();
  motion?.removeEventListener('change', syncMotionPreference);
  window.removeEventListener('blur', settleOrbit);
  if (frame) cancelAnimationFrame(frame);
});
</script>

<template>
  <section
    id="top"
    ref="hero"
    class="landing-hero"
    aria-labelledby="hero-title"
    :data-hero-entering="entering ? '' : undefined"
    :data-hero-paused="motionPaused ? '' : undefined"
    @pointermove.passive="followOrbit"
    @pointerleave.passive="settleOrbit"
  >
    <HeroParticles :paused="motionPaused" />
    <div
      class="hero-copy"
      data-landing-reveal="hero"
    >
      <p class="hero-kicker"><span class="landing-dot"></span> Учебный день становится понятнее</p>
      <h1 id="hero-title"
        >За каждой отметкой<br />
        целая система.</h1
      >
      <p class="hero-intro">Расписание, посещаемость и учебный контекст.</p>
    </div>
    <div
      ref="orbit"
      class="hero-orbit"
      data-landing-reveal="hero"
      aria-hidden="true"
      @animationend="finishEntrance"
    >
      <div class="hero-orbit__parallax"><HeroOrbit /></div>
      <div class="hero-orbit-card hero-orbit-card--first">
        <span class="orbit-tag hero-orbit-card__face"
          ><LandingIcon name="calendar" /> Пара началась</span
        >
      </div>
      <div class="hero-orbit-card hero-orbit-card--presence">
        <div class="hero-presence hero-orbit-card__face">
          <span class="hero-presence__icon"><LandingIcon name="check" /></span>
          <div><small>Всё начинается здесь</small><b>Я на занятии</b></div>
          <span class="landing-dot"></span>
        </div>
      </div>
      <div class="hero-orbit-card hero-orbit-card--last">
        <span class="orbit-tag hero-orbit-card__face"
          ><LandingIcon name="chart" /> Журнал обновлён</span
        >
      </div>
    </div>
    <HeroStack data-landing-reveal="hero" />
  </section>
</template>
