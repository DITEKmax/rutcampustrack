<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import { HeadmanJournalApiError } from './headman-journal-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { HeadmanJournalLesson } from './headman-journal-client'
import type { HeadmanLessonAction } from './headman-journal-actions'
import type { HeadmanLessonActionsApi } from './headman-journal-actions-client'
import './headman-lesson-actions-screen.pcss'
const props = withDefaults(defineProps<{api:HeadmanLessonActionsApi|null;lesson:HeadmanJournalLesson;subjectName?:string;offline?:boolean}>(),{subjectName:'Занятие группы',offline:false})
const emit = defineEmits<{error:[cause:unknown]}>()
const items=shallowRef<readonly HeadmanLessonAction[]>([])
const nextCursor=ref<string|null>(null)
const loading=ref(false)
const error=ref<string|null>(null)
const filter=ref<'all'|'marks'|'lesson'|'homework'>('all')
let revision=0
let disposed=false
const visibleItems=computed(()=>items.value.filter((item)=>filter.value === 'all' || filter.value === 'lesson' && (item.action.startsWith('LESSON_') || item.action==='ROOM_CHANGED') || filter.value === 'homework' && item.action.startsWith('HOMEWORK_') || filter.value === 'marks' && /^(ATTENDANCE_|GEO_|AUTO_|REQUEST_)/.test(item.action)))
function title(item:HeadmanLessonAction):string {
  const labels:Record<string,string>={ATTENDANCE_SET:'Отметка поставлена',ATTENDANCE_CLEARED:'Отметка снята',GEO_ATTEMPT:'Попытка геоотметки',AUTO_ABSENT:'Автоматическая отметка «н»',REQUEST_SUBMITTED:'Заявка подана',REQUEST_DECIDED:'По заявке принято решение',REQUEST_CANCELLED:'Заявка отменена',ROOM_CHANGED:'Аудитория изменена',LESSON_CREATED:'Пара создана',LESSON_EDITED:'Пара изменена',LESSON_CANCELLED:'Пара отменена',LESSON_RESTORED:'Пара восстановлена',LESSON_TRANSFERRED:'Пара перенесена',HOMEWORK_CREATED:'Выдано домашнее задание',HOMEWORK_EDITED:'Домашнее задание изменено',HOMEWORK_ARCHIVED:'Домашнее задание архивировано'}
  const label=labels[item.action] ?? `Событие: ${item.action}`
  return item.student?.displayName ? `${item.student.displayName} · ${label}` : label
}
function rejectionReason(item: HeadmanLessonAction): string {
  const reasons: Record<string, string> = {
    CHECKIN_NOT_ELIGIBLE: 'Отметка сейчас недоступна', CHECKIN_COOLDOWN: 'Повторная попытка пока недоступна',
    MANUAL_ABSENCE_REQUIRES_APPEAL: 'Для ручной отметки «н» нужна заявка', IDEMPOTENCY_PAYLOAD_MISMATCH: 'Содержимое повторного запроса изменилось',
    LESSON_NOT_EDITABLE: 'Пара недоступна для изменения', ATTENDANCE_REVISION_CONFLICT: 'Отметка уже изменилась', ATTENDANCE_WRITE_CONFLICT: 'Занятие или семестр уже изменились',
  }
  return item.errorCode ? reasons[item.errorCode] ?? 'Сервер отклонил действие' : 'Сервер отклонил действие'
}
function timestamp(value:string):string { return new Intl.DateTimeFormat('ru-RU',{day:'numeric',month:'short',hour:'2-digit',minute:'2-digit',timeZone:'Europe/Moscow'}).format(new Date(value)) }
function geoVerdict(value: string): string {
  return ({ ACCEPTED: 'Геолокация подтверждена', OUTSIDE_RADIUS: 'За пределами радиуса', UNAVAILABLE: 'Геолокация недоступна' } as Record<string, string>)[value] ?? value
}
function lessonType(value: string | null): string {
  return value === 'LECTURE' ? 'Лекция' : value === 'PRACTICE' ? 'Практика' : value === 'LAB' ? 'Лабораторная' : value ?? 'Тип не указан'
}
function changeText(item: HeadmanLessonAction): string | null {
  if (item.action==='ROOM_CHANGED') {
    const room=(value: unknown) => typeof value==='string' && value.trim() ? value : 'Не указана'
    return room(item.before?.room_snapshot)+' → '+room(item.after?.room_snapshot)
  }
  const status = (value: unknown) => value === 'PRESENT' ? '+' : value === 'EXCUSED' ? 'у' : value === 'ABSENT' ? 'н' : value === 'PLANNED' ? 'Предстоит' : value === 'ACTIVE' ? 'Открыта' : value === 'CLOSED' ? 'Закрыта' : value === 'CANCELLED' ? 'Отменена' : value === 'TRANSFERRED' ? 'Перенесена' : null
  const before = status(item.before?.status); const after = status(item.after?.status)
  return before || after ? (before ?? 'Без отметки') + ' → ' + (after ?? 'Без отметки') : null
}
function markText(item:HeadmanLessonAction):string|null {
  if (item.outcome === 'REJECTED') return null
  const status=item.after?.status
  return status === 'PRESENT' ? '+' : status === 'EXCUSED' ? 'у' : status === 'ABSENT' ? 'н' : null
}
async function load(more=false):Promise<void> {
  if (loading.value && more) return
  const run=++revision
  const api=props.api
  const lessonId=props.lesson.id
  const cursor=more?nextCursor.value:null
  if (!more) {items.value=[];nextCursor.value=null}
  error.value=null
  if (!api || props.offline) {loading.value=false;return}
  loading.value=true
  try {
    const page=await api.getLessonActions(lessonId,cursor)
    if (disposed || run !== revision || api !== props.api || lessonId !== props.lesson.id || props.offline) return
    const existing=more?items.value:[]
    const ids=new Set(existing.map((item)=>item.id))
    items.value=[...existing,...page.items.filter((item)=>!ids.has(item.id))]
    nextCursor.value=page.nextCursor
  } catch(cause) {
    if (disposed || run !== revision || api !== props.api || lessonId !== props.lesson.id || props.offline || cause instanceof StaleSessionGenerationError) return
    if (cause instanceof HeadmanJournalApiError && [401,403].includes(cause.response.status)) { items.value=[]; nextCursor.value=null }
    error.value=cause instanceof Error?cause.message:'Не удалось загрузить журнал действий.'
    emit('error',cause)
  } finally {if (!disposed && run === revision) loading.value=false}
}
watch(()=>[props.api,props.lesson.id,props.offline] as const,()=>{void load()},{immediate:true,flush:'sync'})
onBeforeUnmount(()=>{disposed=true;revision+=1})
</script>
<template>
  <main
    class="headman-actions"
    aria-labelledby="headman-actions-title"
    :aria-busy="loading"
    :data-lesson-id="lesson.id"
  >
    <h1 id="headman-actions-title">
      Журнал действий
    </h1>
    <section
      class="headman-actions__context"
      aria-label="Выбранная пара"
    >
      <h2>{{ subjectName }}</h2><p>{{ lesson.date }} · {{ lesson.startTime?.slice(0,5) ?? '—' }}–{{ lesson.endTime?.slice(0,5) ?? '—' }}</p><p><MobileIcon name="lesson" />{{ lessonType(lesson.lessonType) }}</p>
    </section>
    <div
      class="headman-actions__filters"
      role="group"
      aria-label="События пары"
    >
      <button
        v-for="choice in ([['all','Все'],['marks','Отметки'],['lesson','Пара'],['homework','ДЗ']] as const)"
        :key="choice[0]"
        type="button"
        :aria-pressed="filter === choice[0]"
        @click="filter=choice[0]"
      >
        {{ choice[1] }}
      </button>
    </div>
    <StudentWarningBlock
      v-if="offline"
      title="Нет подключения"
      message="Журнал действий доступен при подключении к интернету."
    />
    <StudentWarningBlock
      v-else-if="error"
      title="Журнал недоступен"
      :message="error"
      action-label="Повторить"
      @action="load(items.length > 0)"
    />
    <p
      v-else-if="loading && !items.length"
      role="status"
    >
      Загружаем журнал…
    </p>
    <p
      v-else-if="!visibleItems.length"
      role="status"
    >
      {{ items.length ? 'Событий этого типа нет.' : 'Сервер ещё не вернул событий по этой паре.' }}
    </p>
    <ol class="headman-actions__list">
      <li
        v-for="item in visibleItems"
        :key="item.id"
      >
        <div><strong>{{ title(item) }}<span v-if="markText(item)"> · {{ markText(item) }}</span></strong><time :datetime="item.occurredAt">{{ timestamp(item.occurredAt) }}</time></div><p v-if="item.actor">
          {{ item.outcome === 'REJECTED' ? 'Попытку выполнил:' : 'Изменил:' }} {{ item.actor.displayName ?? `Пользователь #${item.actor.userId}` }}
        </p><p
          v-if="item.outcome === 'REJECTED'"
          class="headman-actions__rejected"
        >
          Отклонено · {{ rejectionReason(item) }}
        </p><p v-if="item.source">
          Источник: {{ item.source }}
        </p><p v-if="item.outcome === 'APPLIED' && changeText(item)">
          {{ changeText(item) }}
        </p><p v-if="item.geo">
          {{ geoVerdict(item.geo.verdict) }}<span v-if="item.geo.distanceMeters !== null"> · {{ item.geo.distanceMeters }} м от центра</span>
        </p><p v-if="item.ticket">
          Заявка {{ item.ticket.id }} · {{ item.ticket.status === 'APPROVED' ? 'одобрено' : item.ticket.status === 'PENDING' ? 'ожидает решения' : item.ticket.status === 'REJECTED' ? 'отклонено' : item.ticket.status === 'CANCELLED' ? 'отменено' : item.ticket.status }}
        </p><p v-if="item.homework">
          ДЗ #{{ item.homework.id }}<span v-if="item.homework.revision !== null"> · версия {{ item.homework.revision }}</span>
        </p>
      </li>
    </ol>
    <button
      v-if="nextCursor"
      type="button"
      :disabled="loading || offline"
      @click="load(true)"
    >
      {{ loading?'Загружаем…':'Показать ещё' }}
    </button>
  </main>
</template>
