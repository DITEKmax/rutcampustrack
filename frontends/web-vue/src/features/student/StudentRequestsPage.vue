<script setup lang="ts">
import { computed, nextTick, watch } from 'vue';
import { useRoute } from 'vue-router';
import type {
  RequestDetail,
  RequestKind,
} from '../../../../mobile-core/src/features/requests/types';
import { useStudentResource, useStudentScope } from './student-state';
import { useStudentRequests } from './use-student-requests';
import { useStudentAutoPage } from './use-student-auto-page';
import {
  formatLessonDate,
  requestKindLabel,
} from '../../../../mobile-core/src/features/requests/state';
import StudentRegion from './StudentRegion.vue';
import StudentNotice from './StudentNotice.vue';
import StudentRequestForm from './StudentRequestForm.vue';
import StudentRequestCard from './StudentRequestCard.vue';
import ConfirmDialog from '../../shared/ConfirmDialog.vue';
const scope = useStudentScope(),
  requests = useStudentRequests(scope),
  route = useRoute();
const target = useStudentResource<RequestDetail>(scope);
const initialLesson = typeof route.query.lesson === 'string' ? route.query.lesson : undefined;
if (route.query.kind === 'EXCUSE' || route.query.kind === 'LATE_CHECKIN')
  requests.form.value = route.query.kind;
