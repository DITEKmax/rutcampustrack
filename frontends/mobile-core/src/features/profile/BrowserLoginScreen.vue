<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import BrowserLoginApprovalForm from './BrowserLoginApprovalForm.vue';
import type { BrowserLoginApprovalPort } from './browser-login-approval';
import {
  createBrowserLoginCamera,
  createBrowserQrDecoder,
  type CameraState,
} from './browser-login-scanner';
import type { ProfileResolvedTheme } from './profile-types';
import securityPrevious from './assets/security-previous.svg';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import './profile-screen.pcss';

const props = defineProps<{
  port: BrowserLoginApprovalPort | null;
  offline: boolean;
  theme: ProfileResolvedTheme;
  onBack?: (() => void | Promise<void>) | undefined;
  ownerKey: string | null;
}>();
const video = ref<HTMLVideoElement | null>(null);
const approval = ref<InstanceType<typeof BrowserLoginApprovalForm> | null>(null);
const cameraState = ref<CameraState>('idle');
const approvalBusy = ref(false);
const cameraMessage = ref('');
const interrupted = ref(false);
function allowed(): boolean {
  return (
    !!props.ownerKey &&
    !!props.port?.isOnline() &&
    !props.offline &&
    !interrupted.value &&
    !approvalBusy.value &&
    (typeof document === 'undefined' || document.visibilityState !== 'hidden')
  );
}
const camera = createBrowserLoginCamera({
  allowed,
  decoder: createBrowserQrDecoder,
  acquire: () => {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('Camera unavailable');
    return navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' } },
      audio: false,
    });
  },
  video: async () => {
    await nextTick();
    return video.value;
  },
  state: (state) => {
    cameraState.value = state;
  },
  scanned: async (value) => {
    cameraMessage.value = '';
    await approval.value?.inspectScan(value);
  },
  failed: (error) => {
    cameraMessage.value =
      error instanceof Error && error.name === 'NotAllowedError'
        ? 'Доступ к камере не разрешён. Разреши его в настройках браузера и попробуй снова.'
        : 'Камера или сканирование QR недоступны. Проверь камеру и попробуй снова.';
  },
});
function startCamera(): void {
  if (cameraState.value !== 'idle' || approvalBusy.value) return;
  interrupted.value = false;
  if (!allowed()) return;
  approval.value?.clear();
  cameraMessage.value = '';
  void camera.start();
}
function stopCamera(): void {
  camera.stop();
}
function interrupt(): void {
  interrupted.value = true;
  stopCamera();
  approval.value?.clear();
}
function visibility(): void {
  if (document.visibilityState === 'hidden') interrupt();
}
function back(): void {
  interrupt();
  void props.onBack?.();
}
watch(
  () => [props.port, props.ownerKey, props.offline],
  () => {
    interrupt();
    cameraMessage.value = '';
  },
  { flush: 'sync' },
);
onMounted(() => {
  document.addEventListener('visibilitychange', visibility);
  window.addEventListener('blur', interrupt);
  window.addEventListener('pagehide', interrupt);
  window.addEventListener('offline', interrupt);
});
onBeforeUnmount(() => {
  interrupt();
  document.removeEventListener('visibilitychange', visibility);
  window.removeEventListener('blur', interrupt);
  window.removeEventListener('pagehide', interrupt);
  window.removeEventListener('offline', interrupt);
});
</script>

<template>
  <main
    class="profile-screen profile-browser-page"
    :data-theme="theme"
    aria-labelledby="browser-page-title"
  >
    <div class="profile-screen__content">
      <header class="profile-header">
        <button
          class="profile-back"
          type="button"
          aria-label="Назад"
          @click="back"
        >
          <SvgIcon
            :src="securityPrevious"
            aria-hidden="true"
          />
        </button>
        <h1
          id="browser-page-title"
          class="profile-header__title"
        >
          Вход в браузере
        </h1>
      </header>
      <StudentWarningBlock
        v-if="offline || !port || !port.isOnline()"
        title="Нет подключения"
        message="Для проверки QR и подтверждения входа нужно подключение. Вернись сюда после подключения."
      />
      <template v-else>
        <section
          class="profile-note profile-browser-scanner"
          aria-labelledby="browser-scan-title"
          :aria-busy="cameraState === 'starting'"
        >
          <h2
            id="browser-scan-title"
            class="profile-note__title"
          >
            Сканируй QR с экрана компьютера
          </h2>
          <video
            v-if="cameraState === 'scanning'"
            ref="video"
            class="profile-browser-scanner__video"
            autoplay
            muted
            playsinline
            aria-label="Камера для сканирования QR"
          />
          <button
            v-if="cameraState === 'idle'"
            class="profile-primary-button"
            type="button"
            :disabled="approvalBusy || !ownerKey"
            @click="startCamera"
          >
            {{ cameraMessage ? 'Попробовать снова' : 'Сканировать QR' }}
          </button>
          <template v-else>
            <p
              v-if="cameraState === 'starting'"
              class="profile-note__body"
              role="status"
            >
              Открываем камеру…
            </p>
            <button
              class="profile-secondary-button"
              type="button"
              @click="stopCamera"
            >
              Остановить камеру
            </button>
          </template>
          <p
            v-if="cameraMessage"
            class="profile-note__body"
            role="status"
          >
            {{ cameraMessage }}
          </p>
        </section>
        <BrowserLoginApprovalForm
          scan-only
          :key="ownerKey ?? ''"
          ref="approval"
          :port="port"
          :owner-key="ownerKey"
          @busy="approvalBusy = $event"
          @close="back"
        />
      </template>
    </div>
  </main>
</template>
