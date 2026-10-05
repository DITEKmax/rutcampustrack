<script setup lang="ts">
import { computed } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import type { ProfileResolvedTheme, ProfileRoute } from './profile-types'
import moreMap from './assets/more-map.svg'
import moreRequests from './assets/more-requests.svg'
import moreStatistics from './assets/more-statistics.svg'
import './profile-screen.pcss'

const props = withDefaults(defineProps<{
  onNavigate?: ((route: ProfileRoute) => void | Promise<void>) | undefined
  assistantEnabled?: boolean
  mapEnabled?: boolean
  theme?: ProfileResolvedTheme
  onNotifications?: (() => void) | undefined
  unreadCount?: number | null
}>(), {
  onNavigate: undefined,
  assistantEnabled: false,
  mapEnabled: false,
  theme: 'dark',
  onNotifications: undefined,
  unreadCount: null,
})

const visibleUnreadCount = computed(() => typeof props.unreadCount === 'number' && Number.isFinite(props.unreadCount) && props.unreadCount > 0 ? Math.floor(props.unreadCount) : null)
const notificationsLabel = computed(() => visibleUnreadCount.value ? `Уведомления, непрочитанных: ${visibleUnreadCount.value}` : 'Уведомления')

const routes = computed(() => [
  { route: 'schedule' as const, label: 'Расписание семестра', icon: null, disabled: false, disabledReason: null },
  { route: 'statistics' as const, label: 'Статистика', icon: moreStatistics, disabled: false, disabledReason: null },
  {
    route: 'map' as const,
    label: 'Карта',
    icon: moreMap,
    disabled: !props.mapEnabled,
    disabledReason: props.mapEnabled ? null : 'Раздел пока недоступен',
  },
  { route: 'requests' as const, label: 'Заявки', icon: moreRequests, disabled: false, disabledReason: null },
  ...(props.assistantEnabled
    ? [{ route: 'assistant' as const, label: 'Действия помощника', icon: moreRequests, disabled: false, disabledReason: null }]
    : []),
])

function navigate(route: ProfileRoute): void {
  if (routes.value.find((item) => item.route === route)?.disabled) return
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
        :disabled="item.disabled"
        :aria-disabled="item.disabled"
        :title="item.disabled ? item.disabledReason || undefined : undefined"
        @click="navigate(item.route)"
      >
        <MobileIcon
          v-if="item.route === 'schedule'"
          name="calendar"
          class="profile-icon"
        />
        <MobileIcon
          v-else-if="item.route === 'assistant'"
          name="assistant"
          class="profile-icon"
        />
        <img
          v-else
          class="profile-icon"
          :src="item.icon ?? undefined"
          alt=""
          aria-hidden="true"
        >
        <span>{{ item.label }}</span>
      </button>
      <button
        v-if="onNotifications"
        class="profile-more-row"
        type="button"
        :aria-label="notificationsLabel"
        @click="onNotifications()"
      >
        <span class="profile-notification-icon">
          <MobileIcon
            name="bell"
            class="profile-icon"
          />
          <span
            v-if="visibleUnreadCount"
            class="profile-notification-badge"
            aria-hidden="true"
          >{{ visibleUnreadCount > 99 ? '99+' : visibleUnreadCount }}</span>
        </span>
        <span>Уведомления</span>
      </button>
    </div>
  </main>
</template>
