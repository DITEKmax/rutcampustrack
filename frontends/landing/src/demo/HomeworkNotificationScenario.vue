<script setup lang="ts">
import { computed, nextTick, ref, shallowRef } from 'vue'
import { decodeNotificationLaunch, encodeNotificationLaunch } from '../../../mobile-core/src/features/notifications/notifications-launch'
import type { NotificationAction, NotificationItem, NotificationReadStateAck } from '../../../mobile-core/src/features/notifications/notifications-v2-models'
import { homework } from './fixtures'
import { initialSnapshot, notificationHomeworkDate, notificationHomeworkId, notificationHomeworkTitle, physicalLessonId } from './model'
import LocalScenarioPanel from './LocalScenarioPanel.vue'

type Channel = 'pwa' | 'telegram'
type Source = Channel | 'inbox'
const pwa = ref(true)
const telegram = ref(true)
const delivery = shallowRef<Record<Channel, boolean> | null>(null)
const readState = shallowRef<NotificationReadStateAck | null>(null)
const openedFrom = ref<Source | null>(null)
const message = ref('')
const firstAction = ref<HTMLElement | null>(null)
const homeworkHeading = ref<HTMLElement | null>(null)
const homeworkItems = homework(new Set(), initialSnapshot).items
const openedHomework = shallowRef<(typeof homeworkItems)[number] | null>(null)
const action = {
  kind: 'homework', role: 'STUDENT', homeworkId: notificationHomeworkId, lessonDate: notificationHomeworkDate,
} as const satisfies NotificationAction
const encoded = encodeNotificationLaunch(action)
const sentAt = '2026-11-16T07:10:00Z'
const publishedItem: NotificationItem = {
  id: '000000000000000000000901',
  type: 'HOMEWORK_PUBLISHED',
  heading: 'Новое домашнее задание',
  summary: notificationHomeworkTitle,
  sentAt,
  orderAt: sentAt,
  readAt: null,
  revision: '1',
  details: [{ label: 'Дата пары', value: notificationHomeworkDate }],
  action,
}
const state = computed(() => ({
  preferences: { pwa: pwa.value, telegram: telegram.value },
  event: delivery.value ? {
    event_type: 'homework.published',
    payload: {
      homework_id: Number(notificationHomeworkId), group_id: 411, subject_id: 9,
      title: notificationHomeworkTitle, lesson_date: notificationHomeworkDate, lesson_number: 2,
      lesson_id: Number(physicalLessonId(notificationHomeworkDate, 2)),
    },
  } : null,
  delivery: delivery.value,
  inbox: delivery.value ? readState.value?.item ?? publishedItem : null,
  unreadCount: delivery.value && !readState.value ? 1 : 0,
  readStateAck: readState.value ? { role: readState.value.role, unreadCount: readState.value.unreadCount } : null,
  openedFrom: openedFrom.value,
  openedHomework: openedHomework.value ? {
    id: openedHomework.value.id,
    lessonDate: openedHomework.value.lessonDate,
    title: openedHomework.value.title,
  } : null,
}))
const changed = computed(() => delivery.value !== null || !pwa.value || !telegram.value)

async function publish() {
  if (delivery.value) return
  delivery.value = { pwa: pwa.value, telegram: telegram.value }
  await nextTick()
  firstAction.value?.querySelector<HTMLButtonElement>('button')?.focus({ preventScroll: true })
}
async function open(source: Source) {
  if (!delivery.value || (source !== 'inbox' && !delivery.value[source])) return
  if (source === 'inbox' && !readState.value) {
    readState.value = {
      role: 'STUDENT',
      item: { ...publishedItem, readAt: '2026-11-16T07:10:01Z', revision: '2' },
      unreadCount: 0,
    }
  }
  const target = decodeNotificationLaunch(encoded)
  const item = target?.kind === 'homework' && target.role === 'STUDENT'
    ? homeworkItems.find(item => item.id === target.homeworkId && item.lessonDate === target.lessonDate)
    : undefined
  message.value = item ? '' : 'Задание сейчас недоступно.'
  if (!item) return
  openedFrom.value = source
  openedHomework.value = item
  await nextTick()
  homeworkHeading.value?.focus()
}
function reset() {
  delivery.value = null
  pwa.value = true
  telegram.value = true
  readState.value = null
  openedFrom.value = null
  openedHomework.value = null
  message.value = ''
}
</script>

<template>
  <LocalScenarioPanel id="demo-homework-notifications" title="Одно задание · два канала"
    :json="state" :changed="changed" @reset="reset">
    <p>Задание 901 от 16 ноября. Выбор внешних каналов применяется при отправке. Запись в ленте создаётся в любом случае.</p>
    <div class="local-scene-controls">
      <label><input v-model="pwa" type="checkbox" :disabled="!!delivery" /> Web Push PWA</label>
      <label><input v-model="telegram" type="checkbox" :disabled="!!delivery" /> Telegram</label>
      <button type="button" :disabled="!!delivery" @click="publish">Опубликовать ДЗ</button>
    </div>
    <div v-if="delivery" ref="firstAction" class="notice-cards">
      <article>
        <h4>Web Push PWA</h4>
        <p>{{ delivery.pwa ? 'Доставлено · ' + notificationHomeworkTitle : 'Канал выключен' }}</p>
        <button v-if="delivery.pwa" type="button" @click="open('pwa')">Открыть из Web Push</button>
      </article>
      <article>
        <h4>Telegram</h4>
        <p>{{ delivery.telegram ? 'Доставлено · ' + notificationHomeworkTitle : 'Канал выключен' }}</p>
        <button v-if="delivery.telegram" type="button" @click="open('telegram')">Показать это задание в PWA</button>
      </article>
      <article>
        <h4>Лента PWA</h4>
        <p role="status">{{ readState ? 'Прочитано' : '1 непрочитанное' }}</p>
        <button type="button" @click="open('inbox')">Открыть из ленты</button>
      </article>
      <article v-if="openedHomework" aria-labelledby="demo-notification-homework-title">
        <p>Домашнее задание · {{ openedHomework.subject.name }} · 16 ноября</p>
        <h4 id="demo-notification-homework-title" ref="homeworkHeading" tabindex="-1">{{ openedHomework.title }}</h4>
        <p>{{ openedHomework.description }}</p>
      </article>
    </div>
    <p v-if="message" role="status">{{ message }}</p>
  </LocalScenarioPanel>
</template>

<style scoped>
.notice-cards { display: grid; gap: .45rem; }
.notice-cards article { display: grid; justify-items: start; gap: .3rem; padding: .5rem .65rem; border: 1px solid var(--story-line); border-radius: .45rem; }
.notice-cards h4 { margin: 0; color: var(--story-text); }
</style>
