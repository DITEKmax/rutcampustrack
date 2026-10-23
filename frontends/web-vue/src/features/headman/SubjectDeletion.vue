<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue';
import type { HeadmanFeature } from './context';
import { message, webSession } from '../../auth/session';
import {
  SubjectDeletionClient,
  SubjectDeletionError,
  type SubjectDeletionPreview,
  type SubjectDeletionChallenge,
  type SubjectDeletionOperation,
} from './subject-deletion-client';
const props = defineProps<{ feature: HeadmanFeature; subjectId: number; semesterId: number }>();
const emit = defineEmits<{ completed: []; close: [] }>();
const api = new SubjectDeletionClient(props.feature.transport);
const preview = shallowRef<SubjectDeletionPreview | null>(null),
  challenge = shallowRef<SubjectDeletionChallenge | null>(null),
  operation = shallowRef<SubjectDeletionOperation | null>(null);
const busy = ref(false),
  error = ref<string | null>(null),
  password = ref(''),
  code = ref('');
const operationId = ref<string>(crypto.randomUUID()),
  key = ref<string>(crypto.randomUUID()),
  sent = ref(false);
const storageKey = `rct:web:headman:subject-deletion:${webSession.identity.value}:${props.subjectId}`;
function accept(value: SubjectDeletionOperation) {
  props.feature.assertCurrent();
  if (value.subjectId !== String(props.subjectId) || value.semesterId !== String(props.semesterId))
    throw new Error('Операция относится к другому предмету или семестру.');
  operation.value = value;
  if (value.phase === 'COMPLETED' || value.phase === 'CANCELLED') {
    sessionStorage.removeItem(storageKey);
    if (value.phase === 'COMPLETED') emit('completed');
  }
}
async function load() {
  busy.value = true;
  try {
    const raw = sessionStorage.getItem(storageKey);
    if (raw) {
      const value: unknown = JSON.parse(raw);
      if (
        !value ||
        typeof value !== 'object' ||
        !('operationId' in value) ||
        typeof value.operationId !== 'string' ||
        !('key' in value) ||
        typeof value.key !== 'string'
      )
        throw new Error('Не удалось прочитать исходную операцию удаления.');
      operationId.value = value.operationId;
      key.value = value.key;
      sent.value = true;
      accept(await api.status(operationId.value));
      return;
    }
    const value = await api.preview(props.subjectId);
    props.feature.assertCurrent();
    if (
      value.groupId !== String(props.feature.groupId) ||
      value.semesterId !== String(props.semesterId)
    )
      throw new Error('Удаление относится к другому семестру или группе.');
    preview.value = value;
  } catch (cause) {
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
async function requestCode() {
  if (!props.feature.writable() || !preview.value || busy.value || sent.value) return;
  busy.value = true;
  error.value = null;
  try {
    const value = await api.challenge(
      props.subjectId,
      operationId.value,
      preview.value.previewDigest,
      password.value,
    );
    props.feature.assertCurrent();
    challenge.value = value;
  } catch (cause) {
    error.value = message(cause);
  } finally {
    password.value = '';
    busy.value = false;
  }
}
async function confirm() {
  const proof = challenge.value,
    snapshot = preview.value;
  if (!props.feature.writable() || !proof || !snapshot || busy.value || sent.value) return;
  if (Date.parse(proof.expiresAt) <= Date.now()) {
    error.value = 'Код истёк. Запроси новый код после проверки пароля.';
    challenge.value = null;
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    sessionStorage.setItem(
      storageKey,
      JSON.stringify({ operationId: operationId.value, key: key.value }),
    );
    sent.value = true;
    accept(
      await api.confirm(props.subjectId, key.value, {
        operationId: operationId.value,
        previewDigest: snapshot.previewDigest,
        challengeId: proof.challengeId,
        code: code.value,
      }),
    );
  } catch (cause) {
    if (cause instanceof SubjectDeletionError && [400, 401, 403, 422].includes(cause.status)) {
      sent.value = false;
      sessionStorage.removeItem(storageKey);
      error.value = message(cause);
    } else
      error.value = `${message(cause)}. Проверяй исходную операцию; удаление повторно не отправляется.`;
  } finally {
    code.value = '';
    challenge.value = null;
    busy.value = false;
  }
}
async function check() {
  if (busy.value) return;
  busy.value = true;
  try {
    accept(await api.status(operationId.value));
    error.value = null;
  } catch (cause) {
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section
    class="headman-panel web-stack"
    :aria-busy="busy"
    ><h2>Удаление предмета</h2
    ><p
      v-if="error"
      class="web-error"
      role="alert"
      >{{ error }}</p
    ><template v-if="preview"
      ><h3>{{ preview.subjectName }}</h3
      ><p
        >Будут удалены данные этого предмета в семестре №{{ preview.semesterId }}: учебных записей
        {{ preview.academicRows }}, записей расписания {{ preview.scheduleRows }}, посещаемости
        {{ preview.attendanceRows }}. Смешанных заявок: {{ preview.mixedTickets }}; пары других
        предметов в них сохранятся.</p
      ><p class="web-notice"
        >Действие необратимо. Подтверди пароль и введи код из бота для этого предмета.</p
      ><form
        v-if="!challenge && !sent"
        class="headman-toolbar"
        @submit.prevent="requestCode"
        ><label class="web-field"
          >Твой текущий пароль<input
            v-model="password"
            type="password"
            autocomplete="current-password"
            maxlength="256"
            required /></label
        ><button
          class="web-button"
          :disabled="busy || !feature.writable()"
          >Получить код</button
        ></form
      ><form
        v-if="challenge && !sent"
        class="headman-toolbar"
        @submit.prevent="confirm"
        ><label class="web-field"
          >Код из бота<input
            v-model="code"
            type="text"
            inputmode="numeric"
            pattern="[0-9]{6}"
            maxlength="6"
            autocomplete="one-time-code"
            required /></label
        ><p
          >Код действует до {{ new Date(challenge.expiresAt).toLocaleTimeString('ru-RU') }} ·
          попыток {{ challenge.attemptsRemaining }}</p
        ><button
          class="web-button"
          :disabled="busy || !feature.writable()"
          >Удалить «{{ preview.subjectName }}»</button
        ></form
      ></template
    ><template v-if="sent"
      ><p role="status">{{
        operation
          ? {
              PREPARING: 'Подготовка удаления',
              SEALED: 'Удаление подготовлено',
              COMMITTING: 'Удаление выполняется',
              COMPLETED: 'Предмет удалён',
              RELEASING: 'Отмена подготовки',
              CANCELLED: 'Удаление отменено',
            }[operation.phase]
          : 'Результат пока неизвестен'
      }}</p
      ><p v-if="operation?.reason">{{ operation.reason }}</p
      ><button
        v-if="!operation || !['COMPLETED', 'CANCELLED'].includes(operation.phase)"
        class="web-button"
        :disabled="busy"
        @click="check"
        >Проверить состояние удаления</button
      ></template
    ><button
      class="web-button"
      :disabled="busy"
      @click="$emit('close')"
      >Закрыть</button
    ></section
  >
</template>
