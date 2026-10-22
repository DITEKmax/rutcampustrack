"""Tests for the lesson.closed notification handler (NOTIF-04).

Verifies that when a lesson closes, all stored reminder messages are deleted
from Telegram and Redis keys are cleared. In-memory reminder scheduler был
удалён вместе с миграцией midpoint-напоминаний на schedule-service
(LessonReminderJob), поэтому handler больше не принимает reminder_scheduler.
"""

import pytest

from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_lesson_closed_deletes_all_message_ids_and_clears_redis(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.closed", expected=("Теормех &lt;snapshot&gt;", "10.10.2026"))


@pytest.mark.asyncio
async def test_lesson_closed_skips_students_without_telegram_id(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.closed", chats={2: 0}, expected_users=(1,), expected=("Занятие завершено",)
    )


@pytest.mark.asyncio
async def test_lesson_closed_silently_ignores_telegram_bad_request(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.closed", bad_delete=True, expected=("Теормех &lt;snapshot&gt;", "10.10.2026")
    )


@pytest.mark.asyncio
async def test_lesson_closed_skips_students_with_empty_message_ids(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.closed", no_messages=True, expected=("Теормех &lt;snapshot&gt;", "10.10.2026")
    )


@pytest.mark.asyncio
async def test_lesson_closed_returns_early_on_missing_payload_fields(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.closed", missing_fields=("lesson_id", "group_id", "subject_id"), reject=True
    )
