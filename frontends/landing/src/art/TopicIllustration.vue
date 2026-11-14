<script setup lang="ts">
import { useId } from 'vue'
import type { Topic } from '../topics'

defineProps<{ topic: Topic['slug'] }>()
const id = useId()
const fill = (name: string) => 'url(#' + id + '-' + name + ')'
</script>

<template>
  <svg class="topic-illustration" viewBox="0 0 320 220" aria-hidden="true" focusable="false">
    <defs>
      <linearGradient :id="id + '-top'" x2=".9" y2="1" gradientUnits="objectBoundingBox">
        <stop stop-color="#c9adff" />
        <stop offset=".43" stop-color="#8f65db" />
        <stop offset="1" stop-color="#48337c" />
      </linearGradient>
      <linearGradient :id="id + '-light'" x2=".65" y2="1">
        <stop stop-color="#f4e6ff" />
        <stop offset=".42" stop-color="#b592f2" />
        <stop offset="1" stop-color="#7452b4" />
      </linearGradient>
      <linearGradient :id="id + '-side'" x2=".82" y2="1">
        <stop stop-color="#624790" />
        <stop offset=".56" stop-color="#3b285c" />
        <stop offset="1" stop-color="#271a40" />
      </linearGradient>
      <linearGradient :id="id + '-dark'" x2=".86" y2="1">
        <stop stop-color="#392b58" />
        <stop offset="1" stop-color="#1d162b" />
      </linearGradient>
      <radialGradient :id="id + '-haze'">
        <stop stop-color="#9164dd" stop-opacity=".43" />
        <stop offset="1" stop-color="#9164dd" stop-opacity="0" />
      </radialGradient>
    </defs>

    <ellipse cx="164" cy="161" rx="151" ry="53" :fill="fill('haze')" />
    <path d="M34 162 160 112l126 50-126 48z" fill="#15111e" stroke="#634d83" stroke-opacity=".64" />
    <path d="M57 165 160 126l103 39-103 39z" fill="none" stroke="#ab88ed" stroke-opacity=".25" />
    <path d="m35 161 125 48 125-48" fill="none" stroke="#a681e5" stroke-opacity=".29" />

    <g v-if="topic === 'architecture'" stroke-linejoin="round">
      <path d="m75 141 78-31m12 8 70 23m-77-49v38" fill="none" stroke="#ba9aff" stroke-width="2" stroke-dasharray="4 6" />
      <path d="m63 126 36 13v26l-36-13zM229 138l36-13v26l-36 13z" :fill="fill('side')" />
      <path d="m63 126 37-13 35 13-36 13zm131 0 36-13 35 12-36 13z" :fill="fill('top')" stroke="#d9bdff" stroke-opacity=".8" />
      <path d="m99 139 36-13v26l-36 13zm95-13 35 12v26l-35-12z" :fill="fill('dark')" stroke="#674994" />
      <path d="m106 77 54-19 54 19v65l-54 20-54-20z" :fill="fill('side')" stroke="#8f6fb9" />
      <path d="m106 77 54 20v65l-54-20z" :fill="fill('dark')" />
      <path d="m106 77 54-19 54 19-54 20z" :fill="fill('light')" stroke="#f1dfff" />
      <path d="m120 104 24 9m-24 2 24 9m-24 2 24 9m31-24 23-8m-23 20 23-8m-23 20 23-8" stroke="#c3a3f6" stroke-opacity=".76" stroke-width="2" />
      <path d="m81 120 18-6 16 6m98 0 17-6 17 6" fill="none" stroke="#f1dfff" stroke-width="2" />
    </g>

    <g v-else-if="topic === 'security'" stroke-linejoin="round">
      <path d="m157 37 71 25-1 50c-2 32-23 60-59 76l-13-11c-36-15-55-47-56-78V61z" :fill="fill('side')" stroke="#9f7bdd" />
      <path d="m151 31 65 23v44c0 32-22 61-62 79-35-13-60-42-60-76V56z" :fill="fill('light')" stroke="#f1dbff" />
      <path d="m151 45 54 19v34c0 27-20 53-51 68-29-12-49-37-49-65V65z" :fill="fill('top')" stroke="#e4caff" />
      <path d="m151 54 45 16v28c0 21-15 45-42 59-24-11-41-33-41-57V72z" :fill="fill('dark')" stroke="#6f5198" />
      <path d="M141 103v-9a12 12 0 0 1 24 0v9" fill="none" stroke="#e2caff" stroke-width="5" />
      <rect x="135" y="100" width="36" height="29" rx="5" :fill="fill('light')" />
      <path d="M153 108v12" stroke="#4a326c" stroke-width="4" stroke-linecap="round" />
    </g>

    <g v-else-if="topic === 'data'">
      <path d="M100 122v30c0 14 27 26 60 26s60-12 60-26v-30" :fill="fill('side')" stroke="#a480e8" />
      <ellipse cx="160" cy="122" rx="60" ry="26" :fill="fill('top')" stroke="#d5b8ff" />
      <path d="M100 88v30c0 14 27 26 60 26s60-12 60-26V88" :fill="fill('side')" stroke="#ae8aed" />
      <ellipse cx="160" cy="88" rx="60" ry="26" :fill="fill('top')" stroke="#d5b8ff" />
      <path d="M100 54v30c0 14 27 26 60 26s60-12 60-26V54" :fill="fill('side')" stroke="#bd96ff" />
      <ellipse cx="160" cy="54" rx="60" ry="26" :fill="fill('light')" stroke="#f5e1ff" />
      <ellipse cx="160" cy="54" rx="46" ry="17" fill="none" stroke="#f2dfff" stroke-opacity=".73" />
      <path d="M113 82q11 9 29 12m-29 22q11 9 29 12m-29 22q11 9 29 12" fill="none" stroke="#d4b0ff" stroke-width="2" />
      <path d="m197 94 10-4m-10 38 10-4m-10 38 10-4" stroke="#e9d2ff" stroke-width="2" />
    </g>

    <g v-else-if="topic === 'reliability'" stroke-linejoin="round">
      <path d="M79 132 161 100l81 32-81 32z" :fill="fill('dark')" stroke="#7d60a9" />
      <path d="m79 132 82 32 81-32v16l-81 31-82-31z" :fill="fill('side')" stroke="#946dcc" />
      <path d="m99 63 62-23 62 23v59l-62 23-62-23z" :fill="fill('side')" stroke="#bb94f8" />
      <path d="m99 63 62 23v59l-62-23z" :fill="fill('dark')" />
      <path d="m99 63 62-23 62 23-62 23z" :fill="fill('light')" stroke="#ead8ff" />
      <path d="m126 90 13 16 20-28 16 24 12-11 19 17" fill="none" stroke="#f4e4ff" stroke-width="4" stroke-linecap="round" />
      <path d="M71 110q4-20 23-35m-23 35 3-18m-3 18 15-8m163-12q-4 20-23 35m23-35-3 18m3-18-15 8" fill="none" stroke="#a883ed" stroke-width="3" stroke-linecap="round" />
    </g>

    <g v-else-if="topic === 'academic'" stroke-linejoin="round">
      <path d="m159 80-1 26-68 24m68-24 72 24m-72-24v48" fill="none" stroke="#af8bf3" stroke-width="3" />
      <path d="m74 126 21-8 21 8v27l-21 9-21-9zm63 19 21-8 21 8v28l-21 9-21-9zm66-19 21-8 21 8v27l-21 9-21-9z" :fill="fill('side')" stroke="#a986df" />
      <path d="m74 126 21-8 21 8-21 9zm63 19 21-8 21 8-21 9zm66-19 21-8 21 8-21 9z" :fill="fill('light')" />
      <path d="m112 58 46-23 46 23v30l-46 24-46-24z" :fill="fill('side')" stroke="#a986df" />
      <path d="m112 58 46-23 46 23-46 24z" :fill="fill('light')" stroke="#edd8ff" />
      <path d="m128 58 30-15 31 15-31 15z" fill="none" stroke="#f4e5ff" stroke-opacity=".85" />
      <path d="M158 82v30m-63 23v26m63-7v27m66-46v26" stroke="#be9deb" stroke-opacity=".58" />
    </g>

    <g v-else-if="topic === 'schedule'" stroke-linejoin="round">
      <path d="m85 57 140-15 18 24v101l-141 19-17-18z" :fill="fill('side')" stroke="#b083f0" />
      <path d="m85 57 140-15v105l-140 21z" :fill="fill('top')" stroke="#d9bbff" />
      <path d="m85 57 140-15v30L85 89z" :fill="fill('light')" />
      <path d="m110 46 1 18m88-28v17" stroke="#e5c9ff" stroke-width="6" stroke-linecap="round" />
      <path d="m103 103 104-14v46l-104 15zm0 15 104-14m-104 30 104-15m-79-20v47m26-50v46m26-50v46" fill="none" stroke="#dfc8ff" stroke-opacity=".7" />
      <path d="m155 111 24-3v15l-24 3z" :fill="fill('light')" stroke="#fff0ff" />
    </g>

    <g v-else-if="topic === 'attendance'" stroke-linejoin="round">
      <path d="m77 145 68-32 98 39-68 33z" :fill="fill('top')" stroke="#bc95fd" />
      <path d="m77 145 98 40 68-33v14l-68 31-98-40z" :fill="fill('side')" stroke="#795aaa" />
      <path d="m116 148 33-15 57 23-31 16zm17-7 57 23m-57-9 35-16" fill="none" stroke="#e1c9ff" stroke-opacity=".56" />
      <path d="M167 33c23 0 46 17 46 43 0 30-38 65-46 72l-12-3c-12-12-39-44-39-67 0-25 21-45 51-45z" :fill="fill('side')" stroke="#ae8ce6" />
      <path d="M158 30c24 0 43 18 43 44 0 29-35 63-43 73-11-11-44-45-44-73 0-24 19-44 44-44z" :fill="fill('light')" stroke="#f4dfff" />
      <circle cx="158" cy="75" r="23" :fill="fill('dark')" stroke="#9569d6" />
      <path d="m146 75 8 9 16-18" fill="none" stroke="#eddaff" stroke-width="5" stroke-linecap="round" />
    </g>

    <g v-else-if="topic === 'reports'" stroke-linejoin="round">
      <path d="m89 49 124-15 22 17v107l-124 23-22-17z" :fill="fill('side')" stroke="#af8ee2" />
      <path d="m89 49 124-15v109l-124 21z" :fill="fill('dark')" stroke="#c6a4ff" />
      <path d="m100 58 101-12v86l-101 16z" :fill="fill('top')" stroke="#e1c7ff" />
      <path d="m110 75 77-10m-77 24 77-10m-77 26 77-11m-77 27 77-11m-56-39v54m25-57v53" fill="none" stroke="#e3ccff" stroke-opacity=".74" />
      <path d="m168 156 17-6v-23l-17 6zm22 8 17-6v-41l-17 6zm22 8 17-6v-56l-17 6z" :fill="fill('light')" stroke="#ebd6ff" />
      <path d="m185 127 8 4v23l-8-4zm22-10 8 4v41l-8-4zm22-7 8 4v56l-8-4z" :fill="fill('side')" />
    </g>

    <g v-else-if="topic === 'notifications'" stroke-linejoin="round">
      <path d="m116 62 108-17 14 14v53l-44 6-22 20-8-15-34 6-14-14z" :fill="fill('side')" stroke="#a680e6" />
      <path d="m116 62 108-17v53l-44 7-22 20-8-15-34 6z" :fill="fill('light')" stroke="#f4ddff" />
      <path d="m133 77 73-11m-73 26 55-8" stroke="#7252a9" stroke-width="3" stroke-linecap="round" />
      <path d="m72 113 108-18 14 14v51l-44 7-22 20-8-15-34 6-14-14z" :fill="fill('side')" stroke="#a680e6" />
      <path d="m72 113 108-18v51l-44 7-22 20-8-15-34 6z" :fill="fill('top')" stroke="#e2caff" />
      <circle cx="99" cy="134" r="4" fill="#f2e0ff" />
      <circle cx="122" cy="130" r="4" fill="#f2e0ff" />
      <circle cx="145" cy="126" r="4" fill="#f2e0ff" />
    </g>

    <g v-else stroke-linejoin="round">
      <path d="m64 109 100-54 94 45-100 54z" :fill="fill('top')" stroke="#d5b8ff" />
      <path d="m64 109 94 45 100-54v19l-100 55-94-45z" :fill="fill('side')" stroke="#a787d9" />
      <path d="m76 103 88-48 81 40-88 47z" :fill="fill('dark')" stroke="#ac86f3" />
      <path d="m101 90 27 13 32-17 26 13m-58 4-16 9m48-26-25-13m25 13 31-17m-5 30 31-17m-31 17-15 8m-14 35v-20l-29-15m29 15 32-17m14-7 20 10m-54-54 13 6" fill="none" stroke="#c7a7f9" stroke-width="2" />
      <path d="m102 102 27 14 44-23 22 11" fill="none" stroke="#fff0ff" stroke-width="2" stroke-dasharray="3 4" />
      <path d="M194 40c13 0 22 10 22 21 0 15-17 33-22 38-5-5-22-23-22-38 0-11 9-21 22-21z" :fill="fill('light')" stroke="#f5ddff" />
      <circle cx="194" cy="62" r="7" :fill="fill('dark')" />
    </g>
  </svg>
</template>
