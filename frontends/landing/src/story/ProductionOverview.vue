<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import {
  canvas, edges, levels, nodes, type NodeId,
} from './production-topology'
import { mobileRoutes, overviewJunctions, overviewRoutes, telemetryRefs, telemetryTargets } from './production-topology-layout'
import TopologySelection from './TopologySelection.vue'
import { useVisiblePackets } from './use-visible-packets'
import './production-topology.pcss'

const graph = ref<HTMLElement | null>(null)
const diagram = ref<SVGSVGElement | null>(null)
const moving = useVisiblePackets(diagram)
const selected = ref<NodeId | null>(null)

const incident = computed(() => edges.filter((link) =>
  link.from === selected.value || link.to === selected.value))
const neighbors = computed(() => new Set(incident.value.map((link) =>
  link.from === selected.value ? link.to : link.from)))
const viewWidth = canvas.width + canvas.labelGutter
const active = computed(() => selected.value
  ? overviewRoutes.filter(({ link }) => incident.value.includes(link))
  : overviewRoutes)
const layers = computed(() => [
  { muted: true, routes: selected.value ? overviewRoutes.filter((route) => !active.value.includes(route)) : [] },
  { muted: false, routes: active.value },
])
const activeKeys = computed(() => new Set(active.value.map(({ key }) => key)))
function junctionActive(links: Set<string>) {
  return [...links].filter((key) => activeKeys.value.has(key)).length > 1
}
const packets = computed(() => selected.value ? active.value : active.value.filter(({ link }) => link.pulse))

function position(item: { x: number; y: number; width: number; height: number }) {
  return {
    '--node-x': 100 * (item.x + canvas.labelGutter) / viewWidth + '%',
    '--node-y': 100 * item.y / canvas.height + '%',
    '--node-w': 100 * item.width / viewWidth + '%',
    '--node-h': 100 * item.height / canvas.height + '%',
  }
}

function nodeButton(id: NodeId) {
  return graph.value?.querySelector<HTMLButtonElement>('[data-node="' + id + '"]')
}

async function selectAt(id: NodeId, next: NodeId | null) {
  const node = nodeButton(id)
  const rect = node?.getBoundingClientRect()
  const header = document.querySelector('.landing-header')
  const topInset = header?.getBoundingClientRect().bottom ?? 0
  const desktop = diagram.value && getComputedStyle(diagram.value).display !== 'none'
  selected.value = next
  await nextTick()
  if (desktop && rect && node && rect.bottom > topInset && rect.top < window.innerHeight) {
    const offset = node.getBoundingClientRect().top - rect.top
    if (Math.abs(offset) > 1) window.scrollBy({ top: offset, behavior: 'instant' })
  }
}

function toggle(id: NodeId) {
  void selectAt(id, selected.value === id ? null : id)
}

async function clear() {
  const previous = selected.value
  if (!previous) return
  await selectAt(previous, null)
  nodeButton(previous)?.focus()
}
</script>

