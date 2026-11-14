<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import TopicFlatIllustration from './art/TopicFlatIllustration.vue'
import { saveTopicFlight } from './topic-flight'
import { topicHref, topics } from './topics'

const grid = ref<HTMLElement | null>(null)
const observing = ref(false)
const revealed = ref(new Map<string, number>())
const visitedByFocus = ref(new Set<string>())
const leaving = ref<string | null>(null)
let observer: IntersectionObserver | undefined
let navigationTimer: number | undefined

function resetNavigation() {
  window.clearTimeout(navigationTimer)
  leaving.value = null
}

function revealFocused(slug: string) {
  if (visitedByFocus.value.has(slug)) return
  visitedByFocus.value = new Set(visitedByFocus.value).add(slug)
}

function visit(event: MouseEvent, slug: string) {
  if (event.defaultPrevented || event.button !== 0 ||
      event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  if (location.hash !== '#topics')
    history.replaceState(history.state, '', '#topics')
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  event.preventDefault()
  if (leaving.value) return

  const link = event.currentTarget as HTMLAnchorElement
  leaving.value = slug
  navigationTimer = window.setTimeout(() => {
    const art = link.querySelector('.topic-card-art')
    if (art) saveTopicFlight(link.pathname, art.getBoundingClientRect())
    window.location.assign(link.href)
  }, 180)
}

onMounted(() => {
  window.addEventListener('pageshow', resetNavigation)
  if (!grid.value || !('IntersectionObserver' in window)) return
  observing.value = true
  observer = new IntersectionObserver(entries => {
    const visible = entries.filter(entry => entry.isIntersecting)
    if (!visible.length) return
    const seen = new Map(revealed.value)
    const children = Array.from(grid.value?.children ?? [])
    visible.sort((a, b) => children.indexOf(a.target) - children.indexOf(b.target))
    for (const [order, { target }] of visible.entries()) {
      const slug = target.getAttribute('data-topic-tile')
      if (slug && !seen.has(slug)) seen.set(slug, order * 90)
      observer?.unobserve(target)
    }
    revealed.value = seen
  }, { threshold: .15 })
  for (const item of grid.value.children) observer.observe(item)
})
onBeforeUnmount(() => {
  window.removeEventListener('pageshow', resetNavigation)
  observer?.disconnect()
  resetNavigation()
})
</script>

<template>
  <nav id="topics" class="landing-shell topic-tiles" aria-label="Темы презентации">
    <ul ref="grid" :data-observing="observing ? '' : undefined">
      <li v-for="topic in topics" :key="topic.slug" :data-topic-tile="topic.slug"
        :data-revealed="revealed.has(topic.slug) ? '' : undefined"
        :data-revealed-by-focus="visitedByFocus.has(topic.slug) ? '' : undefined"
        :style="{ '--topic-delay': (revealed.get(topic.slug) ?? 0) + 'ms' }">
        <a :href="topicHref(topic)" :data-leaving="leaving === topic.slug ? '' : undefined"
          @click="visit($event, topic.slug)" @focus="revealFocused(topic.slug)">
          <div class="topic-card-art"><TopicFlatIllustration :topic="topic.slug" /></div>
          <span class="topic-card-title">{{ topic.title }}</span>
        </a>
      </li>
    </ul>
  </nav>
</template>
