<script setup lang="ts">
defineProps<{ state: 'idle' | 'pending' | 'error' | 'success' }>();
</script>
<template>
  <div
    class="illustration"
    :data-state="state"
    aria-hidden="true"
    ><svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 224 136"
      fill="none"
    >
      <ellipse
        class="ill-orbit"
        cx="112"
        cy="66"
        rx="101"
        ry="43"
        transform="rotate(-12 112 66)"
        stroke-dasharray="3 7"
      />
      <g class="ill-satellite">
        <circle
          cx="204"
          cy="43"
          r="4"
          fill="currentColor"
        />
        <circle
          cx="29"
          cy="93"
          r="2.5"
          fill="currentColor"
          opacity=".5"
        />
      </g>
      <path
        class="ill-flow"
        d="M49 81C68 122 158 118 182 67"
        stroke-width="1.5"
      />
      <g
        class="ill-card-a"
        transform="rotate(-10 75 64)"
      >
        <rect
          class="ill-plate"
          x="36"
          y="22"
          width="76"
          height="91"
          rx="16"
          stroke-width="1.5"
        />
        <path
          class="ill-line"
          d="M53 80h40M53 88h25"
          stroke-width="3"
          stroke-linecap="round"
        />
        <path
          d="m54 51 20-8 20 8-20 8-20-8Zm8 6v10c7 5 17 5 24 0V57m8-6v15"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linejoin="round"
        />
      </g>
      <g
        class="ill-card-b"
        transform="rotate(8 151 62)"
      >
        <rect
          class="ill-plate"
          x="119"
          y="21"
          width="66"
          height="83"
          rx="16"
          stroke-width="1.5"
        />
        <path
          class="ill-line"
          d="M135 83h33"
          stroke-width="3"
          stroke-linecap="round"
        />
        <path
          d="m171 38-6 30-13-10-8 7 2-12 25-15Zm0 0-25 15-12-4 37-11Z"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linejoin="round"
        />
      </g>
      <g class="ill-signal">
        <path
          d="M108 38h6m-8 9h9m-8 9h7"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
        />
      </g>
      <circle
        class="ill-badge"
        cx="121"
        cy="103"
        r="20"
      />
      <g
        class="ill-status"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <g class="ill-idle">
          <path d="M117 100v-3a4 4 0 0 1 8 0v3" />
          <rect
            x="114"
            y="100"
            width="14"
            height="11"
            rx="3"
          />
          <path d="M121 104v3" />
        </g>
        <g class="ill-success">
          <path
            class="ill-check"
            d="m112 103 6 6 12-13"
          />
        </g>
        <g class="ill-error"><path d="m116 98 10 10m0-10-10 10" /></g>
      </g></svg
  ></div>
</template>
