<script setup lang="ts">
import { computed, nextTick, ref, onMounted, onBeforeUnmount, watch } from 'vue';
import LandingIcon from '../art/LandingIcon.vue';
import DemoWeekControls from './DemoWeekControls.vue';
import { selectedDemoLesson } from './fixtures';
import {
  channel,
  initialSnapshot,
  isPhoneView,
  isDemoDate,
  isLessonId,
  isRevision,
  isEditableMemberId,
  isManualMark,
  type EditableMemberId,
  type ManualMark,
  type DemoSetupScenario,
  lessonId,
  type DemoSnapshot,
  type PhoneView,
} from './model';

const props = defineProps<{ snapshot: DemoSnapshot }>();
const emit = defineEmits<{
  mark: [id: string];
  'mark-member': [lesson: string, id: EditableMemberId, value: ManualMark, expectedRevision: number];
  'select-date': [date: string];
  'select-lesson': [lesson: string];
  'choose-scenario': [scenario: DemoSetupScenario];
  navigate: [next: PhoneView, focusMark?: boolean];
  reset: [];
}>();
const frame = ref<HTMLIFrameElement | null>(null);
const phoneModified = ref(false);
const changed = computed(
  () =>
    props.snapshot.view !== initialSnapshot.view ||
    props.snapshot.date !== initialSnapshot.date ||
    props.snapshot.selectedLesson !== initialSnapshot.selectedLesson ||
    props.snapshot.scenario !== initialSnapshot.scenario ||
    props.snapshot.markState !== initialSnapshot.markState ||
    Object.keys(props.snapshot.manualMarks).length > 0 ||
    phoneModified.value,
);
const phoneSrc = import.meta.env.BASE_URL + 'phone.html';
function sync() {
  frame.value?.contentWindow?.postMessage(
    { channel, type: 'snapshot', detail: props.snapshot },
    location.origin,
  );
}
function receive(event: MessageEvent) {
  if (
    event.origin !== location.origin ||
    event.source !== frame.value?.contentWindow ||
    event.data?.channel !== channel
  )
    return;
  if (event.data.type === 'ready') {
    phoneModified.value = false;
    sync();
    return;
  }
  if (event.data.resetEpoch !== props.snapshot.resetEpoch) return;
  if (
    event.data.type === 'local-state' &&
    event.data.detail?.revision === props.snapshot.revision &&
    typeof event.data.detail?.modified === 'boolean'
  ) {
    phoneModified.value = event.data.detail.modified;
  }
  if (
    event.data.type === 'mark' &&
    event.data.detail === lessonId &&
    props.snapshot.view === 'today' && props.snapshot.date === initialSnapshot.date
  )
    emit('mark', lessonId);
  if (event.data.type === 'navigate' && isPhoneView(event.data.detail))
    emit('navigate', event.data.detail);
  if (event.data.type === 'select-date' && isDemoDate(event.data.detail))
    emit('select-date', event.data.detail);
  if (event.data.type === 'select-lesson' && isLessonId(event.data.detail))
    emit('select-lesson', event.data.detail);
  const choice = event.data.detail;
  if (
    event.data.type === 'mark-member' &&
    props.snapshot.view === 'group' &&
    isLessonId(choice?.lesson) &&
    choice.lesson === (props.snapshot.selectedLesson ?? selectedDemoLesson(props.snapshot)?.id) &&
    isEditableMemberId(choice?.id) &&
    isManualMark(choice?.value) &&
    isRevision(choice?.expectedRevision) &&
    choice.expectedRevision > 0
  )
    emit('mark-member', choice.lesson, choice.id, choice.value, choice.expectedRevision);
}
watch(() => props.snapshot, sync);
onMounted(() => window.addEventListener('message', receive));
onBeforeUnmount(() => window.removeEventListener('message', receive));

async function restart() {
  if (!changed.value) return;
  frame.value?.focus({ preventScroll: true });
  phoneModified.value = false;
  emit('reset');
  // On narrow screens the disappearing button changes the frame's position.
  await nextTick();
  scrollToPhone('start');
}
function scrollToPhone(block: ScrollLogicalPosition) {
  frame.value?.closest('#demo-phone')?.scrollIntoView({ block, behavior: 'instant' });
}
function showPhone() {
  scrollToPhone('nearest');
  frame.value?.focus({ preventScroll: true });
}
defineExpose({ showPhone });
</script>

<template>
  <section
    id="demo"
    class="landing-shell landing-section landing-demo"
    aria-labelledby="demo-title"
  >
    <div
      class="section-topline"
      data-landing-reveal
      ><span>01 / В руках пользователя</span><span>Интерактивное PWA-демо</span></div
    >
    <div class="demo-grid">
      <div
        class="demo-story"
        data-landing-reveal
      >
        <h2 id="demo-title">Одна отметка.<br />Всё связано.</h2>
        <button
          v-if="changed"
          class="text-action demo-reset"
          type="button"
          @click="restart"
        >
          <LandingIcon name="repeat" /> Начать заново
        </button>
        <DemoWeekControls
          :snapshot="snapshot"
          @select-date="emit('select-date', $event)"
          @select-lesson="emit('select-lesson', $event)"
          @choose-scenario="emit('choose-scenario', $event)"
          @show-phone="showPhone"
        />
      </div>

      <div class="demo-stage">
        <div
          class="demo-orbit-bg"
          aria-hidden="true"
        ></div>
        <span class="demo-badge"
          ><span class="landing-dot"></span> Демо-данные · без подключения к серверу</span
        >
        <div
          id="demo-phone"
          class="landing-phone"
          data-landing-reveal
        >
          <iframe
            ref="frame"
            :src="phoneSrc"
            title="Интерактивное PWA-демо RutCampusTrack"
            @load="sync"
          ></iframe>
        </div>
      </div>
    </div>
  </section>
</template>
