<script setup lang="ts">
import { computed } from 'vue'
import RequestCard from './RequestCard.vue'
import type { RequestAccessState, RequestAttachment, RequestBucket, RequestDetail } from './types'

const props = withDefaults(defineProps<{
  bucket: RequestBucket
  requests: readonly RequestDetail[]
  loading: boolean
  error: string | null
  offline: boolean
  access: RequestAccessState
  retrying?: boolean
  cancellingId?: string | null
}>(), {
  retrying: false,
  cancellingId: null,
})

const emit = defineEmits<{
  selectBucket: [bucket: RequestBucket]
  newRequest: []
  retry: []
  cancel: [id: string]
  openAttachment: [attachment: RequestAttachment]
}>()

const isArchive = computed(() => props.bucket === 'archive')
const accessBlocked = computed(() => props.access === 'forbidden' || (!isArchive.value && props.access === 'no-active-semester'))
const canStartRequest = computed(() => !props.offline && props.access === 'allowed')
const emptyTitle = computed(() => isArchive.value ? 'Архив пуст' : 'Нет заявок на рассмотрении')
const emptyDescription = computed(() => isArchive.value
  ? 'Здесь появятся заявки после принятого решения.'
  : 'Когда ты отправишь заявку, она появится здесь.')
const accessTitle = computed(() => props.access === 'forbidden' ? 'Раздел недоступен' : 'Нет активного семестра')
const accessDescription = computed(() => props.access === 'forbidden'
  ? 'У тебя сейчас нет доступа к заявкам.'
  : 'Подать заявку можно только в активном семестре.')
</script>

<template>
  <main
    class="requests-screen"
    aria-labelledby="requests-title"
  >
    <header class="requests-screen__header">
      <h1 id="requests-title">
        Заявки
      </h1>
    </header>

    <button
      class="requests-primary-action"
      type="button"
      :disabled="!canStartRequest"
      :aria-describedby="!canStartRequest ? 'requests-action-hint' : undefined"
      @click="emit('newRequest')"
    >
      Подать
    </button>
    <p
      v-if="!canStartRequest"
      id="requests-action-hint"
      class="requests-screen__hint"
    >
      {{ offline ? 'Подача заявок снова станет доступна онлайн.' : accessDescription }}
    </p>

    <div
      class="requests-tabs"
      role="tablist"
      aria-label="Список заявок"
    >
      <button
        class="requests-tab"
        :class="{ 'requests-tab--active': !isArchive }"
        type="button"
        role="tab"
        :aria-selected="!isArchive"
        @click="emit('selectBucket', 'open')"
      >
        Открытые
      </button>
      <button
        class="requests-tab"
        :class="{ 'requests-tab--active': isArchive }"
        type="button"
        role="tab"
        :aria-selected="isArchive"
        @click="emit('selectBucket', 'archive')"
      >
        Архив
      </button>
    </div>

    <p
      v-if="offline"
      class="requests-offline"
      role="status"
    >
      Офлайн · показываем сохранённые данные
    </p>

    <section
      v-if="loading"
      class="requests-state requests-state--loading"
      aria-busy="true"
      aria-live="polite"
      aria-label="Загрузка заявок"
    >
      <span
        v-for="item in 2"
        :key="item"
        class="requests-skeleton"
        aria-hidden="true"
      />
      Загружаем заявки…
    </section>

    <section
      v-else-if="error"
      class="requests-state requests-state--error"
      role="alert"
    >
      <h2>Не удалось получить заявки</h2>
      <p>{{ error }}</p>
      <button
        class="requests-secondary-action"
        type="button"
        :disabled="retrying"
        @click="emit('retry')"
      >
        {{ retrying ? 'Повторяем…' : 'Повторить' }}
      </button>
    </section>

    <section
      v-else-if="accessBlocked"
      class="requests-state"
      :aria-label="accessTitle"
    >
      <h2>{{ accessTitle }}</h2>
      <p>{{ accessDescription }}</p>
    </section>

    <section
      v-else-if="requests.length === 0"
      class="requests-state"
      :aria-label="emptyTitle"
    >
      <h2>{{ emptyTitle }}</h2>
      <p>{{ emptyDescription }}</p>
    </section>

    <ul
      v-else
      class="requests-list"
      aria-label="Заявки"
    >
      <li
        v-for="request in requests"
        :key="request.summary.id"
      >
        <RequestCard
          :detail="request"
          :offline="offline"
          :cancelling="cancellingId === request.summary.id"
          @cancel="emit('cancel', $event)"
          @open-attachment="emit('openAttachment', $event)"
        />
      </li>
    </ul>
  </main>
</template>

<style src="./requests.pcss"></style>
