<script setup lang="ts">
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

defineProps<{
  availability: HeadmanMoreAvailability
}>()

const emit = defineEmits<{
  select: [destination: HeadmanMoreDestination]
}>()

const sections: readonly {
  title: string
  items: readonly { id: HeadmanMoreDestination; label: string; unavailable: string }[]
}[] = [
  {
    title: 'Разделы',
    items: [
      { id: 'stats', label: 'Статистика', unavailable: 'Статистика группы пока недоступна.' },
      { id: 'homework', label: 'Домашнее задание', unavailable: 'Управление домашним заданием пока недоступно.' },
      { id: 'map', label: 'Карта', unavailable: 'Карта кампуса пока недоступна.' },
    ],
  },
  {
    title: 'Управление',
    items: [
      { id: 'group', label: 'Группа', unavailable: 'Управление группой пока недоступно.' },
      { id: 'subjects', label: 'Предметы', unavailable: 'Управление предметами пока недоступно.' },
      { id: 'schedule', label: 'Конструктор расписания', unavailable: 'Конструктор расписания пока недоступен.' },
      { id: 'lessons', label: 'Управление парами', unavailable: 'Управление парами пока недоступно.' },
    ],
  },
]
</script>

<template>
  <main
    class="headman-more"
    aria-labelledby="headman-more-title"
  >
    <header class="headman-more__header">
      <p class="headman-more__eyebrow">
        Староста · разделы
      </p>
      <h1 id="headman-more-title">
        Ещё
      </h1>
    </header>
    <section
      v-for="section in sections"
      :key="section.title"
      class="headman-more__section"
      :aria-labelledby="`headman-more-${section.title}`"
    >
      <h2 :id="`headman-more-${section.title}`">
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
            @click="emit('select', item.id)"
          >
            <span>{{ item.label }}</span>
            <span
              v-if="!availability[item.id]"
              class="headman-more__status"
            >Недоступно</span>
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
