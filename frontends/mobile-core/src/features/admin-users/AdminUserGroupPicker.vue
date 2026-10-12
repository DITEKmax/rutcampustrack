<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import {
  AdminUsersApiError,
  type AdminUsersClient,
  type AdminUserGroupOption,
} from './admin-users-client';
import { StaleSessionGenerationError } from '../../shared/session-owner';
const props = defineProps<{
  client: AdminUsersClient;
  modelValue: string;
  label: string;
  excludedGroupId?: number | null;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: string]; ownerError: [cause: unknown] }>();
const groups = ref<readonly AdminUserGroupOption[]>([]);
const selectedName = ref('');
const search = ref('');
const page = ref(0);
const totalPages = ref(0);
const loading = ref(false);
const error = ref<string | null>(null);
let disposed = false;
let revision = 0;
let abort: AbortController | null = null;
async function load(next = 0): Promise<void> {
  const request = ++revision;
  abort?.abort();
  const controller = new AbortController();
  abort = controller;
  loading.value = true;
  error.value = null;
  try {
    const result = await props.client.listGroups(search.value, next, controller.signal);
    if (disposed || request !== revision) return;
    groups.value = result.items.filter((group) => group.id !== props.excludedGroupId);
    page.value = result.number;
    totalPages.value = result.totalPages;
    selectedName.value =
      groups.value.find((group) => String(group.id) === props.modelValue)?.name ??
      selectedName.value;
  } catch (cause) {
    if (
      disposed ||
      request !== revision ||
      controller.signal.aborted ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    error.value = 'Не удалось загрузить группы.';
    if (cause instanceof AdminUsersApiError && [401, 403].includes(cause.response.status))
      emit('ownerError', cause);
  } finally {
    if (!disposed && request === revision) loading.value = false;
  }
}
function select(group: AdminUserGroupOption, event: Event): void {
  selectedName.value = group.name;
  emit('update:modelValue', String(group.id));
  (event.currentTarget as HTMLElement).closest('details')?.removeAttribute('open');
}
onMounted(() => void load());
onBeforeUnmount(() => {
  disposed = true;
  ++revision;
  abort?.abort();
});
</script>
<template>
  <details
    class="admin-users-select"
    data-picker="group"
  >
    <summary
      ><span
        ><small>{{ label }}</small
        >{{ modelValue && selectedName ? selectedName : 'Выбрать группу' }}</span
      ><MobileIcon name="chevron-down"
    /></summary>
    <div class="admin-users-select__options">
      <div
        class="admin-users-group-search"
        role="search"
      >
        <input
          v-model="search"
          type="search"
          placeholder="Найти группу"
          aria-label="Найти группу"
          @input="load()"
          @keydown.enter.prevent="load()"
        />
      </div>
      <p
        v-if="loading"
        role="status"
      >
        Загружаем группы…
      </p>
      <template v-else-if="error">
        <p role="alert"> {{ error }} </p
        ><button
          type="button"
          @click="load()"
        >
          Попробовать ещё раз
        </button>
      </template>
      <template v-else>
        <button
          v-for="group in groups"
          :key="group.id"
          type="button"
          :aria-pressed="modelValue === String(group.id)"
          @click="select(group, $event)"
        >
          {{ group.name }} </button
        ><p
          v-if="!groups.length"
          role="status"
        >
          Группы не найдены
        </p>
      </template>
      <div
        v-if="totalPages > 1"
        class="admin-users-group-pages"
      >
        <button
          type="button"
          :disabled="loading || page === 0"
          @click="load(page - 1)"
        >
          Назад </button
        ><span>{{ page + 1 }} / {{ totalPages }}</span
        ><button
          type="button"
          :disabled="loading || page + 1 >= totalPages"
          @click="load(page + 1)"
        >
          Дальше
        </button>
      </div>
    </div>
  </details>
</template>
