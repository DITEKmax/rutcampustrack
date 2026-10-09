import { describe, expect, it } from 'vitest'
import { headmanStatsTrendAxis } from '../headman-stats/headman-stats-trend-axis'
import { journalChartBarLayout, type JournalChartBarLayout } from './teacher-journal-chart-layout'
const axis = headmanStatsTrendAxis({ mode: 'SUBJECT', from: '2026-09-01', to: '2026-11-30', points: [] })!
function day(offset:number):string { return new Date(axis.start+offset*86_400_000).toISOString().slice(0,10) }
function lessons(counts:readonly [number,number][]):{id:number;date:string}[]{let id=1;return counts.flatMap(([offset,count])=>Array.from({length:count},()=>({id:id++,date:day(offset)})))}
function assertDistinctWithinPlot(layout:readonly JournalChartBarLayout[], count:number):void {
 expect(layout).toHaveLength(count);expect(new Set(layout.map(bar=>bar.lessonId)).size).toBe(count)
 const ordered=[...layout].sort((a,b)=>a.x-b.x)
 for(let i=0;i<ordered.length;i++){
  const bar=ordered[i]!;expect(bar.width).toBeGreaterThan(0);expect(bar.x).toBeGreaterThanOrEqual(42);expect(bar.x+bar.width).toBeLessThanOrEqual(282+1e-9)
  if(i)expect(ordered[i-1]!.x+ordered[i-1]!.width).toBeLessThanOrEqual(bar.x+1e-9)
 }
}
describe('calendar-anchored journal bar geometry',()=>{
 it('separates sparse lessons on adjacent dates even when the semester is much wider than the observations',()=>{
  const input=lessons([[30,1],[31,1],[60,38]])
  const layout=journalChartBarLayout(input,axis,240,42)
  assertDistinctWithinPlot(layout,40)
  expect(layout[0]!.x+layout[0]!.width).toBeLessThan(layout[1]!.x)
 })
 it('fits six lessons on each day of a ninety-day semester without a one-pixel width floor',()=>{
  const input=lessons(Array.from({length:91},(_,day)=>[day,6] as [number,number]))
  const layout=journalChartBarLayout(input,axis,240,42)
  assertDistinctWithinPlot(layout,546);expect(layout.every(bar=>bar.width<1)).toBe(true)
 })
 it('clamps edge clusters as a whole without overlapping neighbouring dates',()=>{
  const input=lessons([[0,12],[1,6],[89,6],[90,12]])
  const layout=journalChartBarLayout(input,axis,240,42)
  assertDistinctWithinPlot(layout,36)
  expect(layout[0]!.x).toBe(42);expect(layout.at(-1)!.x+layout.at(-1)!.width).toBeCloseTo(282,10)
 })
 it('keeps individual lessons on a dense single day and centres an interior cluster on its calendar anchor',()=>{
  const input=lessons([[40,300]])
  const layout=journalChartBarLayout(input,axis,240,42)
  assertDistinctWithinPlot(layout,300)
  const midpoint=(layout[0]!.x+layout.at(-1)!.x+layout.at(-1)!.width)/2
  // A huge cluster is clamped within the plot; it still contains all 300 unique lessons.
  expect(midpoint).toBeGreaterThan(42);expect(midpoint).toBeLessThan(282)
  const few=journalChartBarLayout(lessons([[40,3]]),axis,240,42)
  expect((few[0]!.x+few.at(-1)!.x+few.at(-1)!.width)/2).toBeCloseTo(42+40/90*240,10)
 })
})
