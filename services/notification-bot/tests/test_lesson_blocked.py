"""Tests for the lesson.blocked notification handler."""

import pytest

from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_lesson_blocked_sends_to_students_with_telegram_id(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.blocked", expected=("Теормех &lt;snapshot&gt;", "10.10.2026"))


@pytest.mark.asyncio
async def test_lesson_blocked_message_contains_subject_date_and_reason(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.blocked",
        extra={"room": "Аудитория <305>"},
        expected=("Самостоятельная отметка закрыта", "10.10.2026", "Пара №2", "10:05–11:25", "Аудитория &lt;305&gt;"),
    )


@pytest.mark.asyncio
async def test_lesson_blocked_skips_missing_payload_fields(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.blocked", missing_fields=("lesson_id", "group_id", "subject_id"), reject=True
    )
