<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { HeadmanJournalApi, HeadmanJournalExcuseType } from '../headman-journal/headman-journal-client'
import type { HeadmanRequestsApi, HeadmanRequestDetail } from '../headman-requests/headman-requests-client'
import type { HeadmanStatsApi } from './headman-stats-client'
import type { HeadmanStatsLesson } from './headman-stats-history-client'
import { useHeadmanStatsHistory } from './use-headman-stats-history'
import RequestAttachmentPreview from '../requests/RequestAttachmentPreview.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import './headman-stats-history.pcss'

const props = withDefaults(defineProps<{ subjectId?: number | null; lessonTypes?: readonly string[]; api: HeadmanStatsApi | null; journalApi: HeadmanJournalApi | null; requestsApi: HeadmanRequestsApi | null; groupId: number | null; studentId: number; semesterId: number | null; offline: boolean; allowed: boolean; markAllowed: boolean; excuseAllowed: boolean }>(), { subjectId: null, lessonTypes: () => [] })
const emit = defineEmits<{ refreshed: [] }>()
const controller = useHeadmanStatsHistory(() => ({ api: props.api, journalApi: props.journalApi, groupId: props.groupId, studentId: props.studentId, semesterId: props.semesterId, subjectId: props.subjectId, lessonTypes: props.lessonTypes, offline: props.offline, allowed: props.allowed, markAllowed: props.markAllowed, excuseAllowed: props.excuseAllowed }), () => emit('refreshed'))
const { data, loading, error, feedback, pending, confirmation, excuse, writable, denied } = controller
const dialog = ref<HTMLDialogElement | null>(null)
const returnFocus = ref<HTMLElement | null>(null)
const excuseType = ref<HeadmanJournalExcuseType>('OTHER'); const comment = ref(''); const file = ref<File | null>(null)
const ticket = ref<HeadmanRequestDetail | null>(null); const ticketError = ref<string | null>(null); const ticketBusy = ref(false)
const ticketId = ref<string | null>(null)
let ticketRevision = 0
const intent = computed(() => confirmation.value ?? excuse.value)
const grouped = computed(() => {
  const groups = new Map<string, HeadmanStatsLesson[]>()
  for (const lesson of data.value?.items ?? []) groups.set(lesson.lessonDate, [...groups.get(lesson.lessonDate) ?? [], lesson])
  return [...groups].map(([date, lessons]) => ({ date, lessons }))
})
const labels = { PRESENT: '+', EXCUSED: 'у', ABSENT: 'н' }
const words = { PRESENT: 'Присутствовал', EXCUSED: 'Уважительная причина', ABSENT: 'Отсутствовал' }
const kinds = ['PRESENT', 'EXCUSED', 'ABSENT'] as const
const reasons: readonly { value: HeadmanJournalExcuseType; label: string }[] = [{ value: 'ILLNESS', label: 'Болезнь' }, { value: 'MEDICAL_EXAMINATION', label: 'Медицинское обследование' }, { value: 'COMPETITION_PARTICIPATION', label: 'Соревнования' }, { value: 'FAMILY_CIRCUMSTANCES', label: 'Семейные обстоятельства' }, { value: 'SUMMONS', label: 'Повестка' }, { value: 'UNIVERSITY_ORDER', label: 'Приказ университета' }, { value: 'EXEMPTION', label: 'Освобождение' }, { value: 'OTHER', label: 'Другая причина' }]
function dateText(value: string): string { return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(value + 'T12:00:00Z')) }
function timeText(value: string): string { return new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Moscow' }).format(new Date(value)) }
function typeText(value: string): string { return { LECTURE: 'Лекция', PRACTICE: 'Практика', LAB: 'Лабораторная' }[value] ?? value }
function choose(lesson: HeadmanStatsLesson, status: typeof kinds[number], event: MouseEvent): void { returnFocus.value = event.currentTarget as HTMLElement; controller.choose(lesson, status) }
watch(intent, (value) => {
  if (value) { excuseType.value = 'OTHER'; comment.value = ''; file.value = null; void nextTick(() => dialog.value?.showModal()) }
  else { dialog.value?.close(); void nextTick(() => { if (returnFocus.value?.isConnected) returnFocus.value.focus() }) }
})
function saveExcuse(): void { controller.saveExcuse({ status: 'EXCUSED', excuseType: excuseType.value, comment: comment.value.trim() || null, file: file.value }) }
function readFile(event: Event): void { file.value = (event.target as HTMLInputElement).files?.[0] ?? null }
function filterKey(): string { return JSON.stringify({ subjectId: props.subjectId, lessonTypes: [...new Set(props.lessonTypes)].sort() }) }
function ownerKey(): string { return `${props.groupId}/${props.studentId}/${props.semesterId}/${filterKey()}/${props.offline}/${props.allowed}/${denied.value}` }
async function openTicket(lesson: HeadmanStatsLesson): Promise<void> {
  const api = props.requestsApi; const link = lesson.approvedTicket; if (!api || !link || denied.value || props.offline || !props.excuseAllowed || ticketBusy.value) return
  const revision = ++ticketRevision; const owner = ownerKey(); ticket.value = null; ticketError.value = null; ticketId.value = link.id; ticketBusy.value = true
  try { const result = await api.get(link.id); if (revision !== ticketRevision || api !== props.requestsApi || owner !== ownerKey()) return; if (result.summary.id !== link.id || result.summary.studentId !== props.studentId || result.summary.kind !== link.kind) throw new Error('Сервер вернул другую заявку.'); ticket.value = result }
  catch (cause) { if (revision === ticketRevision && api === props.requestsApi && owner === ownerKey()) ticketError.value = cause instanceof Error ? cause.message : 'Не удалось открыть заявку.' }
  finally { if (revision === ticketRevision) ticketBusy.value = false }
}
watch(() => [props.api, props.requestsApi, props.journalApi, props.groupId, props.studentId, props.semesterId, filterKey(), props.offline, props.allowed, props.excuseAllowed, denied.value] as const, () => { ticketRevision += 1; ticket.value = null; ticketId.value = null; ticketError.value = null; ticketBusy.value = false; file.value = null; comment.value = '' }, { flush: 'sync' })
onBeforeUnmount(() => { ticketRevision += 1; dialog.value?.close() })
function back(): boolean { if (intent.value) { controller.cancel(); return true }; return false }
defineExpose({ ready: computed(() => data.value !== null), controller, back })
</script>
<template>
  <section
    class="headman-stats-history"
    aria-labelledby="headman-stats-history-title"
    :aria-busy="loading"
  >
    <h2 id="headman-stats-history-title">
      Прошедшие пары
    </h2>
    <p
      v-if="offline"
      role="status"
    >
      История доступна только при подключении к интернету.
    </p>
    <p
      v-if="feedback"
      class="headman-stats-history__feedback"
      role="status"
    >
      ✓ {{ feedback }}
    </p>
    <StudentWarningBlock
      v-if="error"
      title="История недоступна"
      :message="error"
      action-label="Повторить"
      :action-disabled="loading || offline || pending !== null"
      @action="controller.load"
    />
    <p
      v-if="loading"
      role="status"
    >
      Загружаем историю занятий…
    </p>
    <p
      v-if="data && !data.items.length && !loading"
      class="headman-stats-history__empty"
      role="status"
    >
      За выбранный семестр прошедших пар пока нет.
    </p>
    <section
      v-for="group in grouped"
      :key="group.date"
      class="headman-stats-history__day"
    >
      <h3>{{ dateText(group.date) }}</h3><ul>
        <li
          v-for="lesson in group.lessons"
          :key="lesson.lessonId"
          class="headman-stats-history__lesson"
        >
          <div class="headman-stats-history__lesson-main">
            <strong>{{ lesson.subjectName }}</strong><span>{{ timeText(lesson.startsAt) }} · {{ lesson.room || 'Аудитория не указана' }} · {{ typeText(lesson.lessonType) }}</span><small v-if="!lesson.editable">{{ lesson.blockedReason || 'Только просмотр' }}</small>
          </div><div
            class="headman-stats-history__choices"
            role="group"
            :aria-label="`Отметка: ${lesson.subjectName}, ${dateText(lesson.lessonDate)}`"
          >
            <button
              v-for="status in lesson.status ? [lesson.status] : kinds"
              :key="status"
              type="button"
              :class="['headman-stats-history__choice', lesson.status ? 'headman-stats-history__choice--' + status.toLowerCase() : 'headman-stats-history__choice--empty']"
              :disabled="!writable || !lesson.editable || (status === 'EXCUSED' ? !excuseAllowed : !markAllowed) || (lesson.status !== null && !lesson.markRevision)"
              :aria-label="lesson.status ? `Снять отметку: ${words[status]}` : `Поставить отметку: ${words[status]}`"
              @click="choose(lesson, status, $event)"
            >
              {{ labels[status] }}
            </button>
          </div>
          <button
            v-if="lesson.approvedTicket"
            class="headman-stats-history__ticket"
            type="button"
            :disabled="!requestsApi || offline || !excuseAllowed || ticketBusy"
            @click="openTicket(lesson)"
          >
            Открыть принятую заявку {{ lesson.approvedTicket.kind === 'EXCUSE' ? '«у»' : '«н»' }}
          </button>
        </li>
      </ul>
    </section>
    <nav
      v-if="data && data.totalPages > 1"
      class="headman-stats-history__pages"
      aria-label="Страницы истории"
    >
      <button
        type="button"
        :disabled="!data.hasPrevious || loading || pending !== null"
        @click="controller.changePage(data.page - 1)"
      >
        Назад
      </button><span>{{ data.page + 1 }} из {{ data.totalPages }}</span><button
        type="button"
        :disabled="!data.hasNext || loading || pending !== null"
        @click="controller.changePage(data.page + 1)"
      >
        Дальше
      </button>
    </nav>
    <section
      v-if="ticket || ticketError || ticketBusy"
      class="headman-stats-history__request"
      aria-label="Принятая заявка"
    >
      <h3>Заявка</h3><p
        v-if="ticketBusy"
        role="status"
      >
        Загружаем…
      </p><p
        v-if="ticketError"
        role="alert"
      >
        {{ ticketError }}
      </p><template v-if="ticket">
        <span
          class="request-status-badge"
          :data-status="ticket.summary.status"
        >{{ ticket.summary.status === 'APPROVED' ? 'Одобрена' : ticket.summary.status }}</span>
        <p>{{ ticket.summary.reason || ticket.summary.comment || 'Принятая заявка студента' }}</p>
        <RequestAttachmentPreview
          v-for="attachment in ticket.attachments"
          :key="attachment.id"
          :preview-only="true"
          :tap-to-open="true"
          :api="requestsApi"
          :request-id="ticket.summary.id"
          :attachment="attachment"
          :owner-key="ownerKey()"
          :allowed="allowed && excuseAllowed && !denied"
          :offline="offline"
        />
      </template>
    </section>
    <dialog
      ref="dialog"
      class="headman-stats-history__dialog"
      aria-labelledby="headman-stats-confirm-title"
      @cancel.prevent="controller.cancel"
    >
      <template v-if="intent">
        <h2 id="headman-stats-confirm-title">
          {{ confirmation ? 'Снять уважительную отметку?' : 'Уважительная причина' }}
        </h2><p>{{ dateText(intent.lesson.lessonDate) }} · {{ intent.lesson.subjectName }}</p><template v-if="confirmation">
          <p>Если у отметки есть файл, добавленный старостой, он будет удалён. Принятая заявка студента и её файлы сохранятся.</p><div class="headman-stats-history__dialog-actions">
            <button
              type="button"
              autofocus
              data-action="cancel"
              @click="controller.cancel"
            >
              Отмена
            </button><button
              type="button"
              data-action="clear"
              @click="controller.confirm"
            >
              Очистить
            </button>
          </div>
        </template><form
          v-else
          @submit.prevent="saveExcuse"
        >
          <label>Причина<select v-model="excuseType"><option
            v-for="reason in reasons"
            :key="reason.value"
            :value="reason.value"
          >{{ reason.label }}</option></select></label><label>Комментарий<textarea v-model="comment" /></label><label class="mobile-upload"><input
            type="file"
            accept="image/jpeg,image/png,application/pdf"
            @change="readFile"
          ><MobileIcon name="homework" /><span>{{ file ? file.name : 'Прикрепить файл' }}</span><small>JPEG, PNG или PDF · до 10 МиБ</small></label><div class="headman-stats-history__dialog-actions">
            <button
              type="button"
              data-action="cancel"
              @click="controller.cancel"
            >
              Отмена
            </button><button
              type="submit"
              data-action="confirm"
            >
              Сохранить
            </button>
          </div>
        </form>
      </template>
    </dialog>
  </section>
</template>
