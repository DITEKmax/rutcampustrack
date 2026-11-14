<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch, type ComponentPublicInstance } from 'vue';

const props = defineProps<{ paused: boolean }>();
const fullTurn = Math.PI * 2;

const shapes = [
  { path: 'M-1.05 0a1.05 1.05 0 1 0 2.1 0 1.05 1.05 0 1 0-2.1 0', outline: false },
  { path: 'M0-3 .75-.75 2.65 0 .75.75 0 3-.75.75-2.65 0-.75-.75Z', outline: false },
  { path: 'M-1.65-1.65h3.3v3.3h-3.3Z', outline: false },
  { path: 'M0-2.5 1.85 0 0 2.5-1.85 0Z', outline: true },
  { path: 'M-2.3 0h4.6M0-2.3v4.6', outline: true },
  { path: 'M-2-1.05 0-2.3 2-1.05V1.05L0 2.3-2 1.05Z', outline: true },
  { path: 'M-2.35 1.5.35-2.1 1.65 1.25Z', outline: false },
] as const;

interface Position {
  x: number;
  y: number;
}
interface Drift {
  amplitude: number;
  speed: number;
  phase: number;
  secondaryAmplitude: number;
  secondarySpeed: number;
  secondaryPhase: number;
}
interface Particle extends Position {
  shape: (typeof shapes)[number];
  opacity: number;
  pale: boolean;
  angle: number;
  scale: number;
  driftX: Drift;
  driftY: Drift;
  offsetX: number;
  offsetY: number;
  offsetNode: Element | null;
}

const layer = ref<SVGSVGElement | null>(null);
const particles = shallowRef<Particle[]>([]);
const viewBox = ref('0 0 1 1');
let width = 0;
let height = 0;
let motionQuery: MediaQueryList | undefined;
let pointer: Position | null = null;
let localPointer: Position | null = null;
let observer: ResizeObserver | undefined;
let stopListening: (() => void) | undefined;
let frame = 0;
let lastTime = 0;
let motionTime = 0;

// A fixed seed keeps positions stable between reloads. Each grid cell has its own jitter.
function makeParticles(columns: number, rows: number): Particle[] {
  let seed = 5081;
  function random() {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  }
  function makeDrift(amplitude: number): Drift {
    return {
      amplitude: amplitude * (0.55 + random() * 0.45),
      speed: fullTurn / (17 + random() * 21),
      phase: random() * fullTurn,
      secondaryAmplitude: amplitude * (0.15 + random() * 0.25),
      secondarySpeed: fullTurn / (11 + random() * 9),
      secondaryPhase: random() * fullTurn,
    };
  }
  return Array.from({ length: columns * rows }, (_, index) => {
    const variety = random();
    const shapeIndex =
      variety < 0.34 ? 0 : 1 + Math.floor(((variety - 0.34) / 0.66) * (shapes.length - 1));
    return {
      x: (((index % columns) + 0.1 + random() * 0.8) / columns) * width,
      y: ((Math.floor(index / columns) + 0.08 + random() * 0.84) / rows) * height,
      shape: shapes[shapeIndex] ?? shapes[0],
      opacity: 0.16 + random() ** 1.8 * 0.69,
      pale: random() < 0.21,
      angle: random() * 360,
      scale: 0.65 + random() * 0.8,
      driftX: makeDrift(22),
      driftY: makeDrift(17),
      offsetX: 0,
      offsetY: 0,
      offsetNode: null,
    };
  });
}

function bindOffsetNode(particle: Particle, node: Element | ComponentPublicInstance | null) {
  particle.offsetNode = node instanceof Element ? node : null;
}

// Two waves with separate periods give each axis a smooth, changing trajectory.
function driftAtTime(drift: Drift) {
  return (
    drift.amplitude * Math.sin(drift.phase + motionTime * drift.speed) +
    drift.secondaryAmplitude * Math.sin(drift.secondaryPhase + motionTime * drift.secondarySpeed)
  );
}

function moveParticles(easing: number) {
  const radius = Math.min(112, Math.max(62, width * 0.2));
  const strength = radius * 0.3;
  for (const particle of particles.value) {
    const driftX = driftAtTime(particle.driftX);
    const driftY = driftAtTime(particle.driftY);
    let targetX = 0;
    let targetY = 0;
    if (localPointer) {
      const dx = particle.x + driftX - localPointer.x;
      const dy = particle.y + driftY - localPointer.y;
      const distance = Math.hypot(dx, dy);
      if (distance < radius) {
        const force = strength * (1 - distance / radius) ** 2;
        const angle = distance < 0.01 ? (particle.angle * Math.PI) / 180 : Math.atan2(dy, dx);
        targetX = Math.cos(angle) * force;
        targetY = Math.sin(angle) * force;
      }
    }
    const dx = targetX - particle.offsetX;
    const dy = targetY - particle.offsetY;
    particle.offsetX = Math.abs(dx) > 0.08 ? particle.offsetX + dx * easing : targetX;
    particle.offsetY = Math.abs(dy) > 0.08 ? particle.offsetY + dy * easing : targetY;
    const x = driftX + particle.offsetX;
    const y = driftY + particle.offsetY;
    particle.offsetNode?.setAttribute('transform', `translate(${x.toFixed(2)} ${y.toFixed(2)})`);
  }
}

