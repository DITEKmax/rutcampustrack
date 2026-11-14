<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { acquireInert } from '../../../mobile-core/src/shared/inert-lease'
import LandingIcon from '../art/LandingIcon.vue'
import { applyPwaTraceEvent, jsonTokens, pwaDemoChannel, type PwaTraceState } from './pwa-trace'
import '../story/story-ide.pcss'
import './pwa-demo.pcss'

const frame = ref<HTMLIFrameElement | null>(null)
const phone = ref<HTMLElement | null>(null)
const section = ref<HTMLElement | null>(null)
const grid = ref<HTMLElement | null>(null)
const fullscreenButton = ref<HTMLButtonElement | null>(null)
const restartButton = ref<HTMLButtonElement | null>(null)
const requestPane = ref<HTMLElement | null>(null)
const responsePane = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
const started = ref(false)
const sectionHeight = ref<number>()
let visibility: IntersectionObserver | undefined
let fullscreenFrame: Window | null = null
let releaseBackground: (() => void) | undefined
let frameReady = false
type DemoRoute = 'today' | 'attendance' | 'homework' | 'map'
let pendingRoute: DemoRoute | undefined
const instance = ref(0)
const trace = ref<PwaTraceState>({ actionId: 0, exchanges: [] })
const phoneSrc = computed(() => `${import.meta.env.BASE_URL}demo-pwa.html?instance=${instance.value}`)
const orderedExchanges = computed(() => trace.value.exchanges
  .map((exchange, index) => ({ exchange, number: index + 1 }))
  .sort((left, right) => Number(isWrite(right.exchange.method)) - Number(isWrite(left.exchange.method))))

function isWrite(method: string) {
  return method !== 'GET' && method !== 'HEAD'
}

function resetScroll() {
  for (const pane of [requestPane.value, responsePane.value]) {
    if (pane) pane.scrollTop = 0
  }
}

function receive(event: MessageEvent) {
  if (
    event.origin !== location.origin ||
    event.source !== frame.value?.contentWindow ||
    event.data?.channel !== pwaDemoChannel ||
    event.data.frameId !== String(instance.value)
  ) return
  if (event.data.type === 'ready') {
    frameReady = true
    sendPendingRoute()
    return
  }
  if (event.data.type !== 'trace') return
  const previous = trace.value
  const detail = event.data.detail
  const next = applyPwaTraceEvent(previous, detail)
  if (next.actionId !== trace.value.actionId) resetScroll()
  trace.value = next
  if (next !== previous && detail.type === 'request' && isWrite(detail.method) &&
      !previous.exchanges.some(exchange => isWrite(exchange.method))) {
    // Check-in refreshes Today before its POST. Bring the write into view once;
    // changes to a response body must never move either editor while it is being read.
    const currentInstance = instance.value
    void nextTick(() => {
      if (instance.value === currentInstance && trace.value.actionId === next.actionId) resetScroll()
    })
  }
}

function start() {
  started.value = true
  visibility?.disconnect()
}

function followDemoAnchor() {
  if (location.hash === '#demo' || location.hash === '#demo-phone') start()
}

onMounted(() => {
  window.addEventListener('message', receive)
  window.addEventListener('hashchange', followDemoAnchor)
  followDemoAnchor()
  if (started.value || !phone.value) return
  if (!('IntersectionObserver' in window)) {
    start()
    return
  }
  visibility = new IntersectionObserver(([entry]) => {
    if (entry?.isIntersecting) start()
  // The mobile phone starts just below the hero; a larger margin loads it on the first screen.
  }, { rootMargin: '0px 0px 64px' })
  visibility.observe(phone.value)
})
onBeforeUnmount(() => {
  visibility?.disconnect()
  window.removeEventListener('message', receive)
  window.removeEventListener('hashchange', followDemoAnchor)
  releaseBackground?.()
  listenToFrameEscape(null)
})

