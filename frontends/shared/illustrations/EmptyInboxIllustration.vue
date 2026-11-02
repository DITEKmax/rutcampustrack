<script setup lang="ts">
import './empty-inbox-illustration.pcss';
</script>

<template>
  <svg class="rct-empty-inbox-illustration" viewBox="0 0 280 204" fill="none" stroke-width="1.5" aria-hidden="true" focusable="false">
    <path class="rct-empty-inbox-illustration__inside" d="m56 112 23-39h122l23 39-38 26H94Z" />
    <path class="rct-empty-inbox-illustration__fold" d="m56 112 23-39h122l23 39M79 73l15 65m107-65-15 65" />
    <path class="rct-empty-inbox-illustration__front" d="M56 112h52l10 15h44l10-15h52v44a10 10 0 0 1-10 10H66a10 10 0 0 1-10-10Z" />
    <path class="rct-empty-inbox-illustration__handle" d="M121 147h38" stroke-width="2" />
    <path class="rct-empty-inbox-illustration__placeholder" d="M109 26h43l23 23v44h-66Z" stroke-dasharray="3 5" />
    <path class="rct-empty-inbox-illustration__detail" d="M152 26v23h23m-48 12h19m-19 14h30" />
    <circle class="rct-empty-inbox-illustration__accent" cx="207" cy="48" r="4" />
    <path class="rct-empty-inbox-illustration__detail" d="M62 48h8m-4-4v8m159 128h12m-6-6v12" />
  </svg>
</template>
