"""Tests for the lesson.cancelled notification handler."""

import pytest

from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_lesson_cancelled_sends_to_students_with_telegram_id(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.cancelled", expected=("Теормех &lt;snapshot&gt;", "10.10.2026"))


@pytest.mark.asyncio
async def test_lesson_cancelled_message_contains_subject_name_and_date(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.cancelled", expected=("Теормех &lt;snapshot&gt;", "10.10.2026"))


@pytest.mark.asyncio
async def test_lesson_cancelled_with_cancel_reason(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.cancelled",
        extra={"cancel_reason": "Преподаватель <заболел>"},
        expected=("Причина: Преподаватель &lt;заболел&gt;",),
        absent=("<заболел>",),
    )


@pytest.mark.asyncio
async def test_lesson_cancelled_without_cancel_reason(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.cancelled",
        no_reason=True,
        expected=("Занятие отменено", "10.10.2026"),
        absent=("Причина:",),
    )


@pytest.mark.asyncio
async def test_lesson_cancelled_fallback_subject_name_on_grpc_error(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.cancelled",
        missing_fields=("subject_name",),
        expected=("Занятие отменено", "Пара №2"),
        absent=("Теормех",),
    )


@pytest.mark.asyncio
async def test_lesson_cancelled_skips_missing_payload_fields(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.cancelled", missing_fields=("lesson_id", "group_id", "subject_id"), reject=True
    )
