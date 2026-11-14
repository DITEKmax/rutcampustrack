<script setup lang="ts">
import type { Topic } from '../topics'
import academic from './topics-pwa/academic.svg'
import architecture from './topics-pwa/architecture.svg'
import attendance from './topics-pwa/attendance.svg'
import campus from './topics-pwa/campus.svg'
import data from './topics-pwa/data.svg'
import design from './topics-pwa/design.svg'
import notifications from './topics-pwa/notifications.svg'
import reliability from './topics-pwa/reliability.svg'
import reports from './topics-pwa/reports.svg'
import schedule from './topics-pwa/schedule.svg'
import security from './topics-pwa/security.svg'

const illustrations = {
  academic, architecture, attendance, campus, data, design, notifications, reliability, reports, schedule, security,
} satisfies Record<Topic['slug'], string>

defineProps<{ topic: Topic['slug'] }>()
</script>

<template>
  <img class="topic-illustration" :src="illustrations[topic]" alt="" width="320" height="220" />
</template>
