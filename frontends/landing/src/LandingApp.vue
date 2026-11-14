<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import BrandLogo from '../../shared/brand/BrandLogo.vue';
import BackToTop from './BackToTop.vue';
import LandingHero from './LandingHero.vue';
import { createLandingScrollEffects } from './story/story-scroll';
import ProductionOverview from './story/ProductionOverview.vue';
import TopicTiles from './TopicTiles.vue';
import LandingMetrics from './LandingMetrics.vue';
import LandingDeviceShowcase from './LandingDeviceShowcase.vue';
import LandingDemo from './demo/LandingDemo.vue';
import DescentScene from './story/DescentScene.vue';
import { legacyTopicHref } from './legacy-topic-links';
import './story/technical-story.pcss';
import './topics.pcss';

const main = ref<HTMLElement | null>(null);
const header = ref<HTMLElement | null>(null);
let effects: ReturnType<typeof createLandingScrollEffects> | undefined;
const progress = ref(0);

let pending = 0;
function updateProgress() {
  pending = 0;
  const max = document.documentElement.scrollHeight - window.innerHeight;
  progress.value = max > 0 ? Math.max(0, Math.min(1, window.scrollY / max)) : 0;
  effects?.render();
}
function onScroll() {
  if (!pending) pending = window.requestAnimationFrame(updateProgress);
}
function followLegacyAnchor() {
  const destination = legacyTopicHref(window.location.hash);
  if (!destination) return false;
  window.location.replace(destination);
  return true;
}
onMounted(() => {
  if (main.value)
    effects = createLandingScrollEffects(main.value.parentElement ?? main.value, header.value);
  effects?.motion.addEventListener('change', onScroll);
  window.addEventListener('scroll', onScroll, { passive: true });
  window.addEventListener('resize', onScroll);
  window.addEventListener('hashchange', followLegacyAnchor);
  // Initial fragments can be resolved before Vue has mounted the destination.
  if (!followLegacyAnchor()) {
    document.getElementById(window.location.hash.slice(1))?.scrollIntoView({
      block: 'start', behavior: 'instant',
    });
  }
  onScroll();
});
onBeforeUnmount(() => {
  effects?.motion.removeEventListener('change', onScroll);
  window.removeEventListener('scroll', onScroll);
  window.removeEventListener('resize', onScroll);
  window.removeEventListener('hashchange', followLegacyAnchor);
  if (pending) window.cancelAnimationFrame(pending);
});
</script>

<template>
  <a
    class="skip-link"
    href="#demo"
    >Перейти к демо</a
  >
  <header
    ref="header"
    class="landing-header"
  >
    <div class="header-inner">
      <a
        id="landing-brand"
        class="wordmark"
        href="#top"
        aria-label="RutTrack — к началу"
      >
        <BrandLogo alt="" />
      </a>
    </div>
    <div
      class="reading-progress"
      aria-hidden="true"
      ><i :style="{ transform: 'scaleX(' + progress + ')' }"></i
    ></div>
  </header>

  <main ref="main">
    <LandingHero />
    <LandingDemo />

    <DescentScene />

    <div class="rct-story"><ProductionOverview /></div>
    <TopicTiles />
    <LandingMetrics />
    <LandingDeviceShowcase />
  </main>
  <BackToTop focus-target="landing-brand" />
</template>
