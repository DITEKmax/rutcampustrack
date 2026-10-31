<script setup lang="ts">
import { computed, onScopeDispose, ref } from 'vue';
import QRCode from 'qrcode';
import AuthFrame from './AuthFrame.vue';
import AuthMethods from './AuthMethods.vue';
import { qrLoginClient, type QrChallenge } from './qr-login-client';
import { webSession, message } from './session';
const image = ref('');
const state = ref<'loading' | 'pending' | 'confirmed' | 'rejected' | 'expired' | 'error'>(
  'loading',
);
const error = ref('');
const now = ref(Date.now());
let challenge: QrChallenge | undefined;
let revision = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
let controller = new AbortController();
const remaining = computed(() =>
  Math.max(0, Math.ceil(((challenge?.expiresAt ?? 0) - now.value) / 1000)),
);
const clock = setInterval(() => {
  now.value = Date.now();
}, 1000);
async function issue() {
  const owner = ++revision;
  controller.abort();
  controller = new AbortController();
  clearTimeout(timer);
  image.value = '';
  error.value = '';
  state.value = 'loading';
  try {
    const next = await qrLoginClient.issue(challenge, controller.signal);
    const encoded = await QRCode.toDataURL(next.qrPayload, {
      width: 384,
      margin: 2,
      errorCorrectionLevel: 'M',
    });
    if (owner !== revision) return;
    challenge = next;
    image.value = encoded;
    state.value = 'pending';
    now.value = Date.now();
    timer = setTimeout(() => void poll(owner), next.pollAfterSeconds * 1000);
  } catch (cause) {
    if (owner === revision) {
      error.value = message(cause);
      state.value = 'error';
    }
  }
}
async function poll(owner: number) {
  if (owner !== revision || !challenge) return;
  try {
    const status = await qrLoginClient.status(challenge, controller.signal);
    if (owner !== revision) return;
    challenge = {
      ...challenge,
      expiresAt: status.expiresAt,
      pollAfterSeconds: status.pollAfterSeconds,
    };
    now.value = Date.now();
    state.value = status.status;
    if (status.status === 'confirmed') {
      const proof = challenge;
      await webSession.authenticate(
        () => qrLoginClient.exchange(proof, controller.signal),
        () => owner === revision,
      );
      if (owner === revision && webSession.phase.value !== 'ready') {
        error.value =
          webSession.error.value ??
          'Вход не подтверждён. Проверь сессию или выбери другой способ входа.';
        state.value = 'error';
      }
    } else if (status.status === 'pending')
      timer = setTimeout(() => void poll(owner), status.pollAfterSeconds * 1000);
  } catch (cause) {
    if (owner === revision) {
      error.value = message(cause);
      state.value = 'error';
    }
  }
}
onScopeDispose(() => {
  webSession.cancelAuthentication();
  ++revision;
  controller.abort();
  clearTimeout(timer);
  clearInterval(clock);
  challenge = undefined;
  image.value = '';
});
void issue();
</script>
<template>
  <AuthFrame screen="WEB-AUTH-QR">
    <h1>Вход</h1><AuthMethods />
    <div
      class="web-qr-content"
      :aria-busy="state === 'loading' || state === 'confirmed'"
    >
      <img
        v-if="image && state === 'pending'"
        class="web-qr-image"
        :src="image"
        alt="QR-код для входа в этот браузер"
        width="192"
        height="192"
      >
      <p v-if="state === 'pending'">
        Открой сканер в RutTrack на телефоне, отсканируй код и подтверди вход в этот браузер.
      </p>
      <p
        v-if="state === 'pending'"
        class="web-caption"
        role="status"
      >
        Код действует ещё {{ remaining }} с.
      </p>
      <p
        v-if="state === 'loading'"
        role="status"
      >
        Получаем QR-код…
      </p>
      <p
        v-if="state === 'confirmed'"
        role="status"
      >
        Вход подтверждён. Открываем сессию…
      </p>
      <p
        v-if="state === 'rejected'"
        role="status"
      >
        Вход отклонён на телефоне.
      </p>
      <p
        v-if="state === 'expired'"
        role="status"
      >
        Срок действия кода истёк.
      </p>
      <p
        v-if="error"
        class="web-error"
        role="alert"
      >
        {{ error }}
      </p>
      <button
        v-if="['expired', 'rejected', 'error'].includes(state)"
        class="web-button"
        @click="issue"
      >
        Получить новый код
      </button>
    </div>
  </AuthFrame>
</template>
