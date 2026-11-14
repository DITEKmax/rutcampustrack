<template>
  <svg
    class="hero-orbit__art"
    viewBox="0 0 1200 420"
    aria-hidden="true"
  >
    <defs>
      <linearGradient
        id="hero-orbit-gradient"
        x1="0"
        y1="0"
        x2="1"
        y2="1"
      >
        <stop stop-color="#2f1160" />
        <stop
          offset=".48"
          stop-color="#b79cfa"
        />
        <stop
          offset=".65"
          stop-color="#edeaf3"
        />
        <stop
          offset="1"
          stop-color="#2f1160"
        />
      </linearGradient>
      <filter id="hero-orbit-glow"><feGaussianBlur stdDeviation="7" /></filter>
      <radialGradient id="hero-orbit-disc">
        <stop stop-color="#5f348b" />
        <stop
          offset=".75"
          stop-color="#21172f"
        />
        <stop
          offset="1"
          stop-color="#b79cfa"
          stop-opacity=".6"
        />
      </radialGradient>
    </defs>
    <g transform="translate(600 215) rotate(-10)">
      <g class="hero-orbit__ring hero-orbit__ring--outer">
        <ellipse
          rx="565"
          ry="139"
          fill="none"
          stroke="#b79cfa"
          stroke-opacity=".07"
        />
      </g>
      <g class="hero-orbit__ring hero-orbit__ring--middle">
        <ellipse
          rx="460"
          ry="114"
          fill="none"
          stroke="#b79cfa"
          stroke-opacity=".14"
        />
      </g>
      <g class="hero-orbit__ring hero-orbit__ring--signal">
        <ellipse
          rx="359"
          ry="91"
          fill="none"
          stroke="url(#hero-orbit-gradient)"
          stroke-width="3"
        />
        <ellipse
          rx="359"
          ry="91"
          fill="none"
          stroke="#b79cfa"
          stroke-width="12"
          opacity=".27"
          filter="url(#hero-orbit-glow)"
        />
        <circle
          cx="359"
          cy="0"
          r="5"
          fill="#edeaf3"
        />
        <circle
          cx="-284"
          cy="-56"
          r="5"
          fill="#b79cfa"
        />
      </g>
      <g class="hero-orbit__ring hero-orbit__ring--inner">
        <ellipse
          rx="258"
          ry="65"
          fill="none"
          stroke="#b79cfa"
          stroke-opacity=".3"
          stroke-dasharray="1 8"
        />
      </g>
      <circle
        r="104"
        fill="url(#hero-orbit-disc)"
        opacity=".26"
        transform="scale(1 .57)"
      />
      <circle
        r="104"
        fill="none"
        stroke="#b79cfa"
        stroke-opacity=".38"
        transform="scale(1 .57)"
      />
    </g>
  </svg>
</template>
