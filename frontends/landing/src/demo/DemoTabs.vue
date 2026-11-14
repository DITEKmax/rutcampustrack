<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { lessonId, rowKey, type DemoSnapshot, type EditableMemberId, type ManualMark } from './model'

const props = defineProps<{
  snapshot: DemoSnapshot
  applyMark: (lesson: string, member: EditableMemberId, value: ManualMark, expectedRevision: number) => boolean
}>()
const memberId = 2
const current = computed(() => props.snapshot.manualMarks[rowKey(lessonId, memberId)] ??
  { value: 'PRESENT' as const, revision: 1 })
const journalStatus = ref<HTMLElement | null>(null)
const enabled = computed(() => props.snapshot.scenario === 'standard')
const a = ref(current.value)
const b = ref(current.value)
const aResult = ref<'idle' | 'saved' | 'conflict'>('idle')
const bResult = ref<'idle' | 'conflict' | 'refreshed' | 'saved'>('idle')
function readBoth() {
  a.value = current.value
  b.value = current.value
  aResult.value = 'idle'
  bResult.value = 'idle'
}
watch(() => props.snapshot.resetEpoch, readBoth)
async function saveA(event: MouseEvent) {
  if (!enabled.value || aResult.value !== 'idle') return
  const focused = document.activeElement === event.currentTarget
  const expected = a.value.revision
  if (props.applyMark(lessonId, memberId, 'ABSENT', expected)) {
    a.value = { value: 'ABSENT', revision: expected + 1 }
    aResult.value = 'saved'
  } else aResult.value = 'conflict'
  if (focused) {
    await nextTick()
    journalStatus.value?.focus({ preventScroll: true })
  }
}
async function saveB(event: MouseEvent) {
  if (!enabled.value || aResult.value !== 'saved' || bResult.value === 'saved') return
  if (bResult.value === 'conflict') {
    b.value = current.value
    bResult.value = 'refreshed'
    return
  }
  const focused = document.activeElement === event.currentTarget
  const expected = b.value.revision
  if (props.applyMark(lessonId, memberId, 'PRESENT', expected)) {
    b.value = { value: 'PRESENT', revision: expected + 1 }
    bResult.value = 'saved'
    if (focused) {
      await nextTick()
      journalStatus.value?.focus({ preventScroll: true })
    }
  } else bResult.value = 'conflict'
}
const labels = { PRESENT: 'Был', ABSENT: 'Не был' } as const
</script>

<template>
  <section class="demo-tabs" aria-labelledby="demo-tabs-title">
    <h3 id="demo-tabs-title">Две вкладки · одна строка</h3>
    <p>Артём Волков · пара №2 от 9 ноября. Вкладки читают одну запись и сохраняют независимо.</p>
    <p ref="journalStatus" class="demo-tabs__record" role="status" tabindex="-1">В журнале · {{ labels[current.value] }} · r{{ current.revision }}</p>
    <p v-if="!enabled">Сценарий доступен в режиме «Обычный день».</p>
    <div class="demo-tabs__grid">
      <div class="demo-tabs__pane">
        <h4>Вкладка A</h4>
        <p>Снимок · {{ labels[a.value] }} · r{{ a.revision }}</p>
        <button type="button" :disabled="!enabled || aResult !== 'idle'" @click="saveA">A · сохранить «Не был»</button>
        <p v-if="aResult === 'saved'" role="status">200 · сохранено r{{ a.revision }}</p>
        <p v-if="aResult === 'conflict'" role="status">409 · строка уже изменилась</p>
      </div>
      <div class="demo-tabs__pane">
        <h4>Вкладка B</h4>
        <p>Снимок · {{ labels[b.value] }} · r{{ b.revision }}</p>
        <button type="button" :disabled="!enabled || aResult !== 'saved' || bResult === 'saved'" @click="saveB">
          {{ bResult === 'conflict' ? 'B · перечитать строку' : bResult === 'refreshed' ? 'B · подтвердить «Был»' : 'B · сохранить «Был»' }}
        </button>
        <p v-if="bResult === 'conflict'" role="status">409 · ожидалась r{{ b.revision }}. Строка осталась на текущей ревизии.</p>
        <p v-if="bResult === 'refreshed'" role="status">Прочитана r{{ b.revision }}. Проверь строку и подтверди.</p>
        <p v-if="bResult === 'saved'" role="status">200 · сохранено r{{ b.revision }}</p>
      </div>
    </div>
    <button v-if="aResult === 'conflict' || bResult === 'saved'" type="button" class="text-action" @click="readBoth">Перечитать обе вкладки</button>
  </section>
</template>

<style scoped>
.demo-tabs {
  margin-block-start: 1.25rem;
  display: grid;
  gap: .5rem;
  font-size: .82rem;
  color: var(--story-muted);
}
.demo-tabs h3, .demo-tabs h4, .demo-tabs p { margin: 0; }
.demo-tabs h3, .demo-tabs h4, .demo-tabs__record { color: var(--story-text); }
.demo-tabs__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .4rem; }
.demo-tabs__pane {
  border: 1px solid var(--story-line);
  padding: .55rem;
  border-radius: .45rem;
  display: flex;
  flex-direction: column;
  align-items: start;
  gap: .35rem;
}
.demo-tabs__pane button {
  color: var(--story-text);
  font: inherit;
  text-align: start;
  background: transparent;
  border: 1px solid var(--story-line);
  padding: .4rem;
  border-radius: .35rem;
}
.demo-tabs__pane button:disabled { opacity: .5; cursor: default; }
.demo-tabs > button { justify-self: start; }
@media (max-width: 23rem) {
  .demo-tabs__grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
