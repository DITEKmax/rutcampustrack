<script setup lang="ts">
import NotificationHost from '../features/notifications/NotificationHost.vue';
import { computed, ref } from 'vue';
import { canShowFeature, webFeatures } from '../app/features';
import { webSession } from '../auth/session';
import ThemeSelect from '../shared/ThemeSelect.vue';
import RoleSwitcher from './RoleSwitcher.vue';
import { useRoute } from 'vue-router';
import AssistantRouteBoundary from './AssistantRouteBoundary.vue';
import MobileIcon from '../../../mobile-core/src/shared/components/MobileIcon.vue';
import SvgIcon from '../../../shared/foundation/SvgIcon.vue';
import menuIcon from '../shared/assets/header-menu.svg';
import logoutIcon from '../shared/assets/log-out.svg';
import WebAvatar from '../shared/WebAvatar.vue';
import { webOwnProfile } from '../features/profile/own-profile';

const route = useRoute();
const expanded = ref(false);
const profile = ref(false);
const initials = computed(
  () =>
    webSession.snapshot.value?.displayName
      .split(/\s+/u)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join('') ?? '',
);
const studentNavigationOrder = [
  'student-home', 'student-attendance', 'student-statistics', 'student-homework',
  'map', 'student-requests', 'student-schedule',
];
const headmanNavigationOrder = [
  'headman-home', 'headman-attendance', 'headman-statistics', 'headman-homework',
  'map', 'headman-requests', 'headman-group', 'headman-subjects',
  'headman-schedule', 'headman-lessons',
];
const teacherNavigationOrder = ['teacher-home', 'teacher-journal', 'teacher-stats', 'map'];
const adminNavigationOrder = ['admin-dashboard', 'admin-users', 'admin-groups', 'admin-semesters', 'admin-map'];
const navigation = computed(() => {
  const role = webSession.snapshot.value?.activeRole;
  const visible = webFeatures.filter(feature => canShowFeature(feature) && !(role === 'ADMIN' && feature.name === 'map'));
  const order = role === 'STUDENT' ? studentNavigationOrder : role === 'HEADMAN' ? headmanNavigationOrder : role === 'TEACHER' ? teacherNavigationOrder : role === 'ADMIN' ? adminNavigationOrder : null;
  if (!order) return visible;
  const rank = (name: unknown) => {
    const index = order.indexOf(String(name));
    return index < 0 ? order.length : index;
  };
  return visible.sort((left, right) => rank(left.name) - rank(right.name));
});
</script>

<template>
  <a
    class="web-skip"
    href="#main"
  >Пропустить навигацию</a>
  <div class="web-shell">
    <header class="web-header">
      <div class="web-header-row">
        <button
          class="web-button web-menu-toggle"
          :aria-expanded="expanded"
          aria-controls="web-navigation"
          aria-label="Разделы"
          @click="expanded = !expanded"
        >
          <SvgIcon :src="menuIcon" />
        </button>
        <RouterLink
          class="web-brand"
          to="/"
          aria-label="RutTrack — главная"
        ><span class="web-brand-monogram" aria-hidden="true">РТ</span></RouterLink>
        <div class="web-header-role">
          <RoleSwitcher />
        </div>
        <div class="web-header-actions">
          <NotificationHost v-if="webSession.snapshot.value?.activeRole" />
          <ThemeSelect />
          <div class="web-profile">
            <button
              class="web-button web-profile-trigger"
              :aria-label="`Профиль: ${webSession.snapshot.value?.displayName}`"
              :aria-expanded="profile"
              aria-controls="web-profile-menu"
              @click="profile = !profile"
            >
              <span class="web-profile-avatar"><WebAvatar :avatar-id="webOwnProfile?.own.value?.avatarId" :initials="initials" /></span>
              <MobileIcon name="chevron-down" />
            </button>
            <div
              v-if="profile"
              id="web-profile-menu"
              class="web-profile-menu web-stack"
              @keydown.esc="profile = false"
            >
              <span v-if="webSession.snapshot.value?.groupLabel" class="web-caption web-profile-group">{{ webSession.snapshot.value.groupLabel }}</span>
              <div class="web-profile-actions">
              <RouterLink
                class="web-button web-icon-button"
                aria-label="Настройки"
                title="Настройки"
                :to="{ name: 'settings' }"
                @click="profile = false"
              >
                <MobileIcon name="settings" />
              </RouterLink>
              <button
                class="web-button web-icon-button"
                aria-label="Выйти"
                title="Выйти"
                :disabled="webSession.busy.value"
                @click="webSession.logout"
              >
                <SvgIcon :src="logoutIcon" :width="20" :height="20" />
              </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <nav
        id="web-navigation"
        class="web-navigation"
        :class="{ 'is-expanded': expanded }"
        aria-label="Разделы"
      >
        <div class="web-nav-role">
          <RoleSwitcher />
        </div>
        <template
          v-for="feature in navigation"
          :key="feature.path"
        >
          <span
            v-if="webSession.snapshot.value?.activeRole === 'HEADMAN' && feature.name === 'headman-group'"
            class="web-nav-management-divider"
            aria-hidden="true"
          />
          <RouterLink
            :to="feature.path"
            class="web-button"
            @click="expanded = false"
          >
            {{ feature.meta?.label }}
          </RouterLink>
        </template>
      </nav>
    </header>
    <main
      id="main"
      class="web-main"
      tabindex="-1"
    >
      <p
        v-if="webSession.error.value"
        class="web-error"
        role="alert"
      >
        {{ webSession.error.value }}
      </p>
      <p
        v-if="webSession.snapshot.value?.readOnly"
        class="web-notice"
        role="status"
      >
        Только чтение
      </p>
      <AssistantRouteBoundary
        v-if="webSession.snapshot.value"
        :permission="route.meta.assistantPermission"
      >
        <RouterView :key="webSession.generation.value" />
      </AssistantRouteBoundary>
    </main>
  </div>
</template>
