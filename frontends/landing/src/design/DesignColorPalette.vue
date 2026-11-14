<script setup lang="ts">
import { usePaletteDrag } from './use-palette-drag'
import './design-color-palette.pcss'

const { onPointerDown, onPointerMove, onPointerEnd, onTouchStart, onTouchMove, onTouchEnd, finish, onContextMenu } =
  usePaletteDrag()

// Each chip uses its theme token, including the student background in light mode.
const swatches = [
  { id: 'brand', hex: '#B79CFA', size: 'primary', theme: 'dark' },
  { id: 'base', hex: '#0B0B0C', size: 'primary', theme: 'dark' },
  { id: 'raised', hex: '#1C1922', size: 'primary', theme: 'dark' },
  { id: 'text', hex: '#EDEAF3', size: 'primary', theme: 'dark' },
  { id: 'light-base', hex: '#F2F2F4', size: 'secondary', theme: 'light' },
  { id: 'float', hex: '#292531', size: 'secondary', theme: 'dark' },
  { id: 'secondary-text', hex: '#C8C2D6', size: 'secondary', theme: 'dark' },
  { id: 'light-accent', hex: '#6D28D9', size: 'secondary', theme: 'light' },
  { id: 'present', hex: '#61DFB8', size: 'occasional', theme: 'dark' },
  { id: 'excused', hex: '#F5B443', size: 'occasional', theme: 'dark' },
  { id: 'absent', hex: '#F27F7F', size: 'occasional', theme: 'dark' },
  { id: 'focus', hex: '#9E7AFF', size: 'occasional', theme: 'dark' },
] as const
</script>

<template>
  <section id="design-palette" class="design-chapter" aria-labelledby="design-palette-title">
    <div class="landing-shell">
      <header class="design-chapter-heading">
        <h2 id="design-palette-title">Палитра проекта</h2>
      </header>
      <ul class="design-color-palette" role="list"
        @pointerdown="onPointerDown" @pointermove="onPointerMove" @pointerup="onPointerEnd"
        @pointercancel="onPointerEnd" @lostpointercapture="onPointerEnd"
        @touchstart.passive="onTouchStart" @touchmove="onTouchMove" @touchend="onTouchEnd"
        @touchcancel="finish" @contextmenu="onContextMenu">
        <li v-for="swatch in swatches" :key="swatch.id" class="design-color-palette__swatch"
          :data-swatch="swatch.id" :data-size="swatch.size" :data-theme="swatch.theme">
          <code>{{ swatch.hex }}</code>
        </li>
      </ul>
    </div>
  </section>
</template>
