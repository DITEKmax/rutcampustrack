<script setup lang="ts">
import { adminGroupProgramTypes, type AdminGroup } from '../../../../mobile-core/src/features/admin-groups/admin-groups-client';
defineProps<{ items: readonly AdminGroup[]; selectedId?: number | undefined; pinnedId?: number | undefined; creating?: boolean; disabled?: boolean }>();
defineEmits<{ open: [group: AdminGroup] }>();
</script>
<template>
  <div class="admin-registry">
    <slot v-if="creating" name="editor" />
      <div
        v-if="items.length"
        class="admin-table-wrap"
        role="region"
        aria-label="Реестр групп"
        tabindex="0"
      >
        <table class="admin-table">
          <caption class="web-sr-only">
            Реестр групп
          </caption><thead>
            <tr>
              <th scope="col">
                Группа
              </th><th scope="col">
                Курс / срок
              </th><th scope="col">
                Программа
              </th><th scope="col">
                Студенты
              </th><th scope="col">
                Староста
              </th><th scope="col">
                Действия
              </th>
            </tr>
          </thead><tbody>
            <template v-for="group in items" :key="group.id">
            <tr :class="{ 'is-selected': selectedId === group.id }">
              <td>
                {{ group.name
                }}<small v-if="pinnedId === group.id" class="web-caption">Выбранная группа вне текущей страницы или фильтров</small><span
                  v-if="group.draftReason"
                  class="admin-subtext"
                >{{ group.draftReason }}</span>
              </td><td>
                {{ group.currentCourse ?? '—' }} /
                {{ group.trainingDurationYears ?? 'Не указан' }}
              </td><td>
                {{
                  adminGroupProgramTypes.find((p) => p.digit === group.programTypeDigit)?.label ??
                    'Не указана'
                }}
              </td><td>{{ group.studentCount }}</td><td>{{ group.headmanFio ?? 'Не назначен' }}</td><td>
                <button
                  class="web-button"
                  :disabled="disabled"
                  :aria-label="`Открыть группу ${group.name}`"
                :aria-expanded="selectedId === group.id"
                  @click="$emit('open', group)"
                >
                  Открыть
                </button>
              </td>
            </tr>
            <tr v-if="!creating && selectedId === group.id" class="admin-detail-row"><td colspan="6"><slot name="editor" /></td></tr>
            </template>
          </tbody>
        </table>
      </div>
  </div>
</template>
