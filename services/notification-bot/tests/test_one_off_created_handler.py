"""Tests for the lesson.one_off.created notification handler (Phase 60-04)."""

import pytest

from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_sends_to_all_group_students(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.one_off.created",
        chats={3: 303},
        expected_users=(1, 2, 3),
        extra={"room": None, "classroom": "C-<303>", "lesson_number": 3},
        expected=("Добавлено разовое занятие", "10.10.2026", "Пара №3", "C-&lt;303&gt;"),
    )


@pytest.mark.asyncio
async def test_empty_group_no_error(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.one_off.created", empty=True, expected=("Теормех &lt;snapshot&gt;", "10.10.2026")
    )


@pytest.mark.asyncio
async def test_skips_students_without_telegram_id(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.one_off.created", chats={2: 0}, expected_users=(1,), expected=("Добавлено разовое занятие",)
    )


@pytest.mark.asyncio
async def test_skips_missing_payload_fields(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.one_off.created", missing_fields=("lesson_id", "group_id", "subject_id"), reject=True
    )
