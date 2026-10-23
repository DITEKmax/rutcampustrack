<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import type {
  AdminAccessCatalog,
  AdminAccessMode,
  AdminAccessProfile,
} from '../../../../mobile-core/src/features/admin-access/admin-access-client';
import { useAdmin } from './use-admin';
import PermissionFields from './PermissionFields.vue';
import AdminState from './AdminState.vue';
const props = defineProps<{ userId: number }>();
const emit = defineEmits<{ saved: []; locked: [value: boolean] }>();
const admin = useAdmin();
const { loading, busy, error, notice, full } = admin;
const profile = shallowRef<AdminAccessProfile | null>(null);
const catalog = shallowRef<AdminAccessCatalog | null>(null);
const mode = ref<AdminAccessMode>('LIMITED');
const permissions = ref<string[]>([]);
const confirmRevoke = ref(false);
const pending = ref(false);
async function load() {
  const [p, c] = await Promise.all([
    admin.access.getUserAccess(props.userId),
    admin.access.getCatalog(),
  ]);
  admin.assertCurrent();
  profile.value = p;
  catalog.value = c;
  mode.value = p.mode ?? 'LIMITED';
  permissions.value = [...p.permissions];
}
async function mutate(revoke: boolean) {
  await admin.run(async () => {
    if (!profile.value || !full.value) throw new Error('Нужен полный административный доступ.');
    emit('locked', true);
    try {
      if (revoke)
        await admin.access.revokeUserAccess(props.userId, profile.value.version, admin.signal);
      else
        await admin.access.updateUserAccess(
          props.userId,
          {
            mode: mode.value,
            permissions: mode.value === 'FULL' ? [] : permissions.value,
            expectedVersion: profile.value.version,
          },
          admin.signal,
        );
      admin.assertCurrent();
      admin.access.acknowledgeIdentityOperation();
      confirmRevoke.value = false;
      await load();
      notice.value = 'Права обновлены.';
      emit('saved');
    } finally {
      pending.value =
        admin.access.hasPendingIdentityOperation || Boolean(admin.access.failedIdentityOperation);
      emit('locked', pending.value);
    }
  }, 'ADMIN_ACCESS_MANAGE');
}
async function resume() {
  await admin.run(async () => {
    if (admin.access.failedIdentityOperation && !admin.access.hasPendingIdentityOperation) {
      const failed = await admin.access.confirmIdentityRecovery();
      admin.assertCurrent();
      if (!failed) throw new Error('Операция пока не готова к восстановлению.');
      await admin.access.reconcileIdentityOperation(admin.signal);
      admin.assertCurrent();
    }
    await admin.access.resumeIdentityOperation();
    admin.assertCurrent();
    admin.access.acknowledgeIdentityOperation();
    pending.value = false;
    emit('locked', false);
    await load();
    notice.value = 'Результат операции подтверждён.';
    emit('saved');
  });
}
onBeforeRouteLeave(() => {
  if (pending.value || busy.value) {
    error.value = 'Сначала проверь результат изменения прав.';
    return false;
  }
});
onMounted(() => admin.init('ADMIN_ACCESS_MANAGE', load));
</script>
<template>
  <section
    class="admin-panel admin-form"
    aria-label="Административный доступ"
  >
    <h3>Административный доступ</h3><AdminState
      :loading="loading"
      :busy="busy"
      :error="error"
      :notice="notice"
      @retry="admin.run(load)"
    />
    <template v-if="profile && catalog && full">
      <PermissionFields
        v-model:mode="mode"
        v-model:permissions="permissions"
        :catalog="catalog"
        :disabled="busy || pending"
      />
      <div class="admin-actions">
        <button
          class="web-button web-button-primary"
          :disabled="busy || pending || (mode === 'LIMITED' && permissions.length === 0)"
          @click="mutate(false)"
        >
          Сохранить права
        </button><button
          v-if="profile.assigned"
          class="web-button admin-danger"
          :disabled="busy || pending"
          @click="confirmRevoke = true"
        >
          Отозвать доступ
        </button>
      </div>
      <div
        v-if="confirmRevoke"
        class="web-notice"
      >
        <p>Отозвать административные права? Остальные роли пользователя сохранятся.</p><div class="admin-actions">
          <button
            class="web-button"
            @click="confirmRevoke = false"
          >
            Отмена
          </button><button
            class="web-button admin-danger"
            :disabled="busy"
            @click="mutate(true)"
          >
            Подтвердить отзыв
          </button>
        </div>
      </div>
    </template><button
      v-if="pending"
      class="web-button"
      :disabled="busy"
      @click="resume"
    >
      Проверить результат изменения прав
    </button>
  </section>
</template>
