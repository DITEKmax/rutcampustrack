<script setup lang="ts">
import type { AdminUsersClient } from '../../../../mobile-core/src/features/admin-users/admin-users-client';
import AdminGroupCombobox from './AdminGroupCombobox.vue';
defineProps<{ client: AdminUsersClient; assertCurrent(): void; disabled?: boolean; label: string; }>();
const model = defineModel<number | undefined>({ default: undefined });
</script>
<template>
  <AdminGroupCombobox v-model="model" :client="client" :assert-current="assertCurrent" :disabled="disabled" :label="label" />
</template>
