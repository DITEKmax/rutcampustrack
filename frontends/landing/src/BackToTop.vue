<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import LandingIcon from './art/LandingIcon.vue';
import './landing-back-to-top.pcss';

const props = defineProps<{ focusTarget: string }>();
const overlay = ref<HTMLDivElement | null>(null);
const button = ref<HTMLButtonElement | null>(null);
const visible = ref(false);
const returning = ref(false);
const launching = ref(false);
const exiting = ref(false);
const effectKey = ref(0);
let motion: MediaQueryList | undefined;
let launchTimer = 0;
let flightFrame = 0;
let focusAtDeparture: Element | null = null;

function setFlightPosition(lift: number, tailLift: number) {
  const style = overlay.value?.style;
  style?.setProperty('--back-to-top-lift', `${lift}px`);
  style?.setProperty('--back-to-top-tail-lift', `${tailLift}px`);
}

function finishFlight() {
  window.clearTimeout(launchTimer);
  window.cancelAnimationFrame(flightFrame);
  window.removeEventListener('wheel', cancelFlight);
  window.removeEventListener('pointerdown', cancelFlight);
  window.removeEventListener('keydown', stopOnScrollKey);
  launchTimer = 0;
  flightFrame = 0;
  focusAtDeparture = null;
  returning.value = false;
  exiting.value = false;
}

function cancelFlight() {
  finishFlight();
  launching.value = false;
}

function stopOnScrollKey(event: KeyboardEvent) {
  if (event.key === ' ' && event.target === button.value) return;
  if (['ArrowDown', 'ArrowUp', 'PageDown', 'PageUp', 'Home', 'End', ' '].includes(event.key)) {
    cancelFlight();
  }
}

function focusTop() {
  const focused = document.activeElement;
  if (
    focused === button.value ||
    (returning.value && (focused === document.body || focused === focusAtDeparture))
  ) {
    document.getElementById(props.focusTarget)?.focus({ preventScroll: true });
  }
}

function syncScroll() {
  visible.value = window.scrollY > 0;
  if (!visible.value && document.activeElement === button.value) focusTop();
}

function startFlight() {
  launchTimer = 0;
  const departure = Math.max(0, window.scrollY);
  const bounds = button.value?.getBoundingClientRect();
  if (!departure || !bounds || !overlay.value) {
    syncScroll();
    if (!departure) focusTop();
    cancelFlight();
    return;
  }

  const bottomGap = overlay.value.clientHeight - bounds.bottom;
  const liftAtTop = Math.max(1, bounds.top - bottomGap);
  const liftAtExit = bounds.bottom + 2 * bounds.height;
  const speed = Math.max(0.5, liftAtTop / 620);
  window.scrollTo({ top: departure, behavior: 'instant' });
  let expectedY = window.scrollY;
  let startedAt: number | undefined;

  function advance(now: number) {
    // A scrollbar drag or another scroll outside this flight takes priority.
    if (window.scrollY !== expectedY) {
      syncScroll();
      cancelFlight();
      return;
    }

    startedAt ??= now;
    const lift = Math.min(liftAtExit, (now - startedAt) * speed);
    const tailLift = Math.max(0, lift - liftAtTop) * 0.7;
    setFlightPosition(lift, tailLift);

    if (returning.value) {
      // The scrollbar reaches zero while the button and trail keep the same velocity.
      const remaining = departure * Math.max(0, 1 - lift / liftAtTop);
      window.scrollTo({ top: Math.ceil(remaining), behavior: 'instant' });
      expectedY = window.scrollY;
      if (remaining === 0) {
        syncScroll();
        focusTop();
        exiting.value = true;
        returning.value = false;
      }
    }

    // Render the fully cleared position before starting the dissolve.
    flightFrame = window.requestAnimationFrame(lift < liftAtExit ? advance : finishFlight);
  }

  flightFrame = window.requestAnimationFrame(advance);
}

function syncMotion() {
  if (!motion?.matches) return;
  if (returning.value) {
    window.scrollTo({ top: 0, behavior: 'instant' });
    syncScroll();
    focusTop();
  }
  cancelFlight();
}

