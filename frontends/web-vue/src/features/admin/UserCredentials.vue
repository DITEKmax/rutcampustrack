<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { webSession } from '../../auth/session';
import { reauthenticationReceipt } from '../../auth/reauthentication-receipt';
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
const revokedSelf = ref(false);
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
          reauthenticationReceipt.show({
            kind: 'credential-outcome-unknown',
            targetLabel: props.user.fullName,
          });
          webSession.invalidate();
          return;
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
          emit('locked', false);
        }
      }
    },
    selfTarget && unknownVersion.value ? undefined : 'USER_CREDENTIALS_MANAGE',
  );
  loading.value = false;
}
async function generate() {
  const version = credentials.value?.credentialsVersion;
  if (!version || unknownVersion.value) return;
  await admin.run(async () => {
    if (!allowed.value)
      throw new Error('Для пароля действующего администратора нужен полный доступ.');
    emit('locked', true);
    try {
      const result = await client.generate(props.user.id, version);
      admin.assertCurrent();
      credentials.value = result;
      confirming.value = false;
      if (self.value) {
        if (!result.password) throw new Error('Новый пароль не подтверждён.');
        reauthenticationReceipt.show({
          kind: 'generated-password',
          targetLabel: props.user.fullName,
          password: result.password,
        });
        emit('locked', false);
        webSession.invalidate();
        return;
      } else notice.value = 'Новый пароль сгенерирован. Предыдущие сессии пользователя отозваны.';
      emit('locked', false);
    } catch (cause) {
      admin.assertCurrent();
      if (!(cause instanceof CredentialsError) || cause.status === null || cause.status >= 500) {
        unknownVersion.value = version;
        error.value = self.value
          ? 'Результат генерации неизвестен. Если сессия отозвана, войди с помощью другого полного администратора или восстановления. Не запускай генерацию повторно.'
          : 'Результат генерации неизвестен. Проверь текущую версию пароля перед новым действием.';
        confirming.value = false;
      } else {
        emit('locked', false);
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
  if (unknownVersion.value || busy.value) {
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
    <template v-if="credentials">
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
      v-if="allowed && !revokedSelf"
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
      v-if="link"
      class="web-notice"
    >
      <p>Ссылка действует до {{ new Date(link.expiresAt).toLocaleString('ru-RU') }}.</p><output class="admin-secret">{{ link.url }}</output><button
        class="web-button"
        @click="copy(link!.url)"
      >
        Копировать ссылку
      </button>
    </div>
    <button
      v-if="revokedSelf"
      class="web-button web-button-primary"
      @click="webSession.refresh()"
    >
      Пароль сохранён — перейти ко входу
    </button>
  </section>
</template>
