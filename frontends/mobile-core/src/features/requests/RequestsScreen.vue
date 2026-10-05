<script setup lang="ts">
import { computed } from 'vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import RequestCard from './RequestCard.vue'
import type { RequestAccessState, RequestAttachment, RequestAttachmentViewState, RequestBucket, RequestDetail } from './types'

type NotificationRequestView =
  | { status: 'loading' }
  | { status: 'available'; detail: RequestDetail }
  | { status: 'unavailable'; message: string }
  | { status: 'error'; message: string; title?: string }

const props = withDefaults(defineProps<{
  bucket: RequestBucket
  requests: readonly RequestDetail[]
  loading: boolean
  error: string | null
  offline: boolean
  readOnly?: boolean
  access: RequestAccessState
  retrying?: boolean
  cancellingId?: string | null
  hasNextPage?: boolean
  loadingMore?: boolean
  attachmentStates?: Readonly<Record<string, RequestAttachmentViewState | undefined>> | undefined
  notificationTarget?: NotificationRequestView | null
}>(), {
  retrying: false,
  cancellingId: null,
  readOnly: false,
  hasNextPage: false,
  loadingMore: false,
  attachmentStates: undefined,
  notificationTarget: null,
})

const emit = defineEmits<{
  selectBucket: [bucket: RequestBucket]
  newRequest: []
  retry: []
  cancel: [id: string]
  openAttachment: [value: { requestId: string; attachment: RequestAttachment }]
  loadMore: []
  backTarget: []
  retryTarget: []
}>()

const isArchive = computed(() => props.bucket === 'archive')
const isNotificationTarget = computed(() => props.notificationTarget !== null)
const accessBlocked = computed(() => props.access === 'forbidden' || (!isArchive.value && props.access === 'no-active-semester'))
const canStartRequest = computed(() => !props.offline && !props.readOnly && props.access === 'allowed')
const emptyTitle = computed(() => isArchive.value ? 'Архив пуст' : 'Нет заявок на рассмотрении')
const emptyDescription = computed(() => isArchive.value
  ? 'Здесь появятся заявки после принятого решения.'
  : 'Когда ты отправишь заявку, она появится здесь.')
const accessTitle = computed(() => props.access === 'forbidden' ? 'Раздел недоступен' : 'Нет активного семестра')
const accessDescription = computed(() => props.access === 'forbidden'
  ? 'У тебя сейчас нет доступа к заявкам.'
  : 'Подать заявку можно только в активном семестре.')
const actionHint = computed(() => props.readOnly
  ? 'Подача заявок отключена в режиме только чтения.'
  : props.offline
    ? 'Подача заявок снова станет доступна онлайн.'
    : accessDescription.value)
function onTabKeydown(event: KeyboardEvent): void {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const next = event.key === 'Home' ? 'open' : event.key === 'End' ? 'archive' : props.bucket === 'open' ? 'archive' : 'open'
  emit('selectBucket', next)
  const tabs = (event.currentTarget as HTMLElement).querySelectorAll<HTMLButtonElement>('[role="tab"]')
  tabs[next === 'open' ? 0 : 1]?.focus()
}
</script>

<template>
  <main
    class="requests-screen"
    aria-labelledby="requests-title"
  >
    <header class="requests-screen__header">
      <button
        v-if="isNotificationTarget"
        class="requests-secondary-action"
        type="button"
        @click="emit('backTarget')"
      >
        Назад к заявкам
      </button>
      <h1 id="requests-title">
        {{ isNotificationTarget ? 'Заявка' : 'Заявки' }}
      </h1>
    </header>

    <template v-if="notificationTarget">
      <section
        v-if="notificationTarget.status === 'loading'"
        class="requests-state requests-state--loading"
        aria-busy="true"
        aria-live="polite"
        aria-label="Загрузка заявки"
      >
        Загружаем заявку…
      </section>
      <StudentWarningBlock
        v-else-if="notificationTarget.status === 'unavailable'"
        severity="error"
        title="Заявка недоступна"
        :message="notificationTarget.message"
      />
      <StudentWarningBlock
        v-else-if="notificationTarget.status === 'error'"
        severity="error"
        :title="notificationTarget.title ?? 'Не удалось загрузить заявку'"
        :message="notificationTarget.message"
        action-label="Повторить"
        :action-disabled="retrying"
        @action="emit('retryTarget')"
      />

      <RequestCard
        v-else-if="notificationTarget.status === 'available'"
        :detail="notificationTarget.detail"
        :offline="offline"
        :cancelling="cancellingId === notificationTarget.detail.summary.id"
        :attachment-states="attachmentStates"
        focus-target
        @cancel="emit('cancel', $event)"
        @open-attachment="emit('openAttachment', $event)"
      />
    </template>

    <template v-else>
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
        {{ actionHint }}
      </p>

      <div
        class="requests-tabs"
        role="tablist"
        aria-label="Список заявок"
        @keydown="onTabKeydown"
      >
        <button
          class="requests-tab"
          :class="{ 'requests-tab--active': !isArchive }"
          type="button"
          role="tab"
          :aria-selected="!isArchive"
          :tabindex="!isArchive ? 0 : -1"
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
          :tabindex="isArchive ? 0 : -1"
          @click="emit('selectBucket', 'archive')"
        >
          Архив
        </button>
      </div>

      <StudentWarningBlock
        v-if="offline || readOnly"
        :title="offline ? 'Нет подключения' : 'Только чтение'"
        :message="offline ? 'Офлайн · показываем сохранённые данные. Подача заявок снова станет доступна онлайн.' : 'Подача заявок отключена в режиме только чтения.'"
      />

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

      <StudentWarningBlock
        v-else-if="error"
        severity="error"
        title="Не удалось получить заявки"
        :message="error"
        :action-label="retrying ? 'Повторяем…' : 'Повторить'"
        :action-disabled="retrying"
        @action="emit('retry')"
      />

      <StudentWarningBlock
        v-else-if="accessBlocked"
        severity="error"
        :title="accessTitle"
        :message="accessDescription"
      />

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
            :attachment-states="attachmentStates"
            @cancel="emit('cancel', $event)"
            @open-attachment="emit('openAttachment', $event)"
          />
        </li>
      </ul>
      <button
        v-if="!loading && !error && requests.length > 0 && hasNextPage"
        class="requests-secondary-action"
        type="button"
        :disabled="loadingMore"
        @click="emit('loadMore')"
      >
        {{ loadingMore ? 'Загружаем…' : 'Показать ещё' }}
      </button>
    </template>
  </main>
</template>

<style src="./requests.pcss"></style>
