<script setup lang="ts">
import type { AdminUser } from '../../../../mobile-core/src/features/admin-users/admin-users-client';
import { roleLabels, statusLabels } from './use-admin';
defineProps<{ items: readonly AdminUser[]; disabled?: boolean; readOnly?: boolean }>();
defineEmits<{ open: [user: AdminUser] }>();
</script>
<template>
  <div
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
        <tr
          v-for="user in items"
          :key="user.id"
        >
          <td>
            {{ user.fullName }}<span class="admin-subtext">{{ user.login }}</span>
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
              @click="$emit('open', user)"
            >
              Открыть
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
