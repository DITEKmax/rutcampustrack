<script setup lang="ts">
import './headman-schedule-management-review.pcss'
import { createRequestsReviewApi } from './headman-requests-review-fixtures'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import HeadmanScheduleScreen from '../features/schedule/HeadmanScheduleScreen.vue'
import HeadmanLessonManagementScreen from '../features/headman-lesson-management/HeadmanLessonManagementScreen.vue'
import MobileShell from '../shared/components/MobileShell.vue'
import { createMobileNavigationStack, nestedRoute } from '../shared/navigation'
import { createHeadmanNavigationItems } from '../shared/mobile-navigation-items'
import type { MobileHostAdapter } from '../shared/host'
import { DEFAULT_PASSWORD_POLICY, type ProfileSnapshot } from '../features/profile/profile-types'
import { createScheduleManagementApis, scheduleManagementStates } from './headman-schedule-management-fixtures'
import { datePlus } from '../features/headman-lesson-management/lesson-management-state'
const query=new URLSearchParams(window.location.search), state=query.get('state') ?? 'constructor-empty', shell=query.get('shell')==='tma'?'tma':'pwa'
const assistantPermissions=state==='management-assistant'?(['CANCEL_LESSONS'] as const):null
const requestsFixtureState=query.get('requestsFixture')==='unknown'?'unknown':'queue'
const requestsFixture=['queue','unknown'].includes(query.get('requestsFixture') ?? '')
const requestLogs:string[]=[]
const requestsApi=requestsFixture ? createRequestsReviewApi(requestsFixtureState,(line)=>{requestLogs.push(line);document.documentElement.dataset.scheduleManagementRequestsFixtureLog=JSON.stringify(requestLogs)}) : null
const constructor=state.startsWith('constructor') || requestsFixture, apis=createScheduleManagementApis(state), offline=ref(false), task=ref('agenda'), hostVisible=ref(false)
const run=query.get('run') ?? 'schedule-management-r1', session='synthetic-'+run+'-'+state+'-'+shell
const profile:ProfileSnapshot={userId:'1',sessionId:session,displayName:'Максим Кузнецов',groupLabel:'ИБ-21',sessionVersion:'1',rolesVersion:'1',activeRole:'HEADMAN',readOnly:false,passwordPolicy:DEFAULT_PASSWORD_POLICY,roles:[{grantId:'fixture-headman',role:'HEADMAN',status:'ACTIVE',groupId:'4',contextLabel:'ИБ-21',selectable:true,readOnly:false}]}
const navigation=createMobileNavigationStack(nestedRoute('headman-more','headman-more/lessons','overview'))
const route=computed(()=>nestedRoute('headman-more','headman-more/lessons',task.value==='agenda'?'overview':'editor'))
const screen=ref<InstanceType<typeof HeadmanLessonManagementScreen>|null>(null)
const host:MobileHostAdapter={backOwner:shell==='tma'?'host':'product',primaryActionOwner:'product',setBackVisible:(value)=>{hostVisible.value=value},subscribeBack:(listener)=>{window.addEventListener('schedule-management-host-back',listener);return()=>window.removeEventListener('schedule-management-host-back',listener)}}
const stopGuard=navigation.beforeBack(()=>!screen.value?.back())
const originalFont=document.documentElement.style.fontSize
if(query.get('font')==='20')document.documentElement.style.fontSize='20px'
function hostBack():void { window.dispatchEvent(new Event('schedule-management-host-back')) }
let disposed=false
async function waitFor(predicate:()=>boolean):Promise<void>{for(let i=0;i<600&&!disposed;i++){if(predicate())return;await new Promise<void>(resolve=>requestAnimationFrame(()=>resolve()))}throw new Error('Fixture did not settle '+state)}
function clickText(label:string):void{const button=[...document.querySelectorAll<HTMLButtonElement>('button')].find(b=>b.textContent?.trim()===label||b.textContent?.trim().startsWith(label));if(!button||button.disabled)throw new Error('Fixture action unavailable '+label);button.click()}
function click(selector:string):void{const el=document.querySelector<HTMLButtonElement>(selector);if(!el||el.disabled)throw new Error('Fixture action unavailable '+selector);el.click()}
function value(selector:string,text:string):void{const el=document.querySelector<HTMLInputElement|HTMLSelectElement|HTMLTextAreaElement>(selector);if(!el)throw new Error('Fixture field missing '+selector);el.value=text;el.dispatchEvent(new Event(el.tagName==='SELECT'?'change':'input',{bubbles:true}))}
onMounted(async()=>{
  document.documentElement.dataset.scheduleManagementFixtureState=state;document.documentElement.dataset.scheduleManagementFixtureSynthetic='true';document.documentElement.dataset.scheduleManagementFixtureSession=session
  try {
    if(requestsFixture){ await waitFor(()=>!!document.querySelector('.mobile-bottom-nav'));clickText('Заявки');await waitFor(()=>!!document.querySelector('main[data-request-view="queue"][aria-busy="false"]')); }else if(constructor){
      await waitFor(()=>!!document.querySelector('.mobile-bottom-nav'));clickText('Ещё');await waitFor(()=>!!document.querySelector('.headman-more'));clickText('Конструктор расписания')
      await waitFor(()=>!!document.querySelector('[data-constructor-view="list"][aria-busy="false"]'));if(state!=='constructor-read-error')clickText('Ср');await nextTick()
      if(['constructor-add','constructor-slots','constructor-create-unknown'].includes(state)){click('[data-constructor-view="list"] .headman-schedule__primary');await waitFor(()=>!!document.querySelector('[data-constructor-view="editor"][aria-busy="false"]'));click('.headman-schedule__select');await nextTick();if(state!=='constructor-slots'){click('[data-slot="4"]');await nextTick()}}
      if(state==='constructor-series'){click('.headman-schedule__slot');await waitFor(()=>!!document.querySelector('[data-constructor-view="editor"]'));clickText('Рассчитать сохранение');await waitFor(()=>!!document.querySelector('.headman-schedule__context[role="status"]'))}
      if(state==='constructor-create-unknown'){clickText('Сохранить пару');await waitFor(()=>!!document.querySelector('.student-warning')&&!!document.querySelector('[data-constructor-view="editor"][aria-busy="false"]'))}
    }else{
      await waitFor(()=>!!document.querySelector('[data-management-view="agenda"][aria-busy="false"]'))
      if(state==='management-add'||state==='management-oneoff') {click('[role="switch"]');await nextTick();if(state==='management-oneoff'){click('[data-slot="6"]');await waitFor(()=>!!document.querySelector('[data-management-view="oneoff"][aria-busy="false"]'))}}
      if(['management-active','management-transfer','management-calendar','management-cancel','management-room','management-transfer-pending','management-cancel-unknown','management-room-log','management-conflict','management-assistant'].includes(state)){
        click('[data-lesson-id="41"]');await nextTick()
        if(['management-transfer','management-calendar','management-transfer-pending','management-conflict'].includes(state)){clickText('Перенести пару');await waitFor(()=>!!document.querySelector('[data-management-view="transfer"]')&&!document.querySelector('[role="status"]'));value('.lesson-management__field select','4');await nextTick();value('.lesson-management__field input','Б-204')}
        if(state==='management-calendar')click('.lesson-management__select')
        if(state==='management-room')clickText('Изменить аудиторию')
        if(state==='management-cancel'||state==='management-cancel-unknown'){clickText('Отменить пару');await nextTick();value('textarea','Преподаватель отсутствует. Новая дата будет согласована отдельно.');await nextTick()}
        if(state==='management-room-log'){clickText('Открыть журнал действий');await waitFor(()=>!!document.querySelector('.headman-actions:not([aria-busy="true"])'))}
        if(state==='management-transfer-pending'||state==='management-conflict'){clickText('Перенести пару');await waitFor(()=>!!document.querySelector('.student-warning'))}
        if(state==='management-cancel-unknown'){clickText('Отменить пару');await waitFor(()=>!!document.querySelector('.student-warning'))}
      }
      if(state==='management-cancelled'){click('[role="switch"]:last-child');await nextTick();click('[data-lesson-id="45"]')}
      if(state==='management-offline')offline.value=true
    }
    await nextTick();await document.fonts.ready
    document.documentElement.dataset.scheduleManagementFixtureView=document.querySelector('main[data-constructor-view],[data-management-view]')?.getAttribute(constructor?'data-constructor-view':'data-management-view')??''
    document.documentElement.dataset.scheduleManagementFixtureRevision=query.get('revision') ?? 'working-tree'
    document.documentElement.dataset.scheduleManagementFixtureReady='true'
  }catch(cause){document.documentElement.dataset.scheduleManagementFixtureError=cause instanceof Error?cause.message:String(cause)}
})
onBeforeUnmount(()=>{disposed=true;stopGuard();document.documentElement.style.fontSize=originalFont})
</script>
<template>
  <HeadmanScheduleScreen
    v-if="constructor"
    :api="apis.schedule"
    :requests-api="requestsApi"
    :profile="profile"
    :group-id="4"
    :host="host"
  />
  <MobileShell
    v-else
    :route="route"
    :navigation="navigation"
    :nav-items="createHeadmanNavigationItems()"
    :host="host"
  >
    <template #back>
      <span hidden />
    </template><HeadmanLessonManagementScreen
      ref="screen"
      :journal-api="apis.journal"
      :schedule-api="apis.schedule"
      :actions-api="apis.actions"
      :group-id="4"
      :owner="profile"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :initial-date="datePlus(new Date().toLocaleDateString('en-CA',{timeZone:'Europe/Moscow'}),7)"
      @view-change="task=$event"
    />
  </MobileShell>
  <button
    v-if="shell==='tma'&&hostVisible"
    type="button"
    class="schedule-management-fixture__host"
    aria-label="Назад через Telegram"
    @click="hostBack"
  >
    Назад
  </button>
  <span hidden>{{ scheduleManagementStates.length }}</span>
</template>
