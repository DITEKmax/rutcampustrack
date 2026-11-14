<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import headmanMore from './art/surface-return/headman-more.webp';
import headmanProfile from './art/surface-return/headman-profile.webp';
import headmanSecurity from './art/surface-return/headman-security.webp';
import './landing-device-showcase.pcss';

const screens = [
  {
    placement: 'left',
    src: headmanMore,
    alt: 'Экран «Ещё» старосты: группа, предметы и управление расписанием',
    caption: 'PWA · разделы старосты',
  },
  {
    placement: 'center',
    src: headmanProfile,
    alt: 'Профиль старосты: данные аккаунта, настройки и активные сеансы',
    caption: 'PWA · профиль',
  },
  {
    placement: 'right',
    src: headmanSecurity,
    alt: 'Экран «Безопасность» с формой смены пароля',
    caption: 'PWA · безопасность',
  },
] as const;

const screenList = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | undefined;
let motion: MediaQueryList | undefined;

function stopReveal() {
  observer?.disconnect();
  screenList.value?.removeAttribute('data-showcase-motion');
}

onMounted(() => {
  const list = screenList.value;
  if (!list || !('IntersectionObserver' in window)) return;

  motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  if (motion.matches) return;

  const visibility = new IntersectionObserver(entries => {
    for (const { isIntersecting, target } of entries) {
      if (!isIntersecting) continue;
      target.setAttribute('data-visible', '');
      visibility.unobserve(target);
    }
  }, { rootMargin: '0px 0px -24px' });

  observer = visibility;
  motion.addEventListener('change', stopReveal, { once: true });
  list.setAttribute('data-showcase-motion', '');
  for (const screen of list.children) visibility.observe(screen);
});

onBeforeUnmount(() => {
  motion?.removeEventListener('change', stopReveal);
  stopReveal();
});
</script>

<template>
  <section
    id="interfaces"
    class="landing-section surface-return"
    aria-label="Экраны PWA"
  >
    <span id="devices" class="surface-return__anchor" aria-hidden="true"></span>
    <div class="landing-shell">
      <div class="surface-return__stage">
        <div ref="screenList" class="surface-return__screens">
          <figure
            v-for="screen in screens"
            :key="screen.placement"
            class="surface-return__screen"
            :class="'surface-return__screen--' + screen.placement"
          >
            <div class="surface-return__frame">
              <img
                :src="screen.src"
                :alt="screen.alt"
                width="780"
                height="1688"
                loading="lazy"
                decoding="async"
              />
            </div>
            <figcaption>{{ screen.caption }}</figcaption>
          </figure>
        </div>

        <div class="surface-return__callout surface-return__callout--role">
          <span class="surface-return__badge" aria-hidden="true">
            <svg class="landing-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="8" r="3" />
              <path d="M5 20v-1a5 5 0 0 1 5-5h4a5 5 0 0 1 5 5v1" />
            </svg>
          </span>
          <strong>Своя роль — свой интерфейс</strong>
        </div>

        <div class="surface-return__callout surface-return__callout--access">
          <span class="surface-return__badge" aria-hidden="true">
            <svg class="landing-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3 5 6v5c0 4.4 2.7 7.7 7 10 4.3-2.3 7-5.6 7-10V6l-7-3Z" />
              <path d="m9 12 2 2 4-4" />
            </svg>
          </span>
          <strong>Доступ под контролем</strong>
        </div>
      </div>
    </div>
  </section>
</template>