<template>
  <section id="inside" class="rct-story__section topology" aria-labelledby="topology-title">
    <header class="rct-story__wrap rct-story__heading" data-landing-reveal>
      <h2 id="topology-title">Как связаны сервисы</h2>
      <p>Путь к Mobile BFF идёт через Gateway. Для применимых защищённых маршрутов Gateway отдельно проверяет допуск в Auth. Выбери узел, чтобы увидеть его связи и порты.</p>
    </header>

    <div ref="graph" class="topology__layout" :class="{ 'topology__layout--selected': selected }" @keydown.esc.stop="clear">
      <p class="rct-story__sr" role="status">
        {{ selected ? 'Выбран ' + nodes[selected].name + '. Связей: ' + incident.length : '' }}
      </p>
      <div class="topology__graph" :class="{ 'topology__graph--selected': selected }">
        <svg
          ref="diagram" class="topology__diagram"
          :viewBox="'-' + canvas.labelGutter + ' 0 ' + viewWidth + ' ' + canvas.height"
          aria-hidden="true"
        >
          <defs>
            <marker id="topology-arrow" viewBox="0 0 8 8" markerWidth="7" markerHeight="7" refX="7" refY="4" orient="auto">
              <path d="M1 1L7 4L1 7" />
            </marker>
            <radialGradient id="topology-packet-halo">
              <stop stop-color="#edc6ff" stop-opacity=".8" />
              <stop offset="1" stop-color="#be89ff" stop-opacity="0" />
            </radialGradient>
            <mask
              v-for="route in overviewRoutes.filter(({ gaps }) => gaps.length)" :id="'topology-gap-' + route.key"
              :key="route.key" maskUnits="userSpaceOnUse" x="-40" y="0" :width="canvas.width + 80" :height="canvas.height"
            >
              <rect x="-40" y="0" :width="canvas.width + 80" :height="canvas.height" fill="white" />
              <circle v-for="[x, y] in route.gaps" :key="x + ':' + y" :cx="x" :cy="y" r="4.4" fill="black" />
            </mask>
          </defs>
          <g v-for="layer in layers" :key="String(layer.muted)" class="topology__edges" :class="{ 'topology__edges--muted': layer.muted }">
            <path
              v-for="route in layer.routes" :key="route.key" :data-link="route.key"
              :d="route.path"
              :class="{
                'topology__edge--rpc': route.link.protocol.startsWith('gRPC'),
                'topology__edge--telemetry': route.link.telemetry,
                'topology__edge--signal-bus': route.group === 'metrics' || route.group === 'traces',
              }"
              :mask="route.gaps.length ? 'url(#topology-gap-' + route.key + ')' : undefined"
              :marker-end="layer.muted ? undefined : 'url(#topology-arrow)'"
            />
          </g>
          <g class="topology__junctions">
            <circle
              v-for="{ point: [x, y], group, links } in overviewJunctions" :key="group + ':' + x + ':' + y"
              :cx="x" :cy="y" r="2.5"
              :data-inactive="selected && !junctionActive(links) ? '' : undefined"
            />
          </g>
          <g v-if="moving" class="topology__packets">
            <g v-for="({ path, key }, index) in packets" :key="key" :data-link="key">
              <circle r="10" fill="url(#topology-packet-halo)" />
              <circle r="2.5" fill="#fff2ff" />
              <animateMotion :path="path" :dur="(3.3 + index % 4 * .48) + 's'" :begin="-index * .53 + 's'" repeatCount="indefinite" />
            </g>
          </g>
          <g class="topology__signal-labels">
            <text x="29" y="1468">Опрос метрик</text>
            <text x="29" y="1640">Экспорт трасс</text>
            <text x="-130" y="1543">Те же сервисы</text>
          </g>
        </svg>
        <section v-for="level in levels" :key="level.kind" class="topology__level" :data-level="level.kind">
          <h3 :style="{ '--level-y': 100 * level.y / canvas.height + '%' }">{{ level.title }}</h3>
          <div v-for="row in level.rows" :key="row[0]" class="topology__row" :data-leading="row[0]">
            <button
              v-for="id in row" :key="id" type="button"
              class="topology__node" :data-node="id" :data-tone="nodes[id].tone"
              :class="{ 'topology__node--neighbor': neighbors.has(id) }"
              :aria-pressed="selected === id" :style="position(nodes[id])" @click="toggle(id)"
            >
              {{ nodes[id].name }}
            </button>
            <div class="topology__mobile-routes">
              <p v-for="id in row.filter((node) => mobileRoutes[node].length)" :key="id">
                <strong>{{ nodes[id].name }}</strong> <span class="topology__route-arrow" aria-hidden="true">→</span><span class="rct-story__sr"> к </span>
                <template v-for="({ to }, index) in mobileRoutes[id]" :key="to">
                  <span v-if="index" aria-hidden="true"> · </span>
                  <span class="topology__mobile-target" :data-related="selected === id || selected === to ? '' : undefined">{{ nodes[to].name }}</span>
                </template>
              </p>
            </div>
            <TopologySelection
              v-if="selected && row.includes(selected)"
              class="topology__mobile-selection" :selected="selected" :links="incident"
              mobile @close="clear"
            />
          </div>
          <div v-if="level.kind === 'telemetry'" class="topology__mobile-routes topology__mobile-signals">
            <p><strong>Опрос метрик</strong>
              <span>Prometheus →
                <template v-for="(id, index) in telemetryTargets" :key="id">
                  <span v-if="index" aria-hidden="true"> · </span>
                  <span class="topology__mobile-target" :data-related="selected === id || selected === 'prometheus' ? '' : undefined">{{ nodes[id].name }}</span>
                </template>
              </span>
            </p>
            <p><strong>Экспорт трасс</strong>
              <span>
                <template v-for="(id, index) in telemetryTargets" :key="id">
                  <span v-if="index" aria-hidden="true"> · </span>
                  <span class="topology__mobile-target" :data-related="selected === id || selected === 'tempo' ? '' : undefined">{{ nodes[id].name }}</span>
                </template>
                → Tempo
              </span>
            </p>
          </div>
        </section>
        <div class="topology__telemetry-refs" aria-hidden="true">
          <span
            v-for="target in telemetryRefs" :key="target.id" :style="position(target)" :data-telemetry-ref="target.id"
            :data-related="selected === target.id || neighbors.has(target.id) ? '' : undefined"
          >{{ nodes[target.id].name }}</span>
        </div>
      </div>
      <div v-if="selected" :key="selected" class="topology__desktop-selection">
        <TopologySelection :selected="selected" :links="incident" @close="clear" />
      </div>
    </div>
  </section>
</template>