function handleEscape(event: KeyboardEvent) {
  if (!isFullscreen.value || event.key !== 'Escape' || event.defaultPrevented || event.isComposing)
    return
  // Leave Escape to any native modal in the embedded app.
  if (event.view?.document.querySelector(':modal')) return
  event.preventDefault()
  void exitFullscreen()
}

function listenToFrameEscape(target: Window | null) {
  fullscreenFrame?.removeEventListener('keydown', handleEscape)
  fullscreenFrame = target
  fullscreenFrame?.addEventListener('keydown', handleEscape)
}

function onFrameLoad() {
  if (isFullscreen.value) listenToFrameEscape(frame.value?.contentWindow ?? null)
}

function enterFullscreen() {
  if (isFullscreen.value || !grid.value || !section.value) return
  start()
  sectionHeight.value = section.value.offsetHeight
  const { scrollX, scrollY } = window
  const releases: Array<() => void> = []
  // Keep the iframe in place and make every branch outside the demo inert.
  for (let branch: HTMLElement | null = grid.value; branch?.parentElement; branch = branch.parentElement) {
    for (const sibling of branch.parentElement.children) {
      if (sibling !== branch && sibling instanceof HTMLElement) releases.push(acquireInert(sibling))
    }
    if (branch.parentElement === document.body) break
  }
  document.documentElement.classList.add('pwa-demo-scroll-locked')
  window.addEventListener('keydown', handleEscape)
  releaseBackground = () => {
    for (const release of releases) release()
    window.removeEventListener('keydown', handleEscape)
    document.documentElement.classList.remove('pwa-demo-scroll-locked')
    window.scrollTo({ left: scrollX, top: scrollY, behavior: 'instant' })
  }
  isFullscreen.value = true
  onFrameLoad()
  fullscreenButton.value?.focus({ preventScroll: true })
}

async function exitFullscreen() {
  if (!isFullscreen.value) return
  isFullscreen.value = false
  sectionHeight.value = undefined
  listenToFrameEscape(null)
  await nextTick()
  releaseBackground?.()
  releaseBackground = undefined
  fullscreenButton.value?.focus({ preventScroll: true })
}

function toggleFullscreen() {
  if (isFullscreen.value) void exitFullscreen()
  else enterFullscreen()
}

function focusDemoFrame() {
  frame.value?.focus({ preventScroll: true })
}

function restart() {
  frameReady = false
  pendingRoute = undefined
  resetScroll()
  trace.value = { actionId: 0, exchanges: [] }
  instance.value++
  start()
}

function sendPendingRoute() {
  if (!frameReady || !pendingRoute) return
  frame.value?.contentWindow?.postMessage({
    channel: pwaDemoChannel, type: 'navigate', frameId: String(instance.value), route: pendingRoute,
  }, location.origin)
  pendingRoute = undefined
}

async function showPhone(route?: DemoRoute) {
  start()
  pendingRoute = route
  sendPendingRoute()
  phone.value?.scrollIntoView({ block: 'nearest', behavior: 'instant' })
  await nextTick()
  focusDemoFrame()
}

defineExpose({ showPhone })
</script>

