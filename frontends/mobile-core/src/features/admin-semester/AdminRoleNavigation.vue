<script setup lang="ts">
import './admin-role-navigation.pcss'

export type AdminRoleRoute = 'home' | 'map' | 'semesters' | 'users' | 'groups'

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
      :aria-current="active === 'home' ? 'page' : undefined"
      :class="{ 'admin-role-navigation__item--active': active === 'home' }"
      @click="emit('navigate', 'home')"
    >
      Главная
    </button>
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
    <button
      type="button"
      :aria-current="active === 'users' ? 'page' : undefined"
      :class="{ 'admin-role-navigation__item--active': active === 'users' }"
      @click="emit('navigate', 'users')"
    >
      Пользователи
    </button>
    <button
      type="button"
      :aria-current="active === 'groups' ? 'page' : undefined"
      :class="{ 'admin-role-navigation__item--active': active === 'groups' }"
      @click="emit('navigate', 'groups')"
    >
      Группы
    </button>
  </nav>
</template>
