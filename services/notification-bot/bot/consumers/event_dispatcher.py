"""EventDispatcher — routes incoming RabbitMQ events to the correct handler."""

import logging
import grpc
from typing import Awaitable, Callable

from aiogram import Bot

from bot.config import Settings
from bot.grpc_client.academic_client import AcademicGrpcClient
from bot.grpc_client.attendance_client import AttendanceRequestGrpcClient
from bot.services.otp_message_tracker import OtpMessageTracker
from bot.services.redis_client import ReminderRedisClient
from bot.services.request_message_tracker import RequestMessageTracker
from bot.services.send_queue import SendTask, TelegramSendQueue, RecipientSuppressed

logger = logging.getLogger(__name__)


class EventDispatcher:
    """Routes event dicts (by event_type) to registered async handlers.

    Classified delivery tasks are staged; authority failure propagates before admission.
    Other handler errors retain the existing DLQ path.
    """

    def __init__(
        self,
        bot: Bot,
        academic_client: AcademicGrpcClient,
        send_queue: TelegramSendQueue,
        redis_client: ReminderRedisClient,
        config: Settings,
        otp_tracker: OtpMessageTracker,
        request_tracker: RequestMessageTracker | None = None,
        attendance_client: AttendanceRequestGrpcClient | None = None,
    ) -> None:
        self._bot = bot
        self._academic_client = academic_client
        self._send_queue = send_queue
        self._redis_client = redis_client
        self._config = config
        self._otp_tracker = otp_tracker
        self._request_tracker = request_tracker
        self._attendance_client = attendance_client

        # Import handlers here to avoid circular imports at module level
        from bot.notifications.alert_fired import _parse_admin_ids, handle_alert_fired
        from bot.notifications.attendance_marked import handle_attendance_marked
        from bot.notifications.group_archived import handle_group_archived
        from bot.notifications.group_renamed import handle_group_renamed
        from bot.notifications.headman_alerts import handle_headman_alert
        from bot.notifications.homework import handle_homework
        from bot.notifications.lesson_blocked import handle_lesson_blocked
        from bot.notifications.lesson_cancelled import handle_lesson_cancelled
        from bot.notifications.lesson_closed import handle_lesson_closed
        from bot.notifications.lesson_one_off_cancelled import handle_lesson_one_off_cancelled
        from bot.notifications.lesson_one_off_created import handle_lesson_one_off_created
        from bot.notifications.lesson_reminder import handle_lesson_reminder
        from bot.notifications.lesson_started import handle_lesson_started
        from bot.notifications.otp_requested import handle_otp_requested
        from bot.notifications.otp_verified import handle_otp_verified
        from bot.notifications.password_changed import handle_password_changed

        # M04 Группа 9 — admin list parsed once при старте dispatcher'а.
        self._admin_ids = _parse_admin_ids(getattr(config, "admin_telegram_ids", None))

        # Handler registry: event_type -> async callable(event: dict)
        self._handlers: dict[str, Callable[[dict], Awaitable[None]]] = {
            "lesson.started": lambda event: handle_lesson_started(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                redis_client=self._redis_client,
                config=self._config,
            ),
            "lesson.reminder": lambda event: handle_lesson_reminder(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                redis_client=self._redis_client,
            ),
            "lesson.cancelled": lambda event: handle_lesson_cancelled(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            "lesson.blocked": lambda event: handle_lesson_blocked(
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
            "homework.weekly_digest": lambda event: handle_homework(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            "homework.due_reminder": lambda event: handle_homework(
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
                attendance_client=self._attendance_client,
            ),
            # 59-06 / D-28: notify the student when their excuse ticket is decided
            "excuse.decided": lambda event: self._handle_excuse_decided(event),
            "late_checkin.requested": lambda event: handle_headman_alert(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                request_tracker=self._request_tracker,
                attendance_client=self._attendance_client,
            ),
            "late_checkin.decided": lambda event: self._handle_late_checkin_decided(event),
            "lesson.closed": lambda event: handle_lesson_closed(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                redis_client=self._redis_client,
            ),
            "attendance.marked": lambda event: handle_attendance_marked(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                redis_client=self._redis_client,
                send_queue=self._send_queue,
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
            "password.changed": lambda event: handle_password_changed(
                event,
                bot=self._bot,
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
        status = str(payload.get("status") or "").lower()
        if ticket_id and self._request_tracker is not None:
            if status == "approved":
                verdict = "✅ Одобрено"
            elif status == "rejected":
                verdict = "❌ Отклонено"
            elif status == "cancelled":
                verdict = "ℹ️ Заявка отменена"
            else:
                logger.warning("Ignoring excuse.decided with unsupported status=%s", status)
                return
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
        status = str(payload.get("status") or "").lower()
        resolution_reason = str(payload.get("resolution_reason") or "").lower()
        if request_id and self._request_tracker is not None:
            if status == "approved":
                verdict = "✅ Уже подтверждено" if resolution_reason == "present_priority" else "✅ Подтверждено"
            elif status == "cancelled":
                verdict = ("✅ Уже подтверждено по геолокации"
                           if resolution_reason == "geo_confirmed"
                           else "ℹ️ Запрос отменён")
            elif status == "rejected":
                verdict = "❌ Отклонено"
            else:
                logger.warning("Ignoring late_checkin.decided with unsupported status=%s", status)
                return
            await self._close_tracked_messages("late_checkin", str(request_id), verdict)

    async def _close_tracked_messages(self, kind: str, request_id: str, verdict_line: str) -> None:
        entries = await self._request_tracker.get_all(kind, request_id)
        from bot.grpc_client import attendance_pb2
        from bot.notifications.headman_alerts import request_audience
        from bot.services.notification_prefs import NotificationPreferencesUnavailable

        canonical_group = None
        request_kind = (attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE if kind == "excuse"
                        else attendance_pb2.STUDENT_REQUEST_KIND_LATE_CHECKIN)
        unresolved = 0
        for entry in entries:
            chat_id, message_id, user_id = (entry.get(key) for key in ("chat_id", "message_id", "user_id"))
            if any(type(value) is not int or value <= 0 for value in (chat_id, message_id, user_id)):
                # Legacy tracker does not identify its original owner. Retain under existing TTL,
                # never attribute this old message to whoever owns the Telegram ID today.
                unresolved += 1
                continue
            if canonical_group is None:
                if self._attendance_client is None:
                    raise NotificationPreferencesUnavailable("Canonical tracked request authority is unavailable")
                try:
                    context = await self._attendance_client.resolve_request_notification(request_kind, request_id)
                    canonical_group = context.group_id
                    if type(canonical_group) is not int or canonical_group <= 0 \
                            or context.detail.summary.id != request_id or context.detail.summary.kind != request_kind:
                        raise ValueError("Canonical request group is incomplete")
                except grpc.aio.AioRpcError as error:
                    if error.code() in {grpc.StatusCode.NOT_FOUND, grpc.StatusCode.INVALID_ARGUMENT}:
                        return
                    raise NotificationPreferencesUnavailable("Canonical tracked request authority is unavailable") from error
                except Exception as error:
                    raise NotificationPreferencesUnavailable("Canonical tracked request authority is unavailable") from error

            audience = request_audience(self._academic_client, self._attendance_client, request_kind,
                                       request_id, canonical_group, user_id, chat_id, pending=False)

            async def close_message(chat=chat_id, message=message_id, recipient=user_id, authorization=audience):
                await self._bot.edit_message_reply_markup(chat_id=chat, message_id=message, reply_markup=None)
                # Editing and replying are distinct provider calls; revocation can
                # occur while the first call is pending.
                if not await self._send_queue._eligible(SendTask(close_message, user_id=recipient,
                        chat_id=chat, category="tickets", audience_check=authorization)):
                    raise RecipientSuppressed
                return await self._bot.send_message(chat_id=chat, text=f"Решение: {verdict_line}",
                                                    reply_to_message_id=message)

            async def cleanup(result, saved=entry):
                await self._request_tracker.delete_entry(kind, request_id, saved)

            await self._send_queue.put(SendTask(close_message, user_id=user_id, chat_id=chat_id,
                category="tickets", on_sent=cleanup,
                audience_check=audience))
        if unresolved:
            logger.warning("Deferred tracked replies with unknown original owner kind=%s id=%s count=%s",
                           kind, request_id, unresolved)

    async def dispatch(self, event: dict):
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

        async with self._send_queue.staged() as batch:
            await handler(event)
        return batch
