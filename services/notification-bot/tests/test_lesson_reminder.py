"""Tests for lesson.reminder Telegram fan-out."""

import pytest

from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_lesson_reminder_sends_to_unmarked_students_even_without_start_message_key(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.reminder", start_keys=False, expected_users=(1, 2), expected=("Напоминание об отметке",)
    )


@pytest.mark.asyncio
async def test_lesson_reminder_skips_marked_students(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.reminder",
        start_keys=True,
        marked_users=(1,),
        expected_users=(2,),
        expected=("Напоминание об отметке",),
    )


@pytest.mark.asyncio
async def test_lesson_reminder_returns_early_on_missing_required_fields(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.reminder", missing_fields=("lesson_id", "group_id", "subject_id"), reject=True
    )
