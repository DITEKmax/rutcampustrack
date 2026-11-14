<script setup lang="ts">
import './campus-floor-plans.pcss'

const assetBase = `${import.meta.env.BASE_URL}campus-floor-plans/`
const floorNumbers = [0, 1, 2, 3, 4, 5]
const groups = ['original', 'copy'] as const
</script>

<template>
  <section id="campus-floor-plans" class="rct-story" aria-labelledby="campus-floor-plans-title">
    <header class="rct-story__chapter">
      <h2 id="campus-floor-plans-title" class="rct-story__wrap">Поэтажные планы в Figma</h2>
    </header>
    <div
      class="campus-plans__viewport"
      role="region"
      aria-labelledby="campus-floor-plans-title"
      tabindex="0"
    >
      <div class="campus-plans__rail">
        <ul
          v-for="group in groups"
          :key="group"
          class="campus-plans__group"
          :aria-hidden="group === 'copy' ? true : undefined"
          role="list"
        >
          <li v-for="floor in floorNumbers" :key="floor">
            <img
              :src="`${assetBase}1-${floor}.svg`"
              :alt="group === 'original' ? `План корпуса 1, этаж ${floor}` : ''"
              width="7945"
              height="2904"
              decoding="async"
            />
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>
