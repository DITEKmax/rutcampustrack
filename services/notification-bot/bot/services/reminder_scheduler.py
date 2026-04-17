"""ReminderScheduler — spawns timed asyncio tasks for midpoint and near-end reminders.

Phase 25, Plan 01: NOTIF-02 (midpoint reminder) and NOTIF-03 (near-end reminder).

Design decisions:
- Uses naive datetime.now() for delay calculation. The Docker container MUST have
  TZ=Europe/Moscow env var set so that naive times align with lesson time strings
  (which come from Schedule Service as Moscow local time without timezone).
- Checks get_message_ids before sending: empty list means student already checked in
  (NOTIF-05 cleared their key) or lesson was closed.
- cancel_lesson is called by the lesson.closed handler (Plan 02) to prevent stale
  reminders after early lesson close.
- Default-arg binding s=student prevents Python late-binding closure bug (same pattern
  established in Phase 24 lesson_started.py).
"""

import asyncio
import logging
from datetime import datetime, timedelta

from aiogram import Bot

from bot.services.redis_client import ReminderRedisClient
from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)

NEAR_END_OFFSET_MINUTES = 5


def _parse_hhmm_today(s: str) -> datetime:
    """Parse 'HH:MM' into today's naive datetime (same clock as container)."""
    h, m = map(int, s.split(":"))
    return datetime.now().replace(hour=h, minute=m, second=0, microsecond=0)


def midpoint_delay_seconds(start_time: str, end_time: str) -> float:
    """Return seconds from now until the midpoint of the lesson. Minimum 0.0."""
    start_dt = _parse_hhmm_today(start_time)
    end_dt = _parse_hhmm_today(end_time)
    midpoint = start_dt + (end_dt - start_dt) / 2
    now = datetime.now()
    return max(0.0, (midpoint - now).total_seconds())


def near_end_delay_seconds(end_time: str, offset_minutes: int = NEAR_END_OFFSET_MINUTES) -> float:
    """Return seconds from now until offset_minutes before end_time. Minimum 0.0."""
    end_dt = _parse_hhmm_today(end_time)
    near_end = end_dt - timedelta(minutes=offset_minutes)
    now = datetime.now()
    return max(0.0, (near_end - now).total_seconds())


class ReminderScheduler:
    """Spawns and tracks timed asyncio tasks for midpoint and near-end reminders."""

    def __init__(
        self,
        bot: Bot,
        academic_client,
        send_queue: TelegramSendQueue,
        redis_client: ReminderRedisClient,
    ) -> None:
        self._bot = bot
        self._academic_client = academic_client
        self._send_queue = send_queue
        self._redis_client = redis_client
        self._timers: dict[int, list[asyncio.Task]] = {}

    def schedule_reminders(self, lesson_id: int, group_id: int, start_time: str, end_time: str) -> None:
        """Spawn two asyncio.Task instances for midpoint and near-end reminders."""
        mid_delay = midpoint_delay_seconds(start_time, end_time)
        end_delay = near_end_delay_seconds(end_time)
        mid_task = asyncio.create_task(self._send_reminder_after(mid_delay, lesson_id, group_id, "mid"))
        end_task = asyncio.create_task(self._send_reminder_after(end_delay, lesson_id, group_id, "end"))
        self._timers[lesson_id] = [mid_task, end_task]
        logger.info(
            "Scheduled reminders for lesson_id=%d: mid=%.0fs, end=%.0fs",
            lesson_id,
            mid_delay,
            end_delay,
        )

    async def _send_reminder_after(self, delay: float, lesson_id: int, group_id: int, label: str) -> None:
        """Sleep until delay, then fan-out reminder to students not yet checked in."""
        if delay > 0:
            await asyncio.sleep(delay)
        members = await self._academic_client.get_group_members(group_id)
        for student in members:
            if not student.telegram_id:
                continue
            existing_ids = await self._redis_client.get_message_ids(lesson_id, student.user_id)
            if not existing_ids:
                continue  # Already checked in or lesson closed — skip

            # Default-arg binding (s=student) avoids late-binding closure bug
            async def send_and_store(s=student):
                result = await self._bot.send_message(
                    chat_id=s.telegram_id,
                    text="Напоминание: отметьтесь на паре!",
                )
                await self._redis_client.add_message_id(lesson_id, s.user_id, result.message_id)

            await self._send_queue.put(
                SendTask(
                    coroutine_factory=send_and_store,
                    user_id=student.user_id,
                    chat_id=student.telegram_id,
                    category="reminders",
                )
            )
        logger.info("Reminder '%s' queued for lesson_id=%d", label, lesson_id)

    def cancel_lesson(self, lesson_id: int) -> None:
        """Cancel all pending timer tasks for the given lesson_id. No-op if none exist."""
        tasks = self._timers.pop(lesson_id, [])
        for task in tasks:
            task.cancel()
        if tasks:
            logger.info("Cancelled %d timer(s) for lesson_id=%d", len(tasks), lesson_id)
