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
      Изменение пароля · {{ reauthenticationReceipt.value.value.targetLabel }}
    </h2>
    <output
      v-if="reauthenticationReceipt.value.value.kind === 'generated-password'"
      class="web-password-receipt"
    >{{ reauthenticationReceipt.value.value.password }}</output>
    <p
      v-else
      role="alert"
    >
      Результат неизвестен. Войди снова или восстанови доступ. Не повторяй генерацию до проверки доступа.
    </p>
    <button
      class="web-button"
      @click="reauthenticationReceipt.clear"
    >
      Закрыть
    </button>
  </section>
</template>
