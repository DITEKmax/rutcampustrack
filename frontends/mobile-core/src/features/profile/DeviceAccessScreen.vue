<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import {
  useDeviceAccess,
  type DeviceCheck,
  type DeviceKind,
  type DevicePermission,
} from '../../device-access/device-access';
import type { ProfileResolvedTheme } from './profile-types';
import securityPrevious from './assets/security-previous.svg';
import './profile-screen.pcss';
defineProps<{ theme?: ProfileResolvedTheme }>();
const emit = defineEmits<{ back: [] }>();
const owner = useDeviceAccess();
const settingsMessage = ref('');
const browserMessage = ref('');
let stopDeviceReturn: (() => void) | undefined;
const devices = [
  {
    kind: 'location',
    title: 'Геолокация',
    purpose: 'Для отметки на паре. Проверка доступа не отправляет отметку или заявку.',
  },
  {
    kind: 'camera',
    title: 'Камера',
    purpose: 'Для QR при входе в браузере. Проверка камеры не подтверждает вход.',
  },
] as const;
const labels: Record<DevicePermission, string> = {
  prompt: 'Доступ ещё не запрошен',
  granted: 'Доступ разрешён системой',
  denied: 'Доступ запрещён системой',
  unsupported: 'Разрешение недоступно в этом приложении или браузере',
  unknown: 'Системное разрешение неизвестно',
};
const checkLabels: Record<Exclude<DeviceCheck, 'granted'>, string> = {
  checking: 'Проверяем доступ…',
  denied: 'В доступе отказано',
  unsupported: 'Устройство или доступ недоступны',
  cancelled: 'Проверка прервана',
  unconfirmed: 'Запрос закрыт или доступ не выдан',
  timeout: 'Не дождались ответа на запрос',
  error: 'Не удалось проверить доступ',
};
function checkLabel(kind: DeviceKind, check: DeviceCheck | null): string {
  if (!check) return '';
  if (check !== 'granted') return checkLabels[check];
  if (kind === 'location') return 'Геопозиция получена';
  return owner?.openBrowserApp
    ? 'Камера в мини-приложении работает. Проверка завершена.'
    : 'Камера работает. Проверка завершена.';
}
function nativeLocation(): boolean {
  return owner?.state.location.source === 'TMA';
}
function showHelp(kind: DeviceKind): boolean {
  const device = owner?.state[kind];
  if (!device || device.check === 'checking') return false;
  return (
    device.permission === 'denied' ||
    device.permission === 'unsupported' ||
    (device.check !== null && device.check !== 'granted')
  );
}
function openLocationSettings(): void {
  if (!owner?.openLocationSettings) return;
  settingsMessage.value =
    'Если Telegram открыл настройки, разреши геолокацию и вернись для проверки. Если экран остался прежним, открой настройки устройства вручную.';
  try {
    if (!owner.openLocationSettings())
      settingsMessage.value =
        'Настройки геолокации Telegram сейчас недоступны. Проверь разрешения в настройках устройства.';
  } catch {
    settingsMessage.value =
      'Telegram не смог открыть настройки. Разреши геолокацию в настройках устройства.';
  }
}
function openBrowserApp(): void {
  if (!owner?.openBrowserApp) return;
  browserMessage.value =
    'Если браузер не открылся, открой сайт приложения в обычном браузере и войди.';
  try {
    owner.openBrowserApp();
  } catch {
    browserMessage.value = 'Открой сайт приложения в обычном браузере и войди.';
  }
}
function inspect(): void {
  void owner?.inspect('location');
  void owner?.inspect('camera');
}
function visibility(): void {
  if (document.visibilityState !== 'hidden') inspect();
}
onMounted(() => {
  inspect();
  window.addEventListener('focus', inspect);
  document.addEventListener('visibilitychange', visibility);
  stopDeviceReturn = owner?.onDeviceReturn?.(visibility);
});
onBeforeUnmount(() => {
  owner?.cancel();
  window.removeEventListener('focus', inspect);
  document.removeEventListener('visibilitychange', visibility);
  stopDeviceReturn?.();
});
</script>

