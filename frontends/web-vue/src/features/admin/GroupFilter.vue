<script setup lang="ts">
import { onMounted, ref, shallowRef, watch } from 'vue';
import type {
  AdminUsersClient,
  AdminUserGroupOptionsPage,
} from '../../../../mobile-core/src/features/admin-users/admin-users-client';
const props = defineProps<{
  client: AdminUsersClient;
  assertCurrent(): void;
  disabled?: boolean;
}>();
const model = defineModel<number | undefined>({ default: undefined });
const search = ref(''),
  includeArchived = ref(true);
const result = shallowRef<AdminUserGroupOptionsPage | null>(null);
const selectedName = ref(''),
  busy = ref(false),
  error = ref('');
let revision = 0;
async function load(number = 0) {
  const own = ++revision;
  busy.value = true;
  error.value = '';
  try {
    const value = await props.client.listGroups(
      search.value,
      number,
      undefined,
      includeArchived.value,
    );
    props.assertCurrent();
    if (own === revision) {
      result.value = value;
      const selected = value.items.find((g) => g.id === model.value);
      if (selected) selectedName.value = selected.name + (selected.archived ? ' · архив' : '');
    }
  } catch (cause) {
    try {
      props.assertCurrent();
      if (own === revision)
        error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить группы.';
    } catch {
      /* The parent authority retired this picker. */
    }
  } finally {
    try {
      props.assertCurrent();
      if (own === revision) busy.value = false;
    } catch {
      /* Retired. */
    }
  }
}
function choose() {
  const selected = result.value?.items.find((g) => g.id === model.value);
  selectedName.value = selected ? selected.name + (selected.archived ? ' · архив' : '') : '';
}
watch(model, choose, { immediate: true });
onMounted(() => load());
</script>
<template>
  <details class="admin-picker">
    <summary>Группа: {{ model ? selectedName || `№ ${model}` : 'все группы' }}</summary>
    <fieldset
      :disabled="disabled"
      class="web-stack"
    >
      <legend class="web-sr-only">
        Фильтр по группе
      </legend>
      <div class="admin-actions">
        <label class="web-field">Найти группу<input
          v-model="search"
          type="search"
          @keydown.enter.prevent="load()"
        ></label><label><input
          v-model="includeArchived"
          type="checkbox"
        >
          Включая архивные группы</label><button
          type="button"
          class="web-button"
          :disabled="busy"
          @click="load()"
        >
          Найти группы
        </button>
      </div>
      <label class="web-field">Группа для фильтра<select
        v-model="model"
        :disabled="busy"
        @change="choose"
      ><option :value="undefined">Все группы</option><option
        v-if="model && !result?.items.some((g) => g.id === model)"
        :value="model"
      >{{ selectedName || `Группа № ${model}` }}</option><option
        v-for="group in result?.items"
        :key="group.id"
        :value="group.id"
      >{{ group.name }}{{ group.archived ? ' · архив' : '' }}</option></select></label>
      <div
        v-if="result && result.totalPages > 1"
        class="admin-actions"
      >
        <button
          type="button"
          class="web-button"
          :disabled="busy || result.number === 0"
          @click="load(result.number - 1)"
        >
          Предыдущие группы
        </button><span>{{ result.number + 1 }} / {{ result.totalPages }}</span><button
          type="button"
          class="web-button"
          :disabled="busy || result.number + 1 >= result.totalPages"
          @click="load(result.number + 1)"
        >
          Следующие группы
        </button>
      </div>
      <p
        v-if="error"
        class="admin-danger"
        role="alert"
      >
        {{ error }}
      </p>
      <p class="web-caption">
        После выбора примени фильтры кнопкой «Найти». Отбор учитывает текущие и сохранённые связи
        ролей с группой.
      </p>
    </fieldset>
  </details>
</template>
