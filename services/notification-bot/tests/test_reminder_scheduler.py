"""Unit tests for ReminderScheduler — NOTIF-02 (midpoint) and NOTIF-03 (near-end)."""
import asyncio
from datetime import datetime
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from bot.services.reminder_scheduler import (
    ReminderScheduler,
    midpoint_delay_seconds,
    near_end_delay_seconds,
)


# ---------------------------------------------------------------------------
# Helper factories
# ---------------------------------------------------------------------------

def _make_student(user_id: int, telegram_id: int):
    s = MagicMock()
    s.user_id = user_id
    s.telegram_id = telegram_id
    s.is_headman = False
    return s


def _make_scheduler(
    academic_return=None,
    redis_ids=None,
):
    """Build a ReminderScheduler with mocked dependencies."""
    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=500))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(
        return_value=academic_return or []
    )

    send_queue = MagicMock()
    send_queue.put = AsyncMock()

    redis_client = MagicMock()
    redis_client.get_message_ids = AsyncMock(return_value=redis_ids if redis_ids is not None else [100])
    redis_client.add_message_id = AsyncMock()

    scheduler = ReminderScheduler(
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
    )
    return scheduler, bot, academic_client, send_queue, redis_client


# ---------------------------------------------------------------------------
# Test 1: midpoint_delay_seconds — correct delay from start_time to midpoint
# ---------------------------------------------------------------------------

def test_midpoint_delay_seconds_correct():
    """start_time=09:00, end_time=10:30 → midpoint=09:45. 'now'=09:00 → delay=2700s."""
    frozen_now = datetime(2026, 4, 5, 9, 0, 0)
    with patch("bot.services.reminder_scheduler.datetime") as mock_dt:
        mock_dt.now.return_value = frozen_now
        # _parse_hhmm_today calls datetime.now() then replaces hour/minute/second
        mock_dt.now.return_value = frozen_now
        result = midpoint_delay_seconds("09:00", "10:30")
    assert result == pytest.approx(2700.0)


# ---------------------------------------------------------------------------
# Test 2: near_end_delay_seconds — correct delay to 5 min before end_time
# ---------------------------------------------------------------------------

def test_near_end_delay_seconds_correct():
    """end_time=10:30, offset=5 → near_end=10:25. 'now'=09:00 → delay=5100s."""
    frozen_now = datetime(2026, 4, 5, 9, 0, 0)
    with patch("bot.services.reminder_scheduler.datetime") as mock_dt:
        mock_dt.now.return_value = frozen_now
        result = near_end_delay_seconds("10:30", offset_minutes=5)
    assert result == pytest.approx(5100.0)


# ---------------------------------------------------------------------------
# Test 3: delay already passed — returns 0.0 (not negative)
# ---------------------------------------------------------------------------

def test_midpoint_delay_already_passed_returns_zero():
    """If 'now' is after the midpoint, delay is clamped to 0.0."""
    # midpoint for 09:00-10:30 is 09:45; 'now' = 10:00 → past midpoint
    frozen_now = datetime(2026, 4, 5, 10, 0, 0)
    with patch("bot.services.reminder_scheduler.datetime") as mock_dt:
        mock_dt.now.return_value = frozen_now
        result = midpoint_delay_seconds("09:00", "10:30")
    assert result == 0.0


# ---------------------------------------------------------------------------
# Test 4: schedule_reminders creates exactly 2 asyncio.Task objects
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_schedule_reminders_creates_two_tasks():
    """After schedule_reminders, _timers[lesson_id] has exactly 2 asyncio.Task objects."""
    students = [_make_student(user_id=1, telegram_id=111)]
    scheduler, bot, academic_client, send_queue, redis_client = _make_scheduler(
        academic_return=students,
        redis_ids=[100],
    )
    # Use 0-delay so tasks complete without waiting
    with patch("bot.services.reminder_scheduler.midpoint_delay_seconds", return_value=0.0), \
         patch("bot.services.reminder_scheduler.near_end_delay_seconds", return_value=0.0):
        scheduler.schedule_reminders(
            lesson_id=101, group_id=5, start_time="09:00", end_time="10:30"
        )

    assert 101 in scheduler._timers
    tasks = scheduler._timers[101]
    assert len(tasks) == 2
    for t in tasks:
        assert isinstance(t, asyncio.Task)

    # Let tasks complete to avoid warnings
    await asyncio.gather(*tasks, return_exceptions=True)


# ---------------------------------------------------------------------------
# Test 5: cancel_lesson cancels tasks and removes from _timers
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_cancel_lesson_cancels_tasks():
    """cancel_lesson cancels both tasks and removes the entry from _timers."""
    students = [_make_student(user_id=1, telegram_id=111)]
    scheduler, _, _, _, _ = _make_scheduler(academic_return=students, redis_ids=[100])

    # Large delay so tasks don't fire during the test
    with patch("bot.services.reminder_scheduler.midpoint_delay_seconds", return_value=9999.0), \
         patch("bot.services.reminder_scheduler.near_end_delay_seconds", return_value=9999.0):
        scheduler.schedule_reminders(
            lesson_id=202, group_id=5, start_time="09:00", end_time="10:30"
        )

    assert 202 in scheduler._timers
    tasks = list(scheduler._timers[202])  # copy before cancel removes them

    scheduler.cancel_lesson(202)

    assert 202 not in scheduler._timers
    # Give event loop one cycle to propagate cancellation
    await asyncio.sleep(0)
    for t in tasks:
        assert t.cancelled()


# ---------------------------------------------------------------------------
# Test 6: _send_reminder_after skips students with no Redis key (already checked in)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_send_reminder_after_skips_students_with_no_redis_key():
    """Students whose get_message_ids returns [] are skipped (send_queue.put not called)."""
    students = [_make_student(user_id=1, telegram_id=111)]
    scheduler, bot, academic_client, send_queue, redis_client = _make_scheduler(
        academic_return=students,
        redis_ids=[],  # empty → already checked in
    )

    await scheduler._send_reminder_after(
        delay=0.0, lesson_id=101, group_id=5, label="mid"
    )

    send_queue.put.assert_not_called()


# ---------------------------------------------------------------------------
# Test 7: _send_reminder_after sends to students with Redis key and stores message_id
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_send_reminder_after_sends_and_stores_message_id():
    """Student with Redis key gets a reminder; message_id is stored via add_message_id."""
    student = _make_student(user_id=1, telegram_id=111)
    scheduler, bot, academic_client, send_queue, redis_client = _make_scheduler(
        academic_return=[student],
        redis_ids=[100],  # has existing key → should send
    )

    # Capture the SendTask
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await scheduler._send_reminder_after(
        delay=0.0, lesson_id=101, group_id=5, label="end"
    )

    assert len(captured_tasks) == 1

    # Execute the coroutine_factory to verify it sends and stores
    await captured_tasks[0].coroutine_factory()

    bot.send_message.assert_called_once_with(
        chat_id=111,
        text="Напоминание: отметьтесь на паре!",
    )
    redis_client.add_message_id.assert_called_once_with(101, 1, 500)


# ---------------------------------------------------------------------------
# Test 8: cancel_lesson for non-existent lesson_id is a no-op
# ---------------------------------------------------------------------------

def test_cancel_lesson_nonexistent_is_noop():
    """cancel_lesson(999) with no registered timers does not raise."""
    scheduler, _, _, _, _ = _make_scheduler()
    # Must not raise
    scheduler.cancel_lesson(999)
    assert 999 not in scheduler._timers
