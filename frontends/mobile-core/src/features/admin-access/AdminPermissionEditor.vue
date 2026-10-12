<script setup lang="ts">
import { computed } from 'vue';
import AdminCheckbox from '../../shared/components/AdminCheckbox.vue';
import type {
  AdminAccessCatalog,
  AdminAccessSelection,
  AdminPermission,
} from './admin-access-client';
import './admin-permission-editor.pcss';
const props = defineProps<{
  catalog: AdminAccessCatalog;
  modelValue: AdminAccessSelection;
  disabled?: boolean;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: AdminAccessSelection] }>();
const groupLabels: Record<string, string> = {
  overview: 'Обзор',
  users: 'Пользователи',
  groups: 'Группы',
  semesters: 'Семестры',
  map: 'Карта',
  dangerous: 'Опасные действия',
  administration: 'Администраторы',
};
const groups = computed(() => [
  ...new Set(props.catalog.permissions.map((permission) => permission.group)),
]);
const selected = computed(() => new Set(props.modelValue.permissions));
function closure(codes: readonly string[]): string[] {
  const chosen = new Set<string>();
  function add(code: string): void {
    const permission = props.catalog.permissions.find((item) => item.code === code);
    if (!permission || permission.fullOnly || chosen.has(code)) return;
    chosen.add(code);
    permission.requires.forEach(add);
  }
  codes.forEach(add);
  return [...chosen];
}
function setMode(mode: 'FULL' | 'LIMITED'): void {
  emit('update:modelValue', {
    mode,
    permissions: mode === 'FULL' ? [] : props.modelValue.permissions,
  });
}
function requiredBy(permission: AdminPermission): readonly AdminPermission[] {
  return props.catalog.permissions.filter(
    (item) => selected.value.has(item.code) && item.requires.includes(permission.code),
  );
}
function toggle(permission: AdminPermission, checked: boolean): void {
  if (props.disabled || permission.fullOnly || (!checked && requiredBy(permission).length)) return;
  emit('update:modelValue', {
    mode: 'LIMITED',
    permissions: checked
      ? closure([...props.modelValue.permissions, permission.code])
      : props.modelValue.permissions.filter((code) => code !== permission.code),
  });
}
</script>
<template>
  <section
    class="admin-permission-editor"
    aria-label="Права доступа"
  >
    <fieldset class="admin-permission-editor__mode">
      <legend class="admin-permission-editor__mode-legend">
        Уровень доступа
      </legend><label><input
        type="radio"
        name="admin-access-mode"
        :checked="modelValue.mode === 'LIMITED'"
        :disabled="disabled"
        @change="setMode('LIMITED')"
      ><span>Выбранные права</span></label><label><input
        type="radio"
        name="admin-access-mode"
        :checked="modelValue.mode === 'FULL'"
        :disabled="disabled"
        @change="setMode('FULL')"
      ><span>Полный доступ</span></label>
    </fieldset>
    <p
      v-if="modelValue.mode === 'FULL'"
      class="admin-permission-editor__note"
    >
      Все административные действия, включая назначение и отзыв прав других администраторов.
    </p>
    <template v-else>
      <fieldset
        v-for="group in groups"
        :key="group"
        class="admin-permission-editor__group"
      >
        <legend>{{ groupLabels[group] ?? 'Другие права' }}</legend>
        <AdminCheckbox
          v-for="permission in catalog.permissions.filter((item) => item.group === group)"
          :key="permission.code"
          :code="permission.code"
          :label="permission.label"
          :model-value="selected.has(permission.code)"
          :disabled="disabled || permission.fullOnly || requiredBy(permission).length > 0"
          @update:model-value="toggle(permission, $event)"
        />
      </fieldset>
      <p class="admin-permission-editor__note">
        Выбрано прав: {{ selected.size }}
      </p>
    </template>
  </section>
</template>
