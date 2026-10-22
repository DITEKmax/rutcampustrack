"""Tests for the lesson.one_off.cancelled notification handler (Phase 60-04)."""

import pytest

from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_sends_cancellation_to_all(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.one_off.cancelled", expected=("Теормех &lt;snapshot&gt;", "10.10.2026")
    )


@pytest.mark.asyncio
async def test_message_contains_date_and_subject(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.one_off.cancelled", expected=("Теормех &lt;snapshot&gt;", "10.10.2026")
    )


@pytest.mark.asyncio
async def test_fallback_subject_on_grpc_error(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.one_off.cancelled",
        missing_fields=("subject_name",),
        expected=("Занятие отменено", "10.10.2026", "Пара №2"),
        absent=("Теормех",),
    )


@pytest.mark.asyncio
async def test_skips_missing_payload_fields(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.one_off.cancelled", missing_fields=("lesson_id", "group_id", "subject_id"), reject=True
    )
