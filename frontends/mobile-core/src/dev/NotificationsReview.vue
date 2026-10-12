<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef } from 'vue';
import NotificationsScreen from '../features/notifications/NotificationsScreen.vue';
import NotificationsEntryButton from '../features/notifications/NotificationsEntryButton.vue';
import { NotificationsUnreadState } from '../features/notifications/notifications-unread';
import { NotificationsApiError } from '../features/notifications/notifications-client';
import { NotificationsConflictError } from '../features/notifications/notifications-v2-client';
import {
  notificationPaletteIds,
  legacyNotificationTypeIds as notificationTypeIds,
  type NotificationCatalog,
  type NotificationItem,
  type NotificationPreferencesV2,
  type NotificationSignedRole,
  type NotificationsV2Api,
} from '../features/notifications/notifications-v2-models';
import { installFixtureTelegramHost, TelegramHost } from '../../../tma-vue/src/telegram';
import type { MobileHostAdapter } from '../shared/host';
import './notifications-review.pcss';
const params = new URLSearchParams(window.location.search);
if (params.get('theme') === 'light') document.documentElement.dataset.theme = 'light';
if (params.get('font') === '20') document.documentElement.style.fontSize = '20px';
const shell = params.get('shell') === 'tma' ? 'tma' : 'pwa';
if (shell === 'tma') installFixtureTelegramHost();
const host: MobileHostAdapter = shell === 'tma' ? new TelegramHost() : { backOwner: 'product' };
const role = ref<NotificationSignedRole>(
  (['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'].includes(params.get('role') ?? '')
    ? params.get('role')
    : 'STUDENT') as NotificationSignedRole,
);
const generation = ref(1),
  open = ref(params.get('open') !== 'false'),
  offline = ref(false),
  revision = ref(0),
  outcome = ref('');
const contextKey = computed(() => `${generation.value}|fixture7|${role.value}`);
const labels: Record<string, string> = {
  EXCUSE_REQUESTED: 'Новая заявка на уважительную причину',
  EXCUSE_APPROVED: 'Уважительная причина одобрена',
  EXCUSE_REJECTED: 'Уважительная причина отклонена',
  EXCUSE_CANCELLED: 'Заявка отменена',
  LATE_CHECKIN_REQUESTED: 'Новая заявка на отметку опоздания',
  LATE_CHECKIN_APPROVED: 'Отметка опоздания одобрена',
  LATE_CHECKIN_REJECTED: 'Отметка опоздания отклонена',
  LATE_CHECKIN_CANCELLED: 'Заявка на отметку отменена',
  LESSON_STARTED: 'Занятие началось',
  LESSON_CLOSED: 'Занятие завершено',
  LESSON_CANCELLED: 'Занятие отменено',
  LESSON_REMINDER: 'Напоминание об отметке',
  LESSON_BLOCKED: 'Самостоятельная отметка закрыта',
  LESSON_ONE_OFF_CREATED: 'Добавлено разовое занятие',
  HOMEWORK_PUBLISHED: 'Опубликовано домашнее задание',
  HOMEWORK_UPDATED: 'Домашнее задание обновлено',
  HOMEWORK_WEEKLY_DIGEST: 'Домашние задания на неделю',
  HOMEWORK_DUE_REMINDER: 'Срок домашнего задания подходит',
  ATTENDANCE_MARKED_BY_HEADMAN: 'Староста отметил посещаемость',
  GROUP_RENAMED: 'Название группы изменено',
  GROUP_ARCHIVED: 'Группа архивирована',
};
const paletteLabels = [
  'Фиолетовый',
  'Лавандовый',
  'Пурпурный',
  'Индиго',
  'Синий',
  'Кобальтовый',
  'Небесный',
  'Голубой',
  'Бирюзовый',
  'Зелёно-синий',
  'Зелёный',
  'Мятный',
  'Лаймовый',
  'Оливковый',
  'Жёлтый',
  'Янтарный',
  'Оранжевый',
  'Коралловый',
  'Красный',
  'Малиновый',
  'Розовый',
  'Розовый пастельный',
  'Коричневый',
  'Серый',
];
const catalog: NotificationCatalog = {
  schemaVersion: 2,
  revision: 'fixture1',
  groups: [
    { id: 'lessons', label: 'Занятия' },
    { id: 'reminders', label: 'Напоминания' },
    { id: 'homework', label: 'Домашние задания' },
    { id: 'tickets', label: 'Заявки' },
    { id: 'schedule', label: 'Расписание' },
    { id: 'group', label: 'Группа' },
  ],
  types: notificationTypeIds.map((id) => ({
    id,
    label: labels[id]!,
    groupId:
      id.startsWith('EXCUSE') || id.startsWith('LATE')
        ? 'tickets'
        : id.startsWith('HOMEWORK')
          ? 'homework'
          : id.startsWith('GROUP')
            ? 'group'
            : id === 'LESSON_REMINDER'
              ? 'reminders'
              : id === 'LESSON_ONE_OFF_CREATED'
                ? 'schedule'
                : 'lessons',
    roles: ['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'],
  })),
  palette: notificationPaletteIds.map((id, index) => ({ id, label: paletteLabels[index]! })),
};

