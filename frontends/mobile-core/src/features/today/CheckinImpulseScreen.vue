<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  watch,
  type CSSProperties,
} from 'vue';
import type { TodayLesson } from '../../api/types';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import { checkinArrivalGeometry, type useCheckinImpulse } from './checkin-impulse';
import { impulseCoins, impulseConfetti, impulseDust, measuredRem } from './checkin-impulse-effects';
import './checkin-impulse.pcss';

const props = defineProps<{
  lesson: TodayLesson | null;
  state: ReturnType<typeof useCheckinImpulse>['state']['value'];
  feedback: string;
  error: string;
  canSubmit: boolean;
  animate: boolean;
  hostBack: boolean;
}>();
const emit = defineEmits<{
  back: [];
  submit: [];
  retry: [];
  today: [];
}>();
const heading = ref<HTMLElement | null>(null);
const button = ref<HTMLButtonElement | null>(null);
const stage = ref<HTMLElement | null>(null);
const background = ref<HTMLElement | null>(null);
const motion = ref(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches !== true);
const coins = shallowRef<ReturnType<typeof impulseCoins>>([]);
const confetti = shallowRef<CSSProperties[]>([]);
const origin = shallowRef<CSSProperties>();
const busy = computed(() => props.state === 'locating' || props.state === 'submitting');
const type = computed(() =>
  props.lesson
    ? {
        LECTURE: 'Лекция',
        PRACTICE: 'Практика',
        LAB: 'Лабораторная',
      }[props.lesson.schedule.subject.type]
    : '',
);
const actionLabel = computed(() => {
  if (props.state === 'success' || props.state === 'present') return 'Отмечено';
  if (busy.value) return 'Отмечаем…';
  if (props.canSubmit) {
    if (props.error) return 'Повторить';
    return props.lesson?.checkinEligibility.reason === 'COOLDOWN' ||
      props.lesson?.request?.origin === 'AUTO_GEO_FAILURE'
      ? 'Отметиться повторно'
      : 'Отметиться';
  }
  if (props.state === 'pending') return 'На проверке';
  return 'Недоступно';
});

let motionQuery: MediaQueryList | undefined;
function onMotionChange(event: MediaQueryListEvent): void {
  motion.value = !event.matches;
}
onMounted(() => {
  heading.value?.focus({ preventScroll: true });
  motionQuery = window.matchMedia?.('(prefers-reduced-motion: reduce)');
  motionQuery?.addEventListener('change', onMotionChange);
});
onBeforeUnmount(() => motionQuery?.removeEventListener('change', onMotionChange));

function measure() {
  if (!button.value) return null;
  return checkinArrivalGeometry(
    button.value.getBoundingClientRect(),
    window.innerWidth,
    window.innerHeight,
  );
}

watch(
  () => [props.animate, motion.value] as const,
  ([animate, motionAllowed]) => {
    const geometry = measure();
    const stageRect = stage.value?.getBoundingClientRect();
    if (!animate || !motionAllowed || !geometry || !stageRect || !background.value) {
      coins.value = [];
      confetti.value = [];
      origin.value = undefined;
      return;
    }
    const { x, y } = geometry;
    origin.value = {
      '--impulse-origin-x': measuredRem(x - stageRect.left),
      '--impulse-origin-y': measuredRem(y - stageRect.top),
    };
    coins.value = impulseCoins(x, y, window.innerWidth, window.innerHeight);
    confetti.value = impulseConfetti(
      background.value.querySelectorAll<HTMLElement>('.checkin-impulse__dust'),
      x,
      y,
    );
  },
);

defineExpose({ measure });
</script>

