<script setup lang="ts">
import type { AdminUser } from '../../../../mobile-core/src/features/admin-users/admin-users-client';
import { roleLabels, statusLabels } from './use-admin';
defineProps<{ items: readonly AdminUser[]; disabled?: boolean; readOnly?: boolean; selectedId?: number | undefined; pinnedId?: number | undefined; creating?: boolean }>();
defineEmits<{ open: [user: AdminUser] }>();
</script>
<template>
  <div class="admin-registry">
    <slot v-if="creating && !readOnly" name="editor" />
  <div
    v-if="items.length"
    class="admin-table-wrap"
    role="region"
    aria-label="Реестр пользователей"
    tabindex="0"
  >
    <table class="admin-table">
      <caption class="web-sr-only">
        Реестр пользователей
      </caption>
      <thead>
        <tr>
          <th scope="col">
            ФИО / логин
          </th>
          <th scope="col">
            Текущие роли и группы
          </th>
          <th scope="col">
            Табельный номер
          </th>
          <th scope="col">
            Telegram ID
          </th>
          <th
            v-if="!readOnly"
            scope="col"
          >
            Действия
          </th>
        </tr>
      </thead>
      <tbody>
        <template v-for="user in items" :key="user.id">
        <tr :class="{ 'is-selected': selectedId === user.id }">
          <td>
            {{ user.fullName }}<span class="admin-subtext">{{ user.login }}</span><small v-if="pinnedId === user.id" class="web-caption">Выбранная запись вне текущей страницы или фильтров</small>
          </td>
          <td>
            <div
              v-for="grant in user.roles"
              :key="grant.role"
            >
              {{ roleLabels[grant.role] }} · {{ statusLabels[grant.status] }}
              <span
                v-if="grant.groupName"
                class="admin-subtext"
              >{{ grant.groupName }}</span>
            </div>
          </td>
          <td>{{ user.employeeNumber ?? '—' }}</td>
          <td>{{ user.telegramId ?? '—' }}</td>
          <td v-if="!readOnly">
            <button
              class="web-button"
              :disabled="disabled"
              :aria-label="`Открыть ${user.fullName}`"
              :aria-expanded="selectedId === user.id"
              @click="$emit('open', user)"
            >
              Открыть
            </button>
          </td>
        </tr>
        <tr v-if="!readOnly && !creating && selectedId === user.id" class="admin-detail-row"><td colspan="5"><slot name="editor" /></td></tr>
        </template>
      </tbody>
    </table>
  </div>
  </div>
</template>
