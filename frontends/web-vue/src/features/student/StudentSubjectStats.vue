<script setup lang="ts">
import { ref, watch } from 'vue'
import type { StudentLessonType, StudentStatisticsSubjectDetailResponse } from '../../../../mobile-core/src/api/types'
import { type StudentScope, useStudentResource } from './student-state'
import { lessonTypeLabel, percent, studentMark } from './student-format'
import StudentTrend from './StudentTrend.vue'
import StudentNotice from './StudentNotice.vue'
const props=defineProps<{scope:StudentScope;subjectId:string}>()
const detail=useStudentResource<StudentStatisticsSubjectDetailResponse>(props.scope)
const range=ref<'days'|'weeks'>('weeks'),types=ref<StudentLessonType[]>([]),initialized=ref(false)
async function load() {
  const semester=props.scope.session.value?.semester
  if(!semester || (initialized.value && !types.value.length))return
  await detail.load(()=>props.scope.api.getStatisticsSubject(props.subjectId,semester.id,range.value,types.value),true)
  if(props.scope.current() && detail.data.value && !initialized.value){types.value=[...detail.data.value.selectedTypes];initialized.value=true}
}
watch(()=>[props.subjectId,props.scope.refreshed.value],()=>load(),{immediate:true})
</script>
<template>
  <section
    class="student-subject-detail web-stack"
    :aria-label="`Подробности предмета ${detail.data.value?.name ?? ''}`"
  >
    <StudentNotice
      :loading="detail.loading.value"
      :error="detail.error.value"
      @retry="load"
    />
    <template v-if="detail.data.value">
      <div class="student-filter">
        <fieldset class="student-actions">
          <legend>Типы занятий на графике</legend><label
            v-for="type in ['LECTURE','PRACTICE','LAB'] as const"
            :key="type"
          ><input
            v-model="types"
            type="checkbox"
            :value="type"
            :disabled="!detail.data.value.availableTypes.includes(type) || detail.loading.value"
            @change="load"
          >{{ lessonTypeLabel(type) }}<span
            v-if="!detail.data.value.availableTypes.includes(type)"
            class="web-caption"
          > · нет занятий</span></label>
        </fieldset><label class="web-field">Период графика<select
          v-model="range"
          :disabled="detail.loading.value"
          @change="load"
        ><option value="days">По дням</option><option value="weeks">По неделям</option></select></label>
      </div>
      <StudentTrend
        v-if="types.length"
        :points="detail.data.value.series"
        compare
      /><p
        v-else
        class="web-notice"
      >
        Выбери хотя бы один тип занятия для графика.
      </p>
      <div
        class="student-table-scroll"
        tabindex="0"
        aria-label="Статистика всех типов занятий предмета"
      >
        <table class="student-table">
          <thead>
            <tr>
              <th scope="col">
                Тип занятия
              </th><th scope="col">
                Доля «+»
              </th><th scope="col">
                Доля «+» и «у»
              </th><th scope="col">
                Уважительные
              </th><th scope="col">
                Отсутствия
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="card in detail.data.value.typeCards"
              :key="card.type"
            >
              <th scope="row">
                {{ lessonTypeLabel(card.type) }}
              </th><td
                v-for="metric in ['present','presentOrExcused','excused','absent'] as const"
                :key="metric"
              >
                {{ percent(card.metrics[metric].percent) }}<small class="web-caption">{{ card.metrics[metric].count }} / {{ card.metrics.held }}</small>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div
        v-for="card in detail.data.value.typeCards"
        :key="card.type"
        class="web-stack"
      >
        <h4>{{ lessonTypeLabel(card.type) }} · история отметок</h4><p
          v-if="!card.history.length"
          class="web-caption"
        >
          Отметок пока нет.
        </p><div
          v-else
          class="student-history"
        >
          <span
            v-for="(mark,index) in card.history"
            :key="mark.id"
            class="student-mark"
            :data-tone="studentMark(mark.status).tone"
            :aria-label="`${index+1}: ${studentMark(mark.status).label}`"
            :title="studentMark(mark.status).label"
          >{{ studentMark(mark.status).symbol }}</span>
        </div>
      </div>
    </template>
  </section>
</template>
