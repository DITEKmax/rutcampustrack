<script setup lang="ts">
import type {
  AdminAccessCatalog,
  AdminAccessMode,
} from '../../../../mobile-core/src/features/admin-access/admin-access-client';
const props = defineProps<{ catalog: AdminAccessCatalog; disabled?: boolean }>();
const mode = defineModel<AdminAccessMode>('mode', { required: true });
const permissions = defineModel<string[]>('permissions', { required: true });
function toggle(code: string, checked: boolean) {
  const selected = new Set(permissions.value);
  function add(value: string) {
    if (selected.has(value)) return;
    selected.add(value);
    props.catalog.permissions.find((p) => p.code === value)?.requires.forEach(add);
  }
  if (checked) add(code);
  else {
    selected.delete(code);
    let changed = true;
    while (changed) {
      changed = false;
      for (const p of props.catalog.permissions)
        if (selected.has(p.code) && p.requires.some((r) => !selected.has(r))) {
          selected.delete(p.code);
          changed = true;
        }
    }
  }
  permissions.value = [...selected];
}
</script>
<template>
  <fieldset
    class="admin-checklist"
    :disabled="disabled"
  >
    <legend>Административные права</legend>
    <label class="web-field">Профиль<select v-model="mode"><option value="LIMITED">Ограниченный</option><option value="FULL">Полный доступ</option></select></label>
    <p
      v-if="mode === 'FULL'"
      class="web-caption"
    >
      Все административные операции. Последний полный администратор защищён от снятия прав.
    </p>
    <template v-else>
      <label
        v-for="permission in catalog.permissions.filter((p) => !p.fullOnly)"
        :key="permission.code"
        class="admin-check"
      ><input
        type="checkbox"
        :checked="permissions.includes(permission.code)"
        @change="toggle(permission.code, ($event.target as HTMLInputElement).checked)"
      ><span>{{ permission.label
      }}<span class="admin-subtext">{{ permission.description }}</span></span></label>
    </template>
  </fieldset>
</template>
