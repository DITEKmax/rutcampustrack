<script setup lang="ts">
import { onScopeDispose, ref, watch } from 'vue';
import MobileIcon from '../../../mobile-core/src/shared/components/MobileIcon.vue';

const props = defineProps<{ notice: { message: string; tone?: 'success' | 'error' | 'warning' } | null }>();
const visible = ref(false);
const revision = ref(0);
let timer: ReturnType<typeof setTimeout> | undefined;

function dismiss() {
  clearTimeout(timer);
  timer = undefined;
  visible.value = false;
}
watch(() => props.notice, (notice) => {
  dismiss();
  if (!notice) return;
  revision.value++;
  visible.value = true;
  if (!notice.tone || notice.tone === 'success') timer = setTimeout(dismiss, 5_000);
}, { immediate: true });
onScopeDispose(dismiss);
</script>

<template>
  <Teleport to="body">
    <div class="web-feedback-host" role="status" aria-live="polite" aria-atomic="true">
      <div v-if="visible && notice" :key="revision" class="web-feedback-toast" :data-tone="notice.tone ?? 'success'">
        <span class="web-feedback-symbol" aria-hidden="true">{{ notice.tone === 'error' ? '×' : notice.tone === 'warning' ? '!' : '✓' }}</span>
        <p>{{ notice.message }}</p>
        <button class="web-button web-icon-button" type="button" aria-label="Закрыть сообщение" title="Закрыть сообщение" @click="dismiss">
          <MobileIcon name="close" />
        </button>
      </div>
    </div>
  </Teleport>
</template>
