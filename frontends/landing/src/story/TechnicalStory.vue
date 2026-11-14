<script setup lang="ts">
import { computed } from 'vue'
import LandingIcon from '../art/LandingIcon.vue'
import ArchitectureRequestFlow from './ArchitectureRequestFlow.vue'
import ArchitectureTerminal from './ArchitectureTerminal.vue'
import StoryIde from './StoryIde.vue'
import TopicRequestFlow from './TopicRequestFlow.vue'
import type { DemoSnapshot, EditableMemberId, ManualMark } from '../demo/model'
import { topicFlows } from './topic-flows'
import { topicTerminals } from './topic-terminals'
import type { TopicScenarios } from './topic-scenarios'
import type { LoadedChapter } from './topic-chapters'
import type { StoryLanguage } from './story-source'
import { presentationHome, type Topic } from '../topics'
import './technical-story.pcss'

const props = defineProps<{
  topic: Topic['slug']
  chapters: readonly LoadedChapter[]
  scenarios: TopicScenarios
  markState: 'ready' | 'saved' | 'replayed'
  demoSnapshot: DemoSnapshot
  applyDemoMark: (lesson: string, member: EditableMemberId, value: ManualMark, expectedRevision: number) => boolean
}>()
defineEmits<{ replay: [] }>()

const chaptersWithExamples = computed(() => props.chapters.map(chapter => ({
  ...chapter,
  operations: chapter.operations.map(operation => ({
    ...operation,
    flow: topicFlows[operation.id],
    terminal: topicTerminals[operation.id],
  })),
})))
const stateLabel = computed(() => props.markState === 'replayed'
  ? 'Повтор распознан · 1 запись'
  : 'Отметка сохранена · 1 запись')
const related: Readonly<Record<string, readonly { href: string; label: string }[]>> = {
  auth: [{ href: 'security/#security', label: 'Вход и проверки доступа' }],
  events: [
    { href: 'data/#patterns', label: 'Паттерны записи и доставки' },
    { href: 'notifications/#realtime', label: 'Доставка уведомлений' },
  ],
  'geo-checkin-wire': [{ href: 'reliability/#recovery-retention-verification-checks', label: 'Тесты геоотметки' }],
  'attendance-journal-export-plan': [
    { href: 'reliability/#operation-budgets', label: 'Бюджеты выгрузки' },
    { href: 'security/#lesson-request-access', label: 'Проверки доступа к паре' },
    { href: 'academic/#student-transfer-history', label: 'История состава при переводе' },
  ],
}
const nginxContexts: Partial<Record<StoryLanguage, string>> = {
  http: 'Браузер → Nginx',
  config: 'Nginx → Gateway',
}
const statusContexts: Readonly<Record<string, string>> = {
  'api-statuses-success': 'Ответы 2xx',
  'api-statuses-empty': 'Ответы без JSON',
  'api-statuses-validation': 'Контракт запроса',
  'api-statuses-auth': 'Сессия и доступ',
  'api-statuses-state': 'Ресурс и состояние',
  'api-statuses-limits': 'Ограничения HTTP',
  'api-statuses-failures': 'Сбои HTTP',
  'api-statuses-rpc': 'Результат gRPC',
  'api-statuses-rpc-mapping': 'gRPC → REST',
  'api-statuses-rpc-other': 'Диагностика gRPC',
  'api-statuses-client': 'Решение клиента',
}
</script>

<template>
  <div class="rct-story">
    <template v-for="chapter in chaptersWithExamples" :key="chapter.id">
      <section v-if="chapter.title" :id="chapter.id"
        class="rct-story__chapter" :aria-labelledby="chapter.id + '-title'">
        <header class="rct-story__wrap" data-landing-reveal>
          <h2 :id="chapter.id + '-title'">{{ chapter.title }}</h2>
          <p>{{ chapter.lead }}</p>
        </header>
      </section>
      <template v-for="operation in chapter.operations" :key="operation.id">
        <section :id="operation.id" class="rct-story__operation" :aria-labelledby="operation.id + '-title'">
          <span v-if="operation.alias" :id="operation.alias" class="rct-story__alias" aria-hidden="true"></span>
          <div class="rct-story__wrap rct-story__operation-grid">
            <header class="rct-story__operation-copy" data-landing-reveal>
              <h2 :id="operation.id + '-title'">{{ operation.title }}</h2>
              <p>{{ operation.description }}</p>
              <ul v-if="related[operation.id]" class="topic-related">
                <li v-for="link in related[operation.id]" :key="link.href">
                  <a :href="presentationHome + link.href">
                    <span>{{ link.label }}</span>
                    <LandingIcon name="arrow" />
                  </a>
                </li>
              </ul>
              <component :is="scenarios.tabs" v-if="operation.id === 'transactions-revision'" :snapshot="demoSnapshot" :apply-mark="applyDemoMark" />
              <div v-if="operation.id === 'transaction' && markState !== 'ready'" class="rct-story__replay">
                <p role="status">{{ stateLabel }}</p>
                <button type="button" @click="$emit('replay')">Повторить команду</button>
              </div>
            </header>
            <div class="rct-story__operation-code" data-landing-reveal>
              <ArchitectureTerminal v-if="operation.terminal" :id="operation.id" :example="operation.terminal" />
              <StoryIde :frames="operation.frames"
                :glass-context="statusContexts[operation.id] ?? operation.title"
                :language-contexts="operation.id === 'architecture' ? nginxContexts : undefined" />
            </div>
          </div>
          <div v-if="topic === 'architecture' && operation.id === 'gateway-routing'" class="rct-story__wrap">
            <ArchitectureRequestFlow />
          </div>
          <div v-else-if="operation.flow" class="rct-story__wrap">
            <TopicRequestFlow :id="operation.id" :flow="operation.flow" />
          </div>
          <div v-else-if="operation.id === 'lesson-changes-schedule'" class="rct-story__wrap">
            <component :is="scenarios.lessonTransfer" />
          </div>
          <div v-else-if="operation.id === 'student-transfer-history-journal'" class="rct-story__wrap">
            <component :is="scenarios.studentTransfer" />
          </div>
          <div v-else-if="operation.id === 'campus-map-versions'" class="rct-story__wrap">
            <component :is="scenarios.campus" />
          </div>
          <div v-else-if="operation.id === 'attendance-statistics-denominator'" class="rct-story__wrap">
            <component :is="scenarios.calculator" />
          </div>
          <div v-else-if="operation.id === 'student-ranking-ties'" class="rct-story__wrap">
            <component :is="scenarios.rank" />
          </div>
          <div v-else-if="operation.id === 'attendance-journal-threshold'" class="rct-story__wrap">
            <component :is="scenarios.threshold" />
          </div>
          <div v-else-if="operation.id === 'attendance-journal-export-teacher-file'" class="rct-story__wrap">
            <component :is="scenarios.journalExport" />
          </div>
          <div v-else-if="operation.id === 'resilience-outbox'" class="rct-story__wrap">
            <component :is="scenarios.outbox" />
          </div>
          <div v-else-if="operation.id === 'notification-actions-read'" class="rct-story__wrap">
            <component :is="scenarios.homework" />
          </div>
        </section>
      </template>
    </template>
  </div>
</template>
