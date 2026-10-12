<script setup lang="ts">
import { computed } from 'vue';
import type { AdminAccessProfile } from '../admin-access/admin-access-client';
import MobileBottomNav from '../../shared/components/MobileBottomNav.vue';
import {
  adminDestination,
  adminRootId,
  createAdminNavigationItems,
} from '../../shared/admin-navigation';
import type { AdminRoleRoute } from '../../shared/admin-navigation';
import type { MobileRootRouteId } from '../../shared/navigation';

export type { AdminRoleRoute } from '../../shared/admin-navigation';
const props = defineProps<{ active: AdminRoleRoute; authority?: AdminAccessProfile | null }>();
const emit = defineEmits<{ navigate: [route: AdminRoleRoute] }>();
const items = computed(() => createAdminNavigationItems(props.authority));
function navigate(id: MobileRootRouteId): void {
  const destination = adminDestination(id);
  if (destination) emit('navigate', destination);
}
</script>

<template>
  <MobileBottomNav
    :items="items"
    :active-id="adminRootId(active)"
    aria-label="Разделы администратора"
    @navigate="navigate"
  />
</template>
