<script setup lang="ts">
import { computed, nextTick, ref, shallowRef } from 'vue'
import type { StudentCheckinAck, StudentCheckinCommand } from '../../../mobile-core/src/api/types'
import { lessonId } from './model'
import LocalScenarioPanel from './LocalScenarioPanel.vue'

const key = '33000000-0000-4000-8000-000000000716'
const eventId = '44000000-0000-4000-8000-000000000716'
const command: StudentCheckinCommand = { geo: { kind: 'UNAVAILABLE', reason: 'POSITION_UNAVAILABLE' } }
const originalAck: StudentCheckinAck = {
  outcome: 'PENDING_CONFIRMATION',
  lessonId,
  attendance: null,
  request: {
    id: '000000000000000000000716',
    status: 'PENDING',
    origin: 'AUTO_GEO_FAILURE',
    resolutionReason: null,
  },
  retryAt: '2026-11-09T07:17:00Z',
  serverNow: '2026-11-09T07:12:00Z',
  _links: {},
}
const controls = ref<HTMLElement | null>(null)
const paused = ref(false)
const receipt = shallowRef<{ key: string; ack: StudentCheckinAck } | null>(null)
const httpResult = shallowRef<StudentCheckinAck | null>(null)
const sent = ref(false)
const appliedIds = shallowRef<ReadonlySet<string>>(new Set())
const skipped = ref(0)
const state = computed(() => ({
  dispatch: paused.value ? 'paused' : 'running',
  idempotencyKey: key,
  command,
  receipt: receipt.value,
  http: !receipt.value ? null : httpResult.value ? { status: 200, body: httpResult.value } : { lostResponse: true },
  requestCount: receipt.value ? 1 : 0,
  outbox: receipt.value ? [{ event_id: eventId, event_type: 'late_checkin.requested', status: sent.value ? 'SENT' : 'PENDING' }] : [],
  consumer: { applied: [...appliedIds.value], skippedDuplicates: skipped.value },
}))
const changed = computed(() => paused.value || receipt.value !== null)

async function step(run: () => void) {
  run()
  await nextTick()
  controls.value?.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus({ preventScroll: true })
}
function submit(loseResponse: boolean) {
  if (!receipt.value) receipt.value = { key, ack: originalAck }
  if (!loseResponse && receipt.value.key === key) httpResult.value = receipt.value.ack
}
function consume(id: string) {
  if (appliedIds.value.has(id)) {
    skipped.value++
    return
  }
  appliedIds.value = new Set([...appliedIds.value, id])
}
function resume() {
  if (!receipt.value) return
  paused.value = false
  sent.value = true
  consume(eventId)
}
function reset() {
  paused.value = false
  receipt.value = null
  httpResult.value = null
  sent.value = false
  appliedIds.value = new Set()
  skipped.value = 0
}
</script>

<template>
  <LocalScenarioPanel id="demo-outbox-recovery" title="Сбой и восстановление"
    :json="state" :changed="changed" @reset="reset">
    <p>Attendance сохраняет заявку и квитанцию вместе с событием. Проследи одну запись late_checkin.requested.</p>
    <div ref="controls" class="local-scene-controls">
      <button type="button" :disabled="changed" @click="step(() => { paused = true })">Остановить доставку</button>
      <button type="button" :disabled="!paused || !!receipt" @click="step(() => submit(true))">Сохранить заявку и потерять ответ</button>
      <button type="button" :disabled="!receipt || !!httpResult" @click="step(() => submit(false))">Повторить тот же ключ</button>
      <button type="button" :disabled="!paused || !httpResult" @click="step(resume)">Возобновить доставку</button>
      <button type="button" :disabled="!sent" @click="consume(eventId)">Повторить доставку события</button>
    </div>
    <dl class="local-scene-facts" aria-live="polite">
      <div><dt>Запрос</dt><dd>{{ httpResult ? '200 · прежний ответ' : receipt ? 'Ответ потерян' : 'Ожидает отправки' }}</dd></div>
      <div><dt>Outbox</dt><dd>{{ sent ? 'SENT' : receipt ? 'PENDING' : 'Пусто' }}</dd></div>
      <div><dt>Заявки</dt><dd>{{ state.requestCount }}</dd></div>
      <div><dt>Применено потребителем</dt><dd>{{ appliedIds.size }} · пропущено повторов {{ skipped }}</dd></div>
    </dl>
  </LocalScenarioPanel>
</template>