function tick(time: number) {
  frame = 0;
  const elapsed = lastTime ? Math.min(time - lastTime, 32) : 16;
  motionTime += elapsed / 1000;
  lastTime = time;
  moveParticles(1 - Math.exp(-elapsed / 86));
  schedule();
}

function schedule() {
  if (!frame && !props.paused && !motionQuery?.matches) frame = requestAnimationFrame(tick);
}

function pauseAnimation() {
  if (frame) cancelAnimationFrame(frame);
  frame = 0;
  lastTime = 0;
  pointer = null;
  localPointer = null;
}

function syncPointer() {
  if (!pointer) return;
  const box = layer.value?.getBoundingClientRect();
  if (!box) return;
  const x = pointer.x - box.left;
  const y = pointer.y - box.top;
  localPointer = x >= 0 && x <= width && y >= 0 && y <= height ? { x, y } : null;
}

function releasePointer() {
  pointer = null;
  localPointer = null;
}

function followPointer(event: PointerEvent) {
  if (!event.isPrimary || event.pointerType === 'touch' || props.paused || motionQuery?.matches)
    return;
  pointer = { x: event.clientX, y: event.clientY };
  syncPointer();
}

function followTouch(event: TouchEvent) {
  if (props.paused || motionQuery?.matches) return;
  const touch = event.touches[0];
  if (event.touches.length !== 1 || !touch) {
    releasePointer();
    return;
  }
  pointer = { x: touch.clientX, y: touch.clientY };
  syncPointer();
  // A brief tap may finish before the first animation frame.
  if (event.type === 'touchstart' && localPointer) moveParticles(0.28);
}

function setSize(nextWidth: number, nextHeight: number) {
  if (!nextWidth || !nextHeight || (nextWidth === width && nextHeight === height)) return;
  width = nextWidth;
  height = nextHeight;
  viewBox.value = `0 0 ${width} ${height}`;
  const columns = Math.min(24, Math.max(5, Math.round(width / 100)));
  const rows = Math.min(16, Math.max(7, Math.round(height / 68)));
  particles.value = makeParticles(columns * 2, rows);
  syncPointer();
}

function onMotionChange() {
  if (!motionQuery?.matches) {
    schedule();
    return;
  }
  pauseAnimation();
  for (const particle of particles.value) {
    particle.offsetX = 0;
    particle.offsetY = 0;
    particle.offsetNode?.removeAttribute('transform');
  }
}

watch(() => props.paused, (paused) => {
  if (paused) pauseAnimation();
  else schedule();
});

onMounted(() => {
  const svg = layer.value;
  const hero = svg?.parentElement;
  if (!svg || !hero) return;

  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
  motionQuery.addEventListener('change', onMotionChange);
  const { width: initialWidth, height: initialHeight } = svg.getBoundingClientRect();
  setSize(initialWidth, initialHeight);
  observer = new ResizeObserver(([entry]) => {
    if (entry) setSize(entry.contentRect.width, entry.contentRect.height);
  });
  observer.observe(svg);
  schedule();

  hero.addEventListener('pointermove', followPointer, { passive: true });
  hero.addEventListener('pointerleave', releasePointer, { passive: true });
  // Touch events continue during native panning, when pointer events get canceled.
  hero.addEventListener('touchstart', followTouch, { passive: true });
  hero.addEventListener('touchmove', followTouch, { passive: true });
  hero.addEventListener('touchend', releasePointer, { passive: true });
  hero.addEventListener('touchcancel', releasePointer, { passive: true });
  window.addEventListener('scroll', syncPointer, { passive: true });
  window.addEventListener('blur', releasePointer);
  stopListening = () => {
    hero.removeEventListener('pointermove', followPointer);
    hero.removeEventListener('pointerleave', releasePointer);
    hero.removeEventListener('touchstart', followTouch);
    hero.removeEventListener('touchmove', followTouch);
    hero.removeEventListener('touchend', releasePointer);
    hero.removeEventListener('touchcancel', releasePointer);
    window.removeEventListener('scroll', syncPointer);
    window.removeEventListener('blur', releasePointer);
  };
});

onBeforeUnmount(() => {
  stopListening?.();
  observer?.disconnect();
  motionQuery?.removeEventListener('change', onMotionChange);
  pauseAnimation();
});
</script>

<template>
  <svg
    ref="layer"
    class="hero-particles"
    :viewBox="viewBox"
    aria-hidden="true"
  >
    <g
      v-for="(particle, index) in particles"
      :key="index"
      :transform="`translate(${particle.x} ${particle.y})`"
      :opacity="particle.opacity"
      :color="particle.pale ? 'var(--landing-text)' : undefined"
    >
      <g :ref="(node) => bindOffsetNode(particle, node)">
        <path
          :d="particle.shape.path"
          :transform="`rotate(${particle.angle}) scale(${particle.scale})`"
          :fill="particle.shape.outline ? 'none' : 'currentColor'"
          :stroke="particle.shape.outline ? 'currentColor' : undefined"
          stroke-width="0.85"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </g>
    </g>
  </svg>
</template>
