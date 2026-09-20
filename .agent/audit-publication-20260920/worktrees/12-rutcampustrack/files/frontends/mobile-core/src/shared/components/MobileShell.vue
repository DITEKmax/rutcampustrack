<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import MobileBottomNav from './MobileBottomNav.vue'
import type { MobileHostAdapter } from '../host'
import {
  rootRoute,
  type MobileBottomNavItems,
  type MobileNavigationStack,
  type MobileRootRouteId,
  type MobileRoute,
} from '../navigation'
import { shouldShowHostBack, shouldShowMobileDock, shouldShowProductBack } from '../shell-contract'
import './mobile-shell.pcss'

const props = withDefaults(defineProps<{
  route?: MobileRoute | null
  navigation?: MobileNavigationStack | null
  navItems: MobileBottomNavItems
  activeId?: MobileRootRouteId | null
  keyboardVisible?: boolean
  host?: MobileHostAdapter | null
  backLabel?: string
}>(), {
  route: null,
  navigation: null,
  activeId: null,
  // An absent Boolean prop otherwise casts to false and masks host keyboard state.
  keyboardVisible: undefined as never,
  backLabel: 'Назад',
  host: null,
})

const emit = defineEmits<{
  navigate: [route: MobileRootRouteId]
  back: [route: MobileRoute | null]
}>()

const hostKeyboardVisible = ref(false)
const stackRevision = ref(0)
let stopNavigationSubscription: (() => void) | undefined
let stopKeyboardSubscription: (() => void) | undefined
let stopBackSubscription: (() => void) | undefined

const currentRoute = computed<MobileRoute>(() => {
  // The revision makes an owned stack reactive after shell actions. Consumers
  // that own a route prop remain fully controlled and take precedence.
  void stackRevision.value
  return props.route ?? props.navigation?.current ?? rootRoute('today')
})

const activeId = computed(() => props.activeId ?? currentRoute.value.root)
const keyboardVisible = computed(() => props.keyboardVisible ?? hostKeyboardVisible.value)
const dockVisible = computed(() => shouldShowMobileDock(currentRoute.value, keyboardVisible.value))
const productBackVisible = computed(() => shouldShowProductBack(currentRoute.value, props.host?.backOwner ?? 'product'))
const hostBackVisible = computed(() => shouldShowHostBack(currentRoute.value, props.host?.backOwner ?? 'none'))

function navigate(route: MobileRootRouteId): void {
  props.navigation?.goRoot(route)
  emit('navigate', route)
}

function goBack(): void {
  const previous = props.navigation?.back() ?? null
  emit('back', previous)
}

function subscribeNavigation(navigation: MobileNavigationStack | null): void {
  stopNavigationSubscription?.()
  stopNavigationSubscription = navigation?.subscribe(() => {
    stackRevision.value += 1
  })
}

watch(
  () => props.navigation,
  subscribeNavigation,
  { immediate: true },
)

function subscribeHost(host: MobileHostAdapter | null): void {
  stopKeyboardSubscription?.()
  stopBackSubscription?.()
  stopKeyboardSubscription = undefined
  stopBackSubscription = undefined

  hostKeyboardVisible.value = false
  stopKeyboardSubscription = host?.subscribeKeyboard?.((visible) => {
    hostKeyboardVisible.value = visible
  })
  stopBackSubscription = host?.subscribeBack?.(goBack)
}

watch(
  () => props.host,
  subscribeHost,
  { immediate: true },
)

watch(
  () => [props.host, hostBackVisible.value] as const,
  ([host, visible], previous) => {
    const previousHost = previous?.[0]
    if (previousHost && previousHost !== host) previousHost.setBackVisible?.(false)
    host?.setBackVisible?.(visible)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopNavigationSubscription?.()
  stopKeyboardSubscription?.()
  stopBackSubscription?.()
  props.host?.setBackVisible?.(false)
})
</script>

<template>
  <div
    class="mobile-shell"
    :data-surface="currentRoute.surface"
    :data-dock-visible="dockVisible"
    :data-keyboard-visible="keyboardVisible"
  >
    <slot
      name="back"
      :visible="productBackVisible"
      :on-back="goBack"
    >
      <button
        v-if="productBackVisible"
        class="mobile-shell__back"
        type="button"
        @click="goBack"
      >
        {{ backLabel }}
      </button>
    </slot>

    <slot />

    <slot
      v-if="dockVisible"
      name="dock"
      :active-id="activeId"
      :items="navItems"
      :on-navigate="navigate"
    >
      <MobileBottomNav
        :items="navItems"
        :active-id="activeId"
        @navigate="navigate"
      />
    </slot>
  </div>
</template>
