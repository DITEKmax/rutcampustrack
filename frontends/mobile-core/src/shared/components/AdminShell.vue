<script setup lang="ts">
import { computed } from 'vue';
import MobileShell from './MobileShell.vue';
import {
  adminCanNavigate,
  adminDestination,
  adminRootId,
  createAdminNavigationItems,
} from '../admin-navigation';
import type { AdminAccessProfile } from '../../features/admin-access/admin-access-client';
import type { AdminRoleRoute } from '../admin-navigation';
import type { MobileHostAdapter } from '../host';
import { nestedRoute, rootRoute, type MobileRootRouteId } from '../navigation';
import './admin-shell.pcss';

const props = withDefaults(
  defineProps<{
    active: AdminRoleRoute;
    authority?: AdminAccessProfile | null;
    task?: boolean;
    host?: MobileHostAdapter | null;
  }>(),
  { authority: null, task: false, host: null },
);
const emit = defineEmits<{ navigate: [route: AdminRoleRoute]; back: [] }>();
const items = computed(() => createAdminNavigationItems(props.authority));
const route = computed(() => {
  const root = adminRootId(props.active);
  return props.task ||
    props.active === 'semesters' ||
    props.active === 'map' ||
    props.active === 'map-statistics'
    ? nestedRoute(root, `${root}/${props.active}`, 'task')
    : rootRoute(root);
});
function navigate(id: MobileRootRouteId): void {
  const destination = adminDestination(id);
  if (destination && adminCanNavigate(props.authority, destination)) emit('navigate', destination);
}
</script>

<template>
  <MobileShell
    class="admin-shell"
    :data-back-owner="host?.backOwner ?? 'product'"
    :route="route"
    :nav-items="items"
    :host="host"
    @navigate="navigate"
    @back="emit('back')"
  >
    <template #back>
      <div class="admin-shell__back-slot" />
    </template>
    <slot />
  </MobileShell>
</template>
