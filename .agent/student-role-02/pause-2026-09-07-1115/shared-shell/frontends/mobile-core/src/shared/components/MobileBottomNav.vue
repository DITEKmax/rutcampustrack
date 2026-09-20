<script setup lang="ts">
import { computed } from 'vue'
import type { MobileBottomNavItem, MobileBottomNavItems, MobileRootRouteId } from '../navigation'
import './mobile-bottom-nav.pcss'

const props = withDefaults(defineProps<{
  items: MobileBottomNavItems
  activeId: MobileRootRouteId
  ariaLabel?: string
}>(), {
  ariaLabel: 'Основная навигация',
})

const emit = defineEmits<{ navigate: [route: MobileRootRouteId] }>()

const slots = computed(() => props.items.length)
const slotsClass = computed(() => `mobile-bottom-nav--slots-${slots.value}`)

function isActive(item: MobileBottomNavItem): boolean {
  return item.id === props.activeId
}

function itemLabel(item: MobileBottomNavItem): string | undefined {
  const label = item.accessibleLabel ?? item.label
  return item.disabledReason ? `${label}. ${item.disabledReason}` : item.accessibleLabel
}

function navigate(item: MobileBottomNavItem): void {
  if (!item.disabled) emit('navigate', item.route)
}
</script>

<template>
  <nav
    class="mobile-bottom-nav"
    :class="slotsClass"
    :data-slots="slots"
    :aria-label="ariaLabel"
  >
    <button
      v-for="item in items"
      :key="item.id"
      class="mobile-bottom-nav__item"
      :class="{ 'mobile-bottom-nav__item--active': isActive(item) }"
      type="button"
      :disabled="item.disabled"
      :aria-current="isActive(item) ? 'page' : undefined"
      :aria-label="itemLabel(item)"
      @click="navigate(item)"
    >
      <span class="mobile-bottom-nav__icon">
        <img
          :src="item.icon"
          alt=""
          aria-hidden="true"
        >
      </span>
      <span class="mobile-bottom-nav__label">{{ item.label }}</span>
      <span
        v-if="item.badge !== undefined"
        class="mobile-bottom-nav__badge"
        aria-hidden="true"
      >{{ item.badge }}</span>
    </button>
  </nav>
</template>
