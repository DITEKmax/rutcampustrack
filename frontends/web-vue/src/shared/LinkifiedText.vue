<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{ text: string }>();
const segments = computed(() => {
  const parts: { text: string; href?: string }[] = [];
  let start = 0;
  for (const match of props.text.matchAll(/https?:\/\/[^\s<>]+/gu)) {
    const index = match.index;
    if (index > start) parts.push({ text: props.text.slice(start, index) });
    const raw = match[0];
    const href = raw.replace(/[.,;!?)]+$/u, '');
    try {
      parts.push({ text: new URL(href).host, href });
    } catch {
      parts.push({ text: href });
    }
    if (href.length < raw.length) parts.push({ text: raw.slice(href.length) });
    start = index + raw.length;
  }
  if (start < props.text.length) parts.push({ text: props.text.slice(start) });
  return parts;
});
</script>

<template>
  <template
    v-for="(part, index) in segments"
    :key="index"
  >
    <a
      v-if="part.href"
      :href="part.href"
      target="_blank"
      rel="noopener noreferrer"
    >{{ part.text }}</a><template v-else>
      {{ part.text }}
    </template>
  </template>
</template>
