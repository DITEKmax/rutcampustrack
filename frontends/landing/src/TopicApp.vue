<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, type Component } from 'vue'
import BrandLogo from '../../shared/brand/BrandLogo.vue'
import BackToTop from './BackToTop.vue'
import LandingIcon from './art/LandingIcon.vue'
import TopicFlatIllustration from './art/TopicFlatIllustration.vue'
import CampusFloorPlans from './campus/CampusFloorPlans.vue'
import { lessonId, useDemo } from './demo/model'
import ReportExamples from './reports/ReportExamples.vue'
import TechnicalStory from './story/TechnicalStory.vue'
import type { LoadedChapter } from './story/topic-chapters'
import { createLandingScrollEffects } from './story/story-scroll'
import { presentationHome, type Topic } from './topics'
import { takeTopicFlight } from './topic-flight'

defineProps<{ topic: Topic; chapters: LoadedChapter[]; report?: Component }>()
const { markState, mark } = useDemo()
const main = ref<HTMLElement | null>(null)
const header = ref<HTMLElement | null>(null)
const illustration = ref<HTMLElement | null>(null)
let effects: ReturnType<typeof createLandingScrollEffects> | undefined
let revealFrame = 0

function queueReveal() {
  if (revealFrame) return
  revealFrame = window.requestAnimationFrame(() => {
    revealFrame = 0
    effects?.render()
  })
}

function alignAnchor() {
  const id = location.hash.slice(1)
  if (!id) return
  const target = document.getElementById(id)
  if (!target) return
  const options = { block: 'start', behavior: 'instant' } as const
  target.scrollIntoView(options)
  queueReveal()
  // Font reflow and the browser's first native fragment scroll can move a deep anchor.
  if (document.fonts.status === 'loading') {
    const expected = location.hash
    void document.fonts.ready.then(() => {
      if (location.hash !== expected || !target.isConnected) return
      target.scrollIntoView(options)
      queueReveal()
    })
  }
}

function animateIllustration() {
  const flight = takeTopicFlight()
  const element = illustration.value
  if (!flight || !element || typeof element.animate !== 'function' ||
      matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const rect = element.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  const scale = Math.min(flight.width / rect.width, flight.height / rect.height)
  const x = flight.left + flight.width / 2 - (rect.left + rect.width / 2)
  const y = flight.top + flight.height / 2 - (rect.top + rect.height / 2)
  element.dataset.arriving = ''
  const animation = element.animate([
    { transform: 'translate(' + x + 'px,' + y + 'px) scale(' + scale + ')' },
    { transform: 'none' },
  ], { duration: 490, easing: 'cubic-bezier(.19, .82, .24, 1)' })
  const cleanup = () => { delete element.dataset.arriving }
  void animation.finished.then(cleanup, cleanup)
}

onMounted(() => {
  if (main.value) effects = createLandingScrollEffects(main.value, header.value)
  effects?.motion.addEventListener('change', queueReveal)
  window.addEventListener('scroll', queueReveal, { passive: true })
  window.addEventListener('resize', queueReveal)
  window.addEventListener('hashchange', alignAnchor)
  alignAnchor()
  queueReveal()
  animateIllustration()
})
onBeforeUnmount(() => {
  effects?.motion.removeEventListener('change', queueReveal)
  window.removeEventListener('scroll', queueReveal)
  window.removeEventListener('resize', queueReveal)
  window.removeEventListener('hashchange', alignAnchor)
  window.cancelAnimationFrame(revealFrame)
})
</script>

<template>
  <a class="skip-link" href="#topic-title">К содержанию темы</a>
  <header ref="header" class="landing-header topic-header">
    <div class="header-inner">
      <a class="wordmark" :href="presentationHome" aria-label="RutTrack — на главную">
        <BrandLogo alt="" />
      </a>
    </div>
  </header>

  <main ref="main" class="topic-page" :data-topic="topic.slug">
    <div class="topic-intro">
      <div class="landing-shell topic-intro-layout">
        <div class="topic-intro-copy">
          <a class="topic-back" :href="presentationHome + '#topics'">
            <LandingIcon name="arrow" /> Все темы
          </a>
          <h1 id="topic-title" tabindex="-1">{{ topic.title }}</h1>
          <p v-if="topic.lead">{{ topic.lead }}</p>
        </div>
        <div ref="illustration" class="topic-intro-art">
          <TopicFlatIllustration :topic="topic.slug" />
        </div>
      </div>
    </div>
    <component :is="report" v-if="report" />
    <TechnicalStory v-if="chapters.length" :topic="topic.slug" :chapters="chapters" :mark-state="markState"
      @replay="mark(lessonId)" />
    <ReportExamples v-if="topic.slug === 'reports'" />
    <CampusFloorPlans v-if="topic.slug === 'campus'" />
  </main>
  <BackToTop focus-target="topic-title" />
</template>
