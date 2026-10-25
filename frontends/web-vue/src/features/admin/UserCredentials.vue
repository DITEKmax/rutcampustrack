<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { webSession } from '../../auth/session';
import {
  beginSelfCredentialHandoff,
  type SelfCredentialHandoff,
} from '../../auth/reauthentication-receipt';
import {
  AdminUsersClient,
  type AdminUser,
  type AdminRecoveryLink,
} from '../../../../mobile-core/src/features/admin-users/admin-users-client';
import {
  CredentialsClient,
  CredentialsError,
  type GeneratedCredentials,
} from './credentials-client';
import { useAdmin } from './use-admin';
import AdminState from './AdminState.vue';
const props = defineProps<{ user: AdminUser }>();
const emit = defineEmits<{ locked: [value: boolean] }>();
const admin = useAdmin();
const { loading, busy, error, notice, full } = admin;
const client = new CredentialsClient(admin.options);
const users = new AdminUsersClient(admin.options);
const credentials = shallowRef<GeneratedCredentials | null>(null);
const link = shallowRef<AdminRecoveryLink | null>(null);
const confirming = ref(false);
const unknownVersion = ref<string | null>(null);
const handingOff = ref(false);
let selfHandoff: SelfCredentialHandoff | null = null;
function releaseSelfHandoff() {
  handingOff.value = true;
  confirming.value = false;
  unknownVersion.value = null;
  credentials.value = null;
  link.value = null;
  // Only the admitted self intent calls this before retiring its original session.
  emit('locked', false);
}
onBeforeUnmount(() => selfHandoff?.cancel());
watch(
  () => !handingOff.value && (busy.value || !!unknownVersion.value),
  (locked) => emit('locked', locked),
  { immediate: true },
);
admin.guardUnfinished(() => !handingOff.value && (busy.value || !!unknownVersion.value));
const selfTarget = String(props.user.id) === webSession.snapshot.value?.userId;
const self = computed(() => String(props.user.id) === webSession.snapshot.value?.userId);
const allowed = computed(
  () => full.value || !props.user.roles.some((r) => r.role === 'ADMIN' && r.status === 'ACTIVE'),
);
async function read() {
  await admin.run(
    async () => {
      let value: GeneratedCredentials;
      try {
        value = await client.read(props.user.id);
      } catch (cause) {
        if (
          selfTarget &&
          unknownVersion.value &&
          cause instanceof CredentialsError &&
          cause.status === 401
        ) {
          if (selfHandoff?.unknown()) {
            webSession.invalidate();
            return;
          }
        }
        throw cause;
      }
      admin.assertCurrent();
      credentials.value = value;
      if (unknownVersion.value) {
        notice.value =
          BigInt(value.credentialsVersion) > BigInt(unknownVersion.value)
            ? 'Версия пароля изменилась. Показаны текущие данные; повторная генерация не выполнялась.'
            : 'Версия пароля не изменилась. Предыдущая попытка не подтверждена; автоматического повтора не будет.';
        // A read showing the old version cannot prove that an in-flight mutation has stopped.
        if (BigInt(value.credentialsVersion) > BigInt(unknownVersion.value)) {
          unknownVersion.value = null;
          selfHandoff?.cancel();
          selfHandoff = null;
        }
      }
    },
    selfTarget && unknownVersion.value ? undefined : 'USER_CREDENTIALS_MANAGE',
  );
}
async function generate() {
  const version = credentials.value?.credentialsVersion;
  if (!version || unknownVersion.value) return;
  await admin.run(async () => {
    if (!allowed.value)
      throw new Error('Для пароля действующего администратора нужен полный доступ.');
    selfHandoff?.cancel();
    selfHandoff = null;
    if (self.value)
      selfHandoff = beginSelfCredentialHandoff(
        String(props.user.id),
        props.user.fullName,
        releaseSelfHandoff,
      );
    try {
      const result = await client.generate(props.user.id, version);
      admin.assertCurrent();
      confirming.value = false;
      if (selfHandoff) {
        if (!result.password || !selfHandoff.confirmed(result.password))
          throw new Error('Новый пароль не подтверждён.');
        webSession.invalidate();
        return;
      }
      credentials.value = result;
      notice.value = 'Новый пароль сгенерирован. Предыдущие сессии пользователя отозваны.';
    } catch (cause) {
      admin.assertCurrent();
      if (!(cause instanceof CredentialsError) || cause.status === null || cause.status >= 500) {
        unknownVersion.value = version;
        error.value = self.value
          ? 'Результат генерации неизвестен. Если сессия отозвана, войди с помощью другого полного администратора или восстановления. Не запускай генерацию повторно.'
          : 'Результат генерации неизвестен. Проверь текущую версию пароля перед новым действием.';
        confirming.value = false;
      } else {
        selfHandoff?.cancel();
        selfHandoff = null;
        throw cause;
      }
    }
  }, 'USER_CREDENTIALS_MANAGE');
}
async function recovery() {
  await admin.run(async () => {
    if (!allowed.value) throw new Error('Для действующего администратора нужен полный доступ.');
    link.value = null;
    // No retry after an ambiguous POST: a second link would invalidate the first one.
    const value = await users.issueRecoveryLink(props.user.id);
    admin.assertCurrent();
    link.value = value;
  }, 'USER_CREDENTIALS_MANAGE');
}
async function copy(value: string) {
  await admin.run(async () => {
    await navigator.clipboard.writeText(value);
    admin.assertCurrent();
    notice.value = 'Скопировано.';
  });
}
onBeforeRouteLeave(() => {
  if (!handingOff.value && (unknownVersion.value || busy.value)) {
    error.value = 'Сначала проверь результат генерации пароля.';
    return false;
  }
});
void admin.init('USER_CREDENTIALS_MANAGE', async () => {});
</script>
<template>
  <section
    class="admin-panel admin-form"
    aria-label="Доступ пользователя"
  >
    <h3>Пароль и восстановление</h3><AdminState
      :loading="loading"
      :busy="busy"
      :error="error"
      :notice="notice"
      @retry="read"
    />
    <p v-if="!allowed && !loading">
      Для пароля действующего администратора нужен полный доступ.
    </p>
    <template v-if="credentials && allowed && admin.can('USER_CREDENTIALS_MANAGE')">
      <p v-if="credentials.state === 'USER_CHANGED'">
        Пользователь изменил пароль. Его пароль не раскрывается.
      </p><p v-else-if="credentials.state === 'UNAVAILABLE'">
        Сгенерированный пароль недоступен.
      </p><div
        v-else
        class="admin-actions"
      >
        <output
          class="admin-secret"
          aria-label="Текущий сгенерированный пароль"
        >{{ credentials.password }}</output><button
          class="web-button"
          @click="copy(credentials!.password!)"
        >
          Копировать пароль
        </button>
      </div>
    </template>
    <div
      v-if="allowed && admin.can('USER_CREDENTIALS_MANAGE')"
      class="admin-actions"
    >
      <button
        class="web-button"
        :disabled="busy"
        @click="read"
      >
        {{ unknownVersion ? 'Проверить результат генерации' : 'Показать текущий пароль' }}
      </button><button
        class="web-button"
        :disabled="busy || !credentials || !!unknownVersion"
        @click="confirming = true"
      >
        Сгенерировать новый пароль
      </button><button
        class="web-button"
        :disabled="busy || !!unknownVersion"
        @click="recovery"
      >
        Выдать ссылку восстановления
      </button>
    </div>
    <div
      v-if="confirming"
      class="web-notice"
    >
      <h4>Заменить пароль?</h4><p>
        Текущий пароль перестанет действовать, все сессии пользователя будут отозваны.<template
          v-if="self"
        >
          Ты также выйдешь из своего аккаунта. Сохрани новый пароль после ответа.
        </template>
      </p><div class="admin-actions">
        <button
          class="web-button"
          :disabled="busy"
          @click="confirming = false"
        >
          Отмена
        </button><button
          class="web-button web-button-danger"
          :disabled="busy"
          @click="generate"
        >
          Заменить пароль
        </button>
      </div>
    </div>
    <div
      v-if="link && allowed && admin.can('USER_CREDENTIALS_MANAGE')"
      class="web-notice"
    >
      <p>Ссылка действует до {{ new Date(link.expiresAt).toLocaleString('ru-RU') }}.</p><output class="admin-secret">{{ link.url }}</output><button
        class="web-button"
        @click="copy(link!.url)"
      >
        Копировать ссылку
      </button>
    </div>
  </section>
</template>
