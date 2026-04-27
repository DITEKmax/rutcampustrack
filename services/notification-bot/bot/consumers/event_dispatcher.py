"""EventDispatcher — routes incoming RabbitMQ events to the correct handler."""

import logging
from typing import Awaitable, Callable

from aiogram import Bot

from bot.config import Settings
from bot.grpc_client.academic_client import AcademicGrpcClient
from bot.services.otp_message_tracker import OtpMessageTracker
from bot.services.redis_client import ReminderRedisClient
from bot.services.request_message_tracker import RequestMessageTracker
from bot.services.send_queue import TelegramSendQueue

logger = logging.getLogger(__name__)


class EventDispatcher:
    """Routes event dicts (by event_type) to registered async handlers.

    All handler exceptions are caught and logged so that RabbitMQ messages
    are always acknowledged (no infinite requeue loop).
    """

    def __init__(
        self,
        bot: Bot,
        academic_client: AcademicGrpcClient,
        send_queue: TelegramSendQueue,
        redis_client: ReminderRedisClient,
        config: Settings,
        otp_tracker: OtpMessageTracker,
        reminder_scheduler=None,  # Optional — injected by __main__.py (Plan 25-02)
        request_tracker: RequestMessageTracker | None = None,
    ) -> None:
        self._bot = bot
        self._academic_client = academic_client
        self._send_queue = send_queue
        self._redis_client = redis_client
        self._config = config
        self._otp_tracker = otp_tracker
        self._reminder_scheduler = reminder_scheduler
        self._request_tracker = request_tracker

        # Import handlers here to avoid circular imports at module level
        from bot.notifications.alert_fired import _parse_admin_ids, handle_alert_fired
        from bot.notifications.attendance_marked import handle_attendance_marked
        from bot.notifications.group_archived import handle_group_archived
        from bot.notifications.group_renamed import handle_group_renamed
        from bot.notifications.headman_alerts import handle_headman_alert
        from bot.notifications.homework import handle_homework
        from bot.notifications.lesson_cancelled import handle_lesson_cancelled
        from bot.notifications.lesson_closed import handle_lesson_closed
        from bot.notifications.lesson_one_off_cancelled import handle_lesson_one_off_cancelled
        from bot.notifications.lesson_one_off_created import handle_lesson_one_off_created
        from bot.notifications.otp_requested import handle_otp_requested
        from bot.notifications.otp_verified import handle_otp_verified

        # M04 Группа 9 — admin list parsed once при старте dispatcher'а.
        self._admin_ids = _parse_admin_ids(getattr(config, "admin_telegram_ids", None))

        # Handler registry: event_type -> async callable(event: dict)
        self._handlers: dict[str, Callable[[dict], Awaitable[None]]] = {
            "lesson.started": lambda event: self._handle_lesson_started_with_scheduling(event),
            "lesson.cancelled": lambda event: handle_lesson_cancelled(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            # 60-04: one-off lesson added/removed by a headman
            "lesson.one_off.created": lambda event: handle_lesson_one_off_created(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            "lesson.one_off.cancelled": lambda event: handle_lesson_one_off_cancelled(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            "homework.published": lambda event: handle_homework(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            "homework.updated": lambda event: handle_homework(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            "excuse.requested": lambda event: handle_headman_alert(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                request_tracker=self._request_tracker,
            ),
            # 59-06 / D-28: notify the student when their excuse ticket is decided
            "excuse.decided": lambda event: self._handle_excuse_decided(event),
            "late_checkin.requested": lambda event: handle_headman_alert(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                request_tracker=self._request_tracker,
            ),
            "late_checkin.decided": lambda event: self._handle_late_checkin_decided(event),
            "lesson.closed": lambda event: handle_lesson_closed(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                redis_client=self._redis_client,
                reminder_scheduler=self._reminder_scheduler,
            ),
            "attendance.marked": lambda event: handle_attendance_marked(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                redis_client=self._redis_client,
            ),
            # 58-07 / BUG-006-6: notify students when group is renamed / archived
            "group.renamed": lambda event: handle_group_renamed(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            "group.archived": lambda event: handle_group_archived(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            # M09 G2 (08 P0-2): OTP код теперь доставляется через event,
            # а не в HTTP body. Handler отправляет код пользователю в Telegram.
            "otp.requested": lambda event: handle_otp_requested(
                event,
                bot=self._bot,
                tracker=self._otp_tracker,
            ),
            # OTP login cleanup: remove code + request messages when user logs in
            "otp.verified": lambda event: handle_otp_verified(
                event,
                bot=self._bot,
                tracker=self._otp_tracker,
            ),
            # M04 Группа 9: Alertmanager → notification-web → RabbitMQ →
            # этот handler. Форвардит alert админу в Telegram.
            "alert.fired": lambda event: handle_alert_fired(
                event,
                bot=self._bot,
                send_queue=self._send_queue,
                admin_ids=self._admin_ids,
            ),
        }

    async def _handle_excuse_decided(self, event: dict) -> None:
        """Извещаем студента + закрываем карточки у старост в Telegram.

        Симметрия с web-панелью: когда решение пришло через веб (или через TG
        другого старосты), мы убираем inline-клавиатуру у всех сохранённых
        карточек и добавляем вердикт. Если карточку в TG уже отредактировал
        локальный callback (excuse.py), Telegram вернёт 400 "message not
        modified" — это не ошибка, просто пропускаем.
        """
        from bot.notifications.student_alerts import handle_student_alert

        await handle_student_alert(
            event,
            bot=self._bot,
            academic_client=self._academic_client,
            send_queue=self._send_queue,
        )

        payload = event.get("payload") or {}
        ticket_id = payload.get("ticket_id")
        status = payload.get("status")
        if ticket_id and self._request_tracker is not None:
            verdict = "✅ Одобрено" if status == "approved" else "❌ Отклонено"
            await self._close_tracked_messages("excuse", str(ticket_id), verdict)

    async def _handle_late_checkin_decided(self, event: dict) -> None:
        """Как _handle_excuse_decided, но для late-checkin-запросов."""
        from bot.notifications.student_alerts import handle_student_alert

        await handle_student_alert(
            event,
            bot=self._bot,
            academic_client=self._academic_client,
            send_queue=self._send_queue,
        )

        payload = event.get("payload") or {}
        request_id = payload.get("request_id")
        status = payload.get("status")
        if request_id and self._request_tracker is not None:
            verdict = "✅ Подтверждено" if status == "approved" else "❌ Отклонено"
            await self._close_tracked_messages("late_checkin", str(request_id), verdict)

    async def _close_tracked_messages(self, kind: str, request_id: str, verdict_line: str) -> None:
        entries = await self._request_tracker.get_all(kind, request_id)
        for entry in entries:
            chat_id = entry.get("chat_id")
            message_id = entry.get("message_id")
            if chat_id is None or message_id is None:
                continue
            try:
                # Убираем клавиатуру и шлём verdict отдельным reply-сообщением:
                # Telegram Bot API не возвращает исходный текст сообщения, поэтому
                # edit_text/edit_caption без сохранённого контекста перезапишет
                # карточку. Reply — минимально-инвазивный вариант: сохраняет
                # оригинал в истории чата и однозначно связан с ним.
                await self._bot.edit_message_reply_markup(chat_id=chat_id, message_id=message_id, reply_markup=None)
                await self._bot.send_message(
                    chat_id=chat_id,
                    text=verdict_line,
                    reply_to_message_id=message_id,
                )
            except Exception:
                logger.debug(
                    "Failed to close TG message kind=%s id=%s chat=%s msg=%s",
                    kind,
                    request_id,
                    chat_id,
                    message_id,
                    exc_info=True,
                )
        await self._request_tracker.delete(kind, request_id)

    async def _handle_lesson_started_with_scheduling(self, event: dict) -> None:
        """Handle lesson.started: send initial messages then schedule reminders.

        This wrapper calls handle_lesson_started (NOTIF-01) and then, if a
        ReminderScheduler is available, schedules midpoint and near-end reminders
        (NOTIF-02 and NOTIF-03).
        """
        from bot.notifications.lesson_started import handle_lesson_started

        await handle_lesson_started(
            event,
            bot=self._bot,
            academic_client=self._academic_client,
            send_queue=self._send_queue,
            redis_client=self._redis_client,
            config=self._config,
        )
        if self._reminder_scheduler is not None:
            payload = event.get("payload", {})
            lesson_id = payload.get("lesson_id")
            group_id = payload.get("group_id")
            start_time = payload.get("start_time")
            end_time = payload.get("end_time")
            if all(v is not None for v in [lesson_id, group_id, start_time, end_time]):
                self._reminder_scheduler.schedule_reminders(lesson_id, group_id, start_time, end_time)

    async def dispatch(self, event: dict) -> None:
        """Dispatch an event dict to the appropriate handler.

        Unknown event types are logged at DEBUG and silently ignored
        (compatibility — нам приходит fanout, у некоторых event_type
        просто нет handler'а в боте, это не ошибка).

        Handler exceptions **пропускаются вверх** (M16 G2): caller
        (`event_consumer.py`) делает NACK через `requeue=False` →
        message уходит в DLQ → triage вручную (см.
        `docs/operations/runbooks/dlq-triage.md`).

        До M16 G2 handler exceptions swallow'ились здесь, что
        конфликтовало с G24-fix-2 в consumer'е (комментировал DLQ-flow,
        но swallow на dispatcher-уровне делал silent loss). Сейчас
        восстановлена консистентность: silent loss → DLQ + alert.
        """
        event_type = event.get("event_type")
        handler = self._handlers.get(event_type)

        if handler is None:
            logger.debug("Unhandled event type: %s", event_type)
            return

        await handler(event)
