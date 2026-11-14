<script setup lang="ts">
import { computed, ref } from 'vue'
import { edgeKey, listening, nodes, type NodeId, type TopologyEdge } from './production-topology'
import { useVisiblePackets } from './use-visible-packets'

const props = defineProps<{ selected: NodeId; links: TopologyEdge[]; mobile?: boolean }>()
defineEmits<{ close: [] }>()

const panel = ref<HTMLElement | null>(null)
const moving = useVisiblePackets(panel)
const ports = computed(() => [...new Set([
  ...(listening[props.selected] ?? []),
  ...props.links.filter((link) => link.port !== null && link.to === props.selected && link.from !== 'rabbit')
    .map((link) => link.protocol + ' :' + link.port),
])])
const groups = computed(() => [
  { name: 'Входящие', links: props.links.filter((link) => link.to === props.selected) },
  { name: 'Исходящие', links: props.links.filter((link) => link.from === props.selected) },
].filter((group) => group.links.length))
</script>

<template>
  <aside
    ref="panel"
    class="topology__selection"
    :aria-label="'Связи узла ' + nodes[selected].name"
  >
    <div class="topology__selection-heading">
      <h3>{{ nodes[selected].name }}</h3>
      <button type="button" aria-label="Сбросить выбор" @click="$emit('close')">
        <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
          <path d="m4 4 10 10M14 4 4 14" />
        </svg>
      </button>
    </div>
    <p v-if="ports.length" class="topology__ports">Порты узла <code>{{ ports.join(' · ') }}</code></p>
    <div v-for="group in groups" :key="group.name" class="topology__selection-group">
      <h4>{{ group.name }}</h4>
      <ul>
        <li v-for="link in group.links" :key="edgeKey(link)" :data-link="edgeKey(link)">
          <span class="topology__direction" :class="{ 'topology__direction--incoming': link.to === selected }">
            <svg viewBox="0 0 36 12" aria-hidden="true">
              <path d="M2 6H32m-4-4 4 4-4 4" />
              <circle v-if="mobile && moving" r="2.1">
                <animateMotion path="M2 6H30" dur="1.75s" repeatCount="indefinite" />
              </circle>
            </svg>
            {{ nodes[link.from === selected ? link.to : link.from].name }}
          </span>
          <code>{{ link.protocol }}{{ link.port === null ? ' · Unix-сокет' : ' :' + link.port }}</code>
          <p>{{ link.detail }}</p>
        </li>
      </ul>
    </div>
  </aside>
</template>
