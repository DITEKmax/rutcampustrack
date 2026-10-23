<script setup lang="ts">
import { roleLabels, webSession } from '../auth/session';
</script>

<template>
  <div
    class="web-role-switcher"
    aria-label="Активная роль"
  >
    <template v-if="webSession.roles.value.length > 1 || !webSession.snapshot.value?.activeRole">
      <button
        v-for="grant in webSession.roles.value"
        :key="grant.grantId"
        class="web-button"
        :class="{ 'web-button-primary': grant.role === webSession.snapshot.value?.activeRole }"
        :aria-pressed="grant.role === webSession.snapshot.value?.activeRole"
        :disabled="webSession.busy.value"
        @click="webSession.selectRole(grant.role)"
      >
        {{ roleLabels[grant.role] }}<span v-if="grant.readOnly"> · Только чтение</span>
      </button>
    </template>
    <span
      v-else-if="webSession.roles.value[0]"
      class="web-role-label"
    >{{ roleLabels[webSession.roles.value[0].role]
    }}<span v-if="webSession.roles.value[0].readOnly"> · Только чтение</span></span>
  </div>
</template>