let prefs: NotificationPreferencesV2 = {
  revision: 'p1',
  splitChannels: true,
  types: Object.fromEntries(
    notificationTypeIds.map((id) => [
      id,
      { appPush: true, telegram: id !== 'LESSON_STARTED', colorId: 'violet' },
    ]),
  ) as NotificationPreferencesV2['types'],
  mutedUntil: '2026-10-12T12:00:00Z',
  telegramLinked: false,
};
const rows = new Map<NotificationSignedRole, NotificationItem[]>();
for (const contextRole of ['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'] as const)
  rows.set(contextRole, [
    {
      id: `${contextRole}-3`,
      type: 'LESSON_CANCELLED',
      heading: 'Занятие отменено',
      summary: 'Программирование · Практика',
      sentAt: '2026-10-10T14:10:00Z',
      orderAt: '2026-10-10T14:10:00Z',
      readAt: null,
      revision: 'i1',
      details: [
        { label: 'Время', value: '12 октября · 10:40–12:10' },
        { label: 'Причина', value: 'Изменение расписания' },
      ],
      action: { kind: 'lesson', role: contextRole, lessonId: '22' },
    },
    {
      id: `${contextRole}-2`,
      type: 'EXCUSE_APPROVED',
      heading: 'Уважительная причина одобрена',
      sentAt: '2026-10-10T13:00:00Z',
      orderAt: '2026-10-10T13:00:00Z',
      readAt: '2026-10-10T13:05:00Z',
      revision: 'i1',
      details: [
        { label: 'Решение', value: 'Одобрены две пары. Одна выбранная пара не вошла в решение.' },
      ],
      action: null,
    },
    {
      id: `${contextRole}-1`,
      type: 'LESSON_REMINDER',
      heading: 'Напоминание об отметке',
      sentAt: '2026-10-10T12:00:00Z',
      orderAt: '2026-10-10T12:00:00Z',
      readAt: null,
      revision: 'i1',
      details: [{ label: 'Предмет', value: 'Программирование' }],
      action: null,
    },
  ]);
const failure = ref(params.get('failure') === '503'),
  conflictNext = ref(false);
const count = () => rows.get(role.value)!.filter((item) => item.readAt === null).length;
const api: NotificationsV2Api = {
  async catalog() {
    if (failure.value) throw new NotificationsApiError('Временно недоступно', 503);
    return structuredClone(catalog);
  },
  async inbox(options = {}) {
    if (failure.value) throw new NotificationsApiError('Временно недоступно', 503);
    return {
      role: role.value,
      items: structuredClone(
        rows.get(role.value)!.filter((item) => !options.unreadOnly || !item.readAt),
      ),
      nextCursor: null,
    };
  },
  async unreadCount() {
    return count();
  },
  async setReadState(item, read) {
    const row = rows.get(role.value)!.find((value) => value.id === item.id)!;
    if (row.revision !== item.revision) throw new NotificationsApiError('Конфликт', 409);
    row.readAt = read ? new Date().toISOString() : null;
    if (!read) row.orderAt = new Date().toISOString();
    row.revision += 'n';
    return { role: role.value, item: structuredClone(row), unreadCount: count() };
  },
  async markAllRead() {
    if (params.get('bulkFailure') === '503')
      throw new NotificationsApiError('Временно недоступно', 503);
    for (const row of rows.get(role.value)!) row.readAt = new Date().toISOString();
    return { role: role.value, unreadCount: count() };
  },
  async getPreferences() {
    return structuredClone(prefs);
  },
  async updatePreferences(value) {
    if (params.get('saveFailure') === '503')
      throw new NotificationsApiError('Временно недоступно', 503);
    if (conflictNext.value) {
      conflictNext.value = false;
      prefs.revision += 'c';
      throw new NotificationsConflictError(structuredClone(prefs));
    }
    if (value.expectedRevision !== prefs.revision)
      throw new NotificationsConflictError(structuredClone(prefs));
    prefs = {
      ...prefs,
      revision: prefs.revision + 's',
      splitChannels: value.splitChannels,
      types: structuredClone(value.types),
    };
    outcome.value = 'Настройки подтверждены; пауза сохранена';
    return structuredClone(prefs);
  },
};
const unreadOwner = new NotificationsUnreadState(),
  unread = shallowRef({ ...unreadOwner.view });
unreadOwner.bind(contextKey.value, api);
const stop = unreadOwner.subscribe(() => {
  unread.value = { ...unreadOwner.view };
});
void unreadOwner.refresh();
function changeRole(next: NotificationSignedRole): void {
  open.value = false;
  role.value = next;
  generation.value += 1;
  unreadOwner.bind(contextKey.value, api);
  void unreadOwner.refresh();
}
function toggleTheme(): void {
  document.documentElement.dataset.theme =
    document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
}
onBeforeUnmount(() => {
  stop();
  unreadOwner.bind(null, null);
});
</script>
<template>
  <section class="notifications-review-controls">
    <p>DEV · синтетический {{ shell }} API · не подтверждает доставку или backend ACL.</p>
    <button
      v-for="next in ['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'] as const"
      :key="next"
      type="button"
      @click="changeRole(next)"
    >
      {{ next }}
    </button>
    <button
      type="button"
      @click="toggleTheme"
    >
      Тема </button
    ><button
      type="button"
      @click="open = true"
    >
      Открыть
    </button>
    <label
      ><input
        v-model="offline"
        type="checkbox"
      />Офлайн</label
    ><label
      ><input
        v-model="failure"
        type="checkbox"
      />503</label
    ><label
      ><input
        v-model="conflictNext"
        type="checkbox"
      />Следующее сохранение: 409</label
    >
    <NotificationsEntryButton
      :count="unread.count"
      @click="open = true"
    /><p>{{ outcome }}</p>
  </section>
  <Teleport to="body">
    <NotificationsScreen
      v-if="open"
      :key="contextKey"
      :api="api"
      :context-key="contextKey"
      :unread-owner="unreadOwner"
      :host="host"
      :offline="offline"
      :can-open-target="!offline"
      :realtime-revision="revision"
      :open-action="
        async () => {
          outcome = 'Защищённый target в этом fixture недоступен';
          return false;
        }
      "
      @close="open = false"
    />
  </Teleport>
</template>
