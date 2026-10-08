<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { AdminUsersApiError, type AdminUsersClient, type AdminRecoveryLink } from './admin-users-client'
import AdminNotice from '../../shared/components/AdminNotice.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import './recovery-link-panel.pcss'

const props = defineProps<{client:AdminUsersClient;userId:number;disabled?:boolean;embedded?:boolean}>()
const emit = defineEmits<{ownerError:[cause:unknown];issued:[visible:boolean];done:[]}>()
const link=ref<AdminRecoveryLink | null>(null)
const busy=ref(false), uncertain=ref(false), expired=ref(false)
const feedback=ref<string | null>(null)
const feedbackTone=ref<'success'|'warning'|'danger'>('danger')
const now=ref(Date.now())
const remaining=computed(() => link.value ? Math.max(0,Math.ceil((Date.parse(link.value.expiresAt)-now.value)/1000)) : 0)
const expiryLabel=computed(() => link.value ? new Intl.DateTimeFormat('ru-RU',{dateStyle:'short',timeStyle:'medium'}).format(new Date(link.value.expiresAt)) : '')
let disposed=false, revision=0
let abort:AbortController | null=null
let timer:ReturnType<typeof setInterval> | null=null
function clearLink():void {
  link.value=null
  if(timer !== null) clearInterval(timer)
  timer=null
  if(!disposed) emit('issued',false)
}
async function issue():Promise<void> {
  if(busy.value || props.disabled) return
  const request=++revision
  abort?.abort()
  const controller=new AbortController()
  abort=controller
  clearLink()
  expired.value=false;feedback.value=null;uncertain.value=false;busy.value=true
  try {
    const result=await props.client.issueRecoveryLink(props.userId,controller.signal)
    if(disposed || request !== revision || controller.signal.aborted) return
    now.value=Date.now()
    if(Date.parse(result.expiresAt) <= now.value) { expired.value=true;return }
    link.value=result
    emit('issued',true)
    timer=setInterval(() => { now.value=Date.now();if(remaining.value === 0) { clearLink();expired.value=true } },1000)
  } catch(cause) {
    if(disposed || request !== revision || controller.signal.aborted || cause instanceof StaleSessionGenerationError) return
    if(!(cause instanceof AdminUsersApiError) || cause.response.status >= 500) uncertain.value=true
    else {
      if([401,403,426].includes(cause.response.status)) emit('ownerError',cause)
      feedbackTone.value='danger';feedback.value='Ссылка не выдана. Попробуй ещё раз.'
    }
  } finally { if(!disposed && request === revision) busy.value=false }
}
async function copy():Promise<void> {
  const value=link.value
  if(!value || Date.parse(value.expiresAt) <= Date.now()) { clearLink();expired.value=true;return }
  const request=revision
  try {
    await navigator.clipboard.writeText(value.url)
    if(!disposed && request === revision && link.value === value) { feedbackTone.value='success';feedback.value='Ссылка скопирована.' }
  } catch {
    if(!disposed && request === revision && link.value === value) { feedbackTone.value='warning';feedback.value='Выдели ссылку и скопируй её вручную.' }
  }
}
function done():void { ++revision;abort?.abort();clearLink();feedback.value=null;uncertain.value=false;emit('done') }
watch(() => [props.userId,props.client,props.disabled] as const,() => {
  ++revision;abort?.abort();clearLink();busy.value=false;feedback.value=null;expired.value=false;uncertain.value=false
})
onBeforeUnmount(() => { disposed=true;++revision;abort?.abort();clearLink() })
</script>
<template>
  <section
    class="recovery-link-panel"
    :class="{'recovery-link-panel--issued':link}"
    :data-recovery-view="link ? 'issued' : 'issue'"
    aria-label="Восстановление доступа"
  >
    <h3 v-if="!embedded">
      {{ link ? 'Ссылка восстановления' : 'Восстановление доступа' }}
    </h3>
    <template v-if="link">
      <AdminNotice
        tone="success"
        title="Ссылка создана"
      >
        Передай её пользователю, чтобы он задал новый пароль.
      </AdminNotice>
      <label class="recovery-link-panel__field"><span>Одноразовая ссылка</span><input
        type="text"
        readonly
        :value="link.url"
        autocomplete="off"
        spellcheck="false"
        @focus="($event.target as HTMLInputElement).select()"
      ><button
        type="button"
        aria-label="Скопировать ссылку"
        :disabled="disabled"
        @click="copy"
      ><MobileIcon name="copy" /></button></label>
      <p class="admin-users-muted">
        Действует до {{ expiryLabel }}. Осталось {{ remaining }} сек.
      </p>
      <AdminNotice
        v-if="feedback"
        :tone="feedbackTone"
        :title="feedbackTone === 'success' ? 'Скопировано' : 'Не удалось скопировать'"
      >
        {{ feedback }}
      </AdminNotice>
      <button
        class="admin-users-action admin-users-footer"
        type="button"
        :disabled="disabled"
        @click="done"
      >
        Готово
      </button>
    </template>
    <template v-else>
      <AdminNotice
        v-if="uncertain"
        tone="warning"
        title="Результат не подтверждён"
      >
        Предыдущая ссылка могла быть создана, но ответ не получен. Новая выдача аннулирует все прежние ссылки.<template #actions>
          <button
            class="admin-users-action"
            type="button"
            :disabled="busy || disabled"
            @click="issue"
          >
            Создать новую ссылку
          </button>
        </template>
      </AdminNotice>
      <AdminNotice
        v-else-if="expired"
        tone="warning"
        title="Срок ссылки истёк"
      >
        Создай новую ссылку.<template #actions>
          <button
            class="admin-users-action"
            type="button"
            :disabled="busy || disabled"
            @click="issue"
          >
            Создать новую ссылку
          </button>
        </template>
      </AdminNotice>
      <template v-else>
        <p class="admin-users-muted">
          Создай одноразовую ссылку и передай её пользователю. Новая выдача аннулирует прежние ссылки.
        </p><button
          class="admin-users-action"
          type="button"
          :disabled="busy || disabled"
          :aria-busy="busy"
          @click="issue"
        >
          {{ busy ? 'Создаём ссылку…' : 'Создать ссылку восстановления' }}
        </button>
      </template>
      <AdminNotice
        v-if="feedback"
        :tone="feedbackTone"
        title="Ссылка не выдана"
      >
        {{ feedback }}
      </AdminNotice>
    </template>
  </section>
</template>
