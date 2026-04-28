import { Injectable, Signal, computed, signal } from '@angular/core';

/**
 * Локальные настройки уведомлений пользователя (web-panel).
 *
 * Симметрия с PWA `notificationPrefs.ts`: те же 6 категорий, тот же квайт-час.
 * Хранение в `localStorage` под ключом `rct.web.notif-prefs.v1` — чтобы
 * настройки переживали закрытие вкладки. Серверу ничего не отправляем —
 * фильтрация делается на клиенте в `NotificationCenterService.handleFrame`.
 *
 * Bot-side preferences (Telegram категория `reminders`) живут отдельно в
 * Redis и управляются ботом через команду `/prefs` — синхронизации между
 * каналами нет by design (разные surfaces разных пользователей).
 */
export type NotificationCategory =
  | 'lessons'
  | 'reminders'
  | 'homework'
  | 'tickets'
  | 'schedule'
  | 'group';

export const CATEGORY_LABELS: Record<NotificationCategory, string> = {
  lessons: 'Пары (начало, отмена)',
  reminders: 'Напоминания об отметке',
  homework: 'Домашние задания',
  tickets: 'Тикеты (у.п., опоздания)',
  schedule: 'Изменения расписания',
  group: 'Группа (переименование, архив)',
};

const CATEGORY_MAP: Record<string, NotificationCategory> = {
  'lesson.started': 'lessons',
  'lesson.cancelled': 'lessons',
  'lesson.reminder': 'reminders',
  'lesson.one_off.created': 'schedule',
  'lesson.one_off.cancelled': 'schedule',
  'homework.published': 'homework',
  'homework.updated': 'homework',
  'excuse.requested': 'tickets',
  'excuse.decided': 'tickets',
  'late_checkin.requested': 'tickets',
  'late_checkin.decided': 'tickets',
  'group.renamed': 'group',
  'group.archived': 'group',
};

export function categoryOf(eventType: string): NotificationCategory | null {
  return CATEGORY_MAP[eventType] ?? null;
}

export interface QuietHours {
  /** HH:mm в локальном времени устройства. */
  from: string;
  to: string;
}

export interface NotificationPrefs {
  categories: Record<NotificationCategory, boolean>;
  quietHours: QuietHours | null;
}

export const DEFAULT_PREFS: NotificationPrefs = {
  categories: {
    lessons: true,
    reminders: true,
    homework: true,
    tickets: true,
    schedule: true,
    group: true,
  },
  quietHours: null,
};

const STORAGE_KEY = 'rct.web.notif-prefs.v1';

function loadFromStorage(): NotificationPrefs {
  try {
    if (typeof localStorage === 'undefined') return DEFAULT_PREFS;
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_PREFS;
    const parsed = JSON.parse(raw) as Partial<NotificationPrefs>;
    return {
      categories: { ...DEFAULT_PREFS.categories, ...(parsed.categories ?? {}) },
      quietHours: parsed.quietHours ?? null,
    };
  } catch {
    return DEFAULT_PREFS;
  }
}

function isQuietNow(qh: QuietHours | null, now = new Date()): boolean {
  if (!qh) return false;
  const toMinutes = (hhmm: string): number => {
    const [h, m] = hhmm.split(':').map((x) => Number.parseInt(x, 10));
    if (Number.isNaN(h) || Number.isNaN(m)) return -1;
    return h * 60 + m;
  };
  const from = toMinutes(qh.from);
  const to = toMinutes(qh.to);
  if (from < 0 || to < 0 || from === to) return false;
  const current = now.getHours() * 60 + now.getMinutes();
  if (from < to) return current >= from && current < to;
  return current >= from || current < to;
}

@Injectable({ providedIn: 'root' })
export class NotificationPrefsService {
  private readonly _prefs = signal<NotificationPrefs>(loadFromStorage());

  readonly prefs: Signal<NotificationPrefs> = this._prefs.asReadonly();
  readonly categories = computed(() => this._prefs().categories);

  setPrefs(next: NotificationPrefs): void {
    this._prefs.set(next);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      // quota — игнор
    }
  }

  toggleCategory(category: NotificationCategory): void {
    const curr = this._prefs();
    this.setPrefs({
      ...curr,
      categories: {
        ...curr.categories,
        [category]: !curr.categories[category],
      },
    });
  }

  setQuietHours(qh: QuietHours | null): void {
    this.setPrefs({ ...this._prefs(), quietHours: qh });
  }

  resetAll(): void {
    this.setPrefs(DEFAULT_PREFS);
  }

  /** Должны ли мы пропустить событие (категория выключена ИЛИ тихий час). */
  shouldSuppressBanner(eventType: string, now = new Date()): boolean {
    const cat = categoryOf(eventType);
    const prefs = this._prefs();
    if (cat && !prefs.categories[cat]) return true;
    if (isQuietNow(prefs.quietHours, now)) return true;
    return false;
  }

  /** Должны ли мы сохранить событие в историю (только категория важна). */
  shouldStoreInHistory(eventType: string): boolean {
    const cat = categoryOf(eventType);
    if (cat && !this._prefs().categories[cat]) return false;
    return true;
  }
}