function goToTop() {
  if (returning.value || exiting.value || !visible.value) return;
  focusAtDeparture = document.activeElement;
  const { hash, pathname, search } = window.location;
  // Clear the fragment before scrolling so deferred anchor alignment cannot override the click.
  if (hash) window.history.replaceState(window.history.state, '', pathname + search);
  returning.value = true;

  if (motion?.matches) {
    syncMotion();
    return;
  }
  setFlightPosition(0, 0);
  launching.value = true;
  effectKey.value++;
  window.addEventListener('wheel', cancelFlight, { passive: true });
  window.addEventListener('pointerdown', cancelFlight, { passive: true });
  window.addEventListener('keydown', stopOnScrollKey);
  // Let the browser finish handling the touch or fragment scroll before measuring departure.
  launchTimer = window.setTimeout(startFlight, 90);
}

onMounted(() => {
  motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  motion.addEventListener('change', syncMotion);
  window.addEventListener('scroll', syncScroll, { passive: true });
  syncScroll();
});

onBeforeUnmount(() => {
  motion?.removeEventListener('change', syncMotion);
  window.removeEventListener('scroll', syncScroll);
  cancelFlight();
});
</script>

<template>
  <div
    ref="overlay"
    v-show="visible || launching || returning || exiting"
    class="back-to-top"
    :data-launching="launching ? '' : undefined"
    :data-returning="returning ? '' : undefined"
    :data-exiting="exiting ? '' : undefined"
  >
    <div
      v-if="launching"
      :key="effectKey"
      class="back-to-top__effect"
      aria-hidden="true"
      @animationend.self="launching = false"
    >
      <div class="back-to-top__glow"></div>
      <div class="back-to-top__trail"></div>
      <svg
        class="back-to-top__wisps"
        viewBox="0 0 320 100"
        preserveAspectRatio="none"
      >
        <path d="M312 65C263 39 229 93 189 71S151 31 114 50 63 84 41 65 46 38 56 46 54 57 49 53" />
        <path d="M313 79C255 98 247 46 198 52S123 84 86 63 51 34 13 53" />
        <path
          d="M316 24C281 63 267 17 233 35S188 79 156 57 138 24 122 28 104 48 119 47 124 37 117 39"
        />
      </svg>
      <svg
        class="back-to-top__particles"
        viewBox="-12 -12 24 24"
      >
        <circle
          cx="-7"
          cy="4"
          r="1.45"
          fill="currentColor"
        />
        <path
          d="M0-4 .85-.85 4 0 .85.85 0 4-.85.85-4 0-.85-.85Z"
          fill="currentColor"
        />
        <path d="M5-5 7.6-1.8 5 1.4 2.4-1.8Z" />
        <circle
          cx="-3"
          cy="6"
          r="2"
        />
        <path d="M-6-3h5m-2.5-2.5v5" />
        <path
          d="m1 6 2.5-3 1.3 4.2Z"
          fill="currentColor"
        />
        <circle
          cx="6"
          cy="5"
          r="1"
          fill="currentColor"
        />
        <path
          d="M-1-6 1.5-6 1.5-3.5-1-3.5Z"
          fill="currentColor"
        />
        <path d="M-9 2-6 0l2 3-3 2Z" />
        <circle
          cx="3"
          cy="4"
          r="1.6"
          fill="currentColor"
        />
        <path d="M1-1 2.5-2.5 4-1 2.5.5Z" />
        <path d="M-9-1h3m-1.5-1.5v3" />
        <circle
          cx="5"
          cy="7"
          r="1.25"
        />
        <path
          d="m-3-8 2 1-1 2-2-1Z"
          fill="currentColor"
        />
        <circle
          cx="-6"
          cy="-4"
          r=".9"
          fill="currentColor"
        />
        <path
          d="M0 5 .6 6.4 2 7 .6 7.6 0 9-.6 7.6-2 7-.6 6.4Z"
          fill="currentColor"
        />
      </svg>
    </div>
    <button
      v-show="visible || returning || exiting"
      ref="button"
      type="button"
      class="back-to-top__button"
      aria-label="Наверх"
      :aria-disabled="returning || exiting ? true : undefined"
      :aria-hidden="exiting ? true : undefined"
      :tabindex="returning || exiting ? -1 : undefined"
      @click="goToTop"
    >
      <LandingIcon name="down" />
    </button>
  </div>
</template>