<template>
  <main
    class="profile-screen"
    :data-theme="theme"
    aria-labelledby="device-access-title"
  >
    <div class="profile-screen__content">
      <header class="profile-header">
        <button
          class="profile-back"
          type="button"
          aria-label="Назад"
          @click="emit('back')"
        >
          <SvgIcon
            :src="securityPrevious"
            aria-hidden="true"
          />
        </button>
        <h1
          id="device-access-title"
          class="profile-header__title"
          >Доступ к устройству</h1
        >
      </header>
      <p class="profile-note__body"
        >Переключатель задаёт, будет ли приложение использовать устройство в твоём аккаунте.
        Разрешения выдаются в браузере, Telegram или настройках устройства.</p
      >
      <section
        v-for="device in devices"
        :key="device.kind"
        class="profile-note"
        :aria-labelledby="`device-${device.kind}`"
      >
        <h2
          :id="`device-${device.kind}`"
          class="profile-note__title"
          >{{ device.title }}</h2
        >
        <p class="profile-note__body">{{ device.purpose }}</p>
        <template v-if="owner">
          <button
            class="profile-row"
            type="button"
            role="switch"
            :aria-label="`${device.title}: использовать в приложении`"
            :aria-checked="owner.state[device.kind].enabled"
            @click="owner.setEnabled(device.kind, !owner.state[device.kind].enabled)"
          >
            {{
              owner.state[device.kind].enabled ? 'Включено в приложении' : 'Выключено в приложении'
            }}
          </button>
          <div role="status">
            <p class="profile-note__body">{{ labels[owner.state[device.kind].permission] }}</p>
            <p
              v-if="owner.state[device.kind].check"
              class="profile-note__body"
            >
              {{ checkLabel(device.kind, owner.state[device.kind].check) }}
            </p>
          </div>
          <p
            v-if="device.kind === 'location' && owner.openBrowserApp && !nativeLocation()"
            class="profile-note__body"
          >
            Этот клиент Telegram использует геолокацию браузера.
          </p>
          <button
            class="profile-primary-button"
            type="button"
            :disabled="!owner.state[device.kind].enabled || owner.state.checking !== null"
            @click="owner.probe(device.kind)"
            >Проверить доступ</button
          >
          <p
            v-if="device.kind === 'camera' && owner.scanQr"
            class="profile-note__body"
          >
            Здесь проверяется камера мини-приложения. Встроенный сканер Telegram открывается в
            разделе «Вход в браузере».
          </p>
          <button
            v-if="owner.state.checking === device.kind"
            class="profile-secondary-button"
            type="button"
            @click="owner.cancel()"
            >Остановить проверку</button
          >
          <button
            v-if="
              device.kind === 'location' &&
              nativeLocation() &&
              owner.state.location.permission === 'denied' &&
              owner.openLocationSettings
            "
            class="profile-secondary-button"
            type="button"
            :disabled="owner.state.checking !== null"
            @click="openLocationSettings"
            >Настройки геолокации Telegram</button
          >
          <p
            v-if="device.kind === 'location' && settingsMessage"
            class="profile-note__body"
            role="status"
          >
            {{ settingsMessage }}
          </p>
          <template v-if="showHelp(device.kind) || (device.kind === 'location' && settingsMessage)">
            <p
              v-if="device.kind === 'location' && nativeLocation()"
              class="profile-note__body"
            >
              На телефоне открой настройки Telegram в разделе «Приложения» или «Конфиденциальность и
              безопасность». На компьютере открой системные настройки «Конфиденциальность и
              безопасность» → «Расположение» или «Службы геолокации». Разреши доступ Telegram или
              приложениям для компьютера, если система предлагает этот пункт. Затем вернись и
              повтори проверку. Если Telegram не отвечает, закрой и заново открой мини-приложение.
            </p>
            <p
              v-else
              class="profile-note__body"
            >
              В браузере открой сведения о сайте рядом с адресом → разрешения сайта. Разреши
              {{ device.kind === 'location' ? 'геолокацию' : 'камеру' }}. Проверь разрешения
              браузера и включи устройство в настройках телефона или компьютера. Если приложение
              установлено, открой этот же сайт в браузере.
            </p>
            <button
              v-if="owner.openBrowserApp"
              class="profile-secondary-button"
              type="button"
              @click="openBrowserApp"
              >Открыть веб-приложение в браузере</button
            >
          </template>
        </template>
      </section>
      <p
        v-if="browserMessage"
        class="profile-note__body"
        role="status"
        >{{ browserMessage }}</p
      >
    </div>
  </main>
</template>
