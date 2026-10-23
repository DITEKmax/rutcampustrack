<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue';
import { AdminUsersClient } from '../../../../mobile-core/src/features/admin-users/admin-users-client';
import { AdminGroupsClient } from '../../../../mobile-core/src/features/admin-groups/admin-groups-client';
import { AdminAttendanceClient } from '../../../../mobile-core/src/features/admin-attendance/admin-attendance-client';
import {
  ReportDownloadClient,
  exportStateLabel,
  type ReportFormatOption,
  type ReportDownloadFormat,
  type AdminUsersReportSelector,
  type AdminGroupsReportSelector,
  type AdminGroupAttendanceReportSelector,
  type ReportDownloadTicketRequest,
} from '../../../../mobile-core/src/shared/report-download-client';
import { createBrowserReportDownloadPort } from '../../../../mobile-core/src/shared/report-download-browser';
import { useAdmin } from './use-admin';
const props = defineProps<{
  kind: 'users' | 'groups' | 'attendance';
  selector:
    | Omit<AdminUsersReportSelector, 'format'>
    | Omit<AdminGroupsReportSelector, 'format'>
    | Omit<AdminGroupAttendanceReportSelector, 'format'>;
}>();
const admin = useAdmin();
const { busy, error, notice } = admin;
const format = ref<ReportDownloadFormat>('xlsx');
const formats = shallowRef<readonly ReportFormatOption[]>([]);
const tickets = new ReportDownloadClient(admin.options);
const download = createBrowserReportDownloadPort(tickets, admin.options.fetcher);
async function load() {
  const value =
    props.kind === 'users'
      ? await new AdminUsersClient(admin.options).exportFormats()
      : props.kind === 'groups'
        ? await new AdminGroupsClient(admin.options).exportFormats()
        : await new AdminAttendanceClient(admin.options).getFormats();
  admin.assertCurrent();
  formats.value = value;
}
async function save() {
  await admin.run(
    async () => {
      const request: ReportDownloadTicketRequest =
        props.kind === 'users'
          ? {
              kind: 'ADMIN_USERS',
              adminUsers: {
                ...(props.selector as Omit<AdminUsersReportSelector, 'format'>),
                format: format.value,
              },
            }
          : props.kind === 'groups'
            ? {
                kind: 'ADMIN_GROUPS',
                adminGroups: {
                  ...(props.selector as Omit<AdminGroupsReportSelector, 'format'>),
                  format: format.value,
                },
              }
            : {
                kind: 'ADMIN_GROUP_ATTENDANCE',
                adminGroupAttendance: {
                  ...(props.selector as Omit<AdminGroupAttendanceReportSelector, 'format'>),
                  format: format.value,
                },
              };
      const result = await download.download(
        request,
        () => {
          try {
            admin.assertCurrent();
            return true;
          } catch {
            return false;
          }
        },
        (state) => {
          admin.assertCurrent();
          notice.value = exportStateLabel(state);
        },
      );
      admin.assertCurrent();
      if (result === 'accepted') notice.value = 'Файл получен. Скачивание передано браузеру.';
    },
    props.kind === 'users'
      ? 'USERS_VIEW'
      : props.kind === 'groups'
        ? 'GROUPS_VIEW'
        : 'DASHBOARD_VIEW',
  );
}
onMounted(() =>
  admin.init(
    props.kind === 'users'
      ? 'USERS_VIEW'
      : props.kind === 'groups'
        ? 'GROUPS_VIEW'
        : 'DASHBOARD_VIEW',
    load,
  ),
);
</script>
<template>
  <div class="admin-export">
    <div class="admin-actions">
      <label class="web-field">Формат выгрузки<select
        v-model="format"
        :disabled="busy || !formats.length"
      ><option
        v-for="option in formats"
        :key="option.code"
        :value="option.code"
      >{{ option.label }}</option></select></label><button
        class="web-button"
        :disabled="busy || !formats.length"
        @click="save"
      >
        {{ busy ? 'Готовим файл…' : 'Экспортировать' }}
      </button>
    </div><p
      v-if="error"
      class="admin-danger"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="notice"
      class="web-caption"
      role="status"
    >
      {{ notice }}
    </p>
  </div>
</template>
