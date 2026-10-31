<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue';
import type {
  AdminUsersClient,
  AdminUserGroupOptionsPage,
} from '../../../../mobile-core/src/features/admin-users/admin-users-client';
const props = defineProps<{
  client: AdminUsersClient;
  assertCurrent: () => void;
  label: string;
  disabled?: boolean;
}>();
const model = defineModel<number | undefined>({ default: undefined });
const search = ref('');
const result = shallowRef<AdminUserGroupOptionsPage | null>(null);
const error = ref('');
const busy = ref(false);
let revision = 0;
async function load(page = 0) {
  const own = ++revision;
  busy.value = true;
  error.value = '';
  try {
    const value = await props.client.listGroups(search.value, page);
    props.assertCurrent();
    if (own === revision) result.value = value;
  } catch (cause) {
    if (own === revision)
      error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить группы.';
  } finally {
    if (own === revision) busy.value = false;
  }
}
onMounted(() => load());
</script>
<template>
  <fieldset
    class="admin-picker"
    :disabled="disabled"
  >
    <legend>{{ label }}</legend>
    <div class="admin-actions">
      <label class="web-field">Поиск группы<input
        v-model="search"
        type="search"
        @keydown.enter.prevent="load()"
      ></label><button
        type="button"
        class="web-button"
        :disabled="busy"
        @click="load()"
      >
        Найти
      </button>
    </div>
    <label class="web-field">Группа<select
      v-model="model"
      required
    ><option
      :value="undefined"
      disabled
    >Выбери группу</option><option
      v-for="group in result?.items"
      :key="group.id"
      :value="group.id"
    >{{ group.name }}</option></select></label>
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
        Предыдущие
      </button><span>{{ result.number + 1 }} / {{ result.totalPages }}</span><button
        type="button"
        class="web-button"
        :disabled="busy || result.number + 1 >= result.totalPages"
        @click="load(result.number + 1)"
      >
        Следующие
      </button>
    </div>
    <p
      v-if="error"
      class="admin-danger"
      role="alert"
    >
      {{ error }}
    </p>
  </fieldset>
</template>
