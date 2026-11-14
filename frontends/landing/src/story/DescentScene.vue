<script setup lang="ts">
import './descent-scene.pcss';

const layers = Array.from({ length: 10 }, (_, index) => index + 1);
const doors = ['left', 'right'] as const;
</script>

<template>
  <div
    class="descent-scene"
    data-descent
    aria-hidden="true"
  >
    <div
      class="descent-stage"
      data-descent-stage
    >
      <div class="descent-world">
        <div class="descent-camera">
          <div class="descent-vault">
            <div class="descent-shaft">
              <span class="descent-wall descent-wall--left"></span>
              <span class="descent-wall descent-wall--right"></span>
              <span class="descent-wall descent-wall--ceiling"></span>
              <span class="descent-wall descent-wall--floor"></span>
              <span
                v-for="layer in layers"
                :key="layer"
                class="descent-layer"
                :style="{ '--descent-layer': layer }"
                ><i></i><i></i><i></i><i></i
              ></span>
              <div class="descent-exit"><i></i></div>
            </div>
            <div class="descent-frame"></div>
            <div
              v-for="door in doors"
              :key="door"
              class="descent-door"
              :class="'descent-door--' + door"
            >
              <div class="descent-door__face">
                <span class="descent-door__panel"></span>
                <span class="descent-door__lock"></span>
                <span class="descent-door__bolts"></span>
              </div>
              <div class="descent-door__edge"></div>
              <div class="descent-door__rear"></div>
            </div>
          </div>
        </div>
      </div>
      <div class="descent-vignette"></div>
    </div>
  </div>
</template>
