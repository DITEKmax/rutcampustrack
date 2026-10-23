<script setup lang="ts">
import { computed, ref } from 'vue';
import { canShowFeature, webFeatures } from '../app/features';
import { webSession } from '../auth/session';
import ThemeSelect from '../shared/ThemeSelect.vue';
import RoleSwitcher from './RoleSwitcher.vue';

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
const navigation = computed(() => webFeatures.filter(canShowFeature));
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
          ☰
        </button>
        <RouterLink
          class="web-brand"
          to="/"
          aria-label="RutTrack — главная"
        />
        <div class="web-header-role">
          <RoleSwitcher />
        </div>
        <div class="web-header-actions">
          <ThemeSelect />
          <div class="web-profile">
            <button
              class="web-button"
              :aria-label="`Профиль: ${webSession.snapshot.value?.displayName}`"
              :aria-expanded="profile"
              aria-controls="web-profile-menu"
              @click="profile = !profile"
            >
              {{ initials }}
            </button>
            <div
              v-if="profile"
              id="web-profile-menu"
              class="web-profile-menu web-stack"
              @keydown.esc="profile = false"
            >
              <RouterLink
                class="web-button"
                :to="{ name: 'settings' }"
                @click="profile = false"
              >
                Настройки
              </RouterLink>
              <span>{{ webSession.snapshot.value?.groupLabel }}</span>
              <button
                class="web-button"
                :disabled="webSession.busy.value"
                @click="webSession.logout"
              >
                Выйти
              </button>
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
        <RouterLink
          v-for="feature in navigation"
          :key="feature.path"
          :to="feature.path"
          class="web-button"
          @click="expanded = false"
        >
          {{ feature.meta?.label }}
        </RouterLink>
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
      <RouterView
        v-if="webSession.snapshot.value"
        :key="webSession.generation.value"
      />
    </main>
  </div>
</template>
