<script setup lang="ts">
import { reauthenticationReceipt } from './reauthentication-receipt'
</script>

<template>
  <section
    v-if="reauthenticationReceipt.value.value"
    class="web-panel web-reauthentication-receipt web-stack"
    aria-labelledby="reauth-receipt-title"
  >
    <h2 id="reauth-receipt-title">
      Новый пароль · {{ reauthenticationReceipt.value.value.targetLabel }}
    </h2>
    <output class="web-password-receipt">{{ reauthenticationReceipt.value.value.password }}</output>
    <button
      class="web-button"
      @click="reauthenticationReceipt.clear"
    >
      Закрыть
    </button>
  </section>
</template>
