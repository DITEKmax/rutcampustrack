"""EventDispatcher — routes incoming RabbitMQ events to the correct handler."""

import logging
from typing import Awaitable, Callable

from aiogram import Bot

from bot.config import Settings
from bot.grpc_client.academic_client import AcademicGrpcClient
from bot.services.otp_message_tracker import OtpMessageTracker
from bot.services.redis_client import ReminderRedisClient
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
    ) -> None:
        self._bot = bot
        self._academic_client = academic_client
        self._send_queue = send_queue
        self._redis_client = redis_client
        self._config = config
        self._otp_tracker = otp_tracker
        self._reminder_scheduler = reminder_scheduler

        # Import handlers here to avoid circular imports at module level
        from bot.notifications.attendance_marked import handle_attendance_marked
        from bot.notifications.group_archived import handle_group_archived
        from bot.notifications.group_renamed import handle_group_renamed
        from bot.notifications.headman_alerts import handle_headman_alert
        from bot.notifications.homework import handle_homework
        from bot.notifications.lesson_cancelled import handle_lesson_cancelled
        from bot.notifications.lesson_closed import handle_lesson_closed
        from bot.notifications.lesson_one_off_cancelled import handle_lesson_one_off_cancelled
        from bot.notifications.lesson_one_off_created import handle_lesson_one_off_created
        from bot.notifications.otp_verified import handle_otp_verified
        from bot.notifications.student_alerts import handle_student_alert

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
            ),
            # 59-06 / D-28: notify the student when their excuse ticket is decided
            "excuse.decided": lambda event: handle_student_alert(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            "late_checkin.requested": lambda event: handle_headman_alert(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
            "late_checkin.decided": lambda event: handle_student_alert(
                event,
                bot=self._bot,
                academic_client=self._academic_client,
                send_queue=self._send_queue,
            ),
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
            # OTP login cleanup: remove code + request messages when user logs in
            "otp.verified": lambda event: handle_otp_verified(
                event,
                bot=self._bot,
                tracker=self._otp_tracker,
            ),
        }

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

        Unknown event types are logged at DEBUG and silently ignored.
        Handler exceptions are caught and logged — never re-raised.
        """
        event_type = event.get("event_type")
        handler = self._handlers.get(event_type)

        if handler is None:
            logger.debug("Unhandled event type: %s", event_type)
            return

        try:
            await handler(event)
        except Exception:
            logger.exception("Handler failed for event_type=%s", event_type)