const more = computed(
  () =>
    requests.bucket.value.page >= 0 &&
    requests.bucket.value.page + 1 < requests.bucket.value.totalPages,
);
const canAppend = computed(
  () =>
    more.value &&
    !requests.bucket.value.error &&
    !requests.bucket.value.loading &&
    !requests.bucket.value.loadingMore &&
    !requests.busy.value,
);
const { edge, supported } = useStudentAutoPage(canAppend, async () => {
  try {
    await requests.controller.loadMore();
  } catch {
    /* Explicit retry remains visible. */
  }
});
async function loadTarget() {
  const id = typeof route.params.requestId === 'string' ? route.params.requestId : null;
  if (!id) {
    target.clear();
    return;
  }
  await target.load(async () => {
    const value = await scope.api.getRequest(id);
    scope.transport.assertCurrent();
    return requests.controller.loadTargetDetail(id, value.summary.kind);
  });
}
watch(() => [scope.refreshed.value, route.params.requestId], loadTarget);
async function openForm(kind: RequestKind) {
  requests.form.value = kind;
  await nextTick();
  document.querySelector<HTMLElement>('.student-request-form input')?.focus();
}
async function submit(payload: Parameters<typeof requests.submit>[1]) {
  const kind = requests.form.value;
  if (!kind) return;
  const result = await requests.submit(kind, payload);
  if (!result || !scope.current()) return;
  await nextTick();
  document.querySelector<HTMLElement>(`[data-request-id="${result.summary.id}"]`)?.focus();
}
const openCount = computed(() => requests.controller.openCount());
async function cancelAndRefresh() {
  await requests.cancel();
  if (scope.current()) await loadTarget();
}
async function checkCancellationAndRefresh() {
  await requests.checkCancellation();
  if (scope.current()) await loadTarget();
}
</script>
<template>
  <StudentRegion
    :scope="scope"
    screen="WEB-STUDENT-REQUESTS"
  >
    <h1>Заявки и тикеты</h1><StudentNotice
      :loading="scope.loading.value"
      :error="scope.error.value"
      @retry="scope.load"
    />
    <template v-if="scope.session.value">
      <p
        v-if="scope.readOnly.value"
        class="web-notice"
      >
        Доступен только просмотр. Подать или отменить заявку нельзя.
      </p>
      <div class="student-actions">
        <button
          class="web-button"
          :disabled="
            scope.readOnly.value ||
              !scope.session.value.semester ||
              requests.busy.value ||
              requests.ambiguous.value
          "
          @click="openForm('EXCUSE')"
        >
          Подать на «у»
        </button><button
          class="web-button"
          :disabled="
            scope.readOnly.value ||
              !scope.session.value.semester ||
              requests.busy.value ||
              requests.ambiguous.value ||
              (requests.view.options?.budget?.remaining ?? 0) <= 0
          "
          @click="openForm('LATE_CHECKIN')"
        >
          Оспорить «н»
        </button><button
          class="web-button"
          :aria-pressed="requests.view.bucket === 'open'"
          @click="requests.selectBucket('open')"
        >
          На рассмотрении{{ openCount === null ? '' : ` (${openCount})` }}
        </button><button
          class="web-button"
          :aria-pressed="requests.view.bucket === 'archive'"
          @click="requests.selectBucket('archive')"
        >
          Архив
        </button>
      </div>
      <p
        v-if="!scope.session.value.semester"
        class="web-notice"
      >
        Активного семестра нет. Можно просмотреть архив заявок.
      </p><p
        v-else-if="(requests.view.options?.budget?.remaining ?? 1) <= 0"
        class="web-caption"
      >
        Попытки оспорить отсутствие закончились. Заявки на уважительную причину доступны.
      </p>
      <StudentNotice
        :loading="requests.view.optionsLoading"
        :error="requests.view.optionsError ?? ''"
        @retry="requests.controller.loadOptions()"
      />
      <StudentRequestForm
        v-if="requests.form.value"
        :key="requests.form.value"
        :kind="requests.form.value"
        :options="requests.view.options"
        :busy="requests.busy.value"
        :ambiguous="requests.ambiguous.value"
        :read-only="scope.readOnly.value"
        :initial-lesson="initialLesson"
        @close="requests.form.value = null"
        @submit="submit"
      />
      <p
        v-if="requests.error.value || requests.view.mutationError"
        class="web-error"
        role="alert"
      >
        {{ requests.error.value || requests.view.mutationError
        }}<template v-if="requests.ambiguous.value">
          Результат отправки пока неизвестен. Сохрани форму и уточни результат той же отправки.
        </template>
      </p><p
        v-if="requests.message.value"
        role="status"
      >
        {{ requests.message.value }}
      </p><button
        v-if="requests.uncertainCancellation.value"
        class="web-button"
        :disabled="requests.busy.value"
        @click="checkCancellationAndRefresh"
      >
        Проверить состояние отмены
      </button>
      <template v-if="route.params.requestId">
        <RouterLink
          class="web-button"
          :to="{ name: 'student-requests' }"
        >
          К списку моих заявок
        </RouterLink><StudentNotice
          :loading="target.loading.value"
          :error="target.error.value"
          @retry="loadTarget"
        /><StudentRequestCard
          v-if="target.data.value"
          :key="target.data.value.summary.id"
          :request="target.data.value"
          :scope="scope"
          :authority="requests.authority.value"
          :busy="requests.busy.value"
          :cancel-blocked="Boolean(requests.uncertainCancellation.value)"
          initial-expanded
          @cancel="requests.cancelling.value = target.data.value"
          @retry="loadTarget"
        />
      </template>
      <template v-else>
        <StudentNotice
          :loading="requests.bucket.value.loading"
          :error="requests.bucket.value.error ?? ''"
          :empty="
            !requests.bucket.value.loading && !requests.bucket.value.requests.length
              ? requests.view.bucket === 'open'
                ? 'Нет заявок на рассмотрении.'
                : 'Архив заявок пуст.'
              : ''
          "
          @retry="requests.load"
        /><StudentRequestCard
          v-for="request in requests.bucket.value.requests"
          :key="request.summary.id"
          :request="request"
          :scope="scope"
          :authority="requests.authority.value"
          :busy="requests.busy.value"
          :cancel-blocked="Boolean(requests.uncertainCancellation.value)"
          @cancel="requests.cancelling.value = request"
          @retry="requests.load"
        /><p
          v-if="requests.bucket.value.loadingMore"
          role="status"
        >
          Загружаем заявки…
        </p><span
          ref="edge"
          class="student-auto-edge"
          aria-hidden="true"
        /><button
          v-if="more && !supported"
          class="web-button"
          :disabled="requests.bucket.value.loadingMore"
          @click="requests.controller.loadMore()"
        >
          Следующие заявки
        </button>
      </template>
      <ConfirmDialog
        :open="Boolean(requests.cancelling.value)"
        title="Отменить свою заявку?"
        confirm-label="Отменить заявку"
        danger
        :busy="requests.busy.value"
        @cancel="requests.cancelling.value = null"
        @confirm="cancelAndRefresh"
      >
        <template v-if="requests.cancelling.value">
          <p>
            {{ requestKindLabel(requests.cancelling.value.summary.kind) }} ·
            {{ requests.cancelling.value.summary.id }}
          </p><ul>
            <li
              v-for="lesson in requests.cancelling.value.summary.lessons ?? []"
              :key="lesson.id"
            >
              {{ lesson.subjectName }} · {{ formatLessonDate(lesson.date) }}
            </li>
          </ul><p>
            Пары освободятся для новой заявки. Потраченная попытка оспаривания не возвращается.
          </p>
        </template>
      </ConfirmDialog>
    </template>
  </StudentRegion>
</template>
