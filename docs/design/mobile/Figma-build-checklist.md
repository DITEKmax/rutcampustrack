# RutCampusTrack mobile — Figma build checklist

## 1. Собрать сначала

- [ ] Student probe: Сегодня → текущая пара → отметка.
- [ ] Headman probe: текущая пара → 27 студентов → 4 исключения → review/commit.
- [ ] Statistics probe: Metric Lens → студент → период.
- [ ] На каждом probe: 360, 390, 430 и stress 320.

## 2. Shell variants

- [ ] PWA browser: browser Back/chrome не дублируются.
- [ ] PWA standalone: свой Back на detail, собственный sticky action.
- [ ] TMA overview: custom bottom nav; native Back/Main скрыты.
- [ ] TMA task: custom nav скрыт; native Back + contextual MainButton.
- [ ] TMA compact и expanded работают без fullscreen.
- [ ] Keyboard: nav скрыт, поле/CTA не перекрыты, есть «Готово» и draft recovery.
- [ ] Safe-area: device + Telegram content inset отражены в annotations.

## 3. Навигация

- [ ] Student: Сегодня · Задания · Посещаемость · Ещё · Профиль.
- [ ] Headman: Сегодня · Учёт · Заявки · Ещё · Профиль.
- [ ] Teacher: Сегодня · Посещаемость · Статистика · Карта · Профиль.
- [ ] Admin: Обзор · Пользователи · Группы · Ещё · Профиль.
- [ ] «Ещё» старосты — route с секциями «Разделы» и «Управление».
- [ ] У каждого detail один canonical parent; deep link не создаёт screen copy.
- [ ] Role switch открывает Today/Overview новой роли и сбрасывает старый stack.

## 4. Плотные данные

- [ ] Журнал: list + exceptions default; focus deck как вариант; full review до commit.
- [ ] Статистика: Metric Lens + compact bars; Compare Tray максимум 3.
- [ ] Student history: status-beads/Attendance Ribbon, затем day/period detail.
- [ ] Schedule: parity/week strip → day agenda → lesson detail → leaf editor.
- [ ] Registry: server search → compact card → одно раскрытие/full detail.
- [ ] Никакой матрицы, swipe-only статуса или horizontal-scroll реестра.

## 5. Mobile visual extension

- [ ] Один fluid/gradient focus на экран.
- [ ] Dense lists/forms/charts на opaque solid surfaces.
- [ ] Glass только PWA nav/context dock/map controls; solid fallback готов.
- [ ] Radii из системы: 10/12/16/24/full; не создавать 28/30/32 одновременно.
- [ ] Shape имеет словарное значение: blob=current, capsule=filter, cut=managed.
- [ ] Status = слово + symbol/shape + semantic color.
- [ ] Morph только card→related detail; reduced-motion = fade/instant.
- [ ] Все новые gradient/material/shape/motion — token requests, не local values.

## 6. States и QA

- [ ] default · scrolled · pressed · loading · empty · error.
- [ ] stale · offline draft · outbox · sending · server ACK · conflict.
- [ ] long ФИО/предмет, 30 студентов, large text/200% zoom.
- [ ] reduced motion и increased contrast.
- [ ] iOS · Android · Telegram Desktop · Telegram Web.
- [ ] Main App · Menu Button · notification/deep link · reopen/resume.
- [ ] Нет двойной нижней панели и дублирующего Back.
- [ ] Tap budget подписан на critical flows.

## 7. До pixel final

- [ ] Получены свежие 1920 dark exports преподавателя и администратора.
- [ ] Владелец решил student check-in, exception attendance, mobile admin actions и map data.
- [ ] Backend подтвердил bootstrap, drafts/revisions/idempotency, deep links и role linking.
- [ ] Три probes прошли task test; tab order меняется только по данным.

