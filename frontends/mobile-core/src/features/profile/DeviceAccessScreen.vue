<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import {
  useDeviceAccess,
  type DeviceKind,
  type DevicePermission,
} from '../../device-access/device-access';
import type { ProfileResolvedTheme } from './profile-types';
import securityPrevious from './assets/security-previous.svg';
import './profile-screen.pcss';
defineProps<{ theme?: ProfileResolvedTheme }>();
const emit = defineEmits<{ back: [] }>();
const owner = useDeviceAccess();
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
  unsupported: 'Недоступно на этом устройстве',
  unknown: 'Системный доступ неизвестен',
  checking: 'Проверяем доступ…',
  error: 'Не удалось проверить доступ',
};
function toggle(kind: DeviceKind): void {
  if (!owner) return;
  const enabled = !owner.state[kind].enabled;
  owner.setEnabled(kind, enabled);
  if (enabled && kind === 'location') void owner.probe(kind);
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
});
onBeforeUnmount(() => {
  owner?.cancel();
  window.removeEventListener('focus', inspect);
  document.removeEventListener('visibilitychange', visibility);
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
        >Настройки действуют для твоего аккаунта на этом устройстве. Системное разрешение меняется
        отдельно в настройках браузера, Telegram или устройства.</p
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
            @click="toggle(device.kind)"
          >
            {{
              owner.state[device.kind].enabled ? 'Включено в приложении' : 'Выключено в приложении'
            }}
          </button>
          <p
            class="profile-note__body"
            role="status"
            >{{ labels[owner.state[device.kind].permission] }}</p
          >
          <button
            class="profile-primary-button"
            type="button"
            :disabled="
              !owner.state[device.kind].enabled ||
              owner.state[device.kind].permission === 'checking'
            "
            @click="owner.probe(device.kind)"
            >Запросить и проверить доступ</button
          >
          <button
            v-if="owner.state[device.kind].permission === 'checking'"
            class="profile-secondary-button"
            type="button"
            @click="owner.cancel()"
            >Остановить проверку</button
          >
          <p
            v-if="device.kind === 'location' && owner.state.location.permission === 'denied'"
            class="profile-note__body"
            >Если система больше не показывает запрос, разреши геолокацию в настройках сайта или
            устройства. В Telegram используй кнопку настроек ниже, затем вернись и проверь доступ
            снова.</p
          >
          <button
            class="profile-secondary-button"
            type="button"
            @click="owner.inspect(device.kind)"
            >Обновить статус</button
          >
          <button
            v-if="device.kind === 'location' && owner.openLocationSettings"
            class="profile-secondary-button"
            type="button"
            @click="owner.openLocationSettings()"
            >Настройки геолокации Telegram</button
          >
          <p
            v-if="device.kind === 'camera' && owner.scanQr"
            class="profile-note__body"
            >Telegram запрашивает камеру при сканировании QR. Общий статус разрешения камеры он не
            сообщает.</p
          >
        </template>
      </section>
    </div>
  </main>
</template>
