<script setup lang="ts">
import { ref } from 'vue'
import LandingIcon from '../art/LandingIcon.vue'
import type { TopicFlow } from './topic-flows'
import './architecture-request-flow.pcss'

const props = defineProps<{ id: string; flow: TopicFlow }>()
const selection = ref(props.flow.stages[0].nodes[0])
</script>

<template>
  <section class="request-flow request-flow--topic" :aria-labelledby="id + '-flow-title'">
    <header class="request-flow__head">
      <h3 :id="id + '-flow-title'">{{ flow.title }}</h3>
      <div class="request-flow__endpoint">{{ flow.context }}</div>
    </header>
    <ol class="request-flow__route" role="list" aria-label="Этапы сценария">
      <li v-for="(stage, index) in flow.stages" :key="index" class="request-flow__step">
        <div v-if="stage.incoming" class="request-flow__edge"
          :class="{ 'request-flow__edge--active': stage.nodes.some(node => node.id === selection.id) }">
          <span>{{ stage.incoming }}</span><LandingIcon name="arrow" />
        </div>
        <div class="request-flow__choices" :class="{ 'request-flow__choices--branch': stage.nodes.length > 1 }">
          <button v-for="node in stage.nodes" :key="node.id" type="button"
            class="request-flow__node" :aria-pressed="selection.id === node.id"
            :aria-controls="id + '-flow-detail'" @click="selection = node">
            {{ node.label }}
          </button>
        </div>
      </li>
    </ol>
    <div :id="id + '-flow-detail'" class="request-flow__detail" role="status" aria-atomic="true">
      <strong>{{ selection.label }}</strong>
      <p>{{ selection.detail }}</p>
    </div>
  </section>
</template>
