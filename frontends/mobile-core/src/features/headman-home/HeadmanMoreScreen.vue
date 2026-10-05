<script setup lang="ts">
import statsIcon from './assets/headman-more-statistics.svg'
import homeworkIcon from './assets/headman-more-homework.svg'
import mapIcon from './assets/headman-more-map.svg'
import groupIcon from './assets/headman-more-groups.svg'
import subjectsIcon from './assets/headman-more-subjects.svg'
import scheduleIcon from './assets/headman-more-schedule.svg'
import lessonsIcon from './assets/headman-more-lessons.svg'
import nextIcon from './assets/headman-more-next.svg'
import './headman-more-screen.pcss'

type HeadmanMoreDestination = 'stats' | 'homework' | 'map' | 'group' | 'subjects' | 'schedule' | 'lessons'

interface HeadmanMoreAvailability {
  stats: boolean
  homework: boolean
  map: boolean
  group: boolean
  subjects: boolean
  schedule: boolean
  lessons: boolean
}

const props = defineProps<{
  availability: HeadmanMoreAvailability
}>()

const emit = defineEmits<{
  select: [destination: HeadmanMoreDestination]
}>()

const sections: readonly {
  id: string
  title: string
  items: readonly { id: HeadmanMoreDestination; label: string; icon: string; unavailable: string }[]
}[] = [
  {
    id: 'sections',
    title: 'Разделы',
    items: [
      { id: 'stats', label: 'Статистика', icon: statsIcon, unavailable: 'Статистика группы пока недоступна.' },
      { id: 'homework', label: 'Домашнее задание', icon: homeworkIcon, unavailable: 'Управление домашним заданием пока недоступно.' },
      { id: 'map', label: 'Карта', icon: mapIcon, unavailable: 'Карта кампуса пока недоступна.' },
    ],
  },
  {
    id: 'management',
    title: 'Управление',
    items: [
      { id: 'group', label: 'Группа', icon: groupIcon, unavailable: 'Управление группой пока недоступно.' },
      { id: 'subjects', label: 'Предметы', icon: subjectsIcon, unavailable: 'Управление предметами пока недоступно.' },
      { id: 'schedule', label: 'Конструктор расписания', icon: scheduleIcon, unavailable: 'Конструктор расписания пока недоступен.' },
      { id: 'lessons', label: 'Управление парами', icon: lessonsIcon, unavailable: 'Управление парами пока недоступно.' },
    ],
  },
]

function select(destination: HeadmanMoreDestination): void {
  if (props.availability[destination]) emit('select', destination)
}
</script>

<template>
  <main
    class="headman-more"
    aria-labelledby="headman-more-title"
  >
    <header class="headman-more__header">
      <h1 id="headman-more-title">
        Ещё
      </h1>
    </header>
    <section
      v-for="section in sections"
      :key="section.id"
      class="headman-more__section"
      :aria-labelledby="`headman-more-${section.id}`"
    >
      <h2 :id="`headman-more-${section.id}`">
        {{ section.title }}
      </h2>
      <ul class="headman-more__items">
        <li
          v-for="item in section.items"
          :key="item.id"
        >
          <button
            class="headman-more__item"
            type="button"
            :disabled="!availability[item.id]"
            :aria-label="availability[item.id] ? item.label : `${item.label}. ${item.unavailable}`"
            @click="select(item.id)"
          >
            <span class="headman-more__icon-tile">
              <img
                :src="item.icon"
                alt=""
                aria-hidden="true"
              >
            </span>
            <span class="headman-more__copy">
              <span class="headman-more__label">{{ item.label }}</span>
              <span
                v-if="!availability[item.id]"
                class="headman-more__status"
              >Недоступно</span>
            </span>
            <img
              class="headman-more__next"
              :src="nextIcon"
              alt=""
              aria-hidden="true"
            >
          </button>
          <p
            v-if="!availability[item.id]"
            class="headman-more__reason"
          >
            {{ item.unavailable }}
          </p>
        </li>
      </ul>
    </section>
  </main>
</template>