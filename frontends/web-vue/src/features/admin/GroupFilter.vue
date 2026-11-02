<script setup lang="ts">
import type { AdminUsersClient } from '../../../../mobile-core/src/features/admin-users/admin-users-client';
import AdminGroupCombobox from './AdminGroupCombobox.vue';
defineProps<{ client: AdminUsersClient; assertCurrent(): void; disabled?: boolean; }>();
const model = defineModel<number | undefined>({ default: undefined });
</script>
<template>
  <AdminGroupCombobox v-model="model" :client="client" :assert-current="assertCurrent" :disabled="disabled" label="Группа" allow-all allow-archived />
</template>
