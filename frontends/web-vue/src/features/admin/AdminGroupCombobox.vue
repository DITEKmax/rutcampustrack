<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, useId, watch } from 'vue';
import type { AdminUsersClient, AdminUserGroupOption, AdminUserGroupOptionsPage } from '../../../../mobile-core/src/features/admin-users/admin-users-client';
import { useFieldPopover } from '../../shared/use-field-popover';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import '../../shared/web-fields.pcss';
const props = defineProps<{ client: AdminUsersClient; assertCurrent(): void; label: string; disabled?: boolean; allowAll?: boolean; allowArchived?: boolean }>();
const model = defineModel<number | undefined>({ default: undefined });
const id = useId();
const root = ref<HTMLElement | null>(null), input = ref<HTMLInputElement | null>(null), panel = ref<HTMLElement | null>(null);
const popup = useFieldPopover(input, panel, () => undefined, true, root);
const text = ref(''), search = ref(''), editing = ref(false), includeArchived = ref(Boolean(props.allowArchived));
const rows = shallowRef<readonly AdminUserGroupOption[]>([]), page = shallowRef<AdminUserGroupOptionsPage | null>(null);
const selected = shallowRef<AdminUserGroupOption | null>(null);
const busy = ref(false), error = ref(''), active = ref(-1);
let alive = true, revision = 0, restoringFocus = false;
let timer: ReturnType<typeof setTimeout> | null = null;
let controller: AbortController | null = null;
function cancel() {
  revision++;
  if (timer !== null) clearTimeout(timer);
  timer = null;
  controller?.abort(); controller = null; busy.value = false;
}
function optionLabel(group: AdminUserGroupOption) { return group.name + (group.archived ? ' · архив' : ''); }
function validate() { input.value?.setCustomValidity(!props.allowAll && model.value === undefined ? 'Выбери группу из списка.' : ''); }
async function load(number = 0, append = false) {
  cancel();
  if (!alive || props.disabled) return;
  const own = revision, client = props.client, term = search.value, archived = includeArchived.value;
  const request = new AbortController(); controller = request;
  busy.value = true; error.value = '';
  const current = () => alive && own === revision && client === props.client && !props.disabled && term === search.value && archived === includeArchived.value;
  try {
    const value = await client.listGroups(term, number, request.signal, archived);
    props.assertCurrent();
    if (!current()) return;
    rows.value = append ? [...rows.value, ...value.items.filter(group => !rows.value.some(row => row.id === group.id))] : value.items;
    page.value = value;
    const match = rows.value.find(group => group.id === model.value);
    if (match && !editing.value) { selected.value = match; text.value = optionLabel(match); }
    await nextTick(); popup.place();
  } catch (cause) {
    if (!current() || request.signal.aborted) return;
    try { props.assertCurrent(); error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить группы.'; }
    catch { /* Retired authority. */ }
  } finally { if (current()) busy.value = false; }
}
function schedule() {
  cancel(); rows.value = []; page.value = null; active.value = -1; error.value = '';
  if (!alive || props.disabled) return;
  busy.value = true;
  timer = setTimeout(() => { timer = null; void load(); }, 250);
}
function typed(event: Event) {
  if (props.disabled) return;
  editing.value = true; model.value = undefined; selected.value = null;
  text.value = (event.target as HTMLInputElement).value; search.value = text.value;
  input.value?.setCustomValidity(props.allowAll ? '' : 'Выбери группу из списка.');
  schedule(); popup.open();
}
function choose(group: AdminUserGroupOption | null) {
  if (props.disabled || busy.value || (group?.archived && !includeArchived.value)) return;
  props.assertCurrent();
  if (group && !rows.value.some(row => row.id === group.id)) return;
  cancel(); editing.value = false; selected.value = group; model.value = group?.id;
  text.value = group ? optionLabel(group) : ''; search.value = ''; page.value = null;
  input.value?.setCustomValidity(''); close();
}
function close() {
  restoringFocus = true;
  popup.close(false);
  input.value?.focus({ preventScroll: true });
  void nextTick(() => { restoringFocus = false; });
}
function opened() {
  if (props.disabled || restoringFocus) return;
  popup.open();
  if (!page.value && !busy.value) void load();
}
function keydown(event: KeyboardEvent) {
  if (props.disabled) return;
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault(); opened();
    active.value = Math.max(0, Math.min(rows.value.length - 1, active.value + (event.key === 'ArrowDown' ? 1 : -1)));
    void nextTick(() => panel.value?.querySelector(`[id="${id}-option-${active.value}"]`)?.scrollIntoView({ block: 'nearest' }));
  } else if (event.key === 'Enter') {
    event.preventDefault(); const option = rows.value[active.value];
    if (popup.expanded.value && option) choose(option); else opened();
  } else if (event.key === 'Escape') { event.preventDefault(); close(); }
}
watch(model, value => {
  if (value === undefined) {
    if (!editing.value) { text.value = ''; search.value = ''; selected.value = null; }
  } else {
    editing.value = false;
    const option = rows.value.find(group => group.id === value) ?? (selected.value?.id === value ? selected.value : null);
    selected.value = option; text.value = option ? optionLabel(option) : `№ ${value}`;
  }
  void nextTick(validate);
}, { immediate: true });
watch(includeArchived, schedule);
watch(() => [props.client, props.disabled] as const, () => {
  cancel(); rows.value = []; page.value = null;
  if (props.disabled) popup.close(false); else void load();
});
onMounted(() => { validate(); void load(); });
onBeforeUnmount(() => { alive = false; cancel(); });
</script>
<template>
  <div ref="root" class="web-field admin-group-combobox">
    <label :for="id">{{ label }}</label>
    <div class="admin-combobox-control">
      <input :id="id" ref="input" type="text" :value="text" :disabled="disabled" :required="!allowAll" autocomplete="off" role="combobox" aria-autocomplete="list" :aria-expanded="popup.expanded.value" :aria-controls="`${id}-list`" :aria-activedescendant="active >= 0 && rows[active] ? `${id}-option-${active}` : undefined" :placeholder="allowAll ? 'Все группы' : 'Введи название группы'" @input="typed" @focus="opened" @keydown="keydown" @focusout="popup.focusout">
      <MobileIcon name="search" />
    </div>
    <div ref="panel" class="web-field-popover admin-combobox-options" popover="manual" @toggle="popup.toggled" @focusout="popup.focusout" @keydown.esc.stop.prevent="close">
      <label v-if="allowArchived" class="admin-check"><input v-model="includeArchived" type="checkbox" :disabled="disabled">Включая архивные группы</label>
      <div :id="`${id}-list`" role="listbox" :aria-label="label" :aria-busy="busy">
        <button v-if="allowAll" class="web-select-option" type="button" role="option" :aria-selected="model === undefined && !editing" :disabled="busy || disabled" @click="choose(null)">Все группы</button>
        <button v-for="(group, index) in rows" :id="`${id}-option-${index}`" :key="group.id" class="web-select-option" type="button" role="option" tabindex="-1" :aria-selected="model === group.id" :disabled="busy || disabled || (!includeArchived && group.archived)" @pointermove="active = index" @click="choose(group)">{{ optionLabel(group) }}</button>
      </div>
      <p v-if="busy" class="web-caption" role="status">Загружаем группы…</p>
      <p v-else-if="!rows.length && !error" class="web-caption" role="status">Группы не найдены.</p>
      <p v-if="error" class="admin-danger" role="alert">{{ error }}</p>
      <button v-if="error" type="button" class="web-button" :disabled="busy || disabled" @click="load()">Повторить</button>
      <button v-if="page && page.number + 1 < page.totalPages" type="button" class="web-button" :disabled="busy || disabled" @click="load(page.number + 1, true)">Показать ещё группы</button>
    </div>
  </div>
</template>
