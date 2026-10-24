<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue';
import { useRoute } from 'vue-router';
import { message, webSession } from '../../auth/session';
import { useWebLiveRefresh } from '../../app/live';
import type {
  HeadmanRequestDetail,
  HeadmanRequestPage,
  HeadmanRequestKind,
  HeadmanRequestBucket,
} from '../../../../mobile-core/src/features/headman-requests/headman-requests-client';
import {
  freezeRequestDecision,
  sendRequestDecision,
  reconcileRequestDecision,
  updateRequestExclusions,
  type FrozenRequestDecision,
} from '../../../../mobile-core/src/features/headman-requests/request-decision';
import HeadmanPage from './HeadmanPage.vue';
import { restoreRequestDecision, serializeRequestDecision } from './request-decision-state';
import {
  useHeadmanFeature,
  dateLabel,
  lessonTypes,
  lessonTypeCode,
  markLabels,
  reasonLabel,
  saveBlob,
} from './context';
const feature = useHeadmanFeature(),
  route = useRoute();
const loading = ref(false),
  busy = ref(false),
  error = ref<string | null>(null);
const filters = reactive({
  bucket: 'OPEN' as HeadmanRequestBucket,
  type: (route.query.type === 'EXCUSE' || route.query.type === 'LATE_CHECKIN'
    ? route.query.type
    : '') as HeadmanRequestKind | '',
  name: '',
  from: '',
  to: '',
  page: 0,
});
const page = shallowRef<HeadmanRequestPage | null>(null),
  detail = shallowRef<HeadmanRequestDetail | null>(null);
const excluded = ref<readonly string[]>([]),
  reason = ref(''),
  pending = shallowRef<FrozenRequestDecision | null>(null);