<template>
  <section
    id="demo"
    ref="section"
    class="landing-shell landing-section landing-demo"
    aria-label="Интерактивное PWA-демо"
    :style="sectionHeight === undefined ? undefined : { minBlockSize: sectionHeight + 'px' }"
  >
    <div
      ref="grid"
      class="demo-grid pwa-demo-grid"
      :class="{ 'pwa-demo-grid--fullscreen': isFullscreen }"
      :role="isFullscreen ? 'dialog' : undefined"
      :aria-modal="isFullscreen ? true : undefined"
      :aria-label="isFullscreen ? 'Интерактивное PWA-демо' : undefined"
    >
      <div v-if="isFullscreen" class="pwa-demo-focus-guard" tabindex="0" @focus="focusDemoFrame"></div>
      <div class="pwa-demo-trace">
        <div class="pwa-demo-toolbar">
          <button ref="restartButton" class="text-action demo-reset" type="button" @click="restart">
            <LandingIcon name="repeat" /> Начать заново
          </button>
          <button
            ref="fullscreenButton"
            class="text-action demo-reset"
            type="button"
            :aria-label="isFullscreen ? 'Выйти из полноэкранного режима' : undefined"
            @click="toggleFullscreen"
          >
            {{ isFullscreen ? 'Выйти' : 'Полноэкранный режим' }}
          </button>
        </div>
        <div v-show="!isFullscreen" class="rct-story__ide pwa-demo-pane">
          <h3 id="demo-request-title" class="pwa-demo-pane-title">Запрос</h3>
          <div ref="requestPane" class="pwa-demo-editor" role="region" aria-labelledby="demo-request-title" tabindex="0">
            <template v-if="!trace.exchanges.length && trace.local">
              <pre><code><span data-token="keyword">Локальное действие</span></code></pre>
              <p>{{ trace.local.label }}</p>
            </template>
            <p v-else-if="!trace.exchanges.length" data-token="comment">Выполни действие в демо, чтобы увидеть запрос.</p>
            <div v-for="{exchange, number} in orderedExchanges" :key="exchange.id" class="pwa-demo-exchange">
              <pre><code><span data-token="comment">#{{ number }}</span> <span data-token="keyword">{{ exchange.method }}</span> {{ exchange.path }}<span v-if="exchange.cached" data-token="comment"> · ранее получено</span></code></pre>
              <pre v-if="exchange.request"><code><span v-for="(token, part) in jsonTokens(exchange.request)" :key="part" :data-token="token.style">{{ token.text }}</span></code></pre>
              <p v-else data-token="comment">{{ exchange.request === null ? 'Читаем тело запроса…' : 'Без тела запроса' }}</p>
            </div>
          </div>
        </div>
        <div v-show="!isFullscreen" class="rct-story__ide pwa-demo-pane">
          <h3 id="demo-response-title" class="pwa-demo-pane-title">Ответ</h3>
          <div ref="responsePane" class="pwa-demo-editor" role="region" aria-labelledby="demo-response-title" tabindex="0">
            <template v-if="!trace.exchanges.length && trace.local">
              <pre><code><span data-token="keyword">Локальный результат</span></code></pre>
              <p>{{ trace.local.result ?? 'Обрабатывается в интерфейсе…' }}</p>
            </template>
            <p v-else-if="!trace.exchanges.length" data-token="comment">Пока нет ответа.</p>
            <div v-for="{exchange, number} in orderedExchanges" :key="exchange.id" class="pwa-demo-exchange">
              <pre><code><span data-token="comment">#{{ number }}</span> <template v-if="exchange.response?.status != null">HTTP <span data-token="number">{{ exchange.response.status }}</span></template><span v-else data-token="comment">{{ exchange.response ? 'Ответ не получен' : 'Ждём ответ…' }}</span></code></pre>
              <template v-if="exchange.response?.status != null">
                <pre v-if="exchange.response.body"><code><span v-for="(token, part) in jsonTokens(exchange.response.body)" :key="part" :data-token="token.style">{{ token.text }}</span></code></pre>
                <p v-else data-token="comment">{{ exchange.response.body === null ? 'Читаем тело ответа…' : 'Без тела ответа' }}</p>
              </template>
            </div>
          </div>
        </div>
      </div>
      <div class="demo-stage">
        <div class="demo-orbit-bg" aria-hidden="true"></div>
        <div id="demo-phone" ref="phone" class="landing-phone pwa-demo-phone">
          <iframe
            v-if="started"
            :key="instance"
            ref="frame"
            :src="phoneSrc"
            title="Интерактивное PWA-демо RutCampusTrack"
            sandbox="allow-scripts allow-same-origin allow-downloads allow-forms allow-popups"
            allow="geolocation 'none'; camera 'none'; microphone 'none'"
            @load="onFrameLoad"
          ></iframe>
        </div>
      </div>
      <div v-if="isFullscreen" class="pwa-demo-focus-guard" tabindex="0"
        @focus="restartButton?.focus({ preventScroll: true })"></div>
    </div>
  </section>
</template>
