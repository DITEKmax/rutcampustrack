"""EventDispatcher — routes incoming RabbitMQ events to the correct handler."""

import logging
from typing import Awaitable, Callable

from aiogram import Bot

from bot.config import Settings
from bot.grpc_client.academic_client import AcademicGrpcClient
from bot.grpc_client.attendance_client import AttendanceRequestGrpcClient
from bot.services.otp_message_tracker import OtpMessageTracker
from bot.services.redis_client import ReminderRedisClient
from bot.services.request_message_tracker import RequestMessageTracker
from bot.services.send_queue import TelegramSendQueue

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
        credential_cards=None,
    ) -> None:
        self._bot = bot
        self._academic_client = academic_client
        self._send_queue = send_queue
        self._redis_client = redis_client
        self._config = config
        self._otp_tracker = otp_tracker
        self._request_tracker = request_tracker
        self._attendance_client = attendance_client
        self._credential_cards = credential_cards
        from bot.notifications.audience import NotificationDeliveryState

        self._delivery_state = NotificationDeliveryState(getattr(redis_client, "_redis", None))

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
                config=self._config,
                redis_client=self._redis_client,
            ),
            "lesson.reminder": lambda event: handle_lesson_reminder(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
                redis_client=self._redis_client,
            ),
            "lesson.cancelled": lambda event: handle_lesson_cancelled(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
            ),
            "lesson.blocked": lambda event: handle_lesson_blocked(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
            ),
            # 60-04: one-off lesson added/removed by a headman
            "lesson.one_off.created": lambda event: handle_lesson_one_off_created(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
            ),
            "lesson.one_off.cancelled": lambda event: handle_lesson_one_off_cancelled(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
            ),
            "homework.published": lambda event: handle_homework(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
            ),
            "homework.updated": lambda event: handle_homework(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
            ),
            "homework.weekly_digest": lambda event: handle_homework(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
            ),
            "homework.due_reminder": lambda event: handle_homework(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
            ),
            "excuse.requested": lambda event: handle_headman_alert(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
                request_tracker=self._request_tracker,
                attendance_client=self._attendance_client,
            ),
            "late_checkin.requested": lambda event: handle_headman_alert(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
                request_tracker=self._request_tracker,
                attendance_client=self._attendance_client,
            ),
            "lesson.closed": lambda event: handle_lesson_closed(
                event,
                send_queue=self._send_queue,
                config=self._config,
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
                config=self._config,
            ),
            # 58-07 / BUG-006-6: notify students when group is renamed / archived
            "group.renamed": lambda event: handle_group_renamed(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
            ),
            "group.archived": lambda event: handle_group_archived(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
                config=self._config,
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
                credential_cards=self._credential_cards,
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

    async def prepare(self, event: dict):
        from bot.notifications.audience import prepare_business
        from bot.services.notification_prefs import BUSINESS_EVENTS

        if event.get("event_type") in BUSINESS_EVENTS:
            return await prepare_business(event, self._academic_client)
        return None

    async def snapshot_progress(self, event: dict):
        return await self._delivery_state.snapshot(event)

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
        from bot.notifications.audience import handle_business
        from bot.services.notification_prefs import BUSINESS_EVENTS

        if event_type in BUSINESS_EVENTS:
            async with self._send_queue.staged() as batch:
                await handle_business(
                    event,
                    bot=self._bot,
                    academic=self._academic_client,
                    queue=self._send_queue,
                    reminders=self._redis_client,
                    config=self._config,
                    state=self._delivery_state,
                    attendance=self._attendance_client,
                    tracker=self._request_tracker,
                )
            return batch
        handler = self._handlers.get(event_type)

        if handler is None:
            logger.debug("Unhandled event type: unknown")
            return

        async with self._send_queue.staged() as batch:
            await handler(event)
        return batch