const proposed = ref<'APPROVED' | 'REJECTED' | null>(null);
const replayReady = shallowRef<FrozenRequestDecision | null>(null);
const decisionActor = webSession.snapshot.value?.userId;
const decisionStorage = `rct:web:headman:request-decision:${webSession.identity.value}`;
let restored = false;
function persistDecision(value: FrozenRequestDecision | null) {
  feature.assertCurrent();
  if (value) sessionStorage.setItem(decisionStorage, serializeRequestDecision(value));
  else sessionStorage.removeItem(decisionStorage);
  pending.value = value;
  replayReady.value = null;
}
const statuses = {
  PENDING: 'На рассмотрении',
  APPROVED: 'Одобрена',
  REJECTED: 'Отклонена',
  CANCELLED: 'Отозвана',
};
const affected = computed(
  () => detail.value?.currentAffectedLessons ?? detail.value?.lessons ?? [],
);
const ownRequest = computed(
  () => String(detail.value?.summary.studentId) === webSession.snapshot.value?.userId,
);
const decisionAvailable = computed(
  () =>
    !!detail.value &&
    !ownRequest.value &&
    detail.value.summary.status === 'PENDING' &&
    feature.writable('MANAGE_EXCUSES') &&
    (detail.value.summary.kind === 'LATE_CHECKIN' ||
      (!!detail.value.decisionRevision &&
        affected.value.length > 0 &&
        affected.value.every((lesson) => !!lesson.occurrenceId && !!lesson.occurrenceRevision))),
);
let revision = 0;
async function load() {
  const run = ++revision;
  loading.value = true;
  error.value = null;
  try {
    await feature.initialize();
    if (!feature.allowed('MANAGE_EXCUSES')) throw new Error('Нет права работы с заявками группы.');
    if (!restored) {
      const saved = sessionStorage.getItem(decisionStorage);
      if (saved) pending.value = restoreRequestDecision(saved, feature.requests);
      restored = true;
    }
    const result = await feature.requests.list({
      bucket: filters.bucket,
      type: filters.type || null,
      studentName: filters.name,
      coverageDateFrom: filters.from || null,
      coverageDateTo: filters.to || null,
      page: filters.page,
      size: 20,
    });
    feature.assertCurrent();
    if (run !== revision) return;
    page.value = result;
    if (route.query.request && !detail.value) await open(String(route.query.request));
  } catch (cause) {
    if (run === revision) error.value = message(cause);
  } finally {
    if (run === revision) loading.value = false;
  }
}
async function open(id: string) {
  if (busy.value || pending.value) return;
  busy.value = true;
  error.value = null;
  try {
    const result = await feature.requests.get(id);
    feature.assertCurrent();
    detail.value = result;
    excluded.value = [];
    reason.value = '';
    proposed.value = null;
  } catch (cause) {
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
function exclude(id: string, checked: boolean) {
  if (!detail.value || pending.value) return;
  try {
    excluded.value = updateRequestExclusions(detail.value, excluded.value, id, checked);
  } catch (cause) {
    error.value = message(cause);
  }
}
async function decide() {
  if (!detail.value || !proposed.value || busy.value || pending.value || !decisionAvailable.value)
    return;
  try {
    persistDecision(
      freezeRequestDecision(
        feature.requests,
        detail.value,
        proposed.value,
        reason.value,
        proposed.value === 'APPROVED' ? excluded.value : [],
      ),
    );
  } catch (cause) {
    error.value = message(cause);
    return;
  }
  await sendPending(false);
}
function matchesOwnLateDecision(operation: FrozenRequestDecision, observed: HeadmanRequestDetail) {
  return (
    !operation.selection &&
    observed.summary.id === operation.id &&
    observed.summary.kind === 'LATE_CHECKIN' &&
    observed.summary.status === operation.decision &&
    observed.summary.decisionComment === operation.reason &&
    String(observed.summary.decisionBy) === decisionActor
  );
}
async function sendPending(replay: boolean) {
  const operation = pending.value;
  if (!operation) return;
  if (busy.value || (replay && replayReady.value !== operation)) return;
  busy.value = true;
  replayReady.value = null;
  error.value = null;
  try {
    const outcome = await sendRequestDecision(operation, replay, () => {
      feature.assertCurrent();
      return pending.value === operation && feature.writable('MANAGE_EXCUSES');
    });
    feature.assertCurrent();
    if (outcome.status === 'done' || outcome.status === 'conflict') {
      const conflict =
        outcome.status === 'conflict' && !matchesOwnLateDecision(operation, outcome.detail);
      detail.value = outcome.detail;
      persistDecision(null);
      proposed.value = null;
      await load();
      if (conflict)
        error.value = 'Заявка уже обработана другим решением. Показано текущее состояние.';
    } else if (outcome.status === 'rejected') {
      persistDecision(null);
      error.value = message(outcome.cause);
    } else
      error.value =
        'Результат решения неизвестен. Проверь заявку; повторная отправка заблокирована.';
  } catch (cause) {
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
async function reconcile() {
  if (!pending.value || busy.value) return;
  const operation = pending.value;
  busy.value = true;
  replayReady.value = null;
  try {
    const observed = await feature.requests.get(operation.id);
    feature.assertCurrent();
    if (observed.summary.id !== operation.id) throw new Error('Сервер вернул другую заявку.');
    detail.value = observed;
    excluded.value = operation.selection?.excludedOccurrenceIds ?? [];
    reason.value = operation.reason ?? '';
    proposed.value = operation.decision;
    const outcome =
      observed.summary.status === 'PENDING'
        ? null
        : matchesOwnLateDecision(operation, observed)
          ? { status: 'done' as const, detail: observed }
          : await reconcileRequestDecision(operation);
    feature.assertCurrent();
    if (outcome?.status === 'done' || outcome?.status === 'conflict') {
      detail.value = outcome.detail;
      persistDecision(null);
      proposed.value = null;
      await load();
      if (outcome.status === 'conflict')
        error.value = 'Решение отличается от отправленного. Показан результат сервера.';
    } else if (
      outcome === null &&
      (operation.selection || observed.summary.kind === 'LATE_CHECKIN')
    ) {
      replayReady.value = operation;
      error.value =
        operation.selection
          ? 'Заявка ещё ожидает решения. Можно явно продолжить исходное сохранение с прежним ключом.'
          : 'Апелляция ещё ожидает решения. Можно явно продолжить сохранение исходного решения и причины.';
    } else
      error.value = 'Решение ещё не подтверждено. Можно повторить проверку без отправки решения.';
  } catch (cause) {
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
async function download(id: string, name: string | null) {
  const owner = detail.value;
  if (!owner) return;
  try {
    const blob = await feature.requests.downloadAttachment(owner.summary.id, id);
    feature.assertCurrent();
    saveBlob(blob, name ?? 'Подтверждение');
  } catch (cause) {
    error.value = message(cause);
  }
}
onMounted(load);
useWebLiveRefresh(load, {
  blocked: () => busy.value || loading.value || !!detail.value || !!pending.value,
});
</script>
<template>
  <HeadmanPage
    title="Заявки и тикеты"
    :loading="loading"
    :error="error"
    @reload="load"
  >
    <p
      v-if="pending"
      class="web-notice"
      >Сохранено исходное решение по заявке:
      {{ pending.decision === 'APPROVED' ? 'одобрить' : 'отклонить'
      }}{{ pending.reason ? ` · ${pending.reason}` : '' }}. Новая команда заблокирована до проверки
      результата.
      <button
        class="web-button"
        :disabled="busy"
        @click="reconcile"
        >Проверить результат</button
      >
      <button
        v-if="replayReady === pending"
        class="web-button"
        :disabled="busy || !feature.writable('MANAGE_EXCUSES')"
        @click="sendPending(true)"
        >Продолжить исходное сохранение</button
      >
    </p>
    <form
      class="headman-panel headman-toolbar"
      @submit.prevent="
        filters.page = 0;
        load();
      "
    >
      <label class="web-field"
        >Очередь<select v-model="filters.bucket"
          ><option value="OPEN">Открытые</option
          ><option value="ARCHIVE">Архив</option></select
        ></label
      >
      <label class="web-field"
        >Тип<select v-model="filters.type"
          ><option value="">Все</option
          ><option value="EXCUSE">Уважительная причина</option
          ><option value="LATE_CHECKIN">Апелляция отметки</option></select
        ></label
      >
      <label class="web-field"
        >Студент<input
          v-model="filters.name"
          type="search" /></label
      ><label class="web-field"
        >С даты<input
          v-model="filters.from"
          type="date" /></label
      ><label class="web-field"
        >По дату<input
          v-model="filters.to"
          type="date" /></label
      ><button
        class="web-button"
        :disabled="loading"
        >Показать</button
      >
    </form>
    <p
      v-if="page && !page.content.length"
      class="web-notice"
      >Заявок по этим условиям нет.</p
    >
    <div
      v-if="page?.content.length"
      class="headman-table-scroll"
      ><table class="headman-table"
        ><thead
          ><tr
            ><th>Студент</th><th>Тип / причина</th><th>Период</th><th>Пары</th><th>Статус</th
            ><th>Детали</th></tr
          ></thead
        ><tbody
          ><tr
            v-for="request in page.content"
            :key="request.id"
            ><th scope="row">{{ request.studentName }}</th
            ><td
              >{{ request.kind === 'EXCUSE' ? 'Уважительная причина' : 'Апелляция отметки'
              }}<p>{{ reasonLabel(request.reason) }}</p></td
            ><td>{{ dateLabel(request.coverageStart) }} — {{ dateLabel(request.coverageEnd) }}</td
            ><td
              >{{ request.lessonCount
              }}<span v-if="request.alreadyMarkedCount">
                · уже отмечено {{ request.alreadyMarkedCount }}</span
              ></td
            ><td>{{ statuses[request.status] }}</td
            ><td
              ><button
                class="web-button"
                :disabled="busy || !!pending"
                @click="open(request.id)"
                >Открыть</button
              ></td
            ></tr
          ></tbody
        ></table
      ></div
    >
    <div
      v-if="page && page.totalPages > 1"
      class="headman-actions"
      ><button
        class="web-button"
        :disabled="filters.page === 0 || loading"
        @click="
          filters.page--;
          load();
        "
        >Назад</button
      ><span>{{ page.page + 1 }} / {{ page.totalPages }} · всего {{ page.totalElements }}</span
      ><button
        class="web-button"
        :disabled="page.page + 1 >= page.totalPages || loading"
        @click="
          filters.page++;
          load();
        "
        >Далее</button
      ></div
    >
    <section
      v-if="detail"
      class="headman-panel web-stack"
    >
      <div class="headman-heading"
        ><h2>{{ detail.summary.studentName }} · {{ statuses[detail.summary.status] }}</h2
        ><button
          class="web-button"
          :disabled="busy || !!pending"
          @click="detail = null"
          >Закрыть</button
        ></div
      ><p>{{ reasonLabel(detail.summary.reason) }}</p
      ><p v-if="detail.summary.comment">{{ detail.summary.comment }}</p
      ><p v-if="detail.summary.decisionComment">Решение: {{ detail.summary.decisionComment }}</p
      ><p class="headman-muted"
        >Одобрение применяется к текущему составу пар. Уже стоящая отметка «Был» сохраняется.</p
      >
      <div class="headman-table-scroll"
        ><table class="headman-table"
          ><thead
            ><tr
              ><th>Дата / пара</th><th>Предмет</th><th>Отметка</th
              ><th v-if="detail.summary.kind === 'EXCUSE' && decisionAvailable"
                >Исключить из одобрения</th
              ></tr
            ></thead
          ><tbody
            ><tr
              v-for="(lesson, index) in affected"
              :key="lesson.occurrenceId ?? index"
              ><td>{{ dateLabel(lesson.date) }} · {{ lesson.lessonNumber }}</td
              ><td
                >{{ lesson.subjectName }} ·
                {{ lessonTypes[lessonTypeCode(lesson.subjectType)] ?? 'Тип недоступен' }}</td
              ><td>{{
                markLabels[lesson.attendanceStatus ?? ''] ?? lesson.attendanceStatus ?? '—'
              }}</td
              ><td v-if="detail.summary.kind === 'EXCUSE' && decisionAvailable"
                ><label v-if="lesson.occurrenceId"
                  ><input
                    type="checkbox"
                    :checked="excluded.includes(lesson.occurrenceId)"
                    :disabled="busy || !!pending || !feature.writable('MANAGE_EXCUSES')"
                    @change="
                      exclude(lesson.occurrenceId, ($event.target as HTMLInputElement).checked)
                    "
                  />
                  Исключить</label
                ></td
              ></tr
            ></tbody
          ></table
        ></div
      >
      <div class="headman-actions"
        ><button
          v-for="attachment in detail.attachments"
          :key="attachment.id"
          class="web-button"
          @click="download(attachment.id, attachment.name)"
          >{{ attachment.name ?? 'Подтверждение' }} ·
          {{ Math.ceil(attachment.size / 1024) }} КБ</button
        ></div
      >
      <p
        v-if="detail.summary.status === 'PENDING' && !decisionAvailable"
        class="web-notice"
        >{{
          ownRequest
            ? 'Свою заявку обрабатывает другой уполномоченный участник группы.'
            : 'Решение недоступно: сервер не подтвердил текущий состав и версию заявки.'
        }}
        <button
          v-if="!ownRequest"
          class="web-button"
          :disabled="busy || !!pending"
          @click="open(detail.summary.id)"
          >Обновить заявку</button
        >
      </p>
      <form
        v-if="decisionAvailable && !pending"
        class="web-stack"
        @submit.prevent="decide"
        ><label class="web-field"
          >Решение<select
            v-model="proposed"
            required
            ><option
              :value="null"
              disabled
              >Выбери решение</option
            ><option value="APPROVED">Одобрить</option
            ><option value="REJECTED">Отклонить</option></select
          ></label
        ><label
          v-if="proposed === 'REJECTED'"
          class="web-field"
          >Причина отказа<textarea
            v-model="reason"
            required
            maxlength="300"
          /></label
        ><p v-if="proposed === 'APPROVED'"
          >Пар в решении: {{ affected.length - excluded.length }}. Исключено:
          {{ excluded.length }}.</p
        ><button
          class="web-button web-button-primary"
          :disabled="busy || !proposed"
          >Подтвердить решение</button
        ></form
      >
    </section>
  </HeadmanPage>
</template>
