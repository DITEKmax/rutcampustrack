<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import './landing-metrics.pcss'

const measured = [
  { value: 900, prefix: '≈ ', title: 'Уведомлений о занятиях', detail: 'В истории приложения' },
  { value: 150, prefix: '≈ ', unit: 'мс', title: 'Ответ сервера для экрана «Сегодня»', detail: 'p95 успешных ответов' },
  { value: 8000, suffix: '+', title: 'HTTPS-проверок публичного сайта' },
] as const

const capabilities = [
  { value: 3, label: 'Интерфейса', detail: 'Web, PWA, Telegram Mini App' },
  { value: 2, label: 'Канала уведомлений', detail: 'Telegram и Web Push' },
  { value: 4, label: 'Роли' },
  { value: 5, label: 'Форматов недельного отчёта старосты' },
  { value: 11, label: 'Сценариев экспорта' },
] as const

const section = ref<HTMLElement | null>(null)
const duration = 1100
const stagger = 140
const totals: number[] = [...measured, ...capabilities].map(metric => metric.value)
const displayed = ref([...totals])
const format = (value: number) => String(value).replace(/\B(?=(\d{3})+(?!\d))/g, '\u00a0')
const pending = new Map<HTMLElement, { index: number; start: number }>()
let observer: IntersectionObserver | undefined
let motion: MediaQueryList | undefined
let frame = 0

function advance(now: number) {
  frame = 0
  for (const [card, { index, start }] of pending) {
    if (now < start) continue
    if (!card.hasAttribute('data-visible')) card.dataset.visible = ''
    const progress = Math.min(1, (now - start) / duration)
    displayed.value[index] = Math.round(totals[index]! * (1 - (1 - progress) ** 3))
    if (progress === 1) pending.delete(card)
  }
  if (pending.size) frame = requestAnimationFrame(advance)
}

function settle() {
  observer?.disconnect()
  cancelAnimationFrame(frame)
  frame = 0
  pending.clear()
  displayed.value = [...totals]
  section.value?.removeAttribute('data-metrics-motion')
}

function onMotionChange(event: MediaQueryListEvent) {
  if (event.matches) settle()
}

onMounted(() => {
  const root = section.value
  if (!root || !('IntersectionObserver' in window)) return
  motion = matchMedia('(prefers-reduced-motion: reduce)')
  if (motion.matches) return
  motion.addEventListener('change', onMotionChange)

  const cards = [...root.querySelectorAll<HTMLElement>('[data-metric-card]')]
  displayed.value = totals.map(() => 0)
  root.dataset.metricsMotion = ''
  observer = new IntersectionObserver(entries => {
    if (!root.hasAttribute('data-metrics-motion')) return
    const now = performance.now()
    const visible = entries.filter(entry => entry.isIntersecting && entry.intersectionRatio >= .2)
      .sort((a, b) => cards.indexOf(a.target as HTMLElement) - cards.indexOf(b.target as HTMLElement))
    visible.forEach(({ target }, order) => {
      const card = target as HTMLElement
      pending.set(card, { index: cards.indexOf(card), start: now + order * stagger })
      observer?.unobserve(card)
    })
    if (pending.size && !frame) frame = requestAnimationFrame(advance)
  }, { threshold: .2 })
  cards.forEach(card => observer?.observe(card))
})

onBeforeUnmount(() => {
  settle()
  motion?.removeEventListener('change', onMotionChange)
})
</script>

<template>
  <section id="metrics" ref="section" class="landing-metrics" aria-labelledby="metrics-title">
    <div class="landing-shell">
      <header class="landing-metrics__header" data-landing-reveal>
        <h2 id="metrics-title">RutCampusTrack в цифрах</h2>
        <p>По выборке рабочих данных</p>
      </header>

      <dl class="landing-metrics__measured">
        <div v-for="(item, index) in measured" :key="item.title" data-metric-card>
          <dt>{{ item.title }}</dt>
          <dd class="landing-metrics__value">
            <span class="landing-metrics__sr">{{ 'prefix' in item ? item.prefix : '' }}{{ format(item.value) }}{{ 'suffix' in item ? item.suffix : '' }}{{ 'unit' in item ? '\u00a0' + item.unit : '' }}</span>
            <span aria-hidden="true">{{ 'prefix' in item ? item.prefix : '' }}{{ format(displayed[index]!) }}{{ 'suffix' in item ? item.suffix : '' }}<span v-if="'unit' in item" class="landing-metrics__unit">&nbsp;{{ item.unit }}</span></span>
          </dd>
          <dd v-if="'detail' in item" class="landing-metrics__detail">{{ item.detail }}</dd>
        </div>
      </dl>

      <div class="landing-metrics__product">
        <h3>Возможности продукта</h3>
        <dl>
          <div v-for="(item, index) in capabilities" :key="item.label" data-metric-card>
            <dt>{{ item.label }}</dt>
            <dd class="landing-metrics__count">
              <span class="landing-metrics__sr">{{ item.value }}</span>
              <span aria-hidden="true">{{ displayed[measured.length + index] }}</span>
            </dd>
            <dd v-if="'detail' in item" class="landing-metrics__detail">{{ item.detail }}</dd>
          </div>
        </dl>
      </div>
    </div>
  </section>
</template>
