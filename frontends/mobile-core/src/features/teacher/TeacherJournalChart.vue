<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { headmanStatsTrendAxis, headmanStatsTrendPosition } from '../headman-stats/headman-stats-trend-axis'
import type { TeacherJournalResponse } from './teacher-client'
import { journalCounts, journalPercent, type JournalCounts } from './teacher-journal-model'
import { teacherLessonTypeLabel } from './teacher-ui-format'
const props = defineProps<{ journal: TeacherJournalResponse; studentId: number | null; from: string; to: string }>()
const plot = ref<SVGSVGElement|null>(null)
const probe = ref<SVGTextElement|null>(null)
const width = ref(360)
const labelWidth = ref(50)
const readout = ref<string|null>(null)
let observer: ResizeObserver|null=null
function measure(): void {
  if (!plot.value || !probe.value) return
  const measured = plot.value.getBoundingClientRect().width
  if (measured > 0) width.value=measured
  const ctx=document.createElement('canvas').getContext('2d'); if (!ctx) return
  const style=getComputedStyle(probe.value); ctx.font=`${style.fontWeight} ${style.fontSize} ${style.fontFamily}`
  const format=new Intl.DateTimeFormat('ru',{day:'numeric',month:'short',timeZone:'UTC'})
  let max=0; for(let month=0;month<12;month++) for(let day=1;day<=28;day++) max=Math.max(max,ctx.measureText(format.format(new Date(Date.UTC(2026,month,day)))).width)
  labelWidth.value=max
}
onMounted(() => { measure(); if(typeof ResizeObserver!=='undefined'){ observer=new ResizeObserver(measure); if(plot.value)observer.observe(plot.value) }; void document.fonts?.ready.then(measure) })
onBeforeUnmount(() => observer?.disconnect())
watch(() => [props.journal,props.studentId,props.from,props.to], () => { readout.value=null })
const left=42, right=8, top=12, bottom=32, height=210
const plotWidth=computed(() => Math.max(1,width.value-left-right))
// Reuse accepted equidistant label math with explicit anchors and no observation-derived bounds.
const axis=computed(() => headmanStatsTrendAxis({mode:'SUBJECT',from:props.from,to:props.to,points:[],plotWidth:plotWidth.value,labelWidth:labelWidth.value}))
const bars=computed(() => props.journal.lessons.filter(lesson => lesson.status==='CLOSED'&&!lesson.cancelled&&lesson.date>=props.from&&lesson.date<=props.to).map(lesson => {
  const students=props.studentId===null?props.journal.students:props.journal.students.filter(student=>student.studentId===props.studentId)
  const counts:JournalCounts={present:0,excused:0,absent:0,denominator:0}
  for(const student of students){const value=journalCounts(student.cells,new Set([lesson.id])); counts.present+=value.present;counts.excused+=value.excused;counts.absent+=value.absent;counts.denominator+=value.denominator}
  return {lesson,counts,position:axis.value?headmanStatsTrendPosition(lesson.date,axis.value):null}
}))
const barWidth=computed(() => Math.max(1,Math.min(18,plotWidth.value/Math.max(1,bars.value.length)*0.7)))
function x(index:number):number {
  const bar=bars.value[index]!; const sameDay=bars.value.filter(value=>value.lesson.date===bar.lesson.date); const slot=sameDay.findIndex(value=>value.lesson.id===bar.lesson.id)
  const anchor=left+(bar.position??0)*plotWidth.value
  const groupWidth=sameDay.length*barWidth.value
  const groupStart=Math.max(left,Math.min(width.value-right-groupWidth,anchor-groupWidth/2))
  return groupStart+slot*barWidth.value
}
function size(count:number,total:number):number { return total?count/total*(height-top-bottom):0 }
function text(index:number):string {const bar=bars.value[index]!;return `${bar.lesson.date}, ${teacherLessonTypeLabel(bar.lesson.lessonType)}, ${bar.lesson.lessonNumber}-я пара: присутствия ${journalPercent(bar.counts.present,bar.counts.denominator)}, уважительные причины ${journalPercent(bar.counts.excused,bar.counts.denominator)}, отсутствия ${journalPercent(bar.counts.absent,bar.counts.denominator)}; ${bar.counts.denominator} отметок`}
</script>
<template>
  <section
    class="teacher-journal__chart"
    aria-label="Столбчатый график посещаемости"
  >
    <p
      v-if="!bars.some(bar=>bar.counts.denominator)"
      role="status"
    >
      За выбранный период нет данных для графика.
    </p>
    <svg
      ref="plot"
      :viewBox="`0 0 ${width} ${height}`"
      class="teacher-journal__plot"
      role="group"
      aria-label="Зелёный — присутствовал, жёлтый — уважительная причина, красный — отсутствовал"
    >
      <text
        ref="probe"
        class="teacher-journal__chart-probe"
        aria-hidden="true"
      >28 сент.</text>
      <g
        class="teacher-journal__chart-grid"
        aria-hidden="true"
      ><template
        v-for="tick in [0,50,100]"
        :key="tick"
      ><line
        :x1="left"
        :x2="width-right"
        :y1="top+(100-tick)/100*(height-top-bottom)"
        :y2="top+(100-tick)/100*(height-top-bottom)"
      /><text
        :x="left-6"
        :y="top+(100-tick)/100*(height-top-bottom)+4"
        text-anchor="end"
      >{{ tick }}%</text></template></g>
      <g
        v-for="(bar,index) in bars"
        :key="bar.lesson.id"
        tabindex="0"
        role="img"
        :aria-label="text(index)"
        @focus="readout=text(index)"
        @click="readout=text(index)"
      ><title>{{ text(index) }}</title><rect
        class="teacher-journal__bar--present"
        :x="x(index)"
        :y="height-bottom-size(bar.counts.present,bar.counts.denominator)"
        :width="barWidth"
        :height="size(bar.counts.present,bar.counts.denominator)"
      /><rect
        class="teacher-journal__bar--excused"
        :x="x(index)"
        :y="height-bottom-size(bar.counts.present+bar.counts.excused,bar.counts.denominator)"
        :width="barWidth"
        :height="size(bar.counts.excused,bar.counts.denominator)"
      /><rect
        class="teacher-journal__bar--absent"
        :x="x(index)"
        :y="height-bottom-size(bar.counts.denominator,bar.counts.denominator)"
        :width="barWidth"
        :height="size(bar.counts.absent,bar.counts.denominator)"
      /></g>
      <g
        class="teacher-journal__chart-axis"
        aria-hidden="true"
      ><text
        v-for="(tick,index) in axis?.ticks ?? []"
        :key="tick.date"
        :x="left+tick.position*plotWidth"
        :y="height-8"
        :text-anchor="index===0?'start':index===(axis?.ticks.length??0)-1?'end':'middle'"
      >{{ tick.label }}</text></g>
    </svg>
    <p class="teacher-journal__legend">
      <span class="teacher-journal__present">+ Присутствия</span><span class="teacher-journal__excused">у Уважительные</span><span class="teacher-journal__absent">н Отсутствия</span>
    </p>
    <p
      v-if="readout"
      role="status"
    >
      {{ readout }}
    </p>
  </section>
</template>
