<script setup lang="ts">
import './admin-role-navigation.pcss'

export type AdminRoleRoute = 'map' | 'semesters'

defineProps<{
  active: AdminRoleRoute
}>()

const emit = defineEmits<{
  navigate: [route: AdminRoleRoute]
}>()
</script>

<template>
  <nav
    class="admin-role-navigation"
    aria-label="Разделы администратора"
  >
    <button
      type="button"
      :aria-current="active === 'map' ? 'page' : undefined"
      :class="{ 'admin-role-navigation__item--active': active === 'map' }"
      @click="emit('navigate', 'map')"
    >
      Карта
    </button>
    <button
      type="button"
      :aria-current="active === 'semesters' ? 'page' : undefined"
      :class="{ 'admin-role-navigation__item--active': active === 'semesters' }"
      @click="emit('navigate', 'semesters')"
    >
      Семестры
    </button>
  </nav>
</template>
