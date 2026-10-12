<script setup lang="ts">
import { adminCanNavigate } from '../../shared/admin-navigation';
import type { AdminAccessProfile } from '../admin-access/admin-access-client';
import semesterIcon from '../../assets/admin/semester.svg';
import nextIcon from '../../assets/admin/next.svg';
import mapIcon from '../profile/assets/more-map.svg';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import './admin-more-screen.pcss';
defineProps<{ authority: AdminAccessProfile | null }>();
const emit = defineEmits<{ navigate: [route: 'semesters' | 'map' | 'map-statistics'] }>();
</script>

<template>
  <main
    class="admin-more"
    aria-labelledby="admin-more-title"
  >
    <h1 id="admin-more-title"> Ещё </h1>
    <section
      v-if="adminCanNavigate(authority, 'semesters')"
      aria-labelledby="admin-more-academic"
    >
      <h2 id="admin-more-academic"> Учебный процесс </h2>
      <button
        type="button"
        class="admin-more__row"
        @click="emit('navigate', 'semesters')"
      >
        <img
          :src="semesterIcon"
          alt=""
          aria-hidden="true"
        /><span>Семестры</span
        ><img
          :src="nextIcon"
          alt=""
          aria-hidden="true"
        />
      </button>
    </section>
    <section
      v-if="adminCanNavigate(authority, 'map')"
      aria-labelledby="admin-more-campus"
    >
      <h2 id="admin-more-campus"> Кампус </h2>
      <button
        type="button"
        class="admin-more__row"
        @click="emit('navigate', 'map')"
      >
        <img
          :src="mapIcon"
          alt=""
          aria-hidden="true"
        /><span>Корпуса и этажи</span
        ><img
          :src="nextIcon"
          alt=""
          aria-hidden="true"
        />
      </button>
      <button
        type="button"
        class="admin-more__row"
        @click="emit('navigate', 'map-statistics')"
      >
        <MobileIcon name="attendance-graph" /><span>Статистика карты</span
        ><img
          :src="nextIcon"
          alt=""
          aria-hidden="true"
        />
      </button>
    </section>
  </main>
</template>