<template>
  <main
    class="checkin-impulse"
    data-checkin-impulse
    :data-state="state"
    :data-animate="animate && motion"
    aria-labelledby="checkin-impulse-title"
  >
    <div
      ref="background"
      class="checkin-impulse__background"
      aria-hidden="true"
    >
      <template v-if="motion">
        <i
          v-for="(dust, index) in impulseDust"
          :key="index"
          class="checkin-impulse__dust"
          :class="{ 'checkin-impulse__dust--confetti': confetti[index] }"
          :style="[dust, confetti[index]]"
        />
      </template>
    </div>
    <div class="checkin-impulse__scroll">
      <div class="checkin-impulse__layout">
        <button
          v-if="!hostBack"
          class="checkin-impulse__back"
          type="button"
          aria-label="Назад"
          @click="emit('back')"
        >
          <MobileIcon name="back" />
        </button>
        <header class="checkin-impulse__lesson">
          <p
            v-if="lesson"
            class="checkin-impulse__type"
          >
            {{ type }}
          </p>
          <h1
            id="checkin-impulse-title"
            ref="heading"
            tabindex="-1"
          >
            {{ lesson?.schedule.subject.name ?? 'Отметка на паре' }}
          </h1>
          <p
            v-if="lesson"
            class="checkin-impulse__details"
          >
            <span class="checkin-impulse__time">{{ lesson.schedule.startsAt.slice(0, 5) }}–{{
              lesson.schedule.endsAt.slice(0, 5)
            }}</span>
            <span>{{ lesson.schedule.room.current ?? 'Аудитория уточняется' }}</span>
          </p>
        </header>
        <div
          v-if="lesson"
          ref="stage"
          class="checkin-impulse__stage"
        >
          <div
            class="checkin-impulse__ambient"
            aria-hidden="true"
          />
          <div
            class="checkin-impulse__rim"
            aria-hidden="true"
          />
          <div
            class="checkin-impulse__mount"
            aria-hidden="true"
          >
            <i
              v-for="screw in 4"
              :key="screw"
            />
          </div>
          <div
            class="checkin-impulse__coins"
            :style="origin"
            aria-hidden="true"
          >
            <i
              v-for="(coin, index) in coins"
              :key="index"
              class="checkin-impulse__coin"
              :data-flight="coin.direction"
              :style="coin.style"
            />
          </div>
          <div
            v-if="animate && motion"
            class="checkin-impulse__burst"
            aria-hidden="true"
          />
          <button
            ref="button"
            class="checkin-impulse__mark"
            type="button"
            :disabled="!canSubmit"
            :aria-busy="busy"
            @click="emit('submit')"
          >
            <span
              class="checkin-impulse__mark-icon"
              aria-hidden="true"
            >
              <svg
                v-if="state === 'success' || state === 'present'"
                viewBox="0 0 40 40"
                fill="none"
              >
                <path
                  d="m10 20 7 7 14-15"
                  stroke="currentColor"
                  stroke-width="2.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              <svg
                v-else
                viewBox="0 0 40 40"
                fill="none"
              >
                <path
                  d="M20 9v22M9 20h22"
                  stroke="currentColor"
                  stroke-width="2.3"
                  stroke-linecap="round"
                />
                <circle
                  cx="20"
                  cy="20"
                  r="17"
                  stroke="currentColor"
                  stroke-width="1.3"
                  opacity=".32"
                />
              </svg>
            </span>
            <span>{{ actionLabel }}</span>
          </button>
        </div>
        <p
          class="checkin-impulse__feedback"
          role="status"
          :class="{ 'checkin-impulse__feedback--sr': state === 'success' }"
        >
          {{ feedback }}
        </p>
        <p
          class="checkin-impulse__error"
          role="alert"
        >
          {{ error }}
        </p>
        <div
          v-if="!canSubmit && !busy && state !== 'success' && state !== 'loading'"
          class="checkin-impulse__links"
        >
          <button
            v-if="state === 'error'"
            type="button"
            @click="emit('retry')"
          >
            Повторить
          </button>
          <button
            type="button"
            @click="emit('today')"
          >
            Перейти на «Сегодня»
          </button>
        </div>
      </div>
    </div>
  </main>
</template>
