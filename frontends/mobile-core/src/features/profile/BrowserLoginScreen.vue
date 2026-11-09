<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import BrowserLoginApprovalForm from './BrowserLoginApprovalForm.vue';
import type { BrowserLoginApprovalPort } from './browser-login-approval';
import {
  createBrowserLoginCamera,
  createBrowserQrDecoder,
  cameraErrorMessage,
  type CameraState,
} from './browser-login-scanner';
import type { ProfileResolvedTheme } from './profile-types';
import securityPrevious from './assets/security-previous.svg';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import { useDeviceAccess } from '../../device-access/device-access';
import { requestBrowserCamera } from '../../device-access/browser-camera';
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
const deviceAccess = useDeviceAccess();
const nativeUnavailable = ref(false);
let nativeStop: (() => void) | null = null;
function allowed(): boolean {
  return (
    !!props.ownerKey &&
    !!props.port?.isOnline() &&
    !props.offline &&
    !interrupted.value &&
    !approvalBusy.value &&
    deviceAccess?.state.camera.enabled !== false &&
    (typeof document === 'undefined' || document.visibilityState !== 'hidden')
  );
}
const camera = createBrowserLoginCamera({
  allowed,
  decoder: createBrowserQrDecoder,
  acquire: () => {
    return requestBrowserCamera({ facingMode: { ideal: 'environment' } });
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
    cameraMessage.value = cameraErrorMessage(error);
  },
});
function startCamera(): void {
  if (cameraState.value !== 'idle' || approvalBusy.value) return;
  interrupted.value = false;
  if (!allowed()) return;
  approval.value?.clear();
  cameraMessage.value = '';
  if (deviceAccess?.scanQr && !nativeUnavailable.value) {
    cameraState.value = 'scanning';
    nativeStop = deviceAccess.scanQr(
      (value) => {
        nativeStop = null;
        cameraState.value = 'idle';
        if (allowed()) void approval.value?.inspectScan(value);
      },
      () => {
        nativeStop = null;
        cameraState.value = 'idle';
      },
      (error) => {
        nativeStop = null;
        cameraState.value = 'idle';
        nativeUnavailable.value = true;
        cameraMessage.value = cameraErrorMessage(error);
      },
    );
    if (nativeStop) return;
    cameraState.value = 'idle';
  }
  void camera.start();
}
function stopCamera(): void {
  nativeStop?.();
  nativeStop = null;
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
function blur(): void {
  // Browser permission prompts blur the page while getUserMedia is still pending.
  if (cameraState.value !== 'starting' && !nativeStop) interrupt();
}
function back(): void {
  interrupt();
  void props.onBack?.();
}
watch(
  () => [props.port, props.ownerKey, props.offline],
  () => {
    interrupt();
    nativeUnavailable.value = false;
    cameraMessage.value = '';
  },
  { flush: 'sync' },
);
onMounted(() => {
  document.addEventListener('visibilitychange', visibility);
  window.addEventListener('blur', blur);
  window.addEventListener('pagehide', interrupt);
  window.addEventListener('offline', interrupt);
});
const unregisterStop = deviceAccess?.registerStop(interrupt);
onBeforeUnmount(() => {
  interrupt();
  unregisterStop?.();
  document.removeEventListener('visibilitychange', visibility);
  window.removeEventListener('blur', blur);
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
            v-if="cameraState === 'scanning' && !nativeStop"
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
            :disabled="approvalBusy || !ownerKey || deviceAccess?.state.camera.enabled === false"
            @click="startCamera"
          >
            {{
              nativeUnavailable
                ? 'Сканировать камерой браузера'
                : cameraMessage
                  ? 'Попробовать снова'
                  : 'Сканировать QR'
            }}
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
        <p
          v-if="deviceAccess?.state.camera.enabled === false"
          class="profile-note__body"
          >Камера выключена в приложении. Включи её в профиле: «Доступ к устройству».</p
        >
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
