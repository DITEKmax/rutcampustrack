<script setup lang="ts">
import type { ProfileResolvedTheme, ProfileRoute } from './profile-types'
import moreMap from './assets/more-map.svg'
import moreRequests from './assets/more-requests.svg'
import moreStatistics from './assets/more-statistics.svg'
import './profile-screen.pcss'

const props = withDefaults(defineProps<{
  onNavigate?: (route: ProfileRoute) => void | Promise<void>
  theme?: ProfileResolvedTheme
}>(), {
  onNavigate: undefined,
  theme: 'dark',
})

const routes = [
  { route: 'statistics', label: 'Статистика', icon: moreStatistics },
  { route: 'map', label: 'Карта', icon: moreMap },
  { route: 'requests', label: 'Заявки', icon: moreRequests },
] as const satisfies readonly { route: Extract<ProfileRoute, 'statistics' | 'map' | 'requests'>; label: string; icon: string }[]

function navigate(route: ProfileRoute): void {
  void props.onNavigate?.(route)
}
</script>

<template>
  <main
    class="profile-screen profile-more"
    :data-theme="theme"
    aria-labelledby="profile-more-title"
  >
    <div class="profile-screen__content">
      <h1
        id="profile-more-title"
        class="profile-screen__title"
      >
        Ещё
      </h1>

      <button
        v-for="item in routes"
        :key="item.route"
        class="profile-more-row"
        type="button"
        @click="navigate(item.route)"
      >
        <img
          class="profile-icon"
          :src="item.icon"
          alt=""
          aria-hidden="true"
        >
        <span>{{ item.label }}</span>
      </button>
    </div>
  </main>
</template>
