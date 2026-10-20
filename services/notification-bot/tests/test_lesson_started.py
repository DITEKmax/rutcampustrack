"""Tests for the lesson.started notification handler."""

import pytest

from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_lesson_started_sends_to_students_with_telegram_id(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.started", expected=("Теормех &lt;snapshot&gt;", "10.10.2026"))


@pytest.mark.asyncio
async def test_lesson_started_sends_plain_chat_message_without_web_app(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.started",
        app_url=None,
        missing_fields=("subject_name", "date", "lesson_date", "semester_id", "lesson_type"),
        expected=("Занятие началось", "10:05–11:25", "Пара №2", "А-305"),
    )


@pytest.mark.asyncio
async def test_lesson_started_adds_mini_app_checkin_web_app_url(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.started",
        extra={"lesson_id": 77},
        missing_fields=("subject_name", "date", "lesson_date", "semester_id", "lesson_type"),
        app_action={"kind": "lesson", "role": "STUDENT", "lessonId": "77"},
        expected=("Занятие началось",),
    )


@pytest.mark.asyncio
async def test_lesson_started_stores_message_id_in_redis(fake_redis):
    await assert_business_delivery(fake_redis, "lesson.started", expected=("Теормех &lt;snapshot&gt;", "10.10.2026"))


@pytest.mark.asyncio
async def test_lesson_started_resolves_subject_name(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.started",
        extra={"subject_name": "Линейная <алгебра>"},
        expected=("Линейная &lt;алгебра&gt;",),
        absent=("<алгебра>",),
    )


@pytest.mark.asyncio
async def test_lesson_started_fallback_subject_name_on_grpc_error(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "lesson.started",
        missing_fields=("subject_name",),
        expected=("Занятие началось", "Пара №2"),
        absent=("Теормех",),
    )


@pytest.mark.asyncio
async def test_lesson_started_skips_missing_payload_fields(fake_redis):
    await assert_business_delivery(
        fake_redis, "lesson.started", missing_fields=("lesson_id", "group_id", "subject_id"), reject=True
    )
