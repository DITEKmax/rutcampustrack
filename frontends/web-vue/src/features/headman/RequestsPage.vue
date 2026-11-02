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
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import HeadmanPage from './HeadmanPage.vue';
import { restoreRequestDecision, serializeRequestDecision } from './request-decision-state';
import HeadmanRequestCard from './HeadmanRequestCard.vue';
import HeadmanRequestDetails from './HeadmanRequestDetails.vue';
import WebSelect from '../../shared/WebSelect.vue';
import WebDateField from '../../shared/WebDateField.vue';
import './headman-workflows.pcss';
import {
  useHeadmanFeature,
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
const locked = computed(() => busy.value || loading.value || !!pending.value);
const detachedDetail = computed(() => detail.value && !page.value?.content.some(item => item.id === detail.value?.summary.id) ? detail.value : null);
function selectBucket(bucket: HeadmanRequestBucket) { if (busy.value || pending.value) return; detail.value = null; filters.bucket = bucket; filters.page = 0; page.value = null; void load(); }
function applyFilters() { if (busy.value || pending.value) return; detail.value = null; filters.page = 0; void load(); }
function toggle(id: string) { if (busy.value || pending.value) return; if (detail.value?.summary.id === id) detail.value = null; else void open(id); }
let restored = false;
function persistDecision(value: FrozenRequestDecision | null) {
  feature.assertCurrent();
  if (value) sessionStorage.setItem(decisionStorage, serializeRequestDecision(value));
  else sessionStorage.removeItem(decisionStorage);
  pending.value = value;
  replayReady.value = null;
}
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
  <HeadmanPage title="Заявки и тикеты" class="headman-workflows" hide-title :error="error" @reload="load">
    <p v-if="pending" class="web-notice" role="status">Сохранено исходное решение по заявке: {{ pending.decision === 'APPROVED' ? 'одобрить' : 'отклонить' }}{{ pending.reason ? ` · ${pending.reason}` : '' }}. Новая команда заблокирована до проверки результата. <button type="button" class="web-button" :disabled="busy" @click="reconcile">Проверить результат</button><button v-if="replayReady === pending" type="button" class="web-button" :disabled="busy || !feature.writable('MANAGE_EXCUSES')" @click="sendPending(true)">Продолжить исходное сохранение</button></p>
    <div class="headman-flow-tabs" role="group" aria-label="Очередь заявок"><button type="button" class="web-button" :class="{ 'web-button-selected': filters.bucket === 'OPEN' }" :aria-pressed="filters.bucket === 'OPEN'" :disabled="locked" @click="selectBucket('OPEN')">На рассмотрении</button><button type="button" class="web-button" :class="{ 'web-button-selected': filters.bucket === 'ARCHIVE' }" :aria-pressed="filters.bucket === 'ARCHIVE'" :disabled="locked" @click="selectBucket('ARCHIVE')">Архив</button></div>
    <form class="headman-flow-filter-grid headman-request-filters" @submit.prevent="applyFilters">
      <WebSelect v-model="filters.type" label="Тип заявки" :options="[{ value: '', label: 'Все заявки' }, { value: 'EXCUSE', label: 'Уважительная причина' }, { value: 'LATE_CHECKIN', label: 'Апелляция отметки' }]" :disabled="locked" @change="applyFilters" />
      <label class="web-field">Студент<input v-model="filters.name" type="search" :disabled="locked" /></label><WebDateField v-model="filters.from" label="Пары с даты" :max="filters.to || undefined" :disabled="locked" /><WebDateField v-model="filters.to" label="Пары по дату" :min="filters.from || undefined" :disabled="locked" /><button type="submit" class="web-button web-icon-button" aria-label="Показать заявки по фильтрам" title="Показать заявки по фильтрам" :disabled="locked"><MobileIcon name="search" /></button>
    </form>
    <p v-if="loading && !page" class="web-caption" role="status">Загрузка заявок…</p><p v-if="page && !page.content.length && !error" class="headman-flow-empty">Заявок по этим условиям нет.</p>
    <div v-if="page?.content.length" class="headman-request-list">
      <HeadmanRequestCard v-for="request in page.content" :key="request.id" :request="detail?.summary.id === request.id ? detail.summary : request" :expanded="detail?.summary.id === request.id" :locked="locked" @toggle="toggle(request.id)">
        <HeadmanRequestDetails v-if="detail?.summary.id === request.id" v-model:proposed="proposed" v-model:reason="reason" :detail="detail" :affected="affected" :excluded="excluded" :busy="busy" :pending="!!pending" :decision-available="decisionAvailable" :own-request="ownRequest" :writable="feature.writable('MANAGE_EXCUSES')" @exclude="exclude" @download="download" @refresh="open(detail.summary.id)" @decide="decide" />
      </HeadmanRequestCard>
    </div>
    <HeadmanRequestCard v-if="detachedDetail" :request="detachedDetail.summary" expanded :locked="locked" @toggle="detail = null">
      <HeadmanRequestDetails v-model:proposed="proposed" v-model:reason="reason" :detail="detachedDetail" :affected="affected" :excluded="excluded" :busy="busy" :pending="!!pending" :decision-available="decisionAvailable" :own-request="ownRequest" :writable="feature.writable('MANAGE_EXCUSES')" @exclude="exclude" @download="download" @refresh="open(detachedDetail.summary.id)" @decide="decide" />
    </HeadmanRequestCard>
    <div v-if="page && page.totalPages > 1" class="headman-actions"><button class="web-button" :disabled="filters.page === 0 || locked" @click="detail = null; filters.page--; load()">Назад</button><span class="web-caption">{{ page.page + 1 }} / {{ page.totalPages }} · всего {{ page.totalElements }}</span><button class="web-button" :disabled="page.page + 1 >= page.totalPages || locked" @click="detail = null; filters.page++; load()">Далее</button></div>
  </HeadmanPage>
</template>
