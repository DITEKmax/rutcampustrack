<script setup lang="ts">
import { computed, ref } from 'vue'
import LandingIcon from '../art/LandingIcon.vue'
import './architecture-request-flow.pcss'

const nodes = {
  browser: {
    label: 'Браузер',
    detail: 'Приложение отправляет POST по HTTPS. Запрос содержит токен доступа, ключ идемпотентности и данные геопозиции либо причину её отсутствия.',
  },
  nginx: {
    label: 'Nginx',
    detail: 'Принимает HTTPS и проксирует запросы к /api/ в Gateway по внутреннему HTTP.',
  },
  gateway: {
    label: 'Gateway',
    detail: 'Запрашивает допуск в Auth. После проверки ответа сам добавляет внутренний токен и передаёт исходный запрос в Mobile BFF.',
  },
  bff: {
    label: 'Mobile BFF',
    detail: 'Преобразует тело HTTP-запроса в команду геоотметки и передаёт её вместе с ключом идемпотентности в Attendance по gRPC.',
  },
  attendance: {
    label: 'Attendance',
    detail: 'Применяет правила посещаемости, проверяет геопозицию относительно кампуса и возвращает результат геоотметки в Mobile BFF.',
  },
  auth: {
    label: 'Auth',
    detail: 'Auth проверяет сеанс и активную роль. При допуске возвращает Gateway внутренний токен.',
  },
} as const

type Node = keyof typeof nodes

const route = [
  { id: 'browser', incoming: null },
  { id: 'nginx', incoming: { from: 'browser', protocol: 'HTTPS' } },
  { id: 'gateway', incoming: { from: 'nginx', protocol: 'HTTP' } },
  { id: 'bff', incoming: { from: 'gateway', protocol: 'HTTP' } },
  { id: 'attendance', incoming: { from: 'bff', protocol: 'gRPC' } },
] as const

const selected = ref<Node>('gateway')
const detail = computed(() => nodes[selected.value])
const authActive = computed(() => selected.value === 'gateway' || selected.value === 'auth')
</script>

<template>
  <section class="request-flow" aria-labelledby="request-flow-heading" data-architecture-request-flow>
    <header class="request-flow__head">
      <h3 id="request-flow-heading">Маршрут геоотметки</h3>
      <div class="request-flow__endpoint">
        <span>POST</span>
        <code>/api/v1/student/lessons/{lessonId}/checkin</code>
      </div>
    </header>
    <ol class="request-flow__route" role="list" aria-label="Путь запроса">
      <li
        v-for="step in route"
        :key="step.id"
        class="request-flow__step"
        :class="{ 'request-flow__step--branch': step.id === 'gateway' }"
      >
        <div
          v-if="step.incoming"
          class="request-flow__edge"
          :class="{ 'request-flow__edge--active': selected === step.id || selected === step.incoming.from }"
        >
          <span>{{ step.incoming.protocol }}</span><LandingIcon name="arrow" />
        </div>
        <button
          type="button"
          class="request-flow__node"
          :class="{ 'request-flow__node--linked': step.id === 'gateway' && selected === 'auth' }"
          :data-flow-node="step.id"
          :aria-pressed="selected === step.id"
          aria-controls="request-flow-detail"
          @click="selected = step.id"
        >
          {{ nodes[step.id].label }}
        </button>
        <div v-if="step.id === 'gateway'" class="request-flow__branch">
          <div
            class="request-flow__edge request-flow__edge--auth"
            :class="{ 'request-flow__edge--active': authActive }"
          >
            <span>HTTP</span>
          </div>
          <button
            type="button"
            class="request-flow__node request-flow__node--auth"
            :class="{ 'request-flow__node--linked': selected === 'gateway' }"
            data-flow-node="auth"
            :aria-pressed="selected === 'auth'"
            aria-controls="request-flow-detail"
            @click="selected = 'auth'"
          >
            <span>{{ nodes.auth.label }}</span>
            <span class="request-flow__branch-label">Допуск</span>
          </button>
        </div>
      </li>
    </ol>
    <div id="request-flow-detail" class="request-flow__detail" role="status" aria-atomic="true" data-flow-detail>
      <strong>{{ detail.label }}</strong>
      <p>
        <template v-if="selected === 'auth'">Отдельный HTTP-запрос от Gateway: <code>POST /internal/auth/admit</code>. </template>
        {{ detail.detail }}
      </p>
    </div>
  </section>
</template>
